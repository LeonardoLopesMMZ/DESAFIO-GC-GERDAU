# Como abrir o MOKKA sem o .bat

O `MOKKA.bat` (Windows) e o `MOKKA.command` (Mac e Linux) fazem estes passos sozinhos. Abaixo está o caminho à mão, para quem quer entender o que acontece ou está num computador onde o .bat não roda.

O sistema tem três partes:

1. o serviço de similaridade em **Python**, na porta 8000;
2. a aplicação **Java**, na porta 8080, que serve também a tela;
3. o **banco**: H2 na memória, que é o mais simples, ou PostgreSQL, opcional.

## Precisa ter

- **Java 17 ou mais novo** (`java -version`)
- **Python 3.10 a 3.13** (`python --version`)
- Internet só na primeira vez, para baixar as bibliotecas

## 1. Ligar o Python (terminal 1)

Abra o PowerShell na pasta `MOKKA\saneamento`:

```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements.txt
python exportar.py dados\base_codigos_servico.xlsx --tudo
$env:MOKKA_SEGREDO = "troque-por-um-segredo"
python api.py dados\base_codigos_servico.xlsx
```

- Se o PowerShell bloquear o `Activate.ps1`, rode antes `Set-ExecutionPolicy -Scope Process Bypass`.
- Sem a base real, troque `dados\base_codigos_servico.xlsx` por `exemplo\base_exemplo.csv` nos dois comandos.
- O `exportar.py` monta o catálogo que o Java vai carregar (`saida\data.sql`). Rode de novo só se a base mudar.
- O venv e o `pip install` são só da primeira vez. Nas próximas, basta ativar o `.venv`, definir o segredo e rodar o `api.py`.
- Quando aparecer `ok: ... códigos carregados`, confira em http://127.0.0.1:8000/saude. **Deixe esta janela aberta.**

## 2. Ligar o Java (terminal 2)

Abra outro PowerShell na pasta `MOKKA\codigos-api`:

```powershell
.\mvnw.cmd -q -DskipTests package
$env:MOKKA_SEGREDO = "troque-por-um-segredo"
java -jar target\codigos-api-0.4.0.jar --mokka.dados=../saneamento/saida/data.sql
```

- O `mvnw.cmd` compila. É só na primeira vez ou quando o código Java mudar, e leva uns 2 minutos.
- O segredo tem que ser **o mesmo do Python**: o Python só responde a quem manda o segredo.
- Quando aparecer `Started CodigosApplication`, está pronto. **Deixe esta janela aberta.**
- Assim o banco é o **H2**, na memória: tudo começa do zero cada vez que liga.

## 3. Abrir o site

No **Edge** ou no **Chrome**, abra http://localhost:8080 e clique em **Entrar**.

- Use `localhost`: o microfone, a câmera e a chave do Windows só funcionam por esse endereço ou por https.
- O Swagger (API) fica em http://localhost:8080/swagger.

## Opcional: com o PostgreSQL

O PostgreSQL guarda tudo entre um dia e outro. Ele precisa já ter sido preparado uma vez pelo `MOKKA.bat`, que baixa o PostgreSQL e cria o banco e a senha.

1. Ligue o banco, num PowerShell na pasta `MOKKA`:
   ```powershell
   powershell -ExecutionPolicy Bypass -File scripts\postgres.ps1 ligar
   ```
2. No terminal do Java, antes do `java -jar`, defina as variáveis com os valores do arquivo `.ferramentas\pg-env.bat`. A senha está nele, e não deve ser copiada para nenhum outro lugar.
   ```powershell
   $env:DB_URL = "jdbc:postgresql://127.0.0.1:5433/mokka"
   $env:DB_USER = "mokka_app"
   $env:DB_PASSWORD = "(a senha que está no pg-env.bat)"
   ```
3. Ligue o Java com o perfil do PostgreSQL:
   ```powershell
   java -jar target\codigos-api-0.4.0.jar --mokka.dados=../saneamento/saida/data.sql --spring.profiles.active=postgres
   ```

O selo no topo do site mostra qual banco está em uso.

## Opcional: editar a tela sem compilar

Para o Java usar a pasta `front` no lugar da tela que está dentro do jar, acrescente ao `java -jar`:

```
--spring.web.resources.static-locations[0]=file:../front/ --spring.web.resources.static-locations[1]=classpath:/static/
```

Depois de editar a tela, basta salvar e apertar F5 no navegador.

## Mac e Linux

Os passos são os mesmos, com estas trocas:

| Windows | Mac e Linux |
| --- | --- |
| `.\.venv\Scripts\Activate.ps1` | `source .venv/bin/activate` |
| `$env:MOKKA_SEGREDO = "..."` | `export MOKKA_SEGREDO="..."` |
| `.\mvnw.cmd` | `./mvnw` |
| `dados\base...` | `dados/base...` |
| `python` | `python3` |

## Desligar

- Aperte **Ctrl+C** nas duas janelas.
- Se ligou o PostgreSQL: `powershell -ExecutionPolicy Bypass -File scripts\postgres.ps1 parar`.

## Se der errado

| O que aparece | O que fazer |
| --- | --- |
| `Address already in use` (8000 ou 8080) | O MOKKA já está ligado por outra janela ou pelo .bat. Desligue com `MOKKA.bat parar` |
| A tela diz "modo reserva" | O Python não está ligado ou o segredo está diferente nos dois terminais |
| `java` não é reconhecido | Instale o Java 17 (Temurin) e abra o terminal de novo |
| Erro no `pip install` | Use o Python 3.12 e apague a pasta `.venv` |
