import { api } from "@/lib/api-client";
import type { ProtectionPlan } from "@/features/protection/types";

type RawProtectionPlan = {
  id: string;
  code: string;
  name: string;
  description: string;
  priceType: "PER_DAY" | "PER_TRIP";
  priceAmount: number | string;
  deductibleAmount: number | string;
  maxCoverageAmount: number | string | null;
  active: boolean;
};

function toNumber(value: number | string | null | undefined): number {
  if (value === null || value === undefined) return 0;
  if (typeof value === "number") return value;
  const parsed = Number.parseFloat(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

function mapPlan(raw: RawProtectionPlan): ProtectionPlan {
  return {
    id: raw.id,
    code: raw.code,
    name: raw.name,
    description: raw.description,
    priceType: raw.priceType,
    priceAmount: toNumber(raw.priceAmount),
    deductibleAmount: toNumber(raw.deductibleAmount),
    maxCoverageAmount: raw.maxCoverageAmount == null ? null : toNumber(raw.maxCoverageAmount),
    active: raw.active,
  };
}

export async function listProtectionPlans(signal?: AbortSignal): Promise<ProtectionPlan[]> {
  const raw = await api.get<{ items: RawProtectionPlan[] }>("/protection-plans", { signal, skipAuth: true });
  return (raw.items ?? []).map(mapPlan);
}
