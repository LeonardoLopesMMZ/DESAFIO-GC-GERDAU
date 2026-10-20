package br.com.squad.codigos.solicitacao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SolicitacaoRepository extends JpaRepository<Solicitacao, Long> {

    List<Solicitacao> findTop50ByOrderByIdDesc();

    List<Solicitacao> findTop50ByStatusOrderByIdDesc(String status);
}
