# Saneamento dos códigos de serviço

Limpeza, padronização e deduplicação dos códigos de serviço do SAP, e busca de códigos parecidos antes de cadastrar um novo.

## Como rodar

Precisa de Python 3.9 ou mais novo.

```
cd saneamento
python -m pip install -r requirements.txt
```

A base da Gerdau não é versionada. Coloque a planilha em `saneamento/dados/` e passe o caminho para os scripts, por exemplo `python diagnostico.py dados\base_codigos_servico.xlsx`. Os resultados saem em `saneamento/saida/`, que também não é versionada.

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

## Testes

```
python test_padronizar.py
```

Os testes que precisam da base são pulados quando ela não está em `dados/`.
