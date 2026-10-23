"""
organizar_planilha.py

Transforma dados bagunçados (planilha, CSV ou texto colado) em um Excel
organizado, com uma aba por setor/grupo de semelhança.

Etapas:
  1. LIMPEZA   - acha o cabeçalho, divide texto colado em colunas, remove
                 lixo, padroniza datas (dd/mm/aaaa) e números (R$ 1.234,56).
  2. AGRUPAR   - por coluna de setor (juntando grafias parecidas) ou
                 automaticamente pela semelhança do conteúdo.
  3. EXPORTAR  - Excel com abas por grupo + "Resumo" + "Relatório".

Dependências:  pip install pandas openpyxl
Opcional (agrupamento automático):  pip install scikit-learn
"""

import re
import unicodedata
import warnings
from difflib import SequenceMatcher

import pandas as pd

# Padrão brasileiro: datas dia/mês/ano, vírgula como decimal.
_NULOS = {"", "-", "--", "n/a", "na", "n.a.", "null", "none", "nan", "s/d", "nd", "?"}
_RE_DATA = re.compile(
    r"^\s*\d{1,4}[/\-.]\d{1,2}[/\-.]\d{1,4}(\s+\d{1,2}:\d{2}(:\d{2})?)?\s*$"
)


# ======================================================================
# Utilitários gerais
# ======================================================================
def _normalizar(texto) -> str:
    """minúsculas, sem acento, sem espaços duplicados."""
    texto = unicodedata.normalize("NFKD", str(texto)).encode("ascii", "ignore").decode()
    return re.sub(r"\s+", " ", texto).strip().lower()


def _nome_aba(nome: str, usados: set) -> str:
    """Nome válido para aba do Excel (máx. 31 caracteres, sem []:*?/\\)."""
    nome = re.sub(r"[\[\]:*?/\\]", "-", str(nome)).strip() or "Sem nome"
    nome = nome[:31]
    base, i = nome, 2
    while nome.lower() in usados:
        sufixo = f" ({i})"
        nome = base[: 31 - len(sufixo)] + sufixo
        i += 1
    usados.add(nome.lower())
    return nome


def _achar_coluna(df: pd.DataFrame, nome: str):
    """Acha a coluna ignorando maiúsculas/acentos/espaços."""
    alvo = _normalizar(nome)
    for c in df.columns:
        if _normalizar(c) == alvo:
            return c
    raise KeyError(f"Coluna '{nome}' não encontrada. Colunas: {list(df.columns)}")


# ======================================================================
# Etapa 1: LIMPEZA
# ======================================================================
def _limpar_celula(v):
    if v is None:
        return None
    try:
        if pd.isna(v):
            return None
    except (TypeError, ValueError):
        pass
    if isinstance(v, str):
        v = re.sub(r"\s+", " ", v.replace("\xa0", " ")).strip()
        if v.lower() in _NULOS:
            return None
    return v


def _para_numero(v):
    """'R$ 1.234,56' -> 1234.56 | '12%' -> 12.0 | '(50)' -> -50 | senão None."""
    if isinstance(v, bool):
        return None
    if isinstance(v, (int, float)):
        return float(v)
    if not isinstance(v, str):
        return None
    s = v.strip()
    neg = s.startswith("-") or (s.startswith("(") and s.endswith(")"))
    s = re.sub(r"(?i)r\$|us\$|€|£|%", "", s)
    s = re.sub(r"[\s()\-+]", "", s) if re.fullmatch(r"[\s()\-+R$\d.,%]+", v.strip()) else s
    if not re.fullmatch(r"\d[\d.,]*", s):
        return None
    if "," in s and "." in s:
        if s.rfind(",") > s.rfind("."):          # 1.234,56
            s = s.replace(".", "").replace(",", ".")
        else:                                    # 1,234.56
            s = s.replace(",", "")
    elif "," in s:                               # 12,5  (decimal brasileiro)
        if s.count(",") > 1:
            s = s.replace(",", "")
        else:
            s = s.replace(",", ".")
    elif "." in s:
        partes = s.split(".")
        if len(partes) > 2 or len(partes[-1]) == 3:   # 1.234 / 1.234.567 (milhar)
            s = s.replace(".", "")
    try:
        n = float(s)
    except ValueError:
        return None
    return -n if neg else n


def _para_data(v):
    """Interpreta datas no padrão brasileiro (dia/mês/ano); ISO (ano primeiro) respeitado."""
    if v is None:
        return pd.NaT
    t = str(v).strip()
    with warnings.catch_warnings():
        warnings.simplefilter("ignore")
        try:
            return pd.to_datetime(t, dayfirst=not re.match(r"^\d{4}", t), errors="coerce")
        except (ValueError, TypeError):
            return pd.NaT


def _ler_bruto(entrada) -> dict:
    """Devolve {nome_aba: DataFrame bruto sem cabeçalho}."""
    if isinstance(entrada, pd.DataFrame):
        df = entrada.copy()
        if not isinstance(df.columns, pd.RangeIndex):
            cab = pd.DataFrame([list(df.columns)])
            df = pd.concat([cab, pd.DataFrame(df.values)], ignore_index=True)
        return {"Dados": df.astype(object)}

    caminho = str(entrada)
    if caminho.lower().endswith((".xlsx", ".xlsm", ".xls")):
        abas = pd.read_excel(entrada, sheet_name=None, header=None, dtype=object)
        return {str(k): v for k, v in abas.items()}

    enc = "utf-8-sig"
    for enc in ("utf-8-sig", "latin-1"):
        try:
            with open(caminho, encoding=enc) as f:
                linhas = f.read().splitlines()
            break
        except UnicodeDecodeError:
            continue
    # lê linha a linha; o separador é detectado depois por _dividir_texto
    df = pd.DataFrame({0: linhas}, dtype=object)
    return {"Dados": df}


def _dividir_texto(serie: pd.Series):
    """Divide uma coluna de texto corrido no melhor separador. None se não achar."""
    s = serie.dropna().astype(str)
    if s.empty:
        return None
    melhor = None
    for pat in (r"\s*;\s*", r"\s*\|\s*", r"\t", r"\s*,\s*", r"\s{2,}", r"\s+-\s+"):
        cont = s.map(lambda t: len(re.split(pat, t)) - 1)
        moda = int(cont.mode().iloc[0])
        if moda < 1:
            continue
        consistencia = float((cont == moda).mean())
        if consistencia < 0.5:
            continue
        chave = (round(consistencia, 2), moda)
        if melhor is None or chave > melhor[0]:
            melhor = (chave, pat)
    if melhor is None:
        return None
    pat = melhor[1]
    partes = s.map(lambda t: re.split(pat, t))
    return pd.DataFrame(partes.tolist(), index=s.index).reindex(serie.index), pat


def _score_cabecalho(linha) -> float:
    vals = []
    for v in linha:
        v = _limpar_celula(v)
        if v is not None:
            vals.append(str(v))
    if len(vals) < 2:
        return 0.0
    texto = sum(_para_numero(v) is None and not _RE_DATA.match(v) for v in vals)
    return texto * (len(set(vals)) / len(vals))


def _nomes_unicos(nomes) -> list:
    saida, vistos = [], {}
    for i, n in enumerate(nomes, 1):
        n = "" if n is None else str(n).strip()
        n = n or f"Coluna_{i}"
        base = n
        vistos[base] = vistos.get(base, 0) + 1
        if vistos[base] > 1:
            n = f"{base}_{vistos[base]}"
        saida.append(n)
    return saida


def _converter_tipos(df: pd.DataFrame, log: list) -> pd.DataFrame:
    for c in df.columns:
        s = df[c]
        nn = s.dropna()
        if nn.empty:
            continue
        txt = nn.astype(str)

        # datas
        if txt.map(lambda t: bool(_RE_DATA.match(t))).mean() >= 0.8:
            base = s.astype(object).where(s.notna(), None).map(
                lambda v: None if v is None else str(v)
            )
            d = base.map(_para_data)
            d = pd.to_datetime(d, errors="coerce")
            falhas = int(d.isna().sum() - s.isna().sum())
            df[c] = d
            log.append(f"Coluna '{c}': padronizada como data (dd/mm/aaaa)"
                       + (f"; {falhas} valor(es) inválido(s) viraram vazio" if falhas else ""))
            continue

        # números / moedas / porcentagens
        if txt.map(lambda t: bool(re.match(r"^0\d", t))).any():
            continue  # códigos com zero à esquerda: mantém como texto
        nums = nn.map(_para_numero)
        if nums.notna().mean() >= 0.8:
            n = s.map(lambda v: None if pd.isna(v) else _para_numero(v))
            falhas = int(n.isna().sum() - s.isna().sum())
            n = pd.to_numeric(n, errors="coerce")
            if n.dropna().map(float.is_integer).all():
                n = n.astype("Int64")
            df[c] = n
            log.append(f"Coluna '{c}': convertida para número"
                       + (f"; {falhas} valor(es) inválido(s) viraram vazio" if falhas else ""))
    return df


def _processar_aba(raw: pd.DataFrame, nome: str, preencher_mesclados: bool, log: list):
    cel = getattr(raw, "map", None) or raw.applymap
    raw = cel(_limpar_celula)
    raw = raw.dropna(how="all").dropna(axis=1, how="all")
    raw.columns = range(raw.shape[1])
    raw = raw.reset_index(drop=True)
    if raw.empty:
        return None

    # texto corrido numa coluna só -> divide em colunas
    contagem = raw.notna().sum()
    if raw.shape[1] == 1 or contagem.max() >= 0.9 * contagem.sum():
        col = contagem.idxmax()
        res = _dividir_texto(raw[col])
        if res is not None:
            novo, pat = res
            log.append(f"Aba '{nome}': texto corrido dividido em {novo.shape[1]} colunas")
            raw = novo.map(_limpar_celula) if hasattr(novo, "map") else novo.applymap(_limpar_celula)
            raw = raw.dropna(how="all").dropna(axis=1, how="all")
            raw.columns = range(raw.shape[1])
            raw = raw.reset_index(drop=True)

    # acha a linha de cabeçalho (pula títulos e linhas de lixo no topo)
    limite = min(20, len(raw))
    scores = [_score_cabecalho(raw.iloc[i]) for i in range(limite)]
    if not scores or max(scores) == 0:
        idx = 0
    else:
        idx = next(i for i, sc in enumerate(scores) if sc >= 0.8 * max(scores))
    if idx > 0:
        log.append(f"Aba '{nome}': {idx} linha(s) de título/lixo no topo ignoradas")

    cab = _nomes_unicos(raw.iloc[idx].tolist())
    df = raw.iloc[idx + 1:].reset_index(drop=True)
    df.columns = cab

    # remove cabeçalhos repetidos no meio dos dados
    cab_norm = [_normalizar(c) for c in cab]
    rep = df.apply(
        lambda r: sum(_normalizar(v) == cn for v, cn in zip(r, cab_norm) if v is not None)
        >= max(2, 0.6 * len(cab)),
        axis=1,
    )
    if rep.any():
        log.append(f"Aba '{nome}': {int(rep.sum())} cabeçalho(s) repetido(s) removido(s)")
        df = df[~rep].reset_index(drop=True)

    # células mescladas -> repete o valor de cima
    if preencher_mesclados:
        for c in df.columns:
            vazios = df[c].isna().mean()
            if 0 < vazios < 0.6 and df[c].dropna().map(lambda v: _para_numero(v) is None).all():
                df[c] = df[c].ffill()
        log.append(f"Aba '{nome}': células vazias de colunas de texto preenchidas com o valor acima")

    return _converter_tipos(df, log)


def limpar_dados(entrada, preencher_mesclados=False):
    """
    Limpa e estrutura dados bagunçados. Aceita .xlsx/.csv/.txt ou DataFrame.
    Retorna (DataFrame limpo, lista com o relatório do que foi feito).
    """
    log, partes = [], []
    abas = _ler_bruto(entrada)
    for nome, raw in abas.items():
        df = _processar_aba(raw, nome, preencher_mesclados, log)
        if df is not None and not df.empty:
            if len(abas) > 1:
                df.insert(0, "Aba_origem", nome)
            partes.append(df)
    if not partes:
        raise ValueError("Nenhum dado encontrado na entrada.")
    df = pd.concat(partes, ignore_index=True)
    n0 = len(df)
    df = df.drop_duplicates().reset_index(drop=True)
    if len(df) < n0:
        log.append(f"{n0 - len(df)} linha(s) duplicada(s) removida(s)")
    return df, log


# ======================================================================
# Etapa 2: AGRUPAR
# ======================================================================
def _agrupar_rotulos(serie: pd.Series, limiar: float, sinonimos=None) -> pd.Series:
    """Junta rótulos parecidos ("Financeiro", "financeiro ", "Finaceiro")."""
    alias = {}
    for principal, outros in (sinonimos or {}).items():
        for o in outros:
            alias[_normalizar(o)] = _normalizar(principal)

    def _norm(v):
        if pd.isna(v):
            return None
        n = _normalizar(v)
        return alias.get(n, n)

    norm = serie.map(_norm)
    freq = norm.value_counts()

    representantes, mapa = [], {}
    for rotulo in freq.index:  # do mais frequente para o menos
        for rep in representantes:
            if SequenceMatcher(None, rotulo, rep).ratio() >= limiar:
                mapa[rotulo] = rep
                break
        else:
            representantes.append(rotulo)
            mapa[rotulo] = rotulo

    grupo_norm = norm.map(mapa)
    exibicao = {}
    for rep in representantes:
        originais = serie[grupo_norm == rep].astype(str).str.strip()
        exibicao[rep] = originais.value_counts().index[0]
    return grupo_norm.map(exibicao)


def _agrupar_por_conteudo(df, colunas_texto, n_grupos, max_grupos=8) -> pd.Series:
    try:
        from sklearn.cluster import KMeans
        from sklearn.feature_extraction.text import TfidfVectorizer
        from sklearn.metrics import silhouette_score
    except ImportError as e:
        raise ImportError(
            "O modo automático precisa de scikit-learn: pip install scikit-learn "
            "(ou informe coluna_setor=...)"
        ) from e

    if colunas_texto is None:
        colunas_texto = [
            c for c in df.columns
            if not pd.api.types.is_numeric_dtype(df[c])
            and not pd.api.types.is_datetime64_any_dtype(df[c])
        ]
    else:
        colunas_texto = [_achar_coluna(df, c) for c in colunas_texto]
    if not colunas_texto:
        raise ValueError("Nenhuma coluna de texto encontrada para comparar semelhança.")

    textos = df[colunas_texto].fillna("").astype(str).agg(" ".join, axis=1).map(_normalizar)
    X = TfidfVectorizer(ngram_range=(1, 2), sublinear_tf=True).fit_transform(textos)
    vet_termos = TfidfVectorizer(ngram_range=(1, 2), sublinear_tf=True).fit(textos).get_feature_names_out()

    n = X.shape[0]
    if n < 3:
        return pd.Series(["Grupo 1"] * n, index=df.index)

    if n_grupos is None:
        melhor_k, melhor_score = 2, -1
        for k in range(2, min(max_grupos, n - 1) + 1):
            rot = KMeans(n_clusters=k, n_init=10, random_state=0).fit_predict(X)
            if len(set(rot)) < 2:
                continue
            sc = silhouette_score(X, rot)
            if sc > melhor_score:
                melhor_k, melhor_score = k, sc
        n_grupos = melhor_k

    modelo = KMeans(n_clusters=n_grupos, n_init=10, random_state=0).fit(X)
    nomes = {}
    for g in range(n_grupos):
        top = modelo.cluster_centers_[g].argsort()[::-1][:3]
        nomes[g] = f"G{g + 1} - " + ", ".join(vet_termos[i] for i in top)
    return pd.Series(modelo.labels_, index=df.index).map(nomes)


# ======================================================================
# Função principal
# ======================================================================
def organizar_planilha(
    entrada,
    saida="planilha_organizada.xlsx",
    coluna_setor=None,
    colunas_texto=None,
    n_grupos=None,
    limiar_similaridade=0.8,
    sinonimos=None,
    limpar=True,
    preencher_mesclados=False,
):
    """
    Transforma dados bagunçados em uma planilha com abas por setor/semelhança.

    Parâmetros
    ----------
    entrada : .xlsx/.csv/.txt (inclusive texto colado) ou DataFrame.
    saida : caminho do .xlsx gerado.
    coluna_setor : coluna que indica o setor/categoria (pode estar escrita de
        forma inconsistente). Se None, agrupa pela semelhança do conteúdo.
    colunas_texto : colunas comparadas no modo automático (padrão: todas de texto).
    n_grupos : nº de grupos no modo automático (padrão: escolhe sozinho).
    limiar_similaridade : 0 a 1; maior = mais rígido ao juntar rótulos parecidos.
    sinonimos : ex. {"RH": ["Recursos Humanos", "Pessoal"], "TI": ["Tecnologia"]}.
    limpar : aplica a limpeza (cabeçalho, divisão de texto, datas, números).
    preencher_mesclados : repete o valor de cima em células vazias de colunas
        de texto (útil quando o setor aparece só na primeira linha do bloco).

    Retorna
    -------
    dict {nome_do_grupo: DataFrame}
    """
    if limpar:
        df, log = limpar_dados(entrada, preencher_mesclados)
    else:
        df = entrada.copy() if isinstance(entrada, pd.DataFrame) else pd.read_excel(entrada)
        df = df.dropna(how="all").dropna(axis=1, how="all").drop_duplicates().reset_index(drop=True)
        log = []

    if coluna_setor:
        col = _achar_coluna(df, coluna_setor)
        grupos = _agrupar_rotulos(df[col], limiar_similaridade, sinonimos).fillna("Sem classificação")
        df[col] = grupos.where(df[col].notna(), df[col])  # unifica os nomes na própria coluna
        log.append(f"Agrupado pela coluna '{col}' (rótulos parecidos foram unificados)")
    else:
        grupos = _agrupar_por_conteudo(df, colunas_texto, n_grupos)
        log.append("Agrupado automaticamente pela semelhança do conteúdo")

    resultado = {nome: p.reset_index(drop=True) for nome, p in df.groupby(grupos, sort=True)}

    # formato de data no Excel
    cols_data = [c for c in df.columns if pd.api.types.is_datetime64_any_dtype(df[c])]
    tem_hora = any(
        (df[c].dropna().dt.normalize() != df[c].dropna()).any() for c in cols_data
    )
    fmt = "DD/MM/YYYY HH:MM" if tem_hora else "DD/MM/YYYY"

    usados = {"resumo", "relatório"}
    with pd.ExcelWriter(saida, engine="openpyxl", datetime_format=fmt, date_format=fmt) as xl:
        resumo = pd.DataFrame(
            {"Grupo": list(resultado), "Linhas": [len(d) for d in resultado.values()]}
        ).sort_values("Linhas", ascending=False)
        resumo.to_excel(xl, sheet_name="Resumo", index=False)
        pd.DataFrame({"O que foi feito": log}).to_excel(xl, sheet_name="Relatório", index=False)

        for nome, dados in resultado.items():
            aba = _nome_aba(nome, usados)
            dados.to_excel(xl, sheet_name=aba, index=False)

        for ws in xl.book.worksheets:  # visual
            ws.freeze_panes = "A2"
            for col in ws.columns:
                larg = max(len(str(c.value)) if c.value is not None else 0 for c in col)
                ws.column_dimensions[col[0].column_letter].width = min(larg + 2, 60)

    print(f"{len(df)} linhas organizadas em {len(resultado)} grupos -> {saida}")
    for linha in log:
        print(" •", linha)
    return resultado


# ======================================================================
# Exemplos
# ======================================================================
if __name__ == "__main__":
    # Planilha com setor (mesmo bagunçada):
    # organizar_planilha("dados.xlsx", "organizado.xlsx", coluna_setor="Setor",
    #                    sinonimos={"RH": ["Recursos Humanos"]})

    # Texto colado / sem coluna de setor -> agrupa por semelhança:
    # organizar_planilha("dados.txt", "organizado.xlsx")
    pass
