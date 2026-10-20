# 03 · Padronização textual

> **O que pede:** exemplos de normalização de textos, nomenclaturas e critérios que poderão ser usados no saneamento.
> **Critério de aceite:** as regras são documentadas e aplicadas de forma consistente.

Toda descrição passa pelas mesmas etapas, sempre na mesma ordem, pela mesma função (`normalizar` em `saneamento/padronizar.py`). Todos os scripts usam essa função; não existe uma segunda versão da regra em outro lugar.

## As etapas

| Etapa | Regra | Exemplo | Códigos alterados |
| --- | --- | --- | --- |
| 1 | Tira espaço invisível, duplo e nas bordas | `SV  PINTOR ` → `SV PINTOR` | 365 |
| 2 | Tudo em maiúsculas | `Sv Soldador H Adm` → `SV SOLDADOR H ADM` | 2.812 |
| 3 | Tira acentos | `TÉCNICO DE SEGURANÇA` → `TECNICO DE SEGURANCA` | 2.951 |
| 4 | Pontuação vira espaço; letra e número separados; número decimal preservado | `H.E.DF-TEC. DE SEG.` → `H E DF TEC DE SEG` · `HE50%` → `HE 50 %` · `1,5` → `1.5` | 6.502 |
| 5 | Troca abreviação pelo termo padrão e tira palavras vazias (DE, DA, DO, SV...) | `H E DF TEC DE SEG DO TRABALHO` → `HORA EXTRA DOMFER TECNICO SEGURANCA TRABALHO` | 6.935 |
| 6 | Ignora a ordem das palavras (só para comparar) | `ENCARREGADO HE` = `HE ENCARREGADO` | — |

A última coluna conta, entre os 7.937 códigos, quantos tiveram o texto mudado naquela etapa.

Depois das etapas, o sistema lê do texto limpo: **cargo** (lista de 57 cargos), **tipo de hora** (normal, extra seg-sáb, extra dom/fer, noturno, periculosidade, insalubridade, mensal, mobilização...), **percentual**, **especialidade** e **categoria**.

## Dicionário de abreviações

118 termos, em `dicionario.csv`. Os mais usados na base:

| Abreviação | Vira | Vezes na base |
| --- | --- | --- |
| H | HORA | 1.072 |
| HE | HORA EXTRA | 853 |
| NOT | NOTURNO | 499 |
| SEG | SEGURANCA | 349 |
| C | COM | 299 |
| DOM | DOMINGO | 261 |
| P | PARA | 260 |
| TEC | TECNICO | 232 |
| AUX | AUXILIAR | 229 |
| FER | FERIADO | 228 |
| HN | HORA NORMAL | 183 |
| SAB | SABADO | 180 |
| MOB | MOBILIZACAO | 170 |
| OP | OPERADOR | 159 |

O dicionário também corrige erros comuns (PRERICULOSIDADE → PERICULOSIDADE) e junta expressões ("SEG A SAB", "DOM E FER", "HORA EXTRA") antes de trocar palavra por palavra, para que "SEG" vire SEGURANÇA em "TEC SEG" mas SEGUNDA em "SEG A SAB".

## Nomenclatura padrão sugerida

Para mão de obra, o nome padrão é **CARGO - TIPO DE HORA**, com no máximo 40 caracteres:

| Como estava | Nome padrão |
| --- | --- |
| H.E.DF-Téc. de Seg. do trabalho-ADM | TECNICO SEGURANCA TRABALHO - HE DOM/FER |
| Sv Soldador H Adm HE 50% | SOLDADOR - HE SEG-SAB |
| ENCARREGADO ( (HORA NOT) | ENCARREGADO - ADIC NOTURNO |
| MECÂNICO DE MANUTENÇÃO (HORA NORMAL) | MECANICO - HORA NORMAL |

2.988 códigos receberam nome padrão; todos com até 40 caracteres.

## Critérios para o saneamento

1. **Número nunca some.** Medida, percentual, horário e tamanho ficam no texto. `TÊ SOLD 25 MM` e `TÊ SOLD 32 MM` continuam diferentes (teste `test_numeros_nunca_somem`).
2. **Abreviação vira termo completo** antes de comparar, pelo dicionário.
3. **Palavras sem informação saem:** SV, SERVIÇO, COD, VALOR e artigos e preposições (DE, DA, DO, EM, PARA...).
4. **A base original não é alterada:** o texto limpo é uma coluna nova.
5. **Mesma regra em todo lugar:** uma função só, usada por todos os scripts e pela API.

## Arquivos

- `dicionario.csv` (nesta pasta): os 118 termos do dicionário.
- `saida/exemplos_padronizacao.csv`: gerado pelo `diagnostico.py`, com 15 descrições reais mostrando o texto em cada etapa (não versionado, porque contém dados da base da Gerdau).

**Como reproduzir:** na pasta `saneamento`, rode `python diagnostico.py dados\base_codigos_servico.xlsx` (gera `saida\exemplos_padronizacao.csv`). Os testes `test_mesmo_servico_escrito_de_jeitos_diferentes` e `test_abreviacoes_e_erros_de_digitacao` verificam as regras.
