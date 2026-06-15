import { TripConditionPageView } from "@/features/trips/trip-condition-page-view";

type CheckOutPageProps = {
  params: Promise<{ id: string }>;
};

export default async function CheckOutPage({ params }: CheckOutPageProps) {
  const { id } = await params;
  return <TripConditionPageView bookingId={id} reportType="CHECK_OUT" />;
}
