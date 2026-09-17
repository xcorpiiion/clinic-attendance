import { act, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { anAttendance, jsonResponse, mockFetch, PROTOCOL } from "@/test/fixtures";

import { POLL_INTERVAL_MS } from "./attendance-polling";

/**
 * O fluxo da tela inteira: abrir, acompanhar até o protocolo, renovar e dispensar.
 * O módulo é recarregado a cada teste porque a lista acompanhada vive nele.
 */
async function renderPanel() {
  vi.resetModules();
  const { AttendancePanel } = await import("./attendance-panel");
  render(<AttendancePanel />);
  return userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
}

async function advance(ms: number) {
  await act(() => vi.advanceTimersByTimeAsync(ms));
}

describe("AttendancePanel", () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("mostra o atendimento aguardando e atualiza sozinho quando o protocolo chega", async () => {
    mockFetch(
      jsonResponse(anAttendance({ id: 7 }), 201),
      jsonResponse(anAttendance({ id: 7, status: "PROCESSING" })),
      jsonResponse(anAttendance({ id: 7, status: "COMPLETED", protocol: PROTOCOL })),
    );
    const user = await renderPanel();

    await user.type(screen.getByLabelText("Nome do paciente"), "Maria da Silva");
    await user.type(screen.getByLabelText("CPF"), "52998224725");
    await user.click(screen.getByRole("button", { name: "Abrir atendimento" }));

    const card = await screen.findByRole("article");
    expect(within(card).getByText("Aguardando processamento")).toBeInTheDocument();

    await advance(POLL_INTERVAL_MS);
    expect(within(card).getByText("Aguardando processamento")).toBeInTheDocument();

    await advance(POLL_INTERVAL_MS);
    expect(within(card).getByText("Protocolo disponível")).toBeInTheDocument();
    expect(within(card).getByText(PROTOCOL)).toBeInTheDocument();
    expect(JSON.parse(localStorage.getItem("clinic:attendance-ids") ?? "[]")).toEqual([7]);
  });

  it("acompanha vários pacientes ao mesmo tempo, cada um no seu ritmo", async () => {
    localStorage.setItem("clinic:attendance-ids", JSON.stringify([2, 1]));
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string) =>
        url.endsWith("/2")
          ? jsonResponse(anAttendance({ id: 2, patientName: "João Souza", status: "PENDING" }))
          : jsonResponse(anAttendance({ id: 1, status: "COMPLETED", protocol: PROTOCOL })),
      ),
    );
    await renderPanel();

    await advance(0);

    const [joao, maria] = screen.getAllByRole("article");
    expect(within(joao).getByText("João Souza")).toBeInTheDocument();
    expect(within(joao).getByText("Aguardando processamento")).toBeInTheDocument();
    expect(within(maria).getByText(PROTOCOL)).toBeInTheDocument();
  });

  it("troca o atendimento encerrado pelo novo ao renovar", async () => {
    localStorage.setItem("clinic:attendance-ids", JSON.stringify([1]));
    const fetchMock = mockFetch(
      jsonResponse(anAttendance({ id: 1, status: "COMPLETED", protocol: PROTOCOL })),
      jsonResponse(anAttendance({ id: 2 }), 201),
    );
    const user = await renderPanel();
    await advance(0);

    await user.click(screen.getByRole("button", { name: "Novo atendimento para este paciente" }));

    const card = await screen.findByText("Aguardando processamento");
    expect(card).toBeInTheDocument();
    expect(screen.queryByText(PROTOCOL)).not.toBeInTheDocument();
    expect(fetchMock.mock.calls[1][0]).toBe("/api/attendances/1/renewals");
    expect(JSON.parse(localStorage.getItem("clinic:attendance-ids") ?? "[]")).toEqual([2]);
  });

  it("mantém o atendimento e explica quando a renovação é recusada", async () => {
    localStorage.setItem("clinic:attendance-ids", JSON.stringify([1]));
    mockFetch(
      jsonResponse(anAttendance({ id: 1, status: "FAILED" })),
      jsonResponse(
        { title: "Atendimento em andamento", status: 409, detail: "Já existe um atendimento em andamento para este CPF" },
        409,
      ),
    );
    const user = await renderPanel();
    await advance(0);

    expect(screen.getByText("Protocolo indisponível")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Novo atendimento para este paciente" }));

    expect(await screen.findByText("Já existe um atendimento em andamento para este CPF")).toBeInTheDocument();
    expect(screen.getByText("Protocolo indisponível")).toBeInTheDocument();
  });

  it("copia o protocolo para a área de transferência", async () => {
    localStorage.setItem("clinic:attendance-ids", JSON.stringify([1]));
    mockFetch(jsonResponse(anAttendance({ id: 1, status: "COMPLETED", protocol: PROTOCOL })));
    const user = await renderPanel();
    const writeText = vi.spyOn(navigator.clipboard, "writeText").mockResolvedValue();
    await advance(0);

    await user.click(screen.getByRole("button", { name: "Copiar" }));

    expect(writeText).toHaveBeenCalledWith(PROTOCOL);
    expect(await screen.findByRole("button", { name: "Copiado" })).toBeInTheDocument();
    await advance(2_000);
    expect(screen.getByRole("button", { name: "Copiar" })).toBeInTheDocument();
  });

  it("remove da lista o atendimento dispensado", async () => {
    localStorage.setItem("clinic:attendance-ids", JSON.stringify([1]));
    mockFetch(jsonResponse(anAttendance({ id: 1, status: "COMPLETED", protocol: PROTOCOL })));
    const user = await renderPanel();
    await advance(0);

    await user.click(screen.getByRole("button", { name: "Remover da lista" }));

    expect(screen.queryByRole("article")).not.toBeInTheDocument();
    expect(screen.getByText(/Os atendimentos abertos aparecem aqui/)).toBeInTheDocument();
    expect(localStorage.getItem("clinic:attendance-ids")).toBe("[]");
  });

  it("oferece remover o atendimento que não existe mais", async () => {
    localStorage.setItem("clinic:attendance-ids", JSON.stringify([99]));
    mockFetch(jsonResponse({ title: "Atendimento não encontrado", status: 404 }, 404));
    await renderPanel();
    await advance(0);

    expect(screen.getByText("O atendimento #99 não foi encontrado.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Remover da lista" })).toBeInTheDocument();
  });

  it("ignora conteúdo corrompido no armazenamento do navegador", async () => {
    localStorage.setItem("clinic:attendance-ids", "{não é json");
    await renderPanel();

    expect(screen.queryByRole("article")).not.toBeInTheDocument();
  });
});
