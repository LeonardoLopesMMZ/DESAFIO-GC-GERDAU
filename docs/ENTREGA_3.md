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
| Integração completa | [`21-integracao-completa`](21-integracao-completa/README.md) | teste geral de ponta a ponta: 29 de 29 | pronto, testada |
| Indicadores básicos | [`22-indicadores-basicos`](22-indicadores-basicos/README.md) | 881 possíveis duplicidades, nota da base 77 → 100, duplicados evitados | pronto |
| Pendências finais | [`23-pendencias-finais`](23-pendencias-finais/README.md) | o que foi corrigido e o que depende da Gerdau | pronto |

"Medido" quer dizer que o número sai de um script que qualquer um roda de novo (`python validar.py`). "Testada" quer dizer que a evidência foi gravada pela aplicação ligada (tela → Java → Python → PostgreSQL), sem passo manual.

## Como gerar as evidências de novo

- **Python (pastas 15, 16 e 17):** na pasta `MOKKA\saneamento`, rode `python deduplicar.py`, `python validar.py` e `python test_padronizar.py`. Os arquivos saem em `saida\`.
- **Aplicação (pastas 19 a 22):** o `gerar evidencias de novo.bat`, na pasta da entrega, liga o MOKKA se precisar, roda os 6 cenários pela API e grava os arquivos. Não apaga nada do banco.

A planilha da Gerdau é confidencial: os CSVs desta pasta têm dados reais e **não vão para o GitHub**. No repositório ficam só os READMEs.
