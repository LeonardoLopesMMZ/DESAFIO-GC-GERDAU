package br.com.squad.codigos.solicitacao;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Solicitacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String descricaoDigitada;
    private String unidade;
    private String grupo;
    private String solicitante;
    private String resultado;
    private String codigoSugerido;
    private String codigoEscolhido;
    private String candidatos;
    private String justificativa;
    private String status = "PENDENTE";
    private LocalDateTime criadaEm = LocalDateTime.now();

    protected Solicitacao() {
    }

    public Solicitacao(String descricaoDigitada, String unidade, String grupo, String solicitante,
                       String resultado, String codigoSugerido, String candidatos) {
        this.descricaoDigitada = descricaoDigitada;
        this.unidade = unidade;
        this.grupo = grupo;
        this.solicitante = solicitante;
        this.resultado = resultado;
        this.codigoSugerido = codigoSugerido;
        this.candidatos = candidatos;
    }

    public void usarExistente(String codigo) {
        this.codigoEscolhido = codigo;
        this.status = "USOU_EXISTENTE";
    }

    public void criarNovo(String justificativa) {
        this.justificativa = justificativa;
        if ("BLOQUEAR".equals(resultado)) {
            this.status = "AGUARDANDO_CURADOR";
        }
    }

    public void registrarCodigo(String codigo) {
        this.codigoEscolhido = codigo;
        this.status = "CRIADO";
    }

    public void recusar() {
        this.codigoEscolhido = codigoSugerido;
        this.status = "RECUSADO";
    }

    public Long getId() {
        return id;
    }

    public String getDescricaoDigitada() {
        return descricaoDigitada;
    }

    public String getUnidade() {
        return unidade;
    }

    public String getGrupo() {
        return grupo;
    }

    public String getSolicitante() {
        return solicitante;
    }

    public String getResultado() {
        return resultado;
    }

    public String getCodigoSugerido() {
        return codigoSugerido;
    }

    public String getCodigoEscolhido() {
        return codigoEscolhido;
    }

    public String getCandidatos() {
        return candidatos;
    }

    public String getJustificativa() {
        return justificativa;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getCriadaEm() {
        return criadaEm;
    }
}
