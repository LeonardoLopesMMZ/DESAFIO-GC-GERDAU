# 23 · Pendências finais

> **O que pede:** bugs, pequenos ajustes e documentação que precisam ser concluídos.

## Concluído nesta entrega

| Item | Tipo |
| --- | --- |
| Teste "palavra ensinada" falhava no Windows (PowerShell lia só a primeira letra quando vinha uma sugestão só) | bug do teste, corrigido |
| Testes do Python davam 403 depois que o serviço passou a exigir o segredo do MOKKA | bug do teste, corrigido |
| O campo da descrição recusava mais de 40 caracteres | ajuste: agora abrevia no padrão do SAP ao consultar |
| A abreviação da tela e a do Python seguiam ordens diferentes | ajuste: as duas usam a mesma regra |
| A economia do Raio-X era em reais, com um custo inventado | ajuste: virou "ganho no serviço", só com dados reais |
| Abreviações da base sem significado no dicionário (INSTRUM, DISPOSI, NOTU...) | ajuste: dicionário de 118 para 136 termos |
| Correção de escrita não entendia abreviação antes de corrigir | ajuste: 283 → 296 consultas com erro resolvidas |
| O teste "nunca junta errado" era pulado no PC do grupo (o arquivo de pares estava em outra pasta) | bug do teste, corrigido: roda sempre |
| Documentação da Entrega 3 e da entrega final | feito |
| Teste geral | 29 de 29 |

## Depende de uma resposta da Gerdau

Estas perguntas juntariam mais códigos, mas não cabe a nós decidir. Basta responder sim ou não; a mudança é uma linha no topo do `deduplicar.py`.

| Pergunta | Códigos que se juntariam |
| --- | --- |
| EVENTUAL muda o serviço? | 143 |
| À DISPOSIÇÃO muda o serviço? | 129 |
| ADM muda o serviço? | 72 |
| Unidade H e SV são a mesma coisa para mão de obra? | 63 |
| ESP (especializado) muda o serviço? | 42 |

## Próximos passos (fora do escopo do desafio)

- **Integração com o SAP:** hoje o MOKKA exporta o Excel "Pronto para o SAP"; o passo seguinte é criar o código direto no SAP por API.
- **Login corporativo:** trocar a entrada pelo nome pelo login da Gerdau (AD ou SSO); a entrada pelo rosto e a chave do Windows já funcionam.
- **Servidor:** hoje roda no computador de quem apresenta; em produção, vai para um servidor com HTTPS.
- **Textos cortados em 40 caracteres:** 247 códigos ficaram separados por segurança; com a descrição longa do SAP, dá para decidir cada um.
- **Códigos-sigla** ("SV-MEC-01-HN"): precisam de uma tabela de siglas da própria Gerdau.
