"use client";

import {
  AlertTriangle,
  Bell,
  Check,
  CheckCheck,
  CreditCard,
  MessageSquareText,
} from "lucide-react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { ComponentType } from "react";

import { AppShell } from "@/components/rentflow/app-shell";
import { PageHeader } from "@/components/rentflow/page-header";
import { Button } from "@/components/rentflow/ui/button";
import { PageSkeleton } from "@/components/rentflow/page-skeleton";
import { useAuth } from "@/features/auth/auth-context";
import { notificationQueryKeys } from "@/features/notifications/notification-bell";
import type { NotificationItem } from "@/features/notifications/types";
import {
  listMyNotifications,
  markAllNotificationsRead,
  markNotificationRead,
} from "@/features/notifications/api";
import { cn } from "@/lib/utils";

type NotificationMeta = {
  icon: ComponentType<{ className?: string; "aria-hidden"?: boolean | "true" | "false" }>;
  label: string;
  tone: string;
};

const TYPE_META: Record<string, NotificationMeta> = {
  DRIVER_VERIFICATION_EXPIRED: {
    icon: AlertTriangle,
    label: "Xác minh tài xế",
    tone: "bg-amber-50 text-amber-700 ring-amber-200",
  },
  LISTING_REJECTED: {
    icon: AlertTriangle,
    label: "Tin đăng",
    tone: "bg-rose-50 text-rose-700 ring-rose-200",
  },
  PAYMENT_VOID_RETRY_REQUIRED: {
    icon: CreditCard,
    label: "Thanh toán",
    tone: "bg-orange-50 text-orange-700 ring-orange-200",
  },
  PAYMENT_VOID_RETRY_RESOLVED: {
    icon: CreditCard,
    label: "Thanh toán",
    tone: "bg-emerald-50 text-emerald-700 ring-emerald-200",
  },
  PAYMENT_VOID_RETRY_FAILED_MAX_ATTEMPTS: {
    icon: CreditCard,
    label: "Thanh toán",
    tone: "bg-rose-50 text-rose-700 ring-rose-200",
  },
  SUPPORT_CASE_CREATED: {
    icon: MessageSquareText,
    label: "Hỗ trợ",
    tone: "bg-sky-50 text-sky-700 ring-sky-200",
  },
  SUPPORT_CASE_MESSAGE: {
    icon: MessageSquareText,
    label: "Hỗ trợ",
    tone: "bg-sky-50 text-sky-700 ring-sky-200",
  },
  SUPPORT_CASE_CLOSED: {
    icon: MessageSquareText,
    label: "Hỗ trợ",
    tone: "bg-slate-100 text-slate-700 ring-slate-200",
  },
  HOST_PAYOUT_CREATED: {
    icon: CreditCard,
    label: "Payout host",
    tone: "bg-emerald-50 text-emerald-700 ring-emerald-200",
  },
  HOST_PAYOUT_UPDATED: {
    icon: CreditCard,
    label: "Payout host",
    tone: "bg-emerald-50 text-emerald-700 ring-emerald-200",
  },
};

const DEFAULT_META: NotificationMeta = {
  icon: Bell,
  label: "Hệ thống",
  tone: "bg-slate-100 text-slate-700 ring-slate-200",
};

export function NotificationsPageView() {
  const { status } = useAuth();
  const queryClient = useQueryClient();
  const notificationsQuery = useQuery({
    queryKey: notificationQueryKeys.list,
    queryFn: ({ signal }) => listMyNotifications(signal),
    enabled: status === "authenticated",
  });

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: notificationQueryKeys.all });
  };

  const markReadMutation = useMutation({
    mutationFn: markNotificationRead,
    onSuccess: refresh,
  });
  const markAllMutation = useMutation({
    mutationFn: markAllNotificationsRead,
    onSuccess: refresh,
  });

  if (status === "loading" || notificationsQuery.isLoading) {
    return (
      <AppShell activePath="/notifications">
        <PageSkeleton message="Đang tải thông báo..." />
      </AppShell>
    );
  }

  if (notificationsQuery.isError) {
    return (
      <AppShell activePath="/notifications">
        <div className="mx-auto flex w-full max-w-5xl flex-col gap-6 px-4 py-8">
        <PageHeader
          title="Thông báo"
          description="Không tải được trung tâm thông báo. Vui lòng thử lại."
        />
        <section
          role="alert"
          className="flex flex-col gap-3 rounded-lg border border-rose-200 bg-rose-50 px-5 py-6 text-sm text-rose-800 sm:flex-row sm:items-center sm:justify-between"
        >
          <div className="flex items-start gap-3">
            <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0" aria-hidden="true" />
            <div>
              <p className="font-semibold">Có lỗi khi tải thông báo</p>
              <p className="mt-1 text-rose-700">Kết nối có thể đang tạm thời gián đoạn.</p>
            </div>
          </div>
          <Button type="button" variant="outline" onClick={() => void notificationsQuery.refetch()}>
            Thử lại
          </Button>
        </section>
        </div>
      </AppShell>
    );
  }

  const notifications = notificationsQuery.data?.content ?? [];
  const unreadCount = notifications.filter((item) => !item.readAt).length;

  return (
    <AppShell activePath="/notifications">
      <div className="mx-auto flex w-full max-w-5xl flex-col gap-6 px-4 py-8">
      <PageHeader
        title="Thông báo"
        description={`${unreadCount} thông báo chưa đọc`}
        actions={
          <Button
            disabled={unreadCount === 0 || markAllMutation.isPending}
            onClick={() => markAllMutation.mutate()}
            type="button"
            variant="outline"
          >
            <CheckCheck className="h-4 w-4" aria-hidden />
            Đánh dấu đã đọc
          </Button>
        }
      />

      <section className="overflow-hidden rounded-lg border border-slate-200 bg-white shadow-sm">
        {notifications.length === 0 ? (
          <div className="flex flex-col items-center justify-center gap-3 px-4 py-12 text-center text-sm text-slate-500">
            <span className="flex h-12 w-12 items-center justify-center rounded-full bg-slate-100 text-slate-500">
              <Bell className="h-5 w-5" aria-hidden="true" />
            </span>
            <div>
              <p className="font-semibold text-slate-900">Chưa có thông báo</p>
              <p className="mt-1">Các cập nhật về đặt xe, hỗ trợ và payout sẽ xuất hiện tại đây.</p>
            </div>
          </div>
        ) : (
          notifications.map((notification) => (
            <NotificationRow
              key={notification.id}
              notification={notification}
              onMarkRead={(id) => markReadMutation.mutate(id)}
              markReadPending={markReadMutation.isPending}
            />
          ))
        )}
      </section>
      </div>
    </AppShell>
  );
}

function NotificationRow({
  notification,
  onMarkRead,
  markReadPending,
}: {
  notification: NotificationItem;
  onMarkRead: (id: string) => void;
  markReadPending: boolean;
}) {
  const unread = !notification.readAt;
  const meta = TYPE_META[notification.type] ?? DEFAULT_META;
  const Icon = meta.icon;

  return (
    <article
      className={cn(
        "flex flex-col gap-4 border-b border-slate-200 px-4 py-4 last:border-b-0 sm:flex-row sm:items-start sm:justify-between",
        unread ? "bg-emerald-50/45" : "bg-white",
      )}
    >
      <div className="flex min-w-0 gap-3">
        <span
          className={cn(
            "mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-full ring-1",
            meta.tone,
          )}
        >
          <Icon className="h-4 w-4" aria-hidden="true" />
        </span>
        <div className="min-w-0 space-y-2">
          <div className="flex flex-wrap items-center gap-2">
            <span className={cn("rounded-full px-2 py-0.5 text-xs font-semibold ring-1", meta.tone)}>
              {meta.label}
            </span>
            {unread ? (
              <span className="rounded-full bg-emerald-600 px-2 py-0.5 text-xs font-semibold text-white">
                Chưa đọc
              </span>
            ) : (
              <span className="inline-flex items-center gap-1 text-xs font-semibold text-slate-500">
                <Check className="h-3.5 w-3.5" aria-hidden="true" />
                Đã đọc
              </span>
            )}
          </div>
          <div>
            <h2 className="text-sm font-semibold text-slate-950">{notification.title}</h2>
            <p className="mt-1 text-sm leading-6 text-slate-600">{notification.message}</p>
          </div>
          <p className="text-xs text-slate-400">{formatRelativeTime(notification.createdAt)}</p>
        </div>
      </div>
      {unread ? (
        <Button
          disabled={markReadPending}
          onClick={() => onMarkRead(notification.id)}
          type="button"
          variant="ghost"
          className="self-start"
        >
          Đã đọc
        </Button>
      ) : null}
    </article>
  );
}

export function formatRelativeTime(value: string, nowMs = Date.now()): string {
  const timestamp = new Date(value).getTime();
  if (Number.isNaN(timestamp)) {
    return "";
  }
  const diffSeconds = Math.max(0, Math.floor((nowMs - timestamp) / 1000));
  if (diffSeconds < 60) {
    return "Vừa xong";
  }
  const diffMinutes = Math.floor(diffSeconds / 60);
  if (diffMinutes < 60) {
    return `${diffMinutes} phút trước`;
  }
  const diffHours = Math.floor(diffMinutes / 60);
  if (diffHours < 24) {
    return `${diffHours} giờ trước`;
  }
  const diffDays = Math.floor(diffHours / 24);
  if (diffDays < 7) {
    return `${diffDays} ngày trước`;
  }
  return new Intl.DateTimeFormat("vi-VN", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
  }).format(timestamp);
}
