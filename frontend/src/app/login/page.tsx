"use client";

import { useEffect, Suspense } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Button, Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui";
import { useAuth } from "@/hooks/use-auth";

function LoginContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { login, isAuthenticated, isLoading } = useAuth();

  useEffect(() => {
    // If already authenticated, redirect to dashboard
    if (isAuthenticated) {
      router.push("/dashboard");
    }
  }, [isAuthenticated, router]);

  useEffect(() => {
    // Check for OAuth callback parameters
    const code = searchParams.get("code");
    const state = searchParams.get("state");

    if (code && state) {
      // Handle OAuth callback
      handleOAuthCallback(code, state);
    }
  }, [searchParams]);

  const handleOAuthCallback = async (code: string, state: string) => {
    try {
      // Exchange code for token
      const response = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/oauth/callback?code=${code}&state=${state}`);
      
      if (response.ok) {
        const data = await response.json();
        // Store token and redirect
        localStorage.setItem("access_token", data.access_token);
        router.push("/dashboard");
      } else {
        console.error("OAuth callback failed");
      }
    } catch (error) {
      console.error("OAuth error:", error);
    }
  };

  const handleLogin = () => {
    try {
      // Redirect to OAuth authorization URL
      const authUrl = `${process.env.NEXT_PUBLIC_API_URL}/oauth/authorize`;
      window.location.href = authUrl;
    } catch (error) {
      console.error("Login failed:", error);
    }
  };

  if (isLoading) {
    return (
      <div className="flex h-screen items-center justify-center">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary"></div>
      </div>
    );
  }

  return (
    <div className="flex h-screen items-center justify-center bg-gradient-to-br from-background to-muted">
      <Card className="w-full max-w-md">
        <CardHeader className="space-y-1 text-center">
          <div className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-2xl bg-primary">
            <span className="text-3xl font-bold text-primary-foreground">EA</span>
          </div>
          <CardTitle className="text-2xl font-bold">Enterprise Agent</CardTitle>
          <CardDescription>
            AI-Powered ServiceNow Assistant
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="space-y-2 text-center text-sm text-muted-foreground">
            <p>
              Sign in with your ServiceNow account to access intelligent incident management and AI-powered automation.
            </p>
          </div>
          <Button
            onClick={handleLogin}
            className="w-full"
            size="lg"
            disabled={isLoading}
          >
            {isLoading ? "Connecting..." : "Sign in with ServiceNow"}
          </Button>
          <div className="text-center text-xs text-muted-foreground">
            By signing in, you agree to our Terms of Service and Privacy Policy
          </div>
        </CardContent>
      </Card>
    </div>
  );
}

export default function LoginPage() {
  return (
    <Suspense
      fallback={
        <div className="flex h-screen items-center justify-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary"></div>
        </div>
      }
    >
      <LoginContent />
    </Suspense>
  );
}
