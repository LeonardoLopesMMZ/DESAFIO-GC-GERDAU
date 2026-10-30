<div align="center">

# MOKKA · Códigos de serviço

**Um código de serviço por serviço. Sem repetido no SAP.**

Grupo 17 · Desafio GC 2026 · Gerdau · Instituto Caldeira

![Java 17](https://img.shields.io/badge/Java-17-ED8B00?logo=openjdk&logoColor=white)
![Spring Boot 3.5](https://img.shields.io/badge/Spring%20Boot-3.5-6DB33F?logo=springboot&logoColor=white)
![Python](https://img.shields.io/badge/Python-3.11%20a%203.13-3776AB?logo=python&logoColor=white)
![PostgreSQL 16](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white)
![Testes](https://img.shields.io/badge/teste%20geral-29%20de%2029-2EA44F)
![Windows, Mac e Linux](https://img.shields.io/badge/roda%20em-Windows%20%C2%B7%20Mac%20%C2%B7%20Linux-555555)

[Rodar do zero](#rodar-do-zero) · [O que o sistema faz](#o-que-o-sistema-faz) · [Como funciona](#como-funciona-por-dentro) · [Segurança](#segurança) · [API](#api) · [Testes](#testes-automáticos) · [Equipe](#equipe)

</div>

---

Protótipo do Grupo 17 para acabar com os códigos de serviço repetidos no SAP. Antes de criar um código, o comprador digita ou **fala** a descrição. O sistema mostra os códigos parecidos que já existem e dá um de três resultados:

- **BLOQUEAR**: já existe um código igual.
- **REVISAR**: existe um quase igual, e para criar mesmo assim é preciso justificar.
- **LIBERAR**: é novo, e o código é criado na hora.

Quem insiste em criar um código bloqueado vai para a fila do **curador**, que recebe os pedidos iguais juntos, com a **sugestão do MOKKA e a certeza dela**, e decide o grupo de uma vez (e pode desfazer). Cada decisão **ensina o MOKKA**: a próxima consulta igual já vem com o código certo. Tudo fica no **PostgreSQL** (com o H2 de reserva) e protegido: nada abre sem entrar, cada pessoa tem um perfil, e cada ação fica registrada. Cada pessoa entra com **token**: pelo **rosto na câmera**, com a chave do Windows ou pelo nome. Cada código guarda quem criou e quem usou, com a **foto** de quem entrou pelo rosto. Para a alta demanda, uma **planilha inteira** (até 5000 linhas, mesmo sem tratamento) é analisada de uma vez, entra no banco do jeito certo e volta como um Excel organizado.

## Destaques

| | |
| --- | --- |
| 🎙️ **Fala ou digita** | A fala vira descrição no padrão do SAP: maiúsculas, números em dígitos e abreviação automática até 40 caracteres. |
| 🔎 **Acha o repetido antes de nascer** | TF-IDF mais uma régua de regras (cargo, tipo de hora, percentual, unidade) e correção de erro de fala ("SOLDA DOR" vira SOLDADOR). |
| 🧠 **Aprende com o curador** | Cada decisão vira regra IGUAL ou DIFERENTE; a próxima consulta já vem com o código certo. Tudo dá para desfazer. |
| 📊 **Planilha inteira de uma vez** | Até 5000 linhas, mesmo bagunçadas: o MOKKA arruma, analisa, importa numa transação só e devolve um Excel organizado. |
| 🩻 **Raio-X do catálogo** | Nota de saúde da base de 0 a 100, antes e depois, e a constelação das famílias de códigos repetidos. |
| 🔐 **Segurança de verdade** | Token em tudo, perfis, entrada pelo rosto ou chave do Windows, fotos criptografadas (LGPD), auditoria e proteção contra força bruta. |
| 🛟 **Não para** | PostgreSQL com H2 de reserva, modo reserva sem o Python e modo local no navegador sem o Java. |

| Consulta por voz | Planilha inteira | API (Swagger) |
| --- | --- | --- |
| ![Voz](docs/telas/voz-consulta.png) | ![Lote](docs/telas/lote-analise.png) | ![Swagger](docs/telas/swagger.png) |

## Rodar do zero

Estes são os passos exatos para quem nunca abriu o projeto.

### 1. Instale (uma vez só)

Só o navegador é obrigatório (e o Git, para baixar pelo terminal). **Se faltar o Java ou o Python, o `MOKKA.bat` (Windows) e o `MOKKA.command` (Mac e Linux) baixam os dois sozinhos na primeira vez**, para a pasta `.ferramentas` do projeto, sem mexer no resto do computador. Quem já tem pode usar os seus:

| Programa | Versão | Onde baixar |
| --- | --- | --- |
| Git | qualquer | https://git-scm.com |
| Java (JDK) | 17 ou mais novo | https://adoptium.net (Temurin 17) |
| Python | 3.11, 3.12 ou 3.13 | https://www.python.org/downloads (marque "Add python.exe to PATH") |
| Navegador | Chrome, Edge, Safari ou Firefox | a voz funciona no Chrome, no Edge e no Safari; no Firefox, digite |

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
| 6 | Troca o banco entre PostgreSQL e H2 (reserva, na memória) e liga de novo |
| 7 | Zera o banco: apaga pedidos, pessoas, fotos, aprendizado e códigos criados e carrega a base de novo |

Também dá para usar sem menu: `MOKKA.bat ligar`, `MOKKA.bat testar`, `MOKKA.bat zerar` ou `MOKKA.bat parar`.

Na primeira vez ele faz quatro coisas, que levam de 3 a 5 minutos e precisam de internet:

- prepara o Python;
- compila o Java;
- baixa o PostgreSQL 16 (uns 300 MB, para a pasta `.ferramentas`, sem instalar nada no Windows) e cria o banco `mokka`;
- monta o catálogo.

Se o código Java mudar, o `MOKKA.bat` percebe e compila de novo sozinho. Se o PostgreSQL não ligar, o sistema liga com o H2 e avisa; o selo no topo da tela mostra qual banco está em uso.

Depois disso o sistema liga em uns 30 segundos, também sem internet.

**Mac (MacBook) ou Linux:** no Terminal, rode `bash MOKKA.command` (no Mac também dá para dar dois cliques no arquivo; se o Mac bloquear, clique com o botão direito > Abrir). **Não precisa instalar nada:** se o computador não tiver Java 17 ou Python 3.10 a 3.13, o `MOKKA.command` baixa os dois sozinho na primeira vez (uns 220 MB, para a pasta `.ferramentas`) e abre o site no navegador padrão. Para desligar, feche a janela do Terminal. O PostgreSQL também vem sozinho no Mac (uns 300 MB); `bash MOKKA.command h2` usa o H2, `bash MOKKA.command postgres` volta para o PostgreSQL e `bash MOKKA.command zerar` recomeça do zero. Para a voz no Safari, ative o Ditado em Ajustes do Sistema > Teclado > Ditado; no Chrome do Mac a voz funciona direto.

### 4. Teste

Use a opção **2** do `MOKKA.bat`. Ela liga o sistema se precisar e testa:

- banco em uso, consulta e correção de escrita;
- segurança: nada abre sem token, perfis, cabeçalhos, injeção, força bruta, Python só com segredo, foto escondida e apagada, bibliotecas sem falha conhecida;
- curador com sugestão, aprendizado, desfazer e palavra ensinada;
- erros e planilha em lote;
- Excel e Swagger;
- testes do Python;
- microfone e internet.

No fim, abre o teste do microfone (http://localhost:8080/#testar-voz).

Para testar à mão, siga o roteiro [docs/COMO_TESTAR.md](docs/COMO_TESTAR.md): 24 testes curtos, cada um com o resultado esperado.

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
| Raio-X | A nota de saúde da base de 0 a 100, antes (planilha da Gerdau) e depois (catálogo do MOKKA), com os critérios; a constelação das famílias de códigos repetidos (clique numa bolha para ver a ficha); os números por grupo; e a economia estimada, com o custo de um código duplicado ajustável. |
| Curador (perfil Curador) | Os pedidos que pedem o mesmo serviço, mesmo escritos diferente, aparecem juntos ("3 pedidos iguais"). **Aprovar os 3** cria um código só para todos; **Recusar os 3** manda todos usarem o existente. Cada grupo vem com a **sugestão do MOKKA** e a certeza (ex.: "Recusar, 95%: está escrito igual ao 7000109"); **Aceitar sugestões** decide de uma vez as que têm 80% ou mais. Toda decisão tem **Desfazer**. Embaixo, **O que o MOKKA aprendeu**: as descrições ligadas a um código (com quantas decisões e quem) e as palavras ensinadas pelos compradores, que só passam a valer quando o curador aceita. |
| Pessoas (perfil Administrador) | Lista de contas com o perfil de cada uma (Comprador, Curador, Administrador), **Desbloquear entradas**, **Zerar o banco** e o **registro de segurança** com tudo o que aconteceu: entradas, tentativas recusadas, códigos criados, decisões, perfis e fotos apagadas. |

A planilha do lote é lida por nome de coluna ("Descrição", "Unidade", "Grupo", "Código", "Fornecedor"), em qualquer ordem. Ela pode ter também as 5 colunas no formato da Gerdau, ou uma coluna só com as descrições. O modelo pode ser baixado na própria aba.

## Como funciona por dentro

```
Navegador (HTML + JS)  ->  Java / Spring Boot (porta 8080)  ->  Python (porta 8000)
                              |  regras, API REST, Swagger         limpeza, padronização,
                              |  curador, importação               similaridade, Excel
                              v
                        Banco: PostgreSQL 16 (127.0.0.1:5433, guarda tudo)
                               ou H2 em memória (reserva, se o PostgreSQL não ligar)
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
- **Banco**: tabelas `base_original` (intocada), `codigo_servico`, `codigo_fornecedor`, `dicionario`, `solicitacao`, `usuario` (nome, perfil, leitura do rosto e chave pública), `retrato` (fotos pequenas e criptografadas do cadastro e de cada entrada pelo rosto), `aprendizado` (descrição ligada a um código, IGUAL ou DIFERENTE, com os votos), `palavra_aprendida` e `auditoria`, mais a sequência `codigo_novo` (a partir de 9000001). No PostgreSQL os dados ficam entre um dia e outro; na primeira vez o Java carrega o catálogo sozinho, e os códigos criados pelo MOKKA são passados ao Python a cada vez que liga.
- **Aprendizado**: cada "usar este" e cada recusa do curador vota que aquela descrição (palavras em qualquer ordem, mesma unidade) é IGUAL ao código; cada aprovação do curador vota que é DIFERENTE. Na próxima consulta, IGUAL vira BLOQUEAR com o código certo em primeiro ("O MOKKA aprendeu com 2 decisões"), e DIFERENTE tira o bloqueio. Desfazer tira o voto. Os detalhes estão em [docs/14-modelagem-banco](docs/14-modelagem-banco/README.md).

## API

A documentação completa e testável fica em **http://localhost:8080/swagger** com o sistema ligado. Para gravar pelo Swagger: chame `POST /api/acesso/entrar` com `{"nome": "Seu nome"}`, copie o `token`, clique em **Authorize** e cole.

### Segurança

- **Nada abre sem token**: catálogo, unidades, histórico, Raio-X, planilha e fotos respondem 401 para quem não entrou. Só a entrada e `/api/sistema` (qual banco está ligado) são abertos. A tela pede para entrar antes de mostrar qualquer dado.
- **Perfis**: Comprador (consulta, pede e ensina palavras), Curador (também decide, desfaz e aceita palavras) e Administrador (também cuida das pessoas, desbloqueia e zera o banco). Os administradores da equipe vêm da variável `MOKKA_ADMINS`; quem entra só pelo nome fica no máximo como Curador, porque administrador precisa de **rosto ou chave do Windows**.
- **Força bruta**: 8 entradas erradas do mesmo endereço em 5 minutos bloqueiam a entrada (429) até o tempo passar ou um administrador desbloquear; mais de 600 pedidos por minuto também são barrados.
- **Cabeçalhos**: Content-Security-Policy (só scripts do próprio site, nada de iframe), nosniff, sem referrer, câmera e microfone só no próprio site, e a API manda o navegador não guardar respostas. O Swagger e o console do H2 só abrem no computador que liga o MOKKA.
- **Python fechado**: ele só ouve em 127.0.0.1 e só responde a quem manda o segredo que o `MOKKA.bat` sorteia a cada vez que liga (`X-Mokka-Segredo`). Não aceita chamadas de outros sites.
- **PostgreSQL fechado**: só ouve em 127.0.0.1, senha forte sorteada na primeira vez (scram-sha-256), e o MOKKA usa o usuário `mokka_app`, que não é superusuário, não cria bancos nem usuários e só enxerga o banco `mokka`.
- **Fotos (LGPD)**: guardadas criptografadas (AES-256-GCM, chave em `.ferramentas/chave-fotos.key`); cada pessoa só vê as próprias fotos (o curador vê as de todos); **Apagar meu rosto e minhas fotos** na conta tira tudo do banco.
- **Auditoria**: tudo fica na tabela `auditoria` e na aba Pessoas, com quem, quando e de qual IP.
- **Injeção**: o banco só recebe consultas com parâmetros; o teste automático manda ataques de SQL, script e caminho e confere que nada quebra.
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
| DELETE | `/api/acesso/rosto` | apaga a leitura do rosto e todas as fotos da pessoa (LGPD) |
| GET | `/api/sistema` | qual banco está ligado (aberto) |
| POST | `/api/consultas` | consulta uma descrição; devolve o resultado, os candidatos e a correção sugerida |
| POST | `/api/solicitacoes/{id}/decisao` | usar um código existente ou criar um novo |
| POST | `/api/solicitacoes/{id}/curadoria` | o curador aprova ou recusa |
| GET | `/api/solicitacoes/curador` | pedidos do curador agrupados por serviço |
| POST | `/api/solicitacoes/curador/grupo` | aprova ou recusa um grupo inteiro |
| POST | `/api/solicitacoes/curador/desfazer` | desfaz a decisão do curador e o que o MOKKA aprendeu com ela |
| GET | `/api/aprendizado` | o que o MOKKA aprendeu: regras IGUAL e DIFERENTE e palavras ensinadas |
| POST | `/api/aprendizado/palavras` | ensina uma palavra (vale depois que o curador aceitar) |
| POST | `/api/aprendizado/palavras/{termo}/decisao` | curador aceita ou recusa a palavra |
| DELETE | `/api/aprendizado/regras/{id}` | curador manda esquecer uma regra |
| GET | `/api/admin/pessoas`, POST `/api/admin/pessoas/{id}/perfil` | pessoas e perfis (administrador) |
| GET | `/api/admin/auditoria` | registro de segurança (administrador) |
| POST | `/api/admin/desbloquear`, `/api/admin/zerar` | desbloqueia entradas; zera o banco (administrador) |
| GET | `/api/solicitacoes` | histórico de consultas e decisões |
| POST | `/api/lotes/analisar` | analisa uma planilha (multipart, campo `arquivo`) |
| POST | `/api/lotes/importar` | grava as linhas analisadas no banco |
| POST | `/api/lotes/exportar` | devolve o Excel organizado |
| GET | `/api/codigos?busca=` | busca no catálogo |
| GET | `/api/codigos/resumo` | totais da base |
| GET | `/api/raio-x` | nota de saúde antes e depois, critérios, famílias de duplicados e números por grupo |
| GET | `/api/codigos/leve` | catálogo leve que a tela guarda para funcionar sem conexão |
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
- **Para começar do zero entre ensaios**, use a opção 7 do `MOKKA.bat` (zerar o banco). No PostgreSQL, o que foi feito no ensaio continua lá até zerar.
- **Entre com o rosto** quem vai mostrar o curador e a aba Pessoas: pelo nome, o perfil fica no máximo Curador.
- **Para o grupo acessar junto**, outros computadores na **mesma rede** abrem o endereço que o `MOKKA.bat` mostra ao ligar (`http://192.168.x.x:8080`). Se o Firewall perguntar, permita em "Redes privadas". Por Teams, de casas diferentes, isso não funciona: aí só quem está ligado compartilha a tela.

## Como mexer no código

- **Java:**
  - No IntelliJ IDEA, faça File > Open, escolha a pasta `codigos-api` e espere o Maven carregar.
  - Para rodar, use a classe `CodigosApplication` com o argumento `--mokka.dados=../saneamento/saida/data.sql` (H2). Para o PostgreSQL, ligue antes pelo `MOKKA.bat` e use também `--spring.profiles.active=postgres` com as variáveis de `.ferramentas/pg-env.bat`.
  - O serviço Python precisa estar ligado.
- **Python:** no VS Code, abra a pasta `saneamento` e escolha o interpretador `.venv` que o `MOKKA.bat` criou.
- **Tela:** fica em `codigos-api/src/main/resources/static/`: `index.html` (estrutura), `style.css` (visual), `app.js` (comportamento) e `fala.js` (conversão de fala em texto). Funciona em celular, tablet e PC: no celular as tabelas viram cartões e as abas vão para baixo.

Depois de mudar o Java, o `MOKKA.bat` compila de novo sozinho na próxima vez que ligar.

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
| Selo "H2 · reserva" no topo | o PostgreSQL não ligou | Veja `.ferramentas/postgres.log`; no Windows, falta às vezes o Visual C++ (instale o "Microsoft Visual C++ Redistributable") |
| "Muitas tentativas de entrar" | 8 entradas erradas em 5 minutos | Espere 5 minutos ou peça a um administrador para desbloquear na aba Pessoas |
| Não aparece a aba Curador | perfil Comprador | Um administrador muda o perfil na aba Pessoas |

## Testes automáticos

O teste geral liga o sistema e confere 29 itens de ponta a ponta (consulta, curador, aprendizado, lote, Excel, modo reserva, segurança e o Python), mostrando OK ou FALHOU em cada um:

```
MOKKA.bat testar
```

Só os testes do Python:

```
cd saneamento
python test_padronizar.py
```

A documentação de cada etapa está em `saneamento/docs/` (dados) e `docs/` (Java, banco e telas).

## Equipe

Grupo 17, Desafio GC 2026: Leonardo Lopes, Thales, Thirza Midiã Matos de Oliveira, Bárbara Teixeira Baumgardt, Lukas Leimann.

Visual da tela baseado no front da Thirza.

## Agradecimentos

Ao Richard e ao Dori, pelas boas instruções e por fortalecerem as nossas ideias ao longo do desafio. Obrigado!
