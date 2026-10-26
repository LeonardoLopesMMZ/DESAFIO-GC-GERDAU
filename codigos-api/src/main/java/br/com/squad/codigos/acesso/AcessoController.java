package br.com.squad.codigos.acesso;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Acesso", description = "Entrada com token: pelo nome (demonstração), pelo rosto na câmera ou com a chave do Windows (Hello, digital ou PIN)")
@RestController
@RequestMapping("/api/acesso")
public class AcessoController {

    private final Acesso acesso;

    public AcessoController(Acesso acesso) {
        this.acesso = acesso;
    }

    @Operation(summary = "Entrar pelo nome (modo demonstração)", description = "Devolve um token que vale 8 horas. Use em Authorize no Swagger.")
    @PostMapping("/entrar")
    public Entrada entrar(@Valid @RequestBody PedidoNome p) {
        return entrada(acesso.entrarComNome(p.nome().trim()));
    }

    @Operation(summary = "Quem está usando o token")
    @GetMapping("/eu")
    public Entrada eu(@Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String auth) {
        return entrada(acesso.exigir(auth));
    }

    @Operation(summary = "Sair (o token deixa de valer)")
    @PostMapping("/sair")
    public void sair(@Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String auth) {
        acesso.sair(auth);
    }

    @Operation(summary = "Desafio para cadastrar ou usar a chave de acesso")
    @PostMapping("/chave/desafio")
    public Desafio desafio(@Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String auth) {
        String nome = null;
        if (auth != null && !auth.isBlank()) {
            nome = acesso.exigir(auth).nome();
        }
        return new Desafio(acesso.desafio(), nome == null ? null : Acesso.idDoUsuario(nome), nome);
    }

    @Operation(summary = "Cadastrar a chave de acesso de quem já entrou")
    @PostMapping("/chave/cadastrar")
    public Entrada cadastrar(@Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String auth,
                             @Parameter(hidden = true) @RequestHeader(value = "Origin", required = false) String origem,
                             @Valid @RequestBody Cadastro c) {
        Acesso.Sessao s = acesso.exigir(auth);
        acesso.cadastrarChave(s, c.credencialId(), c.chavePublica(), c.algoritmo(), c.clientDataJson(), origem);
        return entrada(s);
    }

    @Operation(summary = "Entrar com a chave de acesso (rosto, digital ou PIN)")
    @PostMapping("/chave/entrar")
    public Entrada entrarComChave(@Parameter(hidden = true) @RequestHeader(value = "Origin", required = false) String origem,
                                  @Valid @RequestBody Assinatura a) {
        return entrada(acesso.entrarComChave(a.credencialId(), a.clientDataJson(), a.dadosAutenticador(), a.assinatura(), origem));
    }

    @Operation(summary = "Cadastrar o rosto de quem já entrou",
            description = "Recebe a leitura do rosto feita no navegador (128 números) e uma foto JPEG pequena, até 20 KB, que fica guardada no banco.")
    @PostMapping("/rosto/cadastrar")
    public Entrada cadastrarRosto(@Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String auth,
                                  @Valid @RequestBody Rosto r) {
        Acesso.Sessao s = acesso.exigir(auth);
        acesso.cadastrarRosto(s, r.descritor(), r.foto());
        return entrada(s);
    }

    @Operation(summary = "Entrar pelo rosto",
            description = "Compara a leitura com os rostos cadastrados. Se a distância for até 0,5, entra e guarda a foto do momento, que fica ligada a tudo o que a pessoa fizer.")
    @PostMapping("/rosto/entrar")
    public Entrada entrarComRosto(@Valid @RequestBody Rosto r) {
        return entrada(acesso.entrarComRosto(r.descritor(), r.foto()));
    }

    @Operation(summary = "Foto guardada no banco", description = "Devolve a foto (JPEG) de um cadastro ou de uma entrada pelo rosto.")
    @GetMapping("/retratos/{id}")
    public ResponseEntity<byte[]> retrato(@Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String auth,
                                          @PathVariable long id) {
        acesso.exigir(auth);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).cacheControl(CacheControl.noStore()).body(acesso.retrato(id));
    }

    private Entrada entrada(Acesso.Sessao s) {
        Usuario u = acesso.usuario(s.nome());
        boolean temRosto = u != null && u.temRosto();
        Long foto = s.retratoId() != null ? s.retratoId() : temRosto ? u.getRetratoId() : null;
        return new Entrada(s.token(), u == null ? null : u.getId(), s.nome(), s.metodo(), acesso.temChave(s.nome()), temRosto, acesso.soChave(), foto, s.expira());
    }

    public record PedidoNome(@NotBlank(message = "Informe o nome") @Size(max = 60, message = "O nome deve ter até 60 caracteres") String nome) {
    }

    public record Entrada(String token, Long contaId, String nome, String metodo, boolean temChave, boolean temRosto, boolean soChave, Long retratoId, Instant expira) {
    }

    public record Rosto(@NotNull(message = "Falta a leitura do rosto") @Size(min = 128, max = 128, message = "A leitura do rosto deve ter 128 números") List<Double> descritor,
                        @NotBlank(message = "Falta a foto") @Size(max = 30000, message = "A foto deve ter até 20 KB") String foto) {
    }

    public record Desafio(String desafio, String usuarioId, String nome) {
    }

    public record Cadastro(@NotBlank @Size(max = 400) String credencialId, @NotBlank @Size(max = 1200) String chavePublica,
                           @NotNull Integer algoritmo, @NotBlank @Size(max = 2000) String clientDataJson) {
    }

    public record Assinatura(@NotBlank @Size(max = 400) String credencialId, @NotBlank @Size(max = 2000) String clientDataJson,
                             @NotBlank @Size(max = 2000) String dadosAutenticador, @NotBlank @Size(max = 1200) String assinatura) {
    }
}
