# 04 · Hipótese de similaridade

> **O que pede:** critérios que poderão indicar que dois códigos representam serviços semelhantes.
> **Critério de aceite:** o grupo consegue mostrar pelo menos alguns exemplos e explicar o raciocínio.

## A hipótese

**Dois códigos são o mesmo serviço quando, depois da padronização, o que sobra de diferente entre os textos é só abreviação, texto cortado, ordem das palavras ou palavra que não muda o serviço.** Se sobrar um número, um cargo, um tipo de hora ou uma especialidade diferente, são serviços diferentes.

Em vez de dar uma nota de 0 a 100 e escolher um corte, o sistema olha **o que sobra** depois de tirar as palavras em comum. Por isso ele consegue explicar cada decisão em português ("muda só a unidade", "muda o percentual").

## Critérios

| O que sobra de diferente | Resultado | Por quê |
| --- | --- | --- |
| Nada | **DUPLICADO** | mesmo serviço escrito de outro jeito |
| Começo da mesma palavra (ROS / ROSC, MANUT / MANUTENCAO) | **DUPLICADO** | abreviação ou texto cortado pelo SAP |
| Só a unidade (H x SV, M2 x SV) | **REVISAR** | pode ser o mesmo serviço cadastrado errado; uma pessoa confirma |
| Só palavras de contexto (ADM, EVENTUAL, À DISPOSIÇÃO, FIXO) | **REVISAR** | a Gerdau precisa dizer se essas palavras mudam o serviço |
| Qualquer número (medida, %, horário) | **DIFERENTE** | 25 mm não é 32 mm; HE 50% não é HE 100% |
| Tipo de hora (normal, extra, noturno, dom/fer) | **DIFERENTE** | é outro custo |
| Especialidade (mecânica x elétrica) | **DIFERENTE** | é outro profissional |
| Palavra que muda o sentido (COM x SEM) | **DIFERENTE** | é outro serviço |

Na mão de obra, o sistema também lê cargo e tipo de hora e compara a combinação: "SV ENC HE SAB" e "ENCARREGADO ( HORA EXTRA SEG A SAB)" viram ambos ENCARREGADO + hora extra seg-sáb.

## Exemplos e raciocínio

| Texto A | Texto B | Resultado | O que sobra | Raciocínio |
| --- | --- | --- | --- | --- |
| MECÂNICO DE MANUTENÇÃO- HE (dom/fer) · H | MECÂNICO DE MANUT. (HORA EXTRA DOM/FER) · H | DUPLICADO | nada | HE = hora extra, MANUT. = manutenção |
| SV-TÊ 90 E DE REDUÇÃO SOLDÁVEL E COM ROS · ST | SV-TÊ 90 E DE REDUÇÃO SOLDÁVEL E CO ROSC · ST | DUPLICADO | nada | o SAP cortou os dois em pontos diferentes |
| SV-CURVA E JOELHO SOLDÁVEL 25 MM · ST | SV-CURVA E JOELHO SOLDÁVEL 32 MM · ST | DIFERENTE | 25, 32 | medida diferente |
| Sv Caldeireiro H Adm HE 50% · SV | Sv Caldeireiro H Adm HE 100% · SV | DIFERENTE | 50, 100 | percentual diferente |
| MONTADOR LIDER(HE NOT SEG A SAB) · H | MONTADOR LIDER (HE SEG A SAB) · H | DIFERENTE | NOTURNO | um é noturno, o outro não |
| SV TRECHO TRILHO 36M c/linha de vida · M | SV TRECHO TRILHO 36M s/linha de vida · M | DIFERENTE | COM, SEM | com x sem linha de vida |
| SV-VIDRO TEMPERADO 6MM · M2 | SV-VIDRO TEMPERADO DE 6MM · SV | REVISAR | unidade M2/SV | texto igual, unidade diferente |
| SV VIGILANTE 18H00 AS 06H00 12 X 36 SEG. · SV | SV VIGILANTE 06H00 AS 18H00 12X 36 SEG. · SV | DIFERENTE | números na outra ordem | turno da noite x turno do dia |
| SV ARGAMASSA ACII PARA REPAROS EM PAREDE · M2 | SV ARGAMASSA ACIII PARA REPARO EM PAREDE · M2 | DIFERENTE | ACII, ACIII | tipos diferentes de argamassa |
| H - Encarregado Mecânica -HE (dom/fer) · H | ENCARREGADO DE ELÉTRICA (HE DOM/FER) · H | DIFERENTE | MECANICA, ELETRICA | especialidades diferentes |

Os 10 casos estão no teste `test_regua_armadilhas`, que falha se algum mudar.

## Como testamos a hipótese

Conferimos à mão **430 pares** de códigos parecidos (guardados fora do repositório, porque contêm dados da Gerdau), marcando cada um como "mesmo serviço" ou "serviço diferente".

| Medida | Resultado |
| --- | --- |
| Pares diferentes que o sistema juntou por engano | **0** |
| Pares iguais que o sistema juntou | 75 de 93 (80,6%) |

Os 18 pares iguais que não foram juntados dependem de uma decisão da Gerdau (por exemplo, se "ADM" muda o serviço) ou têm texto que pode ter sido cortado. Escolhemos errar para o lado seguro: deixar dois iguais separados é corrigível; juntar dois diferentes estraga pedidos e relatórios.

**Ressalvas.** Com 93 pares, a margem de erro do 80,6% vai de cerca de 71% a 87%. Os pares foram escolhidos entre textos parecidos, não sorteados; o próximo passo é sortear 400 códigos e conferir à mão.

## Decisões que dependem da Gerdau

- Palavras de contexto: EVENTUAL (juntaria 143 códigos), À DISPOSIÇÃO (129), ADM (72), ESP (42).
- Unidade: hora (H) e serviço (SV) são a mesma coisa para itens de hora? (63 códigos).
- Percentual da hora extra: deve ficar no nome do código?
- Hora extra de segunda a sexta e de sábado foram tratadas como o mesmo tipo (HE seg-sáb). Precisa de confirmação.

**Como reproduzir:** na pasta `saneamento`, rode `python test_padronizar.py`.
