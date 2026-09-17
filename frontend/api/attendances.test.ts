import { describe, expect, it } from "vitest";

import { anAttendance, jsonResponse, mockFetch } from "@/test/fixtures";

import { ApiError, getAttendance, openAttendance, renewAttendance } from "./attendances";

describe("attendances api", () => {
  it("abre o atendimento com o corpo em JSON", async () => {
    const fetchMock = mockFetch(jsonResponse(anAttendance(), 201));

    const attendance = await openAttendance({ patientName: "Maria da Silva", cpf: "52998224725" });

    expect(attendance.status).toBe("PENDING");
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("/api/attendances");
    expect(init?.method).toBe("POST");
    expect(JSON.parse(String(init?.body))).toEqual({ patientName: "Maria da Silva", cpf: "52998224725" });
  });

  it("renova pelo id do atendimento anterior", async () => {
    const fetchMock = mockFetch(jsonResponse(anAttendance({ id: 2 }), 201));

    await renewAttendance(1);

    expect(fetchMock.mock.calls[0][0]).toBe("/api/attendances/1/renewals");
  });

  it("traz a mensagem e os erros de campo do problem detail", async () => {
    mockFetch(
      jsonResponse(
        { title: "Dados inválidos", status: 400, detail: "Corrija os campos indicados.", errors: { cpf: "CPF inválido" } },
        400,
      ),
    );

    const error = await openAttendance({ patientName: "Maria", cpf: "1" }).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ status: 400, message: "Corrija os campos indicados.", fieldErrors: { cpf: "CPF inválido" } });
  });

  it("não repassa detalhe de erro interno do servidor", async () => {
    mockFetch(jsonResponse({ title: "Erro interno", status: 500, detail: "stack trace" }, 500));

    await expect(getAttendance(1)).rejects.toMatchObject({
      status: 500,
      message: "O servidor não conseguiu atender agora. Tente novamente em instantes.",
    });
  });

  it("entende resposta de erro sem JSON, como a de um proxy fora do ar", async () => {
    mockFetch(new Response("<html>Bad Gateway</html>", { status: 502 }));

    await expect(getAttendance(1)).rejects.toMatchObject({ status: 502, fieldErrors: {} });
  });

  it("marca falha de rede como status 0", async () => {
    mockFetch(new TypeError("Failed to fetch"));

    const error = await getAttendance(1).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).isNetworkError).toBe(true);
  });

  it("deixa o cancelamento passar como cancelamento", async () => {
    const controller = new AbortController();
    controller.abort();
    mockFetch(new DOMException("Aborted", "AbortError"));

    await expect(getAttendance(1, controller.signal)).rejects.toHaveProperty("name", "AbortError");
  });
});
