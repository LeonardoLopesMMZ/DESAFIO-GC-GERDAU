# 15 · Catálogo saneado V2

> **O que pede:** ajustes nas regras de limpeza e padronização a partir dos testes realizados.
> **Critério de aceite:** o catálogo está mais consistente e mantém rastreabilidade com a base original.

O catálogo V2 tem **7.054 códigos oficiais** no lugar dos 7.937 originais (**−11,1%**). A V1 tinha 7.056. Os 883 códigos juntados não foram apagados: cada um aponta para o oficial que vale, com o motivo.

## O que mudou da V1 para a V2, e por quê

Nos testes da Entrega 3 (consultas com erro, planilhas em lote e a abreviação para os 40 caracteres) apareceram abreviações da própria base que o dicionário ainda não conhecia. Elas ficavam como palavras soltas e impediam juntar códigos que são o mesmo serviço.

**1. Dicionário de 118 para 136 termos.** Entraram só abreviações que aparecem na base e que têm um único significado possível:

| Na base | Vira | Exemplo real |
| --- | --- | --- |
| INSTRUM | INSTRUMENTISTA | INSTRUM. DE MANUT. (HE DOM/FER) |
| DISPOSI | DISPOSIÇÃO | OPERADOR DE MÁQ - HORA NOTURNA À DISPOSI |
| NOTU | NOTURNO | H - Mecânico Especializado- Hora AD Notu |
| RESIDUA | RESIDUAL | (texto cortado no limite de 40) |
| DESM, DESMO | DESMOBILIZAÇÃO | |
| FORNEC, FORNECI, FORNECIM | FORNECIMENTO | SV-FORNECIM E MONTAG CHUMBADOR PARABOLT |
| DEMOL | DEMOLIÇÃO | SV-DEMOL COBERTURA C/TELHAS METÁLICAS |
| GALVAN, JARDINAG, APLIC, ESTRUT, PREV, COMB, PLAT | GALVANIZADO, JARDINAGEM, APLICAÇÃO, ESTRUTURAL, PREVENTIVA, COMBUSTÍVEL, PLATAFORMA | |
| ENCARREGADA | ENCARREGADO | |

**2. O que testamos e deixamos de fora.** Três abreviações pareciam óbvias, mas o teste mostrou que estragariam o catálogo:

- **MARTELETE** não é marteleteiro: em "DEMOL CONCRETO ARMADO C/MARTELETE PNEUM" é a ferramenta.
- **ELETROMEC** viraria a especialidade "mecânica" no nome padrão de "SUPERVISOR ELETROMEC".
- **ELETRIC**, na base, quer dizer elétrica (cercas, plataforma), não eletricista.

**3. Nome padrão com a mesma abreviação da tela.** Quando o nome passa de 40 caracteres, o Python agora abrevia na mesma ordem que a tela usa: frases (HORA EXTRA → HE), depois DE/DA/DO e COM → C/, depois as abreviações da base, depois as palavras longas e, por último, o cargo.

## Resultado

| Motivo | V1 | V2 |
| --- | --- | --- |
| oficial | 6.809 | 6.807 |
| mesmo texto depois de limpar | 666 | 668 |
| mão de obra: mesmo cargo, tipo de hora e detalhes | 156 | 157 |
| mesmo texto com abreviação ou corte diferente | 59 | 58 |
| mantido separado: texto pode ter sido cortado pelo SAP | 247 | 247 |
| **códigos oficiais** | **7.056** | **7.054** |

As duas junções novas, conferidas uma a uma:

| Código | Descrição original | Agora aponta para |
| --- | --- | --- |
| 4050569 | SV OP MAQ H NOT DISP | 4096322 OPERADOR DE MÁQ - HORA NOTURNA À DISPOSI |
| 4068679 | H - Mecânico Especializado- Hora AD Notu | 4083566 MECÂNICO ESPECIALIZADO (HORA NOT) |

Além delas, 8 códigos ganharam ou mudaram o nome padrão: por exemplo, os 3 "INSTRUM. DE MANUT." passaram a ter `INSTRUMENTISTA - HE DOM/FER`. Ao todo, **71 códigos** têm alguma diferença entre V1 e V2, todos listados em `mudancas_v1_v2.csv`.

## Rastreabilidade com a base original

- `base_codigos_servico_depara.csv` tem as **7.937 linhas** da base: código original → código oficial, com o motivo.
- `mudancas_v1_v2.csv` mostra, para cada código que mudou, o texto limpo, o oficial e o nome padrão na V1 e na V2.
- No banco, a base original fica intacta na tabela `base_original`, e `codigo_servico.codigo_canonico` liga cada código ao oficial (pasta 14 da Entrega 2).

**Nenhuma junção errada:** o teste automático `test_deduplicacao_nunca_junta_errado` confere os 430 pares rotulados à mão e falha se algum serviço diferente for juntado. Na V2 ele passa.

## Arquivos

| Arquivo | Linhas | O que é |
| --- | --- | --- |
| `base_codigos_servico_catalogo.csv` | 7.054 | o catálogo V2: um código oficial por serviço |
| `base_codigos_servico_depara.csv` | 7.937 | cada código original → oficial, com o motivo |
| `base_codigos_servico_decisoes.csv` | 211 | duplicidades possíveis que dependem de uma resposta da Gerdau |
| `mudancas_v1_v2.csv` | 71 | o que mudou de uma versão para a outra |
| `dicionario.csv` | 136 | o dicionário de abreviações da V2 |

**Como reproduzir:** na pasta `MOKKA\saneamento`, rode `python deduplicar.py` (catálogo, de-para e decisões) e `python exportar.py` (dicionário e carga do banco).

> No GitHub fica só este README. Os arquivos citados acima têm dados reais da Gerdau e ficam na pasta da entrega, no computador do grupo; para gerar de novo, siga o "como reproduzir".
