import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const routerPush = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: vi.fn(), refresh: vi.fn(), push: routerPush }),
  usePathname: () => "/listings/lst-001/book",
}));

import { AuthProvider } from "@/features/auth/auth-context";
import { BookingCreatePageView } from "./booking-create-page-view";

const authedSession = {
  accessToken: "ACCESS",
  accessTokenExpiresAt: "2099-01-01T00:00:00Z",
  user: {
    id: "u-1",
    email: "u@e.com",
    emailVerified: false,
    roles: ["CUSTOMER"],
    fullName: "U",
    phone: null,
    dateOfBirth: null,
    addressLine: null,
    driverVerificationStatus: "NOT_SUBMITTED",
  },
};

function wrap(node: ReactNode) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <AuthProvider initialSession={authedSession}>
      <QueryClientProvider client={qc}>{node}</QueryClientProvider>
    </AuthProvider>,
  );
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function listingResponse(): Response {
  return jsonResponse({
    id: "lst-001",
    title: "Toyota Vios 2022",
    description: "Listing detail",
    city: "HCM",
    address: "District 1",
    basePricePerDay: 700000,
    currency: "VND",
    dailyKmLimit: 200,
    instantBook: true,
    cancellationPolicy: "FLEXIBLE",
    photos: [],
    vehicleSummary: {
      category: "SEDAN",
      make: "Toyota",
      model: "Vios",
      year: 2022,
      transmission: "AUTO",
      fuelType: "PETROL",
      seats: 5,
      status: "ACTIVE",
    },
    extras: [],
  });
}

function profileResponse(overrides?: Partial<typeof authedSession.user>): Response {
  return jsonResponse({
    id: authedSession.user.id,
    email: authedSession.user.email,
    emailVerified: overrides?.emailVerified ?? true,
    roles: overrides?.roles ?? ["CUSTOMER"],
    fullName: authedSession.user.fullName,
    phone: "",
    dateOfBirth: null,
    addressLine: "",
    driverVerificationStatus: overrides?.driverVerificationStatus ?? "APPROVED",
  });
}

function protectionPlansResponse(): Response {
  return jsonResponse({
    items: [
      {
        id: "pp-basic",
        code: "BASIC",
        name: "Basic",
        description: "Included protection",
        priceType: "PER_TRIP",
        priceAmount: 0,
        deductibleAmount: 5000000,
        maxCoverageAmount: null,
        active: true,
      },
    ],
  });
}

function isoOffset(days: number): string {
  const d = new Date();
  d.setDate(d.getDate() + days);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

function requestUrl(input: RequestInfo | URL): string {
  if (input instanceof Request) return input.url;
  return input.toString();
}

describe("BookingCreatePageView", () => {
  let fetchSpy: ReturnType<typeof vi.fn>;

  function mockBookingCreateFlow(options?: {
    profileOverrides?: Partial<typeof authedSession.user>;
    bookingResponse?: Response;
  }) {
    fetchSpy.mockImplementation((input: RequestInfo | URL, init?: RequestInit) => {
      const url = requestUrl(input);
      if (url.endsWith("/api/v1/listings/lst-001")) {
        return Promise.resolve(listingResponse());
      }
      if (url.endsWith("/api/v1/users/me")) {
        return Promise.resolve(profileResponse(options?.profileOverrides));
      }
      if (url.endsWith("/api/v1/protection-plans")) {
        return Promise.resolve(protectionPlansResponse());
      }
      if (url.endsWith("/api/v1/bookings") && init?.method === "POST") {
        return Promise.resolve(
          options?.bookingResponse ??
            jsonResponse({
              id: "bk-99",
              status: "HELD",
              listingId: "lst-001",
              listingTitle: "Toyota Vios 2022",
              customerId: "u-1",
              hostId: "h-1",
              pickupDate: isoOffset(1),
              returnDate: isoOffset(3),
              pickupLocation: "HCM",
              returnLocation: "HCM",
              holdExpiresAt: "2099-01-01T00:15:00Z",
              totalAmount: 1400000,
              currency: "VND",
              priceSnapshot: null,
              policySnapshot: null,
              createdAt: "2099-01-01T00:00:00Z",
            }),
        );
      }
      return Promise.resolve(
        jsonResponse({ code: "UNHANDLED_TEST_REQUEST", message: url }, 500),
      );
    });
  }

  function findFetchCall(path: string, method?: string) {
    return fetchSpy.mock.calls.find(([input, init]) => {
      const matchesPath = requestUrl(input as RequestInfo | URL).endsWith(path);
      if (!matchesPath) return false;
      return method ? (init as RequestInit | undefined)?.method === method : true;
    });
  }

  beforeEach(() => {
    fetchSpy = vi.fn();
    vi.stubGlobal("fetch", fetchSpy);
    routerPush.mockClear();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("submits booking and redirects to detail on success", async () => {
    const pickup = isoOffset(1);
    const ret = isoOffset(3);
    mockBookingCreateFlow({
      bookingResponse: jsonResponse(
        {
          id: "bk-99",
          status: "HELD",
          listingId: "lst-001",
          listingTitle: "Toyota Vios 2022",
          customerId: "u-1",
          hostId: "h-1",
          pickupDate: pickup,
          returnDate: ret,
          pickupLocation: "HCM",
          returnLocation: "HCM",
          holdExpiresAt: "2099-01-01T00:15:00Z",
          totalAmount: 1400000,
          currency: "VND",
          priceSnapshot: null,
          policySnapshot: null,
          createdAt: "2099-01-01T00:00:00Z",
        },
        201,
      ),
    });
    wrap(<BookingCreatePageView listingId="lst-001" isGuest={false} />);
    await screen.findByText(/Dat xe/);

    const dateInputs = document.querySelectorAll<HTMLInputElement>('input[type="date"]');
    await userEvent.type(dateInputs[0], pickup);
    await userEvent.type(dateInputs[1], ret);
    await userEvent.click(screen.getByRole("button", { name: /Giữ xe trong 15 phút/ }));

    await waitFor(() => expect(routerPush).toHaveBeenCalledWith("/bookings/bk-99"));
    expect(findFetchCall("/api/v1/users/me")).toBeDefined();
    expect(findFetchCall("/api/v1/protection-plans")).toBeDefined();
    const bookingPostCall = findFetchCall("/api/v1/bookings", "POST");
    expect(bookingPostCall).toBeDefined();
    const [, postInit] = bookingPostCall as [RequestInfo | URL, RequestInit];
    expect(postInit.method).toBe("POST");
    const headers = new Headers(postInit.headers);
    expect(headers.get("Idempotency-Key")).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i,
    );
  }, 10000);

  it("shows overlap banner on 409 BOOKING_OVERLAP_CUSTOMER", async () => {
    mockBookingCreateFlow({
      bookingResponse: jsonResponse(
        {
          code: "BOOKING_OVERLAP_CUSTOMER",
          message: "Bạn đã có booking trùng thời gian.",
        },
        409,
      ),
    });
    wrap(<BookingCreatePageView listingId="lst-001" isGuest={false} />);
    await screen.findByText(/Dat xe/);

    const dateInputs = document.querySelectorAll<HTMLInputElement>('input[type="date"]');
    await userEvent.type(dateInputs[0], isoOffset(1));
    await userEvent.type(dateInputs[1], isoOffset(3));
    await userEvent.click(screen.getByRole("button", { name: /Giữ xe trong 15 phút/ }));

    await waitFor(() => expect(screen.getByText("Trung booking")).toBeInTheDocument());
    expect(routerPush).not.toHaveBeenCalled();
  });

  it("blocks submit with validation when return ≤ pickup", async () => {
    mockBookingCreateFlow();
    wrap(<BookingCreatePageView listingId="lst-001" isGuest={false} />);
    await screen.findByText(/Dat xe/);
    const sameDay = isoOffset(1);
    const dateInputs = document.querySelectorAll<HTMLInputElement>('input[type="date"]');
    await userEvent.type(dateInputs[0], sameDay);
    await userEvent.type(dateInputs[1], sameDay);
    await userEvent.click(screen.getByRole("button", { name: /Giữ xe trong 15 phút/ }));

    expect(screen.getByText("Ngày trả phải sau ngày nhận.")).toBeInTheDocument();
    expect(findFetchCall("/api/v1/bookings", "POST")).toBeUndefined();
  });

  it("shows blocker card and does not submit when profile is not eligible", async () => {
    mockBookingCreateFlow({
      profileOverrides: {
        emailVerified: false,
        driverVerificationStatus: "NOT_SUBMITTED",
      },
    });
    wrap(<BookingCreatePageView listingId="lst-001" isGuest={false} />);
    await screen.findByText(/Dat xe/);

    await waitFor(() =>
      expect(screen.getByText("Bạn chưa thể đặt xe")).toBeInTheDocument(),
    );
    expect(screen.getByText("Xác minh email")).toBeInTheDocument();
    expect(screen.getByText("Xác minh giấy phép lái xe")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /Hoàn tất xác minh để đặt xe/ }),
    ).toBeDisabled();
    expect(findFetchCall("/api/v1/bookings", "POST")).toBeUndefined();
  });

  it("maps backend driver-verification blocker to friendly Vietnamese message", async () => {
    mockBookingCreateFlow({
      bookingResponse: jsonResponse(
        {
          code: "DRIVER_VERIFICATION_PENDING",
          message: "Driver verification is pending approval",
        },
        403,
      ),
    });
    wrap(<BookingCreatePageView listingId="lst-001" isGuest={false} />);
    await screen.findByText(/Dat xe/);

    const dateInputs = document.querySelectorAll<HTMLInputElement>('input[type="date"]');
    await userEvent.type(dateInputs[0], isoOffset(1));
    await userEvent.type(dateInputs[1], isoOffset(3));
    await userEvent.click(screen.getByRole("button", { name: /Giữ xe trong 15 phút/ }));

    await waitFor(() =>
      expect(screen.getByText("GPLX của bạn đang chờ duyệt.")).toBeInTheDocument(),
    );
    expect(
      screen.getByRole("link", { name: /Xem trạng thái xác minh/ }),
    ).toHaveAttribute("href", "/onboarding/customer/driver-license/pending");
  });
});
