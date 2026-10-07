import axios from 'axios';

/** Mirrors com.teamops.common.exception.ApiError. */
export interface ApiErrorBody {
  timestamp: string;
  status: number;
  error: string;
  code: string;
  message: string;
  path: string;
  fieldErrors?: { field: string; message: string }[];
}

function isApiErrorBody(value: unknown): value is ApiErrorBody {
  return typeof value === 'object' && value !== null && 'code' in value && 'message' in value;
}

export function toApiError(error: unknown): ApiErrorBody | null {
  if (axios.isAxiosError(error) && isApiErrorBody(error.response?.data)) {
    return error.response.data;
  }
  return null;
}

/** A message that is safe to show to the user. */
export function errorMessage(error: unknown, fallback = 'Something went wrong. Please try again.'): string {
  if (axios.isAxiosError(error) && !error.response) {
    return "Can't reach the server. Check your connection and try again.";
  }
  return toApiError(error)?.message ?? fallback;
}
