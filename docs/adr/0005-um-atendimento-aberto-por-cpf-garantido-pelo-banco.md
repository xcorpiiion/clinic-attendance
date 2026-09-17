# 0005 — Um atendimento aberto por CPF, garantido pelo banco

- **Status:** Aceita
- **Data:** 2026-09-16

## Contexto

O extra "gerar novo token" pede que o usuário possa solicitar um novo atendimento depois de
concluir o anterior, *tratando corretamente o estado da aplicação, do banco e do
processamento pendente*.

A leitura adotada: um paciente não pode ter dois atendimentos em andamento. Encerrado o
anterior (`COMPLETED` ou `FAILED`), pode abrir outro.

Checar antes de gravar não basta. Duas requisições simultâneas, como um duplo clique ou duas
abas, passam juntas pela checagem.

## Decisão

A regra mora no banco, com uma coluna gerada e um índice único:

```sql
open_cpf VARCHAR(11) AS (CASE WHEN status IN ('PENDING', 'PROCESSING') THEN cpf END) STORED,
CONSTRAINT uq_attendance_open_cpf UNIQUE (open_cpf)
```

A coluna só tem valor enquanto o atendimento está aberto, e o índice único do MySQL aceita
vários `NULL`. O serviço ainda consulta antes de gravar, para o caso comum ter uma mensagem
clara; a violação do índice vira a mesma resposta, `409`.

A renovação é `POST /api/attendances/{id}/renewals`, e o CPF vem do banco
(ver [0006](0006-dados-pessoais-numa-api-sem-autenticacao.md)).

## Alternativas consideradas

- **Só a checagem na aplicação.** Tem a corrida descrita acima.
- **Trava pessimista por CPF.** Exigiria uma tabela de pacientes, que o schema não tem.
- **Índice parcial.** O MySQL não tem; a coluna gerada é o equivalente.

## Consequências

- O teste com oito requisições simultâneas para o mesmo CPF termina com exatamente uma
  aceita.
- A regra vale para qualquer caminho de escrita, inclusive um que ainda não exista.
- A entidade não mapeia `open_cpf`: ela é derivada, e o Hibernate não deve escrever nela.
