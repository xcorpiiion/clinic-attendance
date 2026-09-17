const CPF_LENGTH = 11;

export function cpfDigits(value: string): string {
  return value.replace(/\D/g, "");
}

/** Aplica a máscara 000.000.000-00 enquanto a pessoa digita. */
export function formatCpf(value: string): string {
  const digits = cpfDigits(value).slice(0, CPF_LENGTH);
  return digits
    .replace(/^(\d{3})(\d)/, "$1.$2")
    .replace(/^(\d{3})\.(\d{3})(\d)/, "$1.$2.$3")
    .replace(/\.(\d{3})(\d{1,2})$/, ".$1-$2");
}

/**
 * Mesma regra do backend: onze dígitos, dígitos verificadores corretos e
 * nenhuma sequência repetida (111.111.111-11 passa no cálculo, mas não existe).
 */
export function isValidCpf(value: string): boolean {
  const digits = cpfDigits(value);
  if (digits.length !== CPF_LENGTH || /^(\d)\1+$/.test(digits)) {
    return false;
  }
  return (
    checkDigit(digits, 9) === Number(digits[9]) &&
    checkDigit(digits, 10) === Number(digits[10])
  );
}

function checkDigit(digits: string, position: number): number {
  let sum = 0;
  for (let i = 0; i < position; i++) {
    sum += Number(digits[i]) * (position + 1 - i);
  }
  const remainder = (sum * 10) % 11;
  return remainder === 10 ? 0 : remainder;
}
