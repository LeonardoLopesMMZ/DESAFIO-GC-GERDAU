package br.com.squad.codigos.aprendizado;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import br.com.squad.codigos.catalogo.CodigoServico;
import br.com.squad.codigos.catalogo.CodigoServicoRepository;
import br.com.squad.codigos.consulta.SimilaridadeClient.Candidato;
import br.com.squad.codigos.consulta.SimilaridadeClient.Resposta;

@Component
public class Aprendizado {

    public static final String IGUAL = "IGUAL";
    public static final String DIFERENTE = "DIFERENTE";

    private final JdbcTemplate jdbc;
    private final CodigoServicoRepository codigos;

    public Aprendizado(JdbcTemplate jdbc, CodigoServicoRepository codigos) {
        this.jdbc = jdbc;
        this.codigos = codigos;
    }

    public record Regra(long id, String descricao, String unidade, String codigo, String tipo, int votos, String ultimoPor,
                        LocalDateTime ultimoEm) {
    }

    public record Palavra(String termo, String correcao, int votos, String status, String ultimoPor, LocalDateTime ultimoEm) {
    }

    public record Sugestao(String acao, int certeza, String motivo) {
    }

    public static String simples(String texto) {
        return Normalizer.normalize(texto == null ? "" : texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toUpperCase()
                .replaceAll("[^A-Z0-9%]+", " ").trim();
    }

    public static String chave(String descricao, String unidade) {
        TreeSet<String> palavras = new TreeSet<>(Arrays.asList(simples(descricao).split(" ")));
        palavras.remove("SV");
        palavras.remove("");
        String k = String.join(" ", palavras) + "|" + simples(unidade);
        return k.length() > 220 ? k.substring(0, 220) : k;
    }

    public void votar(String descricao, String unidade, String codigo, String tipo, String quem, int delta) {
        if (codigo == null || codigo.isBlank()) {
            return;
        }
        String k = chave(descricao, unidade);
        int n = jdbc.update("update aprendizado set votos = votos + ?, ultimo_por = ?, ultimo_em = current_timestamp "
                + "where chave = ? and codigo = ? and tipo = ?", delta, quem, k, codigo, tipo);
        if (n == 0 && delta > 0) {
            jdbc.update("insert into aprendizado (chave, descricao, unidade, codigo, tipo, votos, ultimo_por) values (?, ?, ?, ?, ?, ?, ?)",
                    k, corta(simples(descricao), 60), corta(simples(unidade), 5), codigo, tipo, delta, quem);
        }
        jdbc.update("delete from aprendizado where votos <= 0");
    }

    private Regra maisVotada(String chave, String tipo, String codigo) {
        List<Regra> r = jdbc.query("select id, descricao, unidade, codigo, tipo, votos, ultimo_por, ultimo_em from aprendizado "
                        + "where chave = ? and tipo = ? and votos > 0" + (codigo == null ? "" : " and codigo = ?") + " order by votos desc, ultimo_em desc limit 1",
                (x, i) -> regra(x), codigo == null ? new Object[]{chave, tipo} : new Object[]{chave, tipo, codigo});
        return r.isEmpty() ? null : r.get(0);
    }

    public Resposta aplicar(String descricao, String unidade, Resposta r) {
        String k = chave(descricao, unidade);
        List<Candidato> c = new ArrayList<>(r.candidatos());
        Regra igual = maisVotada(k, IGUAL, null);
        if (igual != null) {
            CodigoServico cod = oficial(igual.codigo());
            if (cod != null) {
                Candidato achado = c.stream().filter(x -> x.codigo().equals(cod.getCodigo())).findFirst().orElse(null);
                c.remove(achado);
                c.add(0, new Candidato(cod.getCodigo(), cod.getDescricao(), cod.getDescricaoPadrao(), cod.getUnidade(), cod.getCategoria(),
                        achado == null ? 100 : achado.aderencia(), "DUPLICADO", List.of("aprendido com " + vezes(igual.votos())), null));
                return new Resposta("BLOQUEAR", "O MOKKA aprendeu com " + vezes(igual.votos()) + ": para essa descrição, use o código "
                        + cod.getCodigo(), c, r.sugestao());
            }
        }
        if ("BLOQUEAR".equals(r.resultado()) && !c.isEmpty()) {
            Regra dif = maisVotada(k, DIFERENTE, c.get(0).codigo());
            if (dif != null) {
                return new Resposta("REVISAR", "O curador já decidiu que isso é diferente do código " + c.get(0).codigo()
                        + " (" + vez(dif.votos()) + "). Confira e, se for o caso, crie", c, r.sugestao());
            }
        }
        return r;
    }

    public Sugestao sugerir(String descricao, String unidade, String existente, String justificativa, int pessoas) {
        String k = chave(descricao, unidade);
        Regra igual = maisVotada(k, IGUAL, null);
        if (igual != null) {
            return new Sugestao("RECUSAR", Math.min(99, 75 + 8 * igual.votos()),
                    "Já decidiram " + vez(igual.votos()) + " que é o mesmo serviço do código " + igual.codigo());
        }
        Regra dif = existente == null ? null : maisVotada(k, DIFERENTE, existente);
        if (dif != null) {
            return new Sugestao("APROVAR", Math.min(99, 75 + 8 * dif.votos()),
                    "O curador já aprovou isso como diferente do código " + existente + " (" + vez(dif.votos()) + ")");
        }
        CodigoServico cod = existente == null ? null : codigos.findById(existente).orElse(null);
        if (cod != null && chave(cod.getDescricao(), cod.getUnidade()).equals(k)) {
            return new Sugestao("RECUSAR", 95, "Está escrito igual ao código " + existente + ", só muda a ordem ou a pontuação");
        }
        if (pessoas >= 2 && justificativa != null && !justificativa.isBlank()) {
            return new Sugestao("APROVAR", 65, pessoas + " pessoas pediram e justificaram: pode ser um serviço novo de verdade");
        }
        return new Sugestao("RECUSAR", 70, "O catálogo tem um código quase igual" + (existente == null ? "" : " (" + existente + ")")
                + ". Aprove só se a justificativa mostrar diferença");
    }

    public void palavra(String de, String para, String quem) {
        String termo = corta(simples(de), 40);
        String correcao = corta(simples(para), 40);
        if (termo.isEmpty() || correcao.isEmpty() || termo.equals(correcao)) {
            return;
        }
        int n = jdbc.update("update palavra_aprendida set votos = votos + 1, ultimo_por = ?, ultimo_em = current_timestamp "
                + "where termo = ? and correcao = ?", quem, termo, correcao);
        if (n == 0) {
            n = jdbc.update("update palavra_aprendida set correcao = ?, votos = 1, ultimo_por = ?, ultimo_em = current_timestamp "
                    + "where termo = ? and status <> 'ACEITA'", correcao, quem, termo);
        }
        if (n == 0 && jdbc.queryForObject("select count(*) from palavra_aprendida where termo = ?", Long.class, termo) == 0) {
            jdbc.update("insert into palavra_aprendida (termo, correcao, votos, ultimo_por) values (?, ?, 1, ?)", termo, correcao, quem);
        }
    }

    public boolean decidirPalavra(String termo, boolean aceitar, String quem) {
        return jdbc.update("update palavra_aprendida set status = ?, ultimo_por = ?, ultimo_em = current_timestamp where termo = ?",
                aceitar ? "ACEITA" : "RECUSADA", quem, simples(termo)) > 0;
    }

    public Map<String, String> aceitas() {
        Map<String, String> m = new LinkedHashMap<>();
        jdbc.query("select termo, correcao from palavra_aprendida where status = 'ACEITA'", x -> {
            m.put(x.getString(1), x.getString(2));
        });
        return m;
    }

    public List<Regra> regras() {
        return jdbc.query("select id, descricao, unidade, codigo, tipo, votos, ultimo_por, ultimo_em from aprendizado "
                + "order by votos desc, ultimo_em desc limit 100", (x, i) -> regra(x));
    }

    public List<Palavra> palavras() {
        return jdbc.query("select termo, correcao, votos, status, ultimo_por, ultimo_em from palavra_aprendida "
                        + "order by case status when 'SUGERIDA' then 0 when 'ACEITA' then 1 else 2 end, votos desc limit 100",
                (x, i) -> new Palavra(x.getString(1), x.getString(2), x.getInt(3), x.getString(4), x.getString(5),
                        x.getTimestamp(6).toLocalDateTime()));
    }

    public boolean esquecer(long id) {
        return jdbc.update("delete from aprendizado where id = ?", id) > 0;
    }

    private CodigoServico oficial(String codigo) {
        CodigoServico c = codigos.findById(codigo).orElse(null);
        if (c != null && "BLOQUEADO".equals(c.getStatus()) && c.getCodigoCanonico() != null) {
            c = codigos.findById(c.getCodigoCanonico()).orElse(null);
        }
        return c;
    }

    private static Regra regra(java.sql.ResultSet x) throws java.sql.SQLException {
        return new Regra(x.getLong(1), x.getString(2), x.getString(3), x.getString(4), x.getString(5), x.getInt(6), x.getString(7),
                x.getTimestamp(8).toLocalDateTime());
    }

    private static String vez(int n) {
        return n == 1 ? "1 vez" : n + " vezes";
    }

    private static String vezes(int n) {
        return n == 1 ? "1 decisão" : n + " decisões";
    }

    private static String corta(String s, int n) {
        return s.length() <= n ? s : s.substring(0, n).trim();
    }
}
