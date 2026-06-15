"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { CalendarDays, ChevronLeft, ChevronRight, MapPin, Search, SearchX } from "lucide-react";

import { ApiErrorPanel } from "@/components/rentflow/api-error-panel";
import { AppShell } from "@/components/rentflow/app-shell";
import { DEFAULT_LISTING_FILTERS, searchListings } from "@/features/listings/api";
import { ListingFiltersPanel } from "@/features/listings/listing-filters-panel";
import { ListingGrid } from "@/features/listings/listing-grid";
import { listingFilterSchema } from "@/features/listings/forms";
import type { ListingFilterState } from "@/features/listings/types";
import { ApiError } from "@/lib/api-error";
import { formatMoney } from "@/lib/formatters";

const PAGE_SIZE = 20;

type ListingsPageViewProps = {
  initialFilters?: ListingFilterState;
};

export function ListingsPageView({
  initialFilters = DEFAULT_LISTING_FILTERS,
}: ListingsPageViewProps) {
  const filterForm = useForm<ListingFilterState>({
    resolver: zodResolver(listingFilterSchema),
    defaultValues: initialFilters,
    mode: "onChange",
  });
  const filters = useWatch({ control: filterForm.control }) as ListingFilterState;
  const [page, setPage] = useState(0);
  const dateRangeInvalid =
    Boolean(filters.pickupDate) &&
    Boolean(filters.returnDate) &&
    filters.returnDate <= filters.pickupDate;

  const query = useQuery({
    queryKey: ["listings", "search", filters, page],
    queryFn: ({ signal }) => searchListings(filters, page, PAGE_SIZE, signal),
    enabled: !dateRangeInvalid,
    placeholderData: keepPreviousData,
  });

  useEffect(() => {
    const subscription = filterForm.watch(() => setPage(0));
    return () => subscription.unsubscribe();
  }, [filterForm]);

  useEffect(() => {
    filterForm.reset(initialFilters);
    setPage(0);
  }, [filterForm, initialFilters]);

  function handleReset() {
    filterForm.reset(DEFAULT_LISTING_FILTERS);
    setPage(0);
  }

  function handleSearchSubmit(event: React.FormEvent) {
    event.preventDefault();
    setPage(0);
  }

  const listings = query.data?.content ?? [];
  const totalElements = query.data?.totalElements ?? 0;
  const totalPages = query.data?.totalPages ?? 0;
  const hasActiveCity = Boolean(filters.city);
  const hasActiveQuery = Boolean(filters.query);
  const hasActivePrice = Boolean(filters.minPrice || filters.maxPrice);

  const summaryParts: string[] = [];
  if (hasActiveQuery) summaryParts.push(`cho "${filters.query}"`);
  if (hasActiveCity) summaryParts.push(`tại ${filters.city}`);
  if (filters.minPrice) summaryParts.push(`từ ${formatMoney(Number(filters.minPrice))}`);
  if (filters.maxPrice) summaryParts.push(`đến ${formatMoney(Number(filters.maxPrice))}`);

  return (
    <AppShell activePath="/listings">
      <div className="relative left-1/2 w-screen -translate-x-1/2 overflow-x-hidden border-b border-border bg-white">
        <div className="pointer-events-none absolute inset-0 overflow-hidden opacity-10">
          <div className="absolute -left-24 -top-24 h-96 w-96 rounded-full bg-primary blur-3xl" />
          <div className="absolute -bottom-24 -right-24 h-96 w-96 rounded-full bg-secondary blur-3xl" />
        </div>
        <section className="rf-shell-container relative z-10 py-10 md:py-12">
          <div className="mb-8 max-w-3xl">
            <h1 className="text-3xl font-bold leading-tight text-foreground md:text-4xl">
              Trải nghiệm hành trình tự do cùng RentFlow
            </h1>
            <p className="mt-3 text-base leading-7 text-muted-foreground">
              Hơn 5000+ xe đời mới, minh bạch thông tin, đặt xe nhanh chóng chỉ trong 3 bước.
            </p>
          </div>

          <form
            onSubmit={handleSearchSubmit}
            className="flex flex-col gap-4 rounded-xl border border-border bg-background p-5 shadow-lg md:flex-row md:items-end"
          >
            <HeroField label="Thành phố" icon={<MapPin className="h-5 w-5" />}>
              <input
                type="text"
                placeholder="Hà Nội, TP. HCM..."
                {...filterForm.register("city")}
                className="h-12 w-full rounded-t-lg border-0 border-b-2 border-border bg-muted pl-10 pr-4 text-sm text-foreground outline-none transition-colors placeholder:text-muted-foreground focus:border-primary focus:ring-0"
              />
            </HeroField>
            <HeroField label="Từ khóa" icon={<Search className="h-5 w-5" />}>
              <input
                type="text"
                placeholder="Toyota, Vios, xe gia đình..."
                {...filterForm.register("query")}
                className="h-12 w-full rounded-t-lg border-0 border-b-2 border-border bg-muted pl-10 pr-4 text-sm text-foreground outline-none transition-colors placeholder:text-muted-foreground focus:border-primary focus:ring-0"
              />
            </HeroField>
            <HeroField label="Ngày nhận" icon={<CalendarDays className="h-5 w-5" />}>
              <input
                type="date"
                {...filterForm.register("pickupDate")}
                className="h-12 w-full rounded-t-lg border-0 border-b-2 border-border bg-muted pl-10 pr-4 text-sm text-foreground outline-none transition-colors focus:border-primary focus:ring-0"
              />
            </HeroField>
            <HeroField label="Ngày trả" icon={<CalendarDays className="h-5 w-5" />}>
              <input
                type="date"
                {...filterForm.register("returnDate")}
                className="h-12 w-full rounded-t-lg border-0 border-b-2 border-border bg-muted pl-10 pr-4 text-sm text-foreground outline-none transition-colors focus:border-primary focus:ring-0"
              />
            </HeroField>
            <button
              type="submit"
              className="inline-flex h-12 w-full items-center justify-center gap-2 rounded-lg bg-primary px-8 text-sm font-semibold text-primary-foreground shadow-md transition-transform active:scale-95 md:w-auto"
            >
              <Search className="h-4 w-4" />
              Tìm kiếm
            </button>
          </form>
        </section>
      </div>

      <section className="grid grid-cols-1 gap-8 py-8 lg:grid-cols-12">
        <div className="lg:col-span-3">
          <div className="lg:sticky lg:top-24">
            <ListingFiltersPanel form={filterForm} onReset={handleReset} />
          </div>
        </div>

        <div className="min-w-0 lg:col-span-9">
          <div className="mb-6 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <p className="text-sm text-muted-foreground">
              Tìm thấy{" "}
              <span className="font-bold text-foreground">
                {totalElements.toLocaleString("vi-VN")}
              </span>{" "}
              xe phù hợp{summaryParts.length > 0 ? ` ${summaryParts.join(", ")}` : ""}
            </p>
            <label className="flex items-center gap-2 text-xs font-medium text-muted-foreground">
              Sắp xếp:
              <select
                {...filterForm.register("sort")}
                className="border-0 bg-transparent text-sm font-semibold text-foreground focus:outline-none focus:ring-0"
              >
                <option value="NEWEST">Mới nhất</option>
                <option value="PRICE_ASC">Giá thấp đến cao</option>
                <option value="PRICE_DESC">Giá cao đến thấp</option>
              </select>
            </label>
          </div>

          {dateRangeInvalid ? (
            <div className="mb-5 flex items-center gap-2 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
              <SearchX className="h-4 w-4 shrink-0" />
              Ngày trả xe phải sau ngày nhận xe. Vui lòng chọn lại ngày.
            </div>
          ) : null}

          {query.isLoading ? (
            <div className="grid gap-6 md:grid-cols-2 xl:grid-cols-3">
              {Array.from({ length: 6 }).map((_, i) => (
                <div key={i} className="animate-pulse overflow-hidden rounded-xl border border-border bg-white">
                  <div className="aspect-[4/3] bg-muted" />
                  <div className="space-y-3 p-4">
                    <div className="h-4 w-3/4 rounded bg-muted" />
                    <div className="h-3 w-1/2 rounded bg-muted" />
                    <div className="h-3 w-2/3 rounded bg-muted" />
                  </div>
                </div>
              ))}
            </div>
          ) : null}

          {query.isError ? (
            <ApiErrorPanel error={query.error instanceof ApiError ? query.error : undefined} />
          ) : null}

          {!query.isLoading && !query.isError ? (
            <ListingGrid listings={dateRangeInvalid ? [] : listings} onReset={handleReset} />
          ) : null}

          {!dateRangeInvalid && totalPages > 1 ? (
            <div className="mt-8 flex items-center justify-center gap-2">
              <PageButton
                disabled={page === 0 || query.isFetching}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
              >
                <ChevronLeft className="h-4 w-4" />
              </PageButton>
              {Array.from({ length: Math.min(totalPages, 3) }).map((_, index) => (
                <PageButton key={index} active={page === index} onClick={() => setPage(index)}>
                  {index + 1}
                </PageButton>
              ))}
              {totalPages > 3 ? (
                <>
                  <span className="px-2 text-muted-foreground">...</span>
                  <PageButton onClick={() => setPage(totalPages - 1)} active={page === totalPages - 1}>
                    {totalPages}
                  </PageButton>
                </>
              ) : null}
              <PageButton
                disabled={page >= totalPages - 1 || query.isFetching}
                onClick={() => setPage((p) => p + 1)}
              >
                <ChevronRight className="h-4 w-4" />
              </PageButton>
            </div>
          ) : null}
        </div>
      </section>

      <section className="relative left-1/2 mt-8 w-screen -translate-x-1/2 overflow-x-hidden bg-[#00174b] py-10">
        <div className="rf-shell-container flex flex-col gap-6 md:flex-row md:items-center md:justify-between">
          <div className="text-white">
            <h2 className="text-3xl font-bold">Bạn cần hỗ trợ tìm xe?</h2>
            <p className="mt-2 max-w-2xl text-sm leading-6 text-white/72">
              Để lại thông tin, RentFlow sẽ tư vấn xe phù hợp nhất với nhu cầu của bạn.
            </p>
          </div>
          <form className="flex w-full flex-col gap-2 sm:flex-row md:w-auto">
            <input
              type="email"
              placeholder="Email của bạn"
              className="h-12 flex-1 rounded-lg border-0 bg-white px-4 text-sm text-foreground outline-none focus:ring-2 focus:ring-primary md:w-80"
            />
            <button
              type="button"
              className="h-12 rounded-lg bg-primary px-7 text-sm font-semibold text-primary-foreground shadow-md transition-opacity hover:opacity-90"
            >
              Gửi yêu cầu
            </button>
          </form>
        </div>
      </section>
    </AppShell>
  );
}

function HeroField({
  label,
  icon,
  children,
}: {
  label: string;
  icon: React.ReactNode;
  children: React.ReactNode;
}) {
  return (
    <label className="w-full flex-1 space-y-1">
      <span className="ml-1 text-xs font-medium text-muted-foreground">{label}</span>
      <span className="relative flex items-center">
        <span className="pointer-events-none absolute left-3 text-muted-foreground">{icon}</span>
        {children}
      </span>
    </label>
  );
}

function PageButton({
  children,
  active = false,
  disabled = false,
  onClick,
}: {
  children: React.ReactNode;
  active?: boolean;
  disabled?: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      disabled={disabled}
      onClick={onClick}
      className={[
        "flex h-10 w-10 items-center justify-center rounded-full border text-sm font-semibold transition-colors disabled:opacity-50",
        active
          ? "border-primary bg-primary text-primary-foreground shadow-md"
          : "border-border bg-background text-muted-foreground hover:bg-muted",
      ].join(" ")}
    >
      {children}
    </button>
  );
}
