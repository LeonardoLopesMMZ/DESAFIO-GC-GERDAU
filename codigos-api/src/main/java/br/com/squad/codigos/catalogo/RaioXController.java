package br.com.squad.codigos.catalogo;

import br.com.squad.codigos.acesso.Acesso;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Raio-X", description = "Saúde do catálogo antes e depois do MOKKA, famílias de duplicados e o ganho no serviço")
@RestController
@RequestMapping("/api/raio-x")
public class RaioXController {

    private final JdbcTemplate jdbc;
    private final Acesso acesso;

    public RaioXController(JdbcTemplate jdbc, Acesso acesso) {
        this.jdbc = jdbc;
        this.acesso = acesso;
    }

    @Operation(summary = "Raio-X do catálogo", description = "Nota de 0 a 100 da base original e do catálogo tratado, com os critérios, as maiores famílias de códigos repetidos e os totais por grupo.")
    @GetMapping
    public RaioX raioX(@Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String auth) {
        acesso.exigirPerfil(auth, "CURADOR");
        List<String[]> antes = jdbc.query("select descricao, unidade from codigo_servico "
                        + "where motivo is null or (motivo not like 'criado%' and motivo not like 'importado%')",
                (r, i) -> new String[]{r.getString(1), r.getString(2)});
        List<String[]> depois = jdbc.query("select coalesce(descricao_padrao, descricao), unidade from codigo_servico where status = 'ATIVO'",
                (r, i) -> new String[]{r.getString(1), r.getString(2)});
        long baseTotal = antes.size();
        Long repetidosBase = jdbc.queryForObject("select count(*) from codigo_servico where status = 'BLOQUEADO' "
                + "and (motivo is null or (motivo not like 'criado%' and motivo not like 'importado%'))", Long.class);
        Long evitados = jdbc.queryForObject("select count(*) from solicitacao where status in ('USOU_EXISTENTE', 'RECUSADO')", Long.class);
        Long aprendidas = jdbc.queryForObject("select coalesce(sum(votos), 0) from aprendizado", Long.class);
        long repetidos = repetidosBase == null ? 0 : repetidosBase;

        List<Criterio> criterios = new ArrayList<>();
        criterios.add(new Criterio("Sem código repetido", 40, pct(baseTotal - repetidos, baseTotal), 100));
        criterios.add(criterio("Maiúsculas, sem espaço sobrando", 15, antes, depois, d -> d.equals(d.trim().toUpperCase()) && !d.contains("  ")));
        criterios.add(criterio("Sem \"SV\" ou traço na frente", 15, antes, depois, d -> !d.trim().toUpperCase().matches("^(SV\\b|SV-|-).*")));
        criterios.add(criterio("Cabe nos 40 caracteres do SAP", 15, antes, depois, d -> d.trim().length() <= 40));
        criterios.add(criterio("Sem pontuação ou símbolo solto", 15, antes, depois, d -> !d.matches(".*[.()\\[\\]$;:!?*\"'].*") && !d.trim().endsWith("-")));
        double notaAntes = 0;
        double notaDepois = 0;
        for (Criterio c : criterios) {
            notaAntes += c.peso() * c.antes() / 100.0;
            notaDepois += c.peso() * c.depois() / 100.0;
        }

        List<Familia> familias = jdbc.query("select o.codigo, o.descricao, o.unidade, o.grupo, count(r.codigo) n from codigo_servico r "
                        + "join codigo_servico o on o.codigo = r.codigo_canonico group by o.codigo, o.descricao, o.unidade, o.grupo "
                        + "order by n desc, o.codigo limit 60",
                (r, i) -> new Familia(r.getString(1), r.getString(2), r.getString(3), r.getString(4), r.getInt(5)));
        List<PorGrupo> grupos = jdbc.query("select grupo, count(*), sum(case when status = 'BLOQUEADO' then 1 else 0 end) from codigo_servico "
                        + "group by grupo order by count(*) desc",
                (r, i) -> new PorGrupo(r.getString(1), r.getLong(2), r.getLong(3)));
        return new RaioX(Math.round(notaAntes), Math.round(notaDepois), criterios, baseTotal, repetidos,
                evitados == null ? 0 : evitados, aprendidas == null ? 0 : aprendidas, familias, grupos);
    }

    @Operation(summary = "Painel do MOKKA", description = "Tudo o que acontece no MOKKA: consultas por dia e por resultado, decisões, códigos criados, curador, aprendizado, pessoas e as consultas mais recentes.")
    @GetMapping("/painel")
    public Painel painel(@Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String auth) {
        acesso.exigirPerfil(auth, "CURADOR");
        LocalDate hoje = LocalDate.now();
        Date inicio = Date.valueOf(hoje.minusDays(13));
        Map<String, Long> resultados = contagem("select resultado, count(*) from solicitacao where resultado is not null group by resultado");
        Map<String, Long> status = contagem("select status, count(*) from solicitacao group by status");
        Map<String, Long> metodos = contagem("select coalesce(metodo, 'NOME'), count(*) from solicitacao group by coalesce(metodo, 'NOME')");
        Map<String, Long> perfis = contagem("select perfil, count(*) from usuario group by perfil");
        Map<LocalDate, long[]> dias = new LinkedHashMap<>();
        for (int i = 13; i >= 0; i--) dias.put(hoje.minusDays(i), new long[3]);
        jdbc.query("select cast(criada_em as date), resultado, count(*) from solicitacao where criada_em >= ? group by cast(criada_em as date), resultado",
                r -> {
                    long[] d = dias.get(r.getDate(1).toLocalDate());
                    int k = "BLOQUEAR".equals(r.getString(2)) ? 0 : "REVISAR".equals(r.getString(2)) ? 1 : 2;
                    if (d != null) d[k] += r.getLong(3);
                }, inicio);
        List<Dia> porDia = dias.entrySet().stream().map(e -> new Dia(e.getKey().toString(), e.getValue()[0], e.getValue()[1], e.getValue()[2])).toList();
        List<Contado> maisConsultados = jdbc.query("select descricao_digitada, count(*) n from solicitacao group by descricao_digitada order by n desc, descricao_digitada limit 6",
                (r, i) -> new Contado(r.getString(1), r.getLong(2)));
        List<Recente> recentes = jdbc.query("select descricao_digitada, unidade, resultado, status, solicitante, criada_em from solicitacao order by id desc limit 8",
                (r, i) -> new Recente(r.getString(1), r.getString(2), r.getString(3), r.getString(4), primeiroNome(r.getString(5)), String.valueOf(r.getTimestamp(6).toInstant())));
        return new Painel(
                numero("select count(*) from solicitacao"),
                numero("select count(*) from solicitacao where criada_em >= ?", Date.valueOf(hoje)),
                resultados, status, porDia,
                numero("select count(*) from codigo_servico where motivo like 'criado%'"),
                numero("select count(*) from codigo_servico where motivo like 'importado%'"),
                numero("select count(*) from aprendizado"),
                numero("select coalesce(sum(votos), 0) from aprendizado"),
                numero("select count(*) from usuario"),
                perfis, metodos, maisConsultados, recentes);
    }

    private Map<String, Long> contagem(String sql) {
        Map<String, Long> m = new LinkedHashMap<>();
        jdbc.query(sql, r -> { m.put(r.getString(1), r.getLong(2)); });
        return m;
    }

    private long numero(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }

    private static String primeiroNome(String nome) {
        return nome == null ? "" : nome.trim().split("\\s+")[0];
    }

    private static Criterio criterio(String nome, int peso, List<String[]> antes, List<String[]> depois, Predicate<String> ok) {
        return new Criterio(nome, peso, pct(antes.stream().filter(x -> x[0] != null && ok.test(x[0])).count(), antes.size()),
                pct(depois.stream().filter(x -> x[0] != null && ok.test(x[0])).count(), depois.size()));
    }

    private static int pct(long parte, long total) {
        return total == 0 ? 100 : (int) Math.round(100.0 * parte / total);
    }

    public record Criterio(String nome, int peso, int antes, int depois) {
    }

    public record Familia(String codigo, String descricao, String unidade, String grupo, int repetidos) {
    }

    public record PorGrupo(String grupo, long codigos, long repetidos) {
    }

    public record Dia(String dia, long bloquear, long revisar, long liberar) {
    }

    public record Contado(String descricao, long vezes) {
    }

    public record Recente(String descricao, String unidade, String resultado, String status, String quem, String quando) {
    }

    public record Painel(long consultas, long consultasHoje, Map<String, Long> porResultado, Map<String, Long> porStatus, List<Dia> porDia,
                         long codigosCriados, long codigosImportados, long regrasAprendidas, long votos, long pessoas,
                         Map<String, Long> porPerfil, Map<String, Long> porEntrada, List<Contado> maisConsultados, List<Recente> recentes) {
    }

    public record RaioX(long notaAntes, long notaDepois, List<Criterio> criterios, long codigosNaBase, long repetidosAchados,
                        long duplicadosEvitados, long decisoesAprendidas, List<Familia> familias, List<PorGrupo> grupos) {
    }
}
