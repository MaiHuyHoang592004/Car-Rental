export type RentalDocumentType =
  | "RENTAL_AGREEMENT"
  | "PAYMENT_RECEIPT"
  | "REFUND_RECEIPT"
  | "DAMAGE_INVOICE";

export type RentalDocumentStatus = "GENERATED" | "VOIDED";

export type RentalDocument = {
  id: string;
  bookingId: string;
  type: RentalDocumentType;
  status: RentalDocumentStatus;
  title: string;
  htmlContent: string;
  sourceEntityType: string | null;
  sourceEntityId: string | null;
  generatedBy: string | null;
  generatedAt: string;
  createdAt: string;
};
