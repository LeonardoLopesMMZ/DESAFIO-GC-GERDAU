# 25 · Correções finais

> **O que pede:** ajustes de bugs e problemas encontrados na Sprint 3.
> **Critério de aceite:** não existem erros impeditivos no fluxo principal da demonstração.

## Problemas encontrados e resolvidos

| Problema | Como apareceu | Correção |
| --- | --- | --- |
| Teste "palavra ensinada" falhava no Windows | teste geral: 27 de 29 | o PowerShell transformava a lista de uma sugestão só em texto e lia só a primeira letra; o teste passou a ler a lista do jeito certo |
| Testes do Python davam 403 | teste geral: 27 de 29 | depois da segurança nova, o Python só aceita quem manda o segredo do MOKKA; o teste passou a mandar |
| Descrição longa era recusada | uso pelo grupo | o campo aceita mais de 40 caracteres e o MOKKA abrevia no padrão do SAP ao consultar |
| A tela e o Python abreviavam de jeitos diferentes | revisão da Entrega 3 | as duas partes seguem a mesma ordem de abreviação |
| A economia em reais dependia de um custo inventado | feedback do grupo | virou "ganho no serviço", só com dados reais da base |
| Abreviações da base sem significado (INSTRUM, DISPOSI, NOTU...) | validação da Entrega 3 | dicionário de 118 para 136 termos, cada um conferido na base |
| Correção de escrita não entendia abreviação antes de corrigir | validação da Entrega 3 | consultas com erro resolvidas: 283 → 296 de 334 |
| Teste "nunca junta errado" era pulado no PC do grupo | revisão dos testes | o arquivo de pares rotulados mudou para `dados\`; o teste roda sempre |

## Situação na versão apresentada

- Teste geral: **29 de 29**.
- Testes do Python: **9 de 9**, com a base real e os 430 pares rotulados.
- Nenhum erro conhecido no fluxo da demonstração.

## Histórico no GitHub

Todas as mudanças estão na `main`, cada uma vinda de uma branch `feature/...` juntada pela `develop`. As versões marcadas são `v1.0` (PostgreSQL, curador que aprende, segurança), `v1.1` (abreviação no padrão do SAP e ganho no serviço) e `v1.2` (Entrega 3 e entrega final).
