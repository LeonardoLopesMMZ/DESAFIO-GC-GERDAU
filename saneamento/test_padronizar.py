from pathlib import Path

from padronizar import ler_atributos, normalizar

BASE = Path("dados/base_codigos_servico.xlsx")


def atributos(desc, un="H"):
    return ler_atributos(normalizar(desc)["n5"], un)


def test_mesmo_servico_escrito_de_jeitos_diferentes():
    for desc in ["SV-MEC-01-HN", "MEC.MANUT.NORM"]:
        a = atributos(desc)
        assert (a["cargo"], a["modificador"]) == ("MECANICO", "Hora normal"), (desc, a)


def test_abreviacoes_e_erros_de_digitacao():
    a = atributos("TEC SEG Valor HE (R$) Feriados e Dom")
    assert (a["cargo"], a["modificador"]) == ("TECNICO SEGURANCA TRABALHO", "HE Dom/Fer"), a
    assert "PERICULOSIDADE" in normalizar("MECÂNICO DE MANUT. (PRERICULOSIDADE)")["n5"]


def test_numeros_nunca_somem():
    a, b = normalizar("SV TÊ SOLD 25 MM")["n5"], normalizar("SV TÊ SOLD 32 MM")["n5"]
    assert a != b and "25" in a and "32" in b


def test_numeros_da_base_batem_com_o_documento():
    if not BASE.exists():
        print("  (pulado: base não encontrada em dados/)")
        return
    from diagnostico import diagnosticar
    r = diagnosticar(str(BASE))
    esperado = {"linhas": 9795, "codigos": 7937, "fornecedores": 111, "reuso": 1234, "tst_he_domfer": 28,
                "len40": 1328, "mesmo_texto_mesmo_forn": 746, "horas": 2519, "horas_sv": 884,
                "mo_sem_tipo": 378, "he_sem_dia": 468, "dois_significados": 1, "gemeos_outro_grupo": 215}
    diferentes = {k: (r[k], v) for k, v in esperado.items() if r[k] != v}
    assert not diferentes, f"mudou (obtido, esperado): {diferentes}"


def test_planilha_limpa():
    if not BASE.exists():
        print("  (pulado: base não encontrada em dados/)")
        return
    from limpar import limpar
    base, dup, c = limpar(BASE)
    assert len(base) == 9795, "a planilha limpa precisa manter todas as linhas da original"
    assert (c.nome.str.len() <= 40).all(), "nome padrão passou de 40 caracteres"
    furo = c[c.desc == "SV - FURAÇÃO EM CONCRETO COM COROA DIAM"]
    assert (furo.confianca == "conferir: pode ser texto cortado").all(), "texto cortado não pode ter confiança alta"
    noite = c[c.desc == "SV VIGILANTE CONDUTOR 18H AS 06H 12X36."].grupo_id.iloc[0]
    dia = c[c.desc == "SV VIGILANTE CONDUTOR 06H AS 18H 12X36."].grupo_id.iloc[0]
    assert noite != dia, "turno da noite e do dia não podem ser o mesmo grupo"


def test_regua_armadilhas():
    from regua import classificar
    n = lambda s: normalizar(s)["n5"]
    casos = [
        ("MECÂNICO DE MANUTENÇÃO- HE (dom/fer)", "H", "MECÂNICO DE MANUT. (HORA EXTRA DOM/FER)", "H", "DUPLICADO"),
        ("SV-TÊ 90 E DE REDUÇÃO SOLDÁVEL E COM ROS", "ST", "SV-TÊ 90 E DE REDUÇÃO SOLDÁVEL E CO ROSC", "ST", "DUPLICADO"),
        ("SV-CURVA E JOELHO SOLDÁVEL 25 MM", "ST", "SV-CURVA E JOELHO SOLDÁVEL 32 MM", "ST", "DIFERENTE"),
        ("Sv Caldeireiro H Adm HE 50%", "SV", "Sv Caldeireiro H Adm HE 100%", "SV", "DIFERENTE"),
        ("MONTADOR LIDER(HE NOT SEG A SAB)", "H", "MONTADOR LIDER (HE SEG A SAB)", "H", "DIFERENTE"),
        ("SV TRECHO TRILHO 36M c/linha de vida", "M", "SV TRECHO TRILHO 36M s/linha de vida", "M", "DIFERENTE"),
        ("SV-VIDRO TEMPERADO 6MM", "M2", "SV-VIDRO TEMPERADO DE 6MM", "SV", "REVISAR"),
        ("SV VIGILANTE 18H00 AS 06H00 12 X 36 SEG.", "SV", "SV VIGILANTE 06H00 AS 18H00 12X 36 SEG.", "SV", "DIFERENTE"),
        ("SV ARGAMASSA ACII PARA REPAROS EM PAREDE", "M2", "SV ARGAMASSA ACIII PARA REPARO EM PAREDE", "M2", "DIFERENTE"),
        ("H - Encarregado Mecânica -HE (dom/fer)", "H", "ENCARREGADO DE ELÉTRICA (HE DOM/FER)", "H", "DIFERENTE"),
    ]
    for a, ua, b, ub, certo in casos:
        assert classificar(n(a), ua, n(b), ub)[0] == certo, (a, b, certo)


def test_deduplicacao_nunca_junta_errado():
    if not BASE.exists() or not Path("dados_teste/pares_rotulados.csv").exists():
        print("  (pulado: base ou pares rotulados não encontrados)")
        return
    import pandas as pd
    import deduplicar as dd
    dp, cat, dec = dd.deduplicar(dd.ler(BASE), log=lambda *a: None)
    oficial = dict(zip(dp.codigo.astype(str), dp.codigo_oficial.astype(str)))
    junto = lambda a, b: oficial[str(a)] == oficial[str(b)]
    lab = pd.read_csv("dados_teste/pares_rotulados.csv", keep_default_na=False)
    errados = [(da, db) for a, b, da, db, r in zip(lab.cod_a, lab.cod_b, lab.desc_a, lab.desc_b, lab.rotulo)
               if r == "N" and junto(a, b)]
    assert not errados, f"juntou serviços diferentes: {errados[:5]}"
    furos = dp[dp.descricao == "SV - FURAÇÃO EM CONCRETO COM COROA DIAM"]
    assert (furos.codigo == furos.codigo_oficial).all(), "texto cortado não pode ser juntado"
    assert len(set(dp[dp.descricao.isin(["SV BACIA PARA CAIXA ACOPLADA", "SV CAIXA ACOPLADA PARA BACIA"])].codigo_oficial)) == 2
    tst = dp[dp.descricao.isin(["TEC SEG Valor HE (R$) Feriados e Dom", "TÉCNICO DE SEGURANÇA (HE DOM/FER)"])]
    assert tst.codigo_oficial.nunique() == 1, "variações do mesmo serviço deveriam virar um código só"


def test_api_devolve_candidatos():
    if not BASE.exists():
        print("  (pulado: base não encontrada em dados/)")
        return
    import json
    import threading
    import urllib.request
    from api import servir
    servidor = servir(BASE, porta=0)
    threading.Thread(target=servidor.serve_forever, daemon=True).start()
    url = f"http://127.0.0.1:{servidor.server_address[1]}/similares"
    abrir = urllib.request.build_opener(urllib.request.ProxyHandler({})).open
    pedido = urllib.request.Request(url, data=json.dumps({"descricao": "MEC.MANUT.NORM", "unidade": "H"}).encode(),
                                    headers={"Content-Type": "application/json"})
    r = json.loads(abrir(pedido).read())
    assert r["resultado"] == "BLOQUEAR" and r["candidatos"][0]["codigo"] == "4083559", r
    pedido = urllib.request.Request(url, data=json.dumps({"descricao": "CURVA SOLDAVEL 25MM", "unidade": "ST"}).encode())
    r = json.loads(abrir(pedido).read())
    assert r["resultado"] == "LIBERAR" and r["candidatos"], r
    pedido = urllib.request.Request(url, data=iter([b'{"descricao": "MEC.MANUT', b'.NORM", "unidade": "H"}']))
    r = json.loads(abrir(pedido).read())
    assert r["resultado"] == "BLOQUEAR", r
    servidor.shutdown()


if __name__ == "__main__":
    for nome, f in list(globals().items()):
        if nome.startswith("test_"):
            f()
            print("ok", nome)
