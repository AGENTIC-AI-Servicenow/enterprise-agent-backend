"use client";

import Link from "next/link";
import { MainLayout } from "@/components/layout/main-layout";
import { Badge, Button, Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui";
import {
  Activity,
  ArrowRight,
  Bot,
  Brain,
  CheckCircle2,
  FileSearch,
  Gauge,
  GitBranch,
  ShieldCheck,
  Sparkles,
  Users,
  Wrench,
} from "lucide-react";

const capabilityPillars = [
  {
    title: "Understand",
    description:
      "Interpreta lenguaje natural, reconoce intención operativa y enruta la solicitud al flujo correcto de ServiceNow.",
    icon: Brain,
    badge: "Intent + Context",
  },
  {
    title: "Decide",
    description:
      "Evalúa riesgo, aplica políticas y define si la acción requiere aprobación humana o puede continuar de forma asistida.",
    icon: ShieldCheck,
    badge: "Guardrails",
  },
  {
    title: "Act",
    description:
      "Consulta incidentes, ejecuta analítica operativa y sintetiza respuestas accionables para service desk y operaciones.",
    icon: Wrench,
    badge: "Tools + APIs",
  },
  {
    title: "Explain",
    description:
      "Devuelve resultados trazables con lenguaje ejecutivo, evidencias y siguientes pasos recomendados.",
    icon: Sparkles,
    badge: "Explainability",
  },
];

const demoJourneys = [
  {
    title: "Triage inteligente de incidentes",
    summary:
      "El agente identifica el ticket, resume el contexto, muestra prioridad y propone próximos pasos para el analista.",
    impact: "Reduce tiempo de lectura y handoff",
    icon: FileSearch,
  },
  {
    title: "Consulta operativa conversacional",
    summary:
      "Un supervisor puede preguntar por métricas, volumen de tickets o estado de la operación sin navegar múltiples vistas.",
    impact: "Acelera decisiones en tiempo real",
    icon: Gauge,
  },
  {
    title: "Orquestación con guardrails",
    summary:
      "La plataforma prepara el camino para aprobaciones humanas, separación por riesgo y evolución hacia agentes autónomos.",
    impact: "Escalable y gobernable",
    icon: GitBranch,
  },
];

const servicenowFit = [
  "Employee Center y Virtual Agent como canales de interacción",
  "Incident, Knowledge, Catalog y flujos ITSM como fuentes y destinos",
  "Human-in-the-loop para aprobaciones y acciones de riesgo medio/alto",
  "Observabilidad del agente: intención, confianza, tiempo y resultado",
];

const nextIterations = [
  "Demo guiada con prompts de negocio preconfigurados",
  "Panel de trazabilidad agentic con intención, tool usada y confidence",
  "Flujo de aprobación para acciones sensibles",
  "Narrativa ejecutiva orientada a ROI, SLA y reducción de MTTR",
];

export default function DemoPage() {
  return (
    <MainLayout
      title="Agentic Demo"
      subtitle="Propuesta de valor para una demo de capacidades agénticas sobre ServiceNow"
    >
      <div className="space-y-6">
        <section className="relative overflow-hidden rounded-3xl border bg-gradient-to-br from-slate-950 via-slate-900 to-blue-950 p-8 text-white">
          <div className="absolute inset-0 bg-[radial-gradient(circle_at_top_right,rgba(59,130,246,0.35),transparent_35%),radial-gradient(circle_at_bottom_left,rgba(168,85,247,0.25),transparent_30%)]" />
          <div className="relative grid gap-8 lg:grid-cols-[1.5fr_0.9fr]">
            <div className="space-y-5">
              <Badge variant="secondary" className="bg-white/10 text-white hover:bg-white/10">
                ServiceNow + Agentic AI
              </Badge>
              <div className="space-y-3">
                <h2 className="max-w-3xl text-3xl font-semibold tracking-tight md:text-4xl">
                  Tu proyecto ya tiene una base sólida de copilot. El siguiente paso es convertirlo en una demo narrativa de agente enterprise.
                </h2>
                <p className="max-w-2xl text-sm text-slate-200 md:text-base">
                  Hoy ya cuentas con orquestación, memoria conversacional, analítica y consulta de incidentes.
                  La oportunidad está en hacer visible la capa agentic: decisión, trazabilidad,
                  guardrails, journeys guiados y valor de negocio para stakeholders de ServiceNow.
                </p>
              </div>

              <div className="flex flex-wrap gap-3">
                <Link href="/agent">
                  <Button className="gap-2">
                    Probar agente actual
                    <ArrowRight className="h-4 w-4" />
                  </Button>
                </Link>
                <Link href="/dashboard">
                  <Button variant="outline" className="gap-2 border-white/20 bg-white/5 text-white hover:bg-white/10 hover:text-white">
                    Ver dashboard actual
                    <Activity className="h-4 w-4" />
                  </Button>
                </Link>
              </div>
            </div>

            <Card className="border-white/10 bg-white/5 text-white backdrop-blur">
              <CardHeader>
                <CardTitle className="flex items-center gap-2 text-white">
                  <CheckCircle2 className="h-5 w-5 text-emerald-400" />
                  Diagnóstico rápido
                </CardTitle>
                <CardDescription className="text-slate-300">
                  Lo que ya está funcionando y conviene capitalizar en la demo.
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-3 text-sm text-slate-200">
                <div className="rounded-xl border border-white/10 bg-black/10 p-3">
                  Backend con orquestación, planner y acceso a incidentes/métricas.
                </div>
                <div className="rounded-xl border border-white/10 bg-black/10 p-3">
                  Frontend moderno con dashboard, chat y navegación enterprise.
                </div>
                <div className="rounded-xl border border-white/10 bg-black/10 p-3">
                  Faltan elementos de storytelling para vender “capacidades agénticas”.
                </div>
              </CardContent>
            </Card>
          </div>
        </section>

        <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
          {capabilityPillars.map((pillar) => {
            const Icon = pillar.icon;
            return (
              <Card key={pillar.title} className="border-primary/10">
                <CardHeader className="space-y-3">
                  <div className="flex items-center justify-between">
                    <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-primary/10 text-primary">
                      <Icon className="h-5 w-5" />
                    </div>
                    <Badge variant="info">{pillar.badge}</Badge>
                  </div>
                  <CardTitle>{pillar.title}</CardTitle>
                  <CardDescription>{pillar.description}</CardDescription>
                </CardHeader>
              </Card>
            );
          })}
        </section>

        <section className="grid gap-6 lg:grid-cols-[1.2fr_0.8fr]">
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <Bot className="h-5 w-5 text-primary" />
                Journeys recomendados para una demo ServiceNow
              </CardTitle>
              <CardDescription>
                Escenarios concretos que muestran inteligencia, control y valor operacional.
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              {demoJourneys.map((journey) => {
                const Icon = journey.icon;
                return (
                  <div
                    key={journey.title}
                    className="rounded-2xl border p-4 transition-colors hover:border-primary/30 hover:bg-primary/5"
                  >
                    <div className="flex items-start gap-4">
                      <div className="mt-1 flex h-10 w-10 items-center justify-center rounded-xl bg-primary/10 text-primary">
                        <Icon className="h-5 w-5" />
                      </div>
                      <div className="space-y-2">
                        <div className="flex flex-wrap items-center gap-2">
                          <h3 className="font-semibold">{journey.title}</h3>
                          <Badge variant="secondary">{journey.impact}</Badge>
                        </div>
                        <p className="text-sm text-muted-foreground">{journey.summary}</p>
                      </div>
                    </div>
                  </div>
                );
              })}
            </CardContent>
          </Card>

          <div className="space-y-6">
            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <Users className="h-5 w-5 text-primary" />
                  Encaje natural con ServiceNow
                </CardTitle>
              </CardHeader>
              <CardContent className="space-y-3">
                {servicenowFit.map((item) => (
                  <div key={item} className="flex gap-3 text-sm">
                    <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-emerald-500" />
                    <span>{item}</span>
                  </div>
                ))}
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>Siguiente iteración recomendada</CardTitle>
                <CardDescription>
                  Cambios de alto impacto para convertir el MVP en demo ejecutiva.
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-3">
                {nextIterations.map((item, index) => (
                  <div key={item} className="flex gap-3 text-sm">
                    <div className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-primary/10 text-xs font-semibold text-primary">
                      {index + 1}
                    </div>
                    <span>{item}</span>
                  </div>
                ))}
              </CardContent>
            </Card>
          </div>
        </section>
      </div>
    </MainLayout>
  );
}
