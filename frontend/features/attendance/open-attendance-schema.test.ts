import { describe, expect, it } from "vitest";

import { openAttendanceSchema } from "./open-attendance-schema";

/** A primeira mensagem de cada campo, que é a que o formulário mostra. */
function firstErrors(input: unknown): Record<string, string> {
  const result = openAttendanceSchema.safeParse(input);
  const errors: Record<string, string> = {};
  for (const issue of result.error?.issues ?? []) {
    errors[issue.path.join(".")] ??= issue.message;
  }
  return errors;
}

describe("openAttendanceSchema", () => {
  it("entrega o CPF só com dígitos e o nome sem espaços nas pontas", () => {
    const result = openAttendanceSchema.parse({ patientName: "  Maria da Silva ", cpf: "529.982.247-25" });

    expect(result).toEqual({ patientName: "Maria da Silva", cpf: "52998224725" });
  });

  it("pede os dois campos quando vazios", () => {
    const errors = firstErrors({ patientName: "   ", cpf: "" });

    expect(errors.patientName).toBe("Informe o nome");
    expect(errors.cpf).toBe("Informe o CPF");
  });

  it("recusa nome curto e nome com números", () => {
    expect(firstErrors({ patientName: "Al", cpf: "52998224725" }).patientName).toBe(
      "O nome deve ter entre 3 e 150 caracteres",
    );
    expect(firstErrors({ patientName: "Maria 2", cpf: "52998224725" }).patientName).toBe(
      "O nome deve conter apenas letras",
    );
  });

  it("aceita acentos, apóstrofo e hífen no nome", () => {
    expect(openAttendanceSchema.safeParse({ patientName: "João D'Ávila-Sá", cpf: "52998224725" }).success).toBe(
      true,
    );
  });

  it("recusa CPF com dígito verificador errado", () => {
    expect(firstErrors({ patientName: "Maria da Silva", cpf: "529.982.247-24" }).cpf).toBe("CPF inválido");
  });
});
