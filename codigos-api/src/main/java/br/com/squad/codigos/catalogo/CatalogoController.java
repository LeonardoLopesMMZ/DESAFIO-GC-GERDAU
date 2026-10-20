package br.com.squad.codigos.catalogo;

import java.text.Normalizer;
import java.util.List;

import org.springframework.data.domain.PageRequest;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/codigos")
public class CatalogoController {

    private final CodigoServicoRepository codigos;

    public CatalogoController(CodigoServicoRepository codigos) {
        this.codigos = codigos;
    }

    @GetMapping
    public List<CodigoServico> buscar(@RequestParam String busca) {
        String trecho = Normalizer.normalize(busca, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toUpperCase().trim();
        if (trecho.length() < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Digite pelo menos 2 letras");
        }
        return trecho.matches("\\d+") ? codigos.findTop20ByCodigoStartingWithOrderByCodigo(trecho)
                : codigos.findTop20ByDescricaoLimpaContainingOrderByCodigo(trecho);
    }

    @GetMapping("/novos")
    public List<CodigoServico> novos() {
        return codigos.findTop20ByCodigoGreaterThanEqualOrderByCodigoDesc("9000001");
    }

    @GetMapping("/resumo")
    public Resumo resumo() {
        long criados = codigos.findTop20ByCodigoGreaterThanEqualOrderByCodigoDesc("9000001").size();
        return new Resumo(codigos.count() - criados, codigos.countByStatus("ATIVO") - criados,
                codigos.countByStatus("BLOQUEADO"), criados);
    }

    @GetMapping("/padronizados")
    public List<Grupo> padronizados() {
        return codigos.oficiaisComMaisRepetidos(PageRequest.of(0, 12)).stream()
                .map(c -> new Grupo(codigos.findById(c).orElseThrow(), codigos.findByCodigoCanonicoOrderByCodigo(c)))
                .toList();
    }

    @GetMapping("/{codigo}")
    public Detalhe detalhe(@PathVariable String codigo) {
        CodigoServico c = codigos.findById(codigo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Código não encontrado: " + codigo));
        CodigoServico oficial = c.getCodigoCanonico() == null ? null : codigos.findById(c.getCodigoCanonico()).orElse(null);
        return new Detalhe(c, codigos.fornecedores(codigo), oficial);
    }

    public record Resumo(long naBase, long oficiais, long repetidos, long criados) {
    }

    public record Grupo(CodigoServico oficial, List<CodigoServico> repetidos) {
    }

    public record Detalhe(CodigoServico codigo, List<String> fornecedores, CodigoServico oficial) {
    }
}
