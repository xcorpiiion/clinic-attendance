import { z } from "zod";

import type { OpenAttendanceRequest } from "@/api/attendances";

import { cpfDigits, isValidCpf } from "./cpf";

const NAME_PATTERN = /^\p{L}[\p{L} .'-]*$/u;

export const openAttendanceSchema = z.object({
  patientName: z
    .string()
    .trim()
    .min(1, "Informe o nome")
    .min(3, "O nome deve ter entre 3 e 150 caracteres")
    .max(150, "O nome deve ter entre 3 e 150 caracteres")
    .regex(NAME_PATTERN, "O nome deve conter apenas letras"),
  cpf: z
    .string()
    .trim()
    .min(1, "Informe o CPF")
    .refine(isValidCpf, "CPF inválido")
    .transform(cpfDigits),
});

export type OpenAttendanceFormValues = z.input<typeof openAttendanceSchema>;

// A saída do schema é o corpo da requisição: se o contrato da API mudar e o
// formulário não acompanhar, a atribuição deixa de compilar.
export type OpenAttendancePayload = z.output<typeof openAttendanceSchema>;
export const toRequest = (payload: OpenAttendancePayload): OpenAttendanceRequest => payload;
