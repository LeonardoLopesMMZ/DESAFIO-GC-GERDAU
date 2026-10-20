# 01 · Diagnóstico de qualidade

> **O que pede:** colunas relevantes, tipos, faltantes, duplicados, inconsistências e limitações da base.
> **Critério de aceite:** há evidências da análise e decisões iniciais sobre tratamento.

A base piloto tem **9.795 linhas** (uma por par fornecedor × código), **7.937 códigos**, **111 fornecedores** e **3 grupos** (Civil, Facilities, Eletromecânica). Nenhuma coluna tem valor faltando. O problema está no conteúdo do texto: o mesmo serviço aparece escrito de muitos jeitos, cortado em 40 caracteres e com unidade trocada.

## Colunas, tipos e faltantes

| Coluna | Tipo | Por que importa | Preenchidos | Faltantes | Distintos | Exemplo |
| --- | --- | --- | --- | --- | --- | --- |
| Grupo de Serviço | texto | separa as áreas do piloto | 9.795 | 0 | 3 | Civil |
| Código do Fornecedor | número usado como código | mostra quantos fornecedores usam cada serviço | 9.795 | 0 | 111 | 200982674 |
| Código do Serviço | número usado como código | é o que está duplicado | 9.795 | 0 | 7.937 | 4033776 |
| Descrição do Item | texto (até 40 caracteres) | é onde o serviço é descrito; base de toda a análise | 9.795 | 0 | 7.468 | SV-EQUIPE TELHADO |
| Unidade medida | texto | H, SV, M2...; muda o significado do serviço | 9.795 | 0 | 17 | SV |

Os códigos foram lidos como texto, não como número, para não perder zeros nem virar notação científica.

## Duplicados

| Verificação | Resultado |
| --- | --- |
| Linhas repetidas por inteiro | 0 |
| Código com mais de uma descrição | 1 |
| Código com mais de uma unidade | 0 |
| Código em mais de um grupo | 23 |
| Códigos a mais por texto exatamente igual a outro código, sem nenhuma limpeza | 470 |
| Códigos usados por mais de um fornecedor (reuso correto) | 1.234 |
| Mesmo serviço com vários códigos: técnico de segurança, hora extra dom/fer | 28 códigos de 28 fornecedores |

## Inconsistências

| Inconsistência | Quantidade |
| --- | --- |
| Texto no limite de 40 caracteres (provavelmente cortado pelo SAP) | 1.328 códigos (16,7%) |
| Texto idêntico a outro código do mesmo fornecedor | 746 códigos |
| Item cobrado por hora com unidade SV em vez de H | 884 de 2.519 (35,1%) |
| Mão de obra sem tipo de hora no nome | 378 |
| Hora extra sem o dia (seg-sáb ou dom/fer) | 468 |
| Espaço invisível, duplo ou nas bordas | 365 |
| Texto em minúsculas ou misturado | 2.812 |
| Erro de digitação "PRERICULOSIDADE" | 32 |
| "SV" no começo da descrição, sem acrescentar informação | 41,8% |
| Hora extra seg-sáb com percentuais diferentes: 40%, 50%, 60%, 70%, 75% | 5 variações |

## Limitações da base

- Não há data de criação, valor nem quantidade por código. Não dá para saber qual código é mais usado em compras nem medir economia em reais.
- O SAP corta a descrição em 40 caracteres. Dois serviços diferentes podem ficar com o mesmo texto cortado.
- Siglas como AN, ADM, XY e FGH não têm significado documentado; o significado foi deduzido.
- O piloto tem só 3 grupos. Outras categorias podem trazer padrões novos.

## Decisões iniciais de tratamento

| Problema | Decisão |
| --- | --- |
| Sem dado de uso por código | O código oficial de cada grupo é o usado por mais fornecedores; empate, o menor número. |
| Texto cortado em 40 caracteres | Texto com 38 caracteres ou mais, repetido no mesmo fornecedor, nunca é juntado automaticamente. |
| Siglas sem significado confirmado | Palavras de contexto (ADM, EVENTUAL, À DISPOSIÇÃO...) não juntam códigos até a Gerdau decidir. |
| Dúvida entre "igual" e "diferente" | Na dúvida, não junta. Juntar serviços diferentes é pior do que deixar dois iguais separados. |
| Base original | Nunca é alterada. Toda limpeza gera colunas novas ao lado e um de-para. |

## Arquivos gerados

- `saida/diagnostico.md`: o relatório inteiro gerado pelo script (não versionado, porque contém dados da base da Gerdau). A seção 5 é este diagnóstico; as seções 1 a 4 e 6 são a EDA; a seção 7 é a padronização.

**Como reproduzir:** na pasta `saneamento`, rode `python diagnostico.py dados\base_codigos_servico.xlsx`. O teste `test_numeros_da_base_batem_com_o_documento` confere que os números acima continuam iguais.
