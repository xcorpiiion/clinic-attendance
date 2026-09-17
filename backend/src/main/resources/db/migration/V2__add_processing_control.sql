-- attempts e next_attempt_at: nova tentativa com espera crescente, e limite de tentativas.
-- last_error: por que a ultima tentativa falhou, para quem investiga.
-- version: trava otimista entre a conclusao de uma tentativa e a recuperacao de
-- atendimentos presos em PROCESSING.
ALTER TABLE attendance
    ADD COLUMN attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN next_attempt_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN last_error VARCHAR(500) NULL,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- Um CPF tem no maximo um atendimento em aberto. A coluna so tem valor enquanto
-- o atendimento esta aberto, e o indice unico ignora NULL: atendimentos concluidos
-- ou que falharam nao impedem um novo. Garante a regra mesmo com duas requisicoes
-- simultaneas, onde a checagem feita pela aplicacao sozinha deixaria as duas passarem.
ALTER TABLE attendance
    ADD COLUMN open_cpf VARCHAR(11)
        AS (CASE WHEN status IN ('PENDING', 'PROCESSING') THEN cpf END) STORED,
    ADD CONSTRAINT uq_attendance_open_cpf UNIQUE (open_cpf);

CREATE INDEX idx_attendance_status_next_attempt ON attendance (status, next_attempt_at);
