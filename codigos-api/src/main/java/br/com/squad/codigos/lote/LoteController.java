package br.com.squad.codigos.lote;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import br.com.squad.codigos.acesso.Acesso;
import br.com.squad.codigos.catalogo.CodigoServico;
import br.com.squad.codigos.catalogo.CodigoServicoRepository;
import br.com.squad.codigos.catalogo.Unidades;
import br.com.squad.codigos.consulta.SimilaridadeClient;

@Tag(name = "Lote por Excel", description = "Analisa uma planilha, um CSV, um texto ou um JSON inteiro de uma vez, coloca no banco do jeito certo e devolve um Excel organizado")
@RestController
@RequestMapping("/api/lotes")
public class LoteController {

    static final int MAXIMO = 100_000;

    private static final String INSERIR_CODIGO = "INSERT INTO codigo_servico (codigo, descricao, descricao_limpa, descricao_padrao, "
            + "unidade, grupo, categoria, status, codigo_canonico, motivo, criado_por, criado_em) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String INSERIR_SOLICITACAO = "INSERT INTO solicitacao (descricao_digitada, unidade, grupo, solicitante, "
            + "resultado, codigo_sugerido, codigo_escolhido, justificativa, status, criada_em, metodo, retrato_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private final SimilaridadeClient similaridade;
    private final CodigoServicoRepository codigos;
    private final JdbcTemplate jdbc;
    private final Acesso acesso;

    public LoteController(SimilaridadeClient similaridade, CodigoServicoRepository codigos, JdbcTemplate jdbc, Acesso acesso) {
        this.similaridade = similaridade;
        this.codigos = codigos;
        this.jdbc = jdbc;
        this.acesso = acesso;
    }

    @Operation(summary = "Analisar planilha",
            description = "Recebe um Excel (.xlsx), CSV, texto ou JSON, até 100.000 linhas, com as descrições (pode vir sem tratamento) e diz, linha a linha, se o código "
                    + "já existe, se é parecido, se é novo ou se repete outra linha da própria planilha. Não grava nada.")
    @PostMapping(value = "/analisar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResultadoAnalise analisar(@RequestPart("arquivo") MultipartFile arquivo,
                            @RequestParam(defaultValue = "") String unidadePadrao,
                            @RequestParam(defaultValue = "") String grupoPadrao) throws IOException {
        if (arquivo.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O arquivo está vazio.");
        }
        long inicio = System.currentTimeMillis();
        SimilaridadeClient.Planilha p = similaridade.lerPlanilha(arquivo.getBytes(), arquivo.getOriginalFilename());
        String unPadrao = unidadePadrao.isBlank() ? null : Unidades.codigo(unidadePadrao);
        if (!unidadePadrao.isBlank() && unPadrao == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unidade padrão não conhecida: " + unidadePadrao);
        }

        List<Linha> linhas = new ArrayList<>();
        List<Map<String, String>> paraAnalisar = new ArrayList<>();
        List<Integer> posicao = new ArrayList<>();
        for (Map<String, Object> l : p.linhas()) {
            String descricao = texto(l.get("descricao"));
            String unDigitada = texto(l.get("unidade"));
            String unidade = unDigitada.isEmpty() ? unPadrao : Unidades.codigo(unDigitada);
            String grupo = texto(l.get("grupo")).isEmpty() ? grupoPadrao.trim() : texto(l.get("grupo"));
            String erro = descricao.isEmpty() ? "Linha sem descrição"
                    : descricao.length() > 200 ? "Descrição com mais de 200 caracteres"
                    : unidade == null ? (unDigitada.isEmpty() ? "Linha sem unidade (escolha uma unidade padrão)" : "Unidade não conhecida: " + unDigitada)
                    : null;
            Linha linha = new Linha(((Number) l.get("linha")).intValue(), texto(l.get("origem")), texto(l.get("codigo")), texto(l.get("fornecedor")),
                    descricao, unidade == null ? unDigitada : unidade, grupo, null, null, null,
                    erro == null ? null : "ERRO", null, null, null, null, erro, erro == null ? null : "IGNORAR", null, null);
            if (erro == null) {
                paraAnalisar.add(Map.of("descricao", descricao, "unidade", unidade));
                posicao.add(linhas.size());
            }
            linhas.add(linha);
        }

        if (!paraAnalisar.isEmpty()) {
            List<SimilaridadeClient.Analise> r = similaridade.analisarLote(paraAnalisar).itens();
            Set<String> informados = linhas.stream().map(Linha::codigoInformado).filter(c -> !c.isEmpty()).collect(Collectors.toSet());
            Map<String, CodigoServico> noBanco = codigos.findAllById(informados).stream()
                    .collect(Collectors.toMap(CodigoServico::getCodigo, Function.identity()));
            for (int k = 0; k < r.size(); k++) {
                int i = posicao.get(k);
                Linha l = linhas.get(i);
                SimilaridadeClient.Analise a = r.get(k);
                CodigoServico existente = noBanco.get(l.codigoInformado());
                String resultado = a.resultado();
                String codExistente = a.codigoExistente();
                String descExistente = a.descricaoExistente();
                String motivo = a.motivo();
                Integer repete = a.repeteItem() == null ? null : linhas.get(posicao.get(a.repeteItem())).linha();
                if (existente != null) {
                    String oficial = existente.getCodigoCanonico() == null ? existente.getCodigo() : existente.getCodigoCanonico();
                    resultado = "BLOQUEAR";
                    codExistente = oficial;
                    descExistente = existente.getDescricao();
                    motivo = "O código " + existente.getCodigo() + " já está no catálogo" + (oficial.equals(existente.getCodigo()) ? "" : " (repetido de " + oficial + ")");
                    repete = null;
                } else if (repete != null && !"BLOQUEAR".equals(resultado)) {
                    resultado = "REPETIDO";
                    motivo = "Mesmo serviço da linha " + repete + " desta planilha";
                }
                boolean parecido = "BLOQUEAR".equals(resultado) || "REVISAR".equals(resultado);
                String acao = switch (resultado) {
                    case "BLOQUEAR", "REVISAR" -> "USAR_EXISTENTE";
                    default -> "CRIAR";
                };
                linhas.set(i, new Linha(l.linha(), l.origem(), l.codigoInformado(), l.fornecedor(), l.descricao(), l.unidade(), l.grupo(),
                        a.descricaoLimpa(), a.nomePadrao(), a.categoria(), resultado, parecido ? codExistente : null,
                        parecido ? descExistente : null, parecido && existente == null ? a.aderencia() : null, repete, motivo, acao, null, null));
            }
        }
        return new ResultadoAnalise(arquivo.getOriginalFilename(), p.colunas(), p.relatorio(), resumo(linhas), System.currentTimeMillis() - inicio, linhas);
    }

    @Operation(summary = "Importar no banco",
            description = "Grava as linhas analisadas conforme a ação de cada uma: USAR_EXISTENTE, CRIAR ou IGNORAR. "
                    + "Códigos antigos da planilha que repetem um oficial entram como repetidos apontando para ele. "
                    + "Tudo numa transação só: se algo falhar, nada fica gravado pela metade.")
    @Transactional
    @PostMapping("/importar")
    public Importacao importar(@Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String auth,
                               @Valid @RequestBody PedidoImportacao pedido) {
        long inicio = System.currentTimeMillis();
        List<Linha> linhas = pedido.linhas().stream().sorted(Comparator.comparingInt(Linha::linha)).toList();
        Set<String> envolvidos = new HashSet<>();
        linhas.forEach(l -> {
            if (!vazio(l.codigoInformado())) envolvidos.add(l.codigoInformado());
            if (!vazio(l.codigoExistente())) envolvidos.add(l.codigoExistente());
        });
        Map<String, CodigoServico> banco = codigos.findAllById(envolvidos).stream()
                .collect(Collectors.toMap(CodigoServico::getCodigo, Function.identity()));
        Set<String> existentes = new HashSet<>(banco.keySet());
        Map<Integer, String> finalPorLinha = new HashMap<>();
        Map<String, String> alvoDoCodigo = new HashMap<>();
        List<Object[]> novosCodigos = new ArrayList<>();
        List<Object[]> registros = new ArrayList<>();
        List<Map<String, String>> paraSimilaridade = new ArrayList<>();
        List<Linha> saida = new ArrayList<>();
        Timestamp agora = Timestamp.valueOf(LocalDateTime.now());
        Acesso.Sessao sessao = acesso.exigir(auth);
        String quem = sessao.nome();

        for (Linha l : linhas) {
            String acao = vazio(l.acao()) ? "IGNORAR" : l.acao();
            String informado = vazio(l.codigoInformado()) ? null : l.codigoInformado().trim();
            String grupo = vazio(l.grupo()) ? "Sem grupo" : corta(l.grupo(), 40);
            String alvo = null;
            String situacao;
            String status = "USOU_EXISTENTE";

            if ("ERRO".equals(l.resultado()) || "IGNORAR".equals(acao)) {
                saida.add(l.com(null, "Ignorada" + (vazio(l.motivo()) || !"ERRO".equals(l.resultado()) ? "" : ": " + l.motivo())));
                continue;
            }
            if (informado != null && banco.containsKey(informado)) {
                CodigoServico c = banco.get(informado);
                alvo = c.getCodigoCanonico() == null ? c.getCodigo() : c.getCodigoCanonico();
                situacao = "Já estava no catálogo" + (alvo.equals(informado) ? "" : "; use " + alvo);
            } else if (informado != null && alvoDoCodigo.containsKey(informado)) {
                alvo = alvoDoCodigo.get(informado);
                situacao = "Código repetido na planilha; " + (alvo.equals(informado) ? "já importado acima" : "use " + alvo);
            } else if (l.repeteLinha() != null && finalPorLinha.containsKey(l.repeteLinha()) && !"USAR_EXISTENTE".equals(acao)) {
                alvo = finalPorLinha.get(l.repeteLinha());
                situacao = "Mesmo código da linha " + l.repeteLinha();
            } else if ("USAR_EXISTENTE".equals(acao)) {
                if (vazio(l.codigoExistente()) || !existentes.contains(l.codigoExistente())) {
                    saida.add(l.com(null, "Não importada: não há código existente para usar"));
                    continue;
                }
                alvo = l.codigoExistente();
                situacao = "Usa o código existente " + alvo;
            } else if ("CRIAR".equals(acao)) {
                if ("BLOQUEAR".equals(l.resultado())) {
                    alvo = l.codigoExistente();
                    situacao = "Não criado: já existe o " + alvo + ". Para insistir, use a consulta e o curador";
                } else {
                    alvo = informado != null ? informado : String.valueOf(codigos.proximoCodigo());
                    String desc = corta(l.descricao().toUpperCase(), 60);
                    novosCodigos.add(new Object[]{alvo, desc, corta(l.descricaoLimpa(), 200), corta(l.nomePadrao(), 40),
                            l.unidade(), grupo, corta(l.categoria(), 40), "ATIVO", null, corta("importado da planilha, linha " + l.linha(), 80), corta(quem, 60), agora});
                    paraSimilaridade.add(item(alvo, desc, l.unidade(), grupo, null));
                    existentes.add(alvo);
                    situacao = "Criado";
                    status = "CRIADO";
                }
            } else {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ação inválida na linha " + l.linha() + ": " + acao);
            }

            if (informado != null && alvo != null && !existentes.contains(informado) && !informado.equals(alvo)) {
                String desc = corta(l.descricao().toUpperCase(), 60);
                novosCodigos.add(new Object[]{informado, desc, corta(l.descricaoLimpa(), 200), corta(l.nomePadrao(), 40),
                        l.unidade(), grupo, corta(l.categoria(), 40), "BLOQUEADO", alvo, corta("importado da planilha: repetido de " + alvo, 80), corta(quem, 60), agora});
                paraSimilaridade.add(item(informado, desc, l.unidade(), grupo, alvo));
                existentes.add(informado);
                situacao += "; o código " + informado + " entrou como repetido de " + alvo;
            }
            finalPorLinha.put(l.linha(), alvo);
            if (informado != null && alvo != null) {
                alvoDoCodigo.putIfAbsent(informado, alvo);
            }
            registros.add(new Object[]{corta(l.descricao(), 40), l.unidade(), grupo, corta(quem, 60), l.resultado(),
                    l.codigoExistente(), alvo, corta("importação em lote, linha " + l.linha(), 200), status, agora, sessao.metodo(), sessao.retratoId()});
            saida.add(l.com(alvo, situacao));
        }

        if (!novosCodigos.isEmpty()) {
            jdbc.batchUpdate(INSERIR_CODIGO, novosCodigos);
        }
        if (!registros.isEmpty()) {
            jdbc.batchUpdate(INSERIR_SOLICITACAO, registros);
        }
        if (!paraSimilaridade.isEmpty()) {
            similaridade.adicionarVarios(paraSimilaridade);
        }
        Map<String, Long> resumo = saida.stream().collect(Collectors.groupingBy(
                l -> l.situacao().startsWith("Criado") ? "criados" : l.situacao().startsWith("Ignorada") || l.situacao().startsWith("Não")
                        ? "nao_importadas" : "usam_existente", LinkedHashMap::new, Collectors.counting()));
        return new Importacao(resumo, System.currentTimeMillis() - inicio, saida);
    }

    @Operation(summary = "Exportar Excel organizado",
            description = "Devolve um .xlsx com três abas: Tratado (linha a linha), Pronto para o SAP (um código por linha, "
                    + "com nome padrão de até 40 caracteres) e Resumo.")
    @PostMapping(value = "/exportar", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> exportar(@Valid @RequestBody PedidoExportacao pedido) {
        byte[] xlsx = similaridade.exportar(pedido.linhas(), pedido.relatorio());
        String nome = "codigos_organizados_" + LocalDateTime.now().toString().substring(0, 16).replace(":", "h") + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nome).build().toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(xlsx);
    }

    private static Map<String, String> item(String codigo, String descricao, String unidade, String grupo, String oficial) {
        Map<String, String> m = new HashMap<>(Map.of("codigo", codigo, "descricao", descricao, "unidade", unidade, "grupo", grupo));
        if (oficial != null) {
            m.put("oficial", oficial);
        }
        return m;
    }

    private static Map<String, Long> resumo(List<Linha> linhas) {
        Map<String, Long> r = new LinkedHashMap<>();
        for (String k : List.of("BLOQUEAR", "REVISAR", "LIBERAR", "REPETIDO", "ERRO")) {
            r.put(k, linhas.stream().filter(l -> k.equals(l.resultado())).count());
        }
        r.put("TOTAL", (long) linhas.size());
        return r;
    }

    private static String texto(Object o) {
        return o == null ? "" : o.toString().trim();
    }

    private static boolean vazio(String s) {
        return s == null || s.isBlank();
    }

    private static String corta(String s, int max) {
        return s == null ? null : s.length() <= max ? s : s.substring(0, max);
    }

    public record Linha(int linha, String origem, String codigoInformado, String fornecedor, String descricao, String unidade, String grupo,
                        String descricaoLimpa, String nomePadrao, String categoria, String resultado, String codigoExistente,
                        String descricaoExistente, Integer aderencia, Integer repeteLinha, String motivo, String acao,
                        String codigoFinal, String situacao) {

        Linha com(String codigoFinal, String situacao) {
            return new Linha(linha, origem, codigoInformado, fornecedor, descricao, unidade, grupo, descricaoLimpa, nomePadrao, categoria,
                    resultado, codigoExistente, descricaoExistente, aderencia, repeteLinha, motivo, acao, codigoFinal, situacao);
        }
    }

    public record ResultadoAnalise(String arquivo, Map<String, String> colunas, List<String> relatorio, Map<String, Long> resumo, long milissegundos, List<Linha> linhas) {
    }

    public record Importacao(Map<String, Long> resumo, long milissegundos, List<Linha> linhas) {
    }

    public record PedidoImportacao(@Size(max = 60, message = "O nome deve ter até 60 caracteres") String solicitante,
                                   @NotEmpty(message = "Nenhuma linha para importar")
                                   @Size(max = MAXIMO, message = "No máximo 100.000 linhas por vez") List<Linha> linhas) {
    }

    public record PedidoExportacao(@NotEmpty(message = "Nenhuma linha para exportar")
                                   @Size(max = MAXIMO, message = "No máximo 100.000 linhas por vez") List<Linha> linhas,
                                   @Size(max = 200) List<String> relatorio) {
    }
}
