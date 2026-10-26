# MOKKA · Códigos de serviço (Desafio GC 2026 · Gerdau)

Protótipo do Grupo 17 para acabar com os códigos de serviço repetidos no SAP. Antes de criar um código, o comprador digita ou **fala** a descrição. O sistema mostra os códigos parecidos que já existem e dá um de três resultados:

- **BLOQUEAR**: já existe um código igual.
- **REVISAR**: existe um quase igual, e para criar mesmo assim é preciso justificar.
- **LIBERAR**: é novo, e o código é criado na hora.

Quem insiste em criar um código bloqueado vai para a fila do **curador**, que recebe os pedidos iguais juntos e decide o grupo de uma vez. Cada pessoa entra com **token**: pelo **rosto na câmera**, com a chave do Windows ou pelo nome. Cada código guarda quem criou e quem usou, com a **foto** de quem entrou pelo rosto. Para a alta demanda, uma **planilha inteira** (até 5000 linhas, mesmo sem tratamento) é analisada de uma vez, entra no banco do jeito certo e volta como um Excel organizado.

| Consulta por voz | Planilha inteira | API (Swagger) |
| --- | --- | --- |
| ![Voz](docs/telas/voz-consulta.png) | ![Lote](docs/telas/lote-analise.png) | ![Swagger](docs/telas/swagger.png) |

## Rodar do zero

Estes são os passos exatos para quem nunca abriu o projeto.

### 1. Instale (uma vez só)

| Programa | Versão | Onde baixar |
| --- | --- | --- |
| Git | qualquer | https://git-scm.com |
| Java (JDK) | 17 ou mais novo | https://adoptium.net (Temurin 17) |
| Python | 3.11, 3.12 ou 3.13 | https://www.python.org/downloads (marque "Add python.exe to PATH") |
| Navegador | Chrome ou Edge | a voz só funciona nesses dois |

Para conferir, abra o terminal (PowerShell no Windows) e rode `git --version`, `java -version` e `python --version`.

### 2. Baixe o projeto

```
git clone https://github.com/LeonardoLopesMMZ/DESAFIO-GC-GERDAU.git
cd DESAFIO-GC-GERDAU
```

Quem não usa Git pode baixar pelo GitHub: botão verde **Code > Download ZIP**, e depois extrair o zip.

### 3. Ligue o sistema

**Windows:** dê dois cliques em **`MOKKA.bat`**. Abre um menu:

| Opção | O que faz |
| --- | --- |
| 1 | Liga o sistema e abre o site no Edge |
| 2 | Testa tudo (mostra OK ou FALHOU em cada item) e abre o teste do microfone |
| 3 | Abre o teste do microfone |
| 4 | Abre o Swagger (API) |
| 5 | Desliga |

Também dá para usar sem menu: `MOKKA.bat ligar`, `MOKKA.bat testar` ou `MOKKA.bat parar`.

Na primeira vez ele faz três coisas, que levam de 3 a 5 minutos e precisam de internet:

- prepara o Python;
- compila o Java;
- monta o catálogo.

Depois disso o sistema liga em uns 30 segundos, também sem internet.

**Mac (Safari) ou Linux:** no Terminal, rode `bash MOKKA.command`. No Mac também dá para dar dois cliques no arquivo; se o Mac bloquear, rode antes `chmod +x MOKKA.command`. Ele faz o mesmo que a opção 1 e abre o Safari; para desligar, feche a janela do Terminal. Precisa de Java 17 e Python 3.11 a 3.13 (`brew install openjdk@17 python@3.12`). Para a voz funcionar no Safari, ative o Ditado em Ajustes do Sistema > Teclado > Ditado.

### 4. Teste

Use a opção **2** do `MOKKA.bat`. Ela liga o sistema se precisar e testa:

- consulta e correção de escrita;
- erros e planilha em lote;
- Excel e Swagger;
- testes do Python;
- microfone e internet.

No fim, abre o teste do microfone (http://localhost:8080/#testar-voz).

Para testar à mão, siga o roteiro [docs/COMO_TESTAR.md](docs/COMO_TESTAR.md): 12 testes curtos, cada um com o resultado esperado.

### Sobre a base de dados

A planilha da Gerdau é confidencial e **não está no repositório**. Por isso o projeto traz uma **base de exemplo fictícia** (`saneamento/exemplo/base_exemplo.csv`), com 337 códigos inventados no mesmo formato e com os mesmos tipos de bagunça: abreviações, maiúsculas e minúsculas, "SV-" na frente, o mesmo serviço com códigos diferentes. Ela é usada automaticamente.

Quem tem a base real coloca o arquivo em `saneamento/dados/base_codigos_servico.xlsx` e liga o `MOKKA.bat` de novo. Ele passa a usar a real; o `.gitignore` impede que ela suba para o GitHub.

## O que o sistema faz

| Aba | O que dá para fazer |
| --- | --- |
| Entrar | Botão no canto de cima. **Rosto**: a câmera reconhece a pessoa e guarda uma foto pequena (uns 4 KB) no banco. **Chave do Windows**: Windows Hello, digital ou PIN (também Touch ID e celular pelo QR code). **Pelo nome**: modo demonstração. Depois de cadastrar rosto ou chave, ninguém mais entra com o seu nome. A entrada gera um token que vale 8 horas, e toda gravação exige o token. |
| Consultar e criar | Digitar ou **falar** a descrição. O botão **Anti-ruído** liga um filtro de ruído na voz (corta zumbido, eco e barulho de fundo); em microfone bom pode piorar, por isso liga e desliga. A fala vira texto em maiúsculas, números falados viram dígitos ("cinquenta por cento" vira 50%) e, se passar de 40 caracteres, é abreviada no padrão do SAP (HORA EXTRA vira HE, TÉCNICO DE SEGURANÇA DO TRABALHO vira TST). Palavras de que o sistema não tem certeza aparecem com uma sugestão em cima, para trocar com um clique. Ver os parecidos com a aderência e o que muda em cada um. Usar um existente ou criar um novo. Se houver erro de digitação ou de fala ("SOLDA DOR"), o sistema sugere a correção ("SOLDADOR") e mostra que o código já existe. |
| Planilha (lote) | Mandar um .xlsx ou .csv bruto, ou colar a lista. Antes de analisar, o sistema arruma a planilha: pula títulos, junta abas, divide texto colado em colunas, tira cabeçalhos repetidos e linhas idênticas e unifica grupos escritos de jeitos diferentes, mostrando um relatório do que fez. Ver linha a linha: já existe, parecido, novo, repetido dentro da própria planilha ou com erro. Trocar a ação de cada linha. Importar tudo numa transação só. Baixar o Excel organizado com as abas Tratado, Pronto para o SAP, Resumo, Relatório e uma aba por grupo. |
| Unidades | Ver como escrever cada unidade. Na consulta dá para escrever "hora" ou "metro quadrado", e a tela troca por H ou M2. |
| Catálogo | Ver os números da base (inclusive consultas feitas e duplicados evitados), os códigos oficiais com os repetidos de cada um e os códigos criados ou importados. Buscar por número ou texto, também por voz. Clicar em qualquer código abre a **ficha**: quem criou, com a foto de quando entrou pelo rosto, como entrou, quando, fornecedores, repetidos e quem usou. |
| Curador | Os pedidos que pedem o mesmo serviço, mesmo escritos diferente, aparecem juntos ("3 pedidos iguais"). **Aprovar os 3** cria um código só para todos; **Recusar os 3** manda todos usarem o existente. Dá para decidir um pedido sozinho também. |

A planilha do lote é lida por nome de coluna ("Descrição", "Unidade", "Grupo", "Código", "Fornecedor"), em qualquer ordem. Ela pode ter também as 5 colunas no formato da Gerdau, ou uma coluna só com as descrições. O modelo pode ser baixado na própria aba.

## Como funciona por dentro

```
Navegador (HTML + JS)  ->  Java / Spring Boot (porta 8080)  ->  Python (porta 8000)
                              |  regras, API REST, Swagger         limpeza, padronização,
                              |  curador, importação               similaridade, Excel
                              v
                        Banco: H2 em memória (demonstração) ou PostgreSQL (produção)
```

- **Python** (`saneamento/`):
  - limpa e padroniza as descrições, com 118 abreviações e o limite de 40 caracteres;
  - acha os repetidos da base e compara textos com TF-IDF de pedaços de 2 a 4 letras, mais uma régua de regras: cargo, tipo de hora, percentual, unidade;
  - corrige palavras erradas comparando com o vocabulário do catálogo;
  - lê e escreve Excel.
- **Java** (`codigos-api/`):
  - recebe as consultas e guarda cada decisão (quem, o que foi sugerido, o que foi escolhido e por quê);
  - controla o curador e faz a importação em lote numa transação só;
  - publica a API com Swagger e devolve erros sempre em português, no formato `{"status", "erro", "caminho", "quando"}`.
- **Banco**: tabelas `base_original` (intocada), `codigo_servico`, `codigo_fornecedor`, `dicionario`, `solicitacao`, `usuario` (nome, leitura do rosto e chave pública) e `retrato` (fotos pequenas do cadastro e de cada entrada pelo rosto), mais a sequência `codigo_novo` (a partir de 9000001). Os detalhes estão em [docs/14-modelagem-banco](docs/14-modelagem-banco/README.md).

## API

A documentação completa e testável fica em **http://localhost:8080/swagger** com o sistema ligado. Para gravar pelo Swagger: chame `POST /api/acesso/entrar` com `{"nome": "Seu nome"}`, copie o `token`, clique em **Authorize** e cole.

### Segurança

- Gravação (consultar, decidir, curador, importar) só com token. Sem token, a resposta é 401.
- O token é aleatório (256 bits), fica só na memória do servidor e vence em 8 horas. **Sair** apaga na hora.
- **Rosto:** o navegador lê o rosto com a biblioteca face-api (roda no próprio computador, sem internet) e transforma em 128 números. O servidor compara com os rostos cadastrados: até 0,5 de distância é a mesma pessoa. A foto vai como JPEG de 128×128, até 20 KB (na prática uns 4 KB), e fica na tabela `retrato`; `GET /api/acesso/retratos/{id}` devolve a imagem, só com token. Cada consulta guarda o `retrato_id` da entrada, então a ficha do código mostra a foto de quem criou e de quem usou.
- O rosto pela câmera comum serve para **identificar** quem fez o quê. Ele não detecta foto impressa; para segurança forte, use junto a chave do Windows.
- A chave do Windows segue o padrão WebAuthn, o mesmo das passkeys: o aparelho guarda a chave privada e só manda uma assinatura; o servidor confere com a chave pública, o desafio de uso único, o site de origem e o contador da chave. A biometria do Windows nunca sai do aparelho.
- Depois que alguém cadastra rosto ou chave, aquele nome não entra mais pelo modo nome.
- Em produção, ligue `MOKKA_SO_CHAVE=true` para não aceitar a entrada pelo nome.
- Câmera e chave só funcionam em `http://localhost` ou em `https`; pelo IP da rede, use a entrada pelo nome.

| Método | Caminho | O que faz |
| --- | --- | --- |
| POST | `/api/acesso/entrar` | entra pelo nome e devolve o token (use em **Authorize** no Swagger) |
| POST | `/api/acesso/rosto/cadastrar`, `/rosto/entrar` | cadastro e entrada pelo rosto (leitura de 128 números e foto JPEG) |
| GET | `/api/acesso/retratos/{id}` | foto guardada no banco (JPEG) |
| POST | `/api/acesso/chave/desafio`, `/chave/cadastrar`, `/chave/entrar` | cadastro e entrada com a chave do Windows (WebAuthn) |
| GET | `/api/acesso/eu` | quem é o dono do token |
| POST | `/api/consultas` | consulta uma descrição; devolve o resultado, os candidatos e a correção sugerida |
| POST | `/api/solicitacoes/{id}/decisao` | usar um código existente ou criar um novo |
| POST | `/api/solicitacoes/{id}/curadoria` | o curador aprova ou recusa |
| GET | `/api/solicitacoes/curador` | pedidos do curador agrupados por serviço |
| POST | `/api/solicitacoes/curador/grupo` | aprova ou recusa um grupo inteiro |
| GET | `/api/solicitacoes` | histórico de consultas e decisões |
| POST | `/api/lotes/analisar` | analisa uma planilha (multipart, campo `arquivo`) |
| POST | `/api/lotes/importar` | grava as linhas analisadas no banco |
| POST | `/api/lotes/exportar` | devolve o Excel organizado |
| GET | `/api/codigos?busca=` | busca no catálogo |
| GET | `/api/codigos/resumo` | totais da base |
| GET | `/api/codigos/padronizados` | códigos oficiais com os repetidos |
| GET | `/api/codigos/novos` | códigos criados ou importados |
| GET | `/api/codigos/{codigo}` | ficha do código: quem criou, fornecedores, oficial, repetidos e quem usou |
| GET | `/api/unidades` | unidades, sinônimos e exemplos |

## Se alguma coisa cair

O sistema não para e não mostra erro de banco:

| O que caiu | O que acontece |
| --- | --- |
| Python (janela "Similaridade") | O Java faz a consulta direto no banco (**modo reserva**): acha os iguais e parecidos pelas palavras e cria o código normalmente. Os códigos criados nesse tempo são passados ao Python sozinhos quando ele voltar. Só a planilha em lote espera o Python. |
| Java ou o banco (janela "Aplicacao") | A tela continua abrindo, porque fica guardada no navegador, e passa para o **modo local**. A consulta usa o catálogo guardado no computador, e "usar este" ou "criar código" ficam numa fila no navegador. Aparece uma faixa amarela com quantos pedidos estão guardados. Quando o sistema volta, a fila é enviada sozinha (se pedir, entre de novo). |

Para o catálogo ficar guardado, basta abrir o site uma vez com o sistema ligado.

## Apresentar sem susto (Teams ou ao vivo)

- **Ligue 5 minutos antes** com o `MOKKA.bat` e deixe a aba aberta. Tudo roda no próprio computador e não depende de internet, menos a voz.
- **Compartilhe a janela do navegador** no Teams, não a tela inteira: as janelas pretas do Python e do Java ficam fora.
- **Voz:** use o Chrome ou o Edge em `http://localhost:8080`. Ela precisa de internet; se cair, digite, que todo o resto continua funcionando.
- **Para começar do zero entre ensaios**, ligue de novo pelo `MOKKA.bat` (opção 1): o banco de demonstração é em memória.
- **Para o grupo acessar junto**, outros computadores na **mesma rede** abrem o endereço que o `MOKKA.bat` mostra ao ligar (`http://192.168.x.x:8080`). Se o Firewall perguntar, permita em "Redes privadas". Por Teams, de casas diferentes, isso não funciona: aí só quem está ligado compartilha a tela.

## Como mexer no código

- **Java:**
  - No IntelliJ IDEA, faça File > Open, escolha a pasta `codigos-api` e espere o Maven carregar.
  - Para rodar, use a classe `CodigosApplication` com o argumento `--spring.sql.init.data-locations=file:../saneamento/saida/data.sql`.
  - O serviço Python precisa estar ligado.
- **Python:** no VS Code, abra a pasta `saneamento` e escolha o interpretador `.venv` que o `MOKKA.bat` criou.
- **Tela:** fica em `codigos-api/src/main/resources/static/`: `index.html` (estrutura), `style.css` (visual), `app.js` (comportamento) e `fala.js` (conversão de fala em texto). Funciona em celular, tablet e PC: no celular as tabelas viram cartões e as abas vão para baixo.

Depois de mudar o Java, apague a pasta `codigos-api/target` para o `MOKKA.bat` compilar de novo.

## Problemas comuns

| Problema | Causa provável | O que fazer |
| --- | --- | --- |
| "Java não encontrado" | JDK não instalado ou fora do PATH | Instale o Temurin 17 e abra o `MOKKA.bat` de novo |
| Falha ao preparar o Python | sem internet ou Python 3.14+ sem pacotes prontos | Instale o Python 3.12 e apague `saneamento/.venv` |
| Aviso "modo reserva" ou faixa amarela "Sem conexão" | o Python ou o Java caiu; o sistema continua funcionando | Ligue de novo pelo `MOKKA.bat`; o que ficou guardado é enviado sozinho |
| O botão Falar está cinza | Firefox, ou página aberta pelo IP | Use o Edge, o Chrome ou o Safari em `http://localhost:8080` |
| "O Brave bloqueia o reconhecimento de voz" | o Brave desliga o serviço de voz do Google | Abra no Edge (o `MOKKA.bat` já abre nele), no Chrome ou no Safari |
| "O reconhecimento de voz precisa de internet" | sem internet | Digite a descrição |
| Outro computador não abre | Firewall do Windows | Permita o Java em "Redes privadas" |
| A tela parece antiga | cache do navegador | Ctrl+F5 |

## Testes automáticos

```
cd saneamento
python test_padronizar.py
```

A documentação de cada etapa está em `saneamento/docs/` (dados) e `docs/` (Java, banco e telas).

## Equipe

Visual da tela baseado no front da Thirza.

Grupo 17, Desafio GC 2026: Leonardo Lopes, Thales, Thirza Midiã Matos de Oliveira, Bárbara Teixeira Baumgardt, Lukas Leimann.
