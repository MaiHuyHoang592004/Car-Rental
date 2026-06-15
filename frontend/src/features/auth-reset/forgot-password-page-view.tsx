"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";

import { AppShell } from "@/components/rentflow/app-shell";
import { ErrorBanner } from "@/components/rentflow/error-banner";
import { AuthCard } from "@/features/auth/auth-card";
import { AuthFormLayout } from "@/features/auth/auth-form-layout";
import { requestPasswordReset } from "@/features/auth-reset/api";
import { ApiError } from "@/lib/api-error";
import { handleApiError } from "@/lib/handle-api-error";

const forgotPasswordSchema = z.object({
  email: z.string().min(1, "Vui lòng nhập email.").email("Email không hợp lệ."),
});

type ForgotPasswordForm = z.infer<typeof forgotPasswordSchema>;

const SUCCESS_COPY = "Nếu email tồn tại, RentFlow đã gửi hướng dẫn đặt lại mật khẩu.";

export function ForgotPasswordPageView() {
  const [submitError, setSubmitError] = useState<ApiError | null>(null);
  const [submittedEmail, setSubmittedEmail] = useState<string | null>(null);

  const form = useForm<ForgotPasswordForm>({
    resolver: zodResolver(forgotPasswordSchema),
    defaultValues: { email: "" },
  });

  async function onSubmit(values: ForgotPasswordForm) {
    setSubmitError(null);
    try {
      await requestPasswordReset(values.email);
      setSubmittedEmail(values.email);
    } catch (err) {
      let fieldHandled = false;
      handleApiError(err, {
        onFieldError: (field, message) => {
          if (field === "email") {
            form.setError("email", { message });
            fieldHandled = true;
          }
        },
        onUnknown: (e) => setSubmitError(e),
        onNetwork: () =>
          setSubmitError(
            new ApiError(0, { code: "UNKNOWN_ERROR", message: "Lỗi không xác định." }),
          ),
      });
      if (err instanceof ApiError && err.code === "VALIDATION_ERROR" && !fieldHandled) {
        setSubmitError(err);
      }
    }
  }

  const errors = form.formState.errors;
  const isSubmitting = form.formState.isSubmitting;

  return (
    <AppShell activePath="/login">
      <div className="py-6">
        <AuthCard
          title="Quên mật khẩu"
          description="Nhập email tài khoản RentFlow để nhận liên kết đặt lại mật khẩu."
        >
          {submittedEmail ? (
            <div className="space-y-4">
              <div className="rounded-lg border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-900">
                <p className="font-semibold">Kiểm tra hộp thư của bạn</p>
                <p className="mt-1">{SUCCESS_COPY}</p>
              </div>
              <div className="border-t border-border pt-4 text-sm text-muted-foreground">
                <Link href="/login" className="font-semibold text-primary hover:underline">
                  Quay lại đăng nhập
                </Link>
              </div>
            </div>
          ) : (
            <form onSubmit={form.handleSubmit(onSubmit)} noValidate>
              <AuthFormLayout
                errorBanner={
                  submitError ? (
                    <ErrorBanner error={submitError} title="Không thể gửi yêu cầu" />
                  ) : null
                }
                footer={
                  <p>
                    Nhớ mật khẩu?{" "}
                    <Link href="/login" className="font-semibold text-primary hover:underline">
                      Đăng nhập
                    </Link>
                  </p>
                }
              >
                <div>
                  <label
                    htmlFor="forgot-password-email"
                    className="mb-1 block text-sm font-semibold text-foreground"
                  >
                    Email
                  </label>
                  <input
                    id="forgot-password-email"
                    type="email"
                    autoComplete="email"
                    {...form.register("email")}
                    className="h-10 w-full rounded-lg border border-input bg-background px-3 text-sm text-foreground outline-none ring-primary/30 focus:ring-2"
                  />
                  {errors.email ? (
                    <p className="mt-1 text-xs text-red-700">{errors.email.message}</p>
                  ) : null}
                </div>

                <button
                  type="submit"
                  disabled={isSubmitting}
                  className="h-10 w-full rounded-full bg-primary text-sm font-semibold text-primary-foreground transition-opacity hover:opacity-90 disabled:opacity-60"
                >
                  {isSubmitting ? "Đang gửi..." : "Gửi hướng dẫn đặt lại mật khẩu"}
                </button>
              </AuthFormLayout>
            </form>
          )}
        </AuthCard>
      </div>
    </AppShell>
  );
}
