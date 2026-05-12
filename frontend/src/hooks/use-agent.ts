"use client";

import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { agentApi } from '@/lib/api-client';
import { AgentRequest, AgentResponse, Message } from '@/types';

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

  // Mutation para enviar mensaje al agente
  const chatMutation = useMutation({
    mutationFn: async (request: AgentRequest) => {
      const response = await agentApi.chat(request);
      return response;
    },
    onSuccess: (response) => {
      // Agregar respuesta del agente a los mensajes
      setMessages((prev) => [
        ...prev,
        {
          id: Date.now().toString(),
          role: 'assistant',
          content: response.message,
          timestamp: response.timestamp,
          metadata: {
            action: response.action,
            confidence: response.confidence,
            suggestedActions: response.suggestedActions,
          },
        },
      ]);

      // Actualizar conversationId si viene en el contexto
      if (response.metadata?.conversationId && !conversationId) {
        setConversationId(response.metadata.conversationId as string);
      }

      options.onMessageReceived?.(response);
    },
    onError: (error) => {
      console.error('Agent chat error:', error);
      options.onError?.(error as Error);
    },
  });

  /**
   * Enviar mensaje al agente
   */
  const sendMessage = (content: string, additionalContext?: Record<string, any>) => {
    // Agregar mensaje del usuario inmediatamente (optimistic update)
    const userMessage: Message = {
      id: Date.now().toString(),
      role: 'user',
      content,
      timestamp: new Date().toISOString(),
    };

    setMessages((prev) => [...prev, userMessage]);

    // Enviar al backend
    const request: AgentRequest = {
      message: content,
      context: {
        conversationId: conversationId || undefined,
        ...additionalContext,
      },
    };

    chatMutation.mutate(request);
  };

  /**
   * Limpiar conversación actual
   */
  const clearConversation = () => {
    setMessages([]);
    setConversationId(null);
  };

  /**
   * Reiniciar agente con nueva conversación
   */
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
        message: 'Provide suggestions based on the context',
        context,
      });
      return response;
    },
  });
}
