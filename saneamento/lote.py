import io
import unicodedata
from collections import Counter
from pathlib import Path

import pandas as pd
from openpyxl import Workbook
from openpyxl.styles import Alignment, Font, PatternFill
from openpyxl.utils import get_column_letter

MAXIMO = 5000
ORDEM_GERDAU = ["grupo", "fornecedor", "codigo", "descricao", "unidade"]


class PlanilhaInvalida(ValueError):
    pass


def _chave(t):
    t = unicodedata.normalize("NFKD", str(t)).encode("ascii", "ignore").decode().upper()
    return " ".join(t.replace("_", " ").replace(".", " ").split())


def _coluna(nome):
    n = _chave(nome)
    if "DESCRI" in n or n in ("SERVICO", "ITEM", "TEXTO"):
        return "descricao"
    if "FORNEC" in n:
        return "fornecedor"
    if n.startswith("UN") or n in ("UM", "MEDIDA", "UNIDADE MEDIDA"):
        return "unidade"
    if "GRUPO" in n or "CATEGORIA" in n:
        return "grupo"
    if "COD" in n:
        return "codigo"
    return None


def _ler_csv(dados):
    for cod in ("utf-8-sig", "latin-1"):
        try:
            texto = dados.decode(cod)
            break
        except UnicodeDecodeError:
            continue
    primeira = texto.splitlines()[0] if texto.strip() else ""
    sep = ";" if primeira.count(";") >= primeira.count(",") and ";" in primeira else ("\t" if "\t" in primeira else ",")
    return pd.read_csv(io.StringIO(texto), sep=sep, dtype=str, keep_default_na=False)


def ler_planilha(dados, nome):
    ext = Path(nome or "").suffix.lower()
    try:
        if ext in (".csv", ".txt"):
            d = _ler_csv(dados)
        elif ext in (".xlsx", ".xlsm", ""):
            d = pd.read_excel(io.BytesIO(dados), sheet_name=0, dtype=str, engine="openpyxl").fillna("")
        elif ext == ".xls":
            raise PlanilhaInvalida("Arquivo .xls antigo não é aceito. Abra no Excel e salve como .xlsx.")
        else:
            raise PlanilhaInvalida(f"Formato {ext} não aceito. Envie .xlsx ou .csv.")
    except PlanilhaInvalida:
        raise
    except Exception:
        raise PlanilhaInvalida("Não consegui abrir o arquivo. Confira se é um Excel (.xlsx) ou CSV válido.")
    d = d.loc[:, [not str(c).startswith("Unnamed") or d[c].astype(str).str.strip().ne("").any() for c in d.columns]]
    if d.empty or d.shape[1] == 0:
        raise PlanilhaInvalida("A planilha está vazia.")
    nomes = [_coluna(c) for c in d.columns]
    if "descricao" not in nomes:
        if d.shape[1] == 5:
            nomes = ORDEM_GERDAU
        elif d.shape[1] == 1:
            nomes = ["descricao"]
        else:
            raise PlanilhaInvalida("Não achei a coluna de descrição. Dê o nome 'Descrição' para ela ou use o modelo.")
    usadas = {}
    for col, n in zip(d.columns, nomes):
        if n and n not in usadas:
            usadas[n] = col
    linhas = []
    for pos, r in enumerate(d.itertuples(index=False), start=2):
        valores = dict(zip(d.columns, r))
        item = {k: str(valores[c]).strip() for k, c in usadas.items()}
        if not any(item.values()):
            continue
        item["linha"] = pos
        linhas.append(item)
    if not any(l.get("descricao") for l in linhas):
        raise PlanilhaInvalida("Nenhuma linha com descrição na planilha.")
    if len(linhas) > MAXIMO:
        raise PlanilhaInvalida(f"A planilha tem {len(linhas)} linhas; o máximo por vez é {MAXIMO}. Divida em partes.")
    return {"colunas": {k: str(v) for k, v in usadas.items()}, "linhas": linhas}


COLUNAS = [("linha", "Linha", 8), ("codigo_informado", "Código na planilha", 14), ("descricao", "Descrição original", 40),
           ("unidade", "Unidade", 9), ("grupo", "Grupo", 15), ("descricao_limpa", "Descrição padronizada", 40),
           ("nome_padrao", "Nome padrão (até 40)", 40), ("categoria", "Categoria", 20), ("resultado", "Resultado", 13),
           ("codigo_existente", "Código parecido", 14), ("descricao_existente", "Descrição do parecido", 40),
           ("aderencia", "Aderência %", 11), ("acao", "Ação", 16), ("codigo_final", "Código final", 13),
           ("situacao", "Situação", 30), ("motivo", "Motivo", 50)]
CORES = {"BLOQUEAR": "FDE2E1", "REVISAR": "FEF0C7", "LIBERAR": "D1FADF", "REPETIDO": "E0EAFF", "ERRO": "F2F4F7"}


def _aba(ws, titulo, cabecalho, larguras, linhas):
    ws.title = titulo
    ws.append(cabecalho)
    for c in ws[1]:
        c.font = Font(bold=True, color="FFFFFF")
        c.fill = PatternFill("solid", fgColor="1D4ED8")
        c.alignment = Alignment(vertical="center", wrap_text=True)
    for l in linhas:
        ws.append(l)
    for i, w in enumerate(larguras, 1):
        ws.column_dimensions[get_column_letter(i)].width = w
    ws.freeze_panes = "A2"
    if ws.max_row > 1:
        ws.auto_filter.ref = ws.dimensions


def exportar_planilha(linhas):
    wb = Workbook()
    ws = wb.active
    _aba(ws, "Tratado", [t for _, t, _ in COLUNAS], [w for _, _, w in COLUNAS],
         [[l.get(k) if l.get(k) not in (None, "") else None for k, _, _ in COLUNAS] for l in linhas])
    col = [k for k, _, _ in COLUNAS].index("resultado") + 1
    for row in ws.iter_rows(min_row=2):
        cor = CORES.get(str(row[col - 1].value or ""))
        if cor:
            row[col - 1].fill = PatternFill("solid", fgColor=cor)

    prontos, vistos = [], set()
    for l in linhas:
        cod = l.get("codigo_final")
        if cod and cod not in vistos:
            vistos.add(cod)
            prontos.append([cod, l.get("nome_padrao"), l.get("unidade"), l.get("grupo"), l.get("categoria"),
                            l.get("situacao")])
    _aba(wb.create_sheet(), "Pronto para o SAP", ["Código", "Nome padrão (até 40)", "Unidade", "Grupo", "Categoria", "Situação"],
         [12, 42, 9, 15, 20, 30], prontos)

    cont = Counter(l.get("resultado") or "SEM ANÁLISE" for l in linhas)
    nomes = {"BLOQUEAR": "Já existem no catálogo", "REVISAR": "Parecidos (conferir)", "LIBERAR": "Novos de verdade",
             "REPETIDO": "Repetidos dentro da planilha", "ERRO": "Com erro"}
    resumo = [[nomes.get(k, k), v] for k, v in cont.most_common()]
    resumo += [["Total de linhas", len(linhas)], ["Códigos finais distintos", len(prontos)]]
    _aba(wb.create_sheet(), "Resumo", ["Item", "Quantidade"], [34, 12], resumo)

    saida = io.BytesIO()
    wb.save(saida)
    return saida.getvalue()
