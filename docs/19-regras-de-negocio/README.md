# 19 · Regras de negócio

> **O que pede:** comportamento para casos com alta similaridade, resultados ambíguos ou ausência de código compatível.
> **Critério de aceite:** o sistema possui comportamento definido para os principais cenários.

## As regras

| Cenário | Quando acontece | O que o MOKKA faz | O que o comprador pode fazer | Evidência |
| --- | --- | --- | --- | --- |
| **Alta similaridade** | existe código igual: mesmo cargo, tipo de hora, números e unidade, ou o curador já decidiu que é o mesmo | **BLOQUEAR** e mostra o código | usar o existente (1 clique) | `cenario-1-...json` |
| **Ambíguo** | existe um quase igual: muda só uma palavra "suave" (FIXO, EVENTUAL, ADM, À DISPOSIÇÃO) ou a unidade | **REVISAR** e diz exatamente o que muda | usar o existente ou criar com **justificativa obrigatória** | `cenario-2-...json`: criar sem justificativa foi recusado (400) |
| **Sem código compatível** | nada igual nem quase igual | **LIBERAR** | criar: o código sai na hora, a partir de 9000001, e já entra na busca | `cenario-3-...json`: criou 9000003 e a mesma consulta, logo depois, deu BLOQUEAR |
| **Erro de escrita ou de fala** | uma palavra não existe na base, mas uma parecida existe | **"Você quis dizer...?"** com o código que existe | aceitar a correção com 1 clique | `cenario-4-...json`: SOLDA DOR → SOLDADOR |
| **Insistir em criar um repetido** | BLOQUEAR e o comprador quer criar mesmo assim | pede justificativa e manda para o **curador** | esperar o curador | `cenario-5-...json`: AGUARDANDO_CURADOR |
| **Curador decide** | o curador aprova ou recusa | recusa = usa o existente e o MOKKA aprende IGUAL; aprova = cria e aprende DIFERENTE | — | `cenario-6-...json`: "O MOKKA aprendeu com 13 decisões" |

## Regras de apoio

| Situação | Comportamento |
| --- | --- |
| Pedidos iguais escritos diferente | o curador recebe juntos ("3 pedidos iguais") e decide o grupo de uma vez |
| Certeza alta | o curador pode aceitar de uma vez todas as sugestões com 80% ou mais |
| Decisão errada | toda decisão do curador tem **Desfazer**, que também tira o que o MOKKA aprendeu |
| Palavra nova ensinada | o comprador sugere ("PEDRERO → PEDREIRO"); só vale depois que o curador aceita |
| Unidade desconhecida | erro em português: "Unidade não conhecida: caixa. Veja a aba Unidades." |
| Mais de 40 caracteres | a tela abrevia no padrão do SAP e mostra o antes e o depois |
| Planilha com linhas repetidas | a linha repetida aparece como "repetido na planilha", apontando a primeira |
| Python fora | modo reserva: o Java consulta pelo banco |
| Java ou banco fora | modo local: a tela guarda os pedidos e envia quando voltar |
| Perfis | comprador consulta e pede; curador decide; administrador cuida das pessoas e do banco |

## Por que essas regras

- **O erro mais caro é juntar dois serviços diferentes**, então o MOKKA só bloqueia quando tudo que muda o serviço é igual. Na dúvida, ele mostra a diferença e deixa a pessoa decidir (REVISAR).
- **Ninguém fica travado:** até no BLOQUEAR dá para pedir a criação, com justificativa e curador.
- **Cada decisão conta:** o que o curador decide uma vez não precisa ser decidido de novo.

Os arquivos `cenario-*.json` têm o pedido, a resposta inteira da API e a decisão gravada no banco. Foram gerados pelo `gerar evidencias de novo.bat`, com o MOKKA ligado no PostgreSQL.

> No GitHub fica só este README. As evidências citadas acima ficam na pasta da entrega, no computador do grupo; o `gerar evidencias de novo.bat` grava tudo de novo com o MOKKA ligado.
