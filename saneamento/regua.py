import re

RUIDO = {"SV", "SERVICO", "SERVICOS", "HORA", "COD", "ITEM"}
SUAVE = {"EVENTUAL", "FIXO", "FIXA", "ADM", "ADMINISTRATIVO", "XY", "CD", "FGH", "MANUTENCAO", "ESPECIALIZADO",
         "DISPOSICAO", "DEMANDA", "ROTINA", "ESP", "OBRAS", "CIVIL", "PREDIAL", "ELETRICA", "MECANICA", "GERAL",
         "CONSERVACAO", "LIMPEZA", "TURNO", "DIURNO", "CONTRATO", "GESTOR", "APOIO", "CAMPO", "INDUSTRIAL",
         "MANUAL", "ATIVIDADE", "ATENDIMENTO", "TRABALHO", "GER"}
ESPECIALIDADE = {"ELETRICA", "MECANICA", "CIVIL", "OBRA", "PREDIAL", "LIMPEZA", "CONSERVACAO", "MANUTENCAO",
                 "ADM", "ADMINISTRATIVO", "CALDEIRARIA", "PINTURA", "REFRIGERACAO", "SEGURANCA"}
NUM = re.compile(r"\d+(?:\.\d+)?")


def canon(t):
    return t[:-1] if len(t) > 4 and t.endswith("S") else t


RUIDO, SUAVE, ESPECIALIDADE = ({canon(t) for t in s} for s in (RUIDO, SUAVE, ESPECIALIDADE))


def preparar(n5, ignorar=frozenset()):
    toks = n5.split()
    return (frozenset(canon(t) for t in toks) - RUIDO - ignorar,
            [float(x) for x in NUM.findall(n5)],
            canon(toks[-1]) if toks else "",
            toks[-1] if toks else "")


def _casa(x, y, fins):
    curto, longo = sorted((x, y), key=len)
    return (x.isalpha() and y.isalpha() and longo.startswith(curto)
            and longo[len(curto):].strip("IVX") != ""
            and (len(curto) >= 3 or (len(curto) == 2 and (curto in fins or len(longo) <= 4))))


def comparar(pa, ua, pb, ub):
    A, na, fa, ta = pa
    B, nb, fb, tb = pb
    ra, rb = set(A - B), set(B - A)
    if ra or rb:
        fins = {fa, fb}
        for x in sorted(ra):
            m = next((y for y in sorted(rb) if _casa(x, y, fins)), None)
            if m:
                ra.discard(x)
                rb.discard(m)
    sobra = ra | rb
    duras = {t for t in sobra - SUAVE if not (t in (ta, tb) and t.isalpha() and len(t) <= 2)}
    if (ra & ESPECIALIDADE) and (rb & ESPECIALIDADE):
        duras |= sobra & ESPECIALIDADE
    if na != nb and not duras:
        duras = {"NUMEROS"}
    if duras:
        return "DIFERENTE", duras
    if sobra or ua != ub:
        return "REVISAR", sobra | ({f"UNIDADE {ua}/{ub}"} if ua != ub else set())
    return "DUPLICADO", set()


def classificar(n5_a, un_a, n5_b, un_b, ignorar=frozenset()):
    return comparar(preparar(n5_a, ignorar), un_a, preparar(n5_b, ignorar), un_b)
