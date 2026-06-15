import type { BookingLocationPatchFormState } from "@/features/bookings/forms";

export type BookingStatus =
  | "HELD"
  | "PENDING_HOST_APPROVAL"
  | "CONFIRMED"
  | "IN_PROGRESS"
  | "COMPLETED"
  | "CANCELLED"
  | "REJECTED"
  | "EXPIRED";

export type BookingListFilterValue = "ALL" | BookingStatus;

export type BookingListFilterState = {
  status: BookingListFilterValue;
};

export type BookingCancellationPreview = {
  eligible: boolean;
  refundableAmount: number;
  penaltyAmount: number;
  currency: string;
  policy: BookingPolicySnapshot["cancellationPolicy"];
};

export type BookingCreateFormErrors = Partial<
  Record<"pickupDate" | "returnDate" | "pickupLocation" | "returnLocation" | "form", string>
>;

export type BookingSummaryViewModel = {
  id: string;
  status: BookingStatus;
  listingId: string;
  listingTitle: string;
  createdAt: string;
  pickupDate: string;
  returnDate: string;
  totalAmount: number;
  currency: string;
  holdExpiresAt?: string;
  hostApprovalExpiresAt?: string;
  voidRetryRequired: boolean;
  paymentRetryState?: string;
  paymentStatus?: string;
  voidRetryLastError?: string;
  voidRetryCount?: number;
};

export type BookingPriceExtraViewModel = {
  id: string;
  name: string;
  quantity: number;
  unitPrice: number;
  totalPrice: number;
  currency: string;
};

export type BookingPriceSnapshot = {
  rentalDays: number;
  basePricePerDay: number;
  baseAmount: number;
  extraAmount: number;
  protectionFee?: number;
  totalAmount: number;
  currency: string;
  protectionPlanCode?: string | null;
  protectionDeductibleAmount?: number | null;
  protectionMaxCoverageAmount?: number | null;
  extras: BookingPriceExtraViewModel[];
};

export type BookingPolicySnapshot = {
  cancellationPolicy: "FLEXIBLE" | "MODERATE" | "STRICT";
  instantBook: boolean;
  dailyKmLimit: number;
};

export type BookingDetailViewModel = BookingSummaryViewModel & {
  customerId?: string;
  hostId?: string;
  pickupLocation: string;
  returnLocation: string;
  cancellationReason?: string;
  rejectionReason?: string;
  hostApprovalExpiresAt?: string;
  voidRetryRequired: boolean;
  paymentRetryState?: string;
  priceSnapshot: BookingPriceSnapshot;
  policySnapshot: BookingPolicySnapshot;
  cancellationPreview?: BookingCancellationPreview;
  reviewEligible?: boolean;
  reviewSubmitted?: boolean;
  disputeEligible?: boolean;
  disputeSubmitted?: boolean;
};

export type BookingLocationPatchPayload = Partial<BookingLocationPatchFormState>;
