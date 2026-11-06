package br.com.squad.codigos.sistema;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class Auditoria {

    private static final Logger LOG = LoggerFactory.getLogger(Auditoria.class);

    private final JdbcTemplate jdbc;

    public Auditoria(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void registrar(String quem, String acao, String alvo, String detalhe) {
        try {
            jdbc.update("insert into auditoria (quem, acao, alvo, detalhe, ip) values (?, ?, ?, ?, ?)",
                    corta(quem, 60), acao, corta(alvo, 80), corta(detalhe, 300), ip());
        } catch (DataAccessException e) {
            LOG.warn("Auditoria não gravou {}: {}", acao, e.getMessage());
        }
    }

    public List<Map<String, Object>> ultimos() {
        return jdbc.queryForList("select id, quando, quem, acao, alvo, detalhe, ip from auditoria order by id desc limit 200");
    }

    public static String ip() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes a ? Guarda.ip(a.getRequest()) : null;
    }

    private static String corta(String s, int n) {
        return s == null || s.length() <= n ? s : s.substring(0, n);
    }
}
