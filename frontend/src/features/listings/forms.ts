import { z } from "zod";

export const listingFilterSchema = z
  .object({
    query: z.string(),
    city: z.string(),
    pickupDate: z.string(),
    returnDate: z.string(),
    category: z.enum([
      "ALL",
      "SEDAN",
      "SUV",
      "HATCHBACK",
      "PICKUP",
      "VAN",
      "MINIVAN",
      "SPORTS",
      "LUXURY",
      "ECONOMY",
      "MPV",
    ]),
    transmission: z.enum(["ALL", "AUTO", "MANUAL"]),
    fuelType: z.enum(["ALL", "PETROL", "GASOLINE", "DIESEL", "ELECTRIC", "HYBRID", "LPG", "EV"]),
    seats: z.string(),
    minPrice: z.string(),
    maxPrice: z.string(),
    instantBook: z.boolean(),
    minRating: z.string(),
    sort: z.enum(["NEWEST", "PRICE_ASC", "PRICE_DESC"]),
  })
  .superRefine((values, ctx) => {
    if (values.pickupDate && values.returnDate && values.returnDate <= values.pickupDate) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ["returnDate"],
        message: "Ngày trả xe phải sau ngày nhận xe.",
      });
    }
  });
