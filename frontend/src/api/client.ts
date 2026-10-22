import type {
  AlertDetail,
  AlertReview,
  AlertSummary,
  ApiErrorBody,
  CreateTransactionRequest,
  Dashboard,
  ReferenceData,
  TransactionDetail,
  TransactionSubmissionResponse,
  TransactionSummary,
  UpdateAlertStatusRequest,
} from '../types/api';

export class ApiError extends Error {
  readonly status: number;
  readonly fieldErrors: Record<string, string>;

  constructor(status: number, message: string, fieldErrors: Record<string, string> = {}) {
    super(message);
    this.status = status;
    this.fieldErrors = fieldErrors;
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`/api${path}`, {
      ...init,
      headers: { 'Content-Type': 'application/json', ...init?.headers },
    });
  } catch {
    throw new ApiError(0, 'Cannot reach the backend. Is Spring Boot running on port 8080?');
  }

  if (!response.ok) {
    let body: Partial<ApiErrorBody> = {};
    try {
      body = await response.json();
    } catch {
      // Non-JSON error page (for example a proxy error); fall back to the status text.
    }
    const fallback =
      response.status >= 500 && response.status !== 503
        ? 'The server could not complete the request.'
        : response.statusText || `Request failed (${response.status})`;
    throw new ApiError(response.status, body.message ?? fallback, body.errors ?? {});
  }

  return response.json() as Promise<T>;
}

export const api = {
  dashboard: () => request<Dashboard>('/dashboard'),
  reference: () => request<ReferenceData>('/reference'),

  transactions: () => request<TransactionSummary[]>('/transactions'),
  transaction: (id: number | string) => request<TransactionDetail>(`/transactions/${id}`),
  submitTransaction: (body: CreateTransactionRequest) =>
    request<TransactionSubmissionResponse>('/transactions', {
      method: 'POST',
      body: JSON.stringify(body),
    }),

  alerts: () => request<AlertSummary[]>('/alerts'),
  alert: (id: number | string) => request<AlertDetail>(`/alerts/${id}`),
  alertReviews: (id: number | string) => request<AlertReview[]>(`/alerts/${id}/reviews`),
  updateAlertStatus: (id: number | string, body: UpdateAlertStatusRequest) =>
    request<AlertDetail>(`/alerts/${id}/status`, {
      method: 'PATCH',
      body: JSON.stringify(body),
    }),

  dbCheck: () => request<{ status: string; customers: number; transactions: number }>('/db-check'),
};
