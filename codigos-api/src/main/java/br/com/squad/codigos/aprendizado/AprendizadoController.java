package br.com.squad.codigos.aprendizado;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import br.com.squad.codigos.acesso.Acesso;
import br.com.squad.codigos.sistema.Auditoria;

@Tag(name = "Aprendizado", description = "O que o MOKKA aprendeu com as decisões das pessoas e do curador")
@RestController
@RequestMapping("/api/aprendizado")
public class AprendizadoController {

    private final Aprendizado aprendizado;
    private final Acesso acesso;
    private final Auditoria auditoria;

    public AprendizadoController(Aprendizado aprendizado, Acesso acesso, Auditoria auditoria) {
        this.aprendizado = aprendizado;
        this.acesso = acesso;
        this.auditoria = auditoria;
    }

    @Operation(summary = "O que o MOKKA aprendeu", description = "Descrições que já foram ligadas a um código (IGUAL), as que o curador disse que são outra coisa (DIFERENTE) e as palavras corrigidas.")
    @GetMapping
    public Painel painel() {
        return new Painel(aprendizado.regras(), aprendizado.palavras());
    }

    @Operation(summary = "Ensinar uma palavra", description = "Quando alguém troca uma palavra que o microfone ouviu errado. Só passa a valer depois que um curador aceitar.")
    @PostMapping("/palavras")
    public void ensinar(@Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String auth,
                        @Valid @RequestBody Troca t) {
        aprendizado.palavra(t.de(), t.para(), acesso.exigir(auth).nome());
    }

    @Operation(summary = "Curador aceita ou recusa uma palavra aprendida")
    @PostMapping("/palavras/{termo}/decisao")
    public void decidir(@Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String auth,
                        @PathVariable String termo, @Valid @RequestBody Decisao d) {
        Acesso.Sessao s = acesso.exigirPerfil(auth, "CURADOR");
        if (!aprendizado.decidirPalavra(termo, d.aceitar(), s.nome())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Palavra não encontrada: " + termo);
        }
        auditoria.registrar(s.nome(), d.aceitar() ? "ACEITOU_PALAVRA" : "RECUSOU_PALAVRA", termo, null);
    }

    @Operation(summary = "Curador manda esquecer uma regra aprendida")
    @DeleteMapping("/regras/{id}")
    public void esquecer(@Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String auth,
                         @PathVariable long id) {
        Acesso.Sessao s = acesso.exigirPerfil(auth, "CURADOR");
        if (!aprendizado.esquecer(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Regra não encontrada: " + id);
        }
        auditoria.registrar(s.nome(), "ESQUECEU_REGRA", String.valueOf(id), null);
    }

    public record Troca(@NotBlank @Size(max = 40) String de, @NotBlank @Size(max = 40) String para) {
    }

    public record Decisao(@NotNull Boolean aceitar) {
    }

    public record Painel(List<Aprendizado.Regra> regras, List<Aprendizado.Palavra> palavras) {
    }
}
