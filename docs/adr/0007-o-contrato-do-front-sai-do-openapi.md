# 0007 — O contrato do front sai do OpenAPI

- **Status:** Aceita
- **Data:** 2026-09-16

## Contexto

Front e back trocam três formatos: a requisição de abertura, o atendimento e o erro.
Escrever os tipos TypeScript à mão é manter uma segunda cópia do contrato, que diverge em
silêncio na primeira mudança.

## Decisão

O backend é a única fonte:

```
controllers + records (Java)
  → springdoc publica /v3/api-docs
  → OpenApiContractTest grava/confere frontend/api/openapi.json
  → openapi-typescript gera frontend/api/schema.d.ts
  → o front importa os tipos dali
```

Os dois arquivos gerados são versionados. O `npm run build` não depende do backend estar no ar.

O `OpenApiContractTest` **falha** quando a API muda e o arquivo não acompanha. Para atualizar:

```bash
cd backend && ./mvnw test -Dtest=OpenApiContractTest -Dopenapi.update=true
cd ../frontend && npm run gen:api
```

## Alternativas consideradas

- **Pacote npm com os tipos.** Um passo de publicação e uma versão a mais para um único
  consumidor, que mora no mesmo repositório.
- **Gerar no build do front, a partir do backend no ar.** Acopla o build do front à subida
  do backend.

## Consequências

- Renomear um campo no Java e regerar faz o TypeScript apontar cada uso que quebrou.
- O tipo some em tempo de execução: o formulário continua validando com zod o que o usuário
  digita. O tipo gerado cobre o formato da resposta; o zod, a entrada.
- O springdoc descreve o `ProblemDetail` do Spring com um campo que não existe no JSON; o erro
  tem um schema próprio, `ApiProblem`, só para o contrato.
