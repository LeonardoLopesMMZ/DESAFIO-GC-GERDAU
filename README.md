# Desafio GC 2026 · Gerdau · Códigos de serviço

Protótipo do Grupo 17 para acabar com os códigos de serviço repetidos no SAP. Antes de criar um código, o comprador digita a descrição. O sistema mostra os códigos parecidos que já existem e dá um de três resultados:

- **BLOQUEAR**: já existe um código igual.
- **REVISAR**: existe um quase igual, e para criar mesmo assim é preciso justificar.
- **LIBERAR**: não existe nada parecido, e o código novo é criado na hora.

Quem insiste em criar um código que foi bloqueado cai na fila do curador. Todo código criado passa a valer na próxima consulta, então o mesmo serviço não é cadastrado duas vezes.

## O que tem aqui

| Pasta | O que é |
| --- | --- |
| `saneamento/` | Python: limpeza e padronização das descrições, deduplicação, catálogo saneado, busca de similares e a API que o Java chama |
| `codigos-api/` | Java 17 + Spring Boot: o site (consultar e criar, unidades, catálogo e curador) e a API REST |
| `docs/` | documentação das etapas do Java (API, persistência, fluxo de consulta, integração, documentação e modelagem do banco) |
| `iniciar.bat` | liga tudo no Windows com dois cliques |

A documentação das etapas de dados (diagnóstico, EDA, padronização, similaridade, catálogo, ranking) está em `saneamento/docs/`.

## O que precisa

- Java 17 ou mais novo ([Temurin](https://adoptium.net))
- Python 3.11 a 3.13 (ou Miniconda)
- Internet só na primeira vez, para baixar as bibliotecas do Python e do Maven
- A planilha da Gerdau: ela **não fica no repositório**. Peça para o grupo e coloque em `saneamento/dados/base_codigos_servico.xlsx`. A pasta `dados/` e todo `.xlsx` estão no `.gitignore`.

## Como ligar (Windows)

1. Coloque a planilha em `saneamento/dados/base_codigos_servico.xlsx`.
2. Dê dois cliques em `iniciar.bat`.
3. Na primeira vez ele:
   - prepara o Python em `saneamento/.venv`;
   - monta o catálogo (`saneamento/saida/data.sql`);
   - compila o Java.

   Isso leva uns minutos. Nas próximas vezes leva uns 30 segundos.
4. O navegador abre em http://localhost:8080.
5. Para desligar, feche as janelas "Similaridade" e "Aplicacao" na barra de tarefas.

Se a planilha mudar, apague `saneamento/saida/` e rode o `iniciar.bat` de novo.

## Como ligar na mão (qualquer sistema)

```
cd saneamento
python -m pip install pandas numpy scikit-learn openpyxl
python exportar.py dados/base_codigos_servico.xlsx --tudo
python api.py dados/base_codigos_servico.xlsx
```

Em outro terminal:

```
cd codigos-api
./mvnw -DskipTests package
java -Dfile.encoding=UTF-8 -jar target/codigos-api-0.2.0.jar --spring.sql.init.data-locations=file:../saneamento/saida/data.sql --spring.sql.init.encoding=UTF-8
```

No Windows, troque `./mvnw` por `mvnw.cmd`. O serviço Python roda na porta 8000 e o site na 8080.

## Como usar

O site tem quatro abas. Em todas as consultas, preencha o campo Comprador com o seu nome. A unidade pode ser escrita do jeito normal (hora, metro quadrado, diária): a tela troca pelo código do SAP.

**Consultar e criar**

1. `ELETRICISTA HORA EXTRA 100%`, unidade H: o resultado é **BLOQUEAR**. O 4096272 já existe, e aparecem outros códigos que são o mesmo serviço com outro nome. Clique em "Usar este".
2. `VIGILANTE NOTURNO 12X36`, unidade H: o resultado é **REVISAR**. É igual ao 4122123, só muda a unidade. Para criar mesmo assim, é preciso justificar.
3. `OPERADOR DE DRONE P/ INSPECAO`, unidade H: o resultado é **LIBERAR**. Clique em "Criar código novo" e o 9000001 é criado na hora.
4. Consulte `OPERADOR DE DRONE P/ INSPECAO` de novo: agora o resultado é **BLOQUEAR**, porque o 9000001 já existe. Escreva uma justificativa e clique em "Criar código novo": o pedido vai para o curador.

**Unidades**

Mostra todas as unidades do SAP, o que pode ser escrito no lugar de cada uma e exemplos de códigos que usam cada uma.

**Catálogo**

Mostra os números da base, os códigos oficiais com os repetidos de cada um e os códigos criados. Dá para buscar por número ou por texto.

**Curador**

Lista os pedidos que esperam decisão. "Aprovar" cria o código. "Recusar" manda usar o código que o sistema sugeriu.

O banco da demonstração fica em memória (H2): ao desligar, os códigos criados somem e a próxima vez começa limpa. Para ver as tabelas, abra http://localhost:8080/h2-console com JDBC URL `jdbc:h2:mem:codigos`, usuário `sa` e sem senha.

## API

| Método | Caminho | O que faz |
| --- | --- | --- |
| POST | `/api/consultas` | consulta uma descrição e devolve resultado e candidatos |
| POST | `/api/solicitacoes/{id}/decisao` | usar um código existente ou criar um novo |
| POST | `/api/solicitacoes/{id}/curadoria` | o curador aprova ou recusa |
| GET | `/api/solicitacoes` | histórico de consultas e decisões |
| GET | `/api/codigos?busca=` | busca no catálogo |
| GET | `/api/codigos/resumo` | totais da base |
| GET | `/api/codigos/padronizados` | códigos oficiais com os repetidos de cada um |
| GET | `/api/codigos/novos` | códigos criados |
| GET | `/api/codigos/{codigo}` | um código e o oficial que vale no lugar dele |
| GET | `/api/unidades` | unidades, sinônimos e exemplos |

Exemplo:

```
curl -X POST localhost:8080/api/consultas -H "Content-Type: application/json" -d "{\"descricao\":\"ELETRICISTA HORA EXTRA 100%\",\"unidade\":\"hora\",\"solicitante\":\"Leonardo\"}"
```

## Banco de dados em produção

O mesmo modelo roda em PostgreSQL. Suba o banco com o `docker-compose.yml` de `codigos-api/` e carregue os dados:

```
cd saneamento
python -m pip install sqlalchemy "psycopg[binary]"
python exportar.py dados/base_codigos_servico.xlsx --db postgresql+psycopg://codigos:codigos@localhost:5432/codigos
```

Depois ligue o Java com `--spring.profiles.active=postgres`. Os detalhes estão em `docs/14-modelagem-banco/`.

## Testes

```
cd saneamento
python test_padronizar.py
```

Os testes que precisam da planilha são pulados quando ela não está em `dados/`.

## Equipe

Grupo 17, Desafio GC 2026: Leonardo Lopes, Thales, Thirza Midiã Matos de Oliveira, Bárbara Teixeira Baumgardt, Lukas Leimann e o time Java.
