"use client";

import { CheckCircle2, Loader2, UploadCloud } from "lucide-react";

import { buttonVariants } from "@/components/rentflow/ui/button";
import type { TripConditionPhotoAngle } from "@/features/trips/api";
import { cn } from "@/lib/utils";

export const REQUIRED_TRIP_PHOTO_SLOTS: { angle: TripConditionPhotoAngle; label: string }[] = [
  { angle: "FRONT", label: "Mat truoc" },
  { angle: "REAR", label: "Mat sau" },
  { angle: "LEFT", label: "Ben trai" },
  { angle: "RIGHT", label: "Ben phai" },
];

export type TripPhotoSlotState = {
  fileId?: string;
  fileName?: string;
  uploading?: boolean;
  error?: string;
};

type TripPhotoGridProps = {
  slots: Record<TripConditionPhotoAngle, TripPhotoSlotState | undefined>;
  onSelect: (angle: TripConditionPhotoAngle, file: File) => void;
};

export function TripPhotoGrid({ slots, onSelect }: TripPhotoGridProps) {
  return (
    <div className="grid gap-3 sm:grid-cols-2">
      {REQUIRED_TRIP_PHOTO_SLOTS.map((slot) => {
        const state = slots[slot.angle];
        const complete = Boolean(state?.fileId);
        return (
          <label
            key={slot.angle}
            className={cn(
              "flex min-h-32 cursor-pointer flex-col justify-between rounded-lg border bg-card p-3 text-sm transition-colors",
              complete ? "border-emerald-300 bg-emerald-50" : "border-dashed border-border",
            )}
          >
            <span className="flex items-center justify-between gap-2 font-medium">
              {slot.label}
              {state?.uploading ? (
                <Loader2 className="size-4 animate-spin text-muted-foreground" aria-hidden="true" />
              ) : complete ? (
                <CheckCircle2 className="size-4 text-emerald-600" aria-hidden="true" />
              ) : (
                <UploadCloud className="size-4 text-muted-foreground" aria-hidden="true" />
              )}
            </span>
            <span className="mt-3 line-clamp-2 text-xs text-muted-foreground">
              {state?.fileName || "Chua co anh"}
            </span>
            {state?.error ? (
              <span className="mt-2 text-xs text-destructive">{state.error}</span>
            ) : null}
            <input
              aria-label={`Tai anh ${slot.label}`}
              className="sr-only"
              type="file"
              accept="image/*"
              onChange={(event) => {
                const file = event.currentTarget.files?.[0];
                if (file) onSelect(slot.angle, file);
                event.currentTarget.value = "";
              }}
            />
            <span className={buttonVariants({ variant: "outline", size: "sm", className: "mt-3 w-fit" })}>
              {complete ? "Doi anh" : "Tai anh"}
            </span>
          </label>
        );
      })}
    </div>
  );
}
