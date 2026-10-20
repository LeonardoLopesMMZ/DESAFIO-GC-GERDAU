# 05 · Tratamento dos dados V1

> **O que pede:** aplicação das primeiras regras de limpeza, normalização e padronização da base.
> **Critério de aceite:** existe uma primeira versão tratada dos dados e as transformações realizadas estão documentadas.

A versão tratada é a planilha `saida/base_codigos_servico_limpa.xlsx`, gerada pelo `limpar.py` (não versionado, porque contém dados da base da Gerdau), com **todas as 9.795 linhas originais** e 7 colunas novas ao lado. Nenhuma coluna original foi alterada.

## Transformações aplicadas, em ordem

| # | Transformação | O que faz | Resultado na base |
| --- | --- | --- | --- |
| 1 | Leitura | lê xlsx ou csv, detecta separador e acentuação, confere as 5 colunas e trata códigos como texto | 9.795 linhas |
| 2 | Limpeza de espaços | tira espaço invisível, duplo e nas bordas | 365 códigos alterados |
| 3 | Maiúsculas e acentos | caixa alta e sem acento | 2.812 e 2.951 códigos alterados |
| 4 | Pontuação | pontuação vira espaço; letra e número separados | 6.502 códigos alterados |
| 5 | Dicionário | 118 abreviações viram termo completo; palavras vazias saem | 6.935 códigos alterados |
| 6 | Leitura de atributos | cargo, tipo de hora, percentual e categoria | 14 categorias; 5,9% em OUTROS |
| 7 | Nome padrão | CARGO - TIPO DE HORA, até 40 caracteres | 2.988 códigos com nome sugerido |
| 8 | Agrupamento | códigos com o mesmo texto limpo e a mesma unidade formam um grupo | 631 grupos com repetição |
| 9 | Referência e confiança | o código usado por mais fornecedores é a referência; texto que pode ter sido cortado vai para conferência | 846 duplicatas: 225 de confiança alta, 621 para conferir |

As regras de cada etapa estão em `03-padronizacao-textual.md`. A versão mais completa da deduplicação, que também junta abreviações e mão de obra pelo cargo e tipo de hora, está em `06-catalogo-saneado-v1.md`.

## Colunas novas da planilha

| Coluna | Como é calculada |
| --- | --- |
| Descrição limpa | texto depois das etapas 2 a 5 |
| Categoria | primeira palavra-chave de categoria encontrada no texto limpo |
| Família (cargo · tipo de hora) | cargo e tipo de hora lidos do texto (só mão de obra) |
| Nome padrão sugerido | CARGO - TIPO DE HORA, até 40 caracteres |
| Situação | único, referência do grupo ou duplicata |
| Código de referência | o código que deveria ser usado no lugar deste |
| Confiança | alta, ou "conferir: pode ser texto cortado" |

A aba **Duplicatas** lista só os códigos que repetem outro, lado a lado com o código de referência, para conferência rápida no Excel.

## Resultado

| Situação | Códigos |
| --- | --- |
| Único | 6.460 |
| Referência do grupo | 631 |
| Duplicata | 846 |
| **Total** | **7.937** |

**Como reproduzir:** na pasta `saneamento`, rode `python limpar.py dados\base_codigos_servico.xlsx`. O resultado sai em `saida\base_codigos_servico_limpa.xlsx`. O teste `test_planilha_limpa` confere que nenhuma linha se perde, que todo nome tem até 40 caracteres e que texto cortado não recebe confiança alta.
