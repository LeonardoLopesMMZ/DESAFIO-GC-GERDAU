package br.com.squad.codigos.acesso;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String nome;
    private String credencialId;
    private String chavePublica;
    private Integer algoritmo;
    private long contador;
    private String rosto;
    private Long retratoId;
    private String perfil = "COMPRADOR";
    private LocalDateTime criadoEm = LocalDateTime.now();

    protected Usuario() {
    }

    public Usuario(String nome) {
        this.nome = nome;
    }

    public void cadastrarChave(String credencialId, String chavePublica, int algoritmo) {
        this.credencialId = credencialId;
        this.chavePublica = chavePublica;
        this.algoritmo = algoritmo;
        this.contador = 0;
    }

    public void atualizarContador(long contador) {
        this.contador = contador;
    }

    public void cadastrarRosto(String rosto, Long retratoId) {
        this.rosto = rosto;
        this.retratoId = retratoId;
    }

    public boolean temRosto() {
        return rosto != null;
    }

    public String getRosto() {
        return rosto;
    }

    public Long getRetratoId() {
        return retratoId;
    }

    public boolean temChave() {
        return credencialId != null;
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getCredencialId() {
        return credencialId;
    }

    public String getChavePublica() {
        return chavePublica;
    }

    public Integer getAlgoritmo() {
        return algoritmo;
    }

    public long getContador() {
        return contador;
    }

    public String getPerfil() {
        return perfil;
    }

    public void mudarPerfil(String perfil) {
        this.perfil = perfil;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }
}
