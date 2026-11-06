# 21 · Integração completa

> **O que pede:** consulta → similaridade → ranking → Java → decisão do usuário.
> **Critério de aceite:** todo o fluxo funciona ponta a ponta sem etapa manual escondida.

## O caminho de uma consulta

```
Tela (navegador)
  │  POST /api/consultas  {descricao, unidade, grupo}  + token
  ▼
Java · Spring Boot (porta 8080)
  │  confere o token e a unidade
  │  POST http://127.0.0.1:8000/similares  + segredo do MOKKA
  ▼
Python (porta 8000, só no próprio computador)
  │  1. padroniza (dicionário de 136 termos)
  │  2. acha os 60 textos mais parecidos (TF-IDF de pedaços de letras)
  │  3. régua: DUPLICADO, REVISAR ou DIFERENTE, com o que muda
  │  4. ranking: classe → aderência → código; devolve o código oficial
  │  5. se nada bate: tenta corrigir a escrita ("Você quis dizer...")
  ▼
Java
  │  aplica o que o MOKKA aprendeu (regras IGUAL e DIFERENTE)
  │  grava a solicitação no PostgreSQL
  ▼
Tela: BLOQUEAR / REVISAR / LIBERAR, candidatos e o que muda
  │  POST /api/solicitacoes/{id}/decisao  {USAR_EXISTENTE | CRIAR_NOVO}
  ▼
Java: grava a decisão, cria o código (9000001...), vota no aprendizado
  │  POST /codigos no Python
  ▼
Python: o código novo entra na busca na hora
```

## Sem etapa manual escondida

| Etapa | Quem faz | Manual? |
| --- | --- | --- |
| Ligar o banco, o Python e o Java | `MOKKA.bat` | não |
| Montar o catálogo a partir da planilha | `MOKKA.bat` (Python gera o `data.sql`, o Java carrega) | não |
| Consultar, decidir, criar | tela → API | não |
| Código novo entrar na busca | Java avisa o Python | não |
| Curador e aprendizado | tela → API | não |
| Python ou Java fora | modo reserva e modo local | não |

## Evidência

- `testar.log`: o teste geral do `MOKKA.bat testar` passou **34 de 34**. Ele liga o sistema e passa pelo fluxo inteiro pela API: segurança, consulta BLOQUEAR e LIBERAR, correção de fala, curador em grupo, aprendizado e desfazer, palavra ensinada, lote, Excel, modo reserva, Raio-X, Swagger e os 9 testes do Python (inclusive "nunca junta errado" nos 430 pares).
- `banco-em-uso.json`: as evidências foram geradas com o PostgreSQL ligado (`"persistente": true`).
- Os cenários da pasta 19 foram gravados pela tela de ponta a ponta, sem nenhum passo à mão.

> No GitHub fica só este README. As evidências citadas acima ficam na pasta da entrega, no computador do grupo; o `gerar evidencias de novo.bat` grava tudo de novo com o MOKKA ligado.

## Também pelo link do grupo

O mesmo fluxo funciona de outro computador pelo link seguro (`0 - Apresentação\5 - Compartilhar com o grupo`): tela → Java → Python → banco, sem passo manual. O teste "Segurança: link do grupo" confere que, pelo link, o MOKKA não se trata como o computador que liga e fecha o Swagger.
