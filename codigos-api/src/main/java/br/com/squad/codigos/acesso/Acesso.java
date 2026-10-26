package br.com.squad.codigos.acesso;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Component
public class Acesso {

    public static final Duration VALIDADE = Duration.ofHours(8);
    private static final Duration VALIDADE_DESAFIO = Duration.ofMinutes(3);
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder D64 = Base64.getUrlDecoder();

    private final SecureRandom aleatorio = new SecureRandom();
    private final Map<String, Sessao> sessoes = new ConcurrentHashMap<>();
    private final Map<String, Instant> desafios = new ConcurrentHashMap<>();
    private final UsuarioRepository usuarios;
    private final ObjectMapper json = new ObjectMapper();
    private final boolean soChave;

    public Acesso(UsuarioRepository usuarios, @Value("${mokka.so-chave:false}") boolean soChave) {
        this.usuarios = usuarios;
        this.soChave = soChave;
    }

    public record Sessao(String token, String nome, String metodo, Instant expira) {
    }

    public Sessao exigir(String autorizacao) {
        String token = autorizacao == null ? "" : autorizacao.replaceFirst("(?i)^Bearer\\s+", "").trim();
        Sessao s = sessoes.get(token);
        if (s == null || s.expira().isBefore(Instant.now())) {
            sessoes.remove(token);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Entre no sistema para continuar.");
        }
        return s;
    }

    public void sair(String autorizacao) {
        sessoes.remove(autorizacao == null ? "" : autorizacao.replaceFirst("(?i)^Bearer\\s+", "").trim());
    }

    public boolean temChave(String nome) {
        return usuarios.findByNomeIgnoreCase(nome).map(Usuario::temChave).orElse(false);
    }

    public boolean soChave() {
        return soChave;
    }

    @Transactional
    public Sessao entrarComNome(String nome) {
        if (soChave) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Aqui a entrada é só com chave de acesso (rosto, digital ou PIN).");
        }
        Usuario u = usuarios.findByNomeIgnoreCase(nome).orElseGet(() -> usuarios.save(new Usuario(nome)));
        if (u.temChave()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Esse nome tem rosto ou digital cadastrado. Use Entrar com chave de acesso.");
        }
        return nova(u.getNome(), "NOME");
    }

    public String desafio() {
        desafios.values().removeIf(v -> v.isBefore(Instant.now()));
        String d = B64.encodeToString(bytes(32));
        desafios.put(d, Instant.now().plus(VALIDADE_DESAFIO));
        return d;
    }

    public static String idDoUsuario(String nome) {
        return B64.encodeToString(Arrays.copyOf(sha256(nome.toLowerCase().getBytes(StandardCharsets.UTF_8)), 16));
    }

    @Transactional
    public void cadastrarChave(Sessao s, String credencialId, String chavePublica, int algoritmo, String clientDataJson, String origem) {
        conferirCliente(D64.decode(clientDataJson), "webauthn.create", origem);
        if (algoritmo != -7 && algoritmo != -257) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de chave não aceito.");
        }
        chave(chavePublica, algoritmo);
        usuarios.findByCredencialId(credencialId).filter(u -> !u.getNome().equalsIgnoreCase(s.nome())).ifPresent(u -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Essa chave já é de outra pessoa.");
        });
        Usuario u = usuarios.findByNomeIgnoreCase(s.nome()).orElseGet(() -> new Usuario(s.nome()));
        u.cadastrarChave(credencialId, chavePublica, algoritmo);
        usuarios.save(u);
    }

    @Transactional
    public Sessao entrarComChave(String credencialId, String clientDataJson, String dadosAutenticador, String assinatura, String origem) {
        byte[] cliente = D64.decode(clientDataJson);
        String origemCliente = conferirCliente(cliente, "webauthn.get", origem);
        Usuario u = usuarios.findByCredencialId(credencialId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Essa chave não está cadastrada aqui."));
        byte[] auth = D64.decode(dadosAutenticador);
        if (auth.length < 37 || !MessageDigest.isEqual(Arrays.copyOf(auth, 32), sha256(URI.create(origemCliente).getHost().getBytes(StandardCharsets.UTF_8)))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "A chave é de outro site.");
        }
        if ((auth[32] & 0x01) == 0 || (auth[32] & 0x04) == 0) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "A chave não confirmou que é você (rosto, digital ou PIN).");
        }
        try {
            Signature sig = Signature.getInstance(u.getAlgoritmo() == -7 ? "SHA256withECDSA" : "SHA256withRSA");
            sig.initVerify(chave(u.getChavePublica(), u.getAlgoritmo()));
            sig.update(auth);
            sig.update(sha256(cliente));
            if (!sig.verify(D64.decode(assinatura))) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Assinatura inválida.");
            }
        } catch (GeneralSecurityException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Assinatura inválida.");
        }
        long contador = ((auth[33] & 0xffL) << 24) | ((auth[34] & 0xffL) << 16) | ((auth[35] & 0xffL) << 8) | (auth[36] & 0xffL);
        if ((contador != 0 || u.getContador() != 0) && contador <= u.getContador()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Essa chave parece copiada. Cadastre de novo.");
        }
        u.atualizarContador(contador);
        usuarios.save(u);
        return nova(u.getNome(), "CHAVE");
    }

    private String conferirCliente(byte[] cliente, String tipo, String origem) {
        JsonNode c;
        try {
            c = json.readTree(cliente);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Resposta da chave inválida.");
        }
        Instant validade = desafios.remove(c.path("challenge").asText(""));
        if (!tipo.equals(c.path("type").asText()) || validade == null || validade.isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Desafio vencido ou inválido. Tente de novo.");
        }
        String o = c.path("origin").asText("");
        URI uri = URI.create(o);
        boolean seguro = "https".equals(uri.getScheme()) || "localhost".equals(uri.getHost());
        if (!seguro || (origem != null && !origem.equals(o))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Origem não permitida.");
        }
        return o;
    }

    private static PublicKey chave(String spki, int algoritmo) {
        try {
            return KeyFactory.getInstance(algoritmo == -7 ? "EC" : "RSA").generatePublic(new X509EncodedKeySpec(D64.decode(spki)));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chave pública inválida.");
        }
    }

    private Sessao nova(String nome, String metodo) {
        sessoes.values().removeIf(s -> s.expira().isBefore(Instant.now()));
        Sessao s = new Sessao(B64.encodeToString(bytes(32)), nome, metodo, Instant.now().plus(VALIDADE));
        sessoes.put(s.token(), s);
        return s;
    }

    private byte[] bytes(int n) {
        byte[] b = new byte[n];
        aleatorio.nextBytes(b);
        return b;
    }

    private static byte[] sha256(byte[] dados) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(dados);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
