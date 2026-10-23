package br.com.squad.codigos.catalogo;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

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

@Tag(name = "Catálogo", description = "Busca no catálogo saneado, números da base e grupos padronizados")
@RestController
@RequestMapping("/api/codigos")
public class CatalogoController {

    private final CodigoServicoRepository codigos;

    public CatalogoController(CodigoServicoRepository codigos) {
        this.codigos = codigos;
    }

    @Operation(summary = "Buscar por número (começo do código) ou texto")
    @GetMapping
    public List<CodigoServico> buscar(@RequestParam String busca) {
        String trecho = Normalizer.normalize(busca, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toUpperCase().trim();
        if (trecho.length() < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Digite pelo menos 2 letras");
        }
        return trecho.matches("\\d+") ? codigos.findTop20ByCodigoStartingWithOrderByCodigo(trecho)
                : codigos.findTop20ByDescricaoLimpaContainingOrderByCodigo(trecho);
    }

    @Operation(summary = "Códigos criados ou importados no protótipo (últimos 50)")
    @GetMapping("/novos")
    public List<CodigoServico> novos() {
        return codigos.novos(PageRequest.of(0, 50));
    }

    @Operation(summary = "Totais da base: códigos, oficiais, repetidos e criados")
    @GetMapping("/resumo")
    public Resumo resumo() {
        long ativos = codigos.novosPorStatus("ATIVO");
        long bloqueados = codigos.novosPorStatus("BLOQUEADO");
        return new Resumo(codigos.count() - ativos - bloqueados, codigos.countByStatus("ATIVO") - ativos,
                codigos.countByStatus("BLOQUEADO") - bloqueados, ativos + bloqueados);
    }

    @Operation(summary = "Códigos oficiais com os repetidos de cada um")
    @GetMapping("/padronizados")
    public List<Grupo> padronizados() {
        return codigos.oficiaisComMaisRepetidos(PageRequest.of(0, 12)).stream()
                .map(c -> new Grupo(codigos.findById(c).orElseThrow(), codigos.findByCodigoCanonicoOrderByCodigo(c)))
                .toList();
    }

    @Operation(summary = "Detalhe de um código, fornecedores e o oficial que vale no lugar")
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
