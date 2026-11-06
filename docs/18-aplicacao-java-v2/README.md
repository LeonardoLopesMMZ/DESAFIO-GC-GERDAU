# 18 · Aplicação Java V2

> **O que pede:** fluxo completo de consulta e solicitação de novo código.
> **Critério de aceite:** a aplicação executa o fluxo principal sem depender de processos manuais externos.

## O fluxo, do começo ao fim

1. **Entrar.** O comprador entra pelo rosto (câmera), pela chave do Windows ou pelo nome. Recebe um token que vale 8 horas; sem ele, nada abre.
2. **Consultar.** Digita ou fala a descrição e a unidade. Se passar de 40 caracteres, a tela abrevia no padrão do SAP.
3. **Ver o resultado.** BLOQUEAR (já existe), REVISAR (existe quase igual, com o que muda) ou LIBERAR (nada igual). Se houver erro de escrita: "Você quis dizer...?". Os parecidos aparecem de 5 em 5, com setas para ver os próximos.
4. **Decidir.** "Usar este" grava o código escolhido. "Criar código" cria na hora (LIBERAR), pede justificativa (REVISAR) ou manda para o curador (BLOQUEAR).
5. **Curador.** Recebe os pedidos iguais juntos, com a sugestão do MOKKA e a certeza, aprova ou recusa o grupo e pode desfazer.
6. **Aprender.** Cada decisão vira regra; a próxima consulta parecida já vem com o código certo.
7. **Alta demanda.** Uma planilha inteira (até 100.000 linhas, em Excel, CSV, texto ou JSON, mesmo bagunçada) é analisada, importada numa transação só e devolvida como Excel organizado.

## Sem processo manual externo

- Um arquivo liga tudo: `MOKKA.bat` (Windows) ou `MOKKA.command` (Mac e Linux). Ele prepara o Python, compila o Java se o código mudou, liga o PostgreSQL, monta o catálogo a partir da base e abre o site.
- Nada de copiar planilha, rodar script à parte ou mexer no banco: o código criado na tela já entra no banco e na busca.
- Se o Python cair, o Java responde sozinho pelo banco (**modo reserva**). Se o Java cair, a tela continua com o catálogo guardado no navegador e envia os pedidos quando o sistema volta (**modo local**).

## O que tem na aplicação

| Aba | Para quê |
| --- | --- |
| Tela inicial | o que o MOKKA faz, com demonstrações animadas e um tour de 7 passos; a entrada fica no fim |
| Consultar e criar | consulta por texto ou voz, resultado, candidatos, decisão |
| Planilha (lote) | análise de uma planilha inteira, importação e Excel organizado |
| Unidades | como escrever cada unidade |
| Catálogo | números da base, oficiais com seus repetidos, ficha de cada código (quem criou, quem usou, com a foto) |
| Raio-X | nota de saúde da base antes e depois, constelação dos duplicados, ganho no serviço |
| Curador | pedidos agrupados, sugestão com certeza, aprovar ou recusar em grupo, desfazer, o que o MOKKA aprendeu |
| Pessoas | perfis, desbloqueio e registro de segurança (administrador) |

## Para o grupo testar de casa

O `5 - Compartilhar com o grupo` (pasta `0 - Apresentação`) liga um link https numa rede privada do Tailscale: só quem foi convidado abre, nada fica exposto na internet e câmera, microfone e chave funcionam. Pelo link, o MOKKA sabe que a pessoa não está no computador que liga e fecha o Swagger, o console do banco e a entrada do teste automático.

## Tecnologia

Java 17 + Spring Boot 3.5, PostgreSQL 16 (com H2 de reserva), Python 3 para a similaridade, API REST com Swagger em http://localhost:8080/swagger. A tela é HTML, CSS e JavaScript servidos pelo próprio Spring Boot.

## Evidências

- O teste geral (`MOKKA.bat testar`) passa por todo o fluxo pela API e dá **34 de 34**: `21 - Integração completa\testar.log`.
- Os 6 cenários da pasta 19 foram gravados pela aplicação ligada, com o código novo 9000003 criado na hora e encontrado na consulta seguinte.
- As telas estão no repositório em `docs/telas/`; a demonstração ao vivo segue o roteiro da pasta `0 - Apresentação`.
