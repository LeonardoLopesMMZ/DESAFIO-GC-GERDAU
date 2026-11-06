# 16 · Similaridade V2

> **O que pede:** evolução da lógica de comparação e do ranking de candidatos.
> **Critério de aceite:** o grupo demonstra o que mudou e por que os resultados melhoraram.

A base da V1 continua (TF-IDF de pedaços de 2 a 4 letras, régua de regras e ranking por classe, aderência e código). A V2 acrescenta o que faltava para o uso real: o comprador escreve errado, fala com o microfone e decide; o sistema precisa entender, corrigir e aprender.

## O que mudou

| # | Mudança | Onde | Antes (V1) | Agora (V2) |
| --- | --- | --- | --- | --- |
| 1 | **Correção de escrita e de fala** | Python | "SOLDA DOR" ou "ELETRECISTA" não batiam com nada: LIBERAR | o MOKKA compara com o vocabulário da própria base, junta palavra partida e corrige letra trocada ou faltando: "Você quis dizer SOLDADOR HORA EXTRA 50%?" |
| 2 | **Aprende com as decisões** | Java | cada consulta começava do zero | "usar este" e a recusa do curador votam IGUAL; a aprovação do curador vota DIFERENTE. A próxima consulta parecida já vem com o código certo ("O MOKKA aprendeu com 13 decisões"). Tudo pode ser desfeito |
| 3 | **Palavras ensinadas** | Java + Python | — | o comprador corrige uma palavra na tela; ela só passa a valer quando o curador aceita |
| 4 | **Código novo entra na busca na hora** | Python | só depois de reiniciar | criou o código, a consulta seguinte já o encontra (cenário 3 da pasta 19) |
| 5 | **Repetido dentro da própria planilha** | Python | — | no lote, a linha 9 igual à linha 2 sai como "repetido na planilha" |
| 6 | **Dicionário 118 → 136 termos** | Python | INSTRUM, DISPOSI, NOTU... ficavam soltos | entendidos (pasta 15) |
| 7 | **Entrada no padrão do SAP** | tela + Python | mais de 40 caracteres era recusado | o MOKKA abrevia no padrão da base antes de consultar |
| 8 | **Modo reserva** | Java | Python fora = sistema fora | o Java consulta direto no banco e continua respondendo |
| 9 | **Lote mais rápido e maior** | Python | até 5.000 linhas, cada linha comparada com todas as outras | linhas iguais analisadas uma vez, repetidos procurados só na mesma unidade e com os mesmos números (só ali podem ser iguais pela régua) e correções conferidas em bloco. O resultado é o mesmo (conferido linha a linha em 4.000 linhas); 100.000 linhas levam uns 3 minutos e a base inteira uns 7 segundos |

## Por que os resultados melhoraram: o teste

Sorteamos 400 códigos oficiais da base (sorteio fixo, semente 17) e colocamos **um erro** em cada descrição, como acontece de verdade: palavra partida pela fala ("SOLDA DOR"), letra faltando ("NOTURA") ou letras trocadas ("NOUTRNA"). Sobraram 334 consultas (as outras não tinham palavra longa para errar). A mesma base foi usada na V1 e na V2.

| | V1 (Entrega 2) | V2 (agora) |
| --- | --- | --- |
| Achou o código certo e avisou o comprador | **0** | **296 (89%)** |
| O código certo aparecia na lista dos 5 primeiros | 99% | 99% |
| Resultado mostrado | LIBERAR em 333 de 334 | LIBERAR com "Você quis dizer..." apontando o código certo |

**O que isso quer dizer:** a V1 já listava o código certo, mas dizia LIBERAR, porque a palavra errada parecia uma diferença real. Na prática, o comprador criaria um código duplicado. A V2 reconhece o erro e manda o comprador para o código que já existe.

Por tipo de erro:

| Erro | Consultas | V2 acertou |
| --- | --- | --- |
| palavra partida pela fala | 105 | 97 (92%) |
| letra faltando | 123 | 106 (86%) |
| letras trocadas | 106 | 93 (88%) |

O único "errado" é um acerto disfarçado: a base tem o código "H.EXTRA 50% ENCARRREGADO" (com três R); o erro sorteado tirou um R, e o MOKKA achou o outro código de ENCARREGADO HE 50%, que é o mesmo serviço.

**Sem efeito colateral:** os 15 serviços que não existem na base continuam saindo LIBERAR, sem nenhuma sugestão falsa (pasta 17), e os 10 exemplos da V1 dão o mesmo resultado na V2 (`exemplos_similaridade.csv`).

**Tempo:** 6 a 8 ms por consulta, contando a correção.

## Ranking

O critério da V1 continua (classe → aderência → código). A V2 coloca **antes de tudo** o que o MOKKA aprendeu: se o curador já decidiu que aquela descrição é o código X, X vem em primeiro com a classe DUPLICADO e a explicação "aprendido com N decisões". Uma decisão DIFERENTE tira o bloqueio daquele código para aquela descrição.

## Arquivos

| Arquivo | O que é |
| --- | --- |
| `validacao_erros_de_escrita.csv` | as 334 consultas com erro: original, consulta com erro, tipo, resultado da V1 e da V2, sugestão |
| `exemplos_similaridade.csv` | os 10 exemplos da V1 rodados na V2, com os 5 primeiros candidatos |

**Como reproduzir:** na pasta `MOKKA\saneamento`, rode `python validar.py`. Para comparar com a V1, passe o arquivo antigo: `python validar.py dados\base_codigos_servico.xlsx "...\Entrega 2\Codigo\python\similaridade.py"`.

> No GitHub fica só este README. Os arquivos citados acima têm dados reais da Gerdau e ficam na pasta da entrega, no computador do grupo; para gerar de novo, siga o "como reproduzir".
