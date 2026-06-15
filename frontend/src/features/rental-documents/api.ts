import { api } from "@/lib/api-client";
import type { RentalDocument, RentalDocumentType } from "@/features/rental-documents/types";

type PageResponse<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export async function listBookingDocuments(
  bookingId: string,
  signal?: AbortSignal,
): Promise<PageResponse<RentalDocument>> {
  return api.get<PageResponse<RentalDocument>>(`/bookings/${bookingId}/documents`, { signal });
}

export async function generateBookingDocument(
  bookingId: string,
  input: { type: RentalDocumentType; sourceEntityId?: string | null },
  idempotencyKey: string,
): Promise<RentalDocument> {
  return api.post<RentalDocument>(
    `/bookings/${bookingId}/documents`,
    {
      type: input.type,
      sourceEntityId: input.sourceEntityId ?? null,
    },
    { idempotencyKey },
  );
}

export async function getRentalDocument(documentId: string): Promise<RentalDocument> {
  return api.get<RentalDocument>(`/rental-documents/${documentId}`);
}
