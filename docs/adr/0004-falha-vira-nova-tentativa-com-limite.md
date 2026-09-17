# 0004 — Falha vira nova tentativa, com espera crescente e limite

- **Status:** Aceita
- **Data:** 2026-09-16

## Contexto

O enunciado pede *tratamento de erros sem perder o registro do atendimento*. O serviço
externo pode responder erro (`/status/500`), demorar além do limite (`/delay/10`), cair, ou
responder algo que não é um UUID.

## Decisão

Toda forma de não receber um UUID válido vira `ProtocolUnavailableException`, e o atendimento
volta para `PENDING` com:

- `attempts` incrementado;
- `next_attempt_at` = agora + espera, que dobra a cada falha: 5s, 10s, 20s, 40s, com teto
  de 2 min;
- `last_error` com o motivo, para quem for investigar.

Na quinta falha (`clinic.processing.max-attempts`), o atendimento vai para `FAILED`. O
registro nunca é apagado.

## Alternativas consideradas

- **Retry imediato, dentro da mesma tarefa.** Insiste num serviço que acabou de cair, que é
  quando ele mais precisa de folga, e segura a vaga de concorrência durante as esperas.
- **Tentar para sempre.** Um serviço que não volta gera uma chamada por atendimento a cada
  rodada, indefinidamente, e o paciente nunca recebe uma resposta definitiva.
- **Distinguir erro permanente de temporário.** Um 4xx do HTTPBin só aconteceria com a URL
  mal configurada, e aí nenhum atendimento funciona. Tratar tudo como temporário e limitar
  as tentativas cobre o caso sem uma classificação que ninguém exercitaria.

## Consequências

- `FAILED` é final para aquele atendimento. O paciente pode abrir outro, e a tela oferece
  isso no próprio card (ver [0005](0005-um-atendimento-aberto-por-cpf-garantido-pelo-banco.md)).
- A espera é um `DATETIME` no banco, não um `sleep`: a tentativa sobrevive a um restart do
  serviço e não ocupa thread enquanto espera.
- A mensagem de timeout do cliente HTTP do JDK é "Request cancelled", e não "timed out". Ela
  aparece assim no `last_error`.
