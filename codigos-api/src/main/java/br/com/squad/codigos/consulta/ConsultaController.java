package br.com.squad.codigos.consulta;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.Parameter;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import br.com.squad.codigos.acesso.Acesso;
import br.com.squad.codigos.aprendizado.Aprendizado;
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
    private final Acesso acesso;
    private final Aprendizado aprendizado;

    public ConsultaController(SimilaridadeClient similaridade, SolicitacaoRepository solicitacoes, Acesso acesso, Aprendizado aprendizado) {
        this.similaridade = similaridade;
        this.solicitacoes = solicitacoes;
        this.acesso = acesso;
        this.aprendizado = aprendizado;
    }

    @Operation(summary = "Consultar uma descrição")
    @PostMapping
    public Resposta consultar(@Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String auth,
                              @Valid @RequestBody Pedido p) {
        Acesso.Sessao quem = acesso.exigir(auth);
        String descricao = p.descricao().trim();
        String unidade = Unidades.codigo(p.unidade());
        if (unidade == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unidade não conhecida: " + p.unidade().trim() + ". Veja a aba Unidades.");
        }
        SimilaridadeClient.Resposta r = aprendizado.aplicar(descricao, unidade, similaridade.buscar(descricao, unidade));
        if (r.sugestao() == null && !"BLOQUEAR".equals(r.resultado())) {
            r = sugerirComPalavras(descricao, unidade, r);
        }
        List<Candidato> c = r.candidatos();
        String sugerido = c.isEmpty() ? null : c.get(0).codigo();
        String mostrados = c.stream().map(Candidato::codigo).collect(Collectors.joining(","));
        Solicitacao s = solicitacoes.save(new Solicitacao(descricao, unidade, p.grupo(), quem.nome(),
                r.resultado(), sugerido, mostrados, quem.metodo(), quem.retratoId()));
        return new Resposta(s.getId(), r.resultado(), r.motivo(), c, r.sugestao());
    }

    @Operation(summary = "Palavras duvidosas", description = "Para cada palavra que não aparece no catálogo, devolve as mais parecidas que aparecem. Usado na busca por voz.")
    @PostMapping("/palavras")
    public SimilaridadeClient.Palavras palavras(@Valid @RequestBody Texto t) {
        String texto = t.texto().trim();
        Map<String, String> aceitas = aprendizado.aceitas();
        List<SimilaridadeClient.Palavra> lista = new ArrayList<>(similaridade.palavras(texto).palavras());
        if (aceitas.isEmpty()) {
            return new SimilaridadeClient.Palavras(lista);
        }
        String[] palavras = texto.split("\\s+");
        for (int i = 0; i < palavras.length; i++) {
            String para = aceitas.get(Aprendizado.simples(palavras[i]));
            if (para == null) {
                continue;
            }
            int indice = i;
            SimilaridadeClient.Palavra antes = lista.stream().filter(x -> x.indice() == indice).findFirst().orElse(null);
            List<String> sugestoes = new ArrayList<>(List.of(para));
            if (antes != null) {
                lista.remove(antes);
                antes.sugestoes().stream().filter(x -> !x.equalsIgnoreCase(para)).forEach(sugestoes::add);
            }
            lista.add(new SimilaridadeClient.Palavra(i, antes == null ? 1 : antes.tamanho(), palavras[i], sugestoes));
        }
        lista.sort(java.util.Comparator.comparingInt(SimilaridadeClient.Palavra::indice));
        return new SimilaridadeClient.Palavras(lista);
    }

    private SimilaridadeClient.Resposta sugerirComPalavras(String descricao, String unidade, SimilaridadeClient.Resposta r) {
        Map<String, String> aceitas = aprendizado.aceitas();
        if (aceitas.isEmpty()) {
            return r;
        }
        List<SimilaridadeClient.Correcao> trocas = new ArrayList<>();
        List<String> novas = new ArrayList<>();
        for (String p : descricao.split("\\s+")) {
            String para = aceitas.get(Aprendizado.simples(p));
            if (para != null) {
                trocas.add(new SimilaridadeClient.Correcao(p, para));
            }
            novas.add(para == null ? p : para);
        }
        String corrigida = String.join(" ", novas).toUpperCase();
        if (trocas.isEmpty() || corrigida.length() > 40) {
            return r;
        }
        SimilaridadeClient.Resposta outra = aprendizado.aplicar(corrigida, unidade, similaridade.buscar(corrigida, unidade));
        if ("LIBERAR".equals(outra.resultado()) || outra.candidatos().isEmpty()) {
            return r;
        }
        Candidato c = outra.candidatos().get(0);
        return new SimilaridadeClient.Resposta(r.resultado(), r.motivo(), r.candidatos(), new SimilaridadeClient.Sugestao(corrigida, trocas,
                outra.resultado(), "O curador ensinou que " + trocas.get(0).de().toUpperCase() + " é " + trocas.get(0).para()
                + " e assim já existe o código " + c.codigo(), c.codigo(), c.descricao()));
    }

    public record Texto(@NotBlank(message = "Informe o texto") @Size(max = 200, message = "O texto deve ter até 200 caracteres") String texto) {
    }

    public record Pedido(@NotBlank(message = "Informe a descrição")
                         @Size(max = 40, message = "A descrição deve ter até 40 caracteres") String descricao,
                         @NotBlank(message = "Informe a unidade")
                         @Size(max = 30, message = "A unidade deve ter até 30 caracteres") String unidade,
                         @Size(max = 40, message = "O grupo deve ter até 40 caracteres") String grupo) {
    }

    public record Resposta(Long solicitacaoId, String resultado, String motivo, List<Candidato> candidatos,
                           SimilaridadeClient.Sugestao sugestao) {
    }
}
