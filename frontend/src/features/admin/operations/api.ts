import { api } from "@/lib/api-client";

export type AdminOperationsQueue = {
  openDamageClaims: number;
  pendingBookingModifications: number;
  pendingLateReturnFees: number;
  openSupportCases: number;
  pendingHostPayouts: number;
  heldHostPayouts: number;
  openDisputes: number;
  paymentVoidRetries: number;
  totalOpenItems: number;
};

export async function getAdminOperationsQueue(): Promise<AdminOperationsQueue> {
  return api.get<AdminOperationsQueue>("/admin/operations/queues");
}
