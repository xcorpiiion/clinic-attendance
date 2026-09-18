# Backend

API de abertura de atendimentos e rotina que busca o protocolo no serviço externo.
Visão geral, decisões e como rodar o projeto inteiro estão no [README da raiz](../README.md).

Requer JDK 21. Os testes precisam do Docker no ar (Testcontainers).

```bash
./mvnw spring-boot:run      # :8080; espera o MySQL em localhost:3306 (cd ../database && docker compose up -d)
./mvnw verify               # testes + cobertura em target/site/jacoco
./mvnw test -Dtest=AttendanceProcessingIntegrationTest
```

Swagger em http://localhost:8080/swagger-ui.html; o documento OpenAPI em `/v3/api-docs`.

## Configuração

Tudo em `src/main/resources/application.yml`. As variáveis de ambiente abaixo
sobrescrevem os padrões:

| Variável | Padrão | Para quê |
|---|---|---|
| `SERVER_PORT` | `8080` | porta HTTP |
| `DB_URL` | `jdbc:mysql://localhost:3306/clinic` | banco |
| `DB_USERNAME` / `DB_PASSWORD` | `clinic` / `clinic` | credenciais do banco |
| `PROTOCOL_URL` | `https://httpbin.org/uuid` | serviço do protocolo; `/status/500` e `/delay/10` simulam falha |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | origens aceitas quando o front não usa o proxy do Next |

Ajustes da rotina, sem variável própria (qualquer propriedade do Spring pode vir do
ambiente, como `CLINIC_PROCESSING_MAX_ATTEMPTS`):

| Propriedade | Padrão | Efeito |
|---|---|---|
| `clinic.protocol.connect-timeout` | `2s` | tempo para conectar no serviço externo |
| `clinic.protocol.read-timeout` | `5s` | tempo para ele responder; estourou, conta como falha |
| `clinic.processing.enabled` | `true` | liga a rotina; os testes desligam e disparam as rodadas na mão |
| `clinic.processing.poll-interval` | `2s` | pausa entre o fim de uma rodada e o início da próxima |
| `clinic.processing.max-concurrency` | `20` | atendimentos consultando o serviço ao mesmo tempo |
| `clinic.processing.max-attempts` | `5` | tentativas antes de `FAILED` |
| `clinic.processing.initial-backoff` | `5s` | espera depois da primeira falha; dobra a cada nova falha |
| `clinic.processing.max-backoff` | `2m` | teto dessa espera |
| `clinic.processing.stale-after` | `2m` | tempo em `PROCESSING` a partir do qual o atendimento volta para a fila |
| `clinic.processing.stale-check-interval` | `30s` | frequência dessa verificação |

## Pacotes

| Pacote | Responsabilidade |
|---|---|
| `attendance` | a entidade e suas transições de estado, a abertura e a renovação |
| `attendance.web` | controller e os records da API (a resposta mascara o CPF) |
| `processing` | agendamento, reserva com `SKIP LOCKED`, execução em virtual threads, novas tentativas |
| `protocol` | cliente do serviço externo; toda falha vira `ProtocolUnavailableException` |
| `cpf` | dígitos verificadores, normalização, máscara e a anotação `@ValidCpf` |
| `web` | tratamento de erros (`ProblemDetail`) e CORS |
| `config` | relógio (UTC, em segundos) e metadados do OpenAPI |

O caminho de um atendimento pelo código está em [docs/arquitetura.md](../docs/arquitetura.md).

## Banco

As migrações ficam em `src/main/resources/db/migration`. A V1 é o `database/init.sql` do
desafio, e a V2 acrescenta o controle de tentativas e o índice de um atendimento aberto
por CPF ([ADR 0002](../docs/adr/0002-as-migracoes-partem-do-schema-entregue.md)).

O Hibernate não cria nada (`ddl-auto=validate`): mudança de schema é migração nova.
Migração já aplicada não se edita, porque o Flyway confere o checksum e recusa subir.

## Testes

| Tipo | Classes | O que garantem |
|---|---|---|
| Unidade | `CpfTest`, `RetryPolicyTest`, `AttendanceTest` | regras sem Spring nem banco |
| Cliente HTTP | `ProtocolClientTest` | erro, timeout, conexão derrubada e resposta sem UUID viram falha |
| Integração (MySQL real) | `AttendanceApiIntegrationTest`, `AttendanceServiceIntegrationTest` | contrato HTTP, índice de CPF aberto sob concorrência |
| | `AttendanceProcessingIntegrationTest`, `ScheduledProcessingIntegrationTest` | reserva sem duplicidade, paralelismo, novas tentativas, recuperação de abandonados |
| | `MigrationOverProvidedSchemaTest` | o Flyway parte do `init.sql` sem recriar a tabela |
| Contrato | `OpenApiContractTest` | `frontend/api/openapi.json` acompanha a API |

Os testes de integração sobem um único MySQL para a suíte inteira (`support/IntegrationTest`),
e o HTTPBin é simulado com WireMock.

Quando a API mudar:

```bash
./mvnw test -Dtest=OpenApiContractTest -Dopenapi.update=true
cd ../frontend && npm run gen:api
```
