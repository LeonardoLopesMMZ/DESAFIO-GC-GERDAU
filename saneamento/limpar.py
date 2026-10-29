import re
import sys
from pathlib import Path

import pandas as pd

from padronizar import categorizar, processar

TIPO = {"Hora normal": "HORA NORMAL", "HE Seg-Sab": "HE SEG-SAB", "HE Dom/Fer": "HE DOM/FER",
        "Adicional noturno": "ADIC NOTURNO", "HE noturna Seg-Sab": "HE NOT SEG-SAB",
        "HE noturna Dom/Fer": "HE NOT DOM/FER", "Periculosidade (hora)": "PERICULOSIDADE",
        "Periculosidade s/ HE": "PERIC S/ HE", "Periculosidade (mensal)": "PERIC MENSAL",
        "Insalubridade (hora)": "INSALUBRIDADE", "Posto mensal": "POSTO MENSAL",
        "Mobilizacao/Desmobilizacao": "MOB/DESMOB", "Retroativo": "RETROATIVO"}
CURTO = {"TECNICO SEGURANCA TRABALHO": "TEC SEG TRABALHO", "ENGENHEIRO SEGURANCA TRABALHO": "ENG SEG TRABALHO",
         "AUXILIAR SERVICOS GERAIS": "AUX SERV GERAIS", "PLANEJADOR/TEC PLANEJAMENTO": "PLANEJADOR",
         "ENCANADOR/BOMBEIRO HIDRAULICO": "ENCANADOR", "PORTEIRO/CONTROLADOR ACESSO": "CONTROLADOR ACESSO"}
CURTOS = [("TECNICO DE SEGURANCA DO TRABALHO", "TST"), ("TECNICO SEGURANCA TRABALHO", "TST"),
          ("AUXILIAR DE SERVICOS GERAIS", "ASG"), ("ADICIONAL NOTURNO", "AD NOT"), ("HORA EXTRA", "HE"), ("HORA NORMAL", "HN"),
          ("SEGUNDA A SABADO", "SEG A SAB"), ("DOMINGO E FERIADO", "DOM/FER"), ("DESMOBILIZACAO", "DESMOB"),
          ("MOBILIZACAO", "MOB"), ("PERICULOSIDADE", "PERIC"), ("INSALUBRIDADE", "INSAL"), ("MANUTENCAO", "MANUT"),
          ("INSTALACAO", "INST"), ("DISPOSICAO", "DISP"), ("ENCARREGADO", "ENCARREG"), ("SUPERVISOR", "SUPERV"),
          ("COORDENADOR", "COORD"), ("PLANEJAMENTO", "PLANEJ"), ("CALDEIREIRO", "CALD"), ("ALMOXARIFE", "ALMOX"),
          ("ASSISTENTE", "ASSIST"), ("ESPECIALIZADO", "ESPEC"), ("CONSERVACAO", "CONSERV"), ("ENGENHEIRO", "ENG"),
          ("TRANSPORTE", "TRANSP"), ("GALVANIZADO", "GALV"), ("GALVANIZADA", "GALV"), ("TUBULACAO", "TUBU"),
          ("RECEPCIONISTA", "RECEP"), ("ALPINISTA", "ALPIN"), ("AUXILIAR", "AUX"), ("AJUDANTE", "AJUD"), ("MECANICO", "MEC"),
          ("OPERADOR", "OP"), ("OFICIAL", "OF"), ("TECNICO", "TEC"), ("SEGURANCA", "SEGUR"), ("TRABALHO", "TRAB"),
          ("VIGILANTE", "VIG"), ("LIMPEZA", "LIMP"), ("MAQUINA", "MAQ"), ("SERVICOS", "SERV"), ("CONCRETO", "CONC"),
          ("DIAMETRO", "DIAM"), ("NOTURNO", "NOT"), ("ADICIONAL", "ADIC"), ("CAIXA", "CX")]


def caber40(texto):
    t = " ".join(str(texto or "").upper().split())
    for longo, curto in CURTOS:
        if len(t) <= 40:
            return t
        t = re.sub(rf"\b{longo}\b", curto, t, count=1)
    for miuda in (" DE ", " DA ", " DO ", " DAS ", " DOS ", " E ", " EM "):
        while len(t) > 40 and miuda in f" {t} ":
            t = f" {t} ".replace(miuda, " ", 1).strip()
    if len(t) > 40:
        t = t[:41].rsplit(" ", 1)[0] if " " in t[:41] else t[:40]
    return t


def nome_padrao(r):
    if not r.estruturado:
        return ""
    curto = CURTO.get(r.cargo, r.cargo)
    for cargo in (f"{r.cargo} {r.espec}", f"{curto} {r.espec}", curto):
        nome = f"{cargo.strip()} - {TIPO[r.modificador]}"
        if len(nome) <= 40:
            return nome
    return ""


def limpar(caminho):
    linhas, c = processar(caminho)
    c.loc[c.n5.str.contains(r"\bSEM (?:PERICULOSIDADE|INSALUBRIDADE)\b"), "estruturado"] = False
    c["nome"] = c.apply(nome_padrao, axis=1)
    c["categoria"] = [categorizar(a, b) for a, b in zip(c.n5, c.mao_obra)]
    c["familia"] = (c.cargo + " · " + c.modificador).where(c.estruturado, "")

    c["grupo_id"] = c.n5 + "|" + c.un
    c = c.sort_values(["n_forn", "cod"], ascending=[False, True])
    c["no_grupo"] = c.groupby("grupo_id").cod.transform("size")
    c["referencia"] = c.groupby("grupo_id").cod.transform("first")
    c["situacao"] = "único"
    c.loc[c.no_grupo > 1, "situacao"] = "duplicata"
    c.loc[(c.no_grupo > 1) & (c.cod == c.referencia), "situacao"] = "referência do grupo"
    por_forn = c[["grupo_id"]].assign(forn=c.forns.str.split("|")).explode("forn")
    mesmo_forn = por_forn.groupby(["grupo_id", "forn"]).size().gt(1).groupby("grupo_id").any()
    cortado = c.grupo_id.map(mesmo_forn) | (c.groupby("grupo_id").tamanho.transform("max") >= 38)
    c["confianca"] = ""
    c.loc[c.no_grupo > 1, "confianca"] = "alta"
    c.loc[(c.no_grupo > 1) & cortado, "confianca"] = "conferir: pode ser texto cortado"

    novas = c[["cod", "n5", "categoria", "familia", "nome", "situacao", "referencia", "confianca"]].copy()
    novas["referencia"] = novas.referencia.where(c.no_grupo > 1, "")
    novas.columns = ["cod", "Descrição limpa", "Categoria", "Família (cargo · tipo de hora)", "Nome padrão sugerido",
                     "Situação", "Código de referência", "Confiança"]
    original = pd.read_excel(caminho, sheet_name=0)
    base = original.merge(novas, left_on=original.columns[2], right_on="cod", how="left").drop(columns="cod")

    dup = (c[c.no_grupo > 1]
           .assign(depois=lambda x: x.cod != x.referencia)
           .sort_values(["no_grupo", "referencia", "depois", "cod"], ascending=[False, True, True, True])
           [["referencia", "cod", "desc", "un", "grupo", "categoria", "n_forn", "situacao", "confianca", "n5"]])
    dup.columns = ["Código de referência", "Código", "Descrição original", "Unidade", "Grupo", "Categoria", "Fornecedores",
                   "Situação", "Confiança", "Descrição limpa"]
    return base, dup, c


def salvar(base, dup, destino):
    with pd.ExcelWriter(destino, engine="openpyxl") as xl:
        for nome, df in [("Base limpa", base), ("Duplicatas", dup)]:
            df.to_excel(xl, sheet_name=nome, index=False)
            ws = xl.sheets[nome]
            ws.freeze_panes = "A2"
            ws.auto_filter.ref = ws.dimensions
            for col in ws.columns:
                ws.column_dimensions[col[0].column_letter].width = min(45, max(len(str(x.value or "")) for x in col) + 2)


if __name__ == "__main__":
    caminho = Path(sys.argv[1] if len(sys.argv) > 1 else "dados/base_codigos_servico.xlsx")
    if not caminho.exists():
        sys.exit(f"Planilha não encontrada: {caminho}. Coloque a planilha em dados/ ou passe o caminho.")
    base, dup, c = limpar(caminho)
    destino = Path("saida") / f"{caminho.stem}_limpa.xlsx"
    destino.parent.mkdir(exist_ok=True)
    try:
        salvar(base, dup, destino)
    except PermissionError:
        sys.exit(f"Não consegui gravar {destino}. Feche o arquivo no Excel e rode de novo.")
    d = c[c.situacao == "duplicata"]
    print(f"ok: {len(c)} códigos | {c[c.no_grupo > 1].grupo_id.nunique()} grupos com repetição | "
          f"{len(d)} duplicatas ({(d.confianca == 'alta').sum()} de confiança alta, "
          f"{(d.confianca != 'alta').sum()} para conferir)")
    print(f"ok: {destino}")
