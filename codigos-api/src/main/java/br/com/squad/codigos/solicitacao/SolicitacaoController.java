package br.com.squad.codigos.solicitacao;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import br.com.squad.codigos.acesso.Acesso;
import br.com.squad.codigos.aprendizado.Aprendizado;
import br.com.squad.codigos.catalogo.CodigoServico;
import br.com.squad.codigos.catalogo.CodigoServicoRepository;
import br.com.squad.codigos.consulta.SimilaridadeClient;
import br.com.squad.codigos.sistema.Auditoria;

@Tag(name = "Solicitações e curador", description = "Decisão do comprador depois da consulta e decisão do curador")
@RestController
@RequestMapping("/api/solicitacoes")
public class SolicitacaoController {

    private final SolicitacaoRepository solicitacoes;
    private final CodigoServicoRepository codigos;
    private final SimilaridadeClient similaridade;
    private final Acesso acesso;
    private final Aprendizado aprendizado;
    private final Auditoria auditoria;
    private final JdbcTemplate jdbc;

    public SolicitacaoController(SolicitacaoRepository solicitacoes, CodigoServicoRepository codigos,
                                 SimilaridadeClient similaridade, Acesso acesso, Aprendizado aprendizado, Auditoria auditoria,
                                 JdbcTemplate jdbc) {
        this.solicitacoes = solicitacoes;
        this.codigos = codigos;
        this.similaridade = similaridade;
        this.acesso = acesso;
        this.aprendizado = aprendizado;
        this.auditoria = auditoria;
        this.jdbc = jdbc;
    }

    @Operation(summary = "Listar as últimas solicitações (filtro opcional por status)")
    @GetMapping
    public List<Solicitacao> listar(@RequestParam(required = false) String status) {
        return status == null ? solicitacoes.findTop50ByOrderByIdDesc() : solicitacoes.findTop50ByStatusOrderByIdDesc(status);
    }

    @Operation(summary = "Usar um código existente ou criar um novo")
    @Transactional
    @PostMapping("/{id}/decisao")
    public Solicitacao decidir(@Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String auth,
                               @PathVariable Long id, @Valid @RequestBody Decisao d) {
        Acesso.Sessao quem = acesso.exigir(auth);
        Solicitacao s = buscar(id, "PENDENTE", "Essa solicitação já foi decidida");
        switch (d.acao()) {
            case "USAR_EXISTENTE" -> {
                if (vazio(d.codigo())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o código escolhido");
                }
                s.usarExistente(d.codigo().trim());
                aprendizado.votar(s.getDescricaoDigitada(), s.getUnidade(), s.getCodigoEscolhido(), Aprendizado.IGUAL, quem.nome(), 1);
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
    public Solicitacao curadoria(@Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String auth,
                                 @PathVariable Long id, @Valid @RequestBody Curadoria c) {
        return curarGrupo(acesso.exigirPerfil(auth, "CURADOR").nome(), List.of(id), c.aprovar()).get(0);
    }

    @Operation(summary = "Pedidos do curador agrupados", description = "Pedidos que pedem o mesmo serviço, mesmo escritos de jeitos diferentes, vêm juntos para decidir de uma vez.")
    @GetMapping("/curador")
    public List<GrupoCurador> grupos() {
        List<Solicitacao> pendentes = solicitacoes.findTop500ByStatusOrderById("AGUARDANDO_CURADOR");
        if (pendentes.isEmpty()) {
            return List.of();
        }
        List<Integer> grupo;
        try {
            grupo = similaridade.agrupar(pendentes.stream()
                    .map(s -> Map.of("descricao", s.getDescricaoDigitada(), "unidade", s.getUnidade())).toList());
        } catch (ResponseStatusException e) {
            grupo = new ArrayList<>();
            for (int i = 0; i < pendentes.size(); i++) {
                grupo.add(i);
            }
        }
        Map<Integer, List<Solicitacao>> juntos = new LinkedHashMap<>();
        for (int i = 0; i < pendentes.size(); i++) {
            juntos.computeIfAbsent(grupo.get(i), k -> new ArrayList<>()).add(pendentes.get(i));
        }
        return juntos.values().stream().map(l -> {
            List<String> pediram = l.stream().map(Solicitacao::getSolicitante).distinct().toList();
            String justificativa = l.stream().map(Solicitacao::getJustificativa).filter(j -> !vazio(j)).findFirst().orElse(null);
            CodigoServico existente = l.get(0).getCodigoSugerido() == null ? null : codigos.findById(l.get(0).getCodigoSugerido()).orElse(null);
            return new GrupoCurador(l.stream().map(Solicitacao::getId).toList(), l.get(0).getDescricaoDigitada(), l.get(0).getUnidade(),
                    l.get(0).getCodigoSugerido(), existente == null ? null : existente.getDescricao(), existente == null ? null : existente.getUnidade(),
                    pediram, l, aprendizado.sugerir(l.get(0).getDescricaoDigitada(), l.get(0).getUnidade(),
                    l.get(0).getCodigoSugerido(), justificativa, pediram.size()));
        }).toList();
    }

    @Operation(summary = "Resumo do curador", description = "Quantos pedidos esperam, quantos foram decididos hoje e quanto o MOKKA já aprendeu.")
    @GetMapping("/curador/resumo")
    public ResumoCurador resumo() {
        java.sql.Timestamp hoje = java.sql.Timestamp.valueOf(java.time.LocalDate.now().atStartOfDay());
        Long esperando = jdbc.queryForObject("select count(*) from solicitacao where status = 'AGUARDANDO_CURADOR'", Long.class);
        Long aprovados = jdbc.queryForObject("select count(*) from solicitacao where curador is not null and status = 'CRIADO' and decidida_em >= ?", Long.class, hoje);
        Long recusados = jdbc.queryForObject("select count(*) from solicitacao where curador is not null and status = 'RECUSADO' and decidida_em >= ?", Long.class, hoje);
        Long regras = jdbc.queryForObject("select count(*) from aprendizado where votos > 0", Long.class);
        Long evitados = jdbc.queryForObject("select count(*) from solicitacao where status in ('USOU_EXISTENTE', 'RECUSADO')", Long.class);
        return new ResumoCurador(n(esperando), n(aprovados), n(recusados), n(regras), n(evitados));
    }

    private static long n(Long v) {
        return v == null ? 0 : v;
    }

    @Operation(summary = "Curador aprova ou recusa um grupo de pedidos iguais",
            description = "Aprovar cria um código só e todos os pedidos do grupo passam a usar ele. Recusar manda todos usarem o existente.")
    @Transactional
    @PostMapping("/curador/grupo")
    public List<Solicitacao> curarGrupo(@Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String auth,
                                        @Valid @RequestBody DecisaoGrupo d) {
        return curarGrupo(acesso.exigirPerfil(auth, "CURADOR").nome(), d.ids(), d.aprovar());
    }

    @Operation(summary = "Desfazer a decisão do curador", description = "Os pedidos voltam a esperar o curador. Se a decisão criou um código, ele vira repetido do código que já existia, e o MOKKA esquece o que tinha aprendido com ela.")
    @Transactional
    @PostMapping("/curador/desfazer")
    public List<Solicitacao> desfazer(@Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String auth,
                                      @Valid @RequestBody Desfazer d) {
        String curador = acesso.exigirPerfil(auth, "CURADOR").nome();
        List<Solicitacao> lista = d.ids().stream().distinct().map(id -> solicitacoes.findById(id)
                .filter(s -> s.getCurador() != null && ("CRIADO".equals(s.getStatus()) || "RECUSADO".equals(s.getStatus())))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "A solicitação " + id + " não tem decisão do curador para desfazer"))).toList();
        for (Solicitacao s : lista) {
            boolean aprovou = "CRIADO".equals(s.getStatus());
            aprendizado.votar(s.getDescricaoDigitada(), s.getUnidade(), s.getCodigoSugerido(), aprovou ? Aprendizado.DIFERENTE : Aprendizado.IGUAL, curador, -1);
            if (aprovou) {
                codigos.findById(s.getCodigoEscolhido()).filter(c -> ("criado na solicitação " + s.getId()).equals(c.getMotivo()))
                        .ifPresent(c -> c.desfazer(s.getCodigoSugerido(), "desfeito por " + curador));
            }
            auditoria.registrar(curador, "DESFEZ", "solicitação " + s.getId(), (aprovou ? "aprovação, código " : "recusa, código ") + s.getCodigoEscolhido());
            s.voltarAoCurador();
        }
        return solicitacoes.saveAll(lista);
    }

    private List<Solicitacao> curarGrupo(String curador, List<Long> ids, boolean aprovar) {
        List<Solicitacao> lista = ids.stream().distinct()
                .map(id -> buscar(id, "AGUARDANDO_CURADOR", "A solicitação " + id + " não está esperando o curador")).toList();
        String codigo = null;
        for (Solicitacao s : lista) {
            if (!aprovar) {
                s.recusar();
            } else if (codigo == null) {
                codigo = criarCodigo(s);
            } else {
                s.registrarCodigo(codigo);
            }
            s.curar(curador);
            aprendizado.votar(s.getDescricaoDigitada(), s.getUnidade(), s.getCodigoSugerido(), aprovar ? Aprendizado.DIFERENTE : Aprendizado.IGUAL, curador, 1);
            auditoria.registrar(curador, aprovar ? "APROVOU" : "RECUSOU", "solicitação " + s.getId(), "código " + s.getCodigoEscolhido());
        }
        return solicitacoes.saveAll(lista);
    }

    private Solicitacao buscar(Long id, String status, String erro) {
        Solicitacao s = solicitacoes.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitação não encontrada: " + id));
        if (!status.equals(s.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, erro);
        }
        return s;
    }

    private String criarCodigo(Solicitacao s) {
        String codigo = String.valueOf(codigos.proximoCodigo());
        String descricao = s.getDescricaoDigitada().toUpperCase();
        String grupo = vazio(s.getGrupo()) ? "Civil" : s.getGrupo();
        SimilaridadeClient.Novo n = similaridade.adicionar(codigo, descricao, s.getUnidade(), grupo);
        codigos.save(new CodigoServico(codigo, descricao, n.descricaoLimpa(), vazio(n.nomePadrao()) ? null : n.nomePadrao(),
                s.getUnidade(), grupo, n.categoria(), "criado na solicitação " + s.getId(), s.getSolicitante()));
        s.registrarCodigo(codigo);
        auditoria.registrar(s.getSolicitante(), "CRIOU_CODIGO", codigo, descricao);
        return codigo;
    }

    private static boolean vazio(String s) {
        return s == null || s.isBlank();
    }

    public record Decisao(@NotBlank String acao, @Size(max = 10) String codigo, @Size(max = 200) String justificativa) {
    }

    public record Curadoria(@NotNull Boolean aprovar) {
    }

    public record DecisaoGrupo(@NotEmpty @Size(max = 500) List<Long> ids, @NotNull Boolean aprovar) {
    }

    public record Desfazer(@NotEmpty @Size(max = 500) List<Long> ids) {
    }

    public record ResumoCurador(long esperando, long aprovadosHoje, long recusadosHoje, long regrasAprendidas, long duplicadosEvitados) {
    }

    public record GrupoCurador(List<Long> ids, String descricao, String unidade, String codigoExistente, String descricaoExistente,
                               String unidadeExistente, List<String> pediram, List<Solicitacao> pedidos, Aprendizado.Sugestao sugestao) {
    }
}
