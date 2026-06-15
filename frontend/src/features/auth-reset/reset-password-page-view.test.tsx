import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({
  searchParams: "token=reset-token",
}));

vi.mock("next/navigation", () => ({
  usePathname: () => "/reset-password",
  useSearchParams: () => new URLSearchParams(mocks.searchParams),
}));

vi.mock("@/features/auth/auth-context", () => ({
  useAuth: () => ({
    status: "guest",
    user: null,
    roles: [],
    hasRole: () => false,
    login: vi.fn(),
    register: vi.fn(),
    logout: vi.fn(),
    logoutAll: vi.fn(),
    refresh: vi.fn(),
  }),
}));

import { ResetPasswordPageView } from "./reset-password-page-view";

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

describe("ResetPasswordPageView", () => {
  let fetchSpy: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    mocks.searchParams = "token=reset-token";
    fetchSpy = vi.fn().mockResolvedValue(new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetchSpy);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("shows missing-token error and does not submit", async () => {
    mocks.searchParams = "";

    render(<ResetPasswordPageView />);

    expect(screen.getByText("Liên kết đặt lại mật khẩu không hợp lệ hoặc thiếu token.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Đặt lại mật khẩu" })).toBeDisabled();
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it("validates password length and confirmation mismatch", async () => {
    render(<ResetPasswordPageView />);

    await userEvent.type(screen.getByLabelText("Mật khẩu mới"), "short");
    await userEvent.type(screen.getByLabelText("Xác nhận mật khẩu mới"), "different");
    await userEvent.click(screen.getByRole("button", { name: "Đặt lại mật khẩu" }));

    expect(await screen.findByText("Mật khẩu mới phải có ít nhất 8 ký tự.")).toBeInTheDocument();
    expect(screen.getByText("Mật khẩu xác nhận không khớp.")).toBeInTheDocument();
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it("posts reset-password request and shows login CTA", async () => {
    render(<ResetPasswordPageView />);

    await userEvent.type(screen.getByLabelText("Mật khẩu mới"), "NewPassword@123");
    await userEvent.type(screen.getByLabelText("Xác nhận mật khẩu mới"), "NewPassword@123");
    await userEvent.click(screen.getByRole("button", { name: "Đặt lại mật khẩu" }));

    await screen.findByText("Đã đặt lại mật khẩu");
    expect(screen.getByRole("link", { name: "Về trang đăng nhập" })).toHaveAttribute("href", "/login");
    await waitFor(() => expect(fetchSpy).toHaveBeenCalledWith(
      "/api/v1/auth/reset-password",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ token: "reset-token", newPassword: "NewPassword@123" }),
        credentials: "omit",
      }),
    ));
  });

  it("maps INVALID_TOKEN to friendly expired-link copy", async () => {
    fetchSpy.mockResolvedValueOnce(
      jsonResponse(
        {
          code: "INVALID_TOKEN",
          message: "Reset token is invalid or expired",
        },
        409,
      ),
    );

    render(<ResetPasswordPageView />);

    await userEvent.type(screen.getByLabelText("Mật khẩu mới"), "NewPassword@123");
    await userEvent.type(screen.getByLabelText("Xác nhận mật khẩu mới"), "NewPassword@123");
    await userEvent.click(screen.getByRole("button", { name: "Đặt lại mật khẩu" }));

    expect(await screen.findByText(/Liên kết đặt lại mật khẩu đã hết hạn hoặc không hợp lệ/)).toBeInTheDocument();
  });
});
