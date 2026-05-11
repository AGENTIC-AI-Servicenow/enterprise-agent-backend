package com.enterprise.agent.memory;

import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * ConversationMemory: Gestión de memoria conversacional por sesión
 * 
 * FUNCIONALIDAD:
 * - Almacena historial de conversaciones por sessionId
 * - Permite recuperar contexto reciente para el LLM
 * - Implementa TTL automático (limpieza de sesiones antiguas)
 * - Thread-safe con ConcurrentHashMap
 * 
 * CASOS DE USO:
 * 1. Usuario: "Dame el estado de mi último ticket"
 *    → El sistema busca en el historial cuál fue el último INC mencionado
 * 
 * 2. Usuario: "Y cuándo se creó?"
 *    → El contexto permite entender que se refiere al ticket anterior
 * 
 * MVP: Memoria en RAM (se pierde al reiniciar)
 * PRODUCCIÓN: Migrar a Redis para persistencia distribuida
 */
@Component
@Log4j2
public class ConversationMemory {

    // Almacenamiento por sesión
    private final Map<String, SessionMemory> sessions = new ConcurrentHashMap<>();
    
    // TTL de sesiones: 1 hora
    private static final long SESSION_TTL_MS = 60 * 60 * 1000;
    
    // Límite máximo de mensajes por sesión
    private static final int MAX_MESSAGES_PER_SESSION = 100;

    /**
     * Memoria de una sesión individual
     */
    @Data
    public static class SessionMemory {
        private final String sessionId;
        private final List<ConversationMessage> messages = new ArrayList<>();
        private Instant lastAccessTime = Instant.now();
        
        public SessionMemory(String sessionId) {
            this.sessionId = sessionId;
        }
        
        public void addMessage(ConversationMessage message) {
            messages.add(message);
            lastAccessTime = Instant.now();
            
            // Limitar tamaño para evitar OOM
            if (messages.size() > MAX_MESSAGES_PER_SESSION) {
                messages.remove(0);
            }
        }
        
        public boolean isExpired() {
            long age = Instant.now().toEpochMilli() - lastAccessTime.toEpochMilli();
            return age > SESSION_TTL_MS;
        }
    }

    /**
     * Mensaje individual en la conversación
     */
    @Data
    public static class ConversationMessage {
        private final String role; // "user" o "assistant"
        private final String content;
        private final Instant timestamp;
        private final Map<String, Object> metadata; // intent, incident_id, etc.
        
        public ConversationMessage(String role, String content, Map<String, Object> metadata) {
            this.role = role;
            this.content = content;
            this.timestamp = Instant.now();
            this.metadata = metadata != null ? metadata : Map.of();
        }
    }

    /**
     * Agregar mensaje del usuario
     */
    public void addUserMessage(String sessionId, String content, Map<String, Object> metadata) {
        SessionMemory session = getOrCreateSession(sessionId);
        session.addMessage(new ConversationMessage("user", content, metadata));
        log.debug("Added user message to session {}: {}", sessionId, content);
    }

    /**
     * Agregar respuesta del asistente
     */
    public void addAssistantMessage(String sessionId, String content, Map<String, Object> metadata) {
        SessionMemory session = getOrCreateSession(sessionId);
        session.addMessage(new ConversationMessage("assistant", content, metadata));
        log.debug("Added assistant message to session {}", sessionId);
    }

    /**
     * Obtener historial reciente como String formateado para LLM
     * 
     * @param sessionId ID de sesión
     * @param limit número de mensajes recientes
     * @return String con formato "User: ... / Assistant: ..."
     */
    public String getRecentHistory(String sessionId, int limit) {
        SessionMemory session = sessions.get(sessionId);
        
        if (session == null || session.getMessages().isEmpty()) {
            return "";
        }
        
        // Obtener últimos N mensajes
        List<ConversationMessage> recent = session.getMessages()
            .stream()
            .skip(Math.max(0, session.getMessages().size() - limit))
            .collect(Collectors.toList());
        
        // Formatear para el LLM
        return recent.stream()
            .map(msg -> {
                String roleName = msg.getRole().equals("user") ? "User" : "Assistant";
                return roleName + ": " + msg.getContent();
            })
            .collect(Collectors.joining("\n"));
    }

    /**
     * Obtener último incident_id mencionado en la sesión
     * Útil para resolver referencias como "mi último ticket"
     */
    public String getLastIncidentId(String sessionId) {
        SessionMemory session = sessions.get(sessionId);
        
        if (session == null) {
            return null;
        }
        
        // Buscar en orden inverso
        for (int i = session.getMessages().size() - 1; i >= 0; i--) {
            ConversationMessage msg = session.getMessages().get(i);
            Object incidentId = msg.getMetadata().get("incident_id");
            
            if (incidentId != null) {
                return incidentId.toString();
            }
        }
        
        return null;
    }

    /**
     * Obtener o crear sesión
     */
    private SessionMemory getOrCreateSession(String sessionId) {
        return sessions.computeIfAbsent(sessionId, SessionMemory::new);
    }

    /**
     * Limpiar sesiones expiradas
     * TODO: En producción, ejecutar con @Scheduled cada 10 minutos
     */
    public void cleanExpiredSessions() {
        int removed = 0;
        
        Iterator<Map.Entry<String, SessionMemory>> iterator = sessions.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, SessionMemory> entry = iterator.next();
            if (entry.getValue().isExpired()) {
                iterator.remove();
                removed++;
            }
        }
        
        if (removed > 0) {
            log.info("Cleaned {} expired sessions", removed);
        }
    }

    /**
     * Obtener todas las sesiones activas (para debugging)
     */
    public int getActiveSessionCount() {
        return sessions.size();
    }

    // =====================================================
    // LEGACY METHODS (mantener compatibilidad con código existente)
    // =====================================================
    
    private final List<String> successfulIncidents = new ArrayList<>();
    private final List<String> failedIncidents = new ArrayList<>();

    public void addSuccessful(String number) {
        if (!successfulIncidents.contains(number)) {
            successfulIncidents.add(number);
        }
    }

    public void addFailed(String number) {
        if (!failedIncidents.contains(number)) {
            failedIncidents.add(number);
        }
    }

    public List<String> getSuccessfulIncidents() {
        return successfulIncidents;
    }

    public List<String> getFailedIncidents() {
        return failedIncidents;
    }

    public String getLastSuccessful() {
        if (successfulIncidents.isEmpty()) return null;
        return successfulIncidents.get(successfulIncidents.size() - 1);
    }
}
