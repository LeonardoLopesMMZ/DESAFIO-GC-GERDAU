# 24 · MVP final integrado

> **O que pede:** fluxo completo de consulta, sugestão e decisão sobre reutilização ou solicitação de novo código.
> **Critério de aceite:** o squad demonstra o fluxo ponta a ponta sem etapa manual escondida.

## O MVP em uma frase

Antes de criar um código de serviço no SAP, o comprador digita ou fala a descrição; o MOKKA mostra o que já existe e responde **BLOQUEAR** (use o existente), **REVISAR** (quase igual, veja o que muda) ou **LIBERAR** (crie). Quem insiste em criar um repetido vai para o curador, e cada decisão ensina o MOKKA.

## Ponta a ponta

| Passo | Quem | O que acontece | Onde ver |
| --- | --- | --- | --- |
| 1 | Comprador | entra (rosto, chave do Windows ou nome) | botão Entrar |
| 2 | Comprador | digita ou fala a descrição | aba Consultar e criar |
| 3 | MOKKA | padroniza, corrige, compara com 7.937 códigos e ordena os candidatos | resultado e candidatos |
| 4 | Comprador | usa o existente ou pede um código novo | botões "Usar este" e "Criar código" |
| 5 | Curador | aprova ou recusa os pedidos de repetidos, em grupo | aba Curador |
| 6 | MOKKA | grava tudo, cria o código, aprende com a decisão | ficha do código, aba Pessoas |
| 7 | Comprador | manda uma planilha inteira de uma vez | aba Planilha |
| 8 | Gestão | vê a saúde da base e o ganho no serviço | aba Raio-X |

Detalhes de cada parte: pastas 18 a 22 da Entrega 3.

## Como ligar

- **Windows:** `MOKKA.bat` (ou o atalho `1 - Ligar o MOKKA e abrir o site` na pasta `0 - Apresentação`).
- **Mac ou Linux:** `bash MOKKA.command`.
- Na primeira vez ele baixa o que faltar (Java, Python, PostgreSQL); depois liga em uns 30 segundos, sem internet (só a voz precisa de internet).

## Prova de que funciona

`MOKKA.bat testar` passou **29 de 29** na versão apresentada (`21 - Integração completa\testar.log`, na Entrega 3).
