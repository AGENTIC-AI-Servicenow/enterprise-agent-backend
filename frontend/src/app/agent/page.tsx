"use client";

import { MainLayout } from "@/components/layout/main-layout";
import { Card, CardContent, CardHeader, CardTitle, Badge, Button, Textarea } from "@/components/ui";
import { useAgent } from "@/hooks/use-agent";
import { Message } from "@/types";
import { Send, Bot, User, Loader2, Sparkles } from "lucide-react";
import { useState, useRef, useEffect, useMemo } from "react";

export default function AgentPage() {
  const { sendMessage, isLoading, messages: agentMessages } = useAgent();
  const [input, setInput] = useState("");
  const messagesEndRef = useRef<HTMLDivElement>(null);

  // Combine welcome message with agent messages
  const messages = useMemo(
    () => [
      {
        id: "welcome",
        role: "assistant" as const,
        content:
          "¡Hola! 👋 Soy AideBot, tu asistente virtual de soporte TI.\n\n" +
          "Estoy aquí para ayudarte con:\n\n" +
          "• 🔑 Recuperación de contraseña\n" +
          "• 📄 Consulta y seguimiento de tickets\n" +
          "• 📞 Escalamiento a soporte técnico\n\n" +
          "¿Con qué te puedo ayudar hoy? 😊",
        // Avoid Next.js hydration mismatch: server render and client render happen at different times.
        // A stable timestamp ensures the server HTML matches the first client render.
        timestamp: "1970-01-01T00:00:00.000Z",
      },
      ...agentMessages,
    ],
    [agentMessages]
  );

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages]);

  const handleSend = () => {
    if (!input.trim() || isLoading) return;

    const message = input;
    setInput("");

    // sendMessage handles adding the message to the conversation
    sendMessage(message);
  };

  const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

  const sendWithDelay = async (message: string) => {
    if (!message.trim() || isLoading) return;

    // Pre-fill input to match the UX in the mock
    setInput(message);

    // Small delay so the user sees the text before it gets sent
    await sleep(150);

    // Clear input and send immediately (no extra sleep)
    setInput("");
    sendMessage(message);
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
      CLASSIFY_TICKET: { label: "Classification", variant: "info" },
      SUMMARIZE_INCIDENT: { label: "Summary", variant: "secondary" },
      SUGGEST_RESOLUTION: { label: "Resolution", variant: "success" },
      SEARCH_KNOWLEDGE: { label: "Search", variant: "info" },
      DETECT_DUPLICATE: { label: "Duplicate Detection", variant: "warning" },
      PRIORITIZE: { label: "Prioritization", variant: "warning" },
      GENERATE_RESPONSE: { label: "Response", variant: "info" },
      GENERAL_QUERY: { label: "General", variant: "secondary" },
    };

    const config = actionLabels[action] || { label: action, variant: "secondary" };
    return <Badge variant={config.variant}>{config.label}</Badge>;
  };

  return (
    <MainLayout
      title="AI Assistant"
      subtitle="Chat with your intelligent ServiceNow copilot"
    >
      <div className="grid h-[calc(100vh-7rem)] grid-cols-1 gap-4 lg:grid-cols-4 overflow-hidden">
        {/* Chat Area */}
        <Card className="lg:col-span-3 flex flex-col overflow-hidden">
          <CardHeader className="border-b shrink-0 py-3 px-4">
            <div className="flex items-center justify-between">
              <CardTitle className="flex items-center gap-3">
                <Bot className="h-4 w-4 text-primary" />
                Enterprise AI Agent
              </CardTitle>
              <Badge variant="success" className="flex items-center gap-1">
                <span className="h-2 w-2 rounded-full bg-green-500 animate-pulse" />
                Online
              </Badge>
            </div>
          </CardHeader>

          <CardContent className="flex-1 overflow-y-auto p-4 space-y-3 min-h-0">
            {messages.map((message) => (
              <div
                key={message.id}
                className={`flex gap-3 ${
                  message.role === "user" ? "justify-end" : "justify-start"
                }`}
              >
                {message.role === "assistant" && (
                  <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-primary text-primary-foreground">
                    <Bot className="h-4 w-4" />
                  </div>
                )}

                <div
                  className={`max-w-[75%] px-5 py-4 text-sm leading-relaxed backdrop-blur-sm transition-all duration-200 ${
                    message.role === "user"
                      ? "bg-primary text-white rounded-2xl rounded-br-md shadow-[0_6px_20px_rgba(37,99,235,0.25)]"
                      : "bg-gradient-to-br from-white/80 to-blue-50/70 border border-primary/20 rounded-2xl rounded-bl-md shadow-[0_6px_18px_rgba(37,99,235,0.12)]"
                  }`}
                >
                  {(message.metadata as any)?.action === "TYPING" ? (
                    <div className="flex items-center gap-2">
                      <Loader2 className="h-4 w-4 animate-spin opacity-70" />
                      <span className="text-xs opacity-70">Escribiendo…</span>
                    </div>
                  ) : (
                    <>
                      {message.metadata?.action && (
                        <div className="mb-2">
                          {getActionBadge(message.metadata.action)}
                        </div>
                      )}

                      <p className="text-sm whitespace-pre-wrap">{message.content}</p>
                    </>
                  )}

                  {message.metadata?.confidence !== undefined && (
                    <div className="mt-2 text-xs opacity-70">
                      Confidence: {Math.round(message.metadata.confidence * 100)}%
                    </div>
                  )}

                  {message.metadata?.suggestedActions &&
                    message.metadata.suggestedActions.length > 0 && (
                      <div className="mt-3 space-y-2">
                        <p className="text-xs font-medium">Suggested Actions:</p>
                        {message.metadata.suggestedActions.map((action) => (
                          <button
                            key={action.id}
                            className="w-full rounded border border-border bg-background px-3 py-2 text-left text-xs hover:bg-accent"
                          >
                            {action.label}
                          </button>
                        ))}
                      </div>
                    )}

                  <div
                    className="mt-2 text-xs opacity-60"
                    suppressHydrationWarning
                  >
                    {new Date(message.timestamp).toLocaleTimeString()}
                  </div>
                </div>

                {message.role === "user" && (
                  <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-accent">
                    <User className="h-4 w-4" />
                  </div>
                )}
              </div>
            ))}

            {isLoading && (
              <div className="flex gap-3">
                <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-primary text-primary-foreground">
                  <Bot className="h-4 w-4" />
                </div>
                <div className="rounded-lg bg-muted p-4">
                  <Loader2 className="h-4 w-4 animate-spin" />
                </div>
              </div>
            )}

            <div ref={messagesEndRef} />
          </CardContent>

          <div className="border-t border-white/40 p-4 space-y-3 shrink-0 bg-transparent backdrop-blur-sm">
            {/* Quick chips above the input */}
            <div className="flex flex-wrap gap-1.5">
              <Button
                type="button"
                variant="outline"
                size="sm"
                className="rounded-full px-3 py-1.5 text-xs h-8"
                disabled={isLoading}
                onClick={() => sendWithDelay("Recuperar contraseña")}
              >
                🔑 Recuperar Contraseña
              </Button>

              <Button
                type="button"
                variant="outline"
                size="sm"
                className="rounded-full px-3 py-1.5 text-xs h-8"
                disabled={isLoading}
                onClick={() => sendWithDelay("Consultar ticket")}
              >
                🎫 Consultar Ticket
              </Button>
            </div>

            <div className="flex gap-1.5">
              <Textarea
                placeholder="Escribe un mensaje a AideBot..."
                value={input}
                onChange={(e) => setInput(e.target.value)}
                onKeyDown={handleKeyDown}
                className="min-h-[52px] resize-none text-sm"
                disabled={isLoading}
              />
              <Button
                onClick={handleSend}
                disabled={!input.trim() || isLoading}
                size="icon"
                className="h-[52px] w-[52px]"
              >
                {isLoading ? (
                  <Loader2 className="h-5 w-5 animate-spin" />
                ) : (
                  <Send className="h-5 w-5" />
                )}
              </Button>
            </div>

            <p className="text-xs text-muted-foreground">
              Enter para enviar, Shift+Enter para nueva línea
            </p>
          </div>
        </Card>

        {/* Quick Actions */}
        <Card className="lg:col-span-1 h-full overflow-hidden flex flex-col">
          <CardHeader className="py-3 px-4">
            <CardTitle className="flex items-center gap-2 text-sm">
              <Sparkles className="h-4 w-4 text-primary" />
              Quick Actions
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-1.5 overflow-y-auto min-h-0 p-4 pt-0">
            <button
              onClick={() => setInput("Classify the most recent incident")}
              className="w-full rounded-xl border border-white/40 bg-white/80 backdrop-blur-sm p-3 text-left text-sm shadow-[0_4px_14px_rgba(37,99,235,0.05)] transition-all duration-200 hover:shadow-[0_6px_20px_rgba(37,99,235,0.12)] hover:border-primary/40 hover:bg-white"
              disabled={isLoading}
            >
              <div className="font-semibold text-foreground">
                Classify Incident
              </div>
              <div className="text-xs text-muted-foreground mt-1">
                Auto-categorize tickets
              </div>
            </button>

            <button
              onClick={() => setInput("Summarize all open incidents")}
              className="w-full rounded-xl border border-white/40 bg-white/80 backdrop-blur-sm p-3 text-left text-sm shadow-[0_4px_14px_rgba(37,99,235,0.05)] transition-all duration-200 hover:shadow-[0_6px_20px_rgba(37,99,235,0.12)] hover:border-primary/40 hover:bg-white"
              disabled={isLoading}
            >
              <div className="font-semibold text-foreground">
                Generate Summary
              </div>
              <div className="text-xs text-muted-foreground mt-1">
                Create incident report
              </div>
            </button>

            <button
              onClick={() => setInput("Suggest resolution for high priority incidents")}
              className="w-full rounded-xl border border-white/40 bg-white/80 backdrop-blur-sm p-3 text-left text-sm shadow-[0_4px_14px_rgba(37,99,235,0.05)] transition-all duration-200 hover:shadow-[0_6px_20px_rgba(37,99,235,0.12)] hover:border-primary/40 hover:bg-white"
              disabled={isLoading}
            >
              <div className="font-semibold text-foreground">
                Suggest Solutions
              </div>
              <div className="text-xs text-muted-foreground mt-1">
                AI-powered recommendations
              </div>
            </button>

            <button
              onClick={() => setInput("Search knowledge base for network issues")}
              className="w-full rounded-xl border border-white/40 bg-white/80 backdrop-blur-sm p-3 text-left text-sm shadow-[0_4px_14px_rgba(37,99,235,0.05)] transition-all duration-200 hover:shadow-[0_6px_20px_rgba(37,99,235,0.12)] hover:border-primary/40 hover:bg-white"
              disabled={isLoading}
            >
              <div className="font-semibold text-foreground">
                Search Knowledge
              </div>
              <div className="text-xs text-muted-foreground mt-1">
                Find relevant articles
              </div>
            </button>

            <button
              onClick={() => setInput("Detect duplicate incidents")}
              className="w-full rounded-xl border border-white/40 bg-white/80 backdrop-blur-sm p-3 text-left text-sm shadow-[0_4px_14px_rgba(37,99,235,0.05)] transition-all duration-200 hover:shadow-[0_6px_20px_rgba(37,99,235,0.12)] hover:border-primary/40 hover:bg-white"
              disabled={isLoading}
            >
              <div className="font-semibold text-foreground">
                Find Duplicates
              </div>
              <div className="text-xs text-muted-foreground mt-1">
                Identify similar tickets
              </div>
            </button>

            <button
              onClick={() => setInput("Prioritize current incidents")}
              className="w-full rounded-xl border border-white/40 bg-white/80 backdrop-blur-sm p-3 text-left text-sm shadow-[0_4px_14px_rgba(37,99,235,0.05)] transition-all duration-200 hover:shadow-[0_6px_20px_rgba(37,99,235,0.12)] hover:border-primary/40 hover:bg-white"
              disabled={isLoading}
            >
              <div className="font-semibold text-foreground">
                Prioritize
              </div>
              <div className="text-xs text-muted-foreground mt-1">
                Determine urgency
              </div>
            </button>
          </CardContent>
        </Card>
      </div>
    </MainLayout>
  );
}
