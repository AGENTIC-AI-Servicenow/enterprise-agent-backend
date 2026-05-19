"use client";

import { Bell, Search, User, LogOut, RefreshCcw } from "lucide-react";
import { Input, Button } from "@/components/ui";
import { ThemeToggle } from "@/components/theme-toggle";
import { useAuth } from "@/hooks/use-auth";

interface HeaderProps {
  title: string;
  subtitle?: string;
}

export function Header({ title, subtitle }: HeaderProps) {
  const { isAuthenticated, logout, initiateOAuth } = useAuth();

  const handleReloadAuth = () => {
    initiateOAuth();
  };

  const handleLogout = () => {
    logout();
    window.location.href = "/login";
  };

  return (
    <header className="sticky top-0 z-20 flex h-16 items-center justify-between px-6 backdrop-blur-xl bg-white/70 border-b border-white/40">
      <div>
        <h1 className="text-xl font-semibold tracking-tight">{title}</h1>
        {subtitle && (
          <p className="text-xs text-muted-foreground mt-0.5">
            {subtitle}
          </p>
        )}
      </div>

        <div className="flex items-center space-x-3">
        {/* Search */}
        <div className="relative hidden md:block">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            type="search"
            placeholder="Search incidents..."
            className="w-64 pl-9"
          />
        </div>

        <ThemeToggle />

        <button
          className="relative rounded-full p-2 transition-all duration-200 hover:bg-primary/10"
          aria-label="Notifications"
        >
          <Bell className="h-5 w-5" />
          <span className="absolute right-1 top-1 h-2 w-2 rounded-full bg-destructive" />
        </button>

        {isAuthenticated && (
          <Button
            variant="outline"
            size="sm"
            onClick={handleReloadAuth}
            className="flex items-center gap-2"
          >
            <RefreshCcw className="h-4 w-4" />
            Reload Auth
          </Button>
        )}

        <div className="flex h-8 w-8 items-center justify-center rounded-full bg-primary text-primary-foreground">
          <User className="h-4 w-4" />
        </div>
      </div>
    </header>
  );
}
