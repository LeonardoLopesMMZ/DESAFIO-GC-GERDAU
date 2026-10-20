import sys
import threading
from pathlib import Path
from types import SimpleNamespace

import numpy as np
import pandas as pd
from scipy.sparse import vstack
from sklearn.feature_extraction.text import TfidfVectorizer

from deduplicar import deduplicar, forma_canonica, ler
from limpar import nome_padrao
from padronizar import categorizar, ler_atributos, normalizar
from regua import comparar, preparar

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

    def adicionar(self, codigo, descricao, unidade, grupo=""):
        n5 = normalizar(descricao)["n5"]
        a = ler_atributos(n5, unidade)
        try:
            nome = nome_padrao(SimpleNamespace(estruturado=a["mao_obra"] and bool(a["cargo"]), **a))
        except KeyError:
            nome = ""
        novo = {"codigo": codigo, "descricao": descricao, "descricao_limpa": n5, "unidade": unidade, "grupo": grupo,
                "categoria": categorizar(n5, a["mao_obra"]), "codigo_oficial": codigo, "nome_padrao_sugerido": nome}
        with self.trava:
            self.c = pd.concat([self.c, pd.DataFrame([novo])], ignore_index=True)
            self.prep.append(preparar(forma_canonica(n5, unidade)[0]))
            self.oficial = self.c.drop_duplicates("codigo").set_index("codigo")
            self.X = vstack([self.X, self.vec.transform([n5])]).tocsr()
        return {"codigo": codigo, "descricao_limpa": n5, "categoria": novo["categoria"], "nome_padrao": nome}

    def buscar(self, descricao, unidade="", limite=10):
        n5 = normalizar(descricao)["n5"]
        if not n5:
            return {"resultado": "LIBERAR", "motivo": "Descrição vazia depois da limpeza", "candidatos": []}
        s = (self.X @ self.vec.transform([n5]).T).toarray().ravel()
        k = min(60, len(s))
        idx = np.argpartition(-s, k - 1)[:k] if k < len(s) else np.arange(len(s))
        idx = [i for i in idx if s[i] > 0]
        consulta = {u: preparar(forma_canonica(n5, u)[0]) for u in {unidade or self.c.unidade[i] for i in idx}}
        achados = []
        for i in idx:
            un = unidade or self.c.unidade[i]
            classe, dif = comparar(consulta[un], un, self.prep[i], self.c.unidade[i])
            achados.append((ORDEM[classe], -round(float(s[i]), 4), self.c.codigo_oficial[i], self.c.codigo[i], classe, dif, i))
        achados.sort()
        candidatos, vistos = [], set()
        for _, score, oficial, codigo, classe, dif, i in achados:
            if oficial in vistos:
                continue
            vistos.add(oficial)
            o = self.oficial.loc[oficial]
            candidatos.append({"codigo": oficial, "descricao": o.descricao, "nome_padrao": o.nome_padrao_sugerido,
                               "unidade": o.unidade, "categoria": o.categoria, "aderencia": round(-score * 100),
                               "classe": classe, "diferencas": sorted(legivel(t) for t in dif),
                               "encontrado_por": {"codigo": codigo, "descricao": self.c.descricao[i]}})
            if len(candidatos) == limite:
                break
        if not candidatos:
            return {"resultado": "LIBERAR", "motivo": "Nenhum código parecido", "candidatos": []}
        top = candidatos[0]
        resultado = RESULTADO[top["classe"]]
        motivo = {"BLOQUEAR": f"Já existe: {top['codigo']} {top['descricao']}",
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
