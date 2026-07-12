"use client";

import { MainLayout } from "@/components/layout/main-layout";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  Badge,
  Button,
  Textarea,
} from "@/components/ui";
import { useAgent } from "@/hooks/use-agent";
import { agentApi } from "@/lib/api-client";
import type { BriefingResponse, Incident } from "@/types";
import {
  Send,
  Loader2,
  Clock3,
  PanelTop,
  Sparkles,
  ArrowRight,
  Paperclip,
  Globe,
  Mic,
  Eye,
  Lightbulb,
  ChevronRight,
  Search,
  Plus,
  CalendarClock,
  AlertTriangle,
  ShieldAlert,
  CheckCircle2,
} from "lucide-react";
import { Fragment, useState, useRef, useEffect, useMemo } from "react";

function buildLocalBriefingFromIncidents(incidents: Incident[]): BriefingResponse {
  const now = new Date();

  const parseDate = (value?: string) => {
    if (!value) return null;
    const parsed = new Date(value);
    return Number.isNaN(parsed.getTime()) ? null : parsed;
  };

  const diffHours = (value?: string) => {
    const parsed = parseDate(value);
    if (!parsed) return 0;
    return Math.max(0, Math.round((now.getTime() - parsed.getTime()) / (1000 * 60 * 60)));
  };

  const diffDays = (value?: string) => {
    const parsed = parseDate(value);
    if (!parsed) return 0;
    return Math.max(0, Math.round((now.getTime() - parsed.getTime()) / (1000 * 60 * 60 * 24)));
  };

  const toPriorityRank = (priority?: string) => {
    const normalized = String(priority ?? "").toLowerCase();
    if (normalized === "critical" || normalized === "1") return 1;
    if (normalized === "high" || normalized === "2") return 2;
    if (normalized === "medium" || normalized === "3") return 3;
    return 4;
  };

  const normalizedOpen = incidents.filter((incident) =>
    ["new", "in progress", "on hold", "1", "2", "3"].includes(
      String(incident.state ?? "").toLowerCase()
    )
  );

  const briefingTickets = normalizedOpen
    .map((incident) => {
      const ageHours = diffHours(incident.opened_at);
      const daysWithoutUpdate = diffDays(incident.updated_at);
      const daysSinceClosed = diffDays(incident.closed_at);
      const priorityRank = toPriorityRank(incident.priority);
      const slaRisk = priorityRank <= 2 || ageHours >= 24 || daysWithoutUpdate >= 2;
      const complianceWindow = daysWithoutUpdate >= 3;
      const topic =
        /pago|payment/i.test(
          `${incident.short_description ?? ""} ${incident.description ?? ""}`
        )
          ? "Pagos"
          : /auth|autentic|acceso|permission/i.test(
                `${incident.short_description ?? ""} ${incident.description ?? ""}`
              )
            ? "Acceso"
            : /inventario|stock/i.test(
                  `${incident.short_description ?? ""} ${incident.description ?? ""}`
                )
              ? "Inventario"
              : "Operación";

      const reasons = [];
      if (priorityRank === 1) reasons.push("prioridad crítica");
      if (priorityRank === 2) reasons.push("prioridad alta");
      if (ageHours >= 24) reasons.push(`${ageHours}h abierto`);
      if (daysWithoutUpdate >= 2) reasons.push(`${daysWithoutUpdate} día(s) sin actualización`);
      if (slaRisk) reasons.push("riesgo de ANS");

      const riskRank =
        (priorityRank === 1 ? 100 : priorityRank === 2 ? 80 : priorityRank === 3 ? 55 : 30) +
        Math.min(ageHours, 48) +
        daysWithoutUpdate * 10;

      return {
        number: incident.number,
        shortDescription: incident.short_description,
        state: incident.state,
        priority: incident.priority,
        ageHours,
        daysWithoutUpdate,
        daysSinceClosed,
        slaRisk,
        complianceWindow,
        reason: reasons.join(" · ") || "requiere revisión",
        suggestedAction:
          priorityRank <= 2
            ? "Validar impacto, comunicar estado y acelerar resolución"
            : "Confirmar bloqueo real y definir siguiente actualización",
        riskRank,
        topic,
      };
    })
    .sort((a, b) => b.riskRank - a.riskRank);

  const attentionToday = briefingTickets.slice(0, 3);
  const complianceWatch = briefingTickets
    .filter((ticket) => ticket.complianceWindow || ticket.daysWithoutUpdate >= 3)
    .slice(0, 3);

  const topicCounts = briefingTickets.reduce<Record<string, number>>((acc, ticket) => {
    acc[ticket.topic] = (acc[ticket.topic] || 0) + 1;
    return acc;
  }, {});

  const patterns = Object.entries(topicCounts)
    .sort((a, b) => b[1] - a[1])
    .slice(0, 3)
    .map(([topic, count]) => `${topic} aparece en ${count} incidente(s)`);

  const summary = attentionToday.length
    ? `Detecté ${normalizedOpen.length} ticket(s) abiertos en tu contexto visible. Prioriza ${attentionToday[0].number} porque combina ${attentionToday[0].reason}. Además, ${briefingTickets.filter((ticket) => ticket.slaRisk).length} caso(s) muestran riesgo de ANS y ${briefingTickets.filter((ticket) => ticket.daysWithoutUpdate >= 2).length} llevan tiempo sin actualización.`
    : "No detecté incidentes abiertos suficientes en el contexto local para generar un briefing priorizado.";

  return {
    summary,
    context: {
      technician: "local-visible-context",
      generatedAt: now.toISOString(),
      metrics: {
        openTickets: normalizedOpen.length,
        slaRiskTickets: briefingTickets.filter((ticket) => ticket.slaRisk).length,
        stalledTickets: briefingTickets.filter((ticket) => ticket.daysWithoutUpdate >= 2).length,
        pendingComplianceTickets: complianceWatch.length,
      },
      attentionToday,
      complianceWatch,
      patterns: patterns.length ? patterns : ["briefing construido desde incidentes visibles locales"],
    },
  };
}

function renderRichMessage(content: string) {
  const blocks = content
    .split(/\n\s*\n/)
    .map((block) => block.trim())
    .filter(Boolean);

  return blocks.map((block, index) => {
    const lines = block
      .split("\n")
      .map((line) => line.trim())
      .filter(Boolean);

    const isBulletList = lines.every(
      (line) => line.startsWith("- ") || line.startsWith("• ")
    );
    const isNumberedList = lines.every((line) => /^\d+\.\s/.test(line));

    if (isBulletList) {
      return (
        <ul
          key={index}
          className="space-y-2 pl-5 text-[14px] leading-[1.65] text-foreground/90 list-disc"
        >
          {lines.map((line, itemIndex) => (
            <li key={itemIndex}>{line.replace(/^(-|•)\s+/, "")}</li>
          ))}
        </ul>
      );
    }

    if (isNumberedList) {
      return (
        <ol
          key={index}
          className="space-y-2 pl-5 text-[14px] leading-[1.65] text-foreground/90 list-decimal"
        >
          {lines.map((line, itemIndex) => (
            <li key={itemIndex}>{line.replace(/^\d+\.\s/, "")}</li>
          ))}
        </ol>
      );
    }

    return (
      <Fragment key={index}>
        {lines.map((line, lineIndex) => {
          const cleanLine = line.replace(/^#+\s*/, "");
          const isHeading =
            cleanLine.endsWith(":") ||
            /^(resumen ejecutivo|estado general|principales focos de riesgo|siguiente acción sugerida|key takeaways|focos de riesgo principales|atención hoy|detecté)/i.test(
              cleanLine
            );

          if (isHeading) {
            return (
              <h4
                key={`${index}-${lineIndex}`}
                className="text-[16px] font-semibold tracking-tight text-foreground"
              >
                {cleanLine}
              </h4>
            );
          }

          return (
            <p
              key={`${index}-${lineIndex}`}
              className="text-[14px] leading-[1.7] text-foreground/90"
            >
              {cleanLine}
            </p>
          );
        })}
      </Fragment>
    );
  });
}

export default function AgentPage() {
  const { sendMessage, isLoading, messages: agentMessages } = useAgent();
  const [input, setInput] = useState("");
  const [visibleIncidents, setVisibleIncidents] = useState<any[]>([]);
  const [briefing, setBriefing] = useState<BriefingResponse | null>(null);
  const [isBriefingLoading, setIsBriefingLoading] = useState(true);
  const [briefingError, setBriefingError] = useState<string | null>(null);
  const [briefingMode, setBriefingMode] = useState<"remote" | "local-fallback">("remote");
  const messagesEndRef = useRef<HTMLDivElement>(null);

  const suggestionPills = [
    "Dame el briefing diario",
    "Resume mis incidentes abiertos en lenguaje ejecutivo",
    "¿Cuál debería atender primero y por qué?",
    "Dame el detalle del ticket más crítico",
  ];

  const followUpPrompts = [
    "¿Cuál debería atender primero?",
    "¿Qué riesgo operativo ves aquí?",
    "Redacta una actualización ejecutiva",
  ];

  const messages = useMemo(
    () => [
      {
        id: "welcome",
        role: "assistant" as const,
        content:
          "AideBot listo para asistir en priorización, análisis de impacto y preparación de respuestas ejecutivas sobre incidentes activos.",
        timestamp: new Date().toISOString(),
      },
      ...agentMessages,
    ],
    [agentMessages]
  );

  const lastAssistantMessage = useMemo(
    () => [...agentMessages].reverse().find((message) => message.role === "assistant"),
    [agentMessages]
  );

  const incidentSnapshot = useMemo(() => {
    const total = visibleIncidents.length;
    const open = visibleIncidents.filter((incident) =>
      ["1", "2", "3", "New", "In Progress", "On Hold"].includes(
        String(incident?.state ?? "")
      )
    );

    const scored = visibleIncidents.map((incident) => {
      const text = `${incident?.short_description ?? ""} ${
        incident?.description ?? ""
      }`.toLowerCase();
      const priority = String(incident?.priority ?? "");
      const urgency = String(incident?.urgency ?? "");
      const impact = String(incident?.impact ?? "");
      const state = String(incident?.state ?? "");

      let score = 0;
      if (priority === "1") score += 50;
      else if (priority === "2") score += 40;
      else if (priority === "3") score += 25;
      else if (priority === "4") score += 10;

      if (urgency === "1") score += 25;
      else if (urgency === "2") score += 15;
      else if (urgency === "3") score += 5;

      if (impact === "1") score += 25;
      else if (impact === "2") score += 15;
      else if (impact === "3") score += 5;

      if (state === "2" || /in progress/i.test(state)) score += 8;
      if (state === "1" || /new/i.test(state)) score += 5;
      if (/pago|payment/.test(text)) score += 20;
      if (/auth|autentic/.test(text)) score += 20;
      if (/producci/.test(text)) score += 15;
      if (/inventario/.test(text)) score += 10;
      if (/security|seguridad|acceso/.test(text)) score += 12;

      return { incident, score, text };
    });

    const ranked = [...scored].sort((a, b) => b.score - a.score);
    const highRisk = ranked.slice(0, 3);

    const unassigned = visibleIncidents.filter((incident) => {
      const assignedTo = incident?.assigned_to;
      if (!assignedTo) return true;
      if (typeof assignedTo === "string") return !assignedTo.trim();
      if (typeof assignedTo === "object") {
        return !assignedTo?.user_name && !assignedTo?.name && !assignedTo?.value;
      }
      return false;
    }).length;

    const dominantThemes = [
      { key: "Pagos", regex: /pago|payment/ },
      { key: "Autenticación", regex: /auth|autentic/ },
      { key: "Inventario", regex: /inventario|stock/ },
      { key: "Producción", regex: /producci|production/ },
      { key: "Acceso", regex: /acceso|permission|permiso/ },
    ]
      .map((theme) => ({
        label: theme.key,
        count: visibleIncidents.filter((incident) =>
          theme.regex.test(
            `${incident?.short_description ?? ""} ${incident?.description ?? ""}`.toLowerCase()
          )
        ).length,
      }))
      .filter((theme) => theme.count > 0)
      .sort((a, b) => b.count - a.count);

    const topIncident = ranked[0]?.incident;
    const topTheme = dominantThemes[0];

    const recommendation = topIncident
      ? `Prioriza ${topIncident?.number ?? "el incidente principal"} porque concentra el mayor riesgo operativo actual${
          topTheme ? ` y el patrón dominante está en ${topTheme.label.toLowerCase()}` : ""
        }.`
      : "Carga incidentes visibles para obtener una recomendación priorizada.";

    return {
      total,
      open: open.length,
      highRisk,
      ranked,
      latest: visibleIncidents.slice(0, 4),
      unassigned,
      dominantThemes: dominantThemes.slice(0, 3),
      topIncident,
      recommendation,
    };
  }, [visibleIncidents]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages, isLoading]);

  useEffect(() => {
    if (typeof window === "undefined") return;

    const loadVisibleIncidents = () => {
      try {
        const raw = localStorage.getItem("ea.visibleIncidents");
        setVisibleIncidents(raw ? JSON.parse(raw) : []);
      } catch {
        setVisibleIncidents([]);
      }
    };

    loadVisibleIncidents();
    window.addEventListener("storage", loadVisibleIncidents);
    return () => window.removeEventListener("storage", loadVisibleIncidents);
  }, []);

  useEffect(() => {
    let active = true;

    const loadBriefing = async () => {
      setIsBriefingLoading(true);
      setBriefingError(null);

      try {
        const response = await agentApi.getBriefing();
        if (!active) return;

        if ((response as any)?.degraded && visibleIncidents.length > 0) {
          setBriefing(buildLocalBriefingFromIncidents(visibleIncidents as Incident[]));
          setBriefingMode("local-fallback");
        } else {
          setBriefing(response.data ?? null);
          setBriefingMode("remote");
        }
      } catch (error: any) {
        if (!active) return;

        if (visibleIncidents.length > 0) {
          setBriefing(buildLocalBriefingFromIncidents(visibleIncidents as Incident[]));
          setBriefingMode("local-fallback");
          setBriefingError(null);
        } else {
          setBriefingError(error?.message || "No se pudo cargar el briefing.");
        }
      } finally {
        if (active) {
          setIsBriefingLoading(false);
        }
      }
    };

    loadBriefing();

    return () => {
      active = false;
    };
  }, [visibleIncidents]);

  const handleSend = () => {
    if (!input.trim() || isLoading) return;
    const message = input;
    setInput("");
    sendMessage(message);
  };

  const sendShortcut = (message: string) => {
    if (isLoading) return;
    setInput("");
    sendMessage(message);
  };

  const refreshBriefing = async () => {
    setIsBriefingLoading(true);
    setBriefingError(null);

    try {
      const response = await agentApi.getBriefing();
      if ((response as any)?.degraded && visibleIncidents.length > 0) {
        setBriefing(buildLocalBriefingFromIncidents(visibleIncidents as Incident[]));
        setBriefingMode("local-fallback");
      } else {
        setBriefing(response.data ?? null);
        setBriefingMode("remote");
      }
    } catch (error: any) {
      if (visibleIncidents.length > 0) {
        setBriefing(buildLocalBriefingFromIncidents(visibleIncidents as Incident[]));
        setBriefingMode("local-fallback");
        setBriefingError(null);
      } else {
        setBriefingError(error?.message || "No se pudo actualizar el briefing.");
      }
    } finally {
      setIsBriefingLoading(false);
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  const getActionBadge = (action?: string) => {
    if (!action) return null;

    const actionLabels: Record<string, { label: string; variant: any }> = {
      CLASSIFY_TICKET: { label: "Clasificación", variant: "info" },
      SUMMARIZE_INCIDENT: { label: "Resumen", variant: "secondary" },
      SUMMARY_OPEN_INCIDENTS: { label: "Resumen ejecutivo", variant: "secondary" },
      LIST_OPEN_INCIDENTS: { label: "Incidentes abiertos", variant: "info" },
      SUGGEST_RESOLUTION: { label: "Resolución", variant: "success" },
      SEARCH_KNOWLEDGE: { label: "Búsqueda", variant: "info" },
      DETECT_DUPLICATE: { label: "Duplicados", variant: "warning" },
      PRIORITIZE: { label: "Priorización", variant: "warning" },
      GENERATE_RESPONSE: { label: "Respuesta", variant: "info" },
      GENERAL_QUERY: { label: "Consulta general", variant: "secondary" },
      ANALYTICS_QUERY: { label: "Analítica", variant: "secondary" },
      GET_INCIDENT: { label: "Detalle de ticket", variant: "info" },
      GET_CRITICAL_INCIDENT: { label: "Ticket crítico", variant: "warning" },
      CHAT: { label: "Asistente", variant: "secondary" },
    };

    const config = actionLabels[action] || {
      label: action,
      variant: "secondary",
    };

    return (
      <Badge variant={config.variant} className="rounded-full px-2.5 py-0.5 text-[11px]">
        {config.label}
      </Badge>
    );
  };

  return (
    <MainLayout
      title="AI Assistant"
      subtitle="Experiencia conversacional para la demo agentic sobre ServiceNow"
    >
      <div className="flex h-[calc(100vh-7rem)] w-full items-stretch justify-center px-2 pb-2">
        <Card className="flex h-full w-full flex-col overflow-hidden rounded-[28px] border-white/70 bg-white/90 shadow-[0_20px_80px_rgba(15,23,42,0.10)] backdrop-blur-2xl dark:border-white/10 dark:bg-slate-950/80">
          <CardHeader className="shrink-0 border-b border-black/5 px-5 py-4 dark:border-white/10">
            <div className="flex items-center gap-3">
              <div className="flex h-9 w-9 items-center justify-center rounded-2xl bg-primary/10 text-primary shadow-sm">
                <PanelTop className="h-4 w-4" />
              </div>
              <div className="min-w-0">
                <CardTitle className="text-sm font-semibold tracking-tight text-foreground">
                  AideBot Workspace
                </CardTitle>
                <p className="mt-0.5 text-xs text-muted-foreground">
                  Briefing diario, priorización y análisis operativo sobre incidentes visibles
                </p>
              </div>
            </div>
          </CardHeader>

          <CardContent className="grid min-h-0 flex-1 grid-cols-1 gap-0 bg-[radial-gradient(circle_at_top,#eff6ff,transparent_35%)] px-0 py-0 lg:grid-cols-3 dark:bg-[radial-gradient(circle_at_top,rgba(59,130,246,0.08),transparent_35%)]">
            <div className="flex min-h-0 flex-col border-r border-black/5 lg:col-span-2 dark:border-white/10">
              <div className="flex-1 overflow-y-auto px-6 py-6 [scrollbar-width:thin] [scrollbar-color:rgba(15,23,42,0.14)_transparent] [&::-webkit-scrollbar]:w-0.5 [&::-webkit-scrollbar-track]:bg-transparent [&::-webkit-scrollbar-thumb]:rounded-full [&::-webkit-scrollbar-thumb]:bg-slate-300/60 dark:[scrollbar-color:rgba(148,163,184,0.22)_transparent] dark:[&::-webkit-scrollbar-thumb]:bg-slate-600/40">
                <div className="mx-auto flex w-full max-w-4xl flex-col gap-5">
                  <div className="rounded-[28px] border border-primary/10 bg-gradient-to-br from-primary/[0.07] via-white/80 to-white/60 p-4 shadow-[0_12px_40px_rgba(59,130,246,0.08)] backdrop-blur-sm dark:from-primary/10 dark:via-slate-900/70 dark:to-slate-900/50">
                    <div className="mb-3 flex items-start justify-between gap-3">
                      <div>
                        <div className="flex items-center gap-2 text-[11px] uppercase tracking-[0.12em] text-muted-foreground">
                          <CalendarClock className="h-3.5 w-3.5" />
                          Briefing diario
                        </div>
                        <div className="mt-1 flex items-center gap-2">
                          <div className="text-base font-semibold text-foreground">
                            Inicio de jornada asistido
                          </div>
                          {briefingMode === "local-fallback" && (
                            <Badge variant="warning" className="rounded-full text-[10px]">
                              Local fallback
                            </Badge>
                          )}
                        </div>
                      </div>

                      <Button
                        variant="outline"
                        size="sm"
                        onClick={refreshBriefing}
                        disabled={isBriefingLoading}
                        className="rounded-full"
                      >
                        {isBriefingLoading ? (
                          <Loader2 className="h-3.5 w-3.5 animate-spin" />
                        ) : (
                          "Actualizar"
                        )}
                      </Button>
                    </div>

                    {isBriefingLoading ? (
                      <div className="space-y-3">
                        <div className="h-3 w-48 animate-pulse rounded-full bg-slate-200/70 dark:bg-slate-700/60" />
                        <div className="h-3 w-full animate-pulse rounded-full bg-slate-200/60 dark:bg-slate-700/50" />
                        <div className="h-3 w-[88%] animate-pulse rounded-full bg-slate-200/50 dark:bg-slate-700/40" />
                      </div>
                    ) : briefingError ? (
                      <div className="rounded-2xl border border-rose-200/70 bg-rose-50/80 px-3 py-3 text-[13px] text-rose-700 dark:border-rose-500/20 dark:bg-rose-500/10 dark:text-rose-200">
                        {briefingError}
                      </div>
                    ) : briefing ? (
                      <div className="space-y-4">
                        <div className="rounded-[22px] bg-white/75 px-4 py-4 text-[14px] leading-[1.7] text-foreground/90 ring-1 ring-black/5 dark:bg-slate-900/50 dark:ring-white/10">
                          {renderRichMessage(briefing.summary)}
                        </div>

                        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
                          <div className="rounded-2xl bg-white/70 px-3 py-3 ring-1 ring-black/5 dark:bg-slate-900/50 dark:ring-white/10">
                            <div className="text-[11px] text-muted-foreground">Abiertos</div>
                            <div className="mt-1 text-xl font-semibold">
                              {briefing.context.metrics.openTickets}
                            </div>
                          </div>
                          <div className="rounded-2xl bg-white/70 px-3 py-3 ring-1 ring-black/5 dark:bg-slate-900/50 dark:ring-white/10">
                            <div className="text-[11px] text-muted-foreground">Riesgo ANS</div>
                            <div className="mt-1 text-xl font-semibold">
                              {briefing.context.metrics.slaRiskTickets}
                            </div>
                          </div>
                          <div className="rounded-2xl bg-white/70 px-3 py-3 ring-1 ring-black/5 dark:bg-slate-900/50 dark:ring-white/10">
                            <div className="text-[11px] text-muted-foreground">Estancados</div>
                            <div className="mt-1 text-xl font-semibold">
                              {briefing.context.metrics.stalledTickets}
                            </div>
                          </div>
                          <div className="rounded-2xl bg-white/70 px-3 py-3 ring-1 ring-black/5 dark:bg-slate-900/50 dark:ring-white/10">
                            <div className="text-[11px] text-muted-foreground">Conformidad</div>
                            <div className="mt-1 text-xl font-semibold">
                              {briefing.context.metrics.pendingComplianceTickets}
                            </div>
                          </div>
                        </div>

                        <div className="space-y-2">
                          <div className="text-[12px] font-medium text-muted-foreground">
                            Atención hoy
                          </div>
                          <div className="flex flex-wrap gap-2">
                            {briefing.context.attentionToday.length ? (
                              briefing.context.attentionToday.map((ticket) => (
                                <button
                                  key={ticket.number}
                                  onClick={() => sendShortcut(`Analiza el ticket ${ticket.number}`)}
                                  className="rounded-full border border-amber-200/70 bg-amber-50/80 px-3 py-2 text-left text-[12px] text-amber-900 transition hover:bg-amber-100 dark:border-amber-400/20 dark:bg-amber-500/10 dark:text-amber-100"
                                >
                                  <span className="font-semibold">{ticket.number}</span>
                                  <span className="mx-1 text-amber-700/70 dark:text-amber-200/60">•</span>
                                  <span>{ticket.reason}</span>
                                </button>
                              ))
                            ) : (
                              <div className="rounded-2xl border border-dashed border-black/10 px-3 py-3 text-[12px] text-muted-foreground dark:border-white/10">
                                No se identificaron tickets urgentes para hoy.
                              </div>
                            )}
                          </div>
                        </div>
                      </div>
                    ) : null}
                  </div>

                  {messages.map((message, index) => (
                    <div
                      key={message.id}
                      className={`flex ${
                        message.role === "user" ? "justify-end" : "justify-start"
                      }`}
                    >
                      <div className={message.role === "user" ? "max-w-[62%]" : "max-w-[88%]"}>
                        {message.role === "user" ? (
                          <div className="space-y-1">
                            <div className="rounded-[22px] rounded-tr-md bg-white px-4 py-3 text-[14px] leading-[1.55] text-foreground shadow-[0_6px_18px_rgba(15,23,42,0.06)] ring-1 ring-black/5 dark:bg-slate-900 dark:ring-white/10">
                              {message.content}
                            </div>
                            <div
                              className="flex items-center justify-end gap-1 text-[11px] text-muted-foreground"
                              suppressHydrationWarning
                            >
                              <Clock3 className="h-3 w-3" />
                              {new Date(message.timestamp).toLocaleTimeString()}
                            </div>
                          </div>
                        ) : (
                          <div className="space-y-3">
                            {index > 0 && (
                              <div className="space-y-2">
                                <div className="flex items-center gap-2 text-[13px] text-muted-foreground">
                                  <Lightbulb className="h-3.5 w-3.5" />
                                  <span>Thought</span>
                                  <ChevronRight className="h-3 w-3 opacity-60" />
                                </div>

                                <div className="flex items-center gap-2 text-[13px] text-muted-foreground">
                                  <Eye className="h-3.5 w-3.5" />
                                  <span>Contexto consultado</span>
                                </div>
                              </div>
                            )}

                            <div className="space-y-2">
                              <div className="flex items-center gap-2">
                                {index > 0 && message.metadata?.action
                                  ? getActionBadge(message.metadata.action)
                                  : (
                                    <Badge variant="secondary" className="rounded-full px-2.5 py-0.5 text-[11px]">
                                      Asistente
                                    </Badge>
                                  )}
                                <div
                                  className="flex items-center gap-1 text-[11px] text-muted-foreground"
                                  suppressHydrationWarning
                                >
                                  <Clock3 className="h-3 w-3" />
                                  {new Date(message.timestamp).toLocaleTimeString()}
                                </div>
                              </div>

                              <div className="rounded-[24px] rounded-bl-md bg-white/70 px-4 py-4 shadow-[0_8px_24px_rgba(15,23,42,0.04)] ring-1 ring-black/5 backdrop-blur-sm dark:bg-slate-900/50 dark:ring-white/10">
                                <div className="space-y-3">
                                  {renderRichMessage(message.content)}
                                </div>
                              </div>
                            </div>
                          </div>
                        )}
                      </div>
                    </div>
                  ))}

                  {isLoading && (
                    <div className="flex justify-start">
                      <div className="max-w-[88%] space-y-3">
                        <div className="flex items-center gap-2 text-[13px] text-muted-foreground">
                          <Lightbulb className="h-3.5 w-3.5" />
                          <span>Thought</span>
                          <ChevronRight className="h-3 w-3 opacity-60" />
                        </div>

                        <div className="flex items-center gap-2 text-[13px] text-muted-foreground">
                          <Loader2 className="h-3.5 w-3.5 animate-spin text-primary" />
                          <span>Generating response...</span>
                        </div>

                        <div className="rounded-[24px] rounded-bl-md bg-white/70 px-4 py-4 shadow-[0_8px_24px_rgba(15,23,42,0.04)] ring-1 ring-black/5 backdrop-blur-sm dark:bg-slate-900/50 dark:ring-white/10">
                          <div className="space-y-2">
                            <div className="h-2.5 w-40 animate-pulse rounded-full bg-slate-200/80 dark:bg-slate-700/70" />
                            <div className="h-2.5 w-[92%] animate-pulse rounded-full bg-slate-200/70 dark:bg-slate-700/60" />
                            <div className="h-2.5 w-[78%] animate-pulse rounded-full bg-slate-200/60 dark:bg-slate-700/50" />
                          </div>
                        </div>
                      </div>
                    </div>
                  )}

                  {lastAssistantMessage && !isLoading && (
                    <>
                      <div className="space-y-2">
                        <div className="text-[12px] text-muted-foreground">
                          Fuentes consultadas ({incidentSnapshot.total || 0})
                        </div>

                        <div className="space-y-1.5">
                          {[...incidentSnapshot.highRisk, ...incidentSnapshot.latest]
                            .map((entry: any) => entry?.incident ?? entry)
                            .filter(
                              (incident: any, index: number, arr: any[]) =>
                                incident && arr.findIndex((item) => item?.sys_id === incident?.sys_id) === index
                            )
                            .slice(0, 3)
                            .map((incident: any, index: number) => (
                              <div
                                key={incident?.sys_id ?? incident?.number ?? index}
                                className="flex items-start gap-2 rounded-xl px-2 py-2"
                              >
                                <div className="mt-0.5 flex h-4 w-4 items-center justify-center rounded bg-muted text-[10px]">
                                  <Search className="h-3 w-3" />
                                </div>
                                <div className="min-w-0 flex-1">
                                  <div className="text-[13px] font-medium text-foreground">
                                    {incident?.number ?? "INC"}
                                  </div>
                                  <div className="truncate text-[12px] text-muted-foreground">
                                    {incident?.short_description ?? "Sin descripción disponible"}
                                  </div>
                                </div>
                              </div>
                            ))}
                        </div>
                      </div>

                      <div className="rounded-[24px] border border-black/5 bg-white/70 p-3 shadow-[0_8px_24px_rgba(15,23,42,0.04)] backdrop-blur-sm dark:border-white/10 dark:bg-slate-900/50">
                        <div className="space-y-1">
                          {followUpPrompts.map((prompt) => (
                            <button
                              key={prompt}
                              onClick={() => sendShortcut(prompt)}
                              disabled={isLoading}
                              className="flex w-full items-center gap-2 rounded-2xl px-3 py-2 text-left text-[14px] text-foreground/90 transition hover:bg-primary/5 disabled:opacity-50"
                            >
                              <ArrowRight className="h-3.5 w-3.5 text-primary" />
                              <span>{prompt}</span>
                            </button>
                          ))}
                        </div>
                      </div>
                    </>
                  )}

                  <div ref={messagesEndRef} />
                </div>
              </div>

              <div className="shrink-0 border-t border-black/5 bg-white/75 px-6 py-4 backdrop-blur-xl dark:border-white/10 dark:bg-slate-950/70">
                <div className="mx-auto flex w-full max-w-4xl flex-col gap-3">
                  {!agentMessages.length && (
                    <div className="flex flex-wrap gap-2">
                      {suggestionPills.map((pill) => (
                        <button
                          key={pill}
                          onClick={() => sendShortcut(pill)}
                          className="rounded-full border border-primary/10 bg-primary/5 px-3 py-1.5 text-[12px] text-primary transition hover:bg-primary/10"
                        >
                          {pill}
                        </button>
                      ))}
                    </div>
                  )}

                  <div className="rounded-[28px] border border-primary/20 bg-white px-3 py-3 shadow-[0_12px_40px_rgba(59,130,246,0.10)] ring-1 ring-primary/10 dark:border-primary/20 dark:bg-slate-950">
                    <div className="flex gap-3">
                      <Textarea
                        placeholder="Ask AI anything"
                        value={input}
                        onChange={(e) => setInput(e.target.value)}
                        onKeyDown={handleKeyDown}
                        className="min-h-[64px] resize-none border-0 bg-transparent px-1 py-1 text-[15px] shadow-none focus-visible:ring-0"
                        disabled={isLoading}
                      />
                      <Button
                        onClick={handleSend}
                        disabled={!input.trim() || isLoading}
                        size="icon"
                        className="mt-auto h-10 w-10 rounded-full"
                      >
                        {isLoading ? (
                          <Loader2 className="h-4.5 w-4.5 animate-spin" />
                        ) : (
                          <Send className="h-4.5 w-4.5" />
                        )}
                      </Button>
                    </div>

                    <div className="mt-2 flex items-center justify-between text-muted-foreground">
                      <div className="flex items-center gap-3 text-xs">
                        <button className="transition hover:text-foreground">
                          <Plus className="h-4 w-4" />
                        </button>
                        <div className="flex items-center gap-1 rounded-full bg-primary/5 px-2.5 py-1 text-primary">
                          <Sparkles className="h-3.5 w-3.5" />
                          GPT5.4 mini
                        </div>
                        <button className="transition hover:text-foreground">
                          <Globe className="h-4 w-4" />
                        </button>
                      </div>

                      <div className="flex items-center gap-3">
                        <button className="transition hover:text-foreground">
                          <Paperclip className="h-4 w-4" />
                        </button>
                        <button className="transition hover:text-foreground">
                          <Mic className="h-4 w-4" />
                        </button>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <aside className="flex min-h-0 flex-col bg-white/45 dark:bg-slate-950/30">
              <div className="border-b border-black/5 px-5 py-4 dark:border-white/10">
                <div className="text-[11px] uppercase tracking-[0.12em] text-muted-foreground">
                  Contexto activo
                </div>
                <div className="mt-1 text-sm font-semibold text-foreground">
                  Información usada por el agente
                </div>
              </div>

              <div className="flex-1 overflow-y-auto px-4 py-4 [scrollbar-width:thin] [scrollbar-color:rgba(15,23,42,0.14)_transparent] [&::-webkit-scrollbar]:w-0.5 [&::-webkit-scrollbar-track]:bg-transparent [&::-webkit-scrollbar-thumb]:rounded-full [&::-webkit-scrollbar-thumb]:bg-slate-300/60 dark:[scrollbar-color:rgba(148,163,184,0.22)_transparent] dark:[&::-webkit-scrollbar-thumb]:bg-slate-600/40">
                <div className="space-y-4">
                  <div className="rounded-[24px] border border-black/5 bg-white/80 p-4 shadow-[0_8px_24px_rgba(15,23,42,0.04)] dark:border-white/10 dark:bg-slate-900/60">
                    <div className="mb-3 flex items-center justify-between">
                      <div>
                        <div className="text-[11px] uppercase tracking-[0.12em] text-muted-foreground">
                          Snapshot
                        </div>
                        <div className="text-sm font-semibold">Estado actual</div>
                      </div>
                      <Badge variant="outline" className="rounded-full text-[10px]">
                        Live
                      </Badge>
                    </div>

                    <div className="grid grid-cols-2 gap-2">
                      <div className="rounded-2xl bg-primary/5 p-3">
                        <div className="text-[11px] text-muted-foreground">Tickets visibles</div>
                        <div className="mt-1 text-xl font-semibold">{incidentSnapshot.total}</div>
                      </div>
                      <div className="rounded-2xl bg-primary/5 p-3">
                        <div className="text-[11px] text-muted-foreground">Abiertos</div>
                        <div className="mt-1 text-xl font-semibold">{incidentSnapshot.open}</div>
                      </div>
                    </div>
                  </div>

                  <div className="rounded-[24px] border border-black/5 bg-white/80 p-4 shadow-[0_8px_24px_rgba(15,23,42,0.04)] dark:border-white/10 dark:bg-slate-900/60">
                    <div className="mb-3 flex items-center justify-between">
                      <div className="text-sm font-semibold">Briefing operativo</div>
                      <Badge variant="info" className="rounded-full text-[10px]">
                        Flujo 1
                      </Badge>
                    </div>

                    {briefing ? (
                      <div className="space-y-3">
                        <div className="rounded-2xl bg-slate-50 px-3 py-3 dark:bg-white/5">
                          <div className="flex items-center gap-2 text-[11px] uppercase tracking-[0.08em] text-muted-foreground">
                            <ShieldAlert className="h-3.5 w-3.5" />
                            Riesgo ANS
                          </div>
                          <div className="mt-1 text-[13px] font-medium text-foreground">
                            {briefing.context.metrics.slaRiskTickets} ticket(s) con riesgo de incumplimiento.
                          </div>
                        </div>

                        <div className="rounded-2xl bg-slate-50 px-3 py-3 dark:bg-white/5">
                          <div className="flex items-center gap-2 text-[11px] uppercase tracking-[0.08em] text-muted-foreground">
                            <AlertTriangle className="h-3.5 w-3.5" />
                            Atención hoy
                          </div>
                          <div className="mt-2 space-y-2">
                            {briefing.context.attentionToday.slice(0, 3).map((ticket) => (
                              <button
                                key={ticket.number}
                                onClick={() => sendShortcut(`Analiza el ticket ${ticket.number}`)}
                                className="w-full rounded-2xl border border-black/5 bg-white px-3 py-2 text-left transition hover:bg-primary/5 dark:border-white/10 dark:bg-slate-900"
                              >
                                <div className="text-[12px] font-semibold text-foreground">
                                  {ticket.number}
                                </div>
                                <div className="mt-1 text-[12px] leading-[1.45] text-muted-foreground">
                                  {ticket.reason}
                                </div>
                              </button>
                            ))}
                          </div>
                        </div>

                        <div className="rounded-2xl bg-slate-50 px-3 py-3 dark:bg-white/5">
                          <div className="flex items-center gap-2 text-[11px] uppercase tracking-[0.08em] text-muted-foreground">
                            <CheckCircle2 className="h-3.5 w-3.5" />
                            Patrones
                          </div>
                          <div className="mt-1 text-[13px] font-medium text-foreground">
                            {briefing.context.patterns.length
                              ? briefing.context.patterns.join(" · ")
                              : "Sin patrones relevantes detectados en la cola actual"}
                          </div>
                        </div>
                      </div>
                    ) : (
                      <div className="rounded-2xl border border-dashed border-black/10 px-3 py-3 text-[12px] text-muted-foreground dark:border-white/10">
                        Carga el briefing para obtener foco operativo de inicio de jornada.
                      </div>
                    )}
                  </div>

                  <div className="rounded-[24px] border border-black/5 bg-white/80 p-4 shadow-[0_8px_24px_rgba(15,23,42,0.04)] dark:border-white/10 dark:bg-slate-900/60">
                    <div className="mb-3 flex items-center justify-between">
                      <div className="text-sm font-semibold">Dónde intervenir primero</div>
                      <Badge variant="warning" className="rounded-full text-[10px]">
                        Acción
                      </Badge>
                    </div>

                    {incidentSnapshot.topIncident ? (
                      <div className="space-y-3">
                        <div className="rounded-2xl border border-amber-200/70 bg-amber-50/80 px-3 py-3 dark:border-amber-400/20 dark:bg-amber-500/10">
                          <div className="flex items-center justify-between gap-3">
                            <div className="text-[12px] font-semibold text-foreground">
                              {incidentSnapshot.topIncident?.number}
                            </div>
                            <Badge variant="outline" className="rounded-full text-[10px]">
                              Prioridad sugerida
                            </Badge>
                          </div>
                          <div className="mt-1 text-[12px] leading-[1.45] text-muted-foreground">
                            {incidentSnapshot.topIncident?.short_description}
                          </div>
                        </div>

                        <div className="rounded-2xl bg-primary/5 px-3 py-3 text-[12px] leading-[1.5] text-foreground/85">
                          {incidentSnapshot.recommendation}
                        </div>
                      </div>
                    ) : (
                      <div className="rounded-2xl border border-dashed border-black/10 px-3 py-3 text-[12px] text-muted-foreground dark:border-white/10">
                        Aún no hay suficiente contexto para sugerir una intervención prioritaria.
                      </div>
                    )}
                  </div>

                  <div className="rounded-[24px] border border-black/5 bg-white/80 p-4 shadow-[0_8px_24px_rgba(15,23,42,0.04)] dark:border-white/10 dark:bg-slate-900/60">
                    <div className="mb-3 text-sm font-semibold">Insights accionables</div>
                    <div className="space-y-2">
                      <div className="rounded-2xl bg-slate-50 px-3 py-3 dark:bg-white/5">
                        <div className="text-[11px] uppercase tracking-[0.08em] text-muted-foreground">
                          Riesgo dominante
                        </div>
                        <div className="mt-1 text-[13px] font-medium text-foreground">
                          {incidentSnapshot.dominantThemes[0]
                            ? `${incidentSnapshot.dominantThemes[0].label} aparece en ${incidentSnapshot.dominantThemes[0].count} incidentes`
                            : "Sin patrón dominante identificado"}
                        </div>
                      </div>

                      <div className="rounded-2xl bg-slate-50 px-3 py-3 dark:bg-white/5">
                        <div className="text-[11px] uppercase tracking-[0.08em] text-muted-foreground">
                          Riesgo de ejecución
                        </div>
                        <div className="mt-1 text-[13px] font-medium text-foreground">
                          {incidentSnapshot.unassigned > 0
                            ? `${incidentSnapshot.unassigned} incidentes siguen sin asignación clara`
                            : "Los incidentes visibles ya tienen ownership identificado"}
                        </div>
                      </div>

                      <div className="rounded-2xl bg-slate-50 px-3 py-3 dark:bg-white/5">
                        <div className="text-[11px] uppercase tracking-[0.08em] text-muted-foreground">
                          Qué preguntarle al agente
                        </div>
                        <div className="mt-1 text-[13px] font-medium text-foreground">
                          Pídele briefing diario, riesgo operativo o plan de acción en vez de solo listar tickets.
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </aside>
          </CardContent>
        </Card>
      </div>
    </MainLayout>
  );
}
