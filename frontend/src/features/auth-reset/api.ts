import { ApiError, type ApiErrorPayload } from "@/lib/api-error";

export async function requestPasswordReset(email: string): Promise<void> {
  const response = await fetch("/api/v1/auth/forgot-password", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
    },
    body: JSON.stringify({ email }),
    credentials: "omit",
  });

  if (!response.ok) {
    throw await parsePublicApiError(response);
  }
}

export async function resetPassword(token: string, newPassword: string): Promise<void> {
  const response = await fetch("/api/v1/auth/reset-password", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
    },
    body: JSON.stringify({ token, newPassword }),
    credentials: "omit",
  });

  if (!response.ok) {
    throw await parsePublicApiError(response);
  }
}

async function parsePublicApiError(response: Response): Promise<ApiError> {
  let payload: ApiErrorPayload;
  try {
    payload = (await response.json()) as ApiErrorPayload;
  } catch {
    payload = {
      code: response.status >= 500 ? "INTERNAL_ERROR" : "REQUEST_FAILED",
      message: response.statusText || "Request failed",
    };
  }
  return new ApiError(response.status, payload);
}
