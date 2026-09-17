# Frontend

Tela da recepção: abre o atendimento e acompanha o protocolo. Visão geral, decisões e
como rodar o projeto inteiro estão no [README da raiz](../README.md).

```bash
npm ci
npm run dev             # http://localhost:3000; /api vai para BACKEND_URL (padrão http://localhost:8080)
npm test                # Vitest
npm run test:coverage
npm run lint
npm run typecheck       # gera os tipos de rota do Next e roda o tsc
npm run gen:api         # regenera api/schema.d.ts a partir de api/openapi.json
```

## Onde fica cada coisa

| Caminho | O quê |
|---|---|
| `api/openapi.json` | contrato publicado pelo backend (conferido pelo `OpenApiContractTest`) |
| `api/schema.d.ts` | tipos gerados dele; não editar à mão |
| `api/attendances.ts` | chamadas à API e tradução dos erros (`ApiError`) |
| `features/attendance/attendance-polling.ts` | a consulta periódica de um atendimento |
| `features/attendance/tracked-attendances.ts` | a lista acompanhada, guardada só com ids no `localStorage` |
| `features/attendance/open-attendance-schema.ts` | validação do formulário (zod) |

Quando a API mudar: `./mvnw test -Dtest=OpenApiContractTest -Dopenapi.update=true` no backend,
depois `npm run gen:api` aqui. O TypeScript aponta o que quebrou.
