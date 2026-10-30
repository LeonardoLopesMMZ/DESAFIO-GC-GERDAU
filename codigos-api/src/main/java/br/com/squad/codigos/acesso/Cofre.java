package br.com.squad.codigos.acesso;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class Cofre {

    private static final Logger LOG = LoggerFactory.getLogger(Cofre.class);
    private static final byte[] MARCA = {'M', 'K', '1'};

    private final SecureRandom aleatorio = new SecureRandom();
    private final SecretKeySpec chave;

    public Cofre(@Value("${mokka.chave-fotos:../.ferramentas/chave-fotos.key}") String arquivo) {
        byte[] k = null;
        Path p = Path.of(arquivo);
        try {
            if (Files.isRegularFile(p) && Files.size(p) == 32) {
                k = Files.readAllBytes(p);
            } else {
                k = new byte[32];
                aleatorio.nextBytes(k);
                Files.createDirectories(p.toAbsolutePath().getParent());
                Files.write(p, k);
                try {
                    Files.setPosixFilePermissions(p, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
                } catch (UnsupportedOperationException e) {
                    LOG.debug("Sem permissões POSIX em {}", p);
                }
            }
        } catch (IOException e) {
            LOG.warn("Não consegui guardar a chave das fotos em {}: as fotos valem só até desligar.", p);
        }
        this.chave = new SecretKeySpec(k, "AES");
    }

    public byte[] trancar(byte[] dados) {
        byte[] iv = new byte[12];
        aleatorio.nextBytes(iv);
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, chave, new GCMParameterSpec(128, iv));
            byte[] x = c.doFinal(dados);
            return ByteBuffer.allocate(MARCA.length + iv.length + x.length).put(MARCA).put(iv).put(x).array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public byte[] abrir(byte[] dados) {
        if (dados.length < 3 || !Arrays.equals(Arrays.copyOf(dados, 3), MARCA)) {
            return dados;
        }
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, chave, new GCMParameterSpec(128, dados, 3, 12));
            return c.doFinal(dados, 15, dados.length - 15);
        } catch (GeneralSecurityException e) {
            return null;
        }
    }
}
