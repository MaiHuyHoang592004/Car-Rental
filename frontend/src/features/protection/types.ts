export type ProtectionPlan = {
  id: string;
  code: "BASIC" | "STANDARD" | "PREMIUM" | string;
  name: string;
  description: string;
  priceType: "PER_DAY" | "PER_TRIP";
  priceAmount: number;
  deductibleAmount: number;
  maxCoverageAmount: number | null;
  active: boolean;
};
