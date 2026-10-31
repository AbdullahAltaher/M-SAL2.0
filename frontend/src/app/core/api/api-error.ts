import { HttpErrorResponse } from '@angular/common/http';

/** The server's error body (API.md 3.1). The message is already Arabic and shown as is. */
export interface ApiError {
  code: string;
  message: string;
  details?: { field: string; code: string; message: string }[];
  requestId?: string;
}

/** Any HTTP failure as an {@link ApiError}, with a message the user can read. */
export function toApiError(error: unknown): ApiError {
  if (error instanceof HttpErrorResponse) {
    const body = error.error as Partial<ApiError> | null;
    if (body && typeof body === 'object' && typeof body.code === 'string' && typeof body.message === 'string') {
      return body as ApiError;
    }
    if (error.status === 0) {
      return { code: 'NETWORK', message: 'لا يوجد اتصال، لا يمكن الحفظ الآن.' };
    }
  }
  return { code: 'SYS-E008', message: 'حدث خطأ غير متوقع.' };
}
