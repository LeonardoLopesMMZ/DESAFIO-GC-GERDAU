# 22 · Indicadores básicos

> **O que pede:** exemplos como quantidade de possíveis duplicidades identificadas, registros agrupados ou casos sem correspondência.
> **Critério de aceite:** pelo menos um ou dois indicadores ajudam a demonstrar os resultados obtidos.

## Na base da Gerdau

| Indicador | Valor |
| --- | --- |
| Códigos na base original | 7.937 (9.795 linhas código × fornecedor) |
| **Possíveis duplicidades identificadas** | **883 códigos (11,1%)** juntados ao oficial no catálogo V2 |
| Duplicidades que dependem de uma resposta da Gerdau | 211 grupos (EVENTUAL, À DISPOSIÇÃO, ADM, H x SV...) |
| Códigos oficiais depois do saneamento | 7.054 |
| Maiores famílias de repetidos | ENCARREGADO HE SEG-SAB e TÉCNICO DE SEGURANÇA HORA NORMAL, 8 códigos cada |
| Repetidos por grupo | Civil 323 de 3.451 (9%) · Facilities 232 de 2.731 (8%) · Eletromecânica 326 de 1.758 (19%) |
| **Nota de saúde da base** | **77 → 100** |

A nota soma cinco critérios: sem código repetido (peso 40), maiúsculas sem espaço sobrando (15), sem "SV" ou traço na frente (15), cabe nos 40 caracteres (15), sem pontuação solta (15).

| Critério | Antes | Depois |
| --- | --- | --- |
| Sem código repetido | 89% | 100% |
| Maiúsculas, sem espaço sobrando | 63% | 100% |
| Sem "SV" ou traço na frente | 58% | 100% |
| Cabe nos 40 caracteres do SAP | 100% | 100% |
| Sem pontuação ou símbolo solto | 58% | 98% |

## Na validação (pastas 16 e 17)

| Indicador | Valor |
| --- | --- |
| Serviços diferentes tratados como iguais | **0 de 256** pares conferidos à mão |
| Mesmo serviço reconhecido como igual | 78 de 93 (84%); outros 14 vão para REVISAR |
| Consultas com erro de escrita ou de fala resolvidas | 296 de 334 (89%); na V1, 0 |
| **Casos sem correspondência** | 15 de 15 saíram LIBERAR, sem sugestão falsa |
| Tempo por consulta | 6 a 8 ms |

## No sistema ligado

O Raio-X da aplicação mostra, ao vivo, o **ganho no serviço**: quanto a lista que o comprador percorre ficou menor (11%), quantos repetidos saíram do caminho, quantos duplicados foram barrados nas consultas e quantas decisões ensinaram o MOKKA. Os números do momento da geração estão em `indicadores-resumo.json` e `indicadores-raio-x.json`, e as regras aprendidas em `aprendizado.json`.

Esses dois arquivos vêm do banco de trabalho do grupo, com as consultas dos nossos testes; por isso os duplicados barrados e as decisões aprendidas não são números de produção. No banco, o catálogo ainda é o da V1 (7.056 oficiais) até alguém zerar o banco, que recarrega o catálogo V2 (7.054).

> No GitHub fica só este README. As evidências citadas acima ficam na pasta da entrega, no computador do grupo; o `gerar evidencias de novo.bat` grava tudo de novo com o MOKKA ligado.
