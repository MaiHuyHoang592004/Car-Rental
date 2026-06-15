import { NextRequest, NextResponse } from "next/server";

import {
  REFRESH_COOKIE_NAME,
  ROLE_COOKIE_NAME,
  parseRoles,
} from "@/lib/session-cookie-shared";

type Requirement = {
  test: (pathname: string) => boolean;
  roles: string[] | null;
};

const REQUIREMENTS: Requirement[] = [
  { test: (p) => p.startsWith("/admin"), roles: ["ADMIN"] },
  { test: (p) => p.startsWith("/host"), roles: ["HOST"] },
  { test: (p) => p.startsWith("/onboarding"), roles: null },
  { test: (p) => p.startsWith("/notifications"), roles: null },
  { test: (p) => p.startsWith("/me/bookings"), roles: ["CUSTOMER"] },
  { test: (p) => p.startsWith("/me"), roles: null },
  { test: (p) => p.startsWith("/bookings"), roles: ["CUSTOMER"] },
  { test: (p) => /^\/listings\/[^/]+\/book\/?$/.test(p), roles: ["CUSTOMER"] },
];

function matchRequirement(pathname: string): Requirement | null {
  return REQUIREMENTS.find((r) => r.test(pathname)) ?? null;
}

function redirectToLogin(request: NextRequest, clearCookies: boolean) {
  const { pathname, search } = request.nextUrl;
  const loginUrl = request.nextUrl.clone();
  loginUrl.pathname = "/login";
  loginUrl.search = "";
  loginUrl.searchParams.set("next", `${pathname}${search}`);
  const response = NextResponse.redirect(loginUrl);
  if (clearCookies) {
    response.cookies.delete(REFRESH_COOKIE_NAME);
    response.cookies.delete(ROLE_COOKIE_NAME);
  }
  return response;
}

function redirectToForbidden(request: NextRequest) {
  const forbiddenUrl = request.nextUrl.clone();
  forbiddenUrl.pathname = "/forbidden";
  forbiddenUrl.search = "";
  return NextResponse.redirect(forbiddenUrl);
}

export function proxy(request: NextRequest) {
  const requirement = matchRequirement(request.nextUrl.pathname);
  if (!requirement) {
    return NextResponse.next();
  }

  const refreshCookie = request.cookies.get(REFRESH_COOKIE_NAME)?.value;
  if (!refreshCookie) {
    return redirectToLogin(request, false);
  }

  const roleCookie = request.cookies.get(ROLE_COOKIE_NAME)?.value;
  const roles = parseRoles(roleCookie);

  if (roles.length === 0) {
    return redirectToLogin(request, true);
  }

  if (requirement.roles === null) {
    return NextResponse.next();
  }

  const allowed = requirement.roles.some((r) => roles.includes(r));
  if (!allowed) {
    return redirectToForbidden(request);
  }

  return NextResponse.next();
}

export const config = {
  matcher: [
    "/me/:path*",
    "/host/:path*",
    "/onboarding/:path*",
    "/notifications/:path*",
    "/admin/:path*",
    "/bookings/:path*",
    "/listings/:id/book",
  ],
};
