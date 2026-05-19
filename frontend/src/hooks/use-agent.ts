"use client";

import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { agentApi } from "@/lib/api-client";
import { AgentRequest, AgentResponse, Message } from "@/types";

/**
 * Hook para interactuar con el AI Agent
 * Maneja chat, contexto de conversación y mensajes
 */

interface UseAgentOptions {
  conversationId?: string;
  onMessageReceived?: (response: AgentResponse) => void;
  onError?: (error: Error) => void;
}

export function useAgent(options: UseAgentOptions = {}) {
  const [messages, setMessages] = useState<Message[]>([]);
  const [conversationId, setConversationId] = useState<string | null>(
    options.conversationId || null
  );

  const chatMutation = useMutation({
    mutationFn: async (request: AgentRequest) => {
      const response = await agentApi.chat(request);
      return response;
    },
    onSuccess: async (response) => {
      // Normalize response shape: backend returns { success, message, intent, confidence, sessionId, data }
      // Previous shape expected timestamp/action/suggestedActions at top-level.
      const anyResp = response as any;

      const content =
        anyResp?.message ?? anyResp?.data?.message ?? anyResp?.data?.content ?? "";

      const timestamp =
        anyResp?.timestamp ?? anyResp?.data?.timestamp ?? new Date().toISOString();

      const action =
        anyResp?.action ??
        anyResp?.metadata?.action ??
        anyResp?.intent ??
        undefined;

      const confidence =
        anyResp?.confidence ?? anyResp?.metadata?.confidence ?? undefined;

      const suggestedActions =
        anyResp?.suggestedActions ??
        anyResp?.metadata?.suggestedActions ??
        undefined;

      const multi = anyResp?.messages ?? anyResp?.data?.messages ?? null;

      const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

      if (Array.isArray(multi) && multi.length > 0) {
        // Render each part as a separate assistant bubble with a small typing illusion between them
        for (let i = 0; i < multi.length; i++) {
          const part = String(multi[i] ?? "");

          // Add assistant bubble
          setMessages((prev) => [
            ...prev,
            {
              id: `${Date.now()}-${i}`,
              role: "assistant" as const,
              content: part,
              timestamp,
              metadata: {
                action,
                confidence,
                suggestedActions,
              },
            },
          ]);

          // Between messages: show loader briefly (typing illusion)
          if (i < multi.length - 1) {
            const typingId = `${Date.now()}-typing`;

            // insert a temporary "typing" bubble (same UI as isLoading)
            setMessages((prev) => [
              ...prev,
              {
                id: typingId,
                role: "assistant" as const,
                content: "",
                timestamp,
                metadata: { action: "TYPING" },
              } as any,
            ]);

            await sleep(650);

            // remove the typing bubble
            setMessages((prev) => prev.filter((m) => m.id !== typingId));
          }
        }
      } else {
        setMessages((prev) => [
          ...prev,
          {
            id: Date.now().toString(),
            role: "assistant",
            content,
            timestamp,
            metadata: {
              action,
              confidence,
              suggestedActions,
            },
          },
        ]);
      }

      // Update conversation/session id if present
      const newConversationId =
        anyResp?.metadata?.conversationId ??
        anyResp?.conversationId ??
        anyResp?.sessionId ??
        anyResp?.data?.sessionId ??
        null;

      if (newConversationId && !conversationId) {
        setConversationId(String(newConversationId));
      }

      options.onMessageReceived?.(response);
    },
    onError: (error) => {
      console.error("Agent chat error:", error);
      options.onError?.(error as Error);
    },
  });

  /**
   * Enviar mensaje al agente
   */
  const sendMessage = (content: string, additionalContext?: Record<string, any>) => {
    if (!content?.trim()) return;

    // Agregar mensaje del usuario inmediatamente (optimistic update)
    const userMessage: Message = {
      id: Date.now().toString(),
      role: "user",
      content,
      timestamp: new Date().toISOString(),
    };

    setMessages((prev) => [...prev, userMessage]);

    // Enviar al backend (align with backend contract)
    // Backend expects: { message, sessionId, user, context? }
    const request: AgentRequest = {
      message: content,
      sessionId: conversationId || undefined,
      user: {
        // Dev fallback user; UI auth integration can override via additionalContext.user
        sys_id: "web-ui-dev-user",
        username: "web.ui",
        email: "web.ui@local",
        fullName: "Web UI Dev User",
        roles: ["user"],
        ...(additionalContext?.user || {}),
      } as any,
      context: {
        ...(additionalContext?.context || {}),
      } as any,
    } as any;

    chatMutation.mutate(request);
  };

  const clearConversation = () => {
    setMessages([]);
    setConversationId(null);
  };

  const reset = () => {
    clearConversation();
    chatMutation.reset();
  };

  return {
    messages,
    conversationId,
    sendMessage,
    clearConversation,
    reset,
    isLoading: chatMutation.isPending,
    error: chatMutation.error,
  };
}

/**
 * Hook para sugerencias del agente
 * Útil para mostrar sugerencias contextuales sin iniciar conversación
 */
export function useAgentSuggestions() {
  return useMutation({
    mutationFn: async (context: Record<string, any>) => {
      const response = await agentApi.chat({
        message: "Provide suggestions based on the context",
        context,
      } as any);
      return response;
    },
  });
}
