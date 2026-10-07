import type { FieldValues, Path, UseFormSetError } from 'react-hook-form';

import { errorMessage, toApiError } from './errors';

/**
 * Copies server-side field errors onto the form (only fields the form knows about) and returns a message for the
 * form-level error banner.
 */
export function applyServerErrors<T extends FieldValues>(
  error: unknown,
  setError: UseFormSetError<T>,
  knownFields: readonly Path<T>[],
): string {
  const apiError = toApiError(error);
  for (const fieldError of apiError?.fieldErrors ?? []) {
    if ((knownFields as readonly string[]).includes(fieldError.field)) {
      setError(fieldError.field as Path<T>, { type: 'server', message: fieldError.message });
    }
  }
  return errorMessage(error);
}
