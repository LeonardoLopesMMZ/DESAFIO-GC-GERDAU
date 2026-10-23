package br.com.squad.codigos.solicitacao;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import br.com.squad.codigos.catalogo.CodigoServico;
import br.com.squad.codigos.catalogo.CodigoServicoRepository;
import br.com.squad.codigos.consulta.SimilaridadeClient;

@Tag(name = "Solicitações e curador", description = "Decisão do comprador depois da consulta e decisão do curador")
@RestController
@RequestMapping("/api/solicitacoes")
public class SolicitacaoController {

    private final SolicitacaoRepository solicitacoes;
    private final CodigoServicoRepository codigos;
    private final SimilaridadeClient similaridade;

    public SolicitacaoController(SolicitacaoRepository solicitacoes, CodigoServicoRepository codigos,
                                 SimilaridadeClient similaridade) {
        this.solicitacoes = solicitacoes;
        this.codigos = codigos;
        this.similaridade = similaridade;
    }

    @Operation(summary = "Listar as últimas solicitações (filtro opcional por status)")
    @GetMapping
    public List<Solicitacao> listar(@RequestParam(required = false) String status) {
        return status == null ? solicitacoes.findTop50ByOrderByIdDesc() : solicitacoes.findTop50ByStatusOrderByIdDesc(status);
    }

    @Operation(summary = "Usar um código existente ou criar um novo")
    @Transactional
    @PostMapping("/{id}/decisao")
    public Solicitacao decidir(@PathVariable Long id, @Valid @RequestBody Decisao d) {
        Solicitacao s = buscar(id, "PENDENTE", "Essa solicitação já foi decidida");
        switch (d.acao()) {
            case "USAR_EXISTENTE" -> {
                if (vazio(d.codigo())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o código escolhido");
                }
                s.usarExistente(d.codigo().trim());
            }
            case "CRIAR_NOVO" -> {
                if (!"LIBERAR".equals(s.getResultado()) && vazio(d.justificativa())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Justificativa obrigatória: já existe código igual ou parecido");
                }
                s.criarNovo(vazio(d.justificativa()) ? null : d.justificativa().trim());
                if (!"AGUARDANDO_CURADOR".equals(s.getStatus())) {
                    criarCodigo(s);
                }
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "acao deve ser USAR_EXISTENTE ou CRIAR_NOVO");
        }
        return solicitacoes.save(s);
    }

    @Operation(summary = "Curador aprova ou recusa a criação")
    @Transactional
    @PostMapping("/{id}/curadoria")
    public Solicitacao curadoria(@PathVariable Long id, @Valid @RequestBody Curadoria c) {
        Solicitacao s = buscar(id, "AGUARDANDO_CURADOR", "Essa solicitação não está esperando o curador");
        if (c.aprovar()) {
            criarCodigo(s);
        } else {
            s.recusar();
        }
        return solicitacoes.save(s);
    }

    private Solicitacao buscar(Long id, String status, String erro) {
        Solicitacao s = solicitacoes.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitação não encontrada: " + id));
        if (!status.equals(s.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, erro);
        }
        return s;
    }

    private void criarCodigo(Solicitacao s) {
        String codigo = String.valueOf(codigos.proximoCodigo());
        String descricao = s.getDescricaoDigitada().toUpperCase();
        String grupo = vazio(s.getGrupo()) ? "Civil" : s.getGrupo();
        SimilaridadeClient.Novo n = similaridade.adicionar(codigo, descricao, s.getUnidade(), grupo);
        codigos.save(new CodigoServico(codigo, descricao, n.descricaoLimpa(), vazio(n.nomePadrao()) ? null : n.nomePadrao(),
                s.getUnidade(), grupo, n.categoria(), "criado na solicitação " + s.getId()));
        s.registrarCodigo(codigo);
    }

    private static boolean vazio(String s) {
        return s == null || s.isBlank();
    }

    public record Decisao(@NotBlank String acao, @Size(max = 10) String codigo, @Size(max = 200) String justificativa) {
    }

    public record Curadoria(@NotNull Boolean aprovar) {
    }
}
