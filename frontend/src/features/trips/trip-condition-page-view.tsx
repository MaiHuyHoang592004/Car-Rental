"use client";

import { AppShell } from "@/components/rentflow/app-shell";
import { PageHeader } from "@/components/rentflow/page-header";
import { TripConditionForm } from "@/features/trips/trip-condition-form";
import type { TripConditionReportType } from "@/features/trips/api";

type TripConditionPageViewProps = {
  bookingId: string;
  reportType: TripConditionReportType;
};

const copyByType: Record<TripConditionReportType, { title: string; description: string }> = {
  CHECK_IN: {
    title: "Nhan xe",
    description: "Ghi nhan tinh trang xe truoc khi bat dau chuyen di.",
  },
  CHECK_OUT: {
    title: "Tra xe",
    description: "Ghi nhan tinh trang xe khi ket thuc chuyen di.",
  },
};

export function TripConditionPageView({ bookingId, reportType }: TripConditionPageViewProps) {
  const copy = copyByType[reportType];
  return (
    <AppShell activePath="/me/bookings">
      <PageHeader title={copy.title} description={copy.description} />
      <TripConditionForm bookingId={bookingId} reportType={reportType} />
    </AppShell>
  );
}
