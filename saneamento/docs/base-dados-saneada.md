# Base de dados com saneamento feito

A base saneada guarda, lado a lado, **a base da Gerdau como veio** e **a análise feita sobre ela**. Nada da base original é alterado: cada código ganha texto limpo, nome padrão, categoria e, quando é repetido, o código oficial que o substitui.

## Tabelas

| Tabela | Uma linha por | Conteúdo |
| --- | --- | --- |
| `base_original` | linha da planilha | as 5 colunas exatamente como vieram: grupo, fornecedor, código, descrição e unidade |
| `codigo_servico` | código | descrição original, descrição limpa, nome padrão, unidade, grupo, categoria, status e código oficial |
| `codigo_fornecedor` | par código × fornecedor | quais fornecedores usam cada código |
| `dicionario` | abreviação | termo padrão usado na limpeza |
| `solicitacao` | consulta feita na aplicação | usada pela aplicação Java para guardar consultas e decisões |

**Como ler `codigo_servico`:** `status` é ATIVO para o código oficial e BLOQUEADO para o código repetido. No código bloqueado, `codigo_canonico` aponta para o oficial que vale no lugar dele, e `motivo` explica a junção.

**Original e análise juntas.** Esta consulta mostra, para cada linha da planilha original, qual é o código oficial:

```sql
SELECT o.codigo_fornecedor, o.codigo_servico, o.descricao_item, c.status,
       COALESCE(c.codigo_canonico, c.codigo) AS codigo_oficial
FROM base_original o
JOIN codigo_servico c ON c.codigo = o.codigo_servico;
```

## Resultado na base piloto

Carregada num PostgreSQL 16 criado do zero:

| Tabela | Linhas |
| --- | --- |
| base_original | 9.795 |
| codigo_servico | 7.937 (7.056 ATIVO + 881 BLOQUEADO) |
| codigo_fornecedor | 9.795 |
| dicionario | 118 |

## Como gerar

1. Crie as tabelas rodando `schema.sql` num PostgreSQL.
2. Na pasta `saneamento`, rode:

```
python exportar.py dados\base_codigos_servico.xlsx --db postgresql+psycopg://usuario:senha@localhost:5432/codigos
```

A carga acontece numa única transação: as tabelas são esvaziadas e recarregadas, então dá para rodar de novo sempre que a base mudar. Sem `--db`, o script só gera os CSVs das tabelas e um `data.sql` de amostra em `saida/` (não versionados, porque contêm dados da base da Gerdau).
