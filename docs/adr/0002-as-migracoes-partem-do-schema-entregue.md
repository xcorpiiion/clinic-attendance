# 0002 — As migrações partem do schema entregue

- **Status:** Aceita
- **Data:** 2026-09-16

## Contexto

O desafio entrega `database/init.sql`, que cria a tabela `attendance` quando o container do
MySQL sobe pela primeira vez, e o `application.properties` veio com `ddl-auto=none`: o schema
é de quem entregou, não do Hibernate.

O processamento precisa de colunas que a tabela não tem: contagem de tentativas, horário da
próxima tentativa e o motivo da última falha (ver [0004](0004-falha-vira-nova-tentativa-com-limite.md)).

## Decisão

Flyway, com duas migrações:

- **V1** é a tabela do `init.sql`, idêntica;
- **V2** acrescenta o que falta.

Com `baseline-on-migrate=true` e `baseline-version=1`, o Flyway se comporta assim:

| Banco | O que acontece |
|---|---|
| Criado pelo `init.sql` (compose do desafio) | a tabela já existe: V1 vira baseline, só a V2 roda |
| Vazio (testes com Testcontainers) | V1 e V2 rodam |

O `ddl-auto` passou de `none` para `validate`: o Hibernate continua sem mexer no schema, e a
aplicação não sobe se a entidade divergir dele.

## Alternativas consideradas

- **Alterar o `init.sql`.** Mais simples, mas só vale para banco novo. Um banco que já
  subiu com o script original ficaria sem as colunas, e o erro só apareceria na primeira
  consulta.
- **Não mudar o schema e tentar para sempre.** Sem contar tentativas, um serviço que nunca
  volta deixa o atendimento pendente para sempre e gera uma chamada a cada rodada.

## Consequências

- O `database/docker-compose.yml` do desafio continua funcionando sem alteração.
- O `MigrationOverProvidedSchemaTest` sobe o MySQL com o `init.sql` e confirma que só a V2
  roda. Os demais testes cobrem o banco vazio.
- A dependência é `spring-boot-starter-flyway`, e não `flyway-core`: no Spring Boot 4 a
  auto-configuração saiu para um módulo próprio, e o `flyway-core` sozinho fica no
  classpath sem rodar nada, e sem avisar.
