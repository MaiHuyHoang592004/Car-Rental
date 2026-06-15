import type { NextRequest } from "next/server";

import { proxyAuthenticatedBackendRequest } from "@/lib/server/authenticated-backend-route";

type RouteContext = {
  params: Promise<{
    path?: string[];
  }>;
};

function buildBackendPath(path: string[] | undefined, search: string) {
  const suffix = path?.length ? `/${path.join("/")}` : "";
  return `/notifications${suffix}${search}`;
}

async function handle(request: NextRequest, context: RouteContext) {
  const params = await context.params;
  return proxyAuthenticatedBackendRequest(
    request,
    buildBackendPath(params.path, request.nextUrl.search),
  );
}

export async function GET(request: NextRequest, context: RouteContext) {
  return handle(request, context);
}

export async function POST(request: NextRequest, context: RouteContext) {
  return handle(request, context);
}
