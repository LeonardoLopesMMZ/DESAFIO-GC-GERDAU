package br.com.squad.codigos.sistema;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

import br.com.squad.codigos.consulta.SimilaridadeClient;

@Component
public class Carga implements SmartInitializingSingleton {

    private static final Logger LOG = LoggerFactory.getLogger(Carga.class);
    private static final List<String> TABELAS = List.of("auditoria", "pedido_entrada", "palavra_aprendida", "aprendizado", "retrato", "usuario",
            "solicitacao", "dicionario", "base_original", "codigo_fornecedor", "codigo_servico");
    private static final List<String> COM_ID = List.of("auditoria", "pedido_entrada", "aprendizado", "retrato", "usuario", "solicitacao", "base_original");

    private final DataSource banco;
    private final JdbcTemplate jdbc;
    private final SimilaridadeClient similaridade;
    private final String dados;
    private final boolean zerarAoLigar;
    private String nomeBanco = "?";

    public Carga(DataSource banco, JdbcTemplate jdbc, SimilaridadeClient similaridade,
                 @Value("${mokka.dados:}") String dados, @Value("${mokka.zerar:false}") boolean zerarAoLigar) {
        this.banco = banco;
        this.jdbc = jdbc;
        this.similaridade = similaridade;
        this.dados = dados;
        this.zerarAoLigar = zerarAoLigar;
    }

    @Override
    public void afterSingletonsInstantiated() {
        try {
            try (Connection c = banco.getConnection()) {
                nomeBanco = c.getMetaData().getDatabaseProductName();
            }
            Long codigos = jdbc.queryForObject("select count(*) from codigo_servico", Long.class);
            if (zerarAoLigar || codigos == null || codigos == 0) {
                zerar();
            } else {
                LOG.info("Banco {} já tem {} códigos: mantendo os dados.", nomeBanco, codigos);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Não consegui preparar o banco: " + e.getMessage(), e);
        }
        sincronizar();
    }

    public String banco() {
        return nomeBanco;
    }

    public boolean persistente() {
        return !nomeBanco.toUpperCase().contains("H2");
    }

    public synchronized void zerar() throws SQLException {
        if (dados.isBlank() || !Files.isRegularFile(Path.of(dados))) {
            LOG.warn("Sem arquivo de dados ({}): o catálogo fica vazio.", dados);
            return;
        }
        long inicio = System.currentTimeMillis();
        try (Connection c = banco.getConnection(); Statement st = c.createStatement()) {
            c.setAutoCommit(false);
            try {
                st.execute("update codigo_servico set codigo_canonico = null");
                for (String t : TABELAS) {
                    st.execute("delete from " + t);
                }
                for (String t : COM_ID) {
                    st.execute("alter table " + t + " alter column id restart with 1");
                }
                st.execute("alter sequence codigo_novo restart with 9000001");
                ScriptUtils.executeSqlScript(c, new EncodedResource(new FileSystemResource(dados), StandardCharsets.UTF_8));
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
        LOG.info("Banco {} carregado do zero em {} ms.", nomeBanco, System.currentTimeMillis() - inicio);
    }

    private void sincronizar() {
        List<Map<String, String>> itens = new ArrayList<>();
        jdbc.query("select codigo, descricao, unidade, grupo, status, codigo_canonico from codigo_servico "
                + "where motivo like 'criado%' or motivo like 'importado%' order by codigo", r -> {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("codigo", r.getString(1));
            m.put("descricao", r.getString(2));
            m.put("unidade", r.getString(3));
            m.put("grupo", r.getString(4) == null ? "" : r.getString(4));
            if ("BLOQUEADO".equals(r.getString(5)) && r.getString(6) != null) {
                m.put("oficial", r.getString(6));
            }
            itens.add(m);
        });
        for (int i = 0; i < itens.size(); i += 5000) {
            similaridade.adicionarVarios(itens.subList(i, Math.min(itens.size(), i + 5000)));
        }
        if (!itens.isEmpty()) {
            LOG.info("{} códigos criados pelo MOKKA mandados para a similaridade.", itens.size());
        }
    }
}
