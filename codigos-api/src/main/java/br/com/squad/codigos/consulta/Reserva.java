package br.com.squad.codigos.consulta;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Component;

import br.com.squad.codigos.catalogo.CodigoServicoRepository;

@Component
public class Reserva {

    static final String AVISO = " · modo reserva: o Python está fora do ar, a busca foi feita direto no banco";
    private static final double QUASE = 0.6;

    private final CodigoServicoRepository codigos;
    private final List<Item> itens = new CopyOnWriteArrayList<>();
    private final List<Map<String, String>> paraPython = new CopyOnWriteArrayList<>();
    private volatile boolean carregado;

    public Reserva(CodigoServicoRepository codigos) {
        this.codigos = codigos;
    }

    record Item(String codigo, String descricao, String unidade, Set<String> palavras) {
    }

    record Nota(Item item, double nota) {
    }

    static String normalizar(String t) {
        String s = Normalizer.normalize(t == null ? "" : t, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toUpperCase();
        return s.replaceAll("[^A-Z0-9%]+", " ").trim();
    }

    static Set<String> palavras(String t) {
        Set<String> p = new LinkedHashSet<>();
        for (String w : normalizar(t).split(" ")) {
            if (!w.isEmpty() && !w.equals("SV")) {
                p.add(w);
            }
        }
        return p;
    }

    static double parecido(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        int comum = 0;
        for (String w : a) {
            if (b.contains(w)) {
                comum++;
            }
        }
        return (double) comum / (a.size() + b.size() - comum);
    }

    private List<Item> itens() {
        if (!carregado) {
            synchronized (this) {
                if (!carregado) {
                    codigos.findByStatus("ATIVO").forEach(c -> itens.add(new Item(c.getCodigo(), c.getDescricao(), c.getUnidade(), palavras(c.getDescricao()))));
                    carregado = true;
                }
            }
        }
        return itens;
    }

    public SimilaridadeClient.Resposta buscar(String descricao, String unidade) {
        Set<String> p = palavras(descricao);
        List<Nota> notas = new ArrayList<>();
        for (Item i : itens()) {
            double n = parecido(p, i.palavras()) * (i.unidade().equalsIgnoreCase(unidade) ? 1 : 0.85);
            if (n > 0) {
                notas.add(new Nota(i, n));
            }
        }
        notas.sort(Comparator.comparingDouble(Nota::nota).reversed());
        List<SimilaridadeClient.Candidato> candidatos = new ArrayList<>();
        for (Nota n : notas.subList(0, Math.min(10, notas.size()))) {
            Item i = n.item();
            String classe = n.nota() > 0.999 ? "DUPLICADO" : n.nota() >= QUASE ? "REVISAR" : "DIFERENTE";
            candidatos.add(new SimilaridadeClient.Candidato(i.codigo(), i.descricao(), null, i.unidade(), null,
                    (int) Math.round(n.nota() * 100), classe, diferencas(p, i, unidade), null));
        }
        if (!candidatos.isEmpty() && "DUPLICADO".equals(candidatos.get(0).classe())) {
            SimilaridadeClient.Candidato c = candidatos.get(0);
            return new SimilaridadeClient.Resposta("BLOQUEAR", "Já existe: " + c.codigo() + " " + c.descricao() + AVISO, candidatos, null);
        }
        if (!candidatos.isEmpty() && "REVISAR".equals(candidatos.get(0).classe())) {
            SimilaridadeClient.Candidato c = candidatos.get(0);
            return new SimilaridadeClient.Resposta("REVISAR", "Parecido com " + c.codigo() + " " + c.descricao() + AVISO, candidatos, null);
        }
        return new SimilaridadeClient.Resposta("LIBERAR", "Nenhum código igual" + AVISO, candidatos, null);
    }

    private static List<String> diferencas(Set<String> pedido, Item i, String unidade) {
        List<String> d = new ArrayList<>();
        for (String w : pedido) {
            if (!i.palavras().contains(w)) {
                d.add(w.toLowerCase());
            }
        }
        for (String w : i.palavras()) {
            if (!pedido.contains(w)) {
                d.add(w.toLowerCase());
            }
        }
        if (!i.unidade().equalsIgnoreCase(unidade)) {
            d.add("UNIDADE " + unidade + "/" + i.unidade());
        }
        return d;
    }

    public SimilaridadeClient.Novo adicionar(String codigo, String descricao, String unidade, String grupo) {
        Map<String, String> m = new HashMap<>(Map.of("codigo", codigo, "descricao", descricao, "unidade", unidade, "grupo", grupo == null ? "" : grupo));
        paraPython.add(m);
        lembrar(codigo, descricao, unidade);
        String limpa = normalizar(descricao);
        return new SimilaridadeClient.Novo(codigo, limpa, null, limpa.length() > 40 ? limpa.substring(0, 40).trim() : limpa);
    }

    public void lembrar(String codigo, String descricao, String unidade) {
        if (carregado) {
            itens.add(new Item(codigo, descricao, unidade, palavras(descricao)));
        }
    }

    public void guardar(List<Map<String, String>> novos) {
        for (Map<String, String> m : novos) {
            paraPython.add(m);
            if (m.get("oficial") == null) {
                lembrar(m.get("codigo"), m.get("descricao"), m.get("unidade"));
            }
        }
    }

    public List<Map<String, String>> pendentes() {
        return new ArrayList<>(paraPython);
    }

    public void enviados(List<Map<String, String>> lista) {
        paraPython.removeAll(lista);
    }

    public List<Integer> agrupar(List<Map<String, String>> pedidos) {
        Map<String, Integer> chave = new HashMap<>();
        List<Integer> grupos = new ArrayList<>();
        for (Map<String, String> p : pedidos) {
            String k = String.join(" ", new java.util.TreeSet<>(palavras(p.get("descricao")))) + "|" + p.get("unidade");
            grupos.add(chave.computeIfAbsent(k, x -> chave.size()));
        }
        return grupos;
    }
}
