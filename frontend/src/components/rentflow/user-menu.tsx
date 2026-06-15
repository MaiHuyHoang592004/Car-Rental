"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { ChevronDown, LogOut, ShieldOff, User as UserIcon, ClipboardList } from "lucide-react";

import { NotificationBell } from "@/features/notifications/notification-bell";
import { useAuth } from "@/features/auth/auth-context";
import { cn } from "@/lib/utils";

export function UserMenu() {
  const { status, user, logout, logoutAll } = useAuth();
  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    function onClick(event: MouseEvent) {
      if (!containerRef.current?.contains(event.target as Node)) {
        setOpen(false);
      }
    }
    if (open) {
      document.addEventListener("mousedown", onClick);
    }
    return () => document.removeEventListener("mousedown", onClick);
  }, [open]);

  if (status === "loading") {
    return <div className="h-9 w-24 animate-pulse rounded-full bg-muted" aria-hidden />;
  }

  if (status === "guest" || !user) {
    return (
      <div className="flex items-center gap-2">
        <Link
          href="/login"
          className="rounded-full border border-border bg-background px-3 py-1.5 text-sm font-semibold text-foreground hover:bg-accent"
        >
          Đăng nhập
        </Link>
        <Link
          href="/register"
          className="rounded-full bg-primary px-3 py-1.5 text-sm font-semibold text-primary-foreground hover:opacity-90"
        >
          Đăng ký
        </Link>
      </div>
    );
  }

  const initials = (user.fullName || user.email || "U")
    .split(/\s+/)
    .map((part) => part.charAt(0))
    .filter(Boolean)
    .slice(0, 2)
    .join("")
    .toUpperCase();

  return (
    <div className="flex items-center gap-2">
      <NotificationBell />
      <div className="relative" ref={containerRef}>
        <button
          type="button"
          onClick={() => setOpen((prev) => !prev)}
          aria-haspopup="menu"
          aria-expanded={open}
          className="flex items-center gap-2 rounded-full border border-border bg-background px-2 py-1 text-sm font-semibold text-foreground hover:bg-accent"
        >
          <span className="flex size-7 items-center justify-center rounded-full bg-primary text-xs font-bold text-primary-foreground">
            {initials}
          </span>
          <span className="hidden max-w-[140px] truncate sm:inline">{user.fullName || user.email}</span>
          <ChevronDown className="size-4 text-muted-foreground" />
        </button>

        <div
          role="menu"
          className={cn(
            "absolute right-0 z-30 mt-2 w-56 origin-top-right rounded-lg border border-border bg-popover p-1 shadow-lg",
            open ? "block" : "hidden",
          )}
        >
          <div className="px-3 py-2 text-xs text-muted-foreground">
            <p className="truncate text-foreground">{user.email}</p>
            <p className="mt-0.5 text-[11px] uppercase tracking-wide">{user.roles.join(" · ")}</p>
          </div>
          <div className="my-1 h-px bg-border" />
          <Link
            href="/me/profile"
            onClick={() => setOpen(false)}
            className="flex items-center gap-2 rounded-md px-3 py-2 text-sm text-foreground hover:bg-accent"
            role="menuitem"
          >
            <UserIcon className="size-4" /> Hồ sơ của tôi
          </Link>
          <Link
            href="/me/bookings"
            onClick={() => setOpen(false)}
            className="flex items-center gap-2 rounded-md px-3 py-2 text-sm text-foreground hover:bg-accent"
            role="menuitem"
          >
            <ClipboardList className="size-4" /> Đơn đặt của tôi
          </Link>
          <div className="my-1 h-px bg-border" />
          <button
            type="button"
            onClick={async () => {
              setOpen(false);
              await logoutAll();
            }}
            className="flex w-full items-center gap-2 rounded-md px-3 py-2 text-left text-sm text-foreground hover:bg-accent"
            role="menuitem"
          >
            <ShieldOff className="size-4" /> Đăng xuất mọi nơi
          </button>
          <button
            type="button"
            onClick={async () => {
              setOpen(false);
              await logout();
            }}
            className="flex w-full items-center gap-2 rounded-md px-3 py-2 text-left text-sm text-foreground hover:bg-accent"
            role="menuitem"
          >
            <LogOut className="size-4" /> Đăng xuất
          </button>
        </div>
      </div>
    </div>
  );
}
