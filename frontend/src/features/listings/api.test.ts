import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import {
  DEFAULT_LISTING_FILTERS,
  buildListingSearchQuery,
  getListingDetailById,
  mapListingSearchResponse,
  parseListingFiltersFromSearchParams,
  searchListings,
} from "./api";

describe("listing search api", () => {
  let fetchSpy: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    fetchSpy = vi.fn();
    vi.stubGlobal("fetch", fetchSpy);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  function jsonResponse(body: unknown): Response {
    return new Response(JSON.stringify(body), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
  }

  it("builds backend query params and skips empty ALL filters", () => {
    const query = buildListingSearchQuery(
      {
        ...DEFAULT_LISTING_FILTERS,
        city: " Hanoi ",
        pickupDate: "2026-07-01",
        returnDate: "2026-07-03",
        category: "SUV",
        transmission: "AUTO",
        fuelType: "EV",
        seats: "5",
        minPrice: "500000",
        maxPrice: "2000000",
      },
      2,
      20,
    );
    const params = new URLSearchParams(query);
    expect(params.get("city")).toBe("Hanoi");
    expect(params.get("categories")).toBe("SUV");
    expect(params.get("pickupDate")).toBe("2026-07-01");
    expect(params.get("returnDate")).toBe("2026-07-03");
    expect(params.get("transmission")).toBe("AUTO");
    expect(params.get("fuelType")).toBe("EV");
    expect(params.get("seats")).toBe("5");
    expect(params.get("minPrice")).toBe("500000");
    expect(params.get("maxPrice")).toBe("2000000");
    expect(params.get("sort")).toBe("NEWEST");
    expect(params.get("page")).toBe("2");
    expect(params.get("size")).toBe("20");
  });

  it("omits default filters", () => {
    const params = new URLSearchParams(buildListingSearchQuery(DEFAULT_LISTING_FILTERS));
    expect(params.has("city")).toBe(false);
    expect(params.has("categories")).toBe(false);
    expect(params.has("transmission")).toBe(false);
    expect(params.has("fuelType")).toBe(false);
    expect(params.get("sort")).toBe("NEWEST");
    expect(params.get("page")).toBe("0");
    expect(params.get("size")).toBe("20");
  });

  it("hydrates listing filters from URL search params", () => {
    expect(
      parseListingFiltersFromSearchParams({
        city: [" Hanoi "],
        query: "Toyota",
        categories: "SUV",
        transmission: "AUTO",
        fuelType: "EV",
        seats: "7",
        minPrice: "800000",
        maxPrice: "2000000",
        pickupDate: "2026-07-01",
        returnDate: "2026-07-03",
        sort: "PRICE_ASC",
      }),
    ).toEqual({
      query: "Toyota",
      city: "Hanoi",
      pickupDate: "2026-07-01",
      returnDate: "2026-07-03",
      category: "SUV",
      transmission: "AUTO",
      fuelType: "EV",
      seats: "7",
      minPrice: "800000",
      maxPrice: "2000000",
      instantBook: false,
      minRating: "",
      sort: "PRICE_ASC",
    });
  });

  it("maps backend search result to listing card view model", () => {
    const card = mapListingSearchResponse({
      id: "lst-1",
      title: "Toyota Vios",
      city: "Hanoi",
      category: "SEDAN",
      basePricePerDay: "700000.00",
      currency: "USD",
      seats: 5,
      transmission: "AUTO",
      fuelType: "GASOLINE",
      coverPhotoUrl: null,
      ratingAverage: "4.75",
    });
    expect(card).toMatchObject({
      id: "lst-1",
      title: "Toyota Vios",
      city: "Hanoi",
      category: "SEDAN",
      basePricePerDay: 700000,
      currency: "VND",
      seats: 5,
      transmission: "AUTO",
      fuelType: "GASOLINE",
      status: "ACTIVE",
      ratingLabel: "4.8 rating",
    });
    expect(card.coverImageUrl).toContain("images.unsplash.com");
  });

  it("falls back to VND when backend search currency is missing", () => {
    const card = mapListingSearchResponse({
      id: "lst-2",
      title: "Honda City",
      city: "Da Nang",
      category: "SEDAN",
      basePricePerDay: 500000,
      currency: null,
      seats: 5,
      transmission: "AUTO",
      fuelType: "GASOLINE",
      coverPhotoUrl: null,
      ratingAverage: null,
    });

    expect(card.currency).toBe("VND");
  });

  it("calls GET /listings with search params and maps page", async () => {
    fetchSpy.mockResolvedValueOnce(
      jsonResponse({
        content: [
          {
            id: "lst-1",
            title: "Toyota Vios",
            city: "Hanoi",
            category: "SEDAN",
            basePricePerDay: 700000,
            currency: "VND",
            seats: 5,
            transmission: "AUTO",
            fuelType: "GASOLINE",
            coverPhotoUrl: null,
            ratingAverage: null,
          },
        ],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      }),
    );

    const page = await searchListings({ ...DEFAULT_LISTING_FILTERS, city: "Hanoi" });

    expect(page.content[0].title).toBe("Toyota Vios");
    expect(fetchSpy.mock.calls[0][0]).toBe("/api/v1/listings?city=Hanoi&sort=NEWEST&page=0&size=20");
  });

  it("normalizes listing detail and extras currency to VND for public flow", async () => {
    fetchSpy.mockResolvedValueOnce(
      jsonResponse({
        id: "lst-3",
        title: "Tesla Model 3",
        description: "Electric sedan",
        city: "Hanoi",
        address: "123 EV Street",
        basePricePerDay: "100.00",
        currency: "USD",
        dailyKmLimit: 250,
        instantBook: true,
        cancellationPolicy: "FLEXIBLE",
        photos: [],
        vehicleSummary: {
          category: "SEDAN",
          make: "Tesla",
          model: "Model 3",
          year: 2024,
          transmission: "AUTO",
          fuelType: "ELECTRIC",
          seats: 5,
          status: "ACTIVE",
        },
        extras: [{ id: "ex-1", name: "Baby seat", price: 10, currency: "USD" }],
      }),
    );

    const detail = await getListingDetailById("lst-3");

    expect(detail?.currency).toBe("VND");
    expect(detail?.extras[0]?.currency).toBe("VND");
    expect(fetchSpy.mock.calls[0][0]).toBe("/api/v1/listings/lst-3");
  });

  it("falls back to VND when listing detail currency is missing", async () => {
    fetchSpy.mockResolvedValueOnce(
      jsonResponse({
        id: "lst-4",
        title: "Kia Morning",
        description: "Compact",
        city: "HCM",
        address: "456 Small Car Ave",
        basePricePerDay: 450000,
        currency: "",
        dailyKmLimit: 150,
        instantBook: false,
        cancellationPolicy: "MODERATE",
        photos: [],
        vehicleSummary: null,
        extras: [{ id: "ex-2", name: "GPS", price: 5, currency: null }],
      }),
    );

    const detail = await getListingDetailById("lst-4");

    expect(detail?.currency).toBe("VND");
    expect(detail?.extras[0]?.currency).toBe("VND");
  });
});
