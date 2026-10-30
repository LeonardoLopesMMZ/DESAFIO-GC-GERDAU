# Saneamento dos códigos de serviço

Limpeza, padronização e deduplicação dos códigos de serviço do SAP, e busca de códigos parecidos antes de cadastrar um novo.

## Como rodar

Precisa de Python 3.11 a 3.13.

```
cd saneamento
python -m pip install -r requirements.txt
```

A base da Gerdau não é versionada. Coloque a planilha em `saneamento/dados/` e passe o caminho para os scripts, por exemplo `python diagnostico.py dados\base_codigos_servico.xlsx`. Os resultados saem em `saneamento/saida/`, que também não é versionada.

Sem a base real, use a base de exemplo fictícia que vem no repositório, por exemplo `python api.py exemplo/base_exemplo.csv`.

## Etapas

| Etapa | Documento | Script |
| --- | --- | --- |
| Padronização textual | [03-padronizacao-textual.md](docs/03-padronizacao-textual.md) | padronizar.py |
| Diagnóstico de qualidade | [01-diagnostico-qualidade.md](docs/01-diagnostico-qualidade.md) | diagnostico.py |
| EDA inicial | [02-eda-inicial.md](docs/02-eda-inicial.md) | diagnostico.py |
| Tratamento dos dados V1 | [05-tratamento-dados-v1.md](docs/05-tratamento-dados-v1.md) | limpar.py |
| Hipótese de similaridade | [04-hipotese-similaridade.md](docs/04-hipotese-similaridade.md) | regua.py |
| Catálogo saneado V1 | [06-catalogo-saneado-v1.md](docs/06-catalogo-saneado-v1.md) | deduplicar.py |
| Base de dados com saneamento feito | [base-dados-saneada.md](docs/base-dados-saneada.md) | exportar.py, schema.sql |
| Similaridade V1 | [07-similaridade-v1.md](docs/07-similaridade-v1.md) | similaridade.py, api.py |
| Ranking de candidatos V1 | [08-ranking-candidatos-v1.md](docs/08-ranking-candidatos-v1.md) | similaridade.py |
| Lote por Excel e correção de escrita | [README principal](../README.md) | lote.py, similaridade.py, api.py |
| Catálogo saneado V2 | [15-catalogo-saneado-v2](../docs/15-catalogo-saneado-v2/README.md) | deduplicar.py, padronizar.py |
| Similaridade V2 e validação | [16-similaridade-v2](../docs/16-similaridade-v2/README.md), [17-validacao-da-similaridade](../docs/17-validacao-da-similaridade/README.md) | similaridade.py, validar.py |
| Checkpoint dos squads e planejamento | [checkpoint-e-planejamento.md](docs/checkpoint-e-planejamento.md) | — |

## Testes

```
python test_padronizar.py
```

Os testes que precisam da base são pulados quando ela não está em `dados/`. O teste "nunca junta errado" usa também `dados/pares_rotulados.csv` (430 pares conferidos à mão, com dados reais, fora do GitHub).

## Validação

```
python validar.py
```

Mede, com a base real, os 430 pares conferidos à mão, 334 consultas com erro de escrita ou de fala e 15 serviços que não existem na base. Os resultados saem em `saida/validacao_*.csv`.
