package br.com.squad.codigos.consulta;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import java.util.stream.Collectors;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import br.com.squad.codigos.catalogo.Unidades;
import br.com.squad.codigos.consulta.SimilaridadeClient.Candidato;
import br.com.squad.codigos.solicitacao.Solicitacao;
import br.com.squad.codigos.solicitacao.SolicitacaoRepository;

@Tag(name = "Consulta", description = "Antes de criar um código, compara a descrição com o catálogo e diz BLOQUEAR, REVISAR ou LIBERAR")
@RestController
@RequestMapping("/api/consultas")
public class ConsultaController {

    private final SimilaridadeClient similaridade;
    private final SolicitacaoRepository solicitacoes;

    public ConsultaController(SimilaridadeClient similaridade, SolicitacaoRepository solicitacoes) {
        this.similaridade = similaridade;
        this.solicitacoes = solicitacoes;
    }

    @Operation(summary = "Consultar uma descrição")
    @PostMapping
    public Resposta consultar(@Valid @RequestBody Pedido p) {
        String descricao = p.descricao().trim();
        String unidade = Unidades.codigo(p.unidade());
        if (unidade == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unidade não conhecida: " + p.unidade().trim() + ". Veja a aba Unidades.");
        }
        SimilaridadeClient.Resposta r = similaridade.buscar(descricao, unidade);
        List<Candidato> c = r.candidatos();
        String sugerido = c.isEmpty() ? null : c.get(0).codigo();
        String mostrados = c.stream().map(Candidato::codigo).collect(Collectors.joining(","));
        Solicitacao s = solicitacoes.save(new Solicitacao(descricao, unidade, p.grupo(), p.solicitante().trim(),
                r.resultado(), sugerido, mostrados));
        return new Resposta(s.getId(), r.resultado(), r.motivo(), c, r.sugestao());
    }

    public record Pedido(@NotBlank(message = "Informe a descrição")
                         @Size(max = 40, message = "A descrição deve ter até 40 caracteres") String descricao,
                         @NotBlank(message = "Informe a unidade")
                         @Size(max = 30, message = "A unidade deve ter até 30 caracteres") String unidade,
                         @Size(max = 40, message = "O grupo deve ter até 40 caracteres") String grupo,
                         @NotBlank(message = "Informe o comprador")
                         @Size(max = 60, message = "O nome do comprador deve ter até 60 caracteres") String solicitante) {
    }

    public record Resposta(Long solicitacaoId, String resultado, String motivo, List<Candidato> candidatos,
                           SimilaridadeClient.Sugestao sugestao) {
    }
}
