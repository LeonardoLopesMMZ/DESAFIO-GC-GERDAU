# Entrega 3 · Evolução, integração e validação

Desafio GC 2026 · Gerdau · Grupo 17 (MOKKA). Esta entrega continua a numeração da Entrega 2. Cada etapa tem uma pasta com o mesmo nome, com um `README.md` (o que foi pedido, o critério de aceite e as evidências) e os arquivos que provam o resultado.

| Etapa | Pasta | Principal evidência | Situação |
| --- | --- | --- | --- |
| Catálogo saneado V2 | [`15-catalogo-saneado-v2`](15-catalogo-saneado-v2/README.md) | 7.937 → 7.054 códigos oficiais, de-para completo e lista do que mudou da V1 para a V2 | pronto |
| Similaridade V2 | [`16-similaridade-v2`](16-similaridade-v2/README.md) | erros de digitação e de fala: V1 acertava 0 de 334, V2 acerta 296 (89%) | pronto, medido |
| Validação da similaridade | [`17-validacao-da-similaridade`](17-validacao-da-similaridade/README.md) | 430 pares conferidos à mão: 0 serviços diferentes tratados como iguais | pronto, medido |
| Aplicação Java V2 | [`18-aplicacao-java-v2`](18-aplicacao-java-v2/README.md) | consulta, decisão, código novo, curador, lote e Excel na mesma aplicação | pronto, testada |
| Regras de negócio | [`19-regras-de-negocio`](19-regras-de-negocio/README.md) | 6 cenários gravados pela API real, um para cada regra | pronto, testada |
| Registro da decisão | [`20-registro-da-decisao`](20-registro-da-decisao/README.md) | cada consulta com o que foi sugerido, o que foi escolhido, quem, quando e por quê | pronto, testada |
| Integração completa | [`21-integracao-completa`](21-integracao-completa/README.md) | teste geral de ponta a ponta: 34 de 34 | pronto, testada |
| Indicadores básicos | [`22-indicadores-basicos`](22-indicadores-basicos/README.md) | 881 possíveis duplicidades, nota da base 77 → 100, duplicados evitados | pronto |
| Pendências finais | [`23-pendencias-finais`](23-pendencias-finais/README.md) | o que foi corrigido e o que depende da Gerdau | pronto |

"Medido" quer dizer que o número sai de um script que qualquer um roda de novo (`python validar.py`). "Testada" quer dizer que a evidência foi gravada pela aplicação ligada (tela → Java → Python → PostgreSQL), sem passo manual.

## Atualizações depois da entrega (v1.4)

| O que mudou | Onde se vê |
| --- | --- |
| **Planilha maior e em mais formatos:** até 100.000 linhas por vez (antes 5.000), em Excel, CSV, texto ou JSON, ou colando a lista na tela | aba Planilha; pasta 18 |
| **Lote mais rápido, com o mesmo resultado:** linhas iguais analisadas uma vez só e repetidos procurados só onde podem existir (mesma unidade e mesmos números). A base inteira passa em uns 7 segundos | pasta 16 |
| **Parecidos de 5 em 5:** a consulta mostra os 5 mais parecidos primeiro e as setas trazem os próximos | aba Consultar |
| **Link seguro para o grupo:** o sistema continua no computador de quem liga e o grupo entra por um link https numa rede privada (Tailscale), sem nada aberto na internet. Pelo link, o Swagger e o console do banco ficam fechados, e a auditoria registra a conta de cada pessoa | pastas 20 e 21; `0 - Apresentação\5 - Compartilhar com o grupo` |
| **Tela inicial:** demonstração animada da consulta, do curador, da planilha e do Raio-X, tour de 7 passos e a entrada no fim | `http://localhost:8080` antes de entrar |
| **Transições:** abas, janelas e a passagem da tela inicial para o sistema com movimento suave | todo o site |
| **Teste geral:** 34 de 34 (entraram link do grupo, lote em JSON e em texto e tela inicial) | pasta 21 |

## Como gerar as evidências de novo

- **Python (pastas 15, 16 e 17):** na pasta `MOKKA\saneamento`, rode `python deduplicar.py`, `python validar.py` e `python test_padronizar.py`. Os arquivos saem em `saida\`.
- **Aplicação (pastas 19 a 22):** o `gerar evidencias de novo.bat`, na pasta da entrega, liga o MOKKA se precisar, roda os 6 cenários pela API e grava os arquivos. Não apaga nada do banco.

A planilha da Gerdau é confidencial: os CSVs desta pasta têm dados reais e **não vão para o GitHub**. No repositório ficam só os READMEs.
