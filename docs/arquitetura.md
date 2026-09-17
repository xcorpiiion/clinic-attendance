# Arquitetura

## Contexto

```mermaid
flowchart LR
    recepcao([Recepção da clínica])
    sistema[Abertura de atendimentos]
    httpbin[(HTTPBin<br/>GET /uuid)]

    recepcao -- "informa nome e CPF,<br/>acompanha o protocolo" --> sistema
    sistema -- "pede um protocolo<br/>por atendimento" --> httpbin
```

## Containers

```mermaid
flowchart LR
    browser([Navegador])

    subgraph compose [docker compose]
        front["frontend<br/>Next.js 16 · :3000"]
        back["backend<br/>Spring Boot 4 · Java 21 · :8080"]
        db[("mysql<br/>MySQL 8.4 · :3306")]
    end

    httpbin[(httpbin.org)]

    browser -- "página e /api/*" --> front
    front -- "rewrite de /api/*" --> back
    back -- "JPA + Flyway" --> db
    back -- "RestClient, timeout 2s/5s" --> httpbin
```

O navegador só conhece o front. `/api/*` chega ao backend pelo rewrite do Next, na mesma
origem, então não há CORS no caminho normal. O CORS do backend existe para quem roda o front
com `next dev` apontando para outra porta.

## Componentes do backend

```mermaid
flowchart TB
    subgraph web [attendance.web]
        controller[AttendanceController]
    end
    subgraph attendance [attendance]
        service[AttendanceService]
        entity[Attendance<br/>transições de estado]
        repo[AttendanceRepository]
    end
    subgraph processing [processing]
        scheduler[AttendanceProcessingScheduler<br/>@Scheduled]
        dispatcher[AttendanceDispatcher<br/>semáforo + virtual threads]
        pservice[AttendanceProcessingService<br/>transações curtas]
        retry[RetryPolicy]
    end
    subgraph protocol [protocol]
        client[ProtocolClient]
    end

    controller --> service --> repo
    scheduler --> dispatcher --> pservice --> repo
    dispatcher --> client
    pservice --> retry
    service -.-> entity
    pservice -.-> entity
```

Os pacotes são por assunto, não por camada: `attendance` (abertura e consulta),
`processing` (a rotina periódica), `protocol` (o serviço externo) e `cpf` (a regra do CPF,
usada na validação e na máscara).

## O fluxo principal

```mermaid
sequenceDiagram
    autonumber
    participant F as Front
    participant A as API
    participant DB as MySQL
    participant S as Rodada agendada
    participant T as Tarefa (virtual thread)
    participant H as HTTPBin

    F->>A: POST /api/attendances {nome, cpf}
    A->>DB: INSERT (PENDING)
    A-->>F: 201 {id, status: PENDING}

    loop a cada 2s, até status final
        F->>A: GET /api/attendances/{id}
        A-->>F: {status}
    end

    S->>DB: SELECT ... FOR UPDATE SKIP LOCKED
    S->>DB: UPDATE status = PROCESSING
    S->>T: entrega o id
    T->>H: GET /uuid
    alt UUID recebido
        T->>DB: UPDATE status = COMPLETED, protocol
    else falha ou timeout
        T->>DB: UPDATE status = PENDING, next_attempt_at += espera
    end

    F->>A: GET /api/attendances/{id}
    A-->>F: {status: COMPLETED, protocol}
```

## Estados do atendimento

```mermaid
stateDiagram-v2
    [*] --> PENDING: aberto
    PENDING --> PROCESSING: reservado por uma rodada
    PROCESSING --> COMPLETED: HTTPBin devolveu UUID
    PROCESSING --> PENDING: falhou, ainda há tentativas<br/>(ou ficou abandonado)
    PROCESSING --> FAILED: falhou na última tentativa
    COMPLETED --> [*]
    FAILED --> [*]
```

`PENDING` e `PROCESSING` são os estados **abertos**: enquanto um atendimento está neles, o CPF
não abre outro ([ADR 0005](adr/0005-um-atendimento-aberto-por-cpf-garantido-pelo-banco.md)).

## Tabela

| Coluna | Origem | Uso |
|---|---|---|
| `id`, `patient_name`, `cpf`, `status`, `protocol`, `created_at`, `updated_at` | `init.sql` do desafio | — |
| `attempts` | V2 | tentativas feitas; no limite, `FAILED` |
| `next_attempt_at` | V2 | quando o atendimento volta a ser elegível |
| `last_error` | V2 | motivo da última falha |
| `version` | V2 | trava otimista entre a gravação do resultado e a recuperação de abandonados |
| `open_cpf` | V2, gerada | CPF enquanto aberto, `NULL` depois; tem índice único |

Horários em UTC. O índice `(status, next_attempt_at)` atende a consulta da rodada.
