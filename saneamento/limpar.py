import re
import sys
import unicodedata
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
FRASES = [("TECNICO DE SEGURANCA DO TRABALHO", "TST"), ("TECNICO EM SEGURANCA DO TRABALHO", "TST"),
          ("TECNICO SEGURANCA TRABALHO", "TST"), ("AUXILIAR DE SERVICOS GERAIS", "ASG"), ("AUXILIAR SERVICOS GERAIS", "ASG"),
          ("ADICIONAL NOTURNO", "AD NOT"), ("HORA EXTRA", "HE"), ("HORA NORMAL", "HN"), ("SEGUNDA A SABADO", "SEG A SAB"),
          ("DOMINGO E FERIADO", "DOM/FER"), ("DOMINGOS E FERIADOS", "DOM/FER"), ("DOMINGO FERIADO", "DOM/FER")]
PALAVRAS = {"PERICULOSIDADE": "PERIC", "INSALUBRIDADE": "INSAL", "ESPECIALIZADO": "ESPEC", "ESPECIALIZADA": "ESPEC",
            "RECEPCIONISTA": "RECEP", "EXTRAORDINARIA": "EXTRA", "DESMOBILIZACAO": "DESMOB", "INSTRUMENTISTA": "INSTRUM",
            "PLANEJAMENTO": "PLANEJ", "MOBILIZACAO": "MOB", "ENCARREGADO": "ENCARREG", "ENCARREGADA": "ENCARREG",
            "CALDEIREIRO": "CALD", "COORDENADOR": "COORD", "CONSERVACAO": "CONSERV", "GALVANIZADO": "GALV",
            "GALVANIZADA": "GALV", "MANUTENCAO": "MANUT", "DISPOSICAO": "DISP", "INSTALACAO": "INST",
            "TRANSPORTE": "TRANSP", "SUPERVISOR": "SUPERV", "ENGENHEIRO": "ENG", "ALMOXARIFE": "ALMOX",
            "ASSISTENTE": "ASSIST", "TUBULACAO": "TUBU", "MOTORISTA": "MOTORI", "SEGURANCA": "SEGUR", "ALPINISTA": "ALPIN",
            "ADICIONAL": "ADIC", "VIGILANTE": "VIG", "PERICULOSO": "PERIC", "PERICULOSA": "PERIC", "INSALUBRE": "INSAL",
            "AUXILIAR": "AUX", "AJUDANTE": "AJUD", "MECANICO": "MEC", "OPERADOR": "OP", "OFICIAL": "OF", "TECNICO": "TEC",
            "TECNICA": "TEC", "TRABALHO": "TRAB", "RESIDUAL": "RESID", "EVENTUAL": "EVENTU", "LIMPEZA": "LIMP",
            "MAQUINA": "MAQ", "SERVICOS": "SERV", "CONCRETO": "CONC", "DIAMETRO": "DIAM", "NOTURNO": "NOT",
            "NOTURNA": "NOT", "CAIXA": "CX"}
RESERVADAS = set(PALAVRAS.values()) | {"ELET", "ELETR", "PERI", "MANU", "MANT", "PLAN", "CORD", "NORM", "DISPOS", "SERVIC",
                                       "CONCR", "INSALUB", "PERICUL", "EXTRAORD", "NOTUR", "NOTURN", "EVENTUA", "HNOT",
                                       "HINS", "HEXTRA"}
CURTAS = {"COM": "C/", "SEM": "S/", "PARA": "P/"}
MINIMAS = {"DE", "DA", "DO", "DAS", "DOS", "E", "EM", "NO", "NA"}
CARGO = re.compile(r"^(ELETRICISTA|SOLDADOR|PEDREIRO|PINTOR|CARPINTEIRO|ENCANADOR|MONTADOR|MARTELETEIRO|MACARIQUEIRO|"
                   r"JARDINEIRO|TORNEIRO|ARMADOR|TELHADISTA|VIDRACEIRO|SERRALHEIRO|INSPETOR|SERVENTE|ANDAIME|ANDAIMISTA|"
                   r"RESGATISTA|ZELADOR|DEDETIZADOR|ANALISTA|PROJETISTA|LAMINADOR|CONTROLADOR|PORTEIRO|BOMBEIRO|MECANICA|"
                   r"ELETRICA|CIVIL)$")


def _sem_acento(t):
    return unicodedata.normalize("NFKD", t).encode("ascii", "ignore").decode()


def encurtar(palavra):
    s = _sem_acento(palavra)
    if len(s) < 7 or not s.isalpha() or len(s) != len(palavra):
        return None
    for n in range(4, min(8, len(s) - 2)):
        if s[n] in "AEIOU" and s[n - 1] not in "AEIOU" and s[:n] not in RESERVADAS:
            return palavra[:n]
    return None


def caber40(texto):
    t = " ".join(str(texto or "").upper().split())
    cabe = lambda: len(t) <= 40
    for longo, curto in FRASES:
        if cabe():
            break
        m = re.search(rf"\b{longo.replace(' ', r'\s+')}\b", _sem_acento(t))
        if m and len(_sem_acento(t)) == len(t):
            t = t[:m.start()] + curto + t[m.end():]
    p = t.split()
    for i in range(len(p) - 1, 0, -1):
        if cabe():
            break
        w = _sem_acento(p[i])
        if w in CURTAS:
            p[i] = CURTAS[w]
            t = " ".join(p)
        elif w in MINIMAS and i < len(p) - 1:
            del p[i]
            t = " ".join(p)
    for troca in (lambda w: PALAVRAS.get(_sem_acento(w)),
                  lambda w: None if CARGO.match(_sem_acento(w)) else encurtar(w),
                  encurtar):
        for i in sorted(range(len(p)), key=lambda i: -len(p[i])):
            if cabe():
                break
            novo = troca(p[i])
            if novo and len(novo) < len(p[i]):
                p[i] = novo
                t = " ".join(p)
    if not cabe():
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
