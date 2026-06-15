import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

vi.mock("next/navigation", () => ({
  usePathname: () => "/login",
  useRouter: () => ({ replace: vi.fn(), refresh: vi.fn() }),
}));

vi.mock("sonner", () => ({
  toast: { success: vi.fn() },
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

import { LoginPageView } from "./login-page-view";

describe("LoginPageView", () => {
  it("renders forgot password link", () => {
    render(<LoginPageView redirectIntent={{ nextPath: "/listings" }} />);

    const link = screen.getByRole("link", { name: "Quên mật khẩu?" });
    expect(link).toHaveAttribute("href", "/forgot-password");
  });
});
