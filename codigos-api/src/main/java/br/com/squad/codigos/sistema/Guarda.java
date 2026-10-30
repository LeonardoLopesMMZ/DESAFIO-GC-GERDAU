package br.com.squad.codigos.sistema;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import br.com.squad.codigos.acesso.Acesso;

@Component
@Order(-100)
public class Guarda extends OncePerRequestFilter {

    private static final Set<String> LIVRES = Set.of("/api/acesso/entrar", "/api/acesso/chave/desafio", "/api/acesso/chave/entrar",
            "/api/acesso/rosto/entrar", "/api/acesso/entrar-local", "/api/sistema");
    private static final Set<String> ENTRADAS = Set.of("/api/acesso/entrar", "/api/acesso/chave/entrar", "/api/acesso/rosto/entrar",
            "/api/acesso/entrar-local");
    private static final Set<String> LOCAIS = Set.of("127.0.0.1", "0:0:0:0:0:0:0:1", "::1");
    private static final int MAX_FALHAS = 8;
    private static final Duration JANELA_FALHAS = Duration.ofMinutes(5);
    private static final int MAX_PEDIDOS = 600;
    private static final Duration JANELA_PEDIDOS = Duration.ofMinutes(1);
    private static final String CSP = "default-src 'self'; img-src 'self' blob: data:; script-src 'self'; style-src 'self' 'unsafe-inline'; "
            + "connect-src 'self'; media-src 'self' blob:; worker-src 'self' blob:; object-src 'none'; frame-ancestors 'none'; "
            + "base-uri 'self'; form-action 'self'";

    private final Acesso acesso;
    private final Auditoria auditoria;
    private final Map<String, Janela> falhas = new ConcurrentHashMap<>();
    private final Map<String, Janela> pedidos = new ConcurrentHashMap<>();

    public Guarda(Acesso acesso, Auditoria auditoria) {
        this.acesso = acesso;
        this.auditoria = auditoria;
    }

    private static final class Janela {
        private Instant inicio = Instant.now();
        private int vezes;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        String p = req.getRequestURI();
        String ip = req.getRemoteAddr();
        boolean ferramenta = p.startsWith("/swagger") || p.startsWith("/v3/api-docs") || p.startsWith("/h2-console");
        cabecalhos(res, ferramenta);
        if (ferramenta && !LOCAIS.contains(ip)) {
            negar(res, 403, "O Swagger e o console do banco só abrem no computador que liga o MOKKA.");
            return;
        }
        if (!p.startsWith("/api/")) {
            chain.doFilter(req, res);
            return;
        }
        res.setHeader("Cache-Control", "no-store");
        boolean entrada = ENTRADAS.contains(p);
        if (entrada && vezes(falhas, ip, JANELA_FALHAS, false) >= MAX_FALHAS) {
            negar(res, 429, "Muitas tentativas de entrar daqui. Espere 5 minutos ou peça a um administrador para desbloquear.");
            return;
        }
        if (vezes(pedidos, ip, JANELA_PEDIDOS, true) > MAX_PEDIDOS) {
            negar(res, 429, "Muitos pedidos seguidos. Espere um minuto.");
            return;
        }
        if (!LIVRES.contains(p)) {
            Acesso.Sessao s = acesso.sessao(req.getHeader("Authorization"));
            if (s == null) {
                negar(res, 401, "Entre no sistema para continuar.");
                return;
            }
            String precisa = perfilExigido(p);
            if (precisa != null && !Acesso.pode(s, precisa)) {
                negar(res, 403, "ADMIN".equals(precisa) ? "Só administradores podem fazer isso." : "Só curadores podem fazer isso.");
                return;
            }
        }
        chain.doFilter(req, res);
        if (entrada && res.getStatus() >= 400) {
            int n = vezes(falhas, ip, JANELA_FALHAS, true);
            auditoria.registrar(null, n >= MAX_FALHAS ? "BLOQUEIO_FORCA_BRUTA" : "ENTRADA_RECUSADA", p, "status " + res.getStatus() + ", tentativa " + n);
        }
    }

    static String perfilExigido(String p) {
        if (p.startsWith("/api/admin/")) {
            return "ADMIN";
        }
        if (p.startsWith("/api/solicitacoes/curador") || p.matches("/api/solicitacoes/\\d+/curadoria")
                || p.startsWith("/api/aprendizado/palavras/") || p.startsWith("/api/aprendizado/regras/")) {
            return "CURADOR";
        }
        return null;
    }

    public void desbloquear() {
        falhas.clear();
        pedidos.clear();
    }

    private static int vezes(Map<String, Janela> mapa, String ip, Duration janela, boolean somar) {
        Instant agora = Instant.now();
        if (mapa.size() > 10_000) {
            mapa.values().removeIf(j -> j.inicio.plus(janela).isBefore(agora));
        }
        Janela j = mapa.computeIfAbsent(ip, k -> new Janela());
        synchronized (j) {
            if (j.inicio.plus(janela).isBefore(agora)) {
                j.inicio = agora;
                j.vezes = 0;
            }
            if (somar) {
                j.vezes++;
            }
            return j.vezes;
        }
    }

    private static void cabecalhos(HttpServletResponse res, boolean ferramenta) {
        res.setHeader("X-Content-Type-Options", "nosniff");
        res.setHeader("Referrer-Policy", "no-referrer");
        res.setHeader("Permissions-Policy", "camera=(self), microphone=(self), geolocation=(), payment=(), usb=()");
        res.setHeader("Cross-Origin-Opener-Policy", "same-origin");
        if (!ferramenta) {
            res.setHeader("X-Frame-Options", "DENY");
            res.setHeader("Content-Security-Policy", CSP);
        } else {
            res.setHeader("X-Frame-Options", "SAMEORIGIN");
        }
    }

    private static void negar(HttpServletResponse res, int status, String msg) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json;charset=UTF-8");
        res.getOutputStream().write(("{\"status\":" + status + ",\"erro\":\"" + msg + "\"}").getBytes(StandardCharsets.UTF_8));
    }
}
