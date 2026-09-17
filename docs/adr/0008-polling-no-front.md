# 0008 — Polling no front, e não WebSocket ou SSE

- **Status:** Aceita
- **Data:** 2026-09-16

## Contexto

A tela precisa mostrar o protocolo assim que ele existir, sem recarregar. O enunciado pede,
nos requisitos do front, *consulta periódica do status do atendimento*.

## Decisão

Cada card consulta `GET /api/attendances/{id}` com um hook próprio (`useAttendancePolling`):

- a cada 2s enquanto o status é `PENDING` ou `PROCESSING`, e para em `COMPLETED` ou `FAILED`;
- a próxima consulta só é agendada quando a anterior termina (`setTimeout` encadeado, não
  `setInterval`), então uma resposta lenta nunca acumula requisições;
- com o servidor fora, a espera dobra (4s, 8s… até 30s) e o card avisa que a consulta
  continua sozinha;
- `404` encerra o ciclo e oferece remover o card;
- desmontar o componente cancela a requisição em andamento e o próximo agendamento.

O hook é escrito à mão, sem SWR nem TanStack Query.

## Alternativas consideradas

- **Server-Sent Events.** Empurraria o protocolo na hora, mas o backend teria que manter uma
  conexão por card e avisar quem estiver ouvindo, o que com várias instâncias pede um
  barramento entre elas. E o enunciado pede consulta periódica.
- **WebSocket.** O mesmo custo, com comunicação nos dois sentidos, que ninguém usa aqui.
- **SWR/TanStack Query com `refreshInterval`.** Resolve em poucas linhas, mas o polling é
  justamente o que o desafio avalia, e uma dependência esconderia as decisões acima.

## Consequências

- Até 2s entre o protocolo existir e aparecer na tela.
- Uma requisição a cada 2s por card aberto. Com a lista limitada a 10 atendimentos e a
  consulta por id, o custo é baixo.
- O comportamento é testado com timers falsos: intervalo, parada no status final, espera
  crescente na falha e cancelamento ao desmontar.
