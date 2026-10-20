package br.com.squad.codigos.catalogo;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "codigo_servico")
public class CodigoServico {

    @Id
    private String codigo;
    private String descricao;
    private String descricaoLimpa;
    private String descricaoPadrao;
    private String unidade;
    private String grupo;
    private String categoria;
    private String status;
    private String codigoCanonico;
    private String motivo;

    protected CodigoServico() {
    }

    public CodigoServico(String codigo, String descricao, String descricaoLimpa, String descricaoPadrao, String unidade,
                         String grupo, String categoria, String motivo) {
        this.codigo = codigo;
        this.descricao = descricao;
        this.descricaoLimpa = descricaoLimpa;
        this.descricaoPadrao = descricaoPadrao;
        this.unidade = unidade;
        this.grupo = grupo;
        this.categoria = categoria;
        this.status = "ATIVO";
        this.motivo = motivo;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getDescricao() {
        return descricao;
    }

    public String getDescricaoLimpa() {
        return descricaoLimpa;
    }

    public String getDescricaoPadrao() {
        return descricaoPadrao;
    }

    public String getUnidade() {
        return unidade;
    }

    public String getGrupo() {
        return grupo;
    }

    public String getCategoria() {
        return categoria;
    }

    public String getStatus() {
        return status;
    }

    public String getCodigoCanonico() {
        return codigoCanonico;
    }

    public String getMotivo() {
        return motivo;
    }
}
