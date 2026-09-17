"use client";

import { useState, type ReactNode } from "react";

import { ApiError, renewAttendance, type Attendance, type AttendanceStatus } from "@/api/attendances";
import { Spinner } from "@/components/spinner";

import { isFinal, useAttendancePolling } from "./attendance-polling";
import { knownAttendance } from "./tracked-attendances";

type AttendanceCardProps = Readonly<{
  id: number;
  onRenewed: (previousId: number, next: Attendance) => void;
  onDismiss: (id: number) => void;
}>;

const STATUS_LABEL: Record<AttendanceStatus, string> = {
  PENDING: "Aguardando processamento",
  PROCESSING: "Aguardando processamento",
  COMPLETED: "Protocolo disponível",
  FAILED: "Protocolo indisponível",
};

const STATUS_BADGE: Record<AttendanceStatus, string> = {
  PENDING: "bg-warning/15 text-warning",
  PROCESSING: "bg-warning/15 text-warning",
  COMPLETED: "bg-success/15 text-success",
  FAILED: "bg-danger/15 text-danger",
};

const timeFormat = new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "short" });

export function AttendanceCard({ id, onRenewed, onDismiss }: AttendanceCardProps) {
  const { attendance, error, notFound } = useAttendancePolling(id, knownAttendance(id));

  if (notFound) {
    return (
      <CardFrame>
        <p className="text-sm text-muted">O atendimento #{id} não foi encontrado.</p>
        <DismissButton onClick={() => onDismiss(id)} />
      </CardFrame>
    );
  }

  if (!attendance) {
    return (
      <CardFrame>
        <p className="flex items-center gap-2 text-sm text-muted">
          <Spinner /> Carregando atendimento #{id}…
        </p>
        {error && <p className="mt-2 text-sm text-danger">{error}</p>}
      </CardFrame>
    );
  }

  const { status } = attendance;

  return (
    <CardFrame>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <h3 className="truncate font-semibold text-foreground">{attendance.patientName}</h3>
          <p className="text-sm text-muted">
            CPF {attendance.maskedCpf} · aberto em {timeFormat.format(new Date(attendance.createdAt))}
          </p>
        </div>
        <span
          className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium ${STATUS_BADGE[status]}`}
        >
          {!isFinal(status) && <Spinner className="size-3" />}
          {STATUS_LABEL[status]}
        </span>
      </div>

      <div className="mt-4" aria-live="polite">
        {status === "COMPLETED" && attendance.protocol && <Protocol value={attendance.protocol} />}
        {status === "FAILED" && (
          <p className="text-sm text-foreground">
            O serviço de protocolos não respondeu depois de várias tentativas. Abra um novo atendimento para
            tentar outra vez.
          </p>
        )}
        {!isFinal(status) && (
          <p className="text-sm text-muted">
            O protocolo aparece aqui assim que ficar pronto. Não é preciso recarregar a página.
          </p>
        )}
      </div>

      {error && (
        <output className="mt-3 block text-sm text-warning">
          Não foi possível atualizar agora. A consulta continua automaticamente.
        </output>
      )}

      {isFinal(status) && (
        <FinishedActions id={id} onRenewed={onRenewed} onDismiss={onDismiss} />
      )}
    </CardFrame>
  );
}

function FinishedActions({ id, onRenewed, onDismiss }: AttendanceCardProps) {
  const [renewing, setRenewing] = useState(false);
  const [renewError, setRenewError] = useState<string>();

  const renew = async () => {
    setRenewing(true);
    setRenewError(undefined);
    try {
      onRenewed(id, await renewAttendance(id));
    } catch (error) {
      if (!(error instanceof ApiError)) {
        throw error;
      }
      setRenewError(error.message);
      setRenewing(false);
    }
  };

  return (
    <div className="mt-4 border-t border-border pt-4">
      <div className="flex flex-wrap gap-2">
        <button
          type="button"
          onClick={renew}
          disabled={renewing}
          className="inline-flex items-center gap-2 rounded-lg bg-accent px-3 py-2 text-sm font-medium text-accent-foreground transition hover:bg-accent-strong focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent disabled:opacity-70"
        >
          {renewing && <Spinner />}
          Novo atendimento para este paciente
        </button>
        <DismissButton onClick={() => onDismiss(id)} />
      </div>
      {renewError && <p className="mt-2 text-sm text-danger">{renewError}</p>}
    </div>
  );
}

function Protocol({ value }: Readonly<{ value: string }>) {
  const [copied, setCopied] = useState(false);

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(value);
      setCopied(true);
      setTimeout(() => setCopied(false), 2_000);
    } catch {
      // Sem permissão de área de transferência: o protocolo continua visível para copiar à mão.
    }
  };

  return (
    <div>
      <p className="text-xs font-medium uppercase tracking-wide text-muted">Protocolo</p>
      <div className="mt-1 flex flex-wrap items-center gap-2">
        <code className="rounded-md bg-surface-muted px-2 py-1 font-mono text-sm break-all text-foreground">
          {value}
        </code>
        <button
          type="button"
          onClick={copy}
          className="rounded-md px-2 py-1 text-sm font-medium text-accent transition hover:bg-accent/10 focus-visible:outline-2 focus-visible:outline-accent"
        >
          {copied ? "Copiado" : "Copiar"}
        </button>
      </div>
    </div>
  );
}

function DismissButton({ onClick }: Readonly<{ onClick: () => void }>) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="rounded-lg px-3 py-2 text-sm font-medium text-muted transition hover:bg-surface-muted hover:text-foreground focus-visible:outline-2 focus-visible:outline-accent"
    >
      Remover da lista
    </button>
  );
}

function CardFrame({ children }: Readonly<{ children: ReactNode }>) {
  return <article className="rounded-xl border border-border bg-surface p-5 shadow-sm">{children}</article>;
}
