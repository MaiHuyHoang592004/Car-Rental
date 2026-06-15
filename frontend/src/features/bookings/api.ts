import { api } from "@/lib/api-client";
import type {
  BookingCancellationPreview,
  BookingDetailViewModel,
  BookingPolicySnapshot,
  BookingPriceSnapshot,
  BookingStatus,
  BookingSummaryViewModel,
  BookingListFilterValue,
} from "@/features/bookings/types";

export type CreateBookingInput = {
  listingId: string;
  pickupDate: string;
  returnDate: string;
  pickupLocation?: string;
  returnLocation?: string;
  selectedExtras: { extraId: string; quantity: number }[];
  protectionPlanCode?: string;
};

export type CancelBookingInput = {
  reason?: string;
};

export type PatchBookingLocationsInput = {
  pickupLocation?: string;
  returnLocation?: string;
};

type RawBookingResponse = {
  id: string;
  status: BookingStatus;
  listingId: string;
  listingTitle: string;
  customerId?: string;
  hostId?: string;
  pickupDate: string;
  returnDate: string;
  pickupLocation: string;
  returnLocation: string;
  holdExpiresAt: string | null;
  hostApprovalExpiresAt?: string | null;
  totalAmount: number | string;
  currency: string;
  priceSnapshot: unknown;
  policySnapshot: unknown;
  cancellationReason?: string | null;
  rejectionReason?: string | null;
  voidRetryRequired?: boolean;
  paymentRetryState?: string | null;
  paymentStatus?: string | null;
  voidRetryLastError?: string | null;
  voidRetryCount?: number | null;
  cancellationPreview?: RawCancellationPreview | null;
  reviewEligible?: boolean;
  reviewSubmitted?: boolean;
  disputeEligible?: boolean;
  disputeSubmitted?: boolean;
  createdAt: string;
};

type RawSummaryResponse = {
  id: string;
  status: BookingStatus;
  listingId: string;
  listingTitle: string;
  pickupDate: string;
  returnDate: string;
  holdExpiresAt: string | null;
  hostApprovalExpiresAt?: string | null;
  totalAmount: number | string;
  currency: string;
  voidRetryRequired?: boolean;
  paymentRetryState?: string | null;
  paymentStatus?: string | null;
  voidRetryLastError?: string | null;
  voidRetryCount?: number | null;
  createdAt: string;
};

type RawCancellationPreview = {
  eligible: boolean;
  refundableAmount: number | string;
  penaltyAmount: number | string;
  currency: string;
  policy: "FLEXIBLE" | "MODERATE" | "STRICT";
};

type RawPageResponse<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type BookingPage = {
  content: BookingSummaryViewModel[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type CancelBookingResult = {
  id: string;
  status: BookingStatus;
  cancellationReason: string | null;
  cancellationCompleted: boolean;
  voidRetryRequired: boolean;
  code: string | null;
  paymentRetryState: string | null;
};

function toNumber(value: number | string | null | undefined): number {
  if (value === null || value === undefined) return 0;
  if (typeof value === "number") return value;
  const parsed = Number.parseFloat(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

const EMPTY_PRICE_SNAPSHOT: BookingPriceSnapshot = {
  rentalDays: 0,
  basePricePerDay: 0,
    baseAmount: 0,
    extraAmount: 0,
    protectionFee: 0,
    totalAmount: 0,
    currency: "VND",
    protectionPlanCode: null,
    protectionDeductibleAmount: null,
    protectionMaxCoverageAmount: null,
    extras: [],
};

const EMPTY_POLICY_SNAPSHOT: BookingPolicySnapshot = {
  cancellationPolicy: "FLEXIBLE",
  instantBook: false,
  dailyKmLimit: 0,
};

function parsePriceSnapshot(raw: unknown, fallbackCurrency: string): BookingPriceSnapshot {
  if (!raw || typeof raw !== "object") return { ...EMPTY_PRICE_SNAPSHOT, currency: fallbackCurrency };
  const obj = raw as Record<string, unknown>;
  const extrasRaw = Array.isArray(obj.extras) ? obj.extras : [];
  return {
    rentalDays: toNumber(obj.rentalDays as number | string),
    basePricePerDay: toNumber(obj.basePricePerDay as number | string),
    baseAmount: toNumber(obj.baseAmount as number | string),
    extraAmount: toNumber(obj.extraAmount as number | string),
    protectionFee: toNumber(obj.protectionFee as number | string),
    totalAmount: toNumber(obj.totalAmount as number | string),
    currency: typeof obj.currency === "string" ? obj.currency : fallbackCurrency,
    protectionPlanCode: typeof obj.protectionPlanCode === "string" ? obj.protectionPlanCode : null,
    protectionDeductibleAmount:
      obj.protectionDeductibleAmount == null ? null : toNumber(obj.protectionDeductibleAmount as number | string),
    protectionMaxCoverageAmount:
      obj.protectionMaxCoverageAmount == null ? null : toNumber(obj.protectionMaxCoverageAmount as number | string),
    extras: extrasRaw.map((item) => {
      const e = item as Record<string, unknown>;
      return {
        id: String(e.id ?? e.extraId ?? ""),
        name: String(e.name ?? ""),
        quantity: toNumber(e.quantity as number | string),
        unitPrice: toNumber(e.unitPrice as number | string),
        totalPrice: toNumber(e.totalPrice as number | string),
        currency: typeof e.currency === "string" ? e.currency : fallbackCurrency,
      };
    }),
  };
}

function parsePolicySnapshot(raw: unknown): BookingPolicySnapshot {
  if (!raw || typeof raw !== "object") return { ...EMPTY_POLICY_SNAPSHOT };
  const obj = raw as Record<string, unknown>;
  const policy = obj.cancellationPolicy;
  return {
    cancellationPolicy:
      policy === "FLEXIBLE" || policy === "MODERATE" || policy === "STRICT" ? policy : "FLEXIBLE",
    instantBook: Boolean(obj.instantBook),
    dailyKmLimit: toNumber(obj.dailyKmLimit as number | string),
  };
}

function mapCancellationPreview(raw: RawCancellationPreview): BookingCancellationPreview {
  return {
    eligible: Boolean(raw.eligible),
    refundableAmount: toNumber(raw.refundableAmount),
    penaltyAmount: toNumber(raw.penaltyAmount),
    currency: raw.currency,
    policy: raw.policy,
  };
}

function mapBookingResponse(raw: RawBookingResponse): BookingDetailViewModel {
  return {
    id: raw.id,
    status: raw.status,
    listingId: raw.listingId,
    listingTitle: raw.listingTitle,
    createdAt: raw.createdAt,
    customerId: raw.customerId,
    hostId: raw.hostId,
    pickupDate: raw.pickupDate,
    returnDate: raw.returnDate,
    pickupLocation: raw.pickupLocation,
    returnLocation: raw.returnLocation,
    holdExpiresAt: raw.holdExpiresAt ?? undefined,
    hostApprovalExpiresAt: raw.hostApprovalExpiresAt ?? undefined,
    totalAmount: toNumber(raw.totalAmount),
    currency: raw.currency,
    cancellationReason: raw.cancellationReason ?? undefined,
    rejectionReason: raw.rejectionReason ?? undefined,
    voidRetryRequired: raw.voidRetryRequired ?? false,
    paymentRetryState: raw.paymentRetryState ?? undefined,
    paymentStatus: raw.paymentStatus ?? undefined,
    voidRetryLastError: raw.voidRetryLastError ?? undefined,
    voidRetryCount: raw.voidRetryCount ?? undefined,
    priceSnapshot: parsePriceSnapshot(raw.priceSnapshot, raw.currency),
    policySnapshot: parsePolicySnapshot(raw.policySnapshot),
    cancellationPreview: raw.cancellationPreview ? mapCancellationPreview(raw.cancellationPreview) : undefined,
    reviewEligible: raw.reviewEligible ?? false,
    reviewSubmitted: raw.reviewSubmitted ?? false,
    disputeEligible: raw.disputeEligible ?? false,
    disputeSubmitted: raw.disputeSubmitted ?? false,
  };
}

function mapSummaryResponse(raw: RawSummaryResponse): BookingSummaryViewModel {
  return {
    id: raw.id,
    status: raw.status,
    listingId: raw.listingId,
    listingTitle: raw.listingTitle,
    createdAt: raw.createdAt,
    pickupDate: raw.pickupDate,
    returnDate: raw.returnDate,
    holdExpiresAt: raw.holdExpiresAt ?? undefined,
    hostApprovalExpiresAt: raw.hostApprovalExpiresAt ?? undefined,
    totalAmount: toNumber(raw.totalAmount),
    currency: raw.currency,
    voidRetryRequired: raw.voidRetryRequired ?? false,
    paymentRetryState: raw.paymentRetryState ?? undefined,
    paymentStatus: raw.paymentStatus ?? undefined,
    voidRetryLastError: raw.voidRetryLastError ?? undefined,
    voidRetryCount: raw.voidRetryCount ?? undefined,
  };
}

export function buildCreateBookingPayload(input: CreateBookingInput) {
  return {
    listingId: input.listingId,
    pickupDate: input.pickupDate,
    returnDate: input.returnDate,
    pickupLocation: input.pickupLocation?.trim() || null,
    returnLocation: input.returnLocation?.trim() || null,
    extras: input.selectedExtras.map((extra) => ({
      extraId: extra.extraId,
      quantity: extra.quantity,
    })),
    protectionPlanCode: input.protectionPlanCode ?? "BASIC",
  };
}

export async function createBooking(
  input: CreateBookingInput,
  idempotencyKey: string,
): Promise<BookingDetailViewModel> {
  const body = buildCreateBookingPayload(input);
  const raw = await api.post<RawBookingResponse>("/bookings", body, { idempotencyKey });
  return mapBookingResponse(raw);
}

export async function getBookingById(id: string): Promise<BookingDetailViewModel> {
  const raw = await api.get<RawBookingResponse>(`/bookings/${id}`);
  return mapBookingResponse(raw);
}

export async function getCancelPreview(id: string): Promise<BookingCancellationPreview> {
  const raw = await api.get<RawCancellationPreview>(`/bookings/${id}/cancel-preview`);
  return mapCancellationPreview(raw);
}

export async function listMyBookings(
  params: {
    status: BookingListFilterValue;
    page?: number;
    size?: number;
  },
  signal?: AbortSignal,
): Promise<BookingPage> {
  const search = new URLSearchParams();
  if (params.status !== "ALL") search.set("status", params.status);
  search.set("page", String(params.page ?? 0));
  search.set("size", String(params.size ?? 20));
  const raw = await api.get<RawPageResponse<RawSummaryResponse>>(
    `/bookings/me?${search.toString()}`,
    { signal },
  );
  return {
    content: raw.content.map(mapSummaryResponse),
    page: raw.page,
    size: raw.size,
    totalElements: raw.totalElements,
    totalPages: raw.totalPages,
  };
}

export async function patchBookingLocations(
  id: string,
  input: PatchBookingLocationsInput,
): Promise<BookingDetailViewModel> {
  const body: Record<string, string> = {};
  if (input.pickupLocation !== undefined) body.pickupLocation = input.pickupLocation;
  if (input.returnLocation !== undefined) body.returnLocation = input.returnLocation;
  const raw = await api.patch<RawBookingResponse>(`/bookings/${id}`, body);
  return mapBookingResponse(raw);
}

export async function cancelBooking(
  id: string,
  input: CancelBookingInput,
  idempotencyKey: string,
): Promise<CancelBookingResult> {
  const raw = await api.post<{
    id: string;
    status: BookingStatus;
    cancellationReason: string | null;
    cancellationCompleted?: boolean;
    voidRetryRequired?: boolean;
    code?: string | null;
    paymentRetryState?: string | null;
  }>(`/bookings/${id}/cancel`, { reason: input.reason ?? null }, { idempotencyKey });
  return {
    id: raw.id,
    status: raw.status,
    cancellationReason: raw.cancellationReason,
    cancellationCompleted: raw.cancellationCompleted ?? raw.status === "CANCELLED",
    voidRetryRequired: raw.voidRetryRequired ?? false,
    code: raw.code ?? null,
    paymentRetryState: raw.paymentRetryState ?? null,
  };
}

export async function createBookingReview(
  id: string,
  input: { rating: number; content?: string },
) {
  return api.post(`/bookings/${id}/review`, input);
}

export async function createBookingDispute(
  id: string,
  input: { category: string; reason: string; context?: string; attachmentFileIds?: string[] },
) {
  return api.post(`/bookings/${id}/dispute`, input);
}

export async function uploadDisputeAttachment(file: File): Promise<string> {
  const intent = await api.post<{
    fileId: string;
    uploadUrl: string;
  }>("/files/dispute-attachments/upload-intents", {
    contentType: file.type || "application/octet-stream",
    sizeBytes: file.size,
  });
  await fetch(intent.uploadUrl, {
    method: "PUT",
    headers: { "Content-Type": file.type || "application/octet-stream" },
    body: file,
  });
  await api.post(`/files/${intent.fileId}/finalize`, {});
  return intent.fileId;
}

export const BOOKING_STATUS_FILTERS: BookingListFilterValue[] = [
  "ALL",
  "HELD",
  "PENDING_HOST_APPROVAL",
  "CONFIRMED",
  "IN_PROGRESS",
  "COMPLETED",
  "CANCELLED",
  "REJECTED",
  "EXPIRED",
];
