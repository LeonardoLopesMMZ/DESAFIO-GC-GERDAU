# 26 · Catálogo final

> **O que pede:** versão utilizada na solução, com os tratamentos e padronizações realizadas.
> **Critério de aceite:** o catálogo final está disponível e mantém relação com os dados originais.

O catálogo final é o **catálogo saneado V2** (pasta 15 da Entrega 3): **7.054 códigos oficiais** a partir dos 7.937 originais.

## Arquivos

| Arquivo | O que é |
| --- | --- |
| `base_codigos_servico_catalogo.csv` | um código oficial por serviço: descrição, nome padrão, categoria, unidade, grupo e quantos códigos ele substitui |
| `base_codigos_servico_depara.csv` | as 7.937 linhas: código original → código oficial, com o motivo |
| `codigo_servico.csv` | o catálogo como fica no banco: descrição original, texto limpo, nome padrão até 40 caracteres, status (ATIVO ou BLOQUEADO) e o oficial |
| `dicionario.csv` | os 136 termos de abreviação usados na padronização |

## Relação com os dados originais

- Cada código original continua no catálogo; o repetido fica **BLOQUEADO** e aponta para o oficial (`codigo_canonico`), com o motivo.
- A planilha original fica intacta na tabela `base_original`, linha por linha.
- O catálogo é montado de novo a partir da planilha toda vez que o MOKKA liga: não existe passo manual entre a base da Gerdau e o sistema.

A planilha da Gerdau é confidencial: estes arquivos ficam só nesta pasta e não vão para o GitHub.

> No GitHub fica só este README. Os arquivos citados acima têm dados reais da Gerdau e ficam na pasta da entrega, no computador do grupo; para gerar de novo, siga o "como reproduzir".
