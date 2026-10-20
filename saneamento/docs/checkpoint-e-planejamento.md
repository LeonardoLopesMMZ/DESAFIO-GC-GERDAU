# Checkpoint dos squads e planejamento

Situação da parte de saneamento e similaridade para a Entrega 2, o que a Gerdau já respondeu e o que precisa ser decidido na reunião com os pontos focais.

## Onde estamos

| Item da Entrega 2 | Situação | Documento |
| --- | --- | --- |
| Diagnóstico de qualidade | pronto | `01-diagnostico-qualidade.md` |
| EDA inicial | pronto | `02-eda-inicial.md` |
| Padronização textual | pronto | `03-padronizacao-textual.md` |
| Hipótese de similaridade | pronto | `04-hipotese-similaridade.md` |
| Tratamento dos dados V1 | pronto | `05-tratamento-dados-v1.md` |
| Catálogo saneado V1 | pronto | `06-catalogo-saneado-v1.md` |
| Base de dados com saneamento feito | pronto | `base-dados-saneada.md` |
| Similaridade V1 | pronto | `07-similaridade-v1.md` |
| Ranking de candidatos V1 | pronto | `08-ranking-candidatos-v1.md` |
| API Java, persistência, fluxo de consulta e integração | com a equipe Java | contrato da API em `07-similaridade-v1.md` |

Números da base piloto: 7.937 códigos viram 7.056 oficiais (−11,1%), ou 6.619 (−16,6%) com as decisões de negócio de exemplo, com 0 junções erradas nos 430 pares conferidos à mão. A busca de similares responde em cerca de 16 ms.

## O que a Gerdau respondeu (12/10)

- Cerca de 20 compradores criam códigos logo depois de negociar, sem consultar os que já existem. Isso confirma que o problema nasce no cadastro e que a consulta precisa acontecer antes de criar.
- A prioridade é a mão de obra. Os outros itens (materiais, equipamentos, locações, mobilização) precisam ao menos ser identificáveis para somar valores; por isso todo código ganhou uma categoria.
- Quem decide é o comprador. Um segundo aprovador pode existir para exceções, mas não pode atrasar o processo.

## Proposta de governança

| Resultado da consulta | O que acontece |
| --- | --- |
| LIBERAR (nada igual) | o comprador cria na hora |
| REVISAR (quase igual) | o comprador usa o existente ou cria com justificativa; o curador revisa as criações da semana em lote |
| BLOQUEAR (já existe igual) | o comprador usa o existente; criar mesmo assim vai para o curador, com resposta em até 24 h; sem resposta, o código é liberado com marcação e entra na revisão |

Um curador por grupo: Civil, Facilities e Eletromecânica.

## Para decidir na reunião

1. Palavras de contexto mudam o serviço? EVENTUAL juntaria 143 códigos, À DISPOSIÇÃO 129, ADM 72 e ESP 42.
2. Hora (H) e serviço (SV) são a mesma unidade para itens cobrados por hora? Juntaria 63 códigos.
3. O percentual da hora extra e da insalubridade fica no nome do código ou só no contrato?
4. Hora CHEIA e hora RESIDUAL são serviços diferentes?
5. Hora extra de segunda a sexta e de sábado podem ser o mesmo tipo (HE seg-sáb)?

## Pedidos de dados à Gerdau

- Data de criação e usuário que criou cada código, para medir quantos duplicados nascem por mês.
- Gasto por código e pedidos ou contratos abertos, para escolher o oficial e aposentar os repetidos sem travar compras.
- Se os serviços estão no SAP ECC ou S/4HANA, porque isso muda como um código é bloqueado.

## Próximos passos

| Prazo | Ação | Responsável |
| --- | --- | --- |
| Reunião com a Gerdau | levar as 5 perguntas e a proposta de governança | Leonardo Lopes |
| Depois da reunião | aplicar as respostas no `deduplicar.py` e regerar o catálogo | Leonardo Lopes e Thales |
| Entrega 2 | sortear 400 códigos e conferir à mão, para medir precisão e cobertura sem viés | parte de dados |
| Entrega 2 | incluir a qualificação (6G, maçariqueiro, RX) no nome padrão da mão de obra | parte de dados |
| Entrega 2 | montador do nome padrão na tela de consulta, com cargo e tipo de hora em listas | equipe Java |
| Depois | tela do curador e regra de prazo de 24 h | equipe Java |
