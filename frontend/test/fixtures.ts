import { vi } from "vitest";

import type { Attendance } from "@/api/attendances";

export const PROTOCOL = "3fa85f64-5717-4562-b3fc-2c963f66afa6";

export function anAttendance(overrides: Partial<Attendance> = {}): Attendance {
  return {
    id: 1,
    patientName: "Maria da Silva",
    maskedCpf: "***.982.247-**",
    status: "PENDING",
    createdAt: "2026-09-16T12:00:00Z",
    updatedAt: "2026-09-16T12:00:00Z",
    ...overrides,
  };
}

export function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": status >= 400 ? "application/problem+json" : "application/json" },
  });
}

/** Troca o fetch global; cada chamada consome a próxima resposta da lista. */
export function mockFetch(...responses: Array<Response | Error | DOMException>) {
  const fetchMock = vi.fn<typeof fetch>();
  for (const response of responses) {
    // O DOMException do jsdom não herda de Error, então a checagem é pelo Response.
    if (response instanceof Response) {
      fetchMock.mockResolvedValueOnce(response);
    } else {
      fetchMock.mockRejectedValueOnce(response);
    }
  }
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}
