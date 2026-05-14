"use client";

import { useEffect, Suspense } from "react";
import { useRouter } from "next/navigation";
import {
  Button,
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui";
import { useAuth } from "@/hooks/use-auth";

function LoginContent() {
  const router = useRouter();
  const { isAuthenticated, isLoading, initiateOAuth } = useAuth();

  // Removed automatic redirect to allow manual re-authentication.
  // If the user is already authenticated, they can still click the dashboard manually.

  if (isLoading) {
    return (
      <div className="flex h-screen items-center justify-center">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary" />
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
          <CardDescription>AI-Powered ServiceNow Assistant</CardDescription>
        </CardHeader>

        <CardContent className="space-y-4">
          <p className="text-center text-sm text-muted-foreground">
            Inicia sesión con tu cuenta de ServiceNow para acceder a la gestión
            inteligente de incidentes y automatización con IA.
          </p>

          <Button
            onClick={initiateOAuth}
            className="w-full"
            size="lg"
            disabled={isLoading}
          >
            {isLoading ? "Conectando..." : "Iniciar sesión con ServiceNow"}
          </Button>

          <p className="text-center text-xs text-muted-foreground">
            Al iniciar sesión aceptas los Términos de Servicio y la Política de
            Privacidad.
          </p>
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
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary" />
        </div>
      }
    >
      <LoginContent />
    </Suspense>
  );
}
