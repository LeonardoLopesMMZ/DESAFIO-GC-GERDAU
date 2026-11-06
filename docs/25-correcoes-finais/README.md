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
| Falando muito, parte da fala ia para o campo Unidade | teste do grupo na tela | só vai para Unidade o que vem depois da palavra "unidade" e é uma unidade conhecida; o resto fica na descrição |
| Botão de criar ficava lá embaixo, sem confirmação | teste do grupo na tela | Usar e Criar código novo logo abaixo da consulta, com animação de conclusão e formulário limpo para o próximo caso |
| A lista de unidades só mostrava a que estava escrita | teste do grupo na tela | a lista abre com todas as unidades e filtra enquanto digita |
| Palavra digitada errado só era corrigida depois de consultar | pedido do grupo | corretor automático ao apertar espaço e ao consultar, com Desfazer |
| Sem câmera, quem tinha rosto cadastrado não conseguia entrar | pedido do grupo | pedido de entrada com observação, liberado por um curador, com registro de quem liberou |
| Não dava para escolher a foto do perfil | pedido do grupo | foto do perfil fixa, pela câmera ou por arquivo |
| O anti-ruído às vezes não ouvia nada | teste do grupo na tela | o filtro passou a ser o do próprio navegador (redução de ruído, eco e isolamento de voz), o áudio é retomado quando o navegador pausa e, se o filtro falhar, o MOKKA ouve de novo sem ele sozinho |
| Pouco retorno visual ao clicar | pedido do grupo | ícones nos botões, onda ao clicar, check verde quando dá certo, tremida quando falta algo e um sinal com a cor e o ícone do resultado |
| O curador era só texto | pedido do grupo | o pedido e o código que existe lado a lado com as diferenças marcadas, anel de certeza, fotos de quem pediu, números do dia e cartão que sai com carimbo |

## Situação na versão apresentada

- Teste geral: **34 itens** (os 29 da Entrega 3 mais foto do perfil, entrada sem câmera, link do grupo, lote em JSON e texto e tela inicial).
- Testes do Python: **9 de 9**, com a base real e os 430 pares rotulados.
- Nenhum erro conhecido no fluxo da demonstração.

## Histórico no GitHub

Todas as mudanças estão na `main`, cada uma vinda de uma branch `feature/...` juntada pela `develop`. As versões marcadas são `v1.0` (PostgreSQL, curador que aprende, segurança), `v1.1` (abreviação no padrão do SAP e ganho no serviço) e `v1.2` (Entrega 3 e entrega final) `v1.3` (corretor, unidades, foto do perfil e entrada sem câmera) e `v1.4` (link seguro para o grupo, planilha até 100.000 linhas em vários formatos, tela inicial com demonstração e transições).
