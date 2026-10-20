# 07 · Similaridade V1

> **O que pede:** primeira lógica capaz de receber uma descrição e retornar códigos semelhantes.
> **Critério de aceite:** a solução retorna candidatos para pelo menos alguns exemplos e o grupo consegue explicar o resultado.

O comprador digita uma descrição e a unidade; em cerca de **16 ms** o sistema devolve até 10 códigos oficiais parecidos e um resultado: **BLOQUEAR** (já existe igual), **REVISAR** (existe quase igual) ou **LIBERAR** (nada igual).

## Como funciona

1. **Padroniza** a descrição digitada com as mesmas regras de `03-padronizacao-textual.md`.
2. **Acha os 60 textos mais parecidos** comparando pedaços de 2 a 4 letras (TF-IDF de caracteres). Pedaços de letras funcionam bem com abreviação e erro de digitação: "MANUT" e "MANUTENCAO" compartilham quase todos os pedaços.
3. **Passa cada um pela régua** de `04-hipotese-similaridade.md` e classifica em DUPLICADO, REVISAR ou DIFERENTE, dizendo o que muda.
4. **Ordena** (`08-ranking-candidatos-v1.md`) e devolve sempre o **código oficial** do grupo (`06-catalogo-saneado-v1.md`), mesmo que o texto parecido seja de um código antigo.
5. O **resultado** vem do primeiro candidato: DUPLICADO → BLOQUEAR, REVISAR → REVISAR, DIFERENTE → LIBERAR.

## Exemplos

| Descrição digitada | Un. | Resultado | Primeiro candidato | Aderência | O que muda | Explicação |
| --- | --- | --- | --- | --- | --- | --- |
| MEC.MANUT.NORM | H | BLOQUEAR | 4083559 MECÂNICO DE MANUTENÇÃO (HORA NORMAL) | 98% | nada | abreviações resolvidas pelo dicionário |
| tec seg trabalho hora extra domingo | H | BLOQUEAR | 4083626 TÉCNICO DE SEGURANÇA (HE DOM/FER) | 85% | nada | mesmo cargo e mesmo tipo de hora |
| VIGILANTE 12X36 NOTURNO | SV | BLOQUEAR | 4122123 VIGILANTE NOTURNO 12X36 | 100% | nada | só a ordem das palavras muda |
| SOLDADOR HE 50% SEG A SAB | H | REVISAR | 4051912 Sv Soldador H Adm HE 50% | 70% | unidade H/SV, ADM | igual, salvo a unidade e a palavra ADM |
| mobilizacao equipe eletricista | SV | REVISAR | 4101469 MOB EQUIPE FIXA-ELETRICISTA | 91% | FIXA | "fixa" pode ou não mudar o serviço |
| SV CURVA SOLDAVEL 25MM | ST | LIBERAR | 4051085 SV CURVA E JOELHO SOLDÁVEL 25 MM | 82% | JOELHO | o existente inclui joelho; é outro item |
| caminhao munck hora produtiva | H | LIBERAR | 4096393 CAMINHÃO CARROCERIA MUNCK - HORA PRODUTI | 75% | CARROCERIA | o existente é caminhão carroceria |
| PINTURA EPOXI PISO | M2 | LIBERAR | 4033516 SV- PINTURA SIMPLES PISO EPOXI | 80% | SIMPLES | o existente é pintura simples |
| cafe da manha ate 20 pessoas | SV | LIBERAR | 4079217 SV LOUÇA COMPLETA PARA CAFÉ - POR PESSOA | 46% | várias palavras | nada parecido de verdade |
| SV-MEC-01-HN | H | LIBERAR | 4071582 SV MECÂNICO HN | 86% | 01 | limitação: o "01" é tratado como número |

O script grava `saida/exemplos_similaridade.csv` com os 5 primeiros candidatos de cada consulta (não versionado, porque contém dados da base da Gerdau).

**Por que confiar no resultado:** cada candidato vem com a lista do que muda. O comprador não precisa acreditar numa nota; ele vê "muda só a unidade" ou "muda o percentual" e decide.

## Limitações conhecidas

- Códigos-sigla como "SV-MEC-01-HN" saem como LIBERAR porque o número "01" não existe no código oficial. Número nunca é ignorado de propósito, porque em medidas (25 mm x 32 mm) ele muda o serviço.
- Abreviação que ainda não está no dicionário não é entendida até alguém acrescentar.
- O índice é montado na memória quando a API sobe (cerca de 6 segundos na base piloto).

**Como reproduzir:** na pasta `saneamento`, rode `python similaridade.py` (os 10 exemplos acima) ou `python similaridade.py "MEC.MANUT.NORM" H` (uma consulta).

## API para a aplicação Java

A mesma busca fica disponível como serviço para a aplicação Java chamar, sem copiar nada à mão:

```
python api.py dados\base_codigos_servico.xlsx
```

| Chamada | Corpo | Resposta |
| --- | --- | --- |
| `GET http://127.0.0.1:8000/saude` | — | `{"status": "ok", "codigos": 7937}` |
| `POST http://127.0.0.1:8000/similares` | `{"descricao": "MEC.MANUT.NORM", "unidade": "H", "limite": 10}` | `resultado`, `motivo` e `candidatos` (código oficial, descrição, nome padrão, unidade, categoria, aderência, classe, o que muda) |

`descricao` é obrigatória (até 200 caracteres), `unidade` tem até 5 caracteres e `limite` vai de 1 a 50. Pedido inválido volta com status 400 e `{"erro": "..."}`.
