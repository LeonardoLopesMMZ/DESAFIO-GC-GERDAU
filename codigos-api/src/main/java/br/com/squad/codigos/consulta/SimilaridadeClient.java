package br.com.squad.codigos.consulta;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class SimilaridadeClient {

    private final RestClient http;

    public SimilaridadeClient(RestClient.Builder builder, @Value("${similaridade.url}") String url) {
        this.http = builder.baseUrl(url).build();
    }

    public Resposta buscar(String descricao, String unidade) {
        return enviar("/similares", Map.of("descricao", descricao, "unidade", unidade, "limite", 10), Resposta.class);
    }

    public Novo adicionar(String codigo, String descricao, String unidade, String grupo) {
        return enviar("/codigos", Map.of("codigo", codigo, "descricao", descricao, "unidade", unidade,
                "grupo", grupo == null ? "" : grupo), Novo.class);
    }

    private <T> T enviar(String caminho, Map<String, Object> corpo, Class<T> tipo) {
        try {
            return http.post().uri(caminho).body(corpo).retrieve().body(tipo);
        } catch (ResourceAccessException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Serviço de similaridade fora do ar. Rode na pasta python: python api.py");
        } catch (RestClientResponseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Serviço de similaridade respondeu com erro: " + e.getResponseBodyAsString());
        }
    }

    public record Resposta(String resultado, String motivo, List<Candidato> candidatos) {
    }

    public record Candidato(String codigo, String descricao, String nomePadrao, String unidade, String categoria,
                            int aderencia, String classe, List<String> diferencas, Origem encontradoPor) {
    }

    public record Origem(String codigo, String descricao) {
    }

    public record Novo(String codigo, String descricaoLimpa, String categoria, String nomePadrao) {
    }
}
