package br.com.squad.codigos.catalogo;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CodigoServicoRepository extends JpaRepository<CodigoServico, String> {

    List<CodigoServico> findTop20ByDescricaoLimpaContainingOrderByCodigo(String trecho);

    List<CodigoServico> findTop20ByCodigoStartingWithOrderByCodigo(String trecho);

    @Query("select c.unidade, count(c) from CodigoServico c where c.status = 'ATIVO' group by c.unidade")
    List<Object[]> ativosPorUnidade();

    @Query("select c.codigoCanonico from CodigoServico c where c.codigoCanonico is not null group by c.codigoCanonico order by count(c) desc, c.codigoCanonico")
    List<String> oficiaisComMaisRepetidos(Pageable pagina);

    List<CodigoServico> findByCodigoCanonicoOrderByCodigo(String codigo);

    long countByStatus(String status);

    @Query("select count(c) from CodigoServico c where (c.motivo like 'criado%' or c.motivo like 'importado%') and c.status = :status")
    long novosPorStatus(@Param("status") String status);

    @Query("select c from CodigoServico c where c.motivo like 'criado%' or c.motivo like 'importado%' order by c.codigo desc")
    List<CodigoServico> novos(Pageable pagina);

    @Query(value = "select nextval('codigo_novo')", nativeQuery = true)
    Long proximoCodigo();

    @Query(value = "select fornecedor from codigo_fornecedor where codigo = :codigo order by fornecedor", nativeQuery = true)
    List<String> fornecedores(@Param("codigo") String codigo);
}
