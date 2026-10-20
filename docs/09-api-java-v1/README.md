# 09 · API Java V1

> **O que pede:** endpoint básico para consultar códigos semelhantes a partir de uma descrição.
> **Critério de aceite:** a API executa, recebe uma consulta e retorna uma resposta no formato definido.

Projeto Spring Boot 3.5 com Java 17, em `codigos-api`. O endpoint principal é **`POST /api/consultas`**.

## Formato definido

**Pedido**

```http
POST http://localhost:8080/api/consultas
Content-Type: application/json

{"descricao": "VIGILANTE 12X36 NOTURNO", "unidade": "SV", "grupo": "Facilities", "solicitante": "Mentor"}
```

| Campo | Obrigatório | Regra |
| --- | --- | --- |
| descricao | sim | até 40 caracteres (limite do SAP) |
| unidade | sim | código SAP ou o nome normal ("hora", "metro quadrado", "diária"); a API troca pelo código (H, M2, TAG) e recusa unidade desconhecida |
| grupo | não | até 40 caracteres |
| solicitante | sim | até 60 caracteres |

**Resposta** (resposta real, encurtada para 3 candidatos; arquivo completo em `exemplo_resposta_consulta.json`)

```json
{
  "solicitacao_id": 5,
  "resultado": "BLOQUEAR",
  "motivo": "Já existe: 4122123 VIGILANTE NOTURNO 12X36",
  "candidatos": [
    {
      "codigo": "4122123",
      "descricao": "VIGILANTE NOTURNO 12X36",
      "nome_padrao": "VIGILANTE - ADIC NOTURNO",
      "unidade": "SV",
      "categoria": "MAO DE OBRA",
      "aderencia": 100,
      "classe": "DUPLICADO",
      "diferencas": [],
      "encontrado_por": {"codigo": "4122123", "descricao": "VIGILANTE NOTURNO 12X36"}
    }
  ]
}
```

| Campo | Significado |
| --- | --- |
| solicitacao_id | número da consulta gravada no banco (pasta 10) |
| resultado | BLOQUEAR (já existe igual), REVISAR (quase igual) ou LIBERAR (nada igual) |
| motivo | frase pronta para mostrar na tela |
| candidatos | até 10 códigos oficiais, na ordem da pasta 08 |
| classe / diferencas | o que a régua achou e o que muda (pasta 04) |
| encontrado_por | o código antigo que levou até o oficial, quando é diferente |

## Todos os endpoints

| Método e caminho | O que faz |
| --- | --- |
| `POST /api/consultas` | consulta códigos parecidos e grava a solicitação |
| `POST /api/solicitacoes/{id}/decisao` | grava a decisão do comprador (usar existente ou criar novo) |
| `GET /api/solicitacoes?status=` | lista as 50 últimas solicitações; com status, a fila do curador |
| `GET /api/codigos?busca=` | busca simples no catálogo por trecho do texto |
| `GET /api/codigos/{codigo}` | detalhe do código, fornecedores e, se bloqueado, o oficial |
| `GET /actuator/health` | diz se a aplicação está no ar |

## Erros

| Status | Quando |
| --- | --- |
| 400 | campo inválido (ex.: descrição com mais de 40 caracteres), justificativa faltando |
| 404 | código ou solicitação não existe |
| 409 | a solicitação já foi decidida |
| 502 | a lógica de similaridade respondeu com erro |
| 503 | a lógica de similaridade está fora do ar |

O corpo do erro traz `message` em português, por exemplo: "A descrição deve ter até 40 caracteres".

## Como executar

```powershell
cd "C:\...\DESAFIO-GC-GERDAU\saneamento"
python api.py dados\base_codigos_servico.xlsx

cd "C:\...\DESAFIO-GC-GERDAU\codigos-api"
.\mvnw.cmd spring-boot:run
```

Teste rápido no PowerShell:

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/consultas `
  -ContentType "application/json; charset=utf-8" `
  -Body '{"descricao":"MEC.MANUT.NORM","unidade":"H","solicitante":"Mentor"}'
```

Passo a passo completo em `13 - Documentação\GUIA_JAVA.txt`.

## Os outros endpoints

| Método e caminho | O que faz |
| --- | --- |
| `POST /api/solicitacoes/{id}/decisao` | `{"acao": "USAR_EXISTENTE", "codigo": "..."}` ou `{"acao": "CRIAR_NOVO", "justificativa": "..."}`. Criar gera o código (a partir de 9000001), grava no catálogo e avisa o serviço de similaridade; se já existe igual, vai para o curador |
| `POST /api/solicitacoes/{id}/curadoria` | `{"aprovar": true}` cria o código; `{"aprovar": false}` manda usar o existente |
| `GET /api/solicitacoes?status=AGUARDANDO_CURADOR` | pedidos esperando o curador |
| `GET /api/codigos?busca=` | busca no catálogo por descrição ou número do código |
| `GET /api/codigos/novos` | códigos criados pela aplicação |
| `GET /api/unidades` | as 17 unidades da base: código SAP, o que é, como pode ser escrita, exemplos e quantos códigos ativos usam |
| `GET /api/codigos/{codigo}` | detalhe do código, fornecedores e código oficial |

O serviço Python ganhou `POST /codigos`, que recebe `codigo`, `descricao`, `unidade` e `grupo` e passa a encontrar o código novo nas próximas consultas.
