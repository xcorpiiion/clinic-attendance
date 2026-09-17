# 0006 — Dados pessoais numa API sem autenticação

- **Status:** Aceita
- **Data:** 2026-09-16

## Contexto

O enunciado não pede autenticação, e implementá-la seria escopo inventado. Mas a API guarda
nome e CPF, que são dados pessoais (LGPD), e o `id` é sequencial: qualquer pessoa consegue
chamar `GET /api/attendances/1`, `/2`, `/3`…

## Decisão

Reduzir o que vaza, sem inventar login:

1. **O CPF sai mascarado** em toda resposta (`***.982.247-**`). O completo só entra, no
   `POST`, e quem digitou já o conhece.
2. **Não há listagem.** Nada de `GET /api/attendances` devolvendo todos. O front guarda os ids
   que ele mesmo criou e consulta um a um.
3. **O front guarda só o id** no `localStorage`, nunca nome ou CPF.
4. **A renovação não recebe CPF.** O novo atendimento usa o CPF do anterior, lido no banco,
   porque o front só tem a versão mascarada.
5. **O CORS aceita só a origem do front**, e o front chama a API pela própria origem.

## Alternativas consideradas

- **`public_id` UUID no lugar do id sequencial.** Tira a enumeração, mas é a segunda mudança
  num schema que não é nosso. Com o CPF mascarado, o que a enumeração expõe é nome e status.
- **Autenticação da recepção.** É o que um sistema real teria, e está fora do enunciado.

## Consequências

- O risco que sobra é enumerar nomes e status, e a renovação de atendimento alheio. Os dois
  estão listados no README como limitação conhecida.
- Num sistema real, o próximo passo seria login da recepção e um identificador não
  enumerável.
