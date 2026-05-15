"use client";

import { MainLayout } from "@/components/layout/main-layout";
import { Card, CardContent, Button, Input } from "@/components/ui";
import { useState, useRef, useEffect } from "react";
import { Send, Bot, User, Loader2 } from "lucide-react";

interface ExecutionStep {
  name: string;
}

interface ExecutionTrace {
  mode: string;
  steps: ExecutionStep[];
  totalMs: number;
}

interface Message {
  id: string;
  role: "user" | "assistant";
  content: string;
  execution?: ExecutionTrace;
}

export default function ChatPage() {
  const [messages, setMessages] = useState<Message[]>([
    {
      id: "1",
      role: "assistant",
      content:
        "Hola 👋\n\n" +
        "Soy tu asistente de operaciones IT.\n\n" +
        "Puedo ayudarte a revisar incidentes, su estado, prioridad o cualquier información registrada en el sistema.\n\n" +
        "Cuéntame qué necesitas y lo vemos juntos.",
    },
  ]);

  const [input, setInput] = useState("");
  const [conversationId, setConversationId] = useState<string | null>(null);

  useEffect(() => {
    setConversationId(crypto.randomUUID());
  }, []);

  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const messagesEndRef = useRef<HTMLDivElement | null>(null);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages]);

  const sendMessage = async () => {
    if (!input.trim() || isLoading) return;

    const userMessage: Message = {
      id: Date.now().toString(),
      role: "user",
      content: input,
    };

    setMessages((prev) => [...prev, userMessage]);
    setInput("");
    setIsLoading(true);
    setError(null);

    try {
      const response = await fetch("/api/agent/chat", {
        method: "POST",
        credentials: "include",
        cache: "no-store", // ✅ Prevent Next.js RSC caching / proxy conflicts
        headers: {
          "Content-Type": "application/json",
          "X-User-Id": localStorage.getItem("user_id") || ""
        },
        body: JSON.stringify({
          message: input,
          sessionId: conversationId,
          user: {
            sys_id: localStorage.getItem("user_id") || "frontend-session-user",
            username: localStorage.getItem("username") || "frontend.user",
            email: localStorage.getItem("email") || "frontend@local",
            fullName: localStorage.getItem("fullName") || "Frontend User",
            roles: ["user"]
          }
        }),
      });

      const data = await response.json();

      // ✅ Artificial 3-second delay for all assistant messages
      await new Promise((resolve) => setTimeout(resolve, 3000));

      const assistantReply: Message = {
        id: (Date.now() + 1).toString(),
        role: "assistant",
        content: data.message || "Sin respuesta del backend",
        execution: data.execution || undefined,
      };

      setMessages((prev) => [...prev, assistantReply]);
    } catch (err) {
      console.error(err);
      setError("An error occurred while contacting the AI service.");
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <MainLayout
      title="Enterprise Service Operations Assistant"
      subtitle="Official IT Operations Interface • Deterministic & Policy-Controlled"
    >
      <div className="h-[calc(100vh-140px)] flex flex-col max-w-4xl mx-auto w-full">
        <Card className="flex flex-col border shadow-sm h-full">
          <CardContent className="flex flex-col p-0 h-full">
            <div className="flex-1 overflow-y-auto px-6 py-6 space-y-4 bg-muted/10">
              {messages.map((message) => (
                <div
                  key={message.id}
                  className={`flex ${
                    message.role === "user"
                      ? "justify-end"
                      : "justify-start"
                  }`}
                >
                  <div className="flex flex-col max-w-[75%] w-full">
                    <div
                      className={`rounded-xl px-4 py-3 text-sm leading-relaxed ${
                        message.role === "user"
                          ? "bg-primary text-primary-foreground"
                          : "bg-muted/30 border border-border"
                      }`}
                    >
                      <div className="flex items-start gap-2">
                        {message.role === "assistant" && (
                          <Bot className="w-4 h-4 mt-0.5 text-primary shrink-0" />
                        )}
                        {message.role === "user" && (
                          <User className="w-4 h-4 mt-0.5 shrink-0" />
                        )}
                        <div className="whitespace-pre-wrap">
                          {message.content}
                        </div>
                      </div>
                    </div>

                    {message.execution && (
                      <details className="mt-2 text-xs text-muted-foreground bg-muted/20 border rounded-md px-3 py-2">
                        <summary className="cursor-pointer font-medium">
                          Ver ejecución ({message.execution.totalMs} ms)
                        </summary>
                        <div className="mt-2 space-y-1">
                          <div className="text-[11px] uppercase tracking-wide">
                            Modo: {message.execution.mode}
                          </div>
                          {message.execution.steps.map((step, index) => (
                            <div key={index}>• {step.name}</div>
                          ))}
                        </div>
                      </details>
                    )}
                  </div>
                </div>
              ))}

              {isLoading && (
                <div className="flex justify-start">
                  <div className="bg-white border rounded-2xl px-4 py-3 shadow-sm flex items-center gap-2">
                    <Loader2 className="w-4 h-4 animate-spin text-primary" />
                    <span className="text-sm text-muted-foreground">
                      AI is thinking...
                    </span>
                  </div>
                </div>
              )}

              {error && (
                <div className="text-sm text-red-500">{error}</div>
              )}

              <div ref={messagesEndRef} />
            </div>

            <div className="border-t px-6 py-4 bg-white">
              <div className="flex items-center gap-3">
                <Input
                  placeholder="Ej: INC0010002"
                  value={input}
                  onChange={(e) => setInput(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === "Enter") sendMessage();
                  }}
                  disabled={isLoading}
                />
                <Button onClick={sendMessage} disabled={isLoading}>
                  <Send className="w-4 h-4 mr-2" />
                  Send
                </Button>
              </div>
              <div className="text-[11px] text-muted-foreground mt-2">
                Session: {conversationId?.slice(0, 8) ?? "—"}
              </div>
            </div>
          </CardContent>
        </Card>
      </div>
    </MainLayout>
  );
}
