# Como testar o MOKKA (passo a passo simples)

São uns 15 minutos. Os resultados abaixo valem para a **base de exemplo** que vem no repositório; com a base real da Gerdau os códigos são outros. Antes de começar, ligue o sistema como explicado no [README](../README.md#rodar-do-zero) e abra http://localhost:8080. Na primeira consulta o sistema pede para **entrar**: escreva o seu nome e clique em Entrar (ou use rosto ou digital, teste 13).

Ligar de novo pelo `MOKKA.bat` (opção 1) zera tudo. Faça isso antes de começar, para os números baterem.

## 1. O código já existe (BLOQUEAR)

1. Aba **Consultar e criar**. Descrição: `ELETRICISTA HORA EXTRA 100%`. Unidade: `hora`.
2. Clique em **Consultar**.
3. **Esperado:**
   - A unidade vira `H`.
   - O aviso fica vermelho: *Já existe: 7000146*.
   - A lista mostra os códigos parecidos com a aderência de cada um.
4. Clique em **Usar este** na primeira linha. **Esperado:** *Pronto: use o código 7000146*.

## 2. É quase igual (REVISAR)

1. Descrição: `VIGILANTE NOTURNO 12X36`. Unidade: `H`.
2. **Esperado:** aviso amarelo, *Parecido com 7001412; muda só: UNIDADE H/SV*.
3. Clique em **Criar código novo** sem escrever justificativa. **Esperado:** a tela pede a justificativa.

## 3. É novo: criar o código (LIBERAR)

1. Descrição: `OPERADOR DE DRONE P/ INSPECAO`. Unidade: `H`.
2. **Esperado:** aviso verde, *Nenhum código igual*.
3. Clique em **Criar código novo**. **Esperado:** *Código 9000001 criado e já está no catálogo*.

## 4. Consultar de novo: o duplicado é barrado

1. Consulte `operador drone inspecao` (escrito diferente de propósito).
2. **Esperado:** *Já existe: 9000001*. Hoje esse duplicado passaria; aqui ele é barrado.
3. Escreva uma justificativa e clique em **Criar código novo**. **Esperado:** o pedido vai para o curador.

## 5. Curador

1. Abra a aba **Curador** (o número 1 aparece ao lado). O pedido do teste 4 está lá.
2. Clique em **Recusar**. **Esperado:** *Recusado: use o 9000001*.

**Pedidos iguais juntos:** consulte `ELETRICISTA HE 100%`, `eletricista hora extra 100%` e `ELETRICISTA - HORA EXTRA 100%`, e em cada um escreva uma justificativa e clique em **Criar código novo**. Na aba **Curador** aparece um cartão só, *3 pedidos iguais*. Clique em **Aprovar os 3**. **Esperado:** *Aprovado: 3 pedidos usam o código 900000X*, um código só para os três.

## 6. Erro de digitação ou de fala

1. Consulte `SOLDA DOR HORA EXTRA 50%`, unidade `H`.
2. **Esperado:** aviso amarelo, *Você quis dizer "SOLDADOR HORA EXTRA 50%"? Assim: Já existe: 7000265*.
3. Clique em **Sim, consultar assim**. **Esperado:** *Já existe: 7000265*.

Outros para testar: `ELETRESISTA HORA EXTRA 100%` e `VIGILANTI NOTURNO 12X36`.

## 7. Busca e criação por voz

Precisa do Edge, do Chrome ou do Safari (no Brave a voz é bloqueada), aberto em **http://localhost:8080** no computador que está rodando o sistema, e com internet: o navegador usa o reconhecimento de voz do Google, da Microsoft ou da Apple. A opção 3 do `MOKKA.bat` abre a página de teste do microfone, com as frases para ler.

1. Clique em **Falar** ao lado da descrição. Na primeira vez, permita o microfone.
2. Fale: *"soldador hora extra cinquenta por cento segunda a sábado, unidade hora"*.
3. **Esperado:**
   - O campo vira `SOLDADOR HORA EXTRA 50% SEG A SAB`, a unidade vira `H` e a consulta roda sozinha: *Já existe: 7000265*.
   - Embaixo do formulário aparece **Entendi**, com cada palavra que o sistema ouviu.
   - Se alguma palavra não existe no catálogo ou o navegador ficou em dúvida, ela ganha borda amarela e uma **sugestão em cima** (por exemplo `SOLDADOR?` em cima de `SOLDA DOR`). Clique na sugestão para trocar; clique na palavra para ver mais opções. A consulta roda de novo sozinha.
4. Fale uma frase longa: *"técnico de segurança do trabalho hora extra cem por cento domingo e feriado, unidade hora"*. **Esperado:** vira `TST HORA EXTRA 100% DOM/FER` e aparece a nota *abreviei para caber nos 40 caracteres*.
5. Fale: *"criar lavagem de piso com máquina, unidade metro quadrado"*. **Esperado:** consulta com `M2` e o aviso *Você pediu para criar: confira e clique em Criar código novo*.

O que a voz entende:

| Fala | Vira |
| --- | --- |
| cinquenta por cento | 50% |
| doze por trinta e seis | 12X36 |
| domingo e feriado | DOM/FER |
| segunda a sábado | SEG A SAB |
| vinte e cinco milímetros | 25 MM |
| para | P/ |
| com | C/ |
| "unidade hora" no fim | preenche o campo Unidade |
| "buscar ..." ou "criar ..." no começo | comando |
| frase com mais de 40 caracteres | abreviada no padrão do SAP: HORA EXTRA vira HE, TÉCNICO DE SEGURANÇA DO TRABALHO vira TST, MANUTENÇÃO vira MANUT |

## 8. Planilha inteira (lote)

1. Aba **Planilha (lote)**, clique em **Baixar modelo de planilha** e salve o `modelo_lote.xlsx`. Ela tem 14 linhas bagunçadas de propósito.
2. Escolha esse arquivo e clique em **Analisar**.
3. **Esperado:** *14 linhas analisadas: 3 já existem, 2 parecidos, 3 novos, 3 repetidos dentro da planilha e 3 com erro*. Pelos botões dá para filtrar cada tipo.
   - Linhas 6 e 7 são o mesmo serviço escrito diferente: a 7 aparece como *Mesmo da linha 6*.
   - Linha 13 está sem unidade, linha 14 sem descrição e linha 15 tem a unidade "caixa", que não existe: as três aparecem como erro, com o motivo.
4. Clique em **Importar no banco** e confirme. A importação fica no nome de quem entrou.
5. **Esperado:**
   - *3 códigos criados, 8 linhas usam código existente e 3 não importadas*.
   - As linhas 6 e 7 ficam com o mesmo código, 9000001.
   - Os códigos antigos 8100002 e 8100003 da planilha entram como **repetidos** apontando para o oficial.
6. Clique em **Baixar Excel organizado**. O arquivo tem as abas:
   - **Tratado:** linha a linha, com o código final.
   - **Pronto para o SAP:** um código por linha, com nome padrão de até 40 caracteres.
   - **Resumo** e **Relatório** (o que foi arrumado na planilha).
   - Uma aba por grupo, quando a planilha tem mais de um grupo.
7. Na aba **Catálogo**, o número de *criados agora* deve ser 5.

**Planilha bagunçada de verdade:** na mesma aba, clique em **Baixar exemplo bagunçado** e analise o `exemplo_baguncado.xlsx`. Ele tem título no topo, uma linha vazia, o cabeçalho repetido no meio, uma linha duplicada, "-" e "n/a" no lugar de vazio, duas abas e o grupo escrito de vários jeitos ("civil", "CIVIL ", "Cívil", "facilites", "Eletro mecânica"). **Esperado:** o quadro **O que eu arrumei antes de analisar** lista cada correção, e os grupos viram Civil, Facilities e Eletromecânica. No Excel exportado aparecem também a aba **Relatório** e uma aba por grupo.

**Colar a lista:** abra **Ou cole a lista aqui**, cole uma descrição por linha (ou colunas separadas por `;`) e clique em **Analisar**, sem escolher arquivo.

Para testar volume, mande uma planilha com até 5000 linhas: com a base real, 5000 linhas são analisadas em uns 15 segundos.

## 9. Swagger (API)

1. Abra http://localhost:8080/swagger.
2. Em **Consulta**, abra **POST /api/consultas**, clique em **Try it out** e envie:
   ```json
   {"descricao": "ELETRICISTA HORA EXTRA 100%", "unidade": "hora", "solicitante": "Avaliador"}
   ```
3. **Esperado:** `"resultado": "BLOQUEAR"` e o código `7000146` em `candidatos`.
4. Em **Lote por Excel**, o **POST /api/lotes/analisar** aceita o arquivo direto pelo Swagger.

## 10. Mensagens de erro

| Faça | Esperado |
| --- | --- |
| Consulte com a unidade `caixa` | *Unidade não conhecida: caixa. Veja a aba Unidades.* |
| No lote, mande um PDF | *Formato .pdf não aceito. Envie .xlsx ou .csv.* |
| No Swagger, mande `{}` em /api/consultas | mensagem dizendo qual campo falta |
| Feche a janela "Similaridade - Python" e consulte | *O serviço de similaridade (Python) está fora do ar...* |
| Abra http://localhost:8080/api/nada | *Endereço não existe. Veja a lista em /swagger.* |

## 11. Acesso por outro computador da mesma rede

1. Ao ligar, o `MOKKA.bat` mostra os endereços, por exemplo `http://192.168.0.10:8080`.
2. Abra esse endereço em outro computador ou no celular, na mesma rede Wi-Fi.
3. Se o Windows perguntar sobre o Firewall, marque **Redes privadas** e clique em **Permitir**.

Pelo outro computador, tudo funciona menos o microfone: o navegador só libera o microfone no próprio computador (localhost).

## 12. Testes automáticos

```
cd saneamento
.venv\Scripts\python test_padronizar.py
```

**Esperado:** uma linha `ok` para cada teste. Os testes que precisam da base real dizem *pulado* quando ela não está em `dados/`.

## 13. Entrar com rosto ou digital

Precisa do Edge ou do Chrome em http://localhost:8080 e de um jeito de confirmar no aparelho: Windows Hello (rosto, digital ou PIN), Touch ID no Mac, ou o celular (o navegador mostra um QR code).

1. Entre pelo nome. Clique no seu nome no canto de cima e em **Cadastrar rosto ou digital**. Confirme no aparelho.
2. **Esperado:** *Pronto! Da próxima vez, entre com rosto ou digital.*
3. Clique em **Sair**. Tente entrar com o mesmo nome. **Esperado:** *Esse nome tem rosto ou digital cadastrado.*
4. Clique em **Entrar com rosto ou digital**. **Esperado:** *Entrada confirmada com rosto ou digital.*

## 14. Ficha do código

Clique em qualquer número de código (na consulta, no catálogo, no curador ou no lote). **Esperado:** abre a ficha com quem criou, se entrou com rosto ou pelo nome, quando, fornecedores, repetidos e a lista de quem usou.

## 15. Anti-ruído e celular

1. Ligue o **Anti-ruído** embaixo da descrição e fale perto de um barulho (ventilador, música baixa). Desligue e repita. Fique com o que acertar mais: depende do microfone.
2. As barrinhas ao lado mostram o volume enquanto você fala. Se ficarem paradas, o sistema avisa para chegar mais perto.
3. Abra o endereço da rede no celular. **Esperado:** abas embaixo, tabelas em cartões e nada cortado na lateral.
