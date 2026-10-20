import re
import sys
import time
from collections import Counter, defaultdict
from pathlib import Path
from types import SimpleNamespace

import pandas as pd

from limpar import nome_padrao
from padronizar import categorizar, ler_atributos, normalizar
from regua import NUM, RUIDO, SUAVE, _casa, canon, comparar, preparar

IRRELEVANTES = set()
UNIDADES_IGUAIS = []
PERCENTUAL_NO_NOME = True
LIMITE_CORTE = 38

VOCAB_MOD = {"HORA", "EXTRA", "NORMAL", "NOTURNO", "ADICIONAL", "DOMFER", "FIM", "SEMANA", "SEMANAL", "SEGUNDA",
             "A", "SABADO", "SEXTA", "PERICULOSIDADE", "INSALUBRIDADE", "MENSAL", "POSTO", "MOBILIZACAO",
             "DESMOBILIZACAO", "RETROATIVO", "%"}
ATOMOS = {"NORMAL": {"NORMAL"}, "HE": {"EXTRA"}, "NOT": {"NOTURNO"}, "DOMFER": {"DOMFER", "FIM"},
          "SEGSAB": {"SEGUNDA", "SABADO", "SEXTA", "SEMANA", "SEMANAL"}, "PERIC": {"PERICULOSIDADE"},
          "INSAL": {"INSALUBRIDADE"}, "MENSAL": {"MENSAL", "POSTO"}, "MOB": {"MOBILIZACAO"},
          "DESMOB": {"DESMOBILIZACAO"}, "RETRO": {"RETROATIVO"}}
ESPERADO = {"Hora normal": {"NORMAL"}, "HE Seg-Sab": {"HE", "SEGSAB"}, "HE Dom/Fer": {"HE", "DOMFER"},
            "Adicional noturno": {"NOT"}, "HE noturna Seg-Sab": {"HE", "NOT", "SEGSAB"},
            "HE noturna Dom/Fer": {"HE", "NOT", "DOMFER"}, "Periculosidade (hora)": {"PERIC"},
            "Periculosidade s/ HE": {"PERIC", "HE"}, "Periculosidade (mensal)": {"PERIC", "MENSAL"},
            "Insalubridade (hora)": {"INSAL"}, "Posto mensal": {"MENSAL"}, "Retroativo": {"RETRO"}}


def ler(caminho):
    p = Path(caminho)
    if p.suffix.lower() in (".csv", ".txt"):
        with open(p, encoding="utf-8-sig", errors="replace") as f:
            sep = ";" if f.readline().count(";") >= 4 else ","
        try:
            d = pd.read_csv(p, sep=sep, dtype=str, encoding="utf-8-sig")
        except UnicodeDecodeError:
            d = pd.read_csv(p, sep=sep, dtype=str, encoding="latin-1")
    else:
        d = pd.read_excel(p, sheet_name=0, dtype=str)
    if d.shape[1] != 5:
        raise SystemExit(f"{caminho} tem {d.shape[1]} colunas; são esperadas 5: Grupo de Serviço, Código do Fornecedor, "
                         "Código do Serviço, Descrição do Item, Unidade medida.")
    d.columns = ["grupo", "forn", "cod", "desc", "un"]
    return d.dropna(subset=["cod", "desc"]).fillna("")


def forma_canonica(n5, un):
    a = ler_atributos(n5, un)
    if (not (a["mao_obra"] and a["cargo"])
            or re.search(r"\bSEM (?:PERICULOSIDADE|INSALUBRIDADE)\b", n5)):
        return n5, None
    explicado = {canon(x) for x in a["cargo"].replace("/", " ").split()} | VOCAB_MOD
    pcts = set(re.findall(r"(\d+(?:\.\d+)?) %", n5) + re.findall(r"EXTRA (\d{2,3})\b", n5)) if "EXTRA" in n5.split() else set()
    nivel = re.search(r"NIVEL ([1-3])", n5)
    toks = n5.split()
    if nivel:
        i = toks.index("NIVEL")
        toks = toks[:i] + [f"NIVEL{nivel.group(1)}"] + toks[i + 2:]
    resto = [t for t in toks if canon(t) not in explicado and t not in pcts]
    t = set(n5.split())
    atomos = {x for x, palavras in ATOMOS.items() if t & palavras}
    if "EXTRA" not in t:
        atomos.discard("SEGSAB")
        resto += [w for w in n5.split() if w in ("SEGUNDA", "SABADO", "SEXTA")]
    if a["inferido"]:
        atomos.add("DOMFER" if "100%" in a["inferido"] else "SEGSAB")
    partes = ["C_" + a["cargo"].replace(" ", "_").replace("/", "_"), "M_" + "+".join(sorted(atomos))]
    if PERCENTUAL_NO_NOME:
        partes += [f"P{p}" for p in sorted(pcts)]
    return " ".join(partes + resto), frozenset(atomos)


def chave_bloco(prep):
    toks, nums, _, _ = prep
    return tuple(nums), frozenset(t[:2] if t.isalpha() else t for t in toks - SUAVE if not NUM.fullmatch(t))


def deduplicar(d, log=print):
    t0 = time.time()
    ignorar = frozenset(canon(t) for t in IRRELEVANTES)
    un_igual = {u: grupo[0] for grupo in UNIDADES_IGUAIS for u in grupo}

    d = d.assign(desc=d.desc.str.strip(), un=d.un.str.strip().str.upper())
    cods = d.drop_duplicates("cod").set_index("cod")[["desc", "un", "grupo"]]
    cods["n_forn"] = d.groupby("cod").forn.nunique()
    cods["tam"] = cods.desc.str.len()

    n5 = {t: normalizar(t)["n5"] for t in cods.desc.unique()}
    cods["n5"] = cods.desc.map(n5)
    pares = cods[["n5", "un"]].drop_duplicates()
    canonica = {(a, u): forma_canonica(a, u) for a, u in zip(pares.n5, pares.un)}
    cods["texto"] = [canonica[(a, u)][0] for a, u in zip(cods.n5, cods.un)]
    cods["atomos"] = [canonica[(a, u)][1] for a, u in zip(cods.n5, cods.un)]
    cods["mo"] = cods.atomos.notna()
    categoria = {(a, u): categorizar(a, ler_atributos(a, u)["mao_obra"]) for a, u in zip(pares.n5, pares.un)}
    cods["categoria"] = [categoria[(a, u)] for a, u in zip(cods.n5, cods.un)]
    cods["un_cmp"] = cods.un.map(lambda u: un_igual.get(u, u))
    log(f"  normalização: {len(n5):,} textos distintos em {time.time() - t0:.0f}s")

    fc = d[["cod", "forn"]].drop_duplicates().merge(cods[["texto", "un"]], left_on="cod", right_index=True)
    rep = fc.groupby(["texto", "un", "forn"]).cod.transform("size") > 1
    cortavel = (cods.tam >= LIMITE_CORTE) & ~cods.desc.str.rstrip().str.endswith((")", "%"))
    suspeitos = set(fc.cod[rep]) & set(cods.index[cortavel])
    cods["suspeito"] = cods.index.isin(suspeitos)

    livres = cods[~cods.suspeito]
    nos = (livres.reset_index().sort_values(["n_forn", "cod"], ascending=[False, True])
           .groupby(["texto", "un_cmp"], sort=False)
           .agg(cods=("cod", list), un=("un", "first"), peso=("n_forn", "sum"), mo=("mo", "first")).reset_index())
    nos = nos.sort_values("peso", ascending=False, kind="stable").reset_index(drop=True)
    prep = [preparar(t, ignorar) for t in nos.texto]
    nucleo = [next((canon(w) for w in t.split() if canon(w) not in RUIDO | ignorar and not NUM.fullmatch(w)), "")
              for t in nos.texto]
    blocos = defaultdict(list)
    for i, p in enumerate(prep):
        blocos[chave_bloco(p)].append(i)
    log(f"  {len(nos):,} textos únicos em {len(blocos):,} blocos (maior: {max(map(len, blocos.values()), default=0):,})")

    centro = {}
    decisoes = Counter()
    exemplo = {}
    for membros in blocos.values():
        for a, i in enumerate(membros):
            if i in centro:
                continue
            centro[i] = i
            for j in membros[a + 1:]:
                if j in centro and centro[j] != j:
                    continue
                classe, dif = comparar(prep[i], nos.un_cmp[i], prep[j], nos.un_cmp[j])
                if classe == "DUPLICADO" and nucleo[i] != nucleo[j] and not _casa(nucleo[i], nucleo[j], {nucleo[i], nucleo[j]}):
                    continue
                if classe == "DUPLICADO" and j not in centro:
                    centro[j] = i
                elif classe == "REVISAR":
                    chave = " + ".join(sorted(dif))
                    decisoes[chave] += len(nos.cods[j])
                    exemplo.setdefault(chave, (nos.cods[i][0], cods.desc[nos.cods[i][0]],
                                               nos.cods[j][0], cods.desc[nos.cods[j][0]]))
    log(f"  comparação: {time.time() - t0:.0f}s")

    linhas = []
    for i, cs in enumerate(nos.cods):
        oficial = nos.cods[centro[i]][0]
        for c in cs:
            if c == oficial:
                motivo = "oficial"
            elif cods.n5[c] == cods.n5[oficial]:
                motivo = "mesmo texto depois de limpar"
            elif cods.mo[c] and cods.mo[oficial]:
                motivo = "mão de obra: mesmo cargo, tipo de hora e detalhes"
            else:
                motivo = "mesmo texto com abreviação ou corte diferente"
            linhas.append((c, oficial, motivo))
    linhas += [(c, c, "mantido separado: texto pode ter sido cortado pelo SAP") for c in cods.index[cods.suspeito]]
    dp = pd.DataFrame(linhas, columns=["codigo", "codigo_oficial", "motivo"])
    dp = dp.merge(cods[["desc", "un", "grupo", "n_forn", "n5", "categoria"]], left_on="codigo", right_index=True)
    dp["descricao_oficial"] = dp.codigo_oficial.map(cods.desc)

    nome = {}
    for c in dp.codigo_oficial.unique():
        if cods.mo[c]:
            a = ler_atributos(cods.n5[c], cods.un[c])
            if ESPERADO.get(a["modificador"]) == cods.atomos[c] - {"NORMAL"} | ({"NORMAL"} & ESPERADO.get(a["modificador"], set())):
                nome[c] = nome_padrao(SimpleNamespace(estruturado=True, **a))
    dp["nome_padrao_sugerido"] = dp.codigo_oficial.map(nome).fillna("")
    dp = dp[["codigo", "desc", "n5", "un", "grupo", "categoria", "n_forn", "codigo_oficial", "descricao_oficial",
             "nome_padrao_sugerido", "motivo"]].rename(columns={"desc": "descricao", "n5": "descricao_limpa",
                                                                "un": "unidade", "n_forn": "fornecedores"})

    cat = dp.groupby("codigo_oficial").size().sub(1).rename("codigos_substituidos").reset_index()
    for col, origem in [("descricao", "desc"), ("unidade", "un"), ("grupo", "grupo"), ("categoria", "categoria")]:
        cat[col] = cat.codigo_oficial.map(cods[origem])
    cat["nome_padrao_sugerido"] = cat.codigo_oficial.map(nome).fillna("")
    cat = cat[["codigo_oficial", "descricao", "nome_padrao_sugerido", "categoria", "unidade", "grupo",
               "codigos_substituidos"]].sort_values(["codigos_substituidos", "codigo_oficial"], ascending=[False, True])
    dec = pd.DataFrame([(k, n, *exemplo[k]) for k, n in decisoes.most_common()],
                       columns=["diferenca", "codigos_que_juntariam", "codigo_a", "descricao_a", "codigo_b", "descricao_b"])
    log(f"  total: {time.time() - t0:.0f}s")
    return dp, cat, dec


if __name__ == "__main__":
    caminho = Path(sys.argv[1] if len(sys.argv) > 1 else "dados/base_codigos_servico.xlsx")
    if not caminho.exists():
        sys.exit(f"Planilha não encontrada: {caminho}")
    print(f"lendo {caminho} ...")
    d = ler(caminho)
    dp, cat, dec = deduplicar(d)
    Path("saida").mkdir(exist_ok=True)
    for nome, df in [("depara", dp), ("catalogo", cat), ("decisoes", dec)]:
        df.to_csv(Path("saida") / f"{caminho.stem}_{nome}.csv", sep=";", index=False, encoding="utf-8-sig")
    n, k = len(dp), len(cat)
    print(f"ok: {n:,} códigos -> {k:,} códigos oficiais ({(n - k) / n:.1%} a menos)")
    print(dp.motivo.value_counts().to_string())
    print(f"ok: arquivos em saida/{caminho.stem}_*.csv")
