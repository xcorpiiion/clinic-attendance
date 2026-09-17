"use client";

import { useEffect, useState } from "react";

import { ApiError, getAttendance, type Attendance, type AttendanceStatus } from "@/api/attendances";

export const POLL_INTERVAL_MS = 2_000;
export const MAX_RETRY_DELAY_MS = 30_000;

const FINAL_STATUSES: ReadonlySet<AttendanceStatus> = new Set(["COMPLETED", "FAILED"]);

export function isFinal(status: AttendanceStatus): boolean {
  return FINAL_STATUSES.has(status);
}

/** Com o servidor fora, espera cada vez mais entre as consultas: 4s, 8s, 16s... até 30s. */
export function retryDelay(consecutiveFailures: number): number {
  return Math.min(POLL_INTERVAL_MS * 2 ** consecutiveFailures, MAX_RETRY_DELAY_MS);
}

export type PollingState = {
  attendance?: Attendance;
  /** Falha da última consulta; a consulta continua sendo tentada. */
  error?: string;
  notFound: boolean;
};

/**
 * Consulta o atendimento a cada {@link POLL_INTERVAL_MS} até o status ser final.
 * A próxima consulta só é agendada quando a anterior termina, então uma resposta
 * lenta nunca acumula requisições. Desmontar o componente cancela tudo.
 */
export function useAttendancePolling(id: number, initial?: Attendance): PollingState {
  const [state, setState] = useState<PollingState>({ attendance: initial, notFound: false });
  const startsFinal = initial !== undefined && isFinal(initial.status);
  // Quem já tem o atendimento (acabou de abrir) espera um intervalo antes da primeira consulta.
  const firstDelay = initial ? POLL_INTERVAL_MS : 0;

  useEffect(() => {
    if (startsFinal) {
      return;
    }
    const controller = new AbortController();
    let timer: ReturnType<typeof setTimeout> | undefined;
    let failures = 0;

    const poll = async () => {
      try {
        const attendance = await getAttendance(id, controller.signal);
        failures = 0;
        setState({ attendance, notFound: false });
        if (!isFinal(attendance.status)) {
          timer = setTimeout(poll, POLL_INTERVAL_MS);
        }
      } catch (error) {
        if (controller.signal.aborted) {
          return;
        }
        if (error instanceof ApiError && error.status === 404) {
          setState((current) => ({ ...current, error: undefined, notFound: true }));
          return;
        }
        failures += 1;
        const message = error instanceof ApiError ? error.message : "Falha ao consultar o atendimento.";
        setState((current) => ({ ...current, error: message }));
        timer = setTimeout(poll, retryDelay(failures));
      }
    };

    timer = setTimeout(poll, firstDelay);
    return () => {
      controller.abort();
      clearTimeout(timer);
    };
  }, [id, startsFinal, firstDelay]);

  return state;
}
