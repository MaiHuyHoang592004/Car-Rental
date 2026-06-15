"use client";

import Link from "next/link";
import { BookmarkCheck } from "lucide-react";
import { useQuery } from "@tanstack/react-query";

import { AppShell } from "@/components/rentflow/app-shell";
import { PageHeader } from "@/components/rentflow/page-header";
import { PageSkeleton } from "@/components/rentflow/page-skeleton";
import { listSavedListings } from "@/features/listings/api";
import { formatMoney } from "@/lib/formatters";

const FALLBACK_COVER_IMAGE_URL =
  "https://images.unsplash.com/photo-1492144534655-ae79c964c9d7?auto=format&fit=crop&w=1200&q=80";

export function SavedListingsPageView() {
  const query = useQuery({
    queryKey: ["saved-listings"],
    queryFn: ({ signal }) => listSavedListings(signal),
  });

  if (query.isLoading) {
    return <PageSkeleton message="Dang tai xe da luu..." />;
  }

  const savedListings = query.data?.content ?? [];

  return (
    <AppShell activePath="/me/saved-listings">
      <main className="mx-auto flex w-full max-w-6xl flex-col gap-6 px-4 py-8">
        <PageHeader
          title="Xe da luu"
          description={`${savedListings.length} xe trong danh sach cua ban`}
        />
        {savedListings.length === 0 ? (
          <section className="flex items-center gap-3 rounded-lg border border-slate-200 bg-white px-4 py-8 text-sm text-slate-500">
            <BookmarkCheck className="h-5 w-5" aria-hidden />
            Ban chua luu xe nao.
          </section>
        ) : (
          <section className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {savedListings.map((item) => (
              <Link
                className="overflow-hidden rounded-lg border border-slate-200 bg-white transition hover:border-slate-300 hover:shadow-sm"
                href={`/listings/${item.listingId}`}
                key={item.id}
              >
                <img
                  alt={item.title ?? "Saved listing"}
                  className="aspect-[4/3] w-full object-cover"
                  src={item.coverPhotoUrl || FALLBACK_COVER_IMAGE_URL}
                />
                <div className="space-y-2 p-4">
                  <h2 className="line-clamp-1 text-sm font-semibold text-slate-950">
                    {item.title ?? "Listing"}
                  </h2>
                  <p className="text-sm text-slate-500">{item.city ?? "Chua co thanh pho"}</p>
                  <p className="text-sm font-semibold text-slate-950">
                    {formatMoney(item.basePricePerDay)} / ngay
                  </p>
                </div>
              </Link>
            ))}
          </section>
        )}
      </main>
    </AppShell>
  );
}
