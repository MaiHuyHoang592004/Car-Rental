import Link from "next/link";
import { ArrowRight } from "lucide-react";

import { ListingGrid } from "@/features/listings/listing-grid";
import type { ListingCardViewModel } from "@/features/listings/types";

type FeaturedListingsSectionProps = {
  featured: ListingCardViewModel[];
};

export function FeaturedListingsSection({ featured }: FeaturedListingsSectionProps) {
  return (
    <section className="relative left-1/2 w-screen -translate-x-1/2 overflow-x-hidden bg-white py-16 md:py-20">
      <div className="rf-shell-container">
        <div className="mb-6 flex items-end justify-between gap-4">
          <div>
            <h2 className="text-2xl font-bold text-foreground">Xe nổi bật tại Hà Nội</h2>
            <p className="mt-1 text-sm text-muted-foreground">
              Những lựa chọn được người dùng đánh giá cao nhất.
            </p>
          </div>
          <Link
            href="/listings"
            className="hidden items-center gap-1 text-sm font-semibold text-primary hover:underline sm:inline-flex"
          >
            Xem tất cả
            <ArrowRight className="h-4 w-4" />
          </Link>
        </div>
        <ListingGrid listings={featured} />
      </div>
    </section>
  );
}
