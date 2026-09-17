"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { Controller, useForm } from "react-hook-form";

import { ApiError, openAttendance, type Attendance } from "@/api/attendances";
import { Spinner } from "@/components/spinner";
import { TextField } from "@/components/text-field";

import { formatCpf } from "./cpf";
import {
  openAttendanceSchema,
  toRequest,
  type OpenAttendanceFormValues,
  type OpenAttendancePayload,
} from "./open-attendance-schema";

type AttendanceFormProps = Readonly<{
  onOpened: (attendance: Attendance) => void;
}>;

const FORM_FIELDS = ["patientName", "cpf"] as const satisfies readonly (keyof OpenAttendanceFormValues)[];

function isFormField(field: string): field is (typeof FORM_FIELDS)[number] {
  return (FORM_FIELDS as readonly string[]).includes(field);
}

export function AttendanceForm({ onOpened }: AttendanceFormProps) {
  const [formError, setFormError] = useState<string>();
  const {
    register,
    control,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<OpenAttendanceFormValues, unknown, OpenAttendancePayload>({
    resolver: zodResolver(openAttendanceSchema),
    defaultValues: { patientName: "", cpf: "" },
    mode: "onTouched",
  });

  const submit = async (payload: OpenAttendancePayload) => {
    setFormError(undefined);
    try {
      const attendance = await openAttendance(toRequest(payload));
      reset();
      onOpened(attendance);
    } catch (error) {
      if (!(error instanceof ApiError)) {
        throw error;
      }
      showApiError(error);
    }
  };

  // Erro de campo vai para o campo; o resto (conflito, servidor fora) vai para o formulário.
  const showApiError = (error: ApiError) => {
    let shownOnField = false;
    for (const [field, message] of Object.entries(error.fieldErrors)) {
      if (isFormField(field)) {
        setError(field, { message });
        shownOnField = true;
      }
    }
    if (!shownOnField) {
      setFormError(error.message);
    }
  };

  return (
    <form noValidate onSubmit={handleSubmit(submit)} className="space-y-5" aria-describedby="form-error">
      <TextField
        label="Nome do paciente"
        autoComplete="name"
        placeholder="Maria da Silva"
        disabled={isSubmitting}
        error={errors.patientName?.message}
        {...register("patientName")}
      />

      <Controller
        control={control}
        name="cpf"
        render={({ field }) => (
          <TextField
            label="CPF"
            inputMode="numeric"
            autoComplete="off"
            placeholder="000.000.000-00"
            maxLength={14}
            disabled={isSubmitting}
            error={errors.cpf?.message}
            {...field}
            onChange={(event) => field.onChange(formatCpf(event.target.value))}
          />
        )}
      />

      <div id="form-error" aria-live="assertive">
        {formError && (
          <p className="rounded-lg border border-danger/30 bg-danger/10 px-3 py-2.5 text-sm text-danger">
            {formError}
          </p>
        )}
      </div>

      <button
        type="submit"
        disabled={isSubmitting}
        className="inline-flex w-full items-center justify-center gap-2 rounded-lg bg-accent px-4 py-2.5 font-medium text-accent-foreground shadow-sm transition hover:bg-accent-strong focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent disabled:cursor-not-allowed disabled:opacity-70"
      >
        {isSubmitting && <Spinner />}
        {isSubmitting ? "Registrando…" : "Abrir atendimento"}
      </button>
    </form>
  );
}
