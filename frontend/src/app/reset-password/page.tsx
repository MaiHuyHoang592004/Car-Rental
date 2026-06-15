import { Suspense } from "react";

import { PageSkeleton } from "@/components/rentflow/page-skeleton";
import { ResetPasswordPageView } from "@/features/auth-reset/reset-password-page-view";

export default function ResetPasswordPage() {
  return (
    <Suspense fallback={<PageSkeleton message="Đang tải đặt lại mật khẩu..." />}>
      <ResetPasswordPageView />
    </Suspense>
  );
}
