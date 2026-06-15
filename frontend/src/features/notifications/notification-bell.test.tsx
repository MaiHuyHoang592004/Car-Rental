import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

const notificationApi = vi.hoisted(() => ({
  getUnreadNotificationCount: vi.fn(),
}));

vi.mock("next/link", () => ({
  default: ({
    href,
    children,
    ...props
  }: React.AnchorHTMLAttributes<HTMLAnchorElement> & { href: string }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

vi.mock("@/features/notifications/api", () => notificationApi);

import { NotificationBell } from "./notification-bell";

function wrap(node: ReactNode) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={qc}>{node}</QueryClientProvider>);
}

describe("NotificationBell", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("links to notifications and shows unread badge", async () => {
    notificationApi.getUnreadNotificationCount.mockResolvedValue(3);

    wrap(<NotificationBell enableLiveCount />);

    const link = await screen.findByRole("link", { name: "Thông báo, 3 chưa đọc" });
    expect(link).toHaveAttribute("href", "/notifications");
    expect(screen.getByText("3")).toBeInTheDocument();
  });

  it("renders mobile label without a badge when count is zero", async () => {
    notificationApi.getUnreadNotificationCount.mockResolvedValue(0);

    wrap(<NotificationBell enableLiveCount variant="mobile" />);

    const link = await screen.findByRole("link", { name: "Thông báo" });
    expect(link).toHaveAttribute("href", "/notifications");
    expect(screen.queryByText("0")).not.toBeInTheDocument();
  });
});
