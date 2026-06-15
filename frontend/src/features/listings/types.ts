export type AvailabilityDayState = "FREE" | "BLOCKED" | "UNAVAILABLE";

export type ListingFilterState = {
  query: string;
  city: string;
  pickupDate: string;
  returnDate: string;
  category: string;
  transmission: string;
  fuelType: string;
  seats: string;
  minPrice: string;
  maxPrice: string;
  instantBook: boolean;
  minRating: string;
  sort: "NEWEST" | "PRICE_ASC" | "PRICE_DESC";
};

export type ListingCardViewModel = {
  id: string;
  title: string;
  city: string;
  category: string;
  basePricePerDay: number;
  currency: "VND";
  seats: number;
  transmission: "AUTO" | "MANUAL";
  fuelType: string;
  status: "ACTIVE" | "PENDING_APPROVAL" | "SUSPENDED";
  coverImageUrl: string;
  ratingLabel: string;
};

export type ListingDetailViewModel = {
  id: string;
  title: string;
  description: string;
  city: string;
  address: string;
  basePricePerDay: number;
  currency: "VND";
  dailyKmLimit: number;
  instantBook: boolean;
  cancellationPolicy: "FLEXIBLE" | "MODERATE" | "STRICT";
  status: "ACTIVE" | "PENDING_APPROVAL" | "SUSPENDED";
  coverImageUrl: string;
  galleryImageUrls: string[];
  vehicle: {
    make: string;
    model: string;
    year: number;
    category: string;
    seats: number;
    transmission: "AUTO" | "MANUAL";
    fuelType: string;
  };
  extras: { id: string; name: string; price: number; currency: "VND" }[];
  availability: {
    from: string;
    to: string;
    days: { date: string; state: AvailabilityDayState }[];
  };
};
