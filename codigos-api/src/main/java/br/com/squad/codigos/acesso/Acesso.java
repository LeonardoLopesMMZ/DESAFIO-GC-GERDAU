package br.com.squad.codigos.acesso;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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
import java.util.List;
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
    private final RetratoRepository retratos;
    private final ObjectMapper json = new ObjectMapper();
    private final boolean soChave;

    public static final double LIMITE_ROSTO = 0.5;
    private static final double ROSTO_DE_OUTRO = 0.45;
    private static final int TAMANHO_DESCRITOR = 128;
    private static final int MAX_FOTO = 20_000;

    public Acesso(UsuarioRepository usuarios, RetratoRepository retratos, @Value("${mokka.so-chave:false}") boolean soChave) {
        this.usuarios = usuarios;
        this.retratos = retratos;
        this.soChave = soChave;
    }

    public record Sessao(String token, String nome, String metodo, Long retratoId, Instant expira) {
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

    public Usuario usuario(String nome) {
        return usuarios.findByNomeIgnoreCase(nome).orElse(null);
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
                    "Esse nome tem chave do Windows cadastrada. Use Entrar com a chave do Windows.");
        }
        if (u.temRosto()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Esse nome tem rosto cadastrado. Use Entrar com o rosto.");
        }
        return nova(u.getNome(), "NOME", null);
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
        return nova(u.getNome(), "CHAVE", null);
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

    @Transactional
    public void cadastrarRosto(Sessao s, List<Double> descritor, String foto) {
        float[] d = descritor(descritor);
        byte[] jpg = foto(foto);
        for (Usuario outro : usuarios.findByRostoIsNotNull()) {
            if (!outro.getNome().equalsIgnoreCase(s.nome()) && distancia(d, ler(outro.getRosto())) < ROSTO_DE_OUTRO) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Esse rosto já está cadastrado para " + outro.getNome() + ".");
            }
        }
        Retrato r = retratos.save(new Retrato(s.nome(), "CADASTRO", null, jpg));
        Usuario u = usuarios.findByNomeIgnoreCase(s.nome()).orElseGet(() -> new Usuario(s.nome()));
        u.cadastrarRosto(escrever(d), r.getId());
        usuarios.save(u);
    }

    @Transactional
    public Sessao entrarComRosto(List<Double> descritor, String foto) {
        float[] d = descritor(descritor);
        byte[] jpg = foto(foto);
        Usuario melhor = null;
        double menor = Double.MAX_VALUE;
        for (Usuario u : usuarios.findByRostoIsNotNull()) {
            double dist = distancia(d, ler(u.getRosto()));
            if (dist < menor) {
                menor = dist;
                melhor = u;
            }
        }
        if (melhor == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Ninguém cadastrou o rosto ainda. Entre pelo nome e cadastre o seu.");
        }
        if (menor > LIMITE_ROSTO) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Não reconheci o rosto. Olhe de frente, com luz no rosto, e tente de novo.");
        }
        Retrato r = retratos.save(new Retrato(melhor.getNome(), "ENTRADA", Math.round(menor * 1000) / 1000.0, jpg));
        return nova(melhor.getNome(), "ROSTO", r.getId());
    }

    public byte[] retrato(long id) {
        return retratos.findById(id).map(Retrato::getFoto)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Foto não encontrada."));
    }

    static double distancia(float[] a, float[] b) {
        double soma = 0;
        for (int i = 0; i < a.length; i++) {
            double x = a[i] - b[i];
            soma += x * x;
        }
        return Math.sqrt(soma);
    }

    private static float[] descritor(List<Double> v) {
        if (v == null || v.size() != TAMANHO_DESCRITOR) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Leitura do rosto inválida.");
        }
        float[] d = new float[TAMANHO_DESCRITOR];
        for (int i = 0; i < d.length; i++) {
            Double x = v.get(i);
            if (x == null || !Double.isFinite(x) || Math.abs(x) > 2) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Leitura do rosto inválida.");
            }
            d[i] = x.floatValue();
        }
        return d;
    }

    private static byte[] foto(String base64) {
        byte[] b;
        try {
            b = Base64.getDecoder().decode(base64.replaceFirst("^data:image/jpeg;base64,", ""));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Foto inválida.");
        }
        if (b.length < 4 || b.length > MAX_FOTO || (b[0] & 0xff) != 0xFF || (b[1] & 0xff) != 0xD8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A foto deve ser um JPEG de até 20 KB.");
        }
        return b;
    }

    private static String escrever(float[] d) {
        ByteBuffer b = ByteBuffer.allocate(d.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float x : d) {
            b.putFloat(x);
        }
        return B64.encodeToString(b.array());
    }

    private static float[] ler(String s) {
        ByteBuffer b = ByteBuffer.wrap(D64.decode(s)).order(ByteOrder.LITTLE_ENDIAN);
        float[] d = new float[TAMANHO_DESCRITOR];
        for (int i = 0; i < d.length; i++) {
            d[i] = b.getFloat();
        }
        return d;
    }

    private Sessao nova(String nome, String metodo, Long retratoId) {
        sessoes.values().removeIf(s -> s.expira().isBefore(Instant.now()));
        Sessao s = new Sessao(B64.encodeToString(bytes(32)), nome, metodo, retratoId, Instant.now().plus(VALIDADE));
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
