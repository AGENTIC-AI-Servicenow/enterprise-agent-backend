"use client";

import { Bell, Search, User } from "lucide-react";
import { Input } from "@/components/ui";
import { ThemeToggle } from "@/components/theme-toggle";

interface HeaderProps {
  title: string;
  subtitle?: string;
}

export function Header({ title, subtitle }: HeaderProps) {
  return (
    <header className="sticky top-0 z-10 flex h-16 items-center justify-between border-b bg-background px-6">
      <div>
        <h1 className="text-2xl font-semibold">{title}</h1>
        {subtitle && (
          <p className="text-sm text-muted-foreground">{subtitle}</p>
        )}
      </div>

      <div className="flex items-center space-x-4">
        {/* Search */}
        <div className="relative hidden md:block">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            type="search"
            placeholder="Search incidents..."
            className="w-64 pl-9"
          />
        </div>

        {/* Theme Toggle */}
        <ThemeToggle />

        {/* Notifications */}
        <button
          className="relative rounded-full p-2 hover:bg-accent"
          aria-label="Notifications"
        >
          <Bell className="h-5 w-5" />
          <span className="absolute right-1 top-1 h-2 w-2 rounded-full bg-destructive" />
        </button>

        {/* User menu */}
        <button
          className="flex items-center space-x-2 rounded-full p-2 hover:bg-accent"
          aria-label="User menu"
        >
          <div className="flex h-8 w-8 items-center justify-center rounded-full bg-primary text-primary-foreground">
            <User className="h-4 w-4" />
          </div>
        </button>
      </div>
    </header>
  );
}
