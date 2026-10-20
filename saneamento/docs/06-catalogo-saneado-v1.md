# 06 · Catálogo saneado V1

> **O que pede:** primeira proposta de catálogo com códigos tratados e possíveis duplicidades identificadas.
> **Critério de aceite:** é possível relacionar os registros tratados aos registros originais.

O catálogo saneado tem **7.056 códigos oficiais**, no lugar dos 7.937 originais (**−11,1%**). Os outros **881 códigos** não foram apagados: cada um aponta para o código oficial que o substitui, com o motivo.

## Arquivos

| Arquivo | Linhas | O que é |
| --- | --- | --- |
| `base_codigos_servico_catalogo.csv` | 7.056 | o catálogo: um código oficial por serviço, com nome padrão, categoria, unidade, grupo e quantos códigos ele substitui |
| `base_codigos_servico_depara.csv` | 7.937 | o de-para: cada código original → código oficial, com o motivo |
| `base_codigos_servico_decisoes.csv` | 211 | as duplicidades possíveis que o sistema não resolveu sozinho, agrupadas pela palavra que muda |

Os três arquivos são gerados em `saida/` pelo `deduplicar.py` (não versionado, porque contém dados da base da Gerdau). Usam ponto e vírgula e abrem direto no Excel.

## Como relacionar com os registros originais

No `depara.csv`, a coluna `codigo` é o código original e `codigo_oficial` é o código que vale. Se os dois são iguais, o código é o oficial do grupo. No banco, a mesma ligação está em `codigo_servico.codigo_canonico`, e a base original fica intacta na tabela `base_original` (ver `base-dados-saneada.md`).

Exemplo, um dos dois maiores grupos: **ENCARREGADO - HE SEG-SAB**, oficial `4076413`, que substitui 8 códigos; ao todo, o grupo vem de 8 fornecedores diferentes:

| Código original | Descrição original | Motivo |
| --- | --- | --- |
| 4076413 | ENCARREGADO ( HORA EXTRA SEG A SAB) | oficial (4 fornecedores) |
| 4089075 | ENCARREGADO ( HORA EXTRA SEG A SAB) | mesmo texto depois de limpar |
| 4101790 | ENCARREGADO(A) ( HE SEG A SAB) | mesmo texto depois de limpar |
| 4073356 | SV - Encarregado HE Segunda a Sabado | mesmo texto depois de limpar |
| 4053845 | SV ENC HE SAB | mão de obra: mesmo cargo, tipo de hora e detalhes |
| 4071563 | SV ENCARREGADO HE (SEG À SEX) | mão de obra: mesmo cargo, tipo de hora e detalhes |
| 4071564 | SV ENCARREGADO HE (SÁBADO) | mão de obra: mesmo cargo, tipo de hora e detalhes |
| 4071573 | SV ENCARREGADO HE (SEG À SEX) | mão de obra: mesmo cargo, tipo de hora e detalhes |
| 4071574 | SV ENCARREGADO HE (SÁBADO) | mão de obra: mesmo cargo, tipo de hora e detalhes |

## Por que cada código foi mantido ou juntado

| Motivo | Códigos |
| --- | --- |
| oficial (sem duplicata ou escolhido como oficial do grupo) | 6.809 |
| mesmo texto depois de limpar | 666 |
| mão de obra: mesmo cargo, tipo de hora e detalhes | 156 |
| mesmo texto com abreviação ou corte diferente | 59 |
| mantido separado: texto pode ter sido cortado pelo SAP | 247 |

Os 247 "mantidos separados" são oficiais por segurança: parecem duplicados, mas o texto está no limite de 40 caracteres e o mesmo fornecedor tem os dois, então a diferença pode ter sido cortada.

## Possíveis duplicidades identificadas para decisão

O `decisoes.csv` mostra quantos códigos se juntariam se a Gerdau disser que a palavra **não** muda o serviço:

| Se esta diferença não importar... | Códigos que se juntariam |
| --- | --- |
| EVENTUAL | 143 |
| À DISPOSIÇÃO | 129 |
| ADM | 72 |
| Unidade H x SV | 63 |
| ADM + ESP | 56 |
| ESP | 42 |
| MANUTENÇÃO | 31 |
| ADM + XY | 29 |

Testamos aplicar algumas respostas de exemplo (ADM, ESP e EVENTUAL sem importância, H = SV, percentual fora do nome): o catálogo cai para **6.619 códigos (−16,6%)**, ainda com 0 junções erradas nos 430 pares conferidos.

## Como a escolha é feita

1. Agrupa por texto limpo + unidade, e na mão de obra por cargo + tipo de hora + detalhes.
2. Compara só códigos com os mesmos números e palavras parecidas (por isso roda 1 milhão de linhas em cerca de 1 minuto).
3. Cada grupo tem um centro; A parecido com B e B parecido com C não junta A com C.
4. O oficial é o código usado por mais fornecedores; empate, o menor número.

**Como reproduzir:** na pasta `saneamento`, rode `python deduplicar.py dados\base_codigos_servico.xlsx`. Os três arquivos saem em `saida\`. As respostas da Gerdau entram no topo do `deduplicar.py` (`IRRELEVANTES`, `UNIDADES_IGUAIS`, `PERCENTUAL_NO_NOME`).
