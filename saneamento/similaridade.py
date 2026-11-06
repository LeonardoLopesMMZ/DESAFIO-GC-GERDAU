import re
import sys
import unicodedata
import threading
from collections import Counter, defaultdict
from difflib import SequenceMatcher, get_close_matches
from functools import lru_cache
from pathlib import Path
from types import SimpleNamespace

import numpy as np
import pandas as pd
from scipy.sparse import vstack
from sklearn.feature_extraction.text import TfidfVectorizer

from deduplicar import deduplicar, forma_canonica, ler
from limpar import caber40, nome_padrao
from padronizar import categorizar, ler_atributos
from padronizar import normalizar as _normalizar
from regua import comparar, preparar

normalizar = lru_cache(maxsize=200_000)(_normalizar)

ORDEM = {"DUPLICADO": 0, "REVISAR": 1, "DIFERENTE": 2}
RESULTADO = {"DUPLICADO": "BLOQUEAR", "REVISAR": "REVISAR", "DIFERENTE": "LIBERAR"}
EXEMPLOS = [("MEC.MANUT.NORM", "H"), ("SV-MEC-01-HN", "H"), ("tec seg trabalho hora extra domingo", "H"),
            ("SOLDADOR HE 50% SEG A SAB", "H"), ("SV CURVA SOLDAVEL 25MM", "ST"), ("VIGILANTE 12X36 NOTURNO", "SV"),
            ("mobilizacao equipe eletricista", "SV"), ("caminhao munck hora produtiva", "H"),
            ("cafe da manha ate 20 pessoas", "SV"), ("PINTURA EPOXI PISO", "M2")]


ATOMO = {"NORMAL": "hora normal", "HE": "hora extra", "NOT": "noturno", "DOMFER": "dom/fer", "SEGSAB": "seg-sáb",
         "PERIC": "periculosidade", "INSAL": "insalubridade", "MENSAL": "mensal", "MOB": "mobilização",
         "DESMOB": "desmobilização", "RETRO": "retroativo"}


def legivel(t):
    if t.startswith("C_"):
        return t[2:].replace("_", " ").lower()
    if t.startswith("M_"):
        return " + ".join(ATOMO[a] for a in t[2:].split("+") if a) or "sem tipo de hora"
    if t[:1] == "P" and t[1:].replace(".", "").isdigit():
        return t[1:] + "%"
    if t.startswith("NIVEL"):
        return "nível " + t[5:]
    if t == "NUMEROS":
        return "números ou medidas"
    return t if t.startswith("UNIDADE") else t.lower()


class Buscador:
    def __init__(self, depara):
        self.c = depara.reset_index(drop=True).astype({"codigo": str, "codigo_oficial": str})
        self.vec = TfidfVectorizer(analyzer="char_wb", ngram_range=(2, 4), sublinear_tf=True)
        self.X = self.vec.fit_transform(self.c.descricao_limpa)
        self.prep = [preparar(forma_canonica(t, u)[0]) for t, u in zip(self.c.descricao_limpa, self.c.unidade)]
        self.oficial = self.c.drop_duplicates("codigo").set_index("codigo")
        self.trava = threading.Lock()
        self._listas()
        self.vocab = set()
        self.freq = Counter()
        self._aprender(self.c.descricao_limpa)

    def _listas(self):
        self._un = self.c.unidade.tolist()
        self._cod = self.c.codigo.tolist()
        self._of = self.c.codigo_oficial.tolist()
        self._desc = self.c.descricao.tolist()
        self._ofi = {r.codigo: r for r in self.oficial.reset_index().itertuples(index=False)}

    def _aprender(self, textos):
        novos = [t for d in textos for t in str(d).split() if len(t) >= 3 and t.isalpha()]
        self.freq.update(novos)
        self.vocab |= set(novos)
        self._lista = sorted(self.vocab)
        self._perto = {}

    def _parecidas(self, w):
        m = get_close_matches(w, self._lista, n=8, cutoff=0.72)
        m = [c for c in m if not any(o != c and o.startswith(c) and self.freq[c] * 3 < self.freq[o] for o in m)]
        m.sort(key=lambda c: (-round(SequenceMatcher(None, w, c).ratio(), 1), -self.freq[c]))
        return m[:3]

    def _conhecida(self, t):
        return all(x in self.vocab or not x.isalpha() for x in normalizar(t)["n5"].split())

    def corrigir(self, descricao):
        toks = normalizar(descricao)["n4"].split()
        saida, correcoes, i = [], [], 0
        while i < len(toks):
            t = toks[i]
            prox = toks[i + 1] if i + 1 < len(toks) else ""
            if prox and t.isalpha() and prox.isalpha() and not (self._conhecida(t) and self._conhecida(prox)) \
                    and normalizar(t + prox)["n5"] and self._conhecida(t + prox):
                saida.append(t + prox)
                correcoes.append({"de": f"{t} {prox}", "para": t + prox})
                i += 2
                continue
            if len(t) >= 4 and t.isalpha() and not self._conhecida(t):
                if t not in self._perto:
                    self._perto[t] = get_close_matches(t, self._lista, n=1, cutoff=0.8)
                m = self._perto[t]
                if m:
                    saida.append(m[0])
                    correcoes.append({"de": t, "para": m[0]})
                    i += 1
                    continue
            saida.append(t)
            i += 1
        if not correcoes:
            return None, []
        texto = descricao.upper()
        for c in correcoes:
            de = r"\b" + r"\W*".join(map(re.escape, c["de"].split())) + r"\b"
            novo = re.sub(de, c["para"], texto)
            if novo == texto:
                return " ".join(saida)[:40], correcoes
            texto = novo
        return texto[:40], correcoes

    def palavras(self, texto):
        brutas = texto.split()
        limpas = [unicodedata.normalize("NFKD", w).encode("ascii", "ignore").decode().upper().strip(".,;:") for w in brutas]
        conhecida = [not w.isalpha() or len(w) < 3 or all(t in self.vocab or not t.isalpha() for t in normalizar(w)["n5"].split())
                     for w in limpas]
        saida, i = [], 0
        while i < len(limpas):
            w = limpas[i]
            if i + 1 < len(limpas) and w.isalpha() and limpas[i + 1].isalpha() and w + limpas[i + 1] in self.vocab \
                    and not (conhecida[i] and conhecida[i + 1]):
                saida.append({"indice": i, "tamanho": 2, "palavra": f"{brutas[i]} {brutas[i + 1]}", "sugestoes": [w + limpas[i + 1]]})
                i += 2
                continue
            if not conhecida[i]:
                m = self._parecidas(w)
                if m:
                    saida.append({"indice": i, "tamanho": 1, "palavra": brutas[i], "sugestoes": m})
            i += 1
        return saida

    def _sugestao(self, descricao, unidade):
        texto, correcoes = self.corrigir(descricao)
        if not texto:
            return None
        r = self.buscar(texto, unidade, 3, sugerir=False)
        if r["resultado"] == "LIBERAR":
            return None
        return {"descricao": texto, "correcoes": correcoes, "resultado": r["resultado"], "motivo": r["motivo"],
                "codigo": r["candidatos"][0]["codigo"], "descricao_existente": r["candidatos"][0]["descricao"]}

    def atributos(self, descricao, unidade):
        n5 = normalizar(descricao)["n5"]
        a = ler_atributos(n5, unidade)
        try:
            nome = nome_padrao(SimpleNamespace(estruturado=a["mao_obra"] and bool(a["cargo"]), **a))
        except KeyError:
            nome = ""
        return n5, categorizar(n5, a["mao_obra"]), nome

    def adicionar(self, codigo, descricao, unidade, grupo=""):
        return self.adicionar_varios([{"codigo": codigo, "descricao": descricao, "unidade": unidade, "grupo": grupo}])[0]

    def adicionar_varios(self, itens):
        novos = []
        for i in itens:
            n5, categoria, nome = self.atributos(i["descricao"], i["unidade"])
            novos.append({"codigo": i["codigo"], "descricao": i["descricao"], "descricao_limpa": n5,
                          "unidade": i["unidade"], "grupo": i.get("grupo") or "", "categoria": categoria,
                          "codigo_oficial": i.get("oficial") or i["codigo"], "nome_padrao_sugerido": nome})
        with self.trava:
            self.c = pd.concat([self.c, pd.DataFrame(novos)], ignore_index=True)
            self.prep += [preparar(forma_canonica(n["descricao_limpa"], n["unidade"])[0]) for n in novos]
            self.oficial = self.c.drop_duplicates("codigo").set_index("codigo")
            self.X = vstack([self.X, self.vec.transform([n["descricao_limpa"] for n in novos])]).tocsr()
            self._aprender([n["descricao_limpa"] for n in novos])
            self._listas()
        return [{"codigo": n["codigo"], "descricao_limpa": n["descricao_limpa"], "categoria": n["categoria"],
                 "nome_padrao": n["nome_padrao_sugerido"]} for n in novos]

    def buscar(self, descricao, unidade="", limite=10, sugerir=True):
        n5 = normalizar(descricao)["n5"]
        if not n5:
            return {"resultado": "LIBERAR", "motivo": "Descrição vazia depois da limpeza", "candidatos": []}
        s = (self.X @ self.vec.transform([n5]).T).toarray().ravel()
        r = self._classificar(n5, unidade, s, limite)
        if sugerir and r["resultado"] == "LIBERAR":
            r["sugestao"] = self._sugestao(descricao, unidade)
        return r

    def buscar_lote(self, itens, bloco=400):
        unicos, onde = {}, []
        for d, u in itens:
            onde.append(unicos.setdefault((d, u), len(unicos)))
        r = self._lote(list(unicos), bloco)
        primeiro, saida = {}, []
        for i, k in enumerate(onde):
            x = dict(r[k])
            if x["repete_item"] is not None:
                x["repete_item"] = primeiro[x["repete_item"]]
            if k not in primeiro:
                primeiro[k] = i
            elif x["repete_item"] is None and x["descricao_limpa"] and x["resultado"] != "BLOQUEAR":
                x["repete_item"] = primeiro[k]
            saida.append(x)
        return saida

    def _lote(self, itens, bloco):
        n5s = [normalizar(d)["n5"] for d, _ in itens]
        Q = self.vec.transform(n5s)
        saida = []
        for ini in range(0, len(itens), bloco):
            S = (self.X @ Q[ini:ini + bloco].T).toarray()
            for j in range(S.shape[1]):
                i = ini + j
                if not n5s[i]:
                    saida.append({"resultado": "LIBERAR", "motivo": "Descrição vazia depois da limpeza", "candidatos": []})
                else:
                    saida.append(self._classificar(n5s[i], itens[i][1], S[:, j], 3, k=30))
        prep = [preparar(forma_canonica(n, u)[0]) if n else None for n, (_, u) in zip(n5s, itens)]
        grupos = defaultdict(list)
        for i, p in enumerate(prep):
            if p is not None:
                grupos[(itens[i][1], tuple(p[1]))].append(i)
        repete = [None] * len(itens)
        for idx in grupos.values():
            Qg = Q[idx]
            for ini in range(0, len(idx), 500):
                P = (Qg[ini:ini + 500] @ Qg[:ini + 500].T).tocsr()
                for r in range(P.shape[0]):
                    i = idx[ini + r]
                    if saida[i]["resultado"] == "BLOQUEAR":
                        continue
                    ini_l, fim_l = P.indptr[r], P.indptr[r + 1]
                    for c in sorted(c for c, v in zip(P.indices[ini_l:fim_l], P.data[ini_l:fim_l]) if c < ini + r and v >= 0.5):
                        j = idx[c]
                        if comparar(prep[i], itens[i][1], prep[j], itens[j][1])[0] == "DUPLICADO":
                            repete[i] = repete[j] if repete[j] is not None else j
                            break
        pend = [(i, *self.corrigir(itens[i][0])) for i, r in enumerate(saida) if r["resultado"] == "LIBERAR" and repete[i] is None and n5s[i]]
        pend = [(i, normalizar(t)["n5"], c) for i, t, c in pend if t]
        pend = [x for x in pend if x[1]]
        Qc = self.vec.transform([n for _, n, _ in pend]) if pend else None
        for ini in range(0, len(pend), bloco):
            S = (self.X @ Qc[ini:ini + bloco].T).toarray()
            for j in range(S.shape[1]):
                i, n5c, correcoes = pend[ini + j]
                c = self._classificar(n5c, itens[i][1], S[:, j], 3)
                if c["resultado"] != "LIBERAR":
                    top = c["candidatos"][0]
                    trocas = ", ".join(f"{x['de']} → {x['para']}" for x in correcoes)
                    saida[i].update(resultado="REVISAR", motivo=f"Corrigindo a escrita ({trocas}) fica igual a {top['codigo']} {str(top['descricao']).upper()}",
                                    candidatos=[{"codigo": top["codigo"], "descricao": top["descricao"], "aderencia": None, "diferencas": []}])
        resultado = []
        for (descricao, unidade), n5, r, rep in zip(itens, n5s, saida, repete):
            _, categoria, nome = self.atributos(descricao, unidade) if n5 else ("", "", "")
            top = r["candidatos"][0] if r["candidatos"] else None
            resultado.append({"descricao_limpa": n5, "nome_padrao": nome or caber40(n5), "categoria": categoria,
                              "resultado": r["resultado"], "motivo": r["motivo"],
                              "repete_item": None if rep is None else int(rep),
                              "codigo_existente": top and top["codigo"], "descricao_existente": top and top["descricao"],
                              "aderencia": top and top["aderencia"], "diferencas": top["diferencas"] if top else []})
        return resultado

    def agrupar(self, itens):
        n5s = [normalizar(d)["n5"] for d, _ in itens]
        prep = [preparar(forma_canonica(n, u)[0]) if n else None for n, (_, u) in zip(n5s, itens)]
        P = (self.vec.transform(n5s) @ self.vec.transform(n5s).T).toarray()
        grupo = list(range(len(itens)))
        for i in range(len(itens)):
            for j in range(i):
                if prep[i] is not None and prep[j] is not None and P[i, j] >= 0.5 \
                        and comparar(prep[i], itens[i][1], prep[j], itens[j][1])[0] == "DUPLICADO":
                    grupo[i] = grupo[j]
                    break
        return grupo

    def _classificar(self, n5, unidade, s, limite, k=60):
        k = min(k, len(s))
        idx = np.argpartition(-s, k - 1)[:k] if k < len(s) else np.arange(len(s))
        idx = [i for i in idx if s[i] > 0]
        consulta = {u: preparar(forma_canonica(n5, u)[0]) for u in {unidade or self._un[i] for i in idx}}
        achados = []
        for i in idx:
            un = unidade or self._un[i]
            classe, dif = comparar(consulta[un], un, self.prep[i], self._un[i])
            achados.append((ORDEM[classe], -round(float(s[i]), 4), self._of[i], self._cod[i], classe, dif, i))
        achados.sort()
        candidatos, vistos = [], set()
        for _, score, oficial, codigo, classe, dif, i in achados:
            if oficial in vistos:
                continue
            vistos.add(oficial)
            o = self._ofi[oficial]
            candidatos.append({"codigo": oficial, "descricao": o.descricao, "nome_padrao": o.nome_padrao_sugerido,
                               "unidade": o.unidade, "categoria": o.categoria, "aderencia": round(-score * 100),
                               "classe": classe, "diferencas": sorted(legivel(t) for t in dif),
                               "encontrado_por": {"codigo": codigo, "descricao": self._desc[i]}})
            if len(candidatos) == limite:
                break
        if not candidatos:
            return {"resultado": "LIBERAR", "motivo": "Nenhum código parecido", "candidatos": []}
        top = candidatos[0]
        resultado = RESULTADO[top["classe"]]
        motivo = {"BLOQUEAR": f"Já existe: {top['codigo']} {str(top['descricao']).upper()}",
                  "REVISAR": f"Parecido com {top['codigo']}; muda só: {', '.join(top['diferencas'])}",
                  "LIBERAR": "Nenhum código igual; os parecidos são diferentes"}[resultado]
        return {"resultado": resultado, "motivo": motivo, "candidatos": candidatos}


def carregar_buscador(caminho):
    dp, _, _ = deduplicar(ler(caminho), log=lambda *a: None)
    return Buscador(dp)


if __name__ == "__main__":
    args = [a for a in sys.argv[1:]]
    caminho = Path(args.pop(0)) if args and Path(args[0]).suffix.lower() in (".xlsx", ".csv", ".txt") else Path("dados/base_codigos_servico.xlsx")
    b = carregar_buscador(caminho)
    consultas = [(args[0], args[1] if len(args) > 1 else "")] if args else EXEMPLOS
    linhas = []
    for desc, un in consultas:
        r = b.buscar(desc, un, limite=5)
        print(f"\n> {desc} [{un}] => {r['resultado']}: {r['motivo']}")
        for pos, c in enumerate(r["candidatos"], 1):
            print(f"  {pos}. {c['aderencia']:>3}% {c['classe']:<9} {c['codigo']} {c['descricao']} [{c['unidade']}]"
                  f"  {' '.join(c['diferencas'])}")
            linhas.append((desc, un, r["resultado"], pos, c["codigo"], c["descricao"], c["unidade"], c["aderencia"],
                           c["classe"], " ".join(c["diferencas"])))
    if not args:
        Path("saida").mkdir(exist_ok=True)
        pd.DataFrame(linhas, columns=["consulta", "unidade", "resultado", "posicao", "codigo", "descricao",
                                      "unidade_codigo", "aderencia", "classe", "diferencas"]).to_csv(
            "saida/exemplos_similaridade.csv", sep=";", index=False, encoding="utf-8-sig")
        print("\nok: saida/exemplos_similaridade.csv")
