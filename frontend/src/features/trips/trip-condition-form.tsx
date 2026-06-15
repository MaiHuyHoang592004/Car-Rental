"use client";

import { AlertTriangle, ArrowLeft, ClipboardCheck, Fuel, Gauge } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useMemo, useState } from "react";
import { toast } from "sonner";

import { Button, buttonVariants } from "@/components/rentflow/ui/button";
import { DamageItemEditor } from "@/features/trips/damage-item-editor";
import {
  checkInTrip,
  checkOutTrip,
  createConditionReport,
  type CreateDamageItemInput,
  type TripConditionPhotoAngle,
  type TripConditionReportType,
  uploadTripPhoto,
} from "@/features/trips/api";
import {
  REQUIRED_TRIP_PHOTO_SLOTS,
  TripPhotoGrid,
  type TripPhotoSlotState,
} from "@/features/trips/trip-photo-grid";
import { ApiError } from "@/lib/api-error";
import { newIdempotencyKey } from "@/lib/idempotency";

type TripConditionFormProps = {
  bookingId: string;
  reportType: TripConditionReportType;
};

const titleByType: Record<TripConditionReportType, string> = {
  CHECK_IN: "Nhan xe",
  CHECK_OUT: "Tra xe",
};

export function TripConditionForm({ bookingId, reportType }: TripConditionFormProps) {
  const router = useRouter();
  const [odometer, setOdometer] = useState("");
  const [fuelLevel, setFuelLevel] = useState("80");
  const [note, setNote] = useState("");
  const [photoSlots, setPhotoSlots] = useState<Record<string, TripPhotoSlotState>>({});
  const [damageItems, setDamageItems] = useState<CreateDamageItemInput[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const requiredPhotosReady = REQUIRED_TRIP_PHOTO_SLOTS.every((slot) => photoSlots[slot.angle]?.fileId);
  const uploading = Object.values(photoSlots).some((slot) => slot?.uploading);
  const numericOdometer = Number(odometer);
  const numericFuel = Number(fuelLevel);
  const validNumbers =
    odometer !== ""
    && fuelLevel !== ""
    && Number.isFinite(numericOdometer)
    && numericOdometer >= 0
    && Number.isFinite(numericFuel)
    && numericFuel >= 0
    && numericFuel <= 100;
  const submitDisabled = submitting || uploading || !validNumbers || !requiredPhotosReady;

  const readinessText = useMemo(() => {
    if (!validNumbers) return "Nhap odometer va nhien lieu hop le.";
    if (!requiredPhotosReady) return "Can du 4 anh bat buoc: mat truoc, mat sau, ben trai, ben phai.";
    if (uploading) return "Dang tai anh.";
    return null;
  }, [requiredPhotosReady, uploading, validNumbers]);

  async function handlePhotoSelect(angle: TripConditionPhotoAngle, file: File) {
    setFormError(null);
    setPhotoSlots((current) => ({
      ...current,
      [angle]: { fileName: file.name, uploading: true },
    }));
    try {
      const fileId = await uploadTripPhoto(bookingId, file, newIdempotencyKey());
      setPhotoSlots((current) => ({
        ...current,
        [angle]: { fileId, fileName: file.name, uploading: false },
      }));
    } catch (error) {
      const message = error instanceof ApiError ? error.message : "Tai anh that bai";
      setPhotoSlots((current) => ({
        ...current,
        [angle]: { fileName: file.name, uploading: false, error: message },
      }));
    }
  }

  async function handleSubmit() {
    if (submitDisabled) return;
    setSubmitting(true);
    setFormError(null);
    try {
      await createConditionReport(
        bookingId,
        {
          reportType,
          odometer: numericOdometer,
          fuelLevel: numericFuel,
          hasVisibleDamage: damageItems.length > 0,
          note: note.trim() || undefined,
          photos: REQUIRED_TRIP_PHOTO_SLOTS.map((slot, index) => ({
            fileId: photoSlots[slot.angle]!.fileId!,
            angle: slot.angle,
            displayOrder: index,
          })),
          damageItems,
        },
        newIdempotencyKey(),
      );
      if (reportType === "CHECK_IN") {
        await checkInTrip(bookingId, {
          odometer: numericOdometer,
          fuelLevel: numericFuel,
          note: note.trim() || undefined,
        });
      } else {
        await checkOutTrip(bookingId, {
          odometer: numericOdometer,
          fuelLevel: numericFuel,
          note: note.trim() || undefined,
        });
      }
      toast.success(reportType === "CHECK_IN" ? "Da nhan xe" : "Da tra xe");
      router.push(`/bookings/${bookingId}`);
    } catch (error) {
      const message = error instanceof ApiError ? error.message : "Khong the hoan tat ban giao";
      setFormError(message);
      toast.error(message);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="mx-auto flex w-full max-w-4xl flex-col gap-5">
      <div className="flex items-center justify-between gap-3">
        <Link
          href={`/bookings/${bookingId}`}
          className={buttonVariants({ variant: "ghost", size: "sm" })}
        >
          <ArrowLeft className="size-4" aria-hidden="true" />
          Quay lai
        </Link>
      </div>

      <section className="rounded-lg border bg-card p-4">
        <div className="mb-4 flex items-center gap-2">
          <ClipboardCheck className="size-5 text-primary" aria-hidden="true" />
          <h1 className="text-xl font-semibold">{titleByType[reportType]}</h1>
        </div>
        <div className="grid gap-3 md:grid-cols-2">
          <label className="grid gap-1 text-sm">
            <span className="flex items-center gap-2 font-medium">
              <Gauge className="size-4" aria-hidden="true" />
              Odometer
            </span>
            <input
              className="h-10 rounded-md border border-input bg-background px-3 text-sm"
              inputMode="numeric"
              min={0}
              type="number"
              value={odometer}
              onChange={(event) => setOdometer(event.target.value)}
            />
          </label>
          <label className="grid gap-1 text-sm">
            <span className="flex items-center gap-2 font-medium">
              <Fuel className="size-4" aria-hidden="true" />
              Nhien lieu (%)
            </span>
            <input
              className="h-10 rounded-md border border-input bg-background px-3 text-sm"
              inputMode="numeric"
              max={100}
              min={0}
              type="number"
              value={fuelLevel}
              onChange={(event) => setFuelLevel(event.target.value)}
            />
          </label>
        </div>
        <label className="mt-3 grid gap-1 text-sm">
          <span className="font-medium">Ghi chu</span>
          <textarea
            className="min-h-20 rounded-md border border-input bg-background px-3 py-2 text-sm"
            value={note}
            onChange={(event) => setNote(event.target.value)}
          />
        </label>
      </section>

      <section className="rounded-lg border bg-card p-4">
        <h2 className="mb-3 text-base font-semibold">Anh ban giao</h2>
        <TripPhotoGrid
          slots={photoSlots as Record<TripConditionPhotoAngle, TripPhotoSlotState | undefined>}
          onSelect={handlePhotoSelect}
        />
      </section>

      <section className="rounded-lg border bg-card p-4">
        <h2 className="mb-3 text-base font-semibold">Hu hong</h2>
        <DamageItemEditor items={damageItems} onChange={setDamageItems} />
      </section>

      {formError ? (
        <div className="flex items-start gap-2 rounded-lg border border-destructive/30 bg-destructive/10 p-3 text-sm text-destructive">
          <AlertTriangle className="mt-0.5 size-4" aria-hidden="true" />
          <span>{formError}</span>
        </div>
      ) : readinessText ? (
        <div className="rounded-lg border bg-muted/40 p-3 text-sm text-muted-foreground">{readinessText}</div>
      ) : null}

      <div className="flex justify-end">
        <Button type="button" disabled={submitDisabled} onClick={handleSubmit}>
          <ClipboardCheck className="size-4" aria-hidden="true" />
          {submitting ? "Dang gui" : "Hoan tat"}
        </Button>
      </div>
    </div>
  );
}
