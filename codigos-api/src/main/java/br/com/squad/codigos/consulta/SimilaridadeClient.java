package br.com.squad.codigos.consulta;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class SimilaridadeClient {

    private final RestClient http;

    public SimilaridadeClient(RestClient.Builder builder, @Value("${similaridade.url}") String url) {
        SimpleClientHttpRequestFactory tempo = new SimpleClientHttpRequestFactory();
        tempo.setConnectTimeout(3_000);
        tempo.setReadTimeout(180_000);
        this.http = builder.baseUrl(url).requestFactory(tempo).build();
    }

    public Resposta buscar(String descricao, String unidade) {
        return enviar("/similares", Map.of("descricao", descricao, "unidade", unidade, "limite", 10), Resposta.class);
    }

    public Novo adicionar(String codigo, String descricao, String unidade, String grupo) {
        return enviar("/codigos", Map.of("codigo", codigo, "descricao", descricao, "unidade", unidade,
                "grupo", grupo == null ? "" : grupo), Novo.class);
    }

    public Planilha lerPlanilha(byte[] arquivo, String nome) {
        return chamar(() -> http.post().uri("/lote/ler")
                .header("X-Arquivo", URLEncoder.encode(nome == null ? "" : nome, StandardCharsets.UTF_8))
                .contentType(MediaType.APPLICATION_OCTET_STREAM).body(arquivo).retrieve().body(Planilha.class));
    }

    public Palavras palavras(String texto) {
        return enviar("/palavras", Map.of("texto", texto), Palavras.class);
    }

    public Analises analisarLote(List<Map<String, String>> itens) {
        return enviar("/lote", Map.of("itens", itens), Analises.class);
    }

    public void adicionarVarios(List<Map<String, String>> itens) {
        enviar("/codigos/lote", Map.of("itens", itens), Map.class);
    }

    public byte[] exportar(List<?> linhas, List<String> relatorio) {
        return enviar("/lote/exportar", Map.of("linhas", linhas, "relatorio", relatorio == null ? List.of() : relatorio), byte[].class);
    }

    private <T> T enviar(String caminho, Map<String, Object> corpo, Class<T> tipo) {
        return chamar(() -> http.post().uri(caminho).body(corpo).retrieve().body(tipo));
    }

    private <T> T chamar(java.util.function.Supplier<T> chamada) {
        try {
            return chamada.get();
        } catch (ResourceAccessException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "O serviço de similaridade (Python) está fora do ar. Feche tudo e rode o iniciar.bat de novo.");
        } catch (RestClientResponseException e) {
            String corpo = e.getResponseBodyAsString();
            String msg = corpo.contains("\"erro\"") ? corpo.replaceAll("(?s).*\"erro\"\\s*:\\s*\"(.*?)\".*", "$1") : corpo;
            throw new ResponseStatusException(e.getStatusCode().is4xxClientError() ? HttpStatus.BAD_REQUEST : HttpStatus.BAD_GATEWAY, msg);
        }
    }

    public record Planilha(Map<String, String> colunas, List<Map<String, Object>> linhas, List<String> relatorio) {
    }

    public record Palavras(List<Palavra> palavras) {
    }

    public record Palavra(int indice, int tamanho, String palavra, List<String> sugestoes) {
    }

    public record Analises(List<Analise> itens) {
    }

    public record Analise(String descricaoLimpa, String nomePadrao, String categoria, String resultado, String motivo,
                          Integer repeteItem, String codigoExistente, String descricaoExistente, Integer aderencia,
                          List<String> diferencas) {
    }

    public record Resposta(String resultado, String motivo, List<Candidato> candidatos, Sugestao sugestao) {
    }

    public record Sugestao(String descricao, List<Correcao> correcoes, String resultado, String motivo, String codigo,
                           String descricaoExistente) {
    }

    public record Correcao(String de, String para) {
    }

    public record Candidato(String codigo, String descricao, String nomePadrao, String unidade, String categoria,
                            int aderencia, String classe, List<String> diferencas, Origem encontradoPor) {
    }

    public record Origem(String codigo, String descricao) {
    }

    public record Novo(String codigo, String descricaoLimpa, String categoria, String nomePadrao) {
    }
}
