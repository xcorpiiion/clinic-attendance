# Decisões de arquitetura

Cada arquivo registra uma decisão: o contexto em que foi tomada, o que se escolheu,
o que foi descartado e o que ela custa. Decisão que mudar ganha um ADR novo, que
substitui o anterior; o antigo fica como registro do que se sabia na época.

| # | Decisão | Status |
|---|---|---|
| [0001](0001-java-21-e-virtual-threads.md) | Java 21, pelas virtual threads | Aceita |
| [0002](0002-as-migracoes-partem-do-schema-entregue.md) | As migrações partem do schema entregue | Aceita |
| [0003](0003-reserva-com-skip-locked-e-chamada-fora-da-transacao.md) | Reserva com `SKIP LOCKED` e chamada externa fora da transação | Aceita |
| [0004](0004-falha-vira-nova-tentativa-com-limite.md) | Falha vira nova tentativa, com espera crescente e limite | Aceita |
| [0005](0005-um-atendimento-aberto-por-cpf-garantido-pelo-banco.md) | Um atendimento aberto por CPF, garantido pelo banco | Aceita |
| [0006](0006-dados-pessoais-numa-api-sem-autenticacao.md) | Dados pessoais numa API sem autenticação | Aceita |
| [0007](0007-o-contrato-do-front-sai-do-openapi.md) | O contrato do front sai do OpenAPI | Aceita |
| [0008](0008-polling-no-front.md) | Polling no front, e não WebSocket ou SSE | Aceita |
