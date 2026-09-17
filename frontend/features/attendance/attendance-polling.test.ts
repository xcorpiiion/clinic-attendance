import { act, renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { anAttendance, jsonResponse, mockFetch, PROTOCOL } from "@/test/fixtures";

import { MAX_RETRY_DELAY_MS, POLL_INTERVAL_MS, retryDelay, useAttendancePolling } from "./attendance-polling";

async function advance(ms: number) {
  await act(() => vi.advanceTimersByTimeAsync(ms));
}

describe("useAttendancePolling", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("consulta até o protocolo chegar e então para", async () => {
    const fetchMock = mockFetch(
      jsonResponse(anAttendance({ status: "PENDING" })),
      jsonResponse(anAttendance({ status: "PROCESSING" })),
      jsonResponse(anAttendance({ status: "COMPLETED", protocol: PROTOCOL })),
    );
    const { result } = renderHook(() => useAttendancePolling(1));

    await advance(0);
    expect(result.current.attendance?.status).toBe("PENDING");

    await advance(POLL_INTERVAL_MS);
    expect(result.current.attendance?.status).toBe("PROCESSING");

    await advance(POLL_INTERVAL_MS);
    expect(result.current.attendance).toMatchObject({ status: "COMPLETED", protocol: PROTOCOL });

    await advance(POLL_INTERVAL_MS * 5);
    expect(fetchMock).toHaveBeenCalledTimes(3);
  });

  it("com o atendimento recém-aberto, espera um intervalo antes de consultar", async () => {
    const fetchMock = mockFetch(jsonResponse(anAttendance({ status: "PENDING" })));
    const { result } = renderHook(() => useAttendancePolling(1, anAttendance()));

    expect(result.current.attendance?.status).toBe("PENDING");
    await advance(POLL_INTERVAL_MS - 1);
    expect(fetchMock).not.toHaveBeenCalled();

    await advance(1);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("não consulta atendimento que já chegou encerrado", async () => {
    const fetchMock = mockFetch();
    renderHook(() => useAttendancePolling(1, anAttendance({ status: "FAILED" })));

    await advance(POLL_INTERVAL_MS * 3);

    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("mostra a falha, espera mais e volta a consultar", async () => {
    const fetchMock = mockFetch(
      new TypeError("Failed to fetch"),
      jsonResponse(anAttendance({ status: "COMPLETED", protocol: PROTOCOL })),
    );
    const { result } = renderHook(() => useAttendancePolling(1));

    await advance(0);
    expect(result.current.error).toMatch(/servidor/);

    await advance(retryDelay(1) - 1);
    expect(fetchMock).toHaveBeenCalledTimes(1);

    await advance(1);
    expect(result.current.error).toBeUndefined();
    expect(result.current.attendance?.status).toBe("COMPLETED");
  });

  it("para ao saber que o atendimento não existe", async () => {
    const fetchMock = mockFetch(jsonResponse({ title: "Atendimento não encontrado", status: 404 }, 404));
    const { result } = renderHook(() => useAttendancePolling(99));

    await advance(0);
    await advance(MAX_RETRY_DELAY_MS);

    expect(result.current.notFound).toBe(true);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("cancela a consulta ao desmontar", async () => {
    const fetchMock = mockFetch(jsonResponse(anAttendance()));
    const { unmount } = renderHook(() => useAttendancePolling(1, anAttendance()));

    unmount();
    await advance(POLL_INTERVAL_MS * 3);

    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe("retryDelay", () => {
  it("dobra a espera a cada falha seguida, até o teto", () => {
    expect(retryDelay(1)).toBe(POLL_INTERVAL_MS * 2);
    expect(retryDelay(2)).toBe(POLL_INTERVAL_MS * 4);
    expect(retryDelay(20)).toBe(MAX_RETRY_DELAY_MS);
  });
});
