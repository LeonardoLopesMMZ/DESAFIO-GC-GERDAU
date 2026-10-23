package br.com.squad.codigos.catalogo;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.text.Normalizer;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Unidades", description = "Unidades do SAP, o que pode ser escrito no lugar e exemplos")
@RestController
public class Unidades {

    public record Unidade(String codigo, String nome, List<String> podeEscrever, List<String> exemplos, String aviso, Long codigosAtivos) {
    }

    private static final List<Unidade> TODAS = List.of(
            u("SV", "Serviço", "serviço, serv, verba, vb, global", "SV OFICIAL MENSAL | SV CONTAINER VESTIARIO 2,4X6,0X2,5M | Operador de Varredeira - 6x1", null),
            u("H", "Hora", "hora, horas, hr, hrs", "SV-HORA OFICIAL-HR NORMAL | OPERADOR DE MÁQ - HORA PERICULOSA 50% | PROGRAMADOR (HORA EXTRA DOM/FER)", null),
            u("ST", "Peça ou unidade", "peça, pç, pc, unidade, un, und", "SV-TORNEIRA PARA LAVATÓRIO-1199 C50 | SV JOELHO 90° COM VISITA 100 X 50 MM | SV CAP E PLUG 100 MM", null),
            u("M2", "Metro quadrado", "m², metro quadrado, metros quadrados", "SV PINT DE PISO EPOXIDICO ESPATUL - FDS | SV-DEMOLIÇÃO PISO PAVIFLEX | SV ALVENARIA BLOCOS CCA 60x30x20 cm", null),
            u("M", "Metro (linear)", "metro, metros, mt, metro linear", "SV-TUBO RÍGIDO LISO 200 MM | SV CABO DE COBRE NU 50 MM² | SV RODAPÉ DURAFLOOR LINHA CLEAN", null),
            u("M3", "Metro cúbico", "m³, metro cúbico, metros cúbicos", "SV-CONCRETO FCK 40,0 | SV ESCAVAÇÃO MEC DE VALA | SV-DEMOLICAO CONCRETO SIMPLES", null),
            u("MON", "Mês", "mês, meses, mensal", "LÍDER DE OBRA - MENSAL | AUXILIAR DE SERVIÇOS GERAIS - MENSAL | COMPRESSOR 825 PCM - MENSAL", null),
            u("TAG", "Dia", "dia, dias, diária, diárias", "MÁQUINA RETRO COM ROMPEDOR DIARIA | COMPRESSOR 825 PCM - DIÁRIA | MECANICO DE MANUTENÇÃO (DIA)", null),
            u("KG", "Quilograma", "quilo, quilos, kilo, quilograma", "ARMADURA CA-50 DN 10,0MM INCLUSIVE CORT | SV PRIMER EPOXI | Pavimentação a frio por kg", null),
            u("TO", "Tonelada", "t, ton, tonelada, toneladas", "PÓ DE BRITA | BASE GRADUADA", null),
            u("KM", "Quilômetro", "quilômetro, quilômetros, kilometro", "KM RODADO 3.30 | SV TRANSPORTE RODOVIARIO - ONIBUS | Adicional por Km", null),
            u("CM", "Centímetro", "centímetro, centímetros", "SV - FURAÇÃO EM CONCRETO COM COROA DIAM | FURAÇÃO CONCRETO C/BROCA VÍDEA ATÉ 1/2\" | SV - ANCORAGEM QUÍMICA SISTEMA HVA DA HI", null),
            u("MM", "Milímetro", "milímetro, milímetros", "SV-FUR CHAPA MET APOIADA CONCR/REFRAT", null),
            u("L", "Litro", "litro, litros, lt", "INJEÇÃO DE RESINA POLIURETANO - GEL | DMA - R$/LITRO | SV PRIMER ENTRE SUBSTRATO E SIST IMP", null),
            u("CDM", "Decímetro cúbico (igual a 1 litro)", "dm³, decímetro cúbico", "FORNECIMENTO E APLICAÇÃO DE GEL ACRÍLICO | FORNECIMENTO E APLICAÇÃO DE MASTIQUE EM",
                    "Mede o mesmo que L. Confirmar com a Gerdau se dá para usar só L."),
            u("ML", "Mililitro", "mililitro, mililitros", "SV - PINTURA ESMALTE SINTÉTICO BASE ÁGUA | SV - PINTURA ESMALTE SINTÉTICO BASE SOLV",
                    "Na base aparece em pintura, onde pode ter sido usado como metro linear. Metro linear é M."),
            u("ZCJ", "Conjunto", "conjunto, conjuntos, cj, conj", "SV ASSENT FERRAGEM PARA PORTA INT DUPLA | SV DOBRADIÇA PARA PORTA DE DIVISÓRIA",
                    "Unidade criada pela própria Gerdau (começa com Z). Confirmar o significado."));

    private static final Map<String, String> DIGITADO = TODAS.stream()
            .flatMap(un -> Stream.concat(Stream.of(un.codigo()), un.podeEscrever().stream())
                    .map(t -> Map.entry(limpar(t), un.codigo())))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a));

    private final CodigoServicoRepository codigos;

    public Unidades(CodigoServicoRepository codigos) {
        this.codigos = codigos;
    }

    @Operation(summary = "Listar unidades")
    @GetMapping("/api/unidades")
    public List<Unidade> listar() {
        Map<String, Long> ativos = codigos.ativosPorUnidade().stream()
                .collect(Collectors.toMap(l -> (String) l[0], l -> (Long) l[1]));
        return TODAS.stream()
                .map(un -> new Unidade(un.codigo(), un.nome(), un.podeEscrever(), un.exemplos(), un.aviso(),
                        ativos.getOrDefault(un.codigo(), 0L)))
                .toList();
    }

    public static String codigo(String digitado) {
        return digitado == null ? null : DIGITADO.get(limpar(digitado));
    }

    static String limpar(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFKD).replaceAll("\\p{M}", "").replace(".", "")
                .trim().replaceAll("\\s+", " ").toUpperCase();
    }

    private static Unidade u(String codigo, String nome, String podeEscrever, String exemplos, String aviso) {
        return new Unidade(codigo, nome, List.of(podeEscrever.split(",\\s*")), List.of(exemplos.split(" \\| ")), aviso, null);
    }
}
