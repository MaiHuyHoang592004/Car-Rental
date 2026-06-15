import { TripConditionPageView } from "@/features/trips/trip-condition-page-view";

type CheckInPageProps = {
  params: Promise<{ id: string }>;
};

export default async function CheckInPage({ params }: CheckInPageProps) {
  const { id } = await params;
  return <TripConditionPageView bookingId={id} reportType="CHECK_IN" />;
}
