package br.com.squad.codigos.acesso;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Retrato {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String nome;
    private String tipo;
    private Double distancia;
    private byte[] foto;
    private LocalDateTime tiradoEm = LocalDateTime.now();

    protected Retrato() {
    }

    public Retrato(String nome, String tipo, Double distancia, byte[] foto) {
        this.nome = nome;
        this.tipo = tipo;
        this.distancia = distancia;
        this.foto = foto;
    }

    public Long getId() {
        return id;
    }

    public byte[] getFoto() {
        return foto;
    }
}
