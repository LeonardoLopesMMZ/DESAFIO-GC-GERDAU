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

## Testes

```
python test_padronizar.py
```

Os testes que precisam da base são pulados quando ela não está em `dados/`.
