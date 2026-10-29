import argparse
from pathlib import Path

import pandas as pd

from deduplicar import deduplicar, ler
from limpar import caber40
from padronizar import ABREV

SAIDA = Path("saida")
COLUNAS = ["codigo", "descricao", "descricao_limpa", "descricao_padrao", "unidade", "grupo", "categoria", "status",
           "codigo_canonico", "motivo"]


def montar_tabelas(caminho):
    d = ler(caminho)
    dp, cat, _ = deduplicar(d, log=lambda *a: None)
    base_original = d.rename(columns={"grupo": "grupo_servico", "forn": "codigo_fornecedor", "cod": "codigo_servico",
                                      "desc": "descricao_item", "un": "unidade_medida"})
    oficial = dp.codigo.astype(str) == dp.codigo_oficial.astype(str)
    cs = pd.DataFrame({
        "codigo": dp.codigo.astype(str), "descricao": dp.descricao, "descricao_limpa": dp.descricao_limpa,
        "descricao_padrao": [n or caber40(l) or None for n, l in zip(dp.nome_padrao_sugerido, dp.descricao_limpa)],
        "unidade": dp.unidade, "grupo": dp.grupo,
        "categoria": dp.categoria, "status": oficial.map({True: "ATIVO", False: "BLOQUEADO"}),
        "codigo_canonico": dp.codigo_oficial.astype(str).where(~oficial, None), "motivo": dp.motivo})
    cs = cs.assign(_o=oficial.values).sort_values(["_o", "codigo"], ascending=[False, True]).drop(columns="_o")
    cf = (d[["cod", "forn"]].drop_duplicates().astype(str)
          .rename(columns={"cod": "codigo", "forn": "fornecedor"}))
    dic = pd.DataFrame(sorted(ABREV.items()), columns=["termo", "padrao"])
    maiores = cat.head(4).codigo_oficial.astype(str)
    amostra = set(cs.codigo[cs.codigo.isin(maiores) | cs.codigo_canonico.isin(maiores)])
    amostra |= set(cs.codigo[cs.descricao_limpa.str.startswith("TE SOLD")].head(6))
    amostra |= set(cs.codigo[cs.descricao_limpa.str.contains(r"^SOLDADOR HORA NORMAL")].head(12))
    amostra |= set(cs.codigo_canonico[cs.codigo.isin(amostra)].dropna())
    return base_original, cs, cf, dic, amostra


def sql(v):
    return "NULL" if v is None or v != v else "'" + str(v).replace("'", "''") + "'"


def gerar_data_sql(cs, cf, amostra):
    linhas = [f"INSERT INTO codigo_servico ({', '.join(COLUNAS)}) VALUES ({', '.join(sql(getattr(r, k)) for k in COLUNAS)});"
              for r in cs[cs.codigo.isin(amostra)].itertuples()]
    linhas += [f"INSERT INTO codigo_fornecedor (codigo, fornecedor) VALUES ({sql(r.codigo)}, {sql(r.fornecedor)});"
               for r in cf[cf.codigo.isin(amostra)].itertuples()]
    return "\n".join(linhas) + "\n"


def carregar_no_banco(url, base_original, cs, cf, dic):
    from sqlalchemy import create_engine, text
    with create_engine(url).begin() as con:
        con.execute(text("TRUNCATE codigo_fornecedor, codigo_servico, dicionario, base_original RESTART IDENTITY"))
        base_original.to_sql("base_original", con, if_exists="append", index=False, chunksize=5000)
        cs.to_sql("codigo_servico", con, if_exists="append", index=False, chunksize=5000)
        cf.to_sql("codigo_fornecedor", con, if_exists="append", index=False, chunksize=5000)
        dic.to_sql("dicionario", con, if_exists="append", index=False)


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("planilha", nargs="?", default="dados/base_codigos_servico.xlsx")
    ap.add_argument("--db")
    ap.add_argument("--tudo", action="store_true")
    a = ap.parse_args()
    if not Path(a.planilha).exists():
        raise SystemExit(f"Planilha não encontrada: {a.planilha}")
    base_original, cs, cf, dic, amostra = montar_tabelas(a.planilha)
    if a.tudo:
        amostra = set(cs.codigo)
    SAIDA.mkdir(exist_ok=True)
    for nome, df in [("base_original", base_original), ("codigo_servico", cs), ("codigo_fornecedor", cf), ("dicionario", dic)]:
        df.to_csv(SAIDA / f"{nome}.csv", sep=";", index=False, encoding="utf-8-sig")
    (SAIDA / "data.sql").write_text(gerar_data_sql(cs, cf, amostra), encoding="utf-8")
    print(f"ok: {len(base_original)} linhas originais, {len(cs)} códigos ({(cs.status == 'ATIVO').sum()} oficiais), "
          f"{len(cf)} pares código x fornecedor, {len(dic)} termos -> saida/")
    print(f"ok: data.sql com {len(amostra)} códigos -> saida/data.sql")
    if a.db:
        carregar_no_banco(a.db, base_original, cs, cf, dic)
        print("ok: tudo carregado no PostgreSQL")
