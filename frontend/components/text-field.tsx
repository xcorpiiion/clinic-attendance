import { useId, type ComponentPropsWithRef } from "react";

type TextFieldProps = Readonly<ComponentPropsWithRef<"input">> & Readonly<{
  label: string;
  error?: string;
  hint?: string;
}>;

export function TextField({ label, error, hint, className, ...inputProps }: TextFieldProps) {
  const id = useId();
  const hintId = `${id}-hint`;
  const errorId = `${id}-error`;
  const describedBy = [hint && hintId, error && errorId].filter(Boolean).join(" ") || undefined;

  return (
    <div className={className}>
      <label htmlFor={id} className="block text-sm font-medium text-foreground">
        {label}
      </label>
      <input
        id={id}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy}
        className="mt-1.5 block w-full rounded-lg border border-border bg-surface px-3 py-2.5 text-base text-foreground shadow-sm outline-none transition placeholder:text-muted focus:border-accent focus:ring-3 focus:ring-accent/20 aria-invalid:border-danger aria-invalid:focus:ring-danger/20 disabled:opacity-60"
        {...inputProps}
      />
      {hint && !error && (
        <p id={hintId} className="mt-1.5 text-sm text-muted">
          {hint}
        </p>
      )}
      {error && (
        <p id={errorId} className="mt-1.5 text-sm text-danger">
          {error}
        </p>
      )}
    </div>
  );
}
