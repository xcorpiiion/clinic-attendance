import type { components } from "./schema";

export type Attendance = components["schemas"]["AttendanceResponse"];
export type AttendanceStatus = Attendance["status"];
export type OpenAttendanceRequest = components["schemas"]["OpenAttendanceRequest"];
type ApiProblem = components["schemas"]["ApiProblem"];

const BASE_PATH = "/api/attendances";

/** Status 0 quando a requisição nem chegou ao servidor. */
export const NETWORK_ERROR_STATUS = 0;

export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
    readonly fieldErrors: Readonly<Record<string, string>> = {},
  ) {
    super(message);
    this.name = "ApiError";
  }

  get isNetworkError(): boolean {
    return this.status === NETWORK_ERROR_STATUS;
  }
}

export function openAttendance(request: OpenAttendanceRequest): Promise<Attendance> {
  return send<Attendance>(BASE_PATH, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request),
  });
}

export function renewAttendance(previousId: number): Promise<Attendance> {
  return send<Attendance>(`${BASE_PATH}/${previousId}/renewals`, { method: "POST" });
}

export function getAttendance(id: number, signal?: AbortSignal): Promise<Attendance> {
  return send<Attendance>(`${BASE_PATH}/${id}`, { signal });
}

async function send<T>(url: string, init: RequestInit): Promise<T> {
  let response: Response;
  try {
    response = await fetch(url, {
      ...init,
      cache: "no-store",
      headers: { Accept: "application/json", ...init.headers },
    });
  } catch (error) {
    if (init.signal?.aborted) {
      throw error;
    }
    throw new ApiError(
      NETWORK_ERROR_STATUS,
      "Não foi possível falar com o servidor. Verifique sua conexão.",
    );
  }

  if (!response.ok) {
    throw await toApiError(response);
  }
  return (await response.json()) as T;
}

async function toApiError(response: Response): Promise<ApiError> {
  const problem = await readProblem(response);
  const message =
    problem?.detail && response.status < 500
      ? problem.detail
      : "O servidor não conseguiu atender agora. Tente novamente em instantes.";
  return new ApiError(response.status, message, problem?.errors ?? {});
}

async function readProblem(response: Response): Promise<Partial<ApiProblem> | null> {
  try {
    return (await response.json()) as Partial<ApiProblem>;
  } catch {
    // Um proxy fora do ar responde HTML ou corpo vazio, não o JSON da API.
    return null;
  }
}
