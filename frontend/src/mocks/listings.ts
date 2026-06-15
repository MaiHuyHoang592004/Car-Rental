import type {
  ListingCardViewModel,
  ListingDetailViewModel,
  ListingFilterState,
} from "@/features/listings/types";

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

export const LISTING_CARDS: ListingCardViewModel[] = [
  {
    id: "lst-001",
    title: "Toyota Vios 2022",
    city: "Ho Chi Minh City",
    category: "SEDAN",
    basePricePerDay: 700000,
    currency: "VND",
    seats: 5,
    transmission: "AUTO",
    fuelType: "GASOLINE",
    status: "ACTIVE",
    coverImageUrl:
      "https://images.unsplash.com/photo-1552519507-da3b142c6e3d?auto=format&fit=crop&w=1200&q=80",
    ratingLabel: "4.8 (138 trips)",
  },
  {
    id: "lst-002",
    title: "Hyundai Santa Fe 2023",
    city: "Da Nang",
    category: "SUV",
    basePricePerDay: 1250000,
    currency: "VND",
    seats: 7,
    transmission: "AUTO",
    fuelType: "DIESEL",
    status: "ACTIVE",
    coverImageUrl:
      "https://images.unsplash.com/photo-1619767886558-efdc259cde1a?auto=format&fit=crop&w=1200&q=80",
    ratingLabel: "4.7 (91 trips)",
  },
  {
    id: "lst-003",
    title: "VinFast VF8 Plus",
    city: "Hanoi",
    category: "SUV",
    basePricePerDay: 1550000,
    currency: "VND",
    seats: 5,
    transmission: "AUTO",
    fuelType: "EV",
    status: "PENDING_APPROVAL",
    coverImageUrl:
      "https://images.unsplash.com/photo-1542282088-fe8426682b8f?auto=format&fit=crop&w=1200&q=80",
    ratingLabel: "New listing",
  },
];

export const LISTING_DETAILS: ListingDetailViewModel[] = [
  {
    id: "lst-001",
    title: "Toyota Vios 2022",
    description:
      "Clean, reliable sedan prepared for city travel and airport runs. Maintained weekly with verified service history.",
    city: "Ho Chi Minh City",
    address: "District 7, Ho Chi Minh City",
    basePricePerDay: 700000,
    currency: "VND",
    dailyKmLimit: 200,
    instantBook: false,
    cancellationPolicy: "FLEXIBLE",
    status: "ACTIVE",
    coverImageUrl:
      "https://images.unsplash.com/photo-1552519507-da3b142c6e3d?auto=format&fit=crop&w=1400&q=80",
    galleryImageUrls: [
      "https://images.unsplash.com/photo-1503376780353-7e6692767b70?auto=format&fit=crop&w=900&q=80",
      "https://images.unsplash.com/photo-1549924231-f129b911e442?auto=format&fit=crop&w=900&q=80",
      "https://images.unsplash.com/photo-1493238792000-8113da705763?auto=format&fit=crop&w=900&q=80",
    ],
    vehicle: {
      make: "Toyota",
      model: "Vios",
      year: 2022,
      category: "SEDAN",
      seats: 5,
      transmission: "AUTO",
      fuelType: "GASOLINE",
    },
    extras: [
      { id: "ex-001", name: "Child Seat", price: 120000, currency: "VND" },
      { id: "ex-002", name: "Airport Delivery", price: 250000, currency: "VND" },
    ],
    availability: {
      from: "2026-06-01",
      to: "2026-06-10",
      days: [
        { date: "2026-06-01", state: "FREE" },
        { date: "2026-06-02", state: "FREE" },
        { date: "2026-06-03", state: "UNAVAILABLE" },
        { date: "2026-06-04", state: "UNAVAILABLE" },
        { date: "2026-06-05", state: "BLOCKED" },
        { date: "2026-06-06", state: "FREE" },
        { date: "2026-06-07", state: "FREE" },
        { date: "2026-06-08", state: "UNAVAILABLE" },
        { date: "2026-06-09", state: "FREE" },
        { date: "2026-06-10", state: "FREE" },
      ],
    },
  },
  {
    id: "lst-002",
    title: "Hyundai Santa Fe 2023",
    description:
      "Large SUV for family and inter-city travel with premium safety package and roomy second row.",
    city: "Da Nang",
    address: "Hai Chau District, Da Nang",
    basePricePerDay: 1250000,
    currency: "VND",
    dailyKmLimit: 250,
    instantBook: true,
    cancellationPolicy: "MODERATE",
    status: "ACTIVE",
    coverImageUrl:
      "https://images.unsplash.com/photo-1619767886558-efdc259cde1a?auto=format&fit=crop&w=1400&q=80",
    galleryImageUrls: [
      "https://images.unsplash.com/photo-1533473359331-0135ef1b58bf?auto=format&fit=crop&w=900&q=80",
      "https://images.unsplash.com/photo-1549399542-7e3f8b79c341?auto=format&fit=crop&w=900&q=80",
      "https://images.unsplash.com/photo-1616455579100-2ceaa4eb2d37?auto=format&fit=crop&w=900&q=80",
    ],
    vehicle: {
      make: "Hyundai",
      model: "Santa Fe",
      year: 2023,
      category: "SUV",
      seats: 7,
      transmission: "AUTO",
      fuelType: "DIESEL",
    },
    extras: [{ id: "ex-003", name: "Camping Kit", price: 180000, currency: "VND" }],
    availability: {
      from: "2026-06-01",
      to: "2026-06-10",
      days: [
        { date: "2026-06-01", state: "FREE" },
        { date: "2026-06-02", state: "FREE" },
        { date: "2026-06-03", state: "FREE" },
        { date: "2026-06-04", state: "FREE" },
        { date: "2026-06-05", state: "UNAVAILABLE" },
        { date: "2026-06-06", state: "UNAVAILABLE" },
        { date: "2026-06-07", state: "FREE" },
        { date: "2026-06-08", state: "BLOCKED" },
        { date: "2026-06-09", state: "FREE" },
        { date: "2026-06-10", state: "FREE" },
      ],
    },
  },
];

export function getListingDetailById(id: string): ListingDetailViewModel | null {
  return LISTING_DETAILS.find((item) => item.id === id) ?? null;
}
