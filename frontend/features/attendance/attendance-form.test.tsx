import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { anAttendance, jsonResponse, mockFetch } from "@/test/fixtures";

import { AttendanceForm } from "./attendance-form";

function setup() {
  const onOpened = vi.fn();
  render(<AttendanceForm onOpened={onOpened} />);
  const user = userEvent.setup();
  const fill = async (name: string, cpf: string) => {
    if (name) await user.type(screen.getByLabelText("Nome do paciente"), name);
    if (cpf) await user.type(screen.getByLabelText("CPF"), cpf);
  };
  const submit = () => user.click(screen.getByRole("button", { name: "Abrir atendimento" }));
  return { onOpened, user, fill, submit };
}

describe("AttendanceForm", () => {
  it("formata o CPF enquanto a pessoa digita", async () => {
    const { fill } = setup();

    await fill("", "52998224725");

    expect(screen.getByLabelText("CPF")).toHaveValue("529.982.247-25");
  });

  it("deixa corrigir o CPF apagando o último dígito", async () => {
    const { fill, user } = setup();
    await fill("", "52998224724");

    await user.type(screen.getByLabelText("CPF"), "{Backspace}5");

    expect(screen.getByLabelText("CPF")).toHaveValue("529.982.247-25");
  });

  it("não envia com campos inválidos e diz o que corrigir", async () => {
    const fetchMock = mockFetch();
    const { fill, submit } = setup();

    await fill("Maria 2", "52998224724");
    await submit();

    expect(await screen.findByText("O nome deve conter apenas letras")).toBeInTheDocument();
    expect(screen.getByText("CPF inválido")).toBeInTheDocument();
    expect(screen.getByLabelText("CPF")).toHaveAttribute("aria-invalid", "true");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("envia só os dígitos do CPF, entrega o atendimento e limpa o formulário", async () => {
    const opened = anAttendance();
    const fetchMock = mockFetch(jsonResponse(opened, 201));
    const { fill, submit, onOpened } = setup();

    await fill("Maria da Silva", "529.982.247-25");
    await submit();

    await waitFor(() => expect(onOpened).toHaveBeenCalledWith(opened));
    expect(JSON.parse(String(fetchMock.mock.calls[0][1]?.body))).toEqual({
      patientName: "Maria da Silva",
      cpf: "52998224725",
    });
    expect(screen.getByLabelText("Nome do paciente")).toHaveValue("");
    expect(screen.getByLabelText("CPF")).toHaveValue("");
  });

  it("envia ao pressionar Enter no último campo", async () => {
    mockFetch(jsonResponse(anAttendance(), 201));
    const { fill, user, onOpened } = setup();

    await fill("Maria da Silva", "52998224725");
    await user.keyboard("{Enter}");

    await waitFor(() => expect(onOpened).toHaveBeenCalledOnce());
  });

  it("mostra o conflito de CPF sem perder o que foi digitado", async () => {
    mockFetch(
      jsonResponse(
        { title: "Atendimento em andamento", status: 409, detail: "Já existe um atendimento em andamento para este CPF" },
        409,
      ),
    );
    const { fill, submit, onOpened } = setup();

    await fill("Maria da Silva", "52998224725");
    await submit();

    expect(await screen.findByText("Já existe um atendimento em andamento para este CPF")).toBeInTheDocument();
    expect(screen.getByLabelText("CPF")).toHaveValue("529.982.247-25");
    expect(onOpened).not.toHaveBeenCalled();
  });

  it("põe o erro de validação do servidor no campo certo", async () => {
    mockFetch(
      jsonResponse(
        { title: "Dados inválidos", status: 400, detail: "Corrija os campos indicados.", errors: { patientName: "Nome recusado" } },
        400,
      ),
    );
    const { fill, submit } = setup();

    await fill("Maria da Silva", "52998224725");
    await submit();

    expect(await screen.findByText("Nome recusado")).toBeInTheDocument();
    expect(screen.queryByText("Corrija os campos indicados.")).not.toBeInTheDocument();
  });

  it("avisa quando o servidor não responde", async () => {
    mockFetch(new TypeError("Failed to fetch"));
    const { fill, submit } = setup();

    await fill("Maria da Silva", "52998224725");
    await submit();

    expect(await screen.findByText(/Não foi possível falar com o servidor/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Abrir atendimento" })).toBeEnabled();
  });
});
