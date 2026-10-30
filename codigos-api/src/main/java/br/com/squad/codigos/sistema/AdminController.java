package br.com.squad.codigos.sistema;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.squad.codigos.acesso.Acesso;
import br.com.squad.codigos.acesso.Usuario;

@Tag(name = "Administração", description = "Perfis das pessoas, registro de tudo o que aconteceu, desbloqueio e recomeço do banco. Só ADMIN.")
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final Acesso acesso;
    private final Auditoria auditoria;
    private final Guarda guarda;
    private final Carga carga;

    public AdminController(Acesso acesso, Auditoria auditoria, Guarda guarda, Carga carga) {
        this.acesso = acesso;
        this.auditoria = auditoria;
        this.guarda = guarda;
        this.carga = carga;
    }

    @Operation(summary = "Pessoas e perfis")
    @GetMapping("/pessoas")
    public List<Pessoa> pessoas(@Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String auth) {
        acesso.exigirPerfil(auth, "ADMIN");
        return acesso.pessoas().stream().map(this::pessoa).toList();
    }

    @Operation(summary = "Mudar o perfil de uma pessoa", description = "COMPRADOR consulta e pede códigos; CURADOR também aprova, recusa e ensina o MOKKA; ADMIN também cuida das pessoas.")
    @PostMapping("/pessoas/{id}/perfil")
    public Pessoa perfil(@Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String auth,
                         @PathVariable long id, @Valid @RequestBody Perfil p) {
        return pessoa(acesso.mudarPerfil(acesso.exigirPerfil(auth, "ADMIN"), id, p.perfil().trim().toUpperCase()));
    }

    @Operation(summary = "Registro de auditoria", description = "Últimos 200 acontecimentos: entradas, entradas recusadas, códigos criados, decisões do curador, perfis, fotos apagadas.")
    @GetMapping("/auditoria")
    public List<Map<String, Object>> registro(@Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String auth) {
        acesso.exigirPerfil(auth, "ADMIN");
        return auditoria.ultimos();
    }

    @Operation(summary = "Desbloquear quem errou a entrada muitas vezes")
    @PostMapping("/desbloquear")
    public void desbloquear(@Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String auth) {
        Acesso.Sessao s = acesso.exigirPerfil(auth, "ADMIN");
        guarda.desbloquear();
        auditoria.registrar(s.nome(), "DESBLOQUEOU", null, null);
    }

    @Operation(summary = "Zerar o banco", description = "Apaga pedidos, pessoas, fotos, aprendizado e códigos criados e carrega o catálogo de novo a partir da base.")
    @PostMapping("/zerar")
    public Map<String, String> zerar(@Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String auth) throws SQLException {
        Acesso.Sessao s = acesso.exigirPerfil(auth, "ADMIN");
        carga.zerar();
        auditoria.registrar(s.nome(), "ZEROU_BANCO", carga.banco(), null);
        return Map.of("banco", carga.banco(), "situacao", "zerado e carregado de novo");
    }

    private Pessoa pessoa(Usuario u) {
        return new Pessoa(u.getId(), u.getNome(), acesso.adminPelaLista(u.getNome()) ? "ADMIN" : u.getPerfil(), acesso.adminPelaLista(u.getNome()),
                u.temRosto(), u.temChave(), u.getCriadoEm());
    }

    public record Perfil(@NotBlank String perfil) {
    }

    public record Pessoa(Long id, String nome, String perfil, boolean adminFixo, boolean temRosto, boolean temChave, LocalDateTime criadoEm) {
    }
}
