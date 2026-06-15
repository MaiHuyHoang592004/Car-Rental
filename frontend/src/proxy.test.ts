import { describe, expect, it } from "vitest";

import { NextRequest } from "next/server";

import { proxy } from "./proxy";
import { REFRESH_COOKIE_NAME, ROLE_COOKIE_NAME } from "@/lib/session-cookie-shared";

type CookieInput = Record<string, string>;

function makeRequest(pathname: string, cookies: CookieInput = {}): NextRequest {
  const url = `http://localhost${pathname}`;
  const cookieHeader = Object.entries(cookies)
    .map(([k, v]) => `${k}=${v}`)
    .join("; ");
  const headers: HeadersInit = cookieHeader ? { cookie: cookieHeader } : {};
  return new NextRequest(url, { headers });
}

function locationHeader(res: Response): string | null {
  return res.headers.get("location");
}

describe("proxy role-based auth", () => {
  it("passes non-protected route without checking cookies", () => {
    const res = proxy(makeRequest("/listings")) as Response;
    // x-middleware-next header signals NextResponse.next() in Edge.
    expect(res.headers.get("x-middleware-next")).toBe("1");
  });

  it("redirects to /login when refresh cookie missing on protected route", () => {
    const res = proxy(makeRequest("/host/listings")) as Response;
    const loc = locationHeader(res);
    expect(loc).toBeTruthy();
    expect(loc).toContain("/login");
    expect(loc).toContain("next=%2Fhost%2Flistings");
  });

  it("redirects to /login and clears cookies when refresh present but role missing", () => {
    const res = proxy(
      makeRequest("/host/listings", { [REFRESH_COOKIE_NAME]: "r" }),
    ) as Response;
    expect(locationHeader(res)).toContain("/login");
    const sc = res.headers.get("set-cookie") ?? "";
    expect(sc).toContain(REFRESH_COOKIE_NAME);
    expect(sc).toContain(ROLE_COOKIE_NAME);
  });

  it("redirects authenticated-only route to /login when refresh exists but role cookie is empty", () => {
    const res = proxy(
      makeRequest("/me/profile", {
        [REFRESH_COOKIE_NAME]: "r",
        [ROLE_COOKIE_NAME]: "",
      }),
    ) as Response;
    expect(locationHeader(res)).toContain("/login");
    const sc = res.headers.get("set-cookie") ?? "";
    expect(sc).toContain(REFRESH_COOKIE_NAME);
    expect(sc).toContain(ROLE_COOKIE_NAME);
  });

  it("redirects CUSTOMER to /forbidden when accessing /host", () => {
    const res = proxy(
      makeRequest("/host/listings", {
        [REFRESH_COOKIE_NAME]: "r",
        [ROLE_COOKIE_NAME]: "CUSTOMER",
      }),
    ) as Response;
    expect(locationHeader(res)).toContain("/forbidden");
  });

  it("allows HOST on /host", () => {
    const res = proxy(
      makeRequest("/host/listings", {
        [REFRESH_COOKIE_NAME]: "r",
        [ROLE_COOKIE_NAME]: "HOST",
      }),
    ) as Response;
    expect(res.headers.get("x-middleware-next")).toBe("1");
  });

  it("redirects ADMIN away from /host (HOST-only)", () => {
    const res = proxy(
      makeRequest("/host/listings", {
        [REFRESH_COOKIE_NAME]: "r",
        [ROLE_COOKIE_NAME]: "ADMIN",
      }),
    ) as Response;
    expect(locationHeader(res)).toContain("/forbidden");
  });

  it("allows ADMIN on /admin", () => {
    const res = proxy(
      makeRequest("/admin/users", {
        [REFRESH_COOKIE_NAME]: "r",
        [ROLE_COOKIE_NAME]: "ADMIN",
      }),
    ) as Response;
    expect(res.headers.get("x-middleware-next")).toBe("1");
  });

  it("allows any authenticated user on /me/profile", () => {
    const res = proxy(
      makeRequest("/me/profile", {
        [REFRESH_COOKIE_NAME]: "r",
        [ROLE_COOKIE_NAME]: "HOST",
      }),
    ) as Response;
    expect(res.headers.get("x-middleware-next")).toBe("1");
  });

  it("requires login for /notifications", () => {
    const res = proxy(makeRequest("/notifications")) as Response;
    expect(locationHeader(res)).toContain("/login");
  });

  it("allows any authenticated user on /notifications", () => {
    const res = proxy(
      makeRequest("/notifications", {
        [REFRESH_COOKIE_NAME]: "r",
        [ROLE_COOKIE_NAME]: "CUSTOMER",
      }),
    ) as Response;
    expect(res.headers.get("x-middleware-next")).toBe("1");
  });

  it("allows any authenticated user on onboarding and leaves role checks to the page", () => {
    const res = proxy(
      makeRequest("/onboarding/customer", {
        [REFRESH_COOKIE_NAME]: "r",
        [ROLE_COOKIE_NAME]: "HOST",
      }),
    ) as Response;
    expect(res.headers.get("x-middleware-next")).toBe("1");
  });

  it("redirects unauthenticated onboarding route to /login", () => {
    const res = proxy(makeRequest("/onboarding/customer")) as Response;
    expect(locationHeader(res)).toContain("/login");
  });

  it("does not protect public verify-email route", () => {
    const res = proxy(makeRequest("/verify-email?token=abc")) as Response;
    expect(res.headers.get("x-middleware-next")).toBe("1");
  });

  it("does not protect public forgot-password route", () => {
    const res = proxy(makeRequest("/forgot-password")) as Response;
    expect(res.headers.get("x-middleware-next")).toBe("1");
  });

  it("does not protect public reset-password route", () => {
    const res = proxy(makeRequest("/reset-password?token=abc")) as Response;
    expect(res.headers.get("x-middleware-next")).toBe("1");
  });

  it("does not protect verify-email api path", () => {
    const res = proxy(makeRequest("/api/v1/auth/verify-email")) as Response;
    expect(res.headers.get("x-middleware-next")).toBe("1");
  });

  it("does not protect driver-license api path", () => {
    const res = proxy(makeRequest("/api/v1/users/me/driver-license")) as Response;
    expect(res.headers.get("x-middleware-next")).toBe("1");
  });

  it("redirects HOST away from /me/bookings (CUSTOMER-only)", () => {
    const res = proxy(
      makeRequest("/me/bookings", {
        [REFRESH_COOKIE_NAME]: "r",
        [ROLE_COOKIE_NAME]: "HOST",
      }),
    ) as Response;
    expect(locationHeader(res)).toContain("/forbidden");
  });

  it("allows CUSTOMER on /listings/abc/book", () => {
    const res = proxy(
      makeRequest("/listings/abc/book", {
        [REFRESH_COOKIE_NAME]: "r",
        [ROLE_COOKIE_NAME]: "CUSTOMER",
      }),
    ) as Response;
    expect(res.headers.get("x-middleware-next")).toBe("1");
  });

  it("passes /listings/abc (detail page, not /book) without auth check", () => {
    const res = proxy(makeRequest("/listings/abc")) as Response;
    expect(res.headers.get("x-middleware-next")).toBe("1");
  });
});
