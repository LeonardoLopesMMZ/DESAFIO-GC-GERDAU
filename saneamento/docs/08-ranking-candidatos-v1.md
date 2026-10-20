# 08 · Ranking de candidatos V1

> **O que pede:** ordenação dos códigos encontrados por score ou critério de similaridade.
> **Critério de aceite:** os candidatos são apresentados em uma ordem reproduzível e compreensível.

## O critério

Os candidatos são ordenados por três chaves, nesta ordem:

| Ordem | Chave | Valores |
| --- | --- | --- |
| 1º | **Classe** (o que a régua achou) | DUPLICADO antes de REVISAR, REVISAR antes de DIFERENTE |
| 2º | **Aderência** (score) | maior primeiro; é a semelhança dos pedaços de letras, de 0% a 100% |
| 3º | **Código** | menor primeiro, só para desempate |

Depois de ordenar, cada código oficial aparece uma vez só, mesmo que vários códigos antigos do grupo tenham sido encontrados.

**Por que a classe vem antes do score.** Um texto pode ser muito parecido e mesmo assim ser outro serviço: "HE 40%" é 91% parecido com "HE 50%", mas não serve. Um código "quase igual" com 70% de aderência é mais útil para o comprador do que um "diferente" com 91%.

## Exemplo: SOLDADOR HE 50% SEG A SAB · H

| Posição | Código | Descrição | Classe | Aderência | O que muda |
| --- | --- | --- | --- | --- | --- |
| 1 | 4051912 | Sv Soldador H Adm HE 50% | REVISAR | 70% | unidade H/SV, adm |
| 2 | 4051915 | Sv Soldador H Esp HE 50% | REVISAR | 66% | unidade H/SV, esp |
| 3 | 4045142 | SV-SOLDADOR H EXTRA 40%(SEG/SAB) | DIFERENTE | 91% | 40% x 50% |
| 4 | 4045172 | SV-SOLDADOR(HX 70% SEG A SAB) | DIFERENTE | 88% | 50% x 70% |
| 5 | 4083591 | SOLDADOR 6G (HORA EXTRA SEM) | DIFERENTE | 86% | 50%, 6 |

Os dois primeiros têm aderência menor, mas são os únicos que podem ser o mesmo serviço. Os três de baixo são parecidos no texto, mas têm outro percentual.

## Exemplo: tec seg trab HE 100% dom · H

| Posição | Código | Descrição | Classe | Aderência | O que muda |
| --- | --- | --- | --- | --- | --- |
| 1 | 4096236 | TÉC. SEGURANÇA - HORA EXTRA 100% | DUPLICADO | 77% | nada |
| 2 | 4045651 | SV- TEC.DE SEGURANÇA (HX 100% DOM E FER) | REVISAR | 92% | unidade H/SV |
| 3 | 4051950 | Sv TST H Adm HE 100% | REVISAR | 88% | unidade H/SV, adm |
| 4 | 4051953 | Sv TST H Esp HE 100% | REVISAR | 86% | unidade H/SV, esp |
| 5 | 4085903 | H.E.DF-Téc. de Seg. do trabalho-ADM | DIFERENTE | 92% | 100% |

## Reprodutível

- Não há nada aleatório: a mesma descrição na mesma base gera sempre a mesma lista, na mesma ordem.
- Rodamos as três consultas desta pasta duas vezes seguidas e comparamos as respostas inteiras: **idênticas**.
- O empate é resolvido pelo número do código, então nem a ordem de leitura da planilha muda o resultado.

**Como reproduzir:** na pasta `saneamento`, rode `python similaridade.py "SOLDADOR HE 50% SEG A SAB" H` duas vezes e compare.
