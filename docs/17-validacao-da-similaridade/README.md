# 17 · Validação da similaridade

> **O que pede:** análise de casos corretos, ambíguos e incorretos.
> **Critério de aceite:** o grupo reconhece limitações e não trata todo texto parecido automaticamente como serviço equivalente.

## Como validamos

1. **430 pares de códigos conferidos à mão** (os mesmos da Entrega 2): para cada par, uma pessoa marcou "mesmo serviço", "serviço diferente" ou "ambíguo, depende da Gerdau". Depois comparamos com o que o MOKKA decide.
2. **334 consultas com erro de digitação ou de fala** (pasta 16).
3. **15 serviços que não existem na base**, para ver se o MOKKA inventa parecido onde não há.

## Resultado nos 430 pares

| Conferido à mão | MOKKA: iguais (BLOQUEAR) | MOKKA: quase iguais (REVISAR) | MOKKA: diferentes (LIBERAR) | Total |
| --- | --- | --- | --- | --- |
| mesmo serviço | **78** | 14 | 1 | 93 |
| serviço diferente | **0** | 1 | **255** | 256 |
| ambíguo | 4 | **50** | 27 | 81 |

### Corretos

- **Nenhum serviço diferente foi tratado como igual (0 de 256).** É o erro mais caro: juntaria dois serviços e o comprador usaria o código errado.
- **84% dos mesmos serviços foram reconhecidos como iguais** (78 de 93). Outros 14 foram para REVISAR: o comprador vê o código e o que muda (por exemplo, "muda só a unidade H/SV") e decide.

### Texto parecido não é serviço igual

Entre os 256 pares de serviços diferentes, **145 têm 80% ou mais de texto em comum**. Uma busca só por texto juntaria todos; o MOKKA juntou **0**. Exemplos:

| Código A | Código B | Texto em comum | Por que o MOKKA separa |
| --- | --- | --- | --- |
| SV-REGISTRO GAVETA BRUTO 1 1/2"-1502 B | SV-REGISTRO GAVETA BRUTO 1/2"-1502 B | 100% | medida diferente |
| KM RODADO 08:00-20:00 SEG-SEG -ISS | KM RODADO 20:00-08:00 SEG-SEG -ISS | 100% | turno diferente |
| SV VIGILANTE 18H00 AS 06H00 12 X 36 | SV VIGILANTE 06H00 AS 18H00 12X 36 | 100% | noite e dia |
| SV CONDULETE 1 1/2" SEM ROSCA | SV CONDULETE 2 1/2" SEM ROSCA | 98% | bitola |
| SV - MARTELETEIRO - HORA NOTURNA RESID | SV - MARTELETEIRO HE NOTURNA RESIDUAL | 97% | hora extra x hora normal |
| SV ENC HE NOT DISP | ENCARREGADO - HORA NOTURNA À DISPOSIÇÃO | 97% | hora extra x hora normal |

A régua não deixa passar número, medida, percentual, turno, tipo de hora nem cargo diferentes, por mais parecido que o texto seja.

### Ambíguos

Dos 81 pares que nem a pessoa soube decidir, **50 foram para REVISAR**: o MOKKA não decide sozinho, mostra a diferença ("FIXO x EVENTUAL", "à disposição", "ADM") e deixa o comprador e o curador escolherem. Os 4 que a régua viu como iguais são textos idênticos cortados no limite de 40 caracteres do SAP (por exemplo, "SV - FURAÇÃO EM CONCRETO COM COROA DIAM"); por isso o **catálogo os mantém separados** (motivo "texto pode ter sido cortado", pasta 15).

### Incorretos

| Caso | O que aconteceu | Por quê |
| --- | --- | --- |
| "Sv Mecânico H Adm HE 100%" x "Mecânico Manut. Hr. Extras 100%" (iguais, conferido à mão) | MOKKA disse diferentes | ADM e MANUTENÇÃO contam como especialidades diferentes |
| "H - Coord. Contrato" x "COORDENADOR ADMINISTRATIVO" (diferentes) | MOKKA mandou para REVISAR, não juntou | as palavras que mudam são "suaves" |
| 37 das 334 consultas com erro | sem sugestão | a palavra errada virou outra palavra que existe ("CONC RETO"), ou o erro caiu numa palavra curta |

## Serviços que não existem na base

Os 15 saíram **LIBERAR**, sem nenhuma sugestão falsa: operador de drone, café da manhã para 20 pessoas, banheiro químico, tradutor juramentado, soldador HE 65% (percentual que não existe), pintor HE 150%, curva soldável 33 mm (bitola que não existe) e outros. O mais parecido aparece, com o que muda, mas o MOKKA deixa criar.

## Limitações que reconhecemos

- **Especialidade escrita de jeitos diferentes:** ADM, MANUTENÇÃO e ESPECIALIZADO às vezes são o mesmo serviço e às vezes não. Hoje o MOKKA manda para REVISAR ou separa; quem resolve é a resposta da Gerdau (pasta 23).
- **Texto cortado em 40 caracteres:** dois códigos com o mesmo início podem ser serviços diferentes; ficam separados por segurança (247 códigos).
- **Número sempre conta:** em códigos-sigla como "SV-MEC-01-HN", o "01" impede juntar. Ignorar números quebraria as medidas (25 mm x 32 mm).
- **Correção não faz milagre:** se a palavra errada vira outra palavra que existe, ou o erro está numa palavra de 3 letras, não há sugestão.
- **Os 430 pares foram rotulados por nós**, não pela Gerdau: são o nosso melhor entendimento, e as respostas da Gerdau podem mudar alguns.

## Arquivos

| Arquivo | O que é |
| --- | --- |
| `validacao_pares.csv` | os 430 pares: descrições, rótulo, texto em comum, decisão do MOKKA e o que muda |
| `validacao_erros_de_escrita.csv` | as 334 consultas com erro e o resultado da V1 e da V2 |
| `validacao_sem_correspondencia.csv` | os 15 serviços que não existem, com o mais parecido e o que muda |

**Como reproduzir:** na pasta `MOKKA\saneamento`, com `dados\pares_rotulados.csv` no lugar, rode `python validar.py`.

> No GitHub fica só este README. Os arquivos citados acima têm dados reais da Gerdau e ficam na pasta da entrega, no computador do grupo; para gerar de novo, siga o "como reproduzir".
