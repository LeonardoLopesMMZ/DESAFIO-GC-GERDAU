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
    private final Reserva reserva;

    public SimilaridadeClient(RestClient.Builder builder, @Value("${similaridade.url}") String url, Reserva reserva) {
        this.reserva = reserva;
        SimpleClientHttpRequestFactory tempo = new SimpleClientHttpRequestFactory();
        tempo.setConnectTimeout(3_000);
        tempo.setReadTimeout(180_000);
        this.http = builder.baseUrl(url).requestFactory(tempo).build();
    }

    public Resposta buscar(String descricao, String unidade) {
        return comReserva(() -> enviar("/similares", Map.of("descricao", descricao, "unidade", unidade, "limite", 10), Resposta.class),
                () -> reserva.buscar(descricao, unidade));
    }

    public Novo adicionar(String codigo, String descricao, String unidade, String grupo) {
        return comReserva(() -> {
            Novo n = enviar("/codigos", Map.of("codigo", codigo, "descricao", descricao, "unidade", unidade,
                    "grupo", grupo == null ? "" : grupo), Novo.class);
            reserva.lembrar(codigo, descricao, unidade);
            return n;
        }, () -> reserva.adicionar(codigo, descricao, unidade, grupo));
    }

    public Planilha lerPlanilha(byte[] arquivo, String nome) {
        return chamar(() -> http.post().uri("/lote/ler")
                .header("X-Arquivo", URLEncoder.encode(nome == null ? "" : nome, StandardCharsets.UTF_8))
                .contentType(MediaType.APPLICATION_OCTET_STREAM).body(arquivo).retrieve().body(Planilha.class));
    }

    public Palavras palavras(String texto) {
        return comReserva(() -> enviar("/palavras", Map.of("texto", texto), Palavras.class), () -> new Palavras(List.of()));
    }

    public Analises analisarLote(List<Map<String, String>> itens) {
        return enviar("/lote", Map.of("itens", itens), Analises.class);
    }

    public void adicionarVarios(List<Map<String, String>> itens) {
        comReserva(() -> {
            enviar("/codigos/lote", Map.of("itens", itens), Map.class);
            itens.stream().filter(m -> m.get("oficial") == null).forEach(m -> reserva.lembrar(m.get("codigo"), m.get("descricao"), m.get("unidade")));
            return null;
        }, () -> {
            reserva.guardar(itens);
            return null;
        });
    }

    public List<Integer> agrupar(List<Map<String, String>> itens) {
        return comReserva(() -> enviar("/agrupar", Map.of("itens", itens), Grupos.class).grupos(), () -> reserva.agrupar(itens));
    }

    private <T> T comReserva(java.util.function.Supplier<T> python, java.util.function.Supplier<T> banco) {
        List<Map<String, String>> pendentes = reserva.pendentes();
        if (!pendentes.isEmpty()) {
            try {
                http.post().uri("/codigos/lote").body(Map.of("itens", pendentes)).retrieve().toBodilessEntity();
                reserva.enviados(pendentes);
            } catch (RuntimeException e) {
                return banco.get();
            }
        }
        try {
            return python.get();
        } catch (ResponseStatusException e) {
            if (e.getStatusCode().value() == HttpStatus.SERVICE_UNAVAILABLE.value()) {
                return banco.get();
            }
            throw e;
        }
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
                    "O serviço de similaridade (Python) está fora do ar. Ligue de novo pelo MOKKA.bat.");
        } catch (RestClientResponseException e) {
            String corpo = e.getResponseBodyAsString();
            String msg = corpo.contains("\"erro\"") ? corpo.replaceAll("(?s).*\"erro\"\\s*:\\s*\"(.*?)\".*", "$1") : corpo;
            throw new ResponseStatusException(e.getStatusCode().is4xxClientError() ? HttpStatus.BAD_REQUEST : HttpStatus.BAD_GATEWAY, msg);
        }
    }

    public record Planilha(Map<String, String> colunas, List<Map<String, Object>> linhas, List<String> relatorio) {
    }

    public record Grupos(List<Integer> grupos) {
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
