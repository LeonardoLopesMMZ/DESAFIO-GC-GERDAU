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


if __name__ == "__main__":
    for nome, f in list(globals().items()):
        if nome.startswith("test_"):
            f()
            print("ok", nome)
