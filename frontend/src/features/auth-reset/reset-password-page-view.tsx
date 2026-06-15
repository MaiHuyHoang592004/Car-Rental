"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";

import { AppShell } from "@/components/rentflow/app-shell";
import { ErrorBanner } from "@/components/rentflow/error-banner";
import { AuthCard } from "@/features/auth/auth-card";
import { AuthFormLayout } from "@/features/auth/auth-form-layout";
import { resetPassword } from "@/features/auth-reset/api";
import { ApiError } from "@/lib/api-error";
import { handleApiError } from "@/lib/handle-api-error";

const resetPasswordSchema = z
  .object({
    newPassword: z.string().min(8, "Mật khẩu mới phải có ít nhất 8 ký tự."),
    confirmPassword: z.string().min(1, "Vui lòng xác nhận mật khẩu mới."),
  })
  .refine((values) => values.newPassword === values.confirmPassword, {
    path: ["confirmPassword"],
    message: "Mật khẩu xác nhận không khớp.",
  });

type ResetPasswordForm = z.infer<typeof resetPasswordSchema>;

export function ResetPasswordPageView() {
  const params = useSearchParams();
  const token = params.get("token") ?? "";
  const [submitError, setSubmitError] = useState<ApiError | null>(
    token
      ? null
      : new ApiError(400, {
          code: "VALIDATION_ERROR",
          message: "Liên kết đặt lại mật khẩu không hợp lệ hoặc thiếu token.",
        }),
  );
  const [resetDone, setResetDone] = useState(false);

  const form = useForm<ResetPasswordForm>({
    resolver: zodResolver(resetPasswordSchema),
    defaultValues: { newPassword: "", confirmPassword: "" },
  });

  async function onSubmit(values: ResetPasswordForm) {
    if (!token) {
      setSubmitError(
        new ApiError(400, {
          code: "VALIDATION_ERROR",
          message: "Liên kết đặt lại mật khẩu không hợp lệ hoặc thiếu token.",
        }),
      );
      return;
    }

    setSubmitError(null);
    try {
      await resetPassword(token, values.newPassword);
      setResetDone(true);
      form.reset();
    } catch (err) {
      let fieldHandled = false;
      handleApiError(err, {
        onFieldError: (field, message) => {
          if (field === "newPassword") {
            form.setError("newPassword", { message });
            fieldHandled = true;
          }
        },
        onUnknown: (e) => setSubmitError(mapResetError(e)),
        onNetwork: () =>
          setSubmitError(
            new ApiError(0, { code: "UNKNOWN_ERROR", message: "Lỗi không xác định." }),
          ),
      });
      if (err instanceof ApiError && err.code === "VALIDATION_ERROR" && !fieldHandled) {
        setSubmitError(mapResetError(err));
      }
    }
  }

  const errors = form.formState.errors;
  const isSubmitting = form.formState.isSubmitting;

  return (
    <AppShell activePath="/login">
      <div className="py-6">
        <AuthCard
          title="Đặt lại mật khẩu"
          description="Tạo mật khẩu mới cho tài khoản RentFlow của bạn."
        >
          {resetDone ? (
            <div className="space-y-4">
              <div className="rounded-lg border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-900">
                <p className="font-semibold">Đã đặt lại mật khẩu</p>
                <p className="mt-1">Bạn có thể đăng nhập bằng mật khẩu mới.</p>
              </div>
              <div className="border-t border-border pt-4 text-sm text-muted-foreground">
                <Link href="/login" className="font-semibold text-primary hover:underline">
                  Về trang đăng nhập
                </Link>
              </div>
            </div>
          ) : (
            <form onSubmit={form.handleSubmit(onSubmit)} noValidate>
              <AuthFormLayout
                errorBanner={
                  submitError ? (
                    <ErrorBanner error={submitError} title="Không thể đặt lại mật khẩu" />
                  ) : null
                }
                footer={
                  <p>
                    Cần yêu cầu link mới?{" "}
                    <Link
                      href="/forgot-password"
                      className="font-semibold text-primary hover:underline"
                    >
                      Quên mật khẩu
                    </Link>
                  </p>
                }
              >
                <div>
                  <label
                    htmlFor="reset-password-new"
                    className="mb-1 block text-sm font-semibold text-foreground"
                  >
                    Mật khẩu mới
                  </label>
                  <input
                    id="reset-password-new"
                    type="password"
                    autoComplete="new-password"
                    disabled={!token}
                    {...form.register("newPassword")}
                    className="h-10 w-full rounded-lg border border-input bg-background px-3 text-sm text-foreground outline-none ring-primary/30 focus:ring-2 disabled:opacity-60"
                  />
                  {errors.newPassword ? (
                    <p className="mt-1 text-xs text-red-700">{errors.newPassword.message}</p>
                  ) : null}
                </div>

                <div>
                  <label
                    htmlFor="reset-password-confirm"
                    className="mb-1 block text-sm font-semibold text-foreground"
                  >
                    Xác nhận mật khẩu mới
                  </label>
                  <input
                    id="reset-password-confirm"
                    type="password"
                    autoComplete="new-password"
                    disabled={!token}
                    {...form.register("confirmPassword")}
                    className="h-10 w-full rounded-lg border border-input bg-background px-3 text-sm text-foreground outline-none ring-primary/30 focus:ring-2 disabled:opacity-60"
                  />
                  {errors.confirmPassword ? (
                    <p className="mt-1 text-xs text-red-700">
                      {errors.confirmPassword.message}
                    </p>
                  ) : null}
                </div>

                <button
                  type="submit"
                  disabled={isSubmitting || !token}
                  className="h-10 w-full rounded-full bg-primary text-sm font-semibold text-primary-foreground transition-opacity hover:opacity-90 disabled:opacity-60"
                >
                  {isSubmitting ? "Đang đặt lại..." : "Đặt lại mật khẩu"}
                </button>
              </AuthFormLayout>
            </form>
          )}
        </AuthCard>
      </div>
    </AppShell>
  );
}

function mapResetError(error: ApiError): ApiError {
  if (error.code === "INVALID_TOKEN" || error.code === "TOKEN_INVALID" || error.code === "TOKEN_EXPIRED") {
    return new ApiError(error.status, {
      code: error.code,
      message: "Liên kết đặt lại mật khẩu đã hết hạn hoặc không hợp lệ. Vui lòng yêu cầu link mới.",
      correlationId: error.correlationId,
    });
  }
  return error;
}
