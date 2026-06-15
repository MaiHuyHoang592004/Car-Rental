"use client";

import type { UseFormReturn } from "react-hook-form";
import { CarFront, Fuel, MapPin, RotateCcw, Settings2, SlidersHorizontal, Star } from "lucide-react";

import type { ListingFilterState } from "@/features/listings/types";

type ListingFiltersPanelProps = {
  form: UseFormReturn<ListingFilterState>;
  onReset: () => void;
};

const CATEGORY_OPTIONS = [
  { value: "ALL", label: "Tất cả" },
  { value: "SEDAN", label: "Xe sedan" },
  { value: "SUV", label: "SUV / CUV" },
  { value: "PICKUP", label: "Xe bán tải" },
  { value: "MPV", label: "MPV" },
  { value: "HATCHBACK", label: "Hatchback" },
];

const TRANSMISSION_OPTIONS = [
  { value: "MANUAL", label: "Số sàn", icon: Settings2 },
  { value: "AUTO", label: "Tự động", icon: RotateCcw },
];

const FUEL_OPTIONS = [
  { value: "GASOLINE", label: "Xăng" },
  { value: "PETROL", label: "Xăng Petrol" },
  { value: "DIESEL", label: "Dầu Diesel" },
  { value: "EV", label: "Xe điện" },
  { value: "HYBRID", label: "Hybrid" },
];

const SEAT_OPTIONS = ["4", "5", "7", "8", "16"];

export function ListingFiltersPanel({ form, onReset }: ListingFiltersPanelProps) {
  const { register, setValue, watch } = form;
  const category = watch("category");
  const transmission = watch("transmission");
  const fuelType = watch("fuelType");
  const seats = watch("seats");
  const maxPrice = watch("maxPrice") || "5000000";

  const hasActiveFilters =
    watch("city") ||
    watch("query") ||
    watch("pickupDate") ||
    watch("returnDate") ||
    category !== "ALL" ||
    transmission !== "ALL" ||
    fuelType !== "ALL" ||
    seats ||
    watch("minPrice") ||
    watch("maxPrice") ||
    watch("instantBook") ||
    watch("minRating");

  return (
    <aside className="space-y-6">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <SlidersHorizontal className="h-5 w-5 text-primary" />
          <h2 className="text-xl font-semibold text-foreground">Bộ lọc</h2>
        </div>
        {hasActiveFilters ? (
          <button
            type="button"
            onClick={onReset}
            className="text-xs font-semibold text-primary hover:underline"
          >
            Xóa tất cả
          </button>
        ) : null}
      </div>

      <div className="space-y-2">
        <label className="text-sm font-semibold text-foreground">Thành phố</label>
        <div className="relative flex items-center">
          <MapPin className="pointer-events-none absolute left-3 h-4 w-4 text-muted-foreground" />
          <input
            type="text"
            placeholder="Thành phố hoặc địa điểm"
            {...register("city")}
            className="h-12 w-full rounded-t-lg border-0 border-b-2 border-border bg-muted pl-10 pr-4 text-sm text-foreground outline-none transition-colors placeholder:text-muted-foreground focus:border-primary focus:ring-0"
          />
        </div>
      </div>

      <div className="space-y-3">
        <h3 className="text-sm font-semibold text-foreground">Loại xe</h3>
        <div className="flex flex-wrap gap-2">
          {CATEGORY_OPTIONS.map((option) => (
            <label
              key={option.value}
              className={[
                "cursor-pointer rounded-full px-4 py-1.5 text-xs font-semibold transition-colors",
                category === option.value
                  ? "bg-primary text-primary-foreground"
                  : "bg-muted text-muted-foreground hover:bg-[#d5e3fc] hover:text-foreground",
              ].join(" ")}
            >
              <input type="radio" value={option.value} {...register("category")} className="sr-only" />
              {option.label}
            </label>
          ))}
        </div>
      </div>

      <div className="space-y-3">
        <h3 className="text-sm font-semibold text-foreground">Giá thuê (VND/ngày)</h3>
        <input
          type="range"
          min={300000}
          max={5000000}
          step={100000}
          value={maxPrice}
          onChange={(event) =>
            setValue("maxPrice", event.target.value, { shouldDirty: true, shouldValidate: true })
          }
          className="h-2 w-full cursor-pointer appearance-none rounded-lg bg-[#e0e3e5] accent-primary"
        />
        <div className="flex justify-between text-xs font-medium text-muted-foreground">
          <span>300.000đ</span>
          <span>{Number(maxPrice).toLocaleString("vi-VN")}đ</span>
        </div>
      </div>

      <div className="space-y-3">
        <h3 className="text-sm font-semibold text-foreground">Tùy chọn đặt xe</h3>
        <label className="flex cursor-pointer items-center gap-3 rounded-lg border border-border bg-background px-3 py-2 text-sm text-foreground">
          <input
            type="checkbox"
            {...register("instantBook")}
            className="h-4 w-4 rounded border-border text-primary focus:ring-primary"
          />
          Đặt ngay
        </label>
        <label className="flex items-center gap-2 text-sm text-muted-foreground">
          <Star className="h-4 w-4 text-amber-500" />
          Tối thiểu
          <select
            {...register("minRating")}
            className="h-9 rounded-md border border-border bg-background px-2 text-sm text-foreground"
          >
            <option value="">Mọi đánh giá</option>
            <option value="4">4.0+</option>
            <option value="4.5">4.5+</option>
          </select>
        </label>
      </div>

      <div className="space-y-3">
        <h3 className="text-sm font-semibold text-foreground">Truyền động</h3>
        <div className="grid grid-cols-2 gap-2">
          {TRANSMISSION_OPTIONS.map((option) => {
            const Icon = option.icon;
            return (
              <label
                key={option.value}
                className={[
                  "flex cursor-pointer items-center justify-center gap-2 rounded-lg border px-3 py-2 text-sm transition-colors",
                  transmission === option.value
                    ? "border-primary bg-[#dbe1ff] text-primary"
                    : "border-border bg-background text-foreground hover:border-primary/60",
                ].join(" ")}
              >
                <input type="radio" value={option.value} {...register("transmission")} className="sr-only" />
                <Icon className="h-4 w-4" />
                {option.label}
              </label>
            );
          })}
        </div>
        <button
          type="button"
          onClick={() => setValue("transmission", "ALL", { shouldDirty: true, shouldValidate: true })}
          className="text-xs font-medium text-muted-foreground hover:text-primary"
        >
          Mọi hộp số
        </button>
      </div>

      <div className="space-y-3">
        <h3 className="text-sm font-semibold text-foreground">Nhiên liệu</h3>
        <div className="space-y-2">
          {FUEL_OPTIONS.map((option) => (
            <label key={option.value} className="flex cursor-pointer items-center gap-3">
              <input
                type="radio"
                value={option.value}
                {...register("fuelType")}
                className="h-5 w-5 rounded border-border text-primary focus:ring-primary"
              />
              <span className="flex items-center gap-2 text-sm text-muted-foreground hover:text-foreground">
                <Fuel className="h-4 w-4" />
                {option.label}
              </span>
            </label>
          ))}
        </div>
        <button
          type="button"
          onClick={() => setValue("fuelType", "ALL", { shouldDirty: true, shouldValidate: true })}
          className="text-xs font-medium text-muted-foreground hover:text-primary"
        >
          Mọi loại nhiên liệu
        </button>
      </div>

      <div className="space-y-3">
        <h3 className="text-sm font-semibold text-foreground">Số chỗ</h3>
        <div className="flex flex-wrap gap-2">
          {SEAT_OPTIONS.map((option) => (
            <button
              key={option}
              type="button"
              onClick={() =>
                setValue("seats", seats === option ? "" : option, {
                  shouldDirty: true,
                  shouldValidate: true,
                })
              }
              className={[
                "inline-flex items-center gap-1.5 rounded-md px-3 py-1.5 text-xs font-semibold transition-colors",
                seats === option
                  ? "bg-primary text-primary-foreground"
                  : "bg-muted text-muted-foreground hover:bg-[#d5e3fc] hover:text-foreground",
              ].join(" ")}
            >
              <CarFront className="h-3.5 w-3.5" />
              {option} chỗ
            </button>
          ))}
        </div>
      </div>
    </aside>
  );
}
