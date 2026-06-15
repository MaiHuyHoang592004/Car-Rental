"use client";

import Link from "next/link";
import { Bell } from "lucide-react";
import { useQuery } from "@tanstack/react-query";

import { getUnreadNotificationCount } from "@/features/notifications/api";
import { cn } from "@/lib/utils";

type NotificationBellProps = {
  active?: boolean;
  className?: string;
  enableLiveCount?: boolean;
  variant?: "header" | "mobile";
};

export const notificationQueryKeys = {
  all: ["notifications"] as const,
  list: ["notifications", "list"] as const,
  unreadCount: ["notifications", "unread-count"] as const,
};

export function NotificationBell({
  active = false,
  className,
  enableLiveCount,
  variant = "header",
}: NotificationBellProps) {
  const shouldFetchCount = enableLiveCount ?? process.env.NODE_ENV !== "test";
  const { data: unreadCount = 0 } = useQuery({
    queryKey: notificationQueryKeys.unreadCount,
    queryFn: async () => (await getUnreadNotificationCount()) ?? 0,
    enabled: shouldFetchCount,
    refetchInterval: 60_000,
    staleTime: 30_000,
  });
  const badge = unreadCount > 99 ? "99+" : String(unreadCount);
  const label = unreadCount > 0 ? `Thông báo, ${unreadCount} chưa đọc` : "Thông báo";

  if (variant === "mobile") {
    return (
      <Link
        href="/notifications"
        aria-label={label}
        className={cn(
          "flex flex-col items-center gap-1 rounded-xl px-2 py-2 text-[11px] font-semibold transition-colors",
          active ? "bg-primary/8 text-primary" : "text-muted-foreground",
          className,
        )}
      >
        <span className="relative">
          <Bell className="h-4 w-4" aria-hidden="true" />
          {unreadCount > 0 ? (
            <span className="absolute -right-2 -top-2 min-w-4 rounded-full bg-rose-600 px-1 text-[10px] leading-4 text-white">
              {badge}
            </span>
          ) : null}
        </span>
        <span>Thông báo</span>
      </Link>
    );
  }

  return (
    <Link
      href="/notifications"
      aria-label={label}
      className={cn(
        "relative inline-flex h-9 w-9 items-center justify-center rounded-full border border-border bg-background text-foreground transition-colors hover:bg-accent",
        active && "border-primary text-primary",
        className,
      )}
    >
      <Bell className="h-4 w-4" aria-hidden="true" />
      {unreadCount > 0 ? (
        <span className="absolute -right-1 -top-1 min-w-4 rounded-full bg-rose-600 px-1 text-center text-[10px] font-bold leading-4 text-white">
          {badge}
        </span>
      ) : null}
    </Link>
  );
}
