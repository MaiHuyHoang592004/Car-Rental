import { api } from "@/lib/api-client";

export type TripConditionReportType = "CHECK_IN" | "CHECK_OUT";

export type TripConditionPhotoAngle =
  | "FRONT"
  | "REAR"
  | "LEFT"
  | "RIGHT"
  | "INTERIOR_FRONT"
  | "INTERIOR_REAR"
  | "ODOMETER"
  | "FUEL"
  | "DAMAGE_CLOSEUP"
  | "OTHER";

export type TripDamageSeverity = "MINOR" | "MODERATE" | "SEVERE";

export type CreateConditionPhotoInput = {
  fileId: string;
  angle: TripConditionPhotoAngle;
  displayOrder?: number;
  note?: string;
};

export type CreateDamageItemInput = {
  location: string;
  severity: TripDamageSeverity;
  description: string;
  photoFileId?: string;
  preExisting?: boolean;
};

export type CreateConditionReportInput = {
  reportType: TripConditionReportType;
  odometer: number;
  fuelLevel: number;
  exteriorCleanliness?: string;
  interiorCleanliness?: string;
  hasVisibleDamage: boolean;
  note?: string;
  latitude?: number;
  longitude?: number;
  photos: CreateConditionPhotoInput[];
  damageItems: CreateDamageItemInput[];
};

export type TripConditionReport = {
  id: string;
  bookingId: string;
  tripRecordId: string | null;
  reporterUserId: string;
  reporterRole: "CUSTOMER" | "HOST";
  reportType: TripConditionReportType;
  odometer: number;
  fuelLevel: number;
  exteriorCleanliness: string | null;
  interiorCleanliness: string | null;
  hasVisibleDamage: boolean;
  note: string | null;
  latitude: number | null;
  longitude: number | null;
  submittedAt: string;
  photos: {
    id: string;
    fileId: string;
    angle: TripConditionPhotoAngle;
    displayOrder: number;
    note: string | null;
    signedUrl: string | null;
    signedUrlExpiresAt: string | null;
  }[];
  damageItems: {
    id: string;
    location: string;
    severity: TripDamageSeverity;
    description: string;
    photoId: string | null;
    preExisting: boolean;
  }[];
};

export type TripRecordResponse = {
  bookingId: string;
  bookingStatus: string;
  checkInAt: string | null;
  checkOutAt: string | null;
  checkInOdometer: number | null;
  checkOutOdometer: number | null;
  checkInFuelLevel: number | null;
  checkOutFuelLevel: number | null;
  notes: string | null;
};

type UploadIntentResponse = {
  fileId: string;
  bucket: string;
  objectKey: string;
  uploadUrl: string;
  expiresAt: string;
};

export async function createTripPhotoUploadIntent(
  bookingId: string,
  file: File,
  idempotencyKey: string,
): Promise<UploadIntentResponse> {
  return api.post<UploadIntentResponse>(
    `/bookings/${bookingId}/trip-photos/upload-intent`,
    {
      contentType: file.type || "application/octet-stream",
      sizeBytes: file.size,
    },
    { idempotencyKey },
  );
}

export async function uploadTripPhoto(
  bookingId: string,
  file: File,
  idempotencyKey: string,
): Promise<string> {
  const intent = await createTripPhotoUploadIntent(bookingId, file, idempotencyKey);
  await fetch(intent.uploadUrl, {
    method: "PUT",
    headers: { "Content-Type": file.type || "application/octet-stream" },
    body: file,
  });
  await api.post(`/files/${intent.fileId}/finalize`, {});
  return intent.fileId;
}

export async function createConditionReport(
  bookingId: string,
  input: CreateConditionReportInput,
  idempotencyKey: string,
): Promise<TripConditionReport> {
  return api.post<TripConditionReport>(
    `/bookings/${bookingId}/condition-reports`,
    input,
    { idempotencyKey },
  );
}

export async function listConditionReports(bookingId: string): Promise<TripConditionReport[]> {
  return api.get<TripConditionReport[]>(`/bookings/${bookingId}/condition-reports`);
}

export async function getConditionReport(
  bookingId: string,
  reportId: string,
): Promise<TripConditionReport> {
  return api.get<TripConditionReport>(`/bookings/${bookingId}/condition-reports/${reportId}`);
}

export async function checkInTrip(
  bookingId: string,
  input: { odometer: number; fuelLevel: number; note?: string },
): Promise<TripRecordResponse> {
  return api.post<TripRecordResponse>(`/bookings/${bookingId}/check-in`, {
    odometer: input.odometer,
    fuelLevel: input.fuelLevel,
    note: input.note || null,
  });
}

export async function checkOutTrip(
  bookingId: string,
  input: { odometer: number; fuelLevel: number; note?: string },
): Promise<TripRecordResponse> {
  return api.post<TripRecordResponse>(`/bookings/${bookingId}/check-out`, {
    odometer: input.odometer,
    fuelLevel: input.fuelLevel,
    note: input.note || null,
  });
}
