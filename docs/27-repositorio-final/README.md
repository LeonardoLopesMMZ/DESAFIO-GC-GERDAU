# 27 · Repositório final

> **O que pede:** código e artefatos organizados na versão apresentada.
> **Critério de aceite:** o repositório possui os arquivos necessários para compreender a entrega.

**GitHub:** https://github.com/LeonardoLopesMMZ/DESAFIO-GC-GERDAU · branch `main` · versão apresentada: tag **`v1.2`**.

## O que tem em cada pasta

| Pasta | Conteúdo |
| --- | --- |
| `README.md` | o que é o MOKKA, como rodar do zero, o que cada aba faz, segurança, API e problemas comuns |
| `MOKKA.bat`, `MOKKA.command` | ligam tudo no Windows, no Mac e no Linux |
| `saneamento/` | Python: padronização, deduplicação, similaridade, correção de escrita, lote, Excel, API, validação e testes |
| `saneamento/docs/` | Entrega 2, etapas 01 a 08 (dados) |
| `saneamento/exemplo/` | base fictícia no formato da Gerdau, para quem não tem a base real |
| `codigos-api/` | Java/Spring Boot: API, regras, curador, aprendizado, segurança e a tela |
| `docs/09-...` a `docs/14-...` | Entrega 2, etapas 09 a 14 (Java e banco) |
| `docs/15-...` a `docs/23-...` | Entrega 3 |
| `docs/24-...` a `docs/28-...` | entrega final |
| `docs/COMO_TESTAR.md` | roteiro de teste à mão |
| `scripts/` | teste geral e PostgreSQL portátil |

## O que não está no repositório, de propósito

- A planilha da Gerdau e qualquer arquivo com dados reais (o `.gitignore` bloqueia `saneamento/dados/` e `saneamento/saida/`).
- Senhas, segredos e a chave das fotos (ficam em `.ferramentas/`, criada no computador de quem liga).

## Como entender a entrega em 5 minutos

1. Leia o `README.md` da raiz.
2. Rode `MOKKA.bat` e siga `docs/COMO_TESTAR.md`.
3. Para os números, veja `docs/16-similaridade-v2` e `docs/17-validacao-da-similaridade`.
