"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";
import { toast } from "sonner";
import { CalendarDays, CheckCircle2, Clock } from "lucide-react";

import { ApiErrorPanel } from "@/components/rentflow/api-error-panel";
import { AppShell } from "@/components/rentflow/app-shell";
import { PageHeader } from "@/components/rentflow/page-header";
import {
  cancelBooking,
  createBookingDispute,
  createBookingReview,
  getBookingById,
  getCancelPreview,
  patchBookingLocations,
  uploadDisputeAttachment,
  type PatchBookingLocationsInput,
} from "@/features/bookings/api";
import { BookingStatusBadge } from "@/features/bookings/booking-status-badge";
import { CancelBookingDialog } from "@/features/bookings/cancel-booking-dialog";
import { EditLocationsDialog } from "@/features/bookings/edit-locations-dialog";
import type { CancelBookingFormState } from "@/features/bookings/forms";
import { HoldCountdown } from "@/features/bookings/hold-countdown";
import { LocationSummary } from "@/features/bookings/location-summary";
import { PolicySnapshotPanel } from "@/features/bookings/policy-snapshot-panel";
import { PriceSnapshotPanel } from "@/features/bookings/price-snapshot-panel";
import type {
  BookingCancellationPreview,
  BookingDetailViewModel,
  BookingStatus,
} from "@/features/bookings/types";
import { ApiError } from "@/lib/api-error";
import { getBookingStatusLabel } from "@/lib/display-labels";
import { formatDateRange, formatMoney } from "@/lib/formatters";
import { newIdempotencyKey } from "@/lib/idempotency";

const LOCATION_EDITABLE_STATUSES: BookingStatus[] = [
  "HELD",
  "PENDING_HOST_APPROVAL",
  "CONFIRMED",
];

const PAY_NOW_VISIBLE_STATUSES: BookingStatus[] = ["HELD", "PENDING_HOST_APPROVAL", "CONFIRMED"];
const MAX_EXPIRE_RETRIES = 3;
const EXPIRE_RETRY_DELAY_MS = 5000;

function getTodayDateString() {
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const day = String(now.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function formatPaymentRetryState(state?: string | null) {
  if (!state) {
    return "He thong dang xu ly hoan tien trong nen.";
  }
  if (state === "VOID_RETRY_REQUIRED") {
    return "He thong dang retry thao tac void thanh toan trong nen.";
  }
  if (state === "VOID_RETRY_EXHAUSTED") {
    return "Hệ thống đã hết lượt retry void thanh toán.";
  }
  return `Trang thai xu ly hien tai: ${state}.`;
}

const STEPS = [
  { key: "HELD", label: "Giu cho" },
  { key: "PENDING_HOST_APPROVAL", label: "Doi duyet" },
  { key: "CONFIRMED", label: "Da xac nhan" },
  { key: "IN_PROGRESS", label: "Dang thue" },
  { key: "COMPLETED", label: "Da tra xe" },
];

const FINAL_STATUSES = ["CANCELLED", "REJECTED", "EXPIRED"];

function isVoidRetryExhausted(booking: BookingDetailViewModel) {
  return (
    booking.status === "CANCELLED"
    && !booking.voidRetryRequired
    && (booking.paymentStatus === "AUTHORIZED" || booking.paymentStatus === "CAPTURED")
    && (
      booking.paymentRetryState === "VOID_RETRY_EXHAUSTED"
      || ((booking.voidRetryCount ?? 0) > 0 && Boolean(booking.voidRetryLastError))
    )
  );
}

function getStepState(
  stepKey: string,
  status: BookingStatus,
): "done" | "active" | "pending" {
  if (FINAL_STATUSES.includes(status)) return "pending";
  const stepIndex = STEPS.findIndex((s) => s.key === stepKey);
  const statusIndex = STEPS.findIndex((s) => s.key === status);
  if (statusIndex === -1) return "pending";
  if (stepIndex < statusIndex) return "done";
  if (stepIndex === statusIndex) return "active";
  return "pending";
}

type BookingDetailPageViewProps = {
  bookingId: string;
};

export function BookingDetailPageView({ bookingId }: BookingDetailPageViewProps) {
  const queryClient = useQueryClient();
  const [editOpen, setEditOpen] = useState<boolean>(false);
  const [cancelOpen, setCancelOpen] = useState<boolean>(false);
  const [reviewOpen, setReviewOpen] = useState(false);
  const [reviewContent, setReviewContent] = useState("");
  const [disputeOpen, setDisputeOpen] = useState(false);
  const [disputeReason, setDisputeReason] = useState("");
  const [disputeContext, setDisputeContext] = useState("");
  const [disputeFiles, setDisputeFiles] = useState<File[]>([]);
  const [freshCancelPreview, setFreshCancelPreview] =
    useState<BookingCancellationPreview | undefined>();
  const cancelKeyRef = useRef<string | null>(null);
  const expireRetryRef = useRef<number>(0);

  const detailQuery = useQuery({
    queryKey: ["bookings", bookingId],
    queryFn: () => getBookingById(bookingId),
  });

  const patchMutation = useMutation({
    mutationFn: (input: PatchBookingLocationsInput) =>
      patchBookingLocations(bookingId, input),
    onSuccess: () => {
      toast.success("Da cap nhat dia diem");
      queryClient.invalidateQueries({ queryKey: ["bookings", bookingId] });
    },
    onError: (error: unknown) => {
      const message =
        error instanceof ApiError ? error.message : "Cap nhat dia diem that bai";
      toast.error(message);
    },
  });

  const cancelMutation = useMutation({
    mutationFn: (input: { reason?: string; idempotencyKey: string }) =>
      cancelBooking(bookingId, { reason: input.reason }, input.idempotencyKey),
    onSuccess: (result) => {
      if (result.voidRetryRequired) {
        toast.success("Da huy don; hoan tien se duoc xu ly tiep");
      } else {
        toast.success("Da huy don");
      }
      queryClient.invalidateQueries({ queryKey: ["bookings", bookingId] });
      queryClient.invalidateQueries({ queryKey: ["bookings", "me"] });
      cancelKeyRef.current = null;
      setFreshCancelPreview(undefined);
      setCancelOpen(false);
    },
    onError: (error: unknown) => {
      if (error instanceof ApiError && error.code === "BOOKING_INVALID_STATUS") {
        queryClient.invalidateQueries({ queryKey: ["bookings", bookingId] });
      }
      const message =
        error instanceof ApiError ? error.message : "Huy don that bai";
      toast.error(message);
    },
  });

  const cancelPreviewMutation = useMutation({
    mutationFn: () => getCancelPreview(bookingId),
    onSuccess: (preview) => {
      setFreshCancelPreview(preview);
      if (!cancelKeyRef.current) {
        cancelKeyRef.current = newIdempotencyKey();
      }
      setCancelOpen(true);
    },
    onError: (error: unknown) => {
      setCancelOpen(false);
      setFreshCancelPreview(undefined);
      cancelKeyRef.current = null;
      if (
        error instanceof ApiError
        && (error.code === "BOOKING_INVALID_STATUS" || error.code === "BOOKING_NOT_FOUND")
      ) {
        queryClient.invalidateQueries({ queryKey: ["bookings", bookingId] });
      }
      if (error instanceof ApiError && error.code === "BOOKING_INVALID_STATUS") {
        toast.error("Booking không còn ở trạng thái có thể hủy. Đang tải lại chi tiết.");
      } else if (error instanceof ApiError && error.code === "BOOKING_NOT_FOUND") {
        toast.error("Không tìm thấy booking hoặc bạn không có quyền truy cập. Đang tải lại chi tiết.");
      } else {
        toast.error(error instanceof ApiError ? error.message : "Không thể tải phí hủy mới.");
      }
    },
  });

  const reviewMutation = useMutation({
    mutationFn: () => createBookingReview(bookingId, { rating: 5, content: reviewContent || undefined }),
    onSuccess: () => {
      toast.success("Da gui danh gia");
      setReviewOpen(false);
      setReviewContent("");
      queryClient.invalidateQueries({ queryKey: ["bookings", bookingId] });
    },
    onError: (error: unknown) => {
      toast.error(error instanceof ApiError ? error.message : "Gui danh gia that bai");
    },
  });

  const disputeMutation = useMutation({
    mutationFn: () =>
      Promise.all(disputeFiles.map(uploadDisputeAttachment)).then((attachmentFileIds) =>
        createBookingDispute(bookingId, {
          category: "OTHER",
          reason: disputeReason,
          context: disputeContext || undefined,
          attachmentFileIds,
        })),
    onSuccess: () => {
      toast.success("Da tao khieu nai");
      setDisputeOpen(false);
      setDisputeReason("");
      setDisputeContext("");
      setDisputeFiles([]);
      queryClient.invalidateQueries({ queryKey: ["bookings", bookingId] });
    },
    onError: (error: unknown) => {
      toast.error(error instanceof ApiError ? error.message : "Tao khieu nai that bai");
    },
  });

  const scheduleExpireRetry = useCallback(() => {
    if (expireRetryRef.current >= MAX_EXPIRE_RETRIES) {
      toast.message("Hold dang duoc xu ly, refresh trang");
      return;
    }
    expireRetryRef.current += 1;
    window.setTimeout(() => {
      queryClient.invalidateQueries({ queryKey: ["bookings", bookingId] });
    }, EXPIRE_RETRY_DELAY_MS);
  }, [bookingId, queryClient]);

  const handleHoldExpired = useCallback(() => {
    expireRetryRef.current = 0;
    queryClient.invalidateQueries({ queryKey: ["bookings", bookingId] });
    scheduleExpireRetry();
  }, [bookingId, queryClient, scheduleExpireRetry]);

  const bookingStatus = detailQuery.data?.status;
  useEffect(() => {
    if (bookingStatus && bookingStatus !== "HELD") {
      expireRetryRef.current = 0;
    }
  }, [bookingStatus]);

  if (detailQuery.isLoading) {
    return (
      <AppShell activePath="/me/bookings">
        <section className="rounded-xl border border-dashed border-border bg-card p-10 text-center">
          <p className="text-sm text-muted-foreground">Dang tai chi tiet don...</p>
        </section>
      </AppShell>
    );
  }

  if (detailQuery.isError) {
    const apiErr =
      detailQuery.error instanceof ApiError ? detailQuery.error : undefined;
    return (
      <AppShell activePath="/me/bookings">
        <ApiErrorPanel error={apiErr} />
        <div className="mt-4">
          <Link
            href="/me/bookings"
            className="rounded-full bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground transition-opacity hover:opacity-90"
          >
            Quay ve danh sach
          </Link>
        </div>
      </AppShell>
    );
  }

  const booking = detailQuery.data;
  if (!booking) {
    return null;
  }

  const canEditLocations = LOCATION_EDITABLE_STATUSES.includes(booking.status);
  const canCancel =
    booking.status === "HELD" ||
    booking.status === "PENDING_HOST_APPROVAL" ||
    (booking.status === "CONFIRMED" &&
      booking.pickupDate > getTodayDateString());
  const showPayNow = PAY_NOW_VISIBLE_STATUSES.includes(booking.status);
  const cancelHint =
    booking.status === "CONFIRMED" && !canCancel
      ? "Don da den hoac qua ngay nhan xe, khong the huy."
      : booking.status === "PENDING_HOST_APPROVAL"
        ? "Huy don nay co the kich hoat void thanh toan dang cho."
        : booking.status === "CONFIRMED"
          ? "Huy don se ap dung chinh sach hien tai."
          : null;
  const cancelDialogStatus =
    canCancel &&
    (booking.status === "HELD" ||
      booking.status === "PENDING_HOST_APPROVAL" ||
      booking.status === "CONFIRMED")
      ? booking.status
      : null;

  function openCancelDialog() {
    cancelPreviewMutation.mutate();
  }

  function handleCancelClose() {
    setCancelOpen(false);
    setFreshCancelPreview(undefined);
    cancelKeyRef.current = null;
  }

  function handleCancelConfirm(next: CancelBookingFormState) {
    const key = cancelKeyRef.current ?? newIdempotencyKey();
    cancelKeyRef.current = key;
    cancelMutation.mutate({
      reason: next.reason || undefined,
      idempotencyKey: key,
    });
  }

  function handleLocationPatch(next: PatchBookingLocationsInput) {
    patchMutation.mutate(next);
  }

  const isFinalStatus = FINAL_STATUSES.includes(booking.status);

  return (
    <AppShell activePath="/me/bookings">
      <div className="space-y-6">
        <section className="rf-section-card p-6 md:p-8">
          <PageHeader
            title="Chi tiết đặt chỗ"
            description="Theo dõi tiến trình booking, cập nhật địa điểm và xử lý thanh toán."
            actions={
              <Link
                href="/me/bookings"
                className="rounded-full border border-border bg-background px-4 py-2 text-sm font-semibold text-foreground hover:bg-accent"
              >
                Quay ve
              </Link>
            }
          />
        </section>

        <section className="rf-section-card p-5 md:p-6">
          <div className="flex flex-wrap items-start justify-between gap-4">
            <div className="min-w-0 flex-1">
              <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                {booking.listingTitle}
              </p>
              <h2 className="mt-1 text-xl font-bold text-foreground">
                {getBookingStatusLabel(booking.status)}
              </h2>
              <div className="mt-2 flex flex-wrap items-center gap-4">
                <div className="flex items-center gap-1.5 text-sm text-muted-foreground">
                  <CalendarDays className="h-4 w-4" />
                  {formatDateRange(booking.pickupDate, booking.returnDate)}
                </div>
                <p className="text-sm font-semibold text-foreground">
                  {formatMoney(booking.totalAmount, booking.currency)}
                </p>
              </div>
            </div>
            <BookingStatusBadge status={booking.status} />
          </div>

          {!isFinalStatus ? (
            <div className="mt-5 overflow-x-auto">
              <div className="flex min-w-max items-center gap-1">
                {STEPS.map((step, i) => {
                  const state = getStepState(step.key, booking.status);
                  const isLast = i === STEPS.length - 1;
                  return (
                    <div key={step.key} className="flex items-center">
                      <div className="flex flex-col items-center">
                        <div
                          className={`flex h-8 w-8 items-center justify-center rounded-full text-xs font-bold ${
                            state === "done"
                              ? "bg-green-100 text-green-700"
                              : state === "active"
                                ? "bg-primary text-primary-foreground"
                                : "bg-muted text-muted-foreground"
                          }`}
                        >
                          {state === "done" ? (
                            <CheckCircle2 className="h-4 w-4" />
                          ) : state === "active" ? (
                            <Clock className="h-4 w-4" />
                          ) : (
                            i + 1
                          )}
                        </div>
                        <p
                          className={`mt-1.5 whitespace-nowrap text-xs font-medium ${
                            state === "active"
                              ? "text-foreground"
                              : "text-muted-foreground"
                          }`}
                        >
                          {step.label}
                        </p>
                      </div>
                      {!isLast && (
                        <div
                          className={`mx-1.5 h-px w-8 ${
                            state === "done" ? "bg-green-500" : "bg-border"
                          }`}
                        />
                      )}
                    </div>
                  );
                })}
              </div>
            </div>
          ) : null}

          {booking.status === "HELD" && booking.holdExpiresAt ? (
            <div className="mt-4">
              <HoldCountdown
                key={booking.holdExpiresAt}
                expiresAt={booking.holdExpiresAt}
                onExpire={handleHoldExpired}
              />
            </div>
          ) : null}

          {booking.status === "CANCELLED" && booking.voidRetryRequired ? (
            <div className="mt-4 rounded-lg border border-amber-300 bg-amber-50 px-4 py-3 text-sm text-amber-900">
              <p className="font-semibold">Don da bi huy, nhung thanh toan van dang duoc xu ly.</p>
              <p className="mt-1">{formatPaymentRetryState(booking.paymentRetryState)}</p>
            </div>
          ) : null}

          {isVoidRetryExhausted(booking) ? (
            <div className="mt-4 rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-950">
              <p className="font-semibold">Thanh toán cần hỗ trợ xử lý thủ công</p>
              <p className="mt-1">
                Hệ thống đã hết lượt retry thao tác thanh toán; support/admin đang xử lý thủ công.
              </p>
              {booking.voidRetryLastError ? (
                <p className="mt-1 text-xs text-rose-800">
                  Lỗi cuối: {booking.voidRetryLastError}
                </p>
              ) : null}
            </div>
          ) : null}

          {booking.status === "REJECTED" && booking.rejectionReason ? (
            <div className="mt-4 rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-900">
              <p className="font-semibold">Ly do chu xe tu choi</p>
              <p className="mt-1">{booking.rejectionReason}</p>
            </div>
          ) : null}

          <div className="mt-4 flex flex-wrap gap-2">
            {showPayNow ? (
              <Link
                href={`/bookings/${booking.id}/payment`}
                className="rounded-full bg-secondary px-4 py-2 text-sm font-semibold text-secondary-foreground transition-opacity hover:opacity-90"
              >
                Thanh toan ngay
              </Link>
            ) : null}
            {booking.status === "CONFIRMED" ? (
              <Link
                href={`/bookings/${booking.id}/check-in`}
                className="rounded-full bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground transition-opacity hover:opacity-90"
              >
                Nhan xe
              </Link>
            ) : null}
            {booking.status === "IN_PROGRESS" ? (
              <Link
                href={`/bookings/${booking.id}/check-out`}
                className="rounded-full bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground transition-opacity hover:opacity-90"
              >
                Tra xe
              </Link>
            ) : null}
            <button
              type="button"
              disabled={!canEditLocations || patchMutation.isPending}
              onClick={() => setEditOpen(true)}
              className="rounded-full border border-border bg-background px-4 py-2 text-sm font-semibold text-foreground disabled:cursor-not-allowed disabled:opacity-50 hover:enabled:bg-accent"
            >
              Chinh dia diem
            </button>
            {canCancel ? (
              <button
                type="button"
                disabled={cancelMutation.isPending || cancelPreviewMutation.isPending}
                onClick={openCancelDialog}
                className="rounded-full bg-rose-600 px-4 py-2 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:opacity-50 hover:enabled:opacity-90"
              >
                {cancelPreviewMutation.isPending ? "Đang tải phí hủy..." : "Huy don"}
              </button>
            ) : null}
            {booking.reviewEligible ? (
              <button
                type="button"
                onClick={() => setReviewOpen((open) => !open)}
                className="rounded-full border border-border bg-background px-4 py-2 text-sm font-semibold text-foreground hover:bg-accent"
              >
                Danh gia chuyen di
              </button>
            ) : null}
            {booking.disputeEligible ? (
              <button
                type="button"
                onClick={() => setDisputeOpen((open) => !open)}
                className="rounded-full border border-border bg-background px-4 py-2 text-sm font-semibold text-foreground hover:bg-accent"
              >
                Tao khieu nai
              </button>
            ) : null}
          </div>
          {booking.status === "CONFIRMED" ? (
            <p className="mt-2 text-sm text-muted-foreground">
              Muon doi ngay thue, hay huy don hien tai va dat lai voi ngay moi.
            </p>
          ) : null}
          {cancelHint ? (
            <p className="mt-2 text-sm text-muted-foreground">{cancelHint}</p>
          ) : null}
        </section>

        {reviewOpen ? (
          <section className="rounded-xl border border-border bg-card p-4">
            <label className="text-sm font-semibold text-foreground">Noi dung danh gia</label>
            <textarea
              value={reviewContent}
              onChange={(event) => setReviewContent(event.target.value)}
              rows={3}
              className="mt-2 w-full rounded-lg border border-input bg-background px-3 py-2 text-sm"
            />
            <button
              type="button"
              disabled={reviewMutation.isPending}
              onClick={() => reviewMutation.mutate()}
              className="mt-3 rounded-full bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground disabled:opacity-50"
            >
              Gui danh gia 5 sao
            </button>
          </section>
        ) : null}

        {disputeOpen ? (
          <section className="rounded-xl border border-border bg-card p-4">
            <label className="text-sm font-semibold text-foreground">Ly do khieu nai</label>
            <textarea
              value={disputeReason}
              onChange={(event) => setDisputeReason(event.target.value)}
              rows={3}
              className="mt-2 w-full rounded-lg border border-input bg-background px-3 py-2 text-sm"
            />
            <label className="mt-3 block text-sm font-semibold text-foreground">Bo sung boi canh</label>
            <textarea
              value={disputeContext}
              onChange={(event) => setDisputeContext(event.target.value)}
              rows={3}
              className="mt-2 w-full rounded-lg border border-input bg-background px-3 py-2 text-sm"
            />
            <label className="mt-3 block text-sm font-semibold text-foreground">Tai lieu dinh kem</label>
            <input
              type="file"
              multiple
              accept="image/*,application/pdf"
              onChange={(event) => setDisputeFiles(Array.from(event.target.files ?? []))}
              className="mt-2 block w-full text-sm text-muted-foreground"
            />
            <button
              type="button"
              disabled={!disputeReason.trim() || disputeMutation.isPending}
              onClick={() => disputeMutation.mutate()}
              className="mt-3 rounded-full bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground disabled:opacity-50"
            >
              Gui khieu nai
            </button>
          </section>
        ) : null}

        <LocationSummary
          pickupLocation={booking.pickupLocation}
          returnLocation={booking.returnLocation}
        />

        <div className="grid gap-4 lg:grid-cols-2">
          <PriceSnapshotPanel priceSnapshot={booking.priceSnapshot} />
          <PolicySnapshotPanel policySnapshot={booking.policySnapshot} />
        </div>
      </div>

      <EditLocationsDialog
        key={`${booking.id}:${booking.pickupLocation}:${booking.returnLocation}:${editOpen ? "open" : "closed"}`}
        open={editOpen}
        initialValue={{
          pickupLocation: booking.pickupLocation,
          returnLocation: booking.returnLocation,
        }}
        onClose={() => setEditOpen(false)}
        onConfirm={handleLocationPatch}
      />

      <CancelBookingDialog
        key={`${booking.id}:${booking.status}:${cancelOpen ? "open" : "closed"}`}
        open={cancelOpen}
        status={cancelDialogStatus}
        preview={freshCancelPreview}
        onClose={handleCancelClose}
        onConfirm={handleCancelConfirm}
      />
    </AppShell>
  );
}
