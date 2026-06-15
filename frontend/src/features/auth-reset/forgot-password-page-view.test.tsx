import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("next/navigation", () => ({
  usePathname: () => "/forgot-password",
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

import { ForgotPasswordPageView } from "./forgot-password-page-view";

describe("ForgotPasswordPageView", () => {
  let fetchSpy: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    fetchSpy = vi.fn().mockResolvedValue(new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetchSpy);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("validates email before submitting", async () => {
    render(<ForgotPasswordPageView />);

    await userEvent.click(screen.getByRole("button", { name: "Gửi hướng dẫn đặt lại mật khẩu" }));

    expect(await screen.findByText("Vui lòng nhập email.")).toBeInTheDocument();
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it("posts forgot-password request and shows neutral success copy", async () => {
    render(<ForgotPasswordPageView />);

    await userEvent.type(screen.getByLabelText("Email"), "alice@example.com");
    await userEvent.click(screen.getByRole("button", { name: "Gửi hướng dẫn đặt lại mật khẩu" }));

    await screen.findByText("Kiểm tra hộp thư của bạn");
    expect(screen.getByText("Nếu email tồn tại, RentFlow đã gửi hướng dẫn đặt lại mật khẩu.")).toBeInTheDocument();
    await waitFor(() => expect(fetchSpy).toHaveBeenCalledWith(
      "/api/v1/auth/forgot-password",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ email: "alice@example.com" }),
        credentials: "omit",
      }),
    ));
  });
});
