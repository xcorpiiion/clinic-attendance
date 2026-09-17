# 0003 — Reserva com `SKIP LOCKED` e chamada externa fora da transação

- **Status:** Aceita
- **Data:** 2026-09-16

## Contexto

Um processo periódico precisa achar os atendimentos pendentes e buscar o protocolo de cada
um, sem que um bloqueie os outros. Dois riscos:

1. **Processar o mesmo atendimento duas vezes:** duas rodadas simultâneas, ou duas
   instâncias do serviço, pegando a mesma linha.
2. **Um atendimento lento travar todos:** se a chamada externa acontece dentro de uma
   transação, a conexão com o banco fica presa enquanto o serviço externo não responde.
   Com poucas conexões presas, o pool esgota, e aí nem a API de consulta responde.

## Decisão

O processamento tem três passos, e só o primeiro e o último tocam o banco:

```
rodada (a cada 2s)            tarefa por atendimento (virtual thread)
──────────────────            ─────────────────────────────────────────
TX: SELECT ... FOR UPDATE     chama o HTTPBin (sem transação aberta)
    SKIP LOCKED LIMIT n       TX: grava COMPLETED, ou agenda nova tentativa
    marca PROCESSING
fim da TX, entrega as tarefas
```

- `FOR UPDATE SKIP LOCKED` faz uma rodada concorrente **pular** as linhas travadas, em vez de
  esperar por elas. Cada atendimento é reservado por uma rodada só.
- O `LIMIT` é o número de vagas livres no semáforo de concorrência. O que não coube fica
  `PENDING` no banco para a próxima rodada, em vez de se acumular numa fila em memória.
- O agendamento é `fixedDelay`: a próxima rodada começa depois de a anterior terminar, e a
  rodada é curta porque só reserva e entrega.
- A chamada externa tem timeout de conexão (2s) e de leitura (5s).

## Alternativas consideradas

- **`@Async` direto sobre um `findByStatus(PENDING)`.** Sem a reserva, a rodada seguinte
  pega de novo o que ainda está em andamento.
- **Fila (RabbitMQ, Kafka).** É o passo natural com volume alto, mas acrescenta
  infraestrutura a um desafio cujo enunciado pede um processo periódico.
- **ShedLock para uma rodada por vez no cluster.** Resolve a duplicidade serializando as
  instâncias; o `SKIP LOCKED` resolve sem serializar, e sem dependência.

## Consequências

- Várias instâncias do serviço podem rodar juntas sem processar nada em dobro.
- Se o serviço cair entre a reserva e a gravação, o atendimento fica em `PROCESSING`. Uma
  segunda rotina devolve à fila o que está assim há mais de `stale-after` (2 min), contando
  como tentativa. Esse tempo precisa ser bem maior que o timeout da chamada externa, senão
  a recuperação pegaria um atendimento ainda em andamento.
- A entidade tem `@Version`: se a recuperação e a gravação do resultado se cruzarem, uma
  delas falha em vez de sobrescrever a outra.
- O teste `slowCallsRunInParallel` prova o paralelismo pelo tempo: quatro chamadas de 1s
  terminam juntas em menos de 2,5s.
