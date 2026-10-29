package br.com.squad.codigos.catalogo;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Raio-X", description = "Saúde do catálogo antes e depois do MOKKA, famílias de duplicados e números para estimar a economia")
@RestController
@RequestMapping("/api/raio-x")
public class RaioXController {

    private final JdbcTemplate jdbc;

    public RaioXController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Operation(summary = "Raio-X do catálogo", description = "Nota de 0 a 100 da base original e do catálogo tratado, com os critérios, as maiores famílias de códigos repetidos e os totais por grupo.")
    @GetMapping
    public RaioX raioX() {
        List<String[]> antes = jdbc.query("select descricao, unidade from codigo_servico "
                        + "where motivo is null or (motivo not like 'criado%' and motivo not like 'importado%')",
                (r, i) -> new String[]{r.getString(1), r.getString(2)});
        List<String[]> depois = jdbc.query("select coalesce(descricao_padrao, descricao), unidade from codigo_servico where status = 'ATIVO'",
                (r, i) -> new String[]{r.getString(1), r.getString(2)});
        long baseTotal = antes.size();
        Long repetidosBase = jdbc.queryForObject("select count(*) from codigo_servico where status = 'BLOQUEADO' "
                + "and (motivo is null or (motivo not like 'criado%' and motivo not like 'importado%'))", Long.class);
        Long evitados = jdbc.queryForObject("select count(*) from solicitacao where status in ('USOU_EXISTENTE', 'RECUSADO')", Long.class);
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
                evitados == null ? 0 : evitados, familias, grupos);
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

    public record RaioX(long notaAntes, long notaDepois, List<Criterio> criterios, long codigosNaBase, long repetidosAchados,
                        long duplicadosEvitados, List<Familia> familias, List<PorGrupo> grupos) {
    }
}
