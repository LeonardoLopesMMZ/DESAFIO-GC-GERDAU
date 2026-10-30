import importlib.util
import random
import sys
import time
from pathlib import Path

import pandas as pd

from deduplicar import deduplicar, ler
from padronizar import normalizar
from regua import classificar
from similaridade import Buscador

SEMENTE = 17
NOVOS = [("OPERADOR DE DRONE PARA INSPECAO", "H"), ("CAFE DA MANHA ATE 20 PESSOAS", "SV"),
         ("LOCACAO DE BANHEIRO QUIMICO", "MES"), ("TRADUTOR JURAMENTADO INGLES", "H"),
         ("FOTOGRAFO PARA EVENTO", "SV"), ("INSTRUTOR DE LIBRAS", "H"), ("ADESTRADOR DE CAES", "H"),
         ("MANUTENCAO DE ELEVADOR PANORAMICO", "SV"), ("CONSULTORIA EM ESG", "H"), ("SOLDADOR HE 65% SEG A SAB", "H"),
         ("VIGILANTE 6X1 DIURNO 30 DIAS", "SV"), ("PINTOR HE 150% DOM/FER", "H"), ("LAVAGEM DE PAINEL SOLAR", "M2"),
         ("TECNICO DE ROBOTICA", "H"), ("CURVA SOLDAVEL 33 MM", "ST")]


def v1(caminho_v1, depara):
    spec = importlib.util.spec_from_file_location("similaridade_v1", caminho_v1)
    m = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(m)
    return m.Buscador(depara)


def errar(texto, rnd):
    palavras = texto.split()
    alvos = [i for i, w in enumerate(palavras) if w.isalpha() and len(w) >= 6]
    if not alvos:
        return None, None
    i = rnd.choice(alvos)
    w = palavras[i]
    tipo = rnd.choice(["fala", "falta", "troca"])
    if tipo == "fala":
        k = rnd.randint(3, len(w) - 3)
        palavras[i] = w[:k] + " " + w[k:]
    elif tipo == "falta":
        k = rnd.randint(1, len(w) - 2)
        palavras[i] = w[:k] + w[k + 1:]
    else:
        k = rnd.randint(1, len(w) - 3)
        palavras[i] = w[:k] + w[k + 1] + w[k] + w[k + 2:]
    return " ".join(palavras), tipo


def acertou(r, esperado, com_sugestao):
    top = r["candidatos"][0]["codigo"] if r["candidatos"] else None
    if r["resultado"] != "LIBERAR" and top == esperado:
        return "certo"
    s = r.get("sugestao")
    if com_sugestao and s and s["codigo"] == esperado:
        return "certo"
    if r["resultado"] == "BLOQUEAR" and top != esperado:
        return "errado"
    return "nao achou"


def na_lista(r, esperado):
    return any(c["codigo"] == esperado for c in r["candidatos"])


def pares(caminho):
    lab = pd.read_csv(caminho, keep_default_na=False, dtype=str)
    linhas = []
    for a, b, da, ua, db, ub, rot in lab[["cod_a", "cod_b", "desc_a", "un_a", "desc_b", "un_b", "rotulo"]].itertuples(index=False):
        classe, dif = classificar(normalizar(da)["n5"], ua, normalizar(db)["n5"], ub)
        linhas.append({"cod_a": a, "desc_a": da, "un_a": ua, "cod_b": b, "desc_b": db, "un_b": ub,
                       "rotulo": {"S": "mesmo serviço", "N": "serviço diferente", "?": "ambíguo"}[rot],
                       "mokka": classe, "o_que_muda": " ".join(sorted(dif))})
    return pd.DataFrame(linhas)


def main():
    base = Path(sys.argv[1] if len(sys.argv) > 1 else "dados/base_codigos_servico.xlsx")
    rotulados = Path("dados/pares_rotulados.csv")
    antigo = Path(sys.argv[2]) if len(sys.argv) > 2 else None
    saida = Path("saida")
    saida.mkdir(exist_ok=True)
    dp, cat, _ = deduplicar(ler(base), log=lambda *a: None)
    b2 = Buscador(dp)
    b1 = v1(antigo, dp) if antigo else None

    if rotulados.exists():
        p = pares(rotulados)
        p.to_csv(saida / "validacao_pares.csv", sep=";", index=False, encoding="utf-8-sig")
        print("\n1. Pares conferidos à mão x decisão do MOKKA")
        print(pd.crosstab(p.rotulo, p.mokka).to_string())

    rnd = random.Random(SEMENTE)
    ofi = dp[dp.codigo == dp.codigo_oficial].sample(400, random_state=SEMENTE)
    linhas = []
    for cod, desc, un in ofi[["codigo", "descricao", "unidade"]].itertuples(index=False):
        texto, tipo = errar(" ".join(normalizar(desc)["n2"].split()), rnd)
        if not texto:
            continue
        r2 = b2.buscar(texto, un, 5)
        linha = {"codigo": cod, "original": desc, "unidade": un, "consulta_com_erro": texto, "tipo_de_erro": tipo,
                 "v2": acertou(r2, str(cod), True), "v2_resultado": r2["resultado"], "v2_na_lista": na_lista(r2, str(cod)),
                 "v2_sugestao": (r2.get("sugestao") or {}).get("descricao", "")}
        if b1:
            r1 = b1.buscar(texto, un, 5)
            linha.update(v1=acertou(r1, str(cod), False), v1_resultado=r1["resultado"], v1_na_lista=na_lista(r1, str(cod)))
        linhas.append(linha)
    e = pd.DataFrame(linhas)
    e.to_csv(saida / "validacao_erros_de_escrita.csv", sep=";", index=False, encoding="utf-8-sig")
    print(f"\n2. {len(e)} consultas com erro de digitação ou de fala (sorteio fixo, semente {SEMENTE})")
    print(pd.DataFrame({k: e[k].value_counts() for k in ("v1", "v2") if k in e}).fillna(0).astype(int).to_string())
    for k in ("v1", "v2"):
        if k in e:
            print(f"   {k}: código certo entre os 5 primeiros em {e[k + '_na_lista'].mean():.0%}; resultados {dict(e[k + '_resultado'].value_counts())}")

    n = []
    for desc, un in NOVOS:
        r = b2.buscar(desc, un, 3)
        c = r["candidatos"][0] if r["candidatos"] else {}
        n.append({"consulta": desc, "unidade": un, "resultado": r["resultado"], "mais_parecido": c.get("descricao", ""),
                  "aderencia": c.get("aderencia", ""), "o_que_muda": ", ".join(c.get("diferencas", []))})
    n = pd.DataFrame(n)
    n.to_csv(saida / "validacao_sem_correspondencia.csv", sep=";", index=False, encoding="utf-8-sig")
    print(f"\n3. {len(n)} serviços que não existem na base")
    print(n.resultado.value_counts().to_string())

    t = time.perf_counter()
    for desc, un in NOVOS * 4:
        b2.buscar(desc, un, 10)
    print(f"\n4. tempo médio por consulta: {(time.perf_counter() - t) / (len(NOVOS) * 4) * 1000:.0f} ms")
    print(f"\nok: arquivos em {saida}/validacao_*.csv")


if __name__ == "__main__":
    main()
