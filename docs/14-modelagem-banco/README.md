# 14 · Modelagem de banco de dados

> **O que pede:** apresentar a modelagem de dados, mantendo a base original e a nova com a análise; aplicar a modelagem em um banco criado; desenvolver o banco para a persistência conforme a modelagem.
> **Critério de aceite:** apresentar as tabelas e colunas necessárias para a aplicação.

O catálogo tem **5 tabelas**. A base da Gerdau fica **como veio** em `base_original`; a análise fica **ao lado**, em `codigo_servico`, ligada pelo código. Assim dá para comparar original e tratado a qualquer momento e desfazer qualquer junção.

## Diagrama

```mermaid
erDiagram
    base_original }o--|| codigo_servico : "codigo_servico = codigo"
    codigo_servico ||--o{ codigo_fornecedor : "usado por"
    codigo_servico |o--o{ codigo_servico : "codigo_canonico aponta para o oficial"
    solicitacao }o--o| codigo_servico : "codigo_sugerido / codigo_escolhido"

    base_original {
        bigint id PK
        varchar grupo_servico
        varchar codigo_fornecedor
        varchar codigo_servico
        varchar descricao_item
        varchar unidade_medida
    }
    codigo_servico {
        varchar codigo PK
        varchar descricao
        varchar descricao_limpa
        varchar descricao_padrao
        varchar unidade
        varchar grupo
        varchar categoria
        varchar status
        varchar codigo_canonico FK
        varchar motivo
    }
    codigo_fornecedor {
        varchar codigo PK, FK
        varchar fornecedor PK
    }
    dicionario {
        varchar termo PK
        varchar padrao
    }
    solicitacao {
        bigint id PK
        varchar descricao_digitada
        varchar unidade
        varchar grupo
        varchar solicitante
        varchar resultado
        varchar codigo_sugerido
        varchar codigo_escolhido
        varchar candidatos
        varchar justificativa
        varchar status
        timestamp criada_em
    }
```

## Tabelas e colunas

**`base_original` — a base da Gerdau, sem nenhuma alteração** (uma linha por linha da planilha)

| Coluna | Tipo | Conteúdo |
| --- | --- | --- |
| id | BIGINT, chave | número da linha |
| grupo_servico | VARCHAR(40) | Civil, Facilities, Eletromecânica |
| codigo_fornecedor | VARCHAR(12) | código do fornecedor |
| codigo_servico | VARCHAR(10) | código do serviço no SAP |
| descricao_item | VARCHAR(60) | descrição como veio |
| unidade_medida | VARCHAR(5) | unidade como veio |

**`codigo_servico` — a base nova, com a análise** (um registro por código)

| Coluna | Tipo | Conteúdo |
| --- | --- | --- |
| codigo | VARCHAR(10), chave | código do serviço |
| descricao | VARCHAR(60), obrigatória | descrição original |
| descricao_limpa | VARCHAR(200) | texto depois da padronização (pasta 03) |
| descricao_padrao | VARCHAR(40) | nome padrão sugerido, até 40 caracteres |
| unidade | VARCHAR(5), obrigatória | unidade |
| grupo | VARCHAR(40), obrigatória | grupo de serviço |
| categoria | VARCHAR(40) | mão de obra, obra civil, mobilização... (14 categorias) |
| status | VARCHAR(10), padrão ATIVO | ATIVO = oficial; BLOQUEADO = substituído por outro |
| codigo_canonico | VARCHAR(10), chave estrangeira para `codigo_servico` | o código oficial que substitui este (vazio se ele é o oficial) |
| motivo | VARCHAR(80) | por que foi juntado ou mantido |

**`codigo_fornecedor` — quem usa cada código**

| Coluna | Tipo | Conteúdo |
| --- | --- | --- |
| codigo | VARCHAR(10), chave, estrangeira | código do serviço |
| fornecedor | VARCHAR(12), chave | código do fornecedor |

**`dicionario` — abreviações da padronização**

| Coluna | Tipo | Conteúdo |
| --- | --- | --- |
| termo | VARCHAR(30), chave | abreviação (ex.: HE) |
| padrao | VARCHAR(40), obrigatória | termo completo (ex.: HORA EXTRA) |

**`solicitacao` — cada consulta feita na aplicação**

| Coluna | Tipo | Conteúdo |
| --- | --- | --- |
| id | BIGINT, chave | número da consulta |
| descricao_digitada | VARCHAR(40), obrigatória | o que o comprador digitou |
| unidade | VARCHAR(5), obrigatória | unidade informada |
| grupo | VARCHAR(40) | grupo informado |
| solicitante | VARCHAR(60), obrigatória | quem consultou |
| resultado | VARCHAR(10) | BLOQUEAR, REVISAR ou LIBERAR |
| codigo_sugerido | VARCHAR(10) | primeiro candidato |
| codigo_escolhido | VARCHAR(10) | código que o comprador decidiu usar |
| candidatos | VARCHAR(200) | códigos mostrados, na ordem (para medir reaproveitamento) |
| justificativa | VARCHAR(200) | por que criou código novo |
| status | VARCHAR(20), padrão PENDENTE | PENDENTE, USOU_EXISTENTE, CRIADO, AGUARDANDO_CURADOR |
| criada_em | TIMESTAMP | data e hora da consulta |

## Banco criado e carregado

Aplicamos a modelagem num **PostgreSQL 16** criado do zero e carregamos a base piloto inteira. Resultado lido do banco (`evidencia_postgresql.txt`):

| Tabela | Linhas |
| --- | --- |
| base_original | 9.795 |
| codigo_servico | 7.937 (7.056 ATIVO + 881 BLOQUEADO) |
| codigo_fornecedor | 9.795 |
| dicionario | 118 |
| solicitacao | 5 (as consultas da demonstração) |

**Original e análise lado a lado.** Esta consulta junta as duas bases e mostra, para cada linha original, qual é o código oficial:

```sql
SELECT o.codigo_fornecedor, o.codigo_servico, o.descricao_item, c.status,
       COALESCE(c.codigo_canonico, c.codigo) AS codigo_oficial
FROM base_original o
JOIN codigo_servico c ON c.codigo = o.codigo_servico
WHERE COALESCE(c.codigo_canonico, c.codigo) = '4076413';
```

Ela devolve 12 linhas originais, de 8 fornecedores, que viram um único código oficial. O resultado completo está em `evidencia_postgresql.txt`.

## Como criar o banco

1. Na pasta `codigos-api`: `docker compose up -d` (sobe o PostgreSQL 16 e já roda o `schema.sql`).
2. Na pasta `saneamento`: `python exportar.py dados\base_codigos_servico.xlsx --db postgresql+psycopg://codigos:codigos@localhost:5432/codigos` (carrega as 4 tabelas de dados numa única transação; pode rodar de novo quando quiser).
3. Suba o Java com o perfil postgres (pasta 09).

Sem Docker, rode o `schema.sql` em qualquer PostgreSQL e use o mesmo comando do passo 2. Para testar sem banco nenhum, o Java usa H2 em memória com o `schema.sql` e o `data.sql` que o `exportar.py` gera.

## Arquivos nesta pasta

- `schema.sql`: criação das 5 tabelas (o mesmo arquivo usado pelo Java e pelo docker-compose).
- `data.sql`: não fica no repositório; o `exportar.py` gera em `saneamento/saida/` a partir da base.
- `evidencia_postgresql.txt`: saída do banco com as tabelas, as contagens, a consulta de original × oficial e as solicitações gravadas.

## Pessoas, aprendizado e segurança

Além das tabelas do catálogo, o `schema.sql` cria as que guardam quem usa o sistema e o que ele aprende:

| Tabela | Para que serve | Colunas principais |
| --- | --- | --- |
| `usuario` | contas | `nome`, `perfil` (COMPRADOR, CURADOR, ADMIN), `rosto` (128 números), `credencial_id` e `chave_publica` (chave do Windows), `retrato_id` |
| `retrato` | fotos pequenas do cadastro e de cada entrada pelo rosto | `nome`, `tipo` (CADASTRO ou ENTRADA), `distancia`, `foto` (JPEG criptografado com AES-256-GCM) |
| `aprendizado` | descrição ligada a um código pelas decisões | `chave` (palavras em ordem alfabética + unidade), `codigo`, `tipo` (IGUAL ou DIFERENTE), `votos`, `ultimo_por` |
| `palavra_aprendida` | correções de palavras ensinadas pelos compradores | `termo`, `correcao`, `votos`, `status` (SUGERIDA, ACEITA, RECUSADA) |
| `auditoria` | registro de segurança | `quando`, `quem`, `acao`, `alvo`, `detalhe`, `ip` |

No PostgreSQL (porta 5433, só em 127.0.0.1) o banco se chama `mokka` e pertence ao usuário `mokka_app`, que não é superusuário. O `schema.sql` roda a cada vez que o sistema liga e só cria o que falta; o catálogo é carregado na primeira vez ou quando alguém zera o banco.
