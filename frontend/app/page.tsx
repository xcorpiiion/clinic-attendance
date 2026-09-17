import { AttendancePanel } from "@/features/attendance/attendance-panel";

export default function Home() {
  return (
    <>
      <header className="border-b border-border bg-surface">
        <div className="mx-auto max-w-5xl px-4 py-5 sm:px-6">
          <p className="text-sm font-medium text-accent">Clínica · Recepção</p>
          <h1 className="text-2xl font-semibold tracking-tight text-foreground">Abertura de atendimentos</h1>
        </div>
      </header>
      <main className="mx-auto w-full max-w-5xl flex-1 px-4 py-8 sm:px-6">
        <AttendancePanel />
      </main>
    </>
  );
}
