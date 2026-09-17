"use client";

import type { Attendance } from "@/api/attendances";

import { AttendanceCard } from "./attendance-card";
import { AttendanceForm } from "./attendance-form";
import { trackAttendance, untrackAttendance, useTrackedAttendanceIds } from "./tracked-attendances";

function replaceWithRenewal(previousId: number, next: Attendance) {
  untrackAttendance(previousId);
  trackAttendance(next);
}

export function AttendancePanel() {
  const ids = useTrackedAttendanceIds();

  return (
    <div className="grid items-start gap-8 lg:grid-cols-[minmax(0,22rem)_minmax(0,1fr)]">
      <section aria-labelledby="new-attendance" className="rounded-xl border border-border bg-surface p-6 shadow-sm">
        <h2 id="new-attendance" className="text-lg font-semibold text-foreground">
          Novo atendimento
        </h2>
        <p className="mt-1 mb-5 text-sm text-muted">Informe os dados do paciente para registrar a solicitação.</p>
        <AttendanceForm onOpened={trackAttendance} />
      </section>

      <section aria-labelledby="tracked-attendances">
        <h2 id="tracked-attendances" className="text-lg font-semibold text-foreground">
          Acompanhamento
        </h2>
        {ids.length === 0 ? (
          <p className="mt-3 rounded-xl border border-dashed border-border p-8 text-center text-sm text-muted">
            Os atendimentos abertos aparecem aqui, com o protocolo assim que ele ficar pronto.
          </p>
        ) : (
          <ul className="mt-3 space-y-3">
            {ids.map((id) => (
              <li key={id}>
                <AttendanceCard id={id} onRenewed={replaceWithRenewal} onDismiss={untrackAttendance} />
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}
