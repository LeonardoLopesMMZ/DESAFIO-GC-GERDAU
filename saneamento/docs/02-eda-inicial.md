# 02 · EDA inicial

> **O que pede:** quantidade de códigos, frequência, padrões de descrição, possíveis duplicidades e grupos de registros.
> **Critério de aceite:** a análise ajuda a compreender onde estão os principais problemas da base.

Os principais problemas estão na **mão de obra** (52% dos códigos), onde o mesmo cargo com o mesmo tipo de hora foi criado de novo por quase cada fornecedor, e no **limite de 40 caracteres**, que corta 1 em cada 6 descrições.

## Quantidade de códigos

| Grupo | Códigos | Linhas | Mão de obra |
| --- | --- | --- | --- |
| Civil | 3.430 | 4.558 | 38,7% |
| Eletromecânica | 1.777 | 2.456 | 79,4% |
| Facilities | 2.730 | 2.781 | 51,7% |
| **Total** | **7.937** | **9.795** | **52,3%** |

23 códigos aparecem em mais de um grupo, por isso a soma por grupo passa de 7.937.

## Frequência

- Os 10 maiores fornecedores têm 60,0% das linhas; o maior sozinho tem 1.531 códigos.
- Códigos por fornecedor: 25% têm até 4, metade até 21, 75% até 52, 90% até 223.
- 1.234 códigos são usados por mais de um fornecedor. Esse é o comportamento certo: um serviço, um código.
- Unidade SV (serviço) em 47,8% dos códigos.

## Padrões de descrição

**Tamanho do texto**

| Caracteres | Códigos |
| --- | --- |
| até 12 | 168 |
| 13 a 25 | 1.698 |
| 26 a 37 | 3.674 |
| 38 a 39 | 1.069 |
| 40 (limite do SAP) | 1.328 |

**Palavras que mais abrem as descrições (depois da limpeza)**

HORA (800), TECNICO (305), SUPERVISOR (269), AUXILIAR (212), ENCARREGADO (196), AJUDANTE (144), SOLDADOR (137), MOBILIZACAO (130), MECANICO (116), INSTALACAO (111), LIDER (108), MANUTENCAO (98), PINTOR (94), ELETRICISTA (89), OFICIAL (79)

**Ruído que atrapalha a busca:** "SV" no início de 41,8% dos textos; "PRERICULOSIDADE" em 32; espaço invisível em 74; 215 códigos com um gêmeo em outro grupo.

## Grupos de registros (categorias)

| Categoria | Códigos | Fornecedores |
| --- | --- | --- |
| MAO DE OBRA | 4.042 | 82 |
| OBRA CIVIL | 1.007 | 34 |
| MATERIAL HIDRAULICO | 566 | 14 |
| OUTROS | 471 | 60 |
| ALIMENTACAO E EVENTOS | 397 | 15 |
| EQUIPAMENTO E LOCACAO | 379 | 51 |
| MOBILIZACAO E DESMOBILIZACAO | 259 | 37 |
| MATERIAL ELETRICO | 222 | 7 |
| MANUTENCAO E FABRICACAO | 218 | 33 |
| TRANSPORTE | 108 | 23 |
| LIMPEZA E CONSERVACAO | 77 | 29 |
| VERBAS E REEMBOLSOS | 72 | 40 |
| MATERIAIS EM GERAL | 61 | 14 |
| VIGILANCIA E PORTARIA | 58 | 5 |

Só 5,9% dos códigos ficam em OUTROS. A categoria atende ao pedido da Gerdau de que os itens que não são mão de obra sejam ao menos identificáveis para somar valores.

## Possíveis duplicidades

**Os 5 tipos de problema encontrados**

| Tipo | Na base | Exemplos |
| --- | --- | --- |
| 1. Mesmo serviço, vários códigos | 28 códigos para "técnico de segurança, HE dom/fer" | `tipo1_mesmo_servico.csv` |
| 2. Texto cortado | 1.328 códigos com 40 caracteres; 746 com texto idêntico a outro do mesmo fornecedor | `tipo2_texto_cortado.csv` |
| 3. Unidade inconsistente | 884 de 2.519 itens de hora (35,1%) em SV | `tipo3_hora_em_sv.csv` |
| 4. Nome incompleto | 378 itens de mão de obra sem tipo de hora; 468 horas extras sem o dia | `tipo4_nome_incompleto.csv` |
| 5. Código com dois significados | 1 código | `tipo5_dois_significados.csv` |

**Quanto cada nível de limpeza revela**

| Comparando o texto... | Códigos a mais (duplicados) |
| --- | --- |
| como está | 470 |
| sem acento, maiúscula e pontuação | 614 |
| + dicionário de abreviações | 909 |
| + ignorando a ordem das palavras | 986 |

Na mão de obra, 2.992 códigos (72,1% da mão de obra) cabem em só 470 combinações de cargo + tipo de hora.

**Serviços de mão de obra criados por mais fornecedores**

| Cargo · tipo de hora | Códigos | Fornecedores |
| --- | --- | --- |
| TECNICO SEGURANCA TRABALHO · HE Dom/Fer | 28 | 28 |
| TECNICO SEGURANCA TRABALHO · HE Seg-Sab | 29 | 27 |
| ENCARREGADO · HE Dom/Fer | 32 | 25 |
| ENCARREGADO · HE Seg-Sab | 33 | 22 |
| AJUDANTE · HE Seg-Sab | 26 | 22 |
| AJUDANTE · HE Dom/Fer | 22 | 22 |
| TECNICO SEGURANCA TRABALHO · Adicional noturno | 30 | 21 |
| TECNICO SEGURANCA TRABALHO · Hora normal | 18 | 21 |
| SUPERVISOR · HE Dom/Fer | 37 | 18 |
| ENCARREGADO · Adicional noturno | 33 | 18 |

## Onde estão os principais problemas

1. **Mão de obra:** cada fornecedor cria seu próprio "técnico de segurança hora extra". É onde está a maior parte das duplicidades e a prioridade que a Gerdau confirmou.
2. **Abreviações:** o dicionário sozinho revela quase o dobro de duplicados (470 → 909).
3. **Texto cortado:** 16,7% dos textos batem no limite; exigem cuidado para não juntar coisas diferentes.
4. **Unidade:** um terço das horas está cadastrado como SV, o que esconde duplicatas e atrapalha somar valores.

**Arquivos gerados:** `saida/tipo1_mesmo_servico.csv` a `saida/tipo5_dois_significados.csv`, com os exemplos reais de cada tipo (não versionado, porque contém dados da base da Gerdau).
**Como reproduzir:** na pasta `saneamento`, rode `python diagnostico.py dados\base_codigos_servico.xlsx`.
