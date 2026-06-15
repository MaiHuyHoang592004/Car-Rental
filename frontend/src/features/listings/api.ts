import { api } from "@/lib/api-client";
import type { ListingCardViewModel, ListingFilterState, ListingDetailViewModel } from "@/features/listings/types";

const FALLBACK_COVER_IMAGE_URL =
  "https://images.unsplash.com/photo-1492144534655-ae79c964c9d7?auto=format&fit=crop&w=1200&q=80";

export const DEFAULT_LISTING_FILTERS: ListingFilterState = {
  query: "",
  city: "",
  pickupDate: "",
  returnDate: "",
  category: "ALL",
  transmission: "ALL",
  fuelType: "ALL",
  seats: "",
  minPrice: "",
  maxPrice: "",
  instantBook: false,
  minRating: "",
  sort: "NEWEST",
};

type ListingSearchParamsInput = Record<string, string | string[] | undefined>;

type RawListingSearchResponse = {
  id: string;
  title: string;
  city: string;
  category: string | null;
  basePricePerDay: number | string;
  currency: string | null;
  seats: number | null;
  transmission: "AUTO" | "MANUAL" | null;
  fuelType: string | null;
  coverPhotoUrl: string | null;
  ratingAverage: number | string | null;
};

export type ListingSearchPage = {
  content: ListingCardViewModel[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

function toNumber(value: number | string | null | undefined): number {
  if (value === null || value === undefined) return 0;
  if (typeof value === "number") return value;
  const parsed = Number.parseFloat(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

function addIfPresent(params: URLSearchParams, key: string, value: string) {
  const trimmed = value.trim();
  if (trimmed) {
    params.set(key, trimmed);
  }
}

function firstParamValue(value: string | string[] | undefined): string {
  return Array.isArray(value) ? (value[0] ?? "") : (value ?? "");
}

function normalizeFilterValue(value: string | string[] | undefined): string {
  return firstParamValue(value).trim();
}

export function parseListingFiltersFromSearchParams(
  searchParams: ListingSearchParamsInput,
): ListingFilterState {
  const sort = normalizeFilterValue(searchParams.sort);
  return {
    query: normalizeFilterValue(searchParams.query),
    city: normalizeFilterValue(searchParams.city),
    pickupDate: normalizeFilterValue(searchParams.pickupDate),
    returnDate: normalizeFilterValue(searchParams.returnDate),
    category:
      normalizeFilterValue(searchParams.categories) ||
      normalizeFilterValue(searchParams.category) ||
      DEFAULT_LISTING_FILTERS.category,
    transmission:
      normalizeFilterValue(searchParams.transmission) || DEFAULT_LISTING_FILTERS.transmission,
    fuelType: normalizeFilterValue(searchParams.fuelType) || DEFAULT_LISTING_FILTERS.fuelType,
    seats: normalizeFilterValue(searchParams.seats),
    minPrice: normalizeFilterValue(searchParams.minPrice),
    maxPrice: normalizeFilterValue(searchParams.maxPrice),
    instantBook: normalizeFilterValue(searchParams.instantBook) === "true",
    minRating: normalizeFilterValue(searchParams.minRating),
    sort:
      sort === "PRICE_ASC" || sort === "PRICE_DESC" || sort === "NEWEST"
        ? sort
        : DEFAULT_LISTING_FILTERS.sort,
  };
}

export function buildListingSearchQuery(
  filters: ListingFilterState,
  page = 0,
  size = 20,
): string {
  const params = new URLSearchParams();
  addIfPresent(params, "query", filters.query);
  addIfPresent(params, "city", filters.city);
  addIfPresent(params, "pickupDate", filters.pickupDate);
  addIfPresent(params, "returnDate", filters.returnDate);
  addIfPresent(params, "minPrice", filters.minPrice);
  addIfPresent(params, "maxPrice", filters.maxPrice);
  addIfPresent(params, "seats", filters.seats);
  addIfPresent(params, "minRating", filters.minRating);
  if (filters.instantBook) params.set("instantBook", "true");
  if (filters.category !== "ALL") params.set("categories", filters.category);
  if (filters.transmission !== "ALL") params.set("transmission", filters.transmission);
  if (filters.fuelType !== "ALL") params.set("fuelType", filters.fuelType);
  params.set("sort", filters.sort);
  params.set("page", String(page));
  params.set("size", String(size));
  return params.toString();
}

type RawPageResponse<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

type RawListingSummaryResponse = {
  id: string;
  title: string;
  city: string;
  status: string;
  basePricePerDay: number | string;
  currency: string | null;
  createdAt: string;
};

type RawListingDetailResponse = {
  id: string;
  title: string;
  description: string;
  city: string;
  address: string;
  basePricePerDay: number | string;
  currency: string | null;
  dailyKmLimit: number;
  instantBook: boolean;
  cancellationPolicy: "FLEXIBLE" | "MODERATE" | "STRICT";
  photos: string[];
  vehicleSummary: {
    category: string;
    make: string;
    model: string;
    year: number;
    transmission: string;
    fuelType: string;
    seats: number;
    status: string;
  } | null;
  extras: { id: string; name: string; price: number; currency: string | null }[];
};

function resolveCurrency(value: string | null | undefined): "VND" {
  void value;
  return "VND";
}

export function mapListingSearchResponse(raw: RawListingSearchResponse): ListingCardViewModel {
  const rating = raw.ratingAverage == null ? null : toNumber(raw.ratingAverage);
  return {
    id: raw.id,
    title: raw.title,
    city: raw.city,
    category: raw.category ?? "UNKNOWN",
    basePricePerDay: toNumber(raw.basePricePerDay),
    currency: resolveCurrency(raw.currency),
    seats: raw.seats ?? 0,
    transmission: raw.transmission ?? "AUTO",
    fuelType: raw.fuelType ?? "UNKNOWN",
    status: "ACTIVE",
    coverImageUrl: raw.coverPhotoUrl || FALLBACK_COVER_IMAGE_URL,
    ratingLabel: rating == null || rating <= 0 ? "New listing" : `${rating.toFixed(1)} rating`,
  };
}

export async function searchListings(
  filters: ListingFilterState,
  page = 0,
  size = 20,
  signal?: AbortSignal,
): Promise<ListingSearchPage> {
  const query = buildListingSearchQuery(filters, page, size);
  const raw = await api.get<RawPageResponse<RawListingSearchResponse>>(`/listings?${query}`, {
    skipAuth: true,
    signal,
  });
  return {
    content: raw.content.map(mapListingSearchResponse),
    page: raw.page,
    size: raw.size,
    totalElements: raw.totalElements,
    totalPages: raw.totalPages,
  };
}

function toListingCard(raw: RawListingSearchResponse | RawListingSummaryResponse): ListingCardViewModel {
  const rating = "ratingAverage" in raw
    ? (raw.ratingAverage == null ? null : toNumber(raw.ratingAverage))
    : null;
  const coverUrl = "coverPhotoUrl" in raw ? raw.coverPhotoUrl : null;
  return {
    id: raw.id,
    title: raw.title,
    city: raw.city,
    category: "category" in raw ? (raw.category ?? "UNKNOWN") : "UNKNOWN",
    basePricePerDay: toNumber(raw.basePricePerDay),
    currency: resolveCurrency(raw.currency),
    seats: "seats" in raw ? (raw.seats ?? 0) : 0,
    transmission: ("transmission" in raw && raw.transmission) ? raw.transmission : "AUTO",
    fuelType: "fuelType" in raw ? (raw.fuelType ?? "UNKNOWN") : "UNKNOWN",
    status: "ACTIVE",
    coverImageUrl: coverUrl || FALLBACK_COVER_IMAGE_URL,
    ratingLabel: rating == null || rating <= 0 ? "New listing" : `${rating.toFixed(1)} rating`,
  };
}

function mapListingDetail(raw: RawListingDetailResponse): ListingDetailViewModel {
  return {
    id: raw.id,
    title: raw.title,
    description: raw.description,
    city: raw.city,
    address: raw.address,
    basePricePerDay: toNumber(raw.basePricePerDay),
    currency: resolveCurrency(raw.currency),
    dailyKmLimit: raw.dailyKmLimit,
    instantBook: raw.instantBook,
    cancellationPolicy: raw.cancellationPolicy,
    status: "ACTIVE",
    coverImageUrl: raw.photos[0] || FALLBACK_COVER_IMAGE_URL,
    galleryImageUrls: raw.photos,
    vehicle: raw.vehicleSummary
      ? {
          make: raw.vehicleSummary.make,
          model: raw.vehicleSummary.model,
          year: raw.vehicleSummary.year,
          category: raw.vehicleSummary.category,
          seats: raw.vehicleSummary.seats,
          transmission: raw.vehicleSummary.transmission as "AUTO" | "MANUAL",
          fuelType: raw.vehicleSummary.fuelType,
        }
      : {
          make: "", model: "", year: 0, category: "", seats: 0, transmission: "AUTO" as const, fuelType: "",
        },
    extras: raw.extras.map((e) => ({
      id: e.id,
      name: e.name,
      price: e.price,
      currency: resolveCurrency(e.currency),
    })),
    availability: { from: "", to: "", days: [] },
  };
}

export async function getListingDetailById(listingId: string): Promise<ListingDetailViewModel | null> {
  try {
    return mapListingDetail(await api.get<RawListingDetailResponse>(`/listings/${listingId}`, { skipAuth: true }));
  } catch (error) {
    if (error instanceof Error && "response" in error) {
      const resp = (error as unknown as { response?: { status?: number } }).response;
      if (resp?.status === 404) return null;
    }
    throw error;
  }
}

export async function getFeaturedListings(limit = 3): Promise<ListingCardViewModel[]> {
  const raw = await api.get<RawPageResponse<RawListingSearchResponse>>(
    `/listings?page=0&size=${limit}`,
    { skipAuth: true },
  );
  return raw.content.map(mapListingSearchResponse);
}

export async function getPublicListings(params: string): Promise<ListingCardViewModel[]> {
  const raw = await api.get<RawPageResponse<RawListingSummaryResponse>>(`/listings?${params}`, { skipAuth: true });
  return raw.content.map(toListingCard);
}

export type SavedListingViewModel = {
  id: string;
  listingId: string;
  title: string | null;
  city: string | null;
  basePricePerDay: number;
  currency: "VND";
  coverPhotoUrl: string | null;
  createdAt: string;
};

type RawSavedListingResponse = {
  id: string;
  listingId: string;
  title: string | null;
  city: string | null;
  basePricePerDay: number | string | null;
  currency: string | null;
  coverPhotoUrl: string | null;
  createdAt: string;
};

function mapSavedListing(raw: RawSavedListingResponse): SavedListingViewModel {
  return {
    id: raw.id,
    listingId: raw.listingId,
    title: raw.title,
    city: raw.city,
    basePricePerDay: toNumber(raw.basePricePerDay),
    currency: resolveCurrency(raw.currency),
    coverPhotoUrl: raw.coverPhotoUrl,
    createdAt: raw.createdAt,
  };
}

export async function listSavedListings(signal?: AbortSignal): Promise<{
  content: SavedListingViewModel[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}> {
  const raw = await api.get<RawPageResponse<RawSavedListingResponse>>("/me/saved-listings?page=0&size=50", { signal });
  return {
    content: raw.content.map(mapSavedListing),
    page: raw.page,
    size: raw.size,
    totalElements: raw.totalElements,
    totalPages: raw.totalPages,
  };
}

export async function saveListing(listingId: string): Promise<SavedListingViewModel> {
  return mapSavedListing(await api.post<RawSavedListingResponse>(`/listings/${listingId}/save`, {}));
}

export async function unsaveListing(listingId: string): Promise<void> {
  await api.delete(`/listings/${listingId}/save`);
}
