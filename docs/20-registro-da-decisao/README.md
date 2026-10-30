# 20 · Registro da decisão

> **O que pede:** armazenamento simples da consulta, sugestão apresentada e decisão tomada pelo usuário.
> **Critério de aceite:** é possível verificar o que aconteceu em uma solicitação.

## O que fica gravado em cada consulta

Toda consulta vira uma linha na tabela `solicitacao`, antes mesmo da decisão:

| Campo | O que guarda | Exemplo real (cenário 5) |
| --- | --- | --- |
| `descricao_digitada` | o que o comprador escreveu ou falou | PEDREIRO HORA NORMAL |
| `unidade`, `grupo` | unidade e grupo | H, Civil |
| `solicitante`, `metodo`, `retrato_id` | quem, como entrou e a foto da entrada pelo rosto | Comprador da Entrega 3, NOME |
| `resultado` | o que o MOKKA respondeu | BLOQUEAR |
| `codigo_sugerido` | o primeiro candidato | 4079249 |
| `candidatos` | todos os códigos mostrados, na ordem | 4079249, 4086432, 4086435, ... |
| `codigo_escolhido` | o código que ficou valendo | 4079249 (depois do curador) |
| `justificativa` | por que quis criar mesmo assim | Preciso de um código só para a obra da aciaria |
| `status` | PENDENTE, USOU_EXISTENTE, CRIADO, AGUARDANDO_CURADOR ou RECUSADO (o curador aprovar vira CRIADO) | RECUSADO |
| `curador`, `decidida_em` | quem decidiu e quando | |
| `criada_em` | quando a consulta foi feita | |

Além disso, a tabela `auditoria` registra **quem fez o quê, quando e de qual IP**: entradas, códigos criados, decisões do curador, desfazer, perfis trocados e fotos apagadas. E a tabela `aprendizado` guarda as regras IGUAL e DIFERENTE com os votos e quem votou por último.

## Como verificar uma solicitação

- **Na tela:** aba Catálogo → clicar no código → ficha com quem criou (com a foto), como entrou, quando e quem usou depois. Aba Pessoas → registro de segurança.
- **Pela API:** `GET /api/solicitacoes` (as últimas 50), `GET /api/solicitacoes/curador` (a fila do curador), `GET /api/admin/auditoria` (registro de segurança).
- **No banco:** `select * from solicitacao where id = ...`.

## Arquivos

| Arquivo | O que é |
| --- | --- |
| `registro-das-decisoes.json` | as solicitações dos cenários da pasta 19, como ficaram gravadas no PostgreSQL |
| `auditoria.json` | as 30 últimas linhas do registro de segurança |
| `fila-do-curador.json` | a fila do curador no momento da geração |

> No GitHub fica só este README. As evidências citadas acima ficam na pasta da entrega, no computador do grupo; o `gerar evidencias de novo.bat` grava tudo de novo com o MOKKA ligado.
