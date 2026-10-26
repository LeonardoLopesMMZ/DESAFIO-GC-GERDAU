package br.com.squad.codigos;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class CodigosApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodigosApplication.class, args);
    }

    @Bean
    OpenAPI documentacao() {
        return new OpenAPI()
                .components(new Components().addSecuritySchemes("token",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer")
                                .description("Pegue o token em POST /api/acesso/entrar e cole aqui.")))
                .addSecurityItem(new SecurityRequirement().addList("token"))
                .info(new Info()
                .title("MOKKA · Códigos de serviço")
                .version("0.4.0")
                .description("API do protótipo do Grupo 17 (Desafio GC 2026 · Gerdau). Consulta descrições antes de criar "
                        + "código, cria códigos novos, manda casos duvidosos para o curador e processa planilhas inteiras "
                        + "de uma vez. Para gravar, entre primeiro em POST /api/acesso/entrar e clique em Authorize com o token. "
                        + "O serviço de similaridade em Python precisa estar ligado na porta 8000."));
    }
}
