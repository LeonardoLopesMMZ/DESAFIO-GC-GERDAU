import sys
from pathlib import Path

import pandas as pd

from padronizar import categorizar, processar

SAIDA = Path("saida")
SV_INICIO = r"^SV\b"
HORA = ["Hora normal", "HE Seg-Sab", "HE Dom/Fer", "Adicional noturno", "HE noturna Seg-Sab", "HE noturna Dom/Fer",
        "Periculosidade (hora)", "Insalubridade (hora)", "Periculosidade s/ HE"]


def n(x):
    return f"{int(x):,}".replace(",", ".")


def pct(x):
    return f"{x * 100:.1f}%".replace(".", ",")


COLUNAS = ["Grupo de Serviço", "Código do Fornecedor", "Código do Serviço", "Descrição do Item", "Unidade medida"]
EXEMPLOS = ["SV-MEC-01-HN", "MEC.MANUT.NORM", "TEC SEG Valor HE (R$) Feriados e Dom", "H.E.DF-Téc. de Seg. do trabalho-ADM",
            "MONTADOR(A) LIDER(HE NOT DOM.FER)", "Sv TST H Adm HE 100%", "SOLDADOR (PRERICULOSIDADE)",
            "AUX. DE SERVIÇOS GERAIS - I20%", "SV VIGILANTE 18H00 AS 06H00 12 X 36 SEG.", "MOB/DESMOB MAQUINA DE JATEAMENTO ABRASIV",
            "SV-CX SINFONADA C/GRELHA 150X150X50 MM", "SV CABO FLEXÍVEL 25,0 mm²", "SV - LOCAÇÃO DE EQUIPAMENTOS",
            "CAMINHÃO CARROCERIA MUNCK - HORA PRODUTI", "Mousse de limão grande - até 22 pessoas"]


def qualidade_e_eda(caminho, linhas, c):
    original = pd.read_excel(caminho, sheet_name=0, dtype=str) if str(caminho).endswith("xlsx") else linhas.copy()
    original.columns = COLUNAS
    L = ["\n## 5. Qualidade por coluna\n", "| Coluna | Tipo | Preenchidos | Faltantes | Valores distintos | Exemplo |",
         "| --- | --- | --- | --- | --- | --- |"]
    for col in COLUNAS:
        v = original[col].dropna().astype(str).str.strip()
        tipo = "número (código)" if v.str.fullmatch(r"\d+").all() else "texto"
        L.append(f"| {col} | {tipo} | {n(len(v))} | {n(len(original) - len(v))} | {n(v.nunique())} | {v.iloc[0] if len(v) else ''} |")
    por_cod = linhas.groupby("cod")
    L += ["", f"- Linhas repetidas por inteiro: {n(original.duplicated().sum())}.",
          f"- Código com mais de uma descrição: {n((por_cod.desc.nunique() > 1).sum())}; com mais de uma unidade: "
          f"{n((por_cod.un.nunique() > 1).sum())}; em mais de um grupo: {n((por_cod.grupo.nunique() > 1).sum())}.",
          f"- Descrições com espaço invisível, duplo ou nas bordas: {n((c.desc != c.n1).sum())}; em minúsculas ou mistas: "
          f"{n((c.desc != c.desc.str.upper()).sum())}.",
          "\n**Limitações e decisões de tratamento**\n",
          "- Não há data nem valor por código: não dá para saber uso ou gasto. Decisão: o código oficial de cada grupo é o usado por mais fornecedores.",
          "- O SAP corta a descrição em 40 caracteres. Decisão: texto perto do limite com o mesmo fornecedor repetido nunca é juntado sozinho.",
          "- Siglas como AN, ADM, XY e FGH têm significado deduzido. Decisão: palavras de contexto não juntam códigos até a Gerdau decidir.",
          "- A base original é mantida intacta; toda limpeza gera colunas novas ao lado.",
          "\n## 6. Análise exploratória\n", "**Tamanho das descrições**\n", "| Faixa | Códigos |", "| --- | --- |"]
    faixas = pd.cut(c.tamanho, [0, 12, 25, 37, 39, 40], labels=["até 12", "13 a 25", "26 a 37", "38 a 39", "40 (limite)"])
    L += [f"| {k} | {n(v)} |" for k, v in faixas.value_counts(sort=False).items()]
    c = c.assign(categoria=[categorizar(a, b) for a, b in zip(c.n5, c.mao_obra)])
    L += ["\n**Categorias**\n", "| Categoria | Códigos | Fornecedores |", "| --- | --- | --- |"]
    for k, x in c.groupby("categoria").agg(codigos=("cod", "size"), forns=("forns", lambda s: len(set("|".join(s).split("|"))))).sort_values("codigos", ascending=False).iterrows():
        L.append(f"| {k} | {n(x.codigos)} | {n(x.forns)} |")
    primeiras = c.n5.str.split().str[0].value_counts().head(15)
    L += ["\n**Palavras que mais abrem as descrições (depois de limpar)**\n", ", ".join(f"{k} ({v})" for k, v in primeiras.items())]
    q = linhas.groupby("forn").cod.nunique().quantile([.25, .5, .75, .9])
    L += [f"\n**Códigos por fornecedor:** 25% têm até {n(q[.25])}, metade até {n(q[.5])}, 75% até {n(q[.75])}, 90% até {n(q[.9])}."]
    from padronizar import ler_atributos, normalizar
    ex = []
    for d in EXEMPLOS:
        z = normalizar(d)
        a = ler_atributos(z["n5"], "H")
        ex.append((d, z["n1"], z["n3"], z["n4"], z["n5"], a["cargo"], a["modificador"], categorizar(z["n5"], a["mao_obra"])))
    pd.DataFrame(ex, columns=["original", "1_espacos", "2_3_caixa_e_acentos", "4_pontuacao", "5_dicionario", "cargo",
                              "tipo_de_hora", "categoria"]).to_csv(SAIDA / "exemplos_padronizacao.csv", sep=";", index=False, encoding="utf-8-sig")
    L += ["\n## 7. Padronização textual\n",
          "Cada descrição passa por 5 etapas, sempre na mesma ordem; os exemplos completos estão em `exemplos_padronizacao.csv`.\n",
          "| Etapa | Regra | Exemplo |", "| --- | --- | --- |",
          f"| 1 | Espaços invisíveis, duplos e nas bordas | `{ex[3][0]}` → `{ex[3][1]}` |",
          f"| 2 e 3 | Maiúsculas, sem acento e sem símbolos | `{ex[3][1]}` → `{ex[3][2]}` |",
          f"| 4 | Pontuação vira espaço; letra e número separados | `{ex[3][2]}` → `{ex[3][3]}` |",
          f"| 5 | Dicionário de abreviações e palavras sem significado | `{ex[3][3]}` → `{ex[3][4]}` |",
          f"| Leitura | Cargo, tipo de hora e categoria | `{ex[3][4]}` → {ex[3][5]} · {ex[3][6]} |"]
    return L


def diagnosticar(caminho):
    linhas, c = processar(caminho)
    mo = c[c.mao_obra]
    est = c[c.estruturado]
    r = {}

    r["linhas"], r["codigos"], r["fornecedores"] = len(linhas), len(c), linhas.forn.nunique()
    por_forn = linhas.groupby("forn").cod.nunique()
    r["top10_linhas"] = por_forn.nlargest(10).sum() / len(linhas)
    r["reuso"] = int((c.n_forn > 1).sum())
    grupos = (c.groupby("grupo").agg(codigos=("cod", "size"), mo=("mao_obra", "mean"))
              .join(linhas.grupo.value_counts().rename("linhas")))

    t1 = (est.groupby("chave").agg(codigos=("cod", "size"),
                                   fornecedores=("forns", lambda s: len(set("|".join(s).split("|")))))
          .sort_values(["fornecedores", "codigos"], ascending=False))
    r["tst_he_domfer"] = int(t1.loc["TECNICO SEGURANCA TRABALHO | HE Dom/Fer", "codigos"])
    colapso = {nv: len(c) - c[nv].nunique() for nv in ["desc", "n4", "n5", "n6"]}

    r["len40"] = int((c.tamanho == 40).sum())
    iguais = linhas.groupby(["forn", "desc"]).cod.nunique()
    iguais = iguais[iguais > 1]
    r["mesmo_texto_mesmo_forn"] = int(iguais.sum())

    horas = mo[mo.modificador.isin(HORA)]
    r["horas"], r["horas_sv"] = len(horas), int((horas.un == "SV").sum())

    sem_mod = mo[mo.modificador == ""]
    he_sem_dia = c[c.modificador.isin(["HE (dia n/i)", "HE noturna (dia n/i)"])]
    r["mo_sem_tipo"], r["he_sem_dia"] = len(sem_mod), len(he_sem_dia)

    multi = linhas.groupby("cod").desc.nunique()
    t5 = linhas[linhas.cod.isin(multi[multi > 1].index)].sort_values("cod")
    r["dois_significados"] = int((multi > 1).sum())

    gemeos = c.groupby("n6").grupo.nunique()
    r["gemeos_outro_grupo"] = int(c.n6.isin(gemeos[gemeos > 1].index).sum())
    pcts_segsab = sorted({p for p in est[est.modificador == "HE Seg-Sab"].pct if p and "|" not in p}, key=int)

    SAIDA.mkdir(exist_ok=True)
    cols = ["cod", "desc", "un", "grupo", "forns"]
    est[est.chave.isin(t1.index[:20])].sort_values(["chave", "cod"])[["chave"] + cols].to_csv(SAIDA / "tipo1_mesmo_servico.csv", index=False)
    linhas.merge(iguais.rename("n").reset_index()[["forn", "desc"]]).sort_values(["forn", "desc"]).to_csv(SAIDA / "tipo2_texto_cortado.csv", index=False)
    horas[horas.un == "SV"][cols + ["modificador"]].to_csv(SAIDA / "tipo3_hora_em_sv.csv", index=False)
    pd.concat([sem_mod[cols].assign(problema="sem tipo de hora"),
               he_sem_dia[cols].assign(problema="hora extra sem o dia")]).to_csv(SAIDA / "tipo4_nome_incompleto.csv", index=False)
    t5.to_csv(SAIDA / "tipo5_dois_significados.csv", index=False)
    c.drop(columns=["n1", "n2", "n3"]).to_csv(SAIDA / "codigos_padronizados.csv", index=False)

    L = [f"# Diagnóstico da base de códigos de serviço\n",
         f"Base: `{caminho}` · {n(r['linhas'])} linhas (fornecedor × código) · {n(r['codigos'])} códigos · "
         f"{n(r['fornecedores'])} fornecedores\n",
         "## 1. Perfil\n", "| Grupo | Códigos | Linhas | Mão de obra |", "| --- | --- | --- | --- |"]
    L += [f"| {g} | {n(x.codigos)} | {n(x.linhas)} | {pct(x.mo)} |" for g, x in grupos.iterrows()]
    L += [f"| **Total** | **{n(len(c))}** | **{n(len(linhas))}** | **{pct(c.mao_obra.mean())}** |\n",
          f"- Os 10 maiores fornecedores somam {pct(r['top10_linhas'])} das linhas; o maior tem {n(por_forn.max())} códigos.",
          f"- {n(r['reuso'])} códigos já são usados por mais de um fornecedor (reuso correto).",
          f"- Unidade SV em {pct((c.un == 'SV').mean())} dos códigos.\n",
          "## 2. Onde surgem as duplicidades\n",
          "| Tipo | Na base | Exemplos |", "| --- | --- | --- |",
          f"| 1. Mesmo serviço, vários códigos | {n(r['tst_he_domfer'])} códigos para \"técnico de segurança, HE dom/fer\" | `tipo1_mesmo_servico.csv` |",
          f"| 2. Texto cortado | {n(r['len40'])} códigos ({pct(r['len40'] / len(c))}) com 40 caracteres; {n(r['mesmo_texto_mesmo_forn'])} com texto idêntico a outro do mesmo fornecedor | `tipo2_texto_cortado.csv` |",
          f"| 3. Unidade inconsistente | {n(r['horas_sv'])} de {n(r['horas'])} itens de hora ({pct(r['horas_sv'] / r['horas'])}) em SV | `tipo3_hora_em_sv.csv` |",
          f"| 4. Nome incompleto | {n(r['mo_sem_tipo'])} itens de mão de obra sem tipo de hora; {n(r['he_sem_dia'])} horas extras sem o dia | `tipo4_nome_incompleto.csv` |",
          f"| 5. Código com dois significados | {n(r['dois_significados'])} código(s) | `tipo5_dois_significados.csv` |\n",
          "Serviços de mão de obra criados por mais fornecedores:\n", "| Cargo · tipo de hora | Códigos | Fornecedores |", "| --- | --- | --- |"]
    L += [f"| {k.replace(' | ', ' · ')} | {x.codigos} | {x.fornecedores} |" for k, x in t1.head(10).iterrows()]
    L += ["\n## 3. Ruído que atrapalha a busca\n",
          f"- \"SV\" no início de {pct(c.n4.str.match(SV_INICIO).mean())} das descrições.",
          f"- \"PRERICULOSIDADE\" em {n(c.n3.str.contains('PRERICULOSIDADE').sum())} códigos; espaço invisível em {n(c.desc.str.contains(chr(160)).sum())}.",
          f"- Percentuais diferentes de HE de segunda a sábado: {', '.join(p + '%' for p in pcts_segsab)}.",
          f"- {n(r['gemeos_outro_grupo'])} códigos têm um gêmeo em outro grupo: a busca precisa olhar a base inteira.\n",
          "## 4. Potencial de padronização\n",
          "| Nível de limpeza | Códigos redundantes |", "| --- | --- |",
          f"| Texto como está | {n(colapso['desc'])} |", f"| Sem acento, caixa e pontuação | {n(colapso['n4'])} |",
          f"| + dicionário de abreviações | {n(colapso['n5'])} |", f"| + ordem das palavras ignorada | {n(colapso['n6'])} |\n",
          f"Mão de obra estruturada (cargo + tipo de hora da taxonomia): {n(len(est))} códigos "
          f"({pct(len(est) / len(mo))} da mão de obra) cabem em {n(est.chave.nunique())} combinações "
          f"(−{pct(1 - est.chave.nunique() / len(est))})."]
    L += qualidade_e_eda(caminho, linhas, c)
    (SAIDA / "diagnostico.md").write_text("\n".join(L), encoding="utf-8")
    return r


if __name__ == "__main__":
    caminho = sys.argv[1] if len(sys.argv) > 1 else "dados/base_codigos_servico.xlsx"
    if not Path(caminho).exists():
        sys.exit(f"Planilha não encontrada: {caminho}. Coloque a base em dados/ ou passe o caminho.")
    r = diagnosticar(caminho)
    print(f"ok: {r['codigos']} códigos analisados -> saida/diagnostico.md")
