# Abertura de atendimentos

Desafio Full Stack da Astro7: a recepção de uma clínica registra o paciente (nome e CPF), o
backend busca o protocolo do atendimento num serviço externo ([HTTPBin](https://httpbin.org/uuid))
e a tela mostra o protocolo assim que ele fica pronto, sem recarregar.

| Camada | Tecnologia |
|---|---|
| Frontend | Next.js 16, React 19, TypeScript, Tailwind 4, react-hook-form, zod |
| Backend | Java 21, Spring Boot 4.1, Spring Data JPA, Flyway, springdoc |
| Banco | MySQL 8.4 |
| Testes | JUnit 6, Testcontainers, WireMock, Vitest, Testing Library |

## Como rodar

Com Docker:

```bash
docker compose up --build
```

| O quê | Onde |
|---|---|
| Tela | http://localhost:3000 |
| API | http://localhost:8080/api/attendances |
| Swagger | http://localhost:8080/swagger-ui.html |

O compose sobe o MySQL com o `database/init.sql` do desafio, depois a API, depois a tela,
cada um esperando o anterior ficar saudável. Se alguma porta estiver ocupada, troque no
ambiente ou num `.env`: `FRONTEND_PORT`, `BACKEND_PORT`, `MYSQL_PORT`.

<details>
<summary>Sem Docker para a aplicação (JDK 21 e Node 24)</summary>

```bash
cd database && docker compose up -d          # o MySQL do jeito que o desafio entregou
cd ../backend && ./mvnw spring-boot:run      # :8080
cd ../frontend && npm ci && npm run dev      # :3000, repassa /api para :8080
```

</details>

## O fluxo

1. A pessoa informa nome e CPF. A tela valida (inclusive os dígitos verificadores do CPF) e envia.
2. A API grava o atendimento como `PENDING` e responde `201`.
3. A cada 2s, uma rodada agendada reserva os pendentes e entrega cada um a uma virtual
   thread, que chama `GET https://httpbin.org/uuid`.
4. Com o UUID, o atendimento vira `COMPLETED`. Se a chamada falhar, volta para `PENDING` e é
   tentado de novo mais tarde, com espera crescente; na quinta falha, vira `FAILED`.
5. A tela consulta o atendimento a cada 2s e mostra o protocolo quando ele chega.

Os desenhos (contexto, containers, sequência e estados) estão em [docs/arquitetura.md](docs/arquitetura.md).

### API

| Método | Rota | Resposta |
|---|---|---|
| `POST` | `/api/attendances` | `201` + `Location`; `400` com o erro de cada campo; `409` se o CPF já tem atendimento em andamento |
| `GET` | `/api/attendances/{id}` | `200`; `404` |
| `POST` | `/api/attendances/{id}/renewals` | `201` com um novo atendimento para o mesmo paciente; `409` se o anterior ainda está em andamento |

Erros saem no formato [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) (`application/problem+json`).
O CPF sai sempre mascarado: `***.982.247-**`.

## Testando os cenários de falha

O serviço externo é configurável por `PROTOCOL_URL`, e o HTTPBin tem endpoints que simulam
falha:

```bash
# o serviço responde 500: o atendimento fica PENDING, com as tentativas e o motivo gravados
PROTOCOL_URL=https://httpbin.org/status/500 docker compose up -d backend

# o serviço demora 10s: estoura o timeout de leitura (5s) e conta como falha
PROTOCOL_URL=https://httpbin.org/delay/10 docker compose up -d backend

# de volta ao normal: os pendentes são processados na próxima tentativa agendada
docker compose up -d backend
```

Para ver o estado no banco:

```bash
docker compose exec mysql mysql -uclinic -pclinic clinic \
  -e "select id, status, attempts, next_attempt_at, last_error from attendance"
```

Com a API fora do ar (`docker compose stop backend`), os cards continuam na tela, avisam que
não conseguiram atualizar e voltam a consultar sozinhos, com intervalo crescente.

### Os extras

- **Mais de um paciente:** a tela acompanha até 10 atendimentos ao mesmo tempo, cada um com
  sua própria consulta. No backend, cada atendimento roda na própria virtual thread, e um
  semáforo limita quantos consultam o serviço externo ao mesmo tempo.
- **Gerar novo token:** um atendimento encerrado (`COMPLETED` ou `FAILED`) oferece "Novo
  atendimento para este paciente". Enquanto houver um em andamento, o mesmo CPF não abre
  outro. Essa regra é garantida por um índice único no banco, e não só por uma consulta
  prévia.

## Decisões

Cada uma tem um ADR em [docs/adr](docs/adr/README.md), com o contexto e o que foi descartado.

| Decisão | Por quê, em uma linha |
|---|---|
| [Java 21 e virtual threads](docs/adr/0001-java-21-e-virtual-threads.md) | uma chamada lenta ocupa só a própria thread, que custa quase nada esperando |
| [As migrações partem do schema entregue](docs/adr/0002-as-migracoes-partem-do-schema-entregue.md) | o `init.sql` continua valendo; o Flyway só acrescenta o que falta |
| [`SKIP LOCKED` e chamada fora da transação](docs/adr/0003-reserva-com-skip-locked-e-chamada-fora-da-transacao.md) | nenhum atendimento é processado duas vezes, e nenhum segura conexão do banco esperando o HTTPBin |
| [Nova tentativa com espera e limite](docs/adr/0004-falha-vira-nova-tentativa-com-limite.md) | o registro nunca se perde, e um serviço fora não é martelado |
| [Um atendimento aberto por CPF, no banco](docs/adr/0005-um-atendimento-aberto-por-cpf-garantido-pelo-banco.md) | duas requisições simultâneas passam juntas por uma checagem na aplicação |
| [Dados pessoais sem autenticação](docs/adr/0006-dados-pessoais-numa-api-sem-autenticacao.md) | CPF mascarado, sem listagem, e o navegador só guarda ids |
| [O contrato do front sai do OpenAPI](docs/adr/0007-o-contrato-do-front-sai-do-openapi.md) | os tipos TypeScript são gerados, e um teste falha se o arquivo ficar para trás |
| [Polling no front](docs/adr/0008-polling-no-front.md) | pedido pelo enunciado; a próxima consulta só sai quando a anterior termina |

## Testes

```bash
cd backend && ./mvnw verify      # 72 testes; precisa do Docker (Testcontainers)
cd frontend && npm test          # 51 testes
cd frontend && npm run lint && npm run typecheck
```

- **Backend:** as regras (CPF, espera entre tentativas, transições de estado) têm testes de
  unidade. Tudo que depende do MySQL roda contra um MySQL de verdade: `SKIP LOCKED`, a
  coluna gerada e o índice único não existem num banco em memória. O HTTPBin é simulado com
  WireMock, inclusive com atraso, erro e conexão derrubada.
- **Frontend:** o polling é testado com timers falsos, e a tela inteira (abrir → aguardar →
  protocolo → renovar) com a API simulada.

A cobertura sai em `backend/target/site/jacoco` e em `frontend/coverage` (`npm run test:coverage`).

### Análise estática (SonarQube)

Resultado da última análise, no SonarQube Community com o perfil padrão (Sonar way):

| Projeto | Quality gate | Bugs | Vulnerabilidades | Code smells | Hotspots | Duplicação | Cobertura |
|---|---|---|---|---|---|---|---|
| backend | aprovado | 0 | 0 | 0 | 0 | 0% | 95,4% |
| frontend | aprovado | 0 | 0 | 0 | 0 | 0% | 91,6% |

Para reproduzir com um SonarQube local:

```bash
docker run -d --name sonarqube -p 9000:9000 sonarqube:community
# em http://localhost:9000, gere um token de análise e exporte como SONAR_TOKEN

cd backend && ./mvnw verify org.sonarsource.scanner.maven:sonar-maven-plugin:sonar \
  -Dsonar.host.url=http://localhost:9000

cd ../frontend && npm run test:coverage && docker run --rm \
  -e SONAR_HOST_URL=http://host.docker.internal:9000 -e SONAR_TOKEN \
  -v "$PWD:/usr/src" sonarsource/sonar-scanner-cli
```

## Estrutura

```
backend/
  src/main/java/com/astro7/clinic/
    attendance/     entidade, regras de abertura, repositório
    attendance/web/ controller e formatos da API
    processing/     rotina periódica, reserva, tentativas
    protocol/       cliente do HTTPBin
    cpf/            validação e máscara do CPF
    web/            erros, CORS
  src/main/resources/db/migration/   V1 (o init.sql) e V2
frontend/
  api/              contrato gerado (openapi.json, schema.d.ts) e cliente HTTP
  features/attendance/   formulário, card, polling, lista acompanhada
  components/       campo de texto e spinner
database/           o compose e o init.sql entregues com o desafio
docs/               arquitetura e ADRs
```

## Limitações conhecidas e próximos passos

- **Sem autenticação**, porque o enunciado não pede. Com o `id` sequencial, dá para enumerar
  nome e status dos atendimentos (o CPF sai mascarado) e renovar o atendimento de outra
  pessoa. Num sistema real: login da recepção e um identificador não enumerável.
  Ver [ADR 0006](docs/adr/0006-dados-pessoais-numa-api-sem-autenticacao.md).
- **Até 2s de atraso** entre o protocolo existir e aparecer. Com push (SSE), seria imediato,
  ao custo de uma conexão aberta por card.
- **Volume:** com muitos atendimentos, o próximo passo seria uma fila (RabbitMQ, SQS) entre a
  abertura e o processamento. O `SKIP LOCKED` já permite várias instâncias do backend.
- **Observabilidade:** há `/actuator/health`; métricas de tentativas e falhas por minuto
  seriam o próximo acréscimo.

## Sobre o esqueleto recebido

- Faltava a pasta `backend/.mvn/wrapper/`, sem a qual o `./mvnw` não roda. Ela foi restaurada.
- O pacote `com.astro7.fullstack_challange` virou `com.astro7.clinic`: underscore em nome de
  pacote é apontado pelo Sonar, e o nome tinha um erro de digitação.
- A versão do Java subiu de 17 para 21 ([ADR 0001](docs/adr/0001-java-21-e-virtual-threads.md)).
- `database/` ficou como veio. O Flyway parte do `init.sql` em vez de substituí-lo.
