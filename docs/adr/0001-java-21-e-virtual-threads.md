# 0001 — Java 21, pelas virtual threads

- **Status:** Aceita
- **Data:** 2026-09-16

## Contexto

O esqueleto veio com Java 17, e o enunciado pede só "Java + Spring Boot", sem versão.

O requisito que mais pesa no desenho é: *o processamento de um atendimento não deve
bloquear o processamento dos demais*. Cada atendimento faz uma chamada HTTP a um serviço
que pode demorar (o próprio HTTPBin tem `/delay/10`). Isso é espera de I/O, não uso de CPU.

## Decisão

Java 21 com `spring.threads.virtual.enabled=true`. Cada atendimento é processado numa
tarefa do executor da aplicação, que com essa opção cria uma virtual thread por tarefa.

Uma chamada lenta segura só a própria virtual thread, que custa quase nada enquanto
espera. Quantos atendimentos rodam ao mesmo tempo é decidido por um semáforo
(`clinic.processing.max-concurrency`), não pelo tamanho de um pool.

## Alternativas consideradas

- **Java 17 com um pool de threads fixo.** Funciona, mas o tamanho do pool vira ao mesmo
  tempo o limite de concorrência e o risco: com o pool cheio de chamadas lentas, o resto
  espera na fila, que é exatamente o bloqueio que o requisito proíbe.
- **Cliente HTTP reativo (WebClient).** Resolve a espera sem thread, mas troca código
  sequencial e legível por encadeamento de operadores num serviço que não é reativo em
  nenhum outro ponto.

## Consequências

- O código do processamento é sequencial e fácil de ler: chama, espera, grava.
- No Java 21 um bloco `synchronized` prende a thread física por baixo da virtual. O driver
  do MySQL usa alguns; com o pool de conexões na frente, o limite real continua sendo o
  número de conexões. Isso deixa de valer a partir do Java 24.
- Quem rodar o projeto precisa de JDK 21. A imagem Docker já traz.
