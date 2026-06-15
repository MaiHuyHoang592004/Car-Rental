import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), refresh: vi.fn() }),
  usePathname: () => "/notifications",
}));

const notificationApi = vi.hoisted(() => ({
  listMyNotifications: vi.fn(),
  markAllNotificationsRead: vi.fn(),
  markNotificationRead: vi.fn(),
}));

vi.mock("@/features/notifications/api", () => notificationApi);

import { AuthProvider, type AuthSession } from "@/features/auth/auth-context";
import { NotificationsPageView, formatRelativeTime } from "./notifications-page-view";

const authedSession: AuthSession = {
  accessToken: "access-token",
  user: {
    id: "u-1",
    email: "demo-customer@rentflow.local",
    emailVerified: true,
    roles: ["CUSTOMER"],
    fullName: "RentFlow Demo Customer",
    phone: null,
    dateOfBirth: null,
    addressLine: null,
    driverVerificationStatus: "NOT_SUBMITTED",
  },
};

function wrap(node: ReactNode) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <AuthProvider initialSession={authedSession}>
      <QueryClientProvider client={qc}>{node}</QueryClientProvider>
    </AuthProvider>,
  );
}

const notificationPage = {
  content: [
    {
      id: "n-1",
      type: "SUPPORT_CASE_MESSAGE",
      title: "Hỗ trợ đã phản hồi",
      message: "RentFlow đã gửi hướng dẫn điều chỉnh ngày nhận xe.",
      readAt: null,
      createdAt: "2026-06-15T07:45:00.000Z",
    },
    {
      id: "n-2",
      type: "HOST_PAYOUT_UPDATED",
      title: "Payout cuối tuần đã cập nhật",
      message: "Khoản thanh toán đang ở trạng thái đợi chuyển khoản.",
      readAt: "2026-06-15T08:00:00.000Z",
      createdAt: "2026-06-14T08:00:00.000Z",
    },
  ],
  page: 0,
  size: 50,
  totalElements: 2,
  totalPages: 1,
};

describe("NotificationsPageView", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders loading state", () => {
    notificationApi.listMyNotifications.mockReturnValue(new Promise(() => undefined));

    wrap(<NotificationsPageView />);

    expect(screen.getByText("Đang tải thông báo...")).toBeInTheDocument();
  });

  it("renders unread and read notifications with Vietnamese labels", async () => {
    notificationApi.listMyNotifications.mockResolvedValue(notificationPage);

    wrap(<NotificationsPageView />);

    expect(await screen.findByText("Hỗ trợ đã phản hồi")).toBeInTheDocument();
    expect(screen.getAllByText("Hỗ trợ").length).toBeGreaterThan(0);
    expect(screen.getByText("Payout host")).toBeInTheDocument();
    expect(screen.getByText("1 thông báo chưa đọc")).toBeInTheDocument();
    expect(screen.getByText("Chưa đọc")).toBeInTheDocument();
    expect(screen.getAllByText("Đã đọc").length).toBeGreaterThan(0);
  });

  it("marks a single notification as read and refreshes queries", async () => {
    notificationApi.listMyNotifications.mockResolvedValue(notificationPage);
    notificationApi.markNotificationRead.mockResolvedValue({
      ...notificationPage.content[0],
      readAt: "2026-06-15T08:00:00.000Z",
    });

    wrap(<NotificationsPageView />);

    await screen.findByText("Hỗ trợ đã phản hồi");
    await userEvent.click(screen.getByRole("button", { name: "Đã đọc" }));

    await waitFor(() => expect(notificationApi.markNotificationRead).toHaveBeenCalled());
    expect(notificationApi.markNotificationRead.mock.calls[0][0]).toBe("n-1");
  });

  it("marks all unread notifications as read", async () => {
    notificationApi.listMyNotifications.mockResolvedValue(notificationPage);
    notificationApi.markAllNotificationsRead.mockResolvedValue(1);

    wrap(<NotificationsPageView />);

    await screen.findByText("Hỗ trợ đã phản hồi");
    await userEvent.click(screen.getByRole("button", { name: "Đánh dấu đã đọc" }));

    await waitFor(() => expect(notificationApi.markAllNotificationsRead).toHaveBeenCalledTimes(1));
  });

  it("renders error state with retry action", async () => {
    notificationApi.listMyNotifications
      .mockRejectedValueOnce(new Error("network"))
      .mockResolvedValueOnce({ ...notificationPage, content: [] });

    wrap(<NotificationsPageView />);

    expect(await screen.findByRole("alert")).toHaveTextContent("Có lỗi khi tải thông báo");
    await userEvent.click(screen.getByRole("button", { name: "Thử lại" }));

    await waitFor(() => expect(notificationApi.listMyNotifications).toHaveBeenCalledTimes(2));
  });

  it("formats notification timestamps relatively", () => {
    expect(formatRelativeTime("2026-06-15T08:55:00.000Z", Date.parse("2026-06-15T09:00:00.000Z"))).toBe(
      "5 phút trước",
    );
  });
});
