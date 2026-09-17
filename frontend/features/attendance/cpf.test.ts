import { describe, expect, it } from "vitest";

import { cpfDigits, formatCpf, isValidCpf } from "./cpf";

describe("isValidCpf", () => {
  it.each(["52998224725", "529.982.247-25", "11144477735"])("aceita %s", (value) => {
    expect(isValidCpf(value)).toBe(true);
  });

  it.each(["52998224724", "5299822472", "529982247250", "", "abc"])("recusa %s", (value) => {
    expect(isValidCpf(value)).toBe(false);
  });

  it("recusa sequência repetida, mesmo com dígitos verificadores coerentes", () => {
    expect(isValidCpf("111.111.111-11")).toBe(false);
  });
});

describe("formatCpf", () => {
  it.each([
    ["5", "5"],
    ["5299", "529.9"],
    ["5299822", "529.982.2"],
    ["529982247", "529.982.247"],
    ["5299822472", "529.982.247-2"],
    ["52998224725", "529.982.247-25"],
  ])("formata %s como %s", (typed, expected) => {
    expect(formatCpf(typed)).toBe(expected);
  });

  it("ignora o que não é dígito e o que passa de onze", () => {
    expect(formatCpf("529a982b247c25999")).toBe("529.982.247-25");
  });
});

describe("cpfDigits", () => {
  it("mantém só os dígitos", () => {
    expect(cpfDigits("529.982.247-25")).toBe("52998224725");
  });
});
