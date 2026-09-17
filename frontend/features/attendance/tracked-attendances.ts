"use client";

import { useSyncExternalStore } from "react";

import type { Attendance } from "@/api/attendances";

/**
 * Os atendimentos que esta tela acompanha, guardados no navegador para sobreviver
 * a um recarregamento. Só o id vai para o storage: nome e CPF ficam no servidor.
 *
 * O atendimento recém-aberto fica também em memória, para o card aparecer com os
 * dados na hora, sem esperar a primeira consulta.
 */
const STORAGE_KEY = "clinic:attendance-ids";
const MAX_TRACKED = 10;
const NO_IDS: readonly number[] = [];

let cachedRaw: string | null = null;
let cachedIds: readonly number[] = NO_IDS;
const knownAttendances = new Map<number, Attendance>();
const listeners = new Set<() => void>();

function readIds(): readonly number[] {
  const raw = readStorage();
  if (raw !== null && raw !== cachedRaw) {
    cachedRaw = raw;
    cachedIds = parseIds(raw);
  }
  return cachedIds;
}

function writeIds(ids: readonly number[]): void {
  cachedIds = ids;
  cachedRaw = JSON.stringify(ids);
  try {
    localStorage.setItem(STORAGE_KEY, cachedRaw);
  } catch {
    // Navegação privada ou storage cheio: a lista segue valendo só nesta aba.
  }
  listeners.forEach((listener) => listener());
}

function readStorage(): string | null {
  try {
    return localStorage.getItem(STORAGE_KEY);
  } catch {
    return null;
  }
}

function parseIds(raw: string): readonly number[] {
  try {
    const value: unknown = JSON.parse(raw);
    return Array.isArray(value) ? value.filter((id): id is number => Number.isSafeInteger(id)) : NO_IDS;
  } catch {
    return NO_IDS;
  }
}

function subscribe(listener: () => void): () => void {
  listeners.add(listener);
  // Outra aba mexeu na lista.
  const onStorage = (event: StorageEvent) => {
    if (event.key === STORAGE_KEY) {
      listener();
    }
  };
  window.addEventListener("storage", onStorage);
  return () => {
    listeners.delete(listener);
    window.removeEventListener("storage", onStorage);
  };
}

export function knownAttendance(id: number): Attendance | undefined {
  return knownAttendances.get(id);
}

/** O mais recente fica no topo; passando do limite, o mais antigo sai. */
export function trackAttendance(attendance: Attendance): void {
  knownAttendances.set(attendance.id, attendance);
  const others = readIds().filter((id) => id !== attendance.id);
  writeIds([attendance.id, ...others].slice(0, MAX_TRACKED));
}

export function untrackAttendance(id: number): void {
  knownAttendances.delete(id);
  writeIds(readIds().filter((tracked) => tracked !== id));
}

export function useTrackedAttendanceIds(): readonly number[] {
  return useSyncExternalStore(subscribe, readIds, () => NO_IDS);
}
