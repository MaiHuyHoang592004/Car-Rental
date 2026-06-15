"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState, type ReactNode } from "react";
import { CarFront, CircleHelp, LayoutGrid, Menu, User, X } from "lucide-react";

import { UserMenu } from "@/components/rentflow/user-menu";
import { useAuth, type AuthRole } from "@/features/auth/auth-context";
import { NotificationBell } from "@/features/notifications/notification-bell";
import { cn } from "@/lib/utils";

type NavItem = {
  href: string;
  label: string;
  visibleTo: "public" | "authenticated" | AuthRole;
};

const NAV: NavItem[] = [
  { href: "/", label: "Trang chủ", visibleTo: "public" },
  { href: "/listings", label: "Tìm xe", visibleTo: "public" },
  { href: "/me/bookings", label: "Đơn của tôi", visibleTo: "authenticated" },
  { href: "/host/dashboard", label: "Chu xe", visibleTo: "HOST" },
  { href: "/admin", label: "Admin", visibleTo: "ADMIN" },
];

type AppShellProps = {
  children: ReactNode;
  activePath?: string;
};

export function AppShell({ children, activePath }: AppShellProps) {
  const pathname = usePathname();
  const active = activePath ?? pathname ?? "/";
  const { status, hasRole } = useAuth();
  const [mobileOpen, setMobileOpen] = useState(false);

  const visibleNav = NAV.filter((item) => {
    switch (item.visibleTo) {
      case "public":
        return true;
      case "authenticated":
        return status === "authenticated";
      default:
        return status === "authenticated" && hasRole(item.visibleTo);
    }
  });

  useEffect(() => {
    if (!mobileOpen) return;
    function onKey(event: KeyboardEvent) {
      if (event.key === "Escape") setMobileOpen(false);
    }
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [mobileOpen]);

  return (
    <div className="min-h-screen bg-background">
      <header className="sticky top-0 z-20 border-b border-border/70 bg-background/95 backdrop-blur">
        <div className="rf-shell-container flex h-18 items-center justify-between gap-4 py-3">
          <div className="flex items-center gap-3">
            <button
              type="button"
              aria-label="Mở menu"
              aria-expanded={mobileOpen}
              aria-controls="mobile-nav"
              onClick={() => setMobileOpen((open) => !open)}
              className="inline-flex h-10 w-10 items-center justify-center rounded-xl border border-border bg-card text-foreground transition-colors hover:bg-accent md:hidden"
            >
              <Menu className="h-5 w-5" aria-hidden="true" />
            </button>
            <Link href="/" className="font-heading text-2xl font-bold tracking-tight text-primary">
              RentFlow
            </Link>
          </div>
          <nav className="hidden items-center gap-7 md:flex">
            {visibleNav.map((item) => (
              <Link
                key={item.href}
                href={item.href}
                className={cn(
                  "border-b-2 border-transparent pb-1 text-sm font-semibold text-muted-foreground transition-colors hover:text-primary",
                  isActive(active, item.href) && "border-primary text-primary",
                )}
              >
                {item.label}
              </Link>
            ))}
            <Link
              href="/host/dashboard"
              className="text-sm font-semibold text-muted-foreground transition-colors hover:text-primary"
            >
              Trở thành chủ xe
            </Link>
            <a
              href="#how-it-works"
              className="text-sm font-semibold text-muted-foreground transition-colors hover:text-primary"
            >
              Hướng dẫn
            </a>
          </nav>
          <UserMenu />
        </div>
      </header>

      {mobileOpen ? (
        <div className="fixed inset-0 z-30 md:hidden" role="dialog" aria-modal="true">
          <div
            className="absolute inset-0 bg-foreground/40"
            onClick={() => setMobileOpen(false)}
            aria-hidden="true"
          />
          <nav
            id="mobile-nav"
            aria-label="Điều hướng chính"
            className="absolute left-0 top-0 h-full w-72 max-w-[84vw] overflow-y-auto border-r border-border bg-card p-4 shadow-lg"
          >
            <div className="mb-4 flex items-center justify-between">
              <span className="font-heading text-lg font-bold text-primary">RentFlow</span>
              <button
                type="button"
                aria-label="Đóng menu"
                onClick={() => setMobileOpen(false)}
                className="inline-flex h-8 w-8 items-center justify-center rounded-md text-foreground hover:bg-accent"
              >
                <X className="h-5 w-5" aria-hidden="true" />
              </button>
            </div>
            <ul className="space-y-1">
              {visibleNav.map((item) => (
                <li key={item.href}>
                  <Link
                    href={item.href}
                    onClick={() => setMobileOpen(false)}
                    className={cn(
                      "block rounded-lg px-3 py-2 text-sm font-semibold text-muted-foreground transition-colors hover:bg-accent hover:text-foreground",
                      isActive(active, item.href) && "bg-accent text-foreground",
                    )}
                  >
                    {item.label}
                  </Link>
                </li>
              ))}
              <li>
                <Link
                  href="/host/dashboard"
                  onClick={() => setMobileOpen(false)}
                  className="block rounded-lg px-3 py-2 text-sm font-semibold text-muted-foreground transition-colors hover:bg-accent hover:text-foreground"
                >
                  Trở thành chủ xe
                </Link>
              </li>
            </ul>
          </nav>
        </div>
      ) : null}

      <main className="rf-shell-container py-8 md:py-10">{children}</main>

      <footer className="mt-12 border-t border-border/70 bg-card">
        <div className="rf-shell-container flex flex-col gap-8 py-10 md:flex-row md:items-start md:justify-between">
          <div className="max-w-sm space-y-3">
            <p className="font-heading text-2xl font-bold text-foreground">RentFlow</p>
            <p className="text-sm leading-6 text-muted-foreground">
              Nền tảng thuê xe tự lái minh bạch, tập trung vào trải nghiệm đặt xe rõ ràng và an toàn.
            </p>
          </div>
          <div className="grid grid-cols-2 gap-6 text-sm text-muted-foreground sm:grid-cols-3">
            <FooterColumn
              title="Khám phá"
              links={[
                { href: "/listings", label: "Tìm xe" },
                { href: "/me/bookings", label: "Chuyến đi" },
              ]}
            />
            <FooterColumn
              title="Host"
              links={[
                { href: "/host/dashboard", label: "Bảng điều khiển" },
                { href: "/host/vehicles", label: "Quản lý xe" },
              ]}
            />
            <FooterColumn
              title="Hỗ trợ"
              links={[
                { href: "/me/profile", label: "Tài khoản" },
                { href: "/forbidden", label: "Chính sách" },
              ]}
            />
          </div>
        </div>
      </footer>

      <div className="fixed inset-x-4 bottom-4 z-20 md:hidden">
        <div className="grid grid-cols-4 rounded-2xl border border-border/70 bg-card/95 p-2 shadow-[0_20px_48px_-24px_rgba(15,23,42,0.45)] backdrop-blur">
          <MobileNavItem href="/listings" label="Tìm xe" active={isActive(active, "/listings")}>
            <LayoutGrid className="h-4 w-4" />
          </MobileNavItem>
          <MobileNavItem href="/me/bookings" label="Chuyến đi" active={isActive(active, "/me/bookings")}>
            <CarFront className="h-4 w-4" />
          </MobileNavItem>
          <MobileNavItem href="/host/dashboard" label="Host" active={isActive(active, "/host/dashboard")}>
            <User className="h-4 w-4" />
          </MobileNavItem>
          {status === "authenticated" ? (
            <NotificationBell variant="mobile" active={isActive(active, "/notifications")} />
          ) : (
            <MobileNavItem href="/" label="Hỗ trợ" active={active === "/"}>
              <CircleHelp className="h-4 w-4" />
            </MobileNavItem>
          )}
        </div>
      </div>
    </div>
  );
}

function isActive(current: string, href: string): boolean {
  if (href === "/") {
    return current === "/";
  }
  return current === href || current.startsWith(`${href}/`);
}

function FooterColumn({
  title,
  links,
}: {
  title: string;
  links: { href: string; label: string }[];
}) {
  return (
    <div className="space-y-2">
      <p className="font-semibold uppercase tracking-[0.16em] text-foreground/80">{title}</p>
      {links.map((link) => (
        <Link key={link.href} href={link.href} className="block hover:text-primary">
          {link.label}
        </Link>
      ))}
    </div>
  );
}

function MobileNavItem({
  href,
  label,
  active,
  children,
}: {
  href: string;
  label: string;
  active: boolean;
  children: ReactNode;
}) {
  return (
    <Link
      href={href}
      className={cn(
        "flex flex-col items-center gap-1 rounded-xl px-2 py-2 text-[11px] font-semibold transition-colors",
        active ? "bg-primary/8 text-primary" : "text-muted-foreground",
      )}
    >
      {children}
      <span>{label}</span>
    </Link>
  );
}
