import { z } from "zod";

import { validateBookingForm } from "@/features/bookings/date-utils";

export function sanitizeCancellationReason(input: string): string {
  return input.replace(/<[^>]*>/g, "").trim();
}

export const bookingCreateSchema = z
  .object({
    pickupDate: z.string(),
    returnDate: z.string(),
    pickupLocation: z.string(),
    returnLocation: z.string(),
    selectedExtraIds: z.array(z.string()),
    protectionPlanCode: z.string().default("BASIC"),
  })
  .superRefine((values, ctx) => {
    const errors = validateBookingForm(values);
    (["pickupDate", "returnDate", "pickupLocation", "returnLocation"] as const).forEach((field) => {
      if (errors[field]) {
        ctx.addIssue({ code: z.ZodIssueCode.custom, path: [field], message: errors[field] });
      }
    });
    if (errors.form) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, path: ["root"], message: errors.form });
    }
  });

export type BookingCreateFormState = z.infer<typeof bookingCreateSchema>;

export const bookingLocationPatchSchema = z
  .object({
    pickupLocation: z.string(),
    returnLocation: z.string(),
  })
  .superRefine((values, ctx) => {
    if (!values.pickupLocation.trim() && !values.returnLocation.trim()) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ["root"],
        message: "Provide at least one location.",
      });
    }
  });

export type BookingLocationPatchFormState = z.infer<typeof bookingLocationPatchSchema>;

export const cancelBookingSchema = z.object({
  reason: z
    .string()
    .transform(sanitizeCancellationReason)
    .refine((reason) => reason.length <= 500, "Reason must be 500 characters or fewer."),
});

export type CancelBookingFormState = z.infer<typeof cancelBookingSchema>;
