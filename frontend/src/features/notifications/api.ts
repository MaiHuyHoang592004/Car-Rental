import { api } from "@/lib/api-client";
import type { NotificationItem, NotificationPage } from "@/features/notifications/types";

export async function listMyNotifications(signal?: AbortSignal): Promise<NotificationPage> {
  return api.get<NotificationPage>("/notifications/me?page=0&size=50", { signal });
}

export async function getUnreadNotificationCount(): Promise<number> {
  const response = await api.get<{ unreadCount: number }>("/notifications/me/unread-count");
  return response.unreadCount;
}

export async function markNotificationRead(notificationId: string): Promise<NotificationItem> {
  return api.post<NotificationItem>(`/notifications/${notificationId}/read`, {});
}

export async function markAllNotificationsRead(): Promise<number> {
  const response = await api.post<{ updatedCount: number }>("/notifications/me/read-all", {});
  return response.updatedCount;
}
