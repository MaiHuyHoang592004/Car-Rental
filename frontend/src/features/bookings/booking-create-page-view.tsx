"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useMemo, useRef, useState } from "react";
import { useForm } from "react-hook-form";
import { toast } from "sonner";
import { CalendarDays, MapPin, PlusCircle, ShieldCheck } from "lucide-react";

import { AppShell } from "@/components/rentflow/app-shell";
import { ApiErrorPanel } from "@/components/rentflow/api-error-panel";
import { BookingSectionCard } from "@/features/bookings/booking-section-card";
import { BookingPriceSummary } from "@/features/bookings/booking-price-summary";
import { createBooking, type CreateBookingInput } from "@/features/bookings/api";
import { getTodayIsoDate } from "@/features/bookings/date-utils";
import {
  deriveBookingEligibility,
  mapBookingCreateError,
  type BookingBlocker,
} from "@/features/bookings/eligibility";
import { bookingCreateSchema, type BookingCreateFormState } from "@/features/bookings/forms";
import { useAuth } from "@/features/auth/auth-context";
import { getListingDetailById } from "@/features/listings/api";
import { listProtectionPlans } from "@/features/protection/api";
import { getProfile } from "@/features/profile/api";
import { ApiError } from "@/lib/api-error";
import { handleApiError } from "@/lib/handle-api-error";
import { newIdempotencyKey } from "@/lib/idempotency";

type BlockerMessageState = {
  blocker: BookingBlocker;
  message: string;
};

type BookingCreatePageViewProps = {
  listingId: string;
  isGuest: boolean;
  initialPickupDate?: string;
  initialReturnDate?: string;
};

export function BookingCreatePageView({
  listingId,
  isGuest,
  initialPickupDate = "",
  initialReturnDate = "",
}: BookingCreatePageViewProps) {
  const router = useRouter();
  const queryClient = useQueryClient();
  const auth = useAuth();
  const { data: listing } = useQuery({
    queryKey: ["listings", listingId],
    queryFn: () => getListingDetailById(listingId),
  });
  const profileQuery = useQuery({
    queryKey: ["profile"],
    queryFn: getProfile,
    enabled: !isGuest && auth.status === "authenticated",
  });
  const protectionPlansQuery = useQuery({
    queryKey: ["protection-plans"],
    queryFn: ({ signal }) => listProtectionPlans(signal),
  });
  const idempotencyKeyRef = useRef<string>(newIdempotencyKey());
  const form = useForm<BookingCreateFormState>({
    resolver: zodResolver(bookingCreateSchema),
    defaultValues: {
      pickupDate: initialPickupDate,
      returnDate: initialReturnDate,
      pickupLocation: "",
      returnLocation: "",
      selectedExtraIds: [],
      protectionPlanCode: "BASIC",
    },
  });
  const [overlap, setOverlap] = useState<string | null>(null);
  const [submitError, setSubmitError] = useState<ApiError | null>(null);
  const [blockerMessage, setBlockerMessage] = useState<BlockerMessageState | null>(null);
  const [extraQuantities, setExtraQuantities] = useState<Record<string, number>>({});
  const selectedExtraIds = form.watch("selectedExtraIds");
  const pickupDate = form.watch("pickupDate");
  const returnDate = form.watch("returnDate");
  const protectionPlanCode = form.watch("protectionPlanCode");
  const errors = form.formState.errors;
  const selectedExtras = useMemo(() => {
    if (!listing) return [];
    return listing.extras.filter((extra) => selectedExtraIds.includes(extra.id));
  }, [listing, selectedExtraIds]);
  const listingData = listing!;
  const profile = profileQuery.data ?? (
    auth.user
      ? {
          id: auth.user.id,
          email: auth.user.email,
          emailVerified: auth.user.emailVerified,
          roles: auth.user.roles.filter(
            (role): role is "CUSTOMER" | "HOST" | "ADMIN" =>
              role === "CUSTOMER" || role === "HOST" || role === "ADMIN",
          ),
          fullName: auth.user.fullName,
          phone: auth.user.phone ?? "",
          dateOfBirth: auth.user.dateOfBirth ?? "",
          addressLine: auth.user.addressLine ?? "",
          driverVerificationStatus: auth.user.driverVerificationStatus as
            | "NOT_SUBMITTED"
            | "PENDING"
            | "APPROVED"
            | "REJECTED"
            | "EXPIRED",
        }
      : null
  );
  const eligibility = profile ? deriveBookingEligibility(profile) : null;
  const canBook = isGuest ? false : (eligibility?.canBook ?? true);
  const holdBannerMessage = canBook
    ? "Hệ thống sẽ giữ xe trong 15 phút sau khi bạn gửi yêu cầu."
    : "Sau khi hoàn tất xác minh, hệ thống sẽ giữ xe trong 15 phút để bạn thanh toán.";

  const createMutation = useMutation({
    mutationFn: (input: CreateBookingInput) => createBooking(input, idempotencyKeyRef.current),
    onSuccess: (booking) => {
      queryClient.invalidateQueries({ queryKey: ["bookings", "me"] });
      queryClient.setQueryData(["bookings", booking.id], booking);
      router.push(`/bookings/${booking.id}`);
    },
    onError: (err: unknown) =>
      handleApiError(err, {
        onCode: {
          EMAIL_NOT_VERIFIED: applyKnownBookingError,
          DRIVER_VERIFICATION_REQUIRED: applyKnownBookingError,
          DRIVER_VERIFICATION_PENDING: applyKnownBookingError,
          DRIVER_VERIFICATION_REJECTED: applyKnownBookingError,
          BOOKING_OVERLAP_CUSTOMER: applyKnownBookingError,
          LISTING_NOT_AVAILABLE: applyKnownBookingError,
          IDEMPOTENCY_KEY_REQUIRED: applyKnownBookingError,
          RATE_LIMIT_EXCEEDED: applyKnownBookingError,
          IDEMPOTENCY_KEY_CONFLICT: () => {
            idempotencyKeyRef.current = newIdempotencyKey();
            toast.error("Yeu cau da thay doi, vui long submit lai");
          },
        },
        onFieldError: (field, message) => {
          if (
            field === "pickupDate" ||
            field === "returnDate" ||
            field === "pickupLocation" ||
            field === "returnLocation"
          ) {
            form.setError(field, { message });
          }
        },
        onUnknown: (e) => setSubmitError(e),
        onNetwork: () => setSubmitError(ApiError.network()),
      }),
  });

  function applyKnownBookingError(error: ApiError) {
    const presentation = mapBookingCreateError(error);
    if (!presentation) {
      setSubmitError(error);
      return;
    }
    if (presentation.kind === "blocker") {
      setBlockerMessage({
        blocker: presentation.blocker,
        message: presentation.message,
      });
      return;
    }
    if (presentation.kind === "overlap") {
      setOverlap(presentation.message);
      return;
    }
    form.setError("root", { message: presentation.message });
  }

  function toggleExtra(extraId: string) {
    const next = selectedExtraIds.includes(extraId)
      ? selectedExtraIds.filter((id) => id !== extraId)
      : [...selectedExtraIds, extraId];
    form.setValue("selectedExtraIds", next, { shouldDirty: true });
  }

  function handleSubmit(values: BookingCreateFormState) {
    if (isGuest) {
      form.setError("root", { message: "Ban can dang nhap truoc khi tao booking." });
      return;
    }
    if (!canBook) {
      return;
    }
    setOverlap(null);
    setSubmitError(null);
    setBlockerMessage(null);
    createMutation.mutate({
      listingId: listingData.id,
      pickupDate: values.pickupDate,
      returnDate: values.returnDate,
      pickupLocation: values.pickupLocation || undefined,
      returnLocation: values.returnLocation || undefined,
      protectionPlanCode: values.protectionPlanCode || "BASIC",
      selectedExtras: values.selectedExtraIds.map((extraId) => ({
        extraId,
        quantity: extraQuantities[extraId] ?? 1,
      })),
    });
  }

  if (!listing) {
    return (
      <AppShell activePath="/listings">
        <section className="rounded-xl border border-dashed border-border bg-card p-10 text-center">
          <h1 className="text-2xl font-bold text-foreground">Khong tim thay xe</h1>
          <p className="mt-2 text-sm text-muted-foreground">
            Xe nay hien khong kha dung hoac da bi go khoi he thong.
          </p>
        </section>
      </AppShell>
    );
  }

  return (
    <AppShell activePath="/listings">
      <div className="space-y-6">
        <section className="rf-section-card p-6 md:p-8">
          <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
            <div>
              <p className="text-sm font-semibold uppercase tracking-[0.2em] text-primary/80">
                Hoàn tất đặt xe
              </p>
              <h1 className="mt-2 text-3xl font-bold text-foreground md:text-4xl">Dat xe</h1>
              <p className="mt-2 max-w-2xl text-sm leading-6 text-muted-foreground">
                Màn này giữ nguyên flow tạo booking và idempotency hiện tại, chỉ tinh chỉnh cấu trúc hiển thị cho dễ theo dõi hơn.
              </p>
            </div>
          <Link
            href={`/listings/${listingData.id}`}
            className="inline-flex items-center gap-1.5 rounded-full border border-border bg-background px-4 py-2 text-sm font-medium text-muted-foreground transition-colors hover:text-foreground"
          >
            Quay lai
          </Link>
          </div>
        </section>

        <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_360px]">
          <div className="space-y-5">
            <div className="rounded-2xl border border-amber-300 bg-amber-50 px-4 py-3 text-sm text-amber-900">
              {holdBannerMessage}
            </div>

            {isGuest ? (
              <section className="rounded-xl border border-amber-200 bg-amber-50 p-4">
                <p className="text-sm text-amber-900">Ban can dang nhap de hoan tat dat xe.</p>
                <Link
                  href={`/login?next=/listings/${listingData.id}/book`}
                  className="mt-3 inline-flex rounded-full bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground hover:opacity-90"
                >
                  Dang nhap de dat xe
                </Link>
              </section>
            ) : null}

            {!isGuest && eligibility && !eligibility.canBook ? (
              <section className="rounded-xl border border-amber-200 bg-amber-50 p-5">
                <p className="text-lg font-semibold text-amber-950">Bạn chưa thể đặt xe</p>
                <p className="mt-2 text-sm text-amber-900">
                  Để giữ xe và thanh toán, bạn cần hoàn tất:
                </p>
                <ul className="mt-3 space-y-2 text-sm text-amber-900">
                  {eligibility.blockers.map((entry) => (
                    <li key={entry.code} className="flex items-center gap-2">
                      <span className="inline-flex size-5 items-center justify-center rounded-full border border-amber-400">
                        {entry.code === "DRIVER_VERIFICATION_PENDING" ? "…" : "!"}
                      </span>
                      <span>{entry.label}</span>
                    </li>
                  ))}
                </ul>
                <div className="mt-4 flex flex-wrap gap-3">
                  <Link
                    href={eligibility.primaryCtaHref}
                    className="inline-flex rounded-full bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground hover:opacity-90"
                  >
                    {eligibility.primaryCtaLabel}
                  </Link>
                  <Link
                    href={`/listings/${listingData.id}`}
                    className="inline-flex rounded-full border border-border bg-background px-4 py-2 text-sm font-semibold text-foreground hover:bg-muted"
                  >
                    Quay lại chi tiết xe
                  </Link>
                </div>
              </section>
            ) : null}

            {overlap ? (
              <section className="rounded-xl border border-rose-200 bg-rose-50 p-4">
                <p className="text-sm font-semibold text-rose-900">Trung booking</p>
                <p className="mt-1 text-sm text-rose-800">{overlap}</p>
                <Link
                  href="/me/bookings"
                  className="mt-3 inline-flex rounded-full bg-rose-700 px-4 py-2 text-xs font-semibold text-white hover:opacity-90"
                >
                  Xem cac booking cua ban
                </Link>
              </section>
            ) : null}

            {blockerMessage ? (
              <section className="rounded-xl border border-amber-200 bg-amber-50 p-4">
                <p className="text-sm font-semibold text-amber-900">Chua du dieu kien dat xe</p>
                <p className="mt-1 text-sm text-amber-800">{blockerMessage.message}</p>
                <Link
                  href={blockerMessage.blocker.ctaHref}
                  className="mt-3 inline-flex rounded-full bg-amber-700 px-4 py-2 text-xs font-semibold text-white hover:opacity-90"
                >
                  {blockerMessage.blocker.ctaLabel}
                </Link>
              </section>
            ) : null}

            {submitError ? <ApiErrorPanel error={submitError} /> : null}

            <BookingSectionCard
              title="Thoi gian thue"
              icon={<CalendarDays className="h-5 w-5" />}
            >
              <div className="grid gap-3 sm:grid-cols-2">
                <div>
                  <label className="mb-1.5 block text-sm font-semibold text-foreground">
                    Ngay nhan xe
                  </label>
                  <input
                    type="date"
                    {...form.register("pickupDate", {
                      onChange: () => {
                        setOverlap(null);
                        setSubmitError(null);
                        setBlockerMessage(null);
                      },
                    })}
                    min={getTodayIsoDate()}
                    className="h-12 w-full rounded-2xl border border-input bg-background px-3 text-sm text-foreground outline-none ring-primary/30 focus:ring-2"
                  />
                  {errors.pickupDate ? (
                    <p className="mt-1 text-xs text-rose-700">{errors.pickupDate.message}</p>
                  ) : null}
                </div>
                <div>
                  <label className="mb-1.5 block text-sm font-semibold text-foreground">
                    Ngay tra xe
                  </label>
                  <input
                    type="date"
                    {...form.register("returnDate", {
                      onChange: () => {
                        setOverlap(null);
                        setSubmitError(null);
                        setBlockerMessage(null);
                      },
                    })}
                    min={pickupDate || getTodayIsoDate()}
                    className="h-12 w-full rounded-2xl border border-input bg-background px-3 text-sm text-foreground outline-none ring-primary/30 focus:ring-2"
                  />
                  {errors.returnDate ? (
                    <p className="mt-1 text-xs text-rose-700">{errors.returnDate.message}</p>
                  ) : null}
                </div>
              </div>
            </BookingSectionCard>

            <BookingSectionCard
              title="Dia diem nhan / tra xe"
              icon={<MapPin className="h-5 w-5" />}
            >
              <div className="grid gap-3 sm:grid-cols-2">
                <div>
                  <label className="mb-1.5 block text-sm font-semibold text-foreground">
                    Dia diem nhan xe
                  </label>
                  <input
                    type="text"
                    {...form.register("pickupLocation")}
                    placeholder={listingData.address}
                    className="h-12 w-full rounded-2xl border border-input bg-background px-3 text-sm text-foreground placeholder:text-muted-foreground outline-none ring-primary/30 focus:ring-2"
                  />
                </div>
                <div>
                  <label className="mb-1.5 block text-sm font-semibold text-foreground">
                    Dia diem tra xe
                  </label>
                  <input
                    type="text"
                    {...form.register("returnLocation")}
                    placeholder={listingData.address}
                    className="h-12 w-full rounded-2xl border border-input bg-background px-3 text-sm text-foreground placeholder:text-muted-foreground outline-none ring-primary/30 focus:ring-2"
                  />
                </div>
              </div>
            </BookingSectionCard>

            <BookingSectionCard
              title="Goi bao ve"
              icon={<ShieldCheck className="h-5 w-5" />}
            >
              <div className="grid gap-3 md:grid-cols-3">
                {(protectionPlansQuery.data ?? []).map((plan) => {
                  const checked = protectionPlanCode === plan.code;
                  const feeLabel = plan.priceAmount === 0
                    ? "Da bao gom"
                    : `${plan.priceAmount.toLocaleString("vi-VN")} ${plan.priceType === "PER_DAY" ? "VND/ngay" : "VND/chuyen"}`;
                  return (
                    <label
                      key={plan.id}
                      className={`rounded-2xl border px-4 py-4 transition-colors ${
                        checked
                          ? "border-primary bg-primary/5"
                          : "border-border bg-background hover:border-primary/50"
                      }`}
                    >
                      <div className="flex items-start justify-between gap-3">
                        <div>
                          <p className="text-sm font-semibold text-foreground">{plan.name}</p>
                          <p className="mt-1 text-xs text-muted-foreground">{feeLabel}</p>
                        </div>
                        <input
                          type="radio"
                          value={plan.code}
                          {...form.register("protectionPlanCode")}
                          className="mt-1 size-4 accent-primary"
                        />
                      </div>
                      <p className="mt-3 text-xs leading-5 text-muted-foreground">{plan.description}</p>
                      <p className="mt-3 text-xs font-semibold text-foreground">
                        Mien thuong {plan.deductibleAmount.toLocaleString("vi-VN")} VND
                      </p>
                    </label>
                  );
                })}
              </div>
            </BookingSectionCard>

            {listingData.extras.length > 0 ? (
              <BookingSectionCard
                title="Dich vu them"
                icon={<PlusCircle className="h-5 w-5" />}
              >
                <div className="grid gap-3 md:grid-cols-2">
                  {listingData.extras.map((extra) => {
                    const checked = selectedExtraIds.includes(extra.id);
                    const quantity = extraQuantities[extra.id] ?? (checked ? 1 : 0);
                    return (
                      <label
                        key={extra.id}
                        className={`rounded-2xl border px-4 py-4 transition-colors ${
                          checked
                            ? "border-primary bg-primary/5"
                            : "border-border bg-background hover:border-primary/50"
                        }`}
                      >
                        <div className="flex items-start justify-between gap-3">
                          <div className="space-y-1">
                            <span className="text-sm font-semibold text-foreground">
                              {extra.name}
                            </span>
                            <p className="text-xs text-muted-foreground">
                              Tien ich bo sung cho chuyen di.
                            </p>
                          </div>
                          <input
                            type="checkbox"
                            checked={checked}
                            onChange={() => {
                              toggleExtra(extra.id);
                              setExtraQuantities((current) => ({
                                ...current,
                                [extra.id]: checked ? 0 : 1,
                              }));
                            }}
                            className="mt-1 size-4 rounded border-input accent-primary"
                          />
                        </div>
                        {checked ? (
                          <div className="mt-3 flex items-center gap-2">
                            <button
                              type="button"
                              onClick={() =>
                                setExtraQuantities((current) => ({
                                  ...current,
                                  [extra.id]: Math.max(1, quantity - 1),
                                }))
                              }
                              className="h-8 w-8 rounded-full border border-border text-sm font-bold"
                            >
                              -
                            </button>
                            <span className="min-w-8 text-center text-sm font-semibold">{quantity}</span>
                            <button
                              type="button"
                              onClick={() =>
                                setExtraQuantities((current) => ({
                                  ...current,
                                  [extra.id]: Math.min(5, quantity + 1),
                                }))
                              }
                              className="h-8 w-8 rounded-full border border-border text-sm font-bold"
                            >
                              +
                            </button>
                          </div>
                        ) : null}
                        <div className="mt-4 text-sm font-semibold text-foreground">
                          {(extra.price * Math.max(1, quantity || 1)).toLocaleString("vi-VN")} {extra.currency}
                        </div>
                      </label>
                    );
                  })}
                </div>
                {selectedExtras.length > 0 ? (
                  <p className="mt-2 text-xs text-muted-foreground">
                    Da chon {selectedExtras.length} dich vu them.
                  </p>
                ) : null}
              </BookingSectionCard>
            ) : null}

            {errors.root ? (
              <p className="rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-800">
                {errors.root.message}
              </p>
            ) : null}
          </div>

          <div className="lg:sticky lg:top-24">
            <BookingPriceSummary
              listing={listingData}
              pickupDate={pickupDate}
              returnDate={returnDate}
              selectedExtras={extraQuantities}
              onBook={form.handleSubmit(handleSubmit)}
              isPending={createMutation.isPending}
              submitLabel={canBook ? "Giữ xe trong 15 phút" : "Hoàn tất xác minh để đặt xe"}
              submitDisabled={!canBook}
              helperText={holdBannerMessage}
            />
          </div>
        </div>
      </div>
    </AppShell>
  );
}
