package br.com.squad.codigos.sistema;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Sistema", description = "Qual banco está ligado")
@RestController
public class SistemaController {

    private final Carga carga;

    public SistemaController(Carga carga) {
        this.carga = carga;
    }

    @Operation(summary = "Banco em uso", description = "PostgreSQL guarda tudo entre um dia e outro; H2 é o banco de reserva, na memória.")
    @GetMapping("/api/sistema")
    public Sistema sistema(HttpServletRequest req) {
        return new Sistema(carga.persistente() ? "PostgreSQL" : "H2", carga.persistente(), Guarda.local(req));
    }

    public record Sistema(String banco, boolean persistente, boolean local) {
    }
}
