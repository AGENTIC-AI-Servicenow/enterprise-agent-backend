"use client";

import { MainLayout } from "@/components/layout/main-layout";
import { Card, CardContent, Button, Input } from "@/components/ui";
import { useState, useRef, useEffect } from "react";
import { Send, Bot, User, Loader2 } from "lucide-react";

interface Message {
  id: string;
  role: "user" | "assistant";
  content: string;
}

export default function AIAssistantPage() {
  const [messages, setMessages] = useState<Message[]>([
    {
      id: "1",
      role: "assistant",
      content:
        "👋 Hola, soy tu AI Assistant. Puedes preguntarme sobre tus tickets, su estado o cualquier información relacionada.",
    },
  ]);

  const [input, setInput] = useState("");
  const [conversationId] = useState<string>(() => crypto.randomUUID());
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
      /**
       * 🔜 FUTURE BACKEND CONTRACT (Microsoft Foundry ready)
       * POST /api/assistant/chat
       * {
       *   conversationId,
       *   messages
       * }
       */

      // Placeholder simulated response
      await new Promise((resolve) => setTimeout(resolve, 1000));

      const assistantReply: Message = {
        id: (Date.now() + 1).toString(),
        role: "assistant",
        content:
          "🔎 (Arquitectura lista para Azure Foundry) Próximamente responderé usando el motor de AI conectado al backend.",
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
      title="AI Assistant"
      subtitle="Consult and analyze your ServiceNow tickets using AI"
    >
      <div className="h-[calc(100vh-140px)] flex flex-col">
        <Card className="flex-1 flex flex-col overflow-hidden">
          <CardContent className="flex-1 flex flex-col p-0">
            {/* Messages */}
            <div className="flex-1 overflow-y-auto p-6 space-y-6 bg-muted/20">
              {messages.map((message) => (
                <div
                  key={message.id}
                  className={`flex ${
                    message.role === "user"
                      ? "justify-end"
                      : "justify-start"
                  }`}
                >
                  <div
                    className={`max-w-[70%] rounded-2xl px-4 py-3 shadow-sm ${
                      message.role === "user"
                        ? "bg-primary text-primary-foreground"
                        : "bg-white border"
                    }`}
                  >
                    <div className="flex items-start gap-3">
                      {message.role === "assistant" && (
                        <Bot className="w-4 h-4 mt-1 text-primary" />
                      )}
                      {message.role === "user" && (
                        <User className="w-4 h-4 mt-1" />
                      )}
                      <div className="text-sm leading-relaxed">
                        {message.content}
                      </div>
                    </div>
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

            {/* Input */}
            <div className="border-t p-4 bg-white">
              <div className="flex items-center gap-3">
                <Input
                  placeholder="Ask about incidents, status, priority..."
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
              <div className="text-xs text-muted-foreground mt-2">
                Conversation ID: {conversationId}
              </div>
            </div>
          </CardContent>
        </Card>
      </div>
    </MainLayout>
  );
}
