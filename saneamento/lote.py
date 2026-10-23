import io
import re
import unicodedata
from collections import Counter
from pathlib import Path

import pandas as pd
from openpyxl import Workbook
from openpyxl.styles import Alignment, Font, PatternFill
from openpyxl.utils import get_column_letter

from organizar_planilha import _agrupar_rotulos, _limpar_celula, _nome_aba, _normalizar

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


SEPARADORES = (r"\s*;\s*", r"\s*\|\s*", r"\t")
GRUPOS = {"Civil": ["civil", "civel", "obras civis", "obra civil"],
          "Facilities": ["facilities", "facilites", "facility", "facilidades", "predial"],
          "Eletromecânica": ["eletromecanica", "eletro mecanica", "eletromec", "eletrica e mecanica", "eletromecanico"]}
CANONICO = {_normalizar(o): k for k, v in GRUPOS.items() for o in v + [k]}


def _abas(dados, nome):
    ext = Path(nome or "").suffix.lower()
    if ext == ".xls":
        raise PlanilhaInvalida("Arquivo .xls antigo não é aceito. Abra no Excel e salve como .xlsx.")
    if ext not in (".xlsx", ".xlsm", ".csv", ".txt", ""):
        raise PlanilhaInvalida(f"Formato {ext} não aceito. Envie .xlsx, .csv ou cole o texto.")
    try:
        if ext in (".xlsx", ".xlsm", ""):
            return pd.read_excel(io.BytesIO(dados), sheet_name=None, header=None, dtype=object, engine="openpyxl")
        for cod in ("utf-8-sig", "latin-1"):
            try:
                texto = dados.decode(cod)
                break
            except UnicodeDecodeError:
                continue
        return {"Texto": pd.DataFrame({0: texto.splitlines()}, dtype=object)}
    except Exception:
        raise PlanilhaInvalida("Não consegui abrir o arquivo. Confira se é um Excel (.xlsx) ou CSV válido.")


def _dividir(serie):
    s = serie.dropna().astype(str)
    melhor = None
    for pat in SEPARADORES:
        cont = s.map(lambda t: len(re.split(pat, t)) - 1)
        if cont.empty:
            continue
        moda = int(cont.mode().iloc[0])
        if moda < 1:
            continue
        consistencia = float((cont == moda).mean())
        if consistencia >= 0.5 and (melhor is None or (consistencia, moda) > melhor[0]):
            melhor = ((consistencia, moda), pat)
    if melhor is None:
        return None
    partes = s.map(lambda t: [p.strip() for p in re.split(melhor[1], t)])
    return pd.DataFrame(partes.tolist(), index=s.index)


def _texto(v):
    if v is None or (isinstance(v, float) and v != v):
        return ""
    if isinstance(v, float) and v.is_integer():
        return str(int(v))
    return str(v).strip()


def _aba(raw, nome, log):
    raw = raw.map(_limpar_celula).dropna(how="all").dropna(axis=1, how="all")
    if raw.empty:
        return None
    if raw.shape[1] == 1:
        partes = _dividir(raw.iloc[:, 0])
        if partes is not None:
            log.append(f"Aba '{nome}': texto corrido dividido em {partes.shape[1]} colunas")
            raw = partes.map(_limpar_celula).dropna(how="all").dropna(axis=1, how="all")
    raw.columns = range(raw.shape[1])
    cab = None
    for i in range(min(20, len(raw))):
        if "descricao" in [_coluna(v) if v is not None else None for v in raw.iloc[i]]:
            cab = i
            break
    if cab is not None:
        if cab:
            log.append(f"Aba '{nome}': {cab} linha(s) de título ignorada(s) no topo")
        cabecalho = raw.iloc[cab].tolist()
        nomes = [_coluna(c) if c is not None else None for c in cabecalho]
        dados = raw.iloc[cab + 1:]
    elif raw.shape[1] == 5:
        nomes, dados = ORDEM_GERDAU, raw
        log.append(f"Aba '{nome}': sem cabeçalho, usei a ordem da base da Gerdau")
    elif raw.shape[1] == 1:
        nomes, dados = ["descricao"], raw
        log.append(f"Aba '{nome}': sem cabeçalho, cada linha virou uma descrição")
    else:
        return None
    usadas = {}
    for pos, n in enumerate(nomes):
        if n and n not in usadas:
            usadas[n] = pos
    df = pd.DataFrame({k: dados[p].map(_texto) for k, p in usadas.items()})
    df["linha_excel"] = [int(i) + 1 for i in dados.index]
    df["aba"] = nome
    if cab is not None:
        titulo = _normalizar(cabecalho[usadas["descricao"]])
        repetido = df["descricao"].map(_normalizar) == titulo
        if repetido.any():
            log.append(f"Aba '{nome}': {int(repetido.sum())} cabeçalho(s) repetido(s) no meio removido(s)")
            df = df[~repetido]
    return df


def _unificar_grupos(df, log):
    if "grupo" not in df or not df["grupo"].str.strip().any():
        return df
    original = df["grupo"].where(df["grupo"] != "", None)
    unidos = _agrupar_rotulos(original, 0.8, GRUPOS)
    final = unidos.map(lambda g: CANONICO.get(_normalizar(g), g) if isinstance(g, str) else g)
    mudou = original.notna() & (original.astype(str).str.strip() != final.astype(str))
    if mudou.any():
        trocas = (pd.DataFrame({"de": original[mudou], "para": final[mudou]}).drop_duplicates()
                  .groupby("para")["de"].apply(lambda s: ", ".join(f"'{x}'" for x in sorted(set(s)))))
        for para, de in trocas.items():
            log.append(f"Grupo: {de} virou '{para}'")
    df["grupo"] = final.fillna("")
    return df


def ler_planilha(dados, nome):
    abas = _abas(dados, nome)
    log, partes = [], []
    for aba, raw in abas.items():
        df = _aba(raw, str(aba), log)
        if df is not None and not df.empty:
            partes.append(df)
    if not partes:
        raise PlanilhaInvalida("Não achei a coluna de descrição. Dê o nome 'Descrição' para ela ou use o modelo.")
    df = pd.concat(partes, ignore_index=True).fillna("")
    campos = [c for c in ("descricao", "unidade", "grupo", "codigo", "fornecedor") if c in df]
    df = df[df[campos].ne("").any(axis=1)]
    antes = len(df)
    df = df.drop_duplicates(subset=campos)
    if len(df) < antes:
        log.append(f"{antes - len(df)} linha(s) repetida(s) idêntica(s) removida(s)")
    df = _unificar_grupos(df, log)
    if not df["descricao"].ne("").any():
        raise PlanilhaInvalida("Nenhuma linha com descrição na planilha.")
    if len(df) > MAXIMO:
        raise PlanilhaInvalida(f"A planilha tem {len(df)} linhas; o máximo por vez é {MAXIMO}. Divida em partes.")
    varias = df["aba"].nunique() > 1
    linhas = []
    for n, r in enumerate(df.itertuples(index=False), start=1):
        item = {c: getattr(r, c) for c in campos}
        item["linha"] = n if varias else int(r.linha_excel)
        item["origem"] = f"{r.aba}, linha {r.linha_excel}"
        linhas.append(item)
    if not log:
        log.append("Planilha já estava organizada: nada precisou ser arrumado")
    return {"colunas": {c: c for c in campos}, "linhas": linhas, "relatorio": log}


COLUNAS = [("linha", "Linha", 8), ("origem", "Origem", 18), ("codigo_informado", "Código na planilha", 14), ("descricao", "Descrição original", 40),
           ("unidade", "Unidade", 9), ("grupo", "Grupo", 15), ("descricao_limpa", "Descrição padronizada", 40),
           ("nome_padrao", "Nome padrão (até 40)", 40), ("categoria", "Categoria", 20), ("resultado", "Resultado", 13),
           ("codigo_existente", "Código parecido", 14), ("descricao_existente", "Descrição do parecido", 40),
           ("aderencia", "Aderência %", 11), ("acao", "Ação", 16), ("codigo_final", "Código final", 13),
           ("situacao", "Situação", 30), ("motivo", "Motivo", 50)]
CORES = {"BLOQUEAR": "FDE2E1", "REVISAR": "FEF0C7", "LIBERAR": "D1FADF", "REPETIDO": "E0EAFF", "ERRO": "F2F4F7"}


def _folha(ws, titulo, cabecalho, larguras, linhas):
    ws.title = titulo
    ws.append(cabecalho)
    for c in ws[1]:
        c.font = Font(bold=True, color="FFFFFF")
        c.fill = PatternFill("solid", fgColor="0B3A6E")
        c.alignment = Alignment(vertical="center", wrap_text=True)
    for l in linhas:
        ws.append(l)
    for i, w in enumerate(larguras, 1):
        ws.column_dimensions[get_column_letter(i)].width = w
    ws.freeze_panes = "A2"
    if ws.max_row > 1:
        ws.auto_filter.ref = ws.dimensions


def _tratado(ws, titulo, linhas):
    _folha(ws, titulo, [t for _, t, _ in COLUNAS], [w for _, _, w in COLUNAS],
           [[l.get(k) if l.get(k) not in (None, "") else None for k, _, _ in COLUNAS] for l in linhas])
    col = [k for k, _, _ in COLUNAS].index("resultado") + 1
    for row in ws.iter_rows(min_row=2):
        cor = CORES.get(str(row[col - 1].value or ""))
        if cor:
            row[col - 1].fill = PatternFill("solid", fgColor=cor)


def exportar_planilha(linhas, relatorio=None):
    wb = Workbook()
    _tratado(wb.active, "Tratado", linhas)

    prontos, vistos = [], set()
    for l in linhas:
        cod = l.get("codigo_final")
        if cod and cod not in vistos:
            vistos.add(cod)
            prontos.append([cod, l.get("nome_padrao"), l.get("unidade"), l.get("grupo"), l.get("categoria"),
                            l.get("situacao")])
    _folha(wb.create_sheet(), "Pronto para o SAP", ["Código", "Nome padrão (até 40)", "Unidade", "Grupo", "Categoria", "Situação"],
         [12, 42, 9, 15, 20, 30], prontos)

    cont = Counter(l.get("resultado") or "SEM ANÁLISE" for l in linhas)
    nomes = {"BLOQUEAR": "Já existem no catálogo", "REVISAR": "Parecidos (conferir)", "LIBERAR": "Novos de verdade",
             "REPETIDO": "Repetidos dentro da planilha", "ERRO": "Com erro"}
    resumo = [[nomes.get(k, k), v] for k, v in cont.most_common()]
    resumo += [["Total de linhas", len(linhas)], ["Códigos finais distintos", len(prontos)]]
    _folha(wb.create_sheet(), "Resumo", ["Item", "Quantidade"], [34, 12], resumo)
    _folha(wb.create_sheet(), "Relatório", ["O que foi arrumado na planilha"], [90],
           [[t] for t in (relatorio or ["Sem relatório"])])
    usados = {"tratado", "pronto para o sap", "resumo", "relatório"}
    grupos = sorted({l.get("grupo") or "Sem grupo" for l in linhas})
    if len(grupos) > 1:
        for g in grupos:
            _tratado(wb.create_sheet(), _nome_aba(g, usados), [l for l in linhas if (l.get("grupo") or "Sem grupo") == g])

    saida = io.BytesIO()
    wb.save(saida)
    return saida.getvalue()
