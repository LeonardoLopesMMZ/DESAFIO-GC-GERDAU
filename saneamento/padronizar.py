import re
import unicodedata

import pandas as pd


def carregar(caminho):
    linhas = pd.read_excel(caminho, sheet_name=0)
    if linhas.shape[1] != 5:
        raise SystemExit(f"A primeira aba de {caminho} tem {linhas.shape[1]} colunas; são esperadas 5, nesta ordem: "
                         "Grupo de Serviço, Código do Fornecedor, Código do Serviço, Descrição do Item, Unidade medida.")
    linhas.columns = ["grupo", "forn", "cod", "desc", "un"]
    linhas["desc"] = linhas["desc"].astype(str)
    codigos = (linhas.groupby("cod")
               .agg(desc=("desc", "first"), un=("un", "first"),
                    grupo=("grupo", lambda s: s.mode().iloc[0]),
                    grupos=("grupo", lambda s: "|".join(sorted(set(s)))),
                    n_forn=("forn", "nunique"),
                    forns=("forn", lambda s: "|".join(map(str, sorted(set(s))))))
               .reset_index())
    return linhas, codigos


def n1(s):
    return re.sub(r"\s+", " ", s.replace("\xa0", " ")).strip()


def n2(s):
    return n1(s).upper()


def n3(s):
    s = n2(s).replace("º", "O").replace("°", " GRAUS ").replace("ª", "A").replace("²", "2").replace("³", "3")
    s = unicodedata.normalize("NFKD", s)
    return "".join(ch for ch in s if not unicodedata.combining(ch))


def n4(s):
    s = n3(s)
    s = re.sub(r"(\d)[,.](\d)", r"\1#\2", s)
    s = re.sub(r"[^A-Z0-9%#]+", " ", s)
    s = re.sub(r"(?<=[A-Z])(?=\d)|(?<=\d)(?=[A-Z%])", " ", s)
    return re.sub(r"\s+", " ", s.replace("#", ".")).strip()


FRASES = [
    (r"\bH P E (SS|DF)\b", r"HORA PERICULOSIDADE HORA EXTRA \1"),
    (r"\bH I E (SS|DF)\b", r"HORA INSALUBRIDADE HORA EXTRA \1"),
    (r"\bH I A NOT\b", "HORA INSALUBRIDADE ADICIONAL NOTURNO"),
    (r"\bH P AD NOT\b", "HORA PERICULOSIDADE ADICIONAL NOTURNO"),
    (r"\bH P\b", "HORA PERICULOSIDADE"),
    (r"\bH E\b", "HORA EXTRA"),
    (r"\bHN ADD N\b", "HORA NORMAL ADICIONAL NOTURNO"),
    (r"\bH OC\b", "HORA OCIOSA"),
    (r"\bH HO\b", "HORA OCIOSA"),
    (r"\bI (\d+) %", r"INSALUBRIDADE \1 %"),
    (r"\b(SV|SERV|SERVIC|SERVICO) GERAIS\b", "SERVICOS GERAIS"),
    (r"\bSEG (A |E )?(SAB|SABADO|SEX|SEXTA)\b", r"SEGUNDA A \2"),
    (r"\bSEGUNDA A SABA\b", "SEGUNDA A SABADO"),
    (r"\bDOM (E |A )?(FER|FERIADO|FERIADOS)\b", "DOMINGO FERIADO"),
    (r"\bTEC SEG\b", "TECNICO SEGURANCA"),
    (r"\bN ?([1-3])\b", r"NIVEL \1"),
    (r"\b12 X 36\b", "12X36"), (r"\b5 X 2\b", "5X2"), (r"\b6 X 1\b", "6X1"), (r"\b4 X 2\b", "4X2"),
    (r"\bEXTRA SEM\b", "EXTRA SEMANA"),
    (r"\bNR 35\b", "NR35"), (r"\bNR 10\b", "NR10"), (r"\bNR 33\b", "NR33"),
]
ABREV = {
    "HE": "HORA EXTRA", "HX": "HORA EXTRA", "HEXTRA": "HORA EXTRA", "HN": "HORA NORMAL",
    "HNOT": "HORA NOTURNO", "HINS": "HORA INSALUBRIDADE", "H": "HORA", "HR": "HORA", "HS": "HORA",
    "HRS": "HORA", "HORAS": "HORA",
    "EXTRAS": "EXTRA", "EXTRAORD": "EXTRA", "EXTRAORDINA": "EXTRA", "EXTRAORDINARIA": "EXTRA", "EXT": "EXTRA",
    "NORM": "NORMAL",
    "NOT": "NOTURNO", "NOTURNA": "NOTURNO", "NOTURN": "NOTURNO", "NOTUR": "NOTURNO",
    "AD": "ADICIONAL", "ADD": "ADICIONAL", "ADC": "ADICIONAL", "ADIC": "ADICIONAL",
    "AN": "ADICIONAL NOTURNO",
    "SS": "SEGUNDA A SABADO", "DF": "DOMINGO FERIADO", "FDS": "FIM SEMANA",
    "DOM": "DOMINGO", "DOMINGOS": "DOMINGO", "FER": "FERIADO", "FERIADOS": "FERIADO",
    "SAB": "SABADO", "SEX": "SEXTA",
    "PERIC": "PERICULOSIDADE", "PERICUL": "PERICULOSIDADE", "PERI": "PERICULOSIDADE", "PER": "PERICULOSIDADE",
    "PERICULOSA": "PERICULOSIDADE", "PERICULOSO": "PERICULOSIDADE", "PRERICULOSIDADE": "PERICULOSIDADE",
    "INSAL": "INSALUBRIDADE", "INSALUB": "INSALUBRIDADE", "INS": "INSALUBRIDADE", "INSALUBRE": "INSALUBRIDADE",
    "MES": "MENSAL", "MOB": "MOBILIZACAO", "DESMOB": "DESMOBILIZACAO",
    "DISP": "DISPOSICAO", "DISPOS": "DISPOSICAO", "DISPOSIC": "DISPOSICAO", "DISPOSICA": "DISPOSICAO",
    "RES": "RESIDUAL", "RESID": "RESIDUAL",
    "EV": "EVENTUAL", "EVENTUA": "EVENTUAL", "EVENTU": "EVENTUAL",
    "AUX": "AUXILIAR", "AJ": "AJUDANTE", "AJUD": "AJUDANTE", "ENC": "ENCARREGADO", "ENCARREG": "ENCARREGADO",
    "SUP": "SUPERVISOR", "SUPERV": "SUPERVISOR", "TEC": "TECNICO", "TECNICA": "TECNICO",
    "TST": "TECNICO SEGURANCA TRABALHO", "SEGUR": "SEGURANCA", "SEG": "SEGURANCA", "TRAB": "TRABALHO",
    "MEC": "MECANICO", "MANUT": "MANUTENCAO", "MANU": "MANUTENCAO", "MAN": "MANUTENCAO", "MANT": "MANUTENCAO",
    "OP": "OPERADOR", "OF": "OFICIAL", "ENG": "ENGENHEIRO", "COORD": "COORDENADOR", "ASSIST": "ASSISTENTE",
    "ALMOX": "ALMOXARIFE", "PLANEJ": "PLANEJAMENTO", "PLAN": "PLANEJAMENTO", "CALD": "CALDEIREIRO",
    "CALDEREIRO": "CALDEIREIRO", "ALP": "ALPINISTA", "ALPIN": "ALPINISTA", "ASG": "AUXILIAR SERVICOS GERAIS",
    "VIG": "VIGILANTE", "LIMP": "LIMPEZA", "ELET": "ELETRICA", "ELETR": "ELETRICA", "ESPEC": "ESPECIALIZADO",
    "INST": "INSTALACAO", "CONSERV": "CONSERVACAO", "MAQ": "MAQUINA", "TRANSP": "TRANSPORTE",
    "SERVIC": "SERVICOS", "SERV": "SERVICOS",
    "GALV": "GALVANIZADO", "GALVANIZADA": "GALVANIZADO", "GALVINIZADA": "GALVANIZADO",
    "CONC": "CONCRETO", "CONCR": "CONCRETO", "DIAM": "DIAMETRO", "CX": "CAIXA", "TUBU": "TUBULACAO",
    "C": "COM", "S": "SEM", "P": "PARA",
    "MILIMETROS": "MM", "MILIMETRO": "MM",
    "RTROATIVO": "RETROATIVO", "CORD": "COORDENADOR", "MOTORI": "MOTORISTA", "RECEP": "RECEPCIONISTA",
    "SOBREAVISO": "SOBRE AVISO",
}
STOP = {"DE", "DA", "DO", "DAS", "DOS", "E", "A", "O", "AS", "OS", "EM", "NO", "NA", "PARA", "POR", "OU",
        "SV", "SERVICO", "COD", "VALOR", "R"}


def expandir(s4):
    for padrao, troca in FRASES:
        s4 = re.sub(padrao, troca, s4)
    toks = []
    for t in s4.split():
        for x in ABREV.get(t, t).split():
            x = "DOMFER" if x in ("DOMINGO", "FERIADO") else x
            if x not in STOP and not (toks and toks[-1] == x == "DOMFER"):
                toks.append(x)
    return " ".join(toks)


def normalizar(desc):
    s4 = n4(desc)
    s5 = expandir(s4)
    return {"n1": n1(desc), "n2": n2(desc), "n3": n3(desc), "n4": s4, "n5": s5,
            "n6": " ".join(sorted(set(s5.split())))}


CARGOS = [
    ("ENGENHEIRO SEGURANCA TRABALHO", r"ENGENHEIRO SEGURANCA|ENGENHEIRO EM SEGURANCA"),
    ("TECNICO SEGURANCA TRABALHO", r"TECNICO SEGURANCA|SEGURANCA TRABALHO"),
    ("AJUDANTE", r"\b(AUXILIAR|AJUDANTE) (ELETRICISTA|MECANICO|MONTADOR|SOLDADOR|CALDEIREIRO|PEDREIRO|ENCANADOR|PINTOR)"),
    ("ENGENHEIRO", r"\bENGENHEIRO"),
    ("AUXILIAR SERVICOS GERAIS", r"AUXILIAR SERVICOS GERAIS|SERVICOS GERAIS|\bAG LIMPEZA|AGENTE LIMPEZA|AUXILIAR LIMPEZA|\bAG HIGIENIZACAO"),
    ("MEIO OFICIAL", r"MEIO OFICIAL"), ("MESTRE", r"\bMESTRE"), ("COORDENADOR", r"\bCOORDENADOR"),
    ("SUPERVISOR", r"\bSUPERVISOR"), ("ENCARREGADO", r"\bENCARREGADO"), ("LIDER", r"\bLIDER"),
    ("INSPETOR", r"\bINSPETOR"), ("PLANEJADOR/TEC PLANEJAMENTO", r"PLANEJADOR|PLANEJAMENTO|PROGRAMADOR"),
    ("TECNICO", r"\bTECNICO"), ("SOLDADOR", r"\bSOLDADOR|\bSOLD MACARIQUEIRO|\bSOLD MACARIC"),
    ("CALDEIREIRO", r"\bCALDEIREIRO"), ("MACARIQUEIRO", r"MACARIQUEIRO"),
    ("MONTADOR ANDAIME", r"MONTADOR ANDAIME|ANDAIMISTA"), ("MECANICO", r"\bMECANICO"),
    ("MONTADOR", r"\bMONTADOR"), ("ELETRICISTA", r"\bELETRICISTA"), ("INSTRUMENTISTA", r"INSTRUMENTISTA"),
    ("TORNEIRO", r"\bTORNEIRO"), ("PINTOR", r"\bPINTOR\b"), ("PEDREIRO", r"\bPEDREIRO"),
    ("CARPINTEIRO", r"\bCARPINTEIRO"), ("ARMADOR", r"\bARMADOR\b"),
    ("ENCANADOR/BOMBEIRO HIDRAULICO", r"\bENCANADOR|BOMBEIRO HIDRAULICO|\bBOMBEIRO\b"),
    ("TELHADISTA", r"TELHADISTA"), ("VIDRACEIRO", r"VIDRACEIRO"), ("SERRALHEIRO", r"SERRALHEIRO"),
    ("MARTELETEIRO", r"MARTELETEIRO"), ("JARDINEIRO", r"JARDINEIRO"), ("OPERADOR", r"\bOPERADOR"),
    ("MOTORISTA", r"\bMOTORISTA"), ("VIGILANTE", r"VIGILANTE"),
    ("PORTEIRO/CONTROLADOR ACESSO", r"PORTEIRO|CONTROLADOR ACESSO"), ("ALMOXARIFE", r"ALMOXARIFE"),
    ("RESGATISTA", r"RESGATISTA|RESGATE"), ("ZELADOR", r"ZELADOR"), ("DEDETIZADOR", r"DEDETIZADOR"),
    ("SERVENTE", r"\bSERVENTE"), ("OFICIAL", r"\bOFICIAL"), ("ANALISTA", r"\bANALISTA"),
    ("ASSISTENTE", r"\bASSISTENTE"), ("AUXILIAR", r"\bAUXILIAR"), ("AJUDANTE", r"\bAJUDANTE"),
    ("MEDICO/ENFERMEIRO", r"\bMEDICO\b|ENFERMEIR"), ("ARTIFICE", r"ARTIFICE|ARTIFICIE"),
    ("MENSAGEIRO", r"MENSAGEIRO"), ("LAMINADOR", r"LAMINADOR"), ("CONTROLADOR", r"CONTROLADOR"),
    ("PROJETISTA", r"PROJETISTA"), ("FEITOR", r"\bFEITOR"), ("MANTENEDOR", r"MANTENEDOR"),
    ("LIMPADOR", r"LIMPADOR"), ("RECEPCIONISTA", r"RECEPCIONISTA"),
]
CARGOS_RX = [(k, re.compile(v)) for k, v in CARGOS]

ESPEC = [("MECANICA", r"MECANIC"), ("ELETRICA", r"ELETRIC"), ("CIVIL", r"CIVIL|OBRAS?\b"),
         ("CALDEIRARIA", r"CALDEIR"), ("ANDAIME", r"ANDAIME"), ("LIMPEZA", r"LIMPEZA"),
         ("PREDIAL", r"PREDIAL"), ("SEGURANCA", r"SEGURANCA"), ("REFRIGERACAO", r"REFRIGERACAO"),
         ("ADMINISTRATIVO", r"ADMINISTRATIV"), ("AREAS VERDES", r"AREAS VERDES|JARDINAGEM|ROCADEIRA")]
ESPEC_RX = [(k, re.compile(v)) for k, v in ESPEC]

TAXONOMIA = {"Hora normal", "HE Seg-Sab", "HE Dom/Fer", "Adicional noturno", "HE noturna Seg-Sab",
             "HE noturna Dom/Fer", "Periculosidade (hora)", "Periculosidade s/ HE", "Periculosidade (mensal)",
             "Insalubridade (hora)", "Posto mensal", "Mobilizacao/Desmobilizacao", "Retroativo"}


def ler_atributos(s5, un):
    t = set(s5.split())
    tem = lambda *w: any(x in t for x in w)
    pcts = [int(float(x)) for x in re.findall(r"(\d+(?:\.\d+)?) %", s5) + re.findall(r"EXTRA (\d{2,3})\b", s5)]
    he, noturno = "EXTRA" in t, tem("NOTURNO")
    domfer = tem("DOMFER", "FIM")
    segsab = (tem("SEGUNDA", "SABADO", "SEXTA") or ("EXTRA" in t and tem("SEMANA", "SEMANAL"))) and not domfer
    peric, insal = tem("PERICULOSIDADE"), tem("INSALUBRIDADE")
    mensal = tem("MENSAL", "POSTO", "12X36", "5X2", "6X1", "4X2") or un == "MON" or bool(re.search(r"\b(24|44) HORA", s5))
    inferido = ""
    if he and not domfer and not segsab and pcts:
        if 100 in pcts:
            domfer, inferido = True, "dia inferido por 100%"
        elif any(40 <= p < 100 for p in pcts):
            segsab, inferido = True, "dia inferido por %"
    if tem("RETROATIVO"): mod = "Retroativo"
    elif tem("MOBILIZACAO", "DESMOBILIZACAO"): mod = "Mobilizacao/Desmobilizacao"
    elif peric and mensal and not he: mod = "Periculosidade (mensal)"
    elif peric and he: mod = "Periculosidade s/ HE"
    elif peric: mod = "Periculosidade (hora)"
    elif insal: mod = "Insalubridade (hora)"
    elif he and noturno: mod = "HE noturna Dom/Fer" if domfer else ("HE noturna Seg-Sab" if segsab else "HE noturna (dia n/i)")
    elif he: mod = "HE Dom/Fer" if domfer else ("HE Seg-Sab" if segsab else "HE (dia n/i)")
    elif noturno: mod = "Adicional noturno"
    elif tem("OCIOSA"): mod = "[fora] Hora ociosa"
    elif tem("DISPOSICAO"): mod = "[fora] A disposicao"
    elif tem("DIARIA") or un == "TAG": mod = "[fora] Diaria"
    elif "SOBRE AVISO" in s5: mod = "[fora] Sobreaviso"
    elif tem("DEMANDA"): mod = "[fora] Sob demanda"
    elif mensal: mod = "Posto mensal"
    elif tem("NORMAL"): mod = "Hora normal"
    else: mod = ""
    cargo = next((k for k, rx in CARGOS_RX if rx.search(s5)), "")
    espec = ""
    if cargo in ("SUPERVISOR", "ENCARREGADO", "LIDER", "TECNICO", "AJUDANTE", "AUXILIAR", "OFICIAL", "INSPETOR",
                 "COORDENADOR", "ASSISTENTE", "MONTADOR", "OPERADOR", "ENGENHEIRO", "MECANICO"):
        espec = next((k for k, rx in ESPEC_RX if rx.search(s5) and not cargo.startswith(k[:6])), "")
    mn = re.search(r"NIVEL ([1-3])|\b(JUNIOR|PLENO|SENIOR)\b", s5)
    qualif = "|".join(q for q in ("NR35", "12X36", "ALPINISTA", "RESIDUAL", "CHEIA", "EVENTUAL") if q in t)
    mao_obra = un in ("H", "SV", "MON", "TAG") and (bool(cargo) or mod in ("Hora normal", "Adicional noturno")
                                                     or mod.startswith("HE"))
    return dict(cargo=cargo, espec=espec, modificador=mod, nivel=(mn.group(1) or mn.group(2)) if mn else "",
                qualif=qualif, pct="|".join(map(str, sorted(set(pcts)))), inferido=inferido, mao_obra=mao_obra)


def processar(caminho):
    linhas, c = carregar(caminho)
    c = pd.concat([c, c.desc.map(normalizar).apply(pd.Series)], axis=1)
    c = pd.concat([c, c.apply(lambda r: ler_atributos(r.n5, r.un), axis=1).apply(pd.Series)], axis=1)
    c["tamanho"] = c.desc.str.len()
    c["estruturado"] = c.mao_obra & (c.cargo != "") & c.modificador.isin(TAXONOMIA)
    c["chave"] = (c.cargo + " | " + c.modificador).where(c.estruturado, "")
    return linhas, c


CATEGORIAS_FORTES = [
    ("MANUTENCAO E FABRICACAO", r"INCONEL|ALLOY|SOLTEIRO|ACO CARBONO|ACOCARBONO"),
    ("ALIMENTACAO E EVENTOS", r"PESSOAS|\bGARCOM"),
]
CATEGORIAS = [
    ("MAO DE OBRA", r"\bPOSTO|APRENDIZ|ESTAGIARIO|\bDIARIA|CUSTO FIXO|^HORA \d|\bEQUIPE|FRENTISTA|\bSPIN\b"),
    ("ALIMENTACAO E EVENTOS", r"CARDAPIO|SALADA|ALMOCO|JANTAR|LANCHE|CAFE|REFEICAO|MARMITA|BOLO|HAPPY|COFFEE|DESJEJUM|CEIA|SOBREMESA|SUCO|ALIMENTACAO|BUFFET|SALGADO|PRATO|\bKIT\b|MOUSSE|TORTA|SANDUICHE|CREME|\bPAO\b|\bFILE\b|FAROFA|BROA|LOMBO|PAVE|BOLINHO|QUICHE|QUIBE|ESCALOPE|PASTEL|CALDO|CROQUETE|MUSSARELA|PRESUNTO|QUEIJO|FRANGO|CARNE|PEIXE|TILAPIA|ARROZ|FEIJAO|MESA MINEIRA|MESA GOIANA|MUSICA|EVENTO|DECORACAO|GARRAFA|FRUTA|BRUNCH|LOUCA"),
    ("TRANSPORTE", r"TRANSPORTE|ONIBUS|\bVAN\b|FRETE|VIAGEM|\bKM\b|DESLOCAMENTO|^LINHA (?!DE\b)"),
    ("EQUIPAMENTO E LOCACAO", r"LOCACAO|ALUGUEL|\bLOC\b|CAMINHAO|GUINDASTE|MUNCK|TRATOR|ESCAVADEIRA|RETROESCAVAD|CARREGADEIRA|GERADOR|COMPRESSOR|CONTAINER|PLATAFORMA|EQUIPAMENTO|MAQUINA|VEICULO|EMPILHADEIRA|BOMBA|SERRA\b|FURADEIRA|BETONEIRA|VIBRADOR|ANDAIME|MOTOSSERRA|ROCADEIRA|VARREDEIRA|\bCARRO|CAMIONETE|PICK UP|BANHEIRO QUIMICO|MARTELETE|LIXADEIRA|ASPIRADOR|ROMPEDOR|COMPACTADOR|PERFURATRIZ|\bBROCA|KARCHER|LAVADORA|APARELHO|AR CONDICIONADO|\bBTU|CAMINHONETE|INSUFLADOR|TENDA"),
    ("MATERIAL HIDRAULICO", r"\bTUBO|TUBULACAO|ADAPTADOR|\bTE\b|\bLUVA|SIFONADA|REGISTRO|\bCURVA|JOELHO|\bBUCHA|TORNEIRA|VALVULA|\bUNIAO|\bCAP\b|COTOVELO|\bRALO|NIPLE|FLANGE|\bBACIA|LAVATORIO|\bPIA\b|SIFAO|ENGATE|CHUVEIRO|MICTORIO|HIDRAULIC|ESGOTO|REDUCAO|PLUG|TAMPAO|BUJAO|CAIXA D AGUA|CAIXA GORDURA|CAIXA SANFONADA|CAIXA INSPECAO|\bCUBA|TANQUE|ASSENTO"),
    ("MATERIAL ELETRICO", r"\bCABO|ELETRODUTO|CONDULETE|LUMINARIA|LAMPADA|DISJUNTOR|DISJUTOR|DISJPCTOR|TOMADA|INTERRUPTOR|QUADRO|REATOR|ELETROCALHA|PERFILADO|CANALETA|CRUZETA|\bPOSTE|TERMINAL|CONECTOR|REFLETOR|\bFIO\b|ATERRAMENTO|SPDA|PARA RAIO|ESPELHO|\bDUTO|\bRACK"),
    ("OBRA CIVIL", r"PINTURA|TINTA|CONCRETO|ALVENARIA|\bPISO|CONTRAPISO|FORRO|DEMOLICAO|\bDEM\b|ESCAVACAO|ATERRO|REVESTIMENTO|CERAMICA|ARGAMASSA|CHAPISCO|EMBOCO|REBOCO|IMPERMEABILIZ|\bTELHA|TELHADO|\bCALHA|\bFORMA\b|ARMADURA|GRAUTE|\bPORTA|JANELA|VIDRO|ESQUADRIA|DIVISORIA|GESSO|PAVIMENTACAO|MEIO FIO|CALCADA|ALAMBRADO|CERCA|GUARDA CORPO|ANCORAGEM|FURACAO|\bLAJE|\bVIGA|PILAR|ESTACA|SOLEIRA|BANCADA|GRANITO|TRILHO|DORMENTE|BRITA|AREIA|CIMENTO|\bTAMPA|GRELHA|MURO|DRENAGEM|\bPLACA|CHAPA|PERFIL|ESTRUTURA METALICA|PARAFUSO|SINALIZACAO|FAIXA|PRIMER|RODAPE|ASSENTAMENTO|BASE GRADUADA|\bMANTA|DRYWALL|RESINA|VERNIZ|\bCAL\b|\bLONA|LINHA DE VIDA|MADEIRA|SUPERFICIE|\bJATO|JATEAMENTO|PEDRA|\bSACO|ACRILICA|ESMALTE|DEMAO|TAPUME|RUFO|DOBRADICA|ISOLAMENTO"),
    ("LIMPEZA E CONSERVACAO", r"LIMPEZA|ROCADA|ROCAGEM|\bPODA|CAPINA|JARDINAGEM|\bCOLETA|DEDETIZACAO|DESRATIZACAO|VARRICAO|HIGIENIZACAO|LAVAGEM|DESENTUPIMENTO|RESIDUO|\bLIXO|CONSERVACAO|DESCARTE|ENTULHO|SILVICULTURA"),
    ("MANUTENCAO E FABRICACAO", r"PREVENTIVA|CORRETIVA|INSPECAO|CALIBRACAO|REVISAO|\bPONTE|USINAGEM|FABRICACAO|\bFABR|PAINEL|PAINEIS|\bSOLDA\b|CALDEIRARIA|REFORMA|RECUPERACAO|REPARO|MANUTENCAO|BALANCEAMENTO|ALINHAMENTO|ENSAIO|TESTE|\bPAIN\b"),
    ("VIGILANCIA E PORTARIA", r"VIGILANCIA|PORTARIA|MONITORAMENTO|ALARME|SEGURANCA PATRIMONIAL|CAMERA|\bCAM\b|CFTV|SPEED DOME|\bIP\b"),
    ("VERBAS E REEMBOLSOS", r"RETROATIVO|\bVERBA|REEMBOLSO|PREMIO|ASSIDUIDADE|PEDAGIO|COMBUSTIVEL|\bTAXA|\bART\b|SEGURO|IMPOSTO|ADICIONAL|BONUS|DESPESA|HOSPEDAGEM|ESTADIA|FATURAMENTO"),
    ("MATERIAIS EM GERAL", r"MATERIAIS|MATERIAL|INSUMO|FORNECIMENTO"),
]
CATEGORIAS_FORTES_RX = [(k, re.compile(v)) for k, v in CATEGORIAS_FORTES]
CATEGORIAS_RX = [(k, re.compile(v)) for k, v in CATEGORIAS]


def categorizar(n5, mao_obra):
    if re.search(r"MOBILIZACAO|DESMOBILIZACAO", n5):
        return "MOBILIZACAO E DESMOBILIZACAO"
    if mao_obra:
        return "MAO DE OBRA"
    forte = next((k for k, rx in CATEGORIAS_FORTES_RX if rx.search(n5)), None)
    if forte:
        return forte
    achados = [(m.start(), k) for k, rx in CATEGORIAS_RX for m in [rx.search(n5)] if m]
    return min(achados)[1] if achados else "OUTROS"
