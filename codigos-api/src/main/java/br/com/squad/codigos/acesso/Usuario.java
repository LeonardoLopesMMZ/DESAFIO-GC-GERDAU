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

    public boolean temChave() {
        return credencialId != null;
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
}
