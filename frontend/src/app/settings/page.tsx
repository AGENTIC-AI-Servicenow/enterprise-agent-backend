"use client";

import { MainLayout } from "@/components/layout/main-layout";
import { Card, CardContent, CardHeader, CardTitle, Button, Badge } from "@/components/ui";
import { Switch } from "@/components/ui/switch";
import { useEffect, useMemo, useState } from "react";

const OLLAMA_ENABLED_KEY = "ea.settings.ollamaEnabled";

/**
 * Settings (MVP)
 * - Permite habilitar/deshabilitar el uso de Ollama en ambiente local.
 * - Persiste en localStorage.
 */
export default function SettingsPage() {
  const [ollamaEnabled, setOllamaEnabled] = useState<boolean>(true);

  useEffect(() => {
    try {
      const raw = localStorage.getItem(OLLAMA_ENABLED_KEY);
      if (raw === null) return;
      setOllamaEnabled(raw === "true");
    } catch {
      // ignore
    }
  }, []);

  const isLocal = useMemo(() => {
    if (typeof window === "undefined") return false;
    return (
      window.location.hostname === "localhost" ||
      window.location.hostname === "127.0.0.1"
    );
  }, []);

  const handleToggle = (value: boolean) => {
    setOllamaEnabled(value);
    try {
      localStorage.setItem(OLLAMA_ENABLED_KEY, String(value));
    } catch {
      // ignore
    }
  };

  return (
    <MainLayout title="Settings" subtitle="Preferencias de experiencia y conexión">
      <div className="grid gap-6 max-w-3xl">
        <Card>
          <CardHeader className="border-b">
            <CardTitle className="flex items-center justify-between">
              <span>Conectividad de Modelos</span>
              <Badge variant={isLocal ? "success" : "secondary"}>
                {isLocal ? "Local" : "No-Local"}
              </Badge>
            </CardTitle>
          </CardHeader>

          <CardContent className="pt-6 space-y-4">
            <div className="flex items-start justify-between gap-6">
              <div className="space-y-1">
                <div className="font-medium">Habilitar Ollama (solo local)</div>
                <p className="text-sm text-muted-foreground">
                  Cuando está deshabilitado, la UI no intentará usar endpoints de
                  Ollama. Recomendado para entornos enterprise / Foundry.
                </p>
              </div>

              <div className="flex items-center gap-3">
                <Switch
                  checked={ollamaEnabled}
                  onCheckedChange={handleToggle}
                  disabled={!isLocal}
                />
                <span className="text-sm text-muted-foreground">
                  {ollamaEnabled ? "ON" : "OFF"}
                </span>
              </div>
            </div>

            {!isLocal && (
              <div className="rounded-lg border p-3 text-sm text-muted-foreground">
                Este ajuste está disponible solo en <code>localhost</code>.
              </div>
            )}

            <div className="flex gap-2">
              <Button
                type="button"
                variant="outline"
                onClick={() => handleToggle(true)}
                disabled={!isLocal}
              >
                Activar
              </Button>
              <Button
                type="button"
                variant="outline"
                onClick={() => handleToggle(false)}
                disabled={!isLocal}
              >
                Desactivar
              </Button>
            </div>
          </CardContent>
        </Card>
      </div>
    </MainLayout>
  );
}
