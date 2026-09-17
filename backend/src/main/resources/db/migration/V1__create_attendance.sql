-- Mesma tabela do database/init.sql entregue com o desafio.
-- Quando o banco ja nasceu pelo init.sql, o Flyway registra esta versao como
-- baseline e nao a executa (spring.flyway.baseline-on-migrate).
CREATE TABLE attendance (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    patient_name VARCHAR(150) NOT NULL,
    cpf VARCHAR(11) NOT NULL,
    status VARCHAR(20) NOT NULL,
    protocol VARCHAR(100) NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
);
