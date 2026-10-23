# Como testar o MOKKA (passo a passo simples)

São uns 15 minutos. Os resultados abaixo valem para a **base de exemplo** que vem no repositório; com a base real da Gerdau os códigos são outros. Antes de começar, ligue o sistema como explicado no [README](../README.md#rodar-do-zero) e abra http://localhost:8080. Em todos os testes, preencha **Comprador** com o seu nome.

Rodar o `iniciar.bat` de novo zera tudo. Faça isso antes de começar, para os números baterem.

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

1. Abra a aba **Curador (1)**. O pedido do teste 4 está lá.
2. Clique em **Recusar**. **Esperado:** *o comprador deve usar o código 9000001*.

## 6. Erro de digitação ou de fala

1. Consulte `SOLDA DOR HORA EXTRA 50%`, unidade `H`.
2. **Esperado:** aviso amarelo, *Você quis dizer "SOLDADOR HORA EXTRA 50%"? Assim: Já existe: 7000265*.
3. Clique em **Sim, consultar assim**. **Esperado:** *Já existe: 7000265*.

Outros para testar: `ELETRESISTA HORA EXTRA 100%` e `VIGILANTI NOTURNO 12X36`.

## 7. Busca e criação por voz

Precisa do Chrome ou do Edge (no Brave a voz é bloqueada), aberto em **http://localhost:8080** no computador que está rodando o sistema, e com internet: o navegador usa o reconhecimento de voz do Google ou da Microsoft.

1. Clique em **Falar** ao lado da descrição. Na primeira vez, permita o microfone.
2. Fale: *"soldador hora extra cinquenta por cento segunda a sábado, unidade hora"*.
3. **Esperado:**
   - O campo vira `SOLDADOR HORA EXTRA 50% SEG A SAB`, a unidade vira `H` e a consulta roda sozinha: *Já existe: 7000265*.
   - Se o navegador ouviu outras versões, elas aparecem embaixo em "Não era isso? Ouvi também" e dá para clicar nelas.
4. Fale: *"criar lavagem de piso com máquina, unidade metro quadrado"*. **Esperado:** consulta com `M2` e o aviso *Você pediu para criar: confira e clique em Criar código novo*.

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

## 8. Planilha inteira (lote)

1. Aba **Planilha (lote)**, clique em **Baixar modelo de planilha** e salve o `modelo_lote.xlsx`. Ela tem 14 linhas bagunçadas de propósito.
2. Escolha esse arquivo e clique em **Analisar**.
3. **Esperado:** *14 linhas analisadas: 3 já existem, 2 parecidos, 3 novos, 3 repetidos dentro da planilha e 3 com erro*. Pelos botões dá para filtrar cada tipo.
   - Linhas 6 e 7 são o mesmo serviço escrito diferente: a 7 aparece como *Mesmo da linha 6*.
   - Linha 13 está sem unidade, linha 14 sem descrição e linha 15 tem a unidade "caixa", que não existe: as três aparecem como erro, com o motivo.
4. Preencha **Quem está importando** e clique em **Importar no banco**.
5. **Esperado:**
   - *3 códigos criados, 8 linhas usam código existente e 3 não importadas*.
   - As linhas 6 e 7 ficam com o mesmo código, 9000001.
   - Os códigos antigos 8100002 e 8100003 da planilha entram como **repetidos** apontando para o oficial.
6. Clique em **Baixar Excel organizado**. O arquivo tem três abas:
   - **Tratado:** linha a linha, com o código final.
   - **Pronto para o SAP:** um código por linha, com nome padrão de até 40 caracteres.
   - **Resumo**.
7. Na aba **Catálogo**, o número de *criados ou importados agora* deve ser 5.

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

1. Ao terminar, o `iniciar.bat` mostra os endereços, por exemplo `http://192.168.0.10:8080`.
2. Abra esse endereço em outro computador ou no celular, na mesma rede Wi-Fi.
3. Se o Windows perguntar sobre o Firewall, marque **Redes privadas** e clique em **Permitir**.

Pelo outro computador, tudo funciona menos o microfone: o navegador só libera o microfone no próprio computador (localhost).

## 12. Testes automáticos

```
cd saneamento
.venv\Scripts\python test_padronizar.py
```

**Esperado:** uma linha `ok` para cada teste. Os testes que precisam da base real dizem *pulado* quando ela não está em `dados/`.
