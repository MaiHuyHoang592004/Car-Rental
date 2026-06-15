"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { toast } from "sonner";
import { z } from "zod";

import { AppShell } from "@/components/rentflow/app-shell";
import { ErrorBanner } from "@/components/rentflow/error-banner";
import { AuthCard } from "@/features/auth/auth-card";
import { AuthFormLayout } from "@/features/auth/auth-form-layout";
import { useAuth, type AuthUser } from "@/features/auth/auth-context";
import type { GuestIntentRedirect } from "@/features/auth/types";
import { ApiError } from "@/lib/api-error";
import { handleApiError } from "@/lib/handle-api-error";

const loginSchema = z.object({
  email: z.string().min(1, "Vui lòng nhập email.").email("Email không hợp lệ."),
  password: z.string().min(1, "Vui lòng nhập mật khẩu."),
});

type LoginForm = z.infer<typeof loginSchema>;

const SAFE_NEXT_PREFIX = /^\/(?!\/)/;

function resolveRedirect(nextPath: string, user: AuthUser): string {
  if (nextPath && SAFE_NEXT_PREFIX.test(nextPath)) {
    return nextPath;
  }
  if (user.roles.includes("ADMIN")) return "/admin";
  if (user.roles.includes("HOST")) return "/host/dashboard";
  return "/listings";
}

type LoginPageViewProps = {
  redirectIntent: GuestIntentRedirect;
};

export function LoginPageView({ redirectIntent }: LoginPageViewProps) {
  const router = useRouter();
  const { login } = useAuth();
  const [submitError, setSubmitError] = useState<ApiError | null>(null);

  const form = useForm<LoginForm>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: "", password: "" },
  });

  async function onSubmit(values: LoginForm) {
    setSubmitError(null);
    try {
      const user = await login(values);
      toast.success("Đăng nhập thành công");
      const target = resolveRedirect(redirectIntent.nextPath, user);
      router.replace(target);
      router.refresh();
    } catch (err) {
      let allFieldsHandled = false;
      handleApiError(err, {
        onFieldError: (field, message) => {
          if (field === "email" || field === "password") {
            form.setError(field as keyof LoginForm, { message });
            allFieldsHandled = true;
          } else {
            allFieldsHandled = false;
          }
        },
        onUnknown: (e) => setSubmitError(e),
        onNetwork: () =>
          setSubmitError(
            new ApiError(0, { code: "UNKNOWN_ERROR", message: "Lỗi không xác định." }),
          ),
      });
      // VALIDATION_ERROR with non-form field → also surface in banner.
      if (err instanceof ApiError && err.code === "VALIDATION_ERROR" && !allFieldsHandled) {
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
          title="Đăng nhập RentFlow"
          description="Tiếp tục để đặt xe, quản lý đăng tin hoặc vận hành hệ thống."
        >
          <form onSubmit={form.handleSubmit(onSubmit)} noValidate>
            <AuthFormLayout
              errorBanner={
                submitError ? (
                  <ErrorBanner
                    error={mapLoginError(submitError)}
                    title="Không thể đăng nhập"
                  />
                ) : null
              }
              footer={
                <p>
                  Chưa có tài khoản?{" "}
                  <Link href="/register" className="font-semibold text-primary hover:underline">
                    Tạo tài khoản
                  </Link>
                </p>
              }
            >
              <div>
                <label
                  htmlFor="login-email"
                  className="mb-1 block text-sm font-semibold text-foreground"
                >
                  Email
                </label>
                <input
                  id="login-email"
                  type="email"
                  autoComplete="email"
                  {...form.register("email")}
                  className="h-10 w-full rounded-lg border border-input bg-background px-3 text-sm text-foreground outline-none ring-primary/30 focus:ring-2"
                />
                {errors.email ? (
                  <p className="mt-1 text-xs text-red-700">{errors.email.message}</p>
                ) : null}
              </div>

              <div>
                <div className="mb-1 flex items-center justify-between gap-3">
                  <label
                    htmlFor="login-password"
                    className="block text-sm font-semibold text-foreground"
                  >
                    Mật khẩu
                  </label>
                  <Link
                    href="/forgot-password"
                    className="text-xs font-semibold text-primary hover:underline"
                  >
                    Quên mật khẩu?
                  </Link>
                </div>
                <input
                  id="login-password"
                  type="password"
                  autoComplete="current-password"
                  {...form.register("password")}
                  className="h-10 w-full rounded-lg border border-input bg-background px-3 text-sm text-foreground outline-none ring-primary/30 focus:ring-2"
                />
                {errors.password ? (
                  <p className="mt-1 text-xs text-red-700">{errors.password.message}</p>
                ) : null}
              </div>

              <button
                type="submit"
                disabled={isSubmitting}
                className="h-10 w-full rounded-full bg-primary text-sm font-semibold text-primary-foreground transition-opacity hover:opacity-90 disabled:opacity-60"
              >
                {isSubmitting ? "Đang đăng nhập..." : "Đăng nhập"}
              </button>
            </AuthFormLayout>
          </form>
        </AuthCard>
      </div>
    </AppShell>
  );
}

function mapLoginError(error: ApiError): ApiError {
  if (error.status === 401 || error.code === "AUTH_INVALID_CREDENTIALS") {
    return new ApiError(error.status, {
      code: error.code,
      message: "Email hoặc mật khẩu không đúng.",
      correlationId: error.correlationId,
    });
  }
  if (error.status === 423 || error.code === "USER_SUSPENDED") {
    return new ApiError(error.status, {
      code: error.code,
      message: "Tài khoản đã bị khóa. Liên hệ quản trị viên để hỗ trợ.",
      correlationId: error.correlationId,
    });
  }
  return error;
}
