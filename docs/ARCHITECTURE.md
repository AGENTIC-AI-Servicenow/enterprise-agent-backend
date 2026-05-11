# Arquitectura Enterprise Agent - Fase 1 MVP

## 🎯 Visión General

Sistema agéntico enterprise integrado con ServiceNow que proporciona capacidades de **AI Copilot** para automatizar y asistir operaciones de Service Desk mediante clasificación inteligente, enrutamiento automático y procesamiento contextual.

### Principios de Diseño

1. **Separation of Concerns**: Cada componente tiene responsabilidades claras y únicas
2. **Enterprise-Ready**: Diseñado para escalabilidad, observabilidad y mantenibilidad
3. **Intent-Driven Architecture**: Flujo basado en clasificación de intenciones
4. **Stateful Conversations**: Memoria conversacional para contexto multiturno
5. **Extensibilidad**: Preparado para evolucionar hacia agentes autónomos (Fase 2)

---

## 📐 Arquitectura de Componentes

```
┌─────────────────────────────────────────────────────────────────┐
│                        API Layer (REST)                         │
│                      AgentController                            │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│                   Orchestration Layer                           │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │              AgentOrchestrator                           │  │
│  │  • Punto de entrada unificado                           │  │
│  │  • Coordinación de flujo completo                       │  │
│  │  • Manejo de errores y timeouts                         │  │
│  │  • Observabilidad (logging, métricas)                   │  │
│  └──────────────────────────────────────────────────────────┘  │
│                         │                                        │
│                         ▼                                        │
│  ┌─────────────────────────────────────────────┐               │
│  │         IntentClassifier                    │               │
│  │  • Clasificación de intenciones            │               │
│  │  • Extracción de parámetros                │               │
│  │  • Análisis de contexto conversacional     │               │
│  └─────────────────────────────────────────────┘               │
│                         │                                        │
│                         ▼                                        │
│  ┌─────────────────────────────────────────────┐               │
│  │           ActionRouter                      │               │
│  │  • Enrutamiento basado en intención        │               │
│  │  • Ejecución de acciones específicas       │               │
│  │  • Integración con ServiceNow              │               │
│  └─────────────────────────────────────────────┘               │
└─────────────────────────────────────────────────────────────────┘
                         │
        ┌────────────────┼────────────────┐
        ▼                ▼                ▼
┌──────────────┐  ┌─────────────┐  ┌──────────────┐
│   LLMService │  │ ServiceNow  │  │ Conversation │
│              │  │   Client    │  │   Memory     │
│ • Prompts    │  │             │  │              │
│ • Generation │  │ • OAuth 2.0 │  │ • Context    │
│ • Classification│ • REST APIs │  │ • History    │
└──────────────┘  └─────────────┘  └──────────────┘
```

---

## 🧩 Componentes Principales

### 1. **AgentController** (`controller/AgentController.java`)

**Responsabilidad**: API REST endpoint para interacción con el agente.

**Endpoints**:
- `POST /api/agent/chat`: Procesamiento de mensajes del usuario

**Input**: 
```json
{
  "message": "string",
  "sessionId": "string",
  "metadata": {}
}
```

**Output**:
```json
{
  "success": true,
  "message": "string",
  "intent": "ENUM",
  "confidence": 0.95,
  "executionTimeMs": 1234,
  "metadata": {}
}
```

**Características Enterprise**:
- Extracción automática de UserContext desde JWT/Security
- Validación de input
- Manejo de errores HTTP estándar
- Logging de auditoría

---

### 2. **AgentOrchestrator** (`agent/AgentOrchestrator.java`)

**Responsabilidad**: Orquestador central del flujo agéntico.

**Flujo de Procesamiento**:

```
1. Recepción de Request
   ↓
2. Validación de entrada
   ↓
3. Clasificación de Intención (IntentClassifier)
   ↓
4. Enrutamiento de Acción (ActionRouter)
   ↓
5. Actualización de Memoria (ConversationMemory)
   ↓
6. Construcción de Response
   ↓
7. Logging y Métricas
```

**Características**:
- ✅ Manejo centralizado de errores
- ✅ Timeout configurable
- ✅ Logging estructurado
- ✅ Métricas de performance
- ✅ Contexto de usuario integrado

**Puntos de Extensión (Fase 2)**:
- Tool calling registry
- Multi-agent coordination
- Planning & reasoning loops
- Human-in-the-loop approval

---

### 3. **IntentClassifier** (`agent/IntentClassifier.java`)

**Responsabilidad**: Clasificación inteligente de intenciones del usuario.

**Modelo de Intenciones ITSM**:

```java
public enum Intent {
    // === CONSULTA DE TICKETS ===
    GET_INCIDENT,           // Obtener detalles de un incidente específico
    SEARCH_INCIDENTS,       // Buscar incidentes con criterios
    LIST_MY_INCIDENTS,      // Listar incidentes del usuario actual
    
    // === ANÁLISIS ===
    ANALYZE_INCIDENT,       // Análisis profundo de un incidente
    SUMMARIZE_INCIDENT,     // Resumen ejecutivo
    INCIDENT_TIMELINE,      // Línea de tiempo de eventos
    
    // === GESTIÓN ===
    CREATE_INCIDENT,        // Crear nuevo incidente
    UPDATE_INCIDENT,        // Actualizar campos
    ASSIGN_INCIDENT,        // Asignar a grupo/persona
    ESCALATE_INCIDENT,      // Escalar prioridad/soporte
    RESOLVE_INCIDENT,       // Marcar como resuelto
    CLOSE_INCIDENT,         // Cerrar formalmente
    
    // === KNOWLEDGE BASE ===
    SEARCH_KNOWLEDGE,       // Buscar en KB (futuro RAG)
    SUGGEST_RESOLUTION,     // Sugerir solución basada en KB
    
    // === DUPLICADOS & RELACIONES ===
    FIND_SIMILAR_INCIDENTS, // Detectar duplicados
    CHECK_RELATED_INCIDENTS,// Buscar incidentes relacionados
    
    // === REPORTES ===
    GENERATE_METRICS,       // Métricas y estadísticas
    
    // === CONVERSACIÓN ===
    CHAT,                   // Conversación general
    UNKNOWN                 // Fallback
}
```

**Proceso de Clasificación**:

1. **System Prompt Engineering**: Prompt estructurado con ejemplos y formato JSON estricto
2. **LLM Inference**: Llamada a LLM (Ollama) con temperatura=0.0 para consistencia
3. **JSON Parsing**: Extracción de intención y parámetros
4. **Validation**: Validación de intención válida
5. **Fallback**: Si falla, clasifica como CHAT

**Parámetros Extraídos**:
- `incidentNumber`: INC0010001
- `searchQuery`: "problemas de red"
- `priority`: 1-5
- `category`: "network", "hardware", etc.

---

### 4. **ActionRouter** (`agent/ActionRouter.java`)

**Responsabilidad**: Enrutamiento y ejecución de acciones basadas en intención.

**Acciones Implementadas**:

| Intent | Acción | Descripción |
|--------|--------|-------------|
| `GET_INCIDENT` | `handleGetIncident()` | Consulta incidente por número |
| `SEARCH_INCIDENTS` | `handleSearchIncidents()` | Búsqueda con filtros |
| `LIST_MY_INCIDENTS` | `handleListMyIncidents()` | Tickets del usuario actual |
| `ANALYZE_INCIDENT` | `handleAnalyzeIncident()` | Análisis con LLM |
| `SUMMARIZE_INCIDENT` | `handleSummarizeIncident()` | Resumen ejecutivo |
| `CREATE_INCIDENT` | `handleCreateIncident()` | Creación de ticket |
| `CHAT` | `handleChat()` | Conversación libre |
| `UNKNOWN` | `handleUnknown()` | Fallback genérico |

**Integración con ServiceNow**:
```java
// Ejemplo: Obtener incidente
JsonNode incident = serviceNowClient.getIncident(number, userContext);

// Ejemplo: Crear incidente
JsonNode created = serviceNowClient.createIncident(data, userContext);
```

**Características**:
- Autenticación OAuth 2.0 automática
- Manejo de errores específicos de ServiceNow
- Formateo de respuestas con LLM
- Logging de auditoría

---

### 5. **LLMService** (`service/LLMService.java`)

**Responsabilidad**: Abstracción del LLM provider (Ollama/OpenAI).

**Métodos Principales**:

```java
// Clasificación con system prompt personalizado
String classify(String systemPrompt, String userInput);

// Generación de resumen de incidente
String generateIncidentSummary(String contextData);

// Respuesta conversacional
String generateChatResponse(String userInput);

// Método centralizado
String generate(String prompt, double temperature, int maxTokens);
```

**Configuración**:
- Model: Configurable vía `application.yml`
- Temperature: 0.0 (clasificación) a 0.6 (chat)
- Max Tokens: 150-500 según caso de uso
- Timeout: 120s para modelos locales

**Enterprise Considerations**:
- ⚠️ **Costo de Inferencia**: Monitoreado por tokens/request
- ⚠️ **Latencia**: Crítico para UX (<2s ideal)
- ⚠️ **Fallback**: Plan B si LLM no disponible
- ⚠️ **Rate Limiting**: Protección contra abuso

---

### 6. **ConversationMemory** (`memory/ConversationMemory.java`)

**Responsabilidad**: Gestión de contexto conversacional multi-turno.

**Capacidades**:
```java
// Guardar interacción
void save(String sessionId, String userMessage, String agentResponse, Intent intent);

// Obtener historial reciente (últimos N turnos)
List<Interaction> getRecentHistory(String sessionId, int limit);

// Obtener historial completo
List<Interaction> getHistory(String sessionId);

// Limpiar sesión
void clearSession(String sessionId);
```

**Estructura de Interacción**:
```java
public record Interaction(
    String userMessage,
    String agentResponse,
    Intent intent,
    long timestamp
) {}
```

**Implementación Actual**:
- **Storage**: In-memory ConcurrentHashMap
- **Límite**: 50 interacciones por sesión
- **TTL**: Manual cleanup (futuro: expiration automático)

**Roadmap (Fase 2)**:
- [ ] Persistencia en base de datos (Redis/PostgreSQL)
- [ ] Embeddings para búsqueda semántica
- [ ] Compresión de historial largo (summarization)
- [ ] Context window management

---

### 7. **ServiceNowClient** (`client/ServiceNowClient.java`)

**Responsabilidad**: Cliente HTTP para APIs de ServiceNow.

**Autenticación**: OAuth 2.0 Authorization Code Flow

**APIs Integradas**:

```java
// Incidentes
JsonNode getIncident(String number, UserContext context);
JsonNode createIncident(Map<String, Object> data, UserContext context);
JsonNode updateIncident(String sysId, Map<String, Object> data, UserContext context);
JsonNode searchIncidents(Map<String, String> filters, UserContext context);

// Usuario
JsonNode getUserProfile(String username, UserContext context);
```

**Headers de Seguridad**:
```
Authorization: Bearer <oauth_token>
X-User-ID: <user_sys_id>
X-Request-ID: <uuid>
Content-Type: application/json
```

---

## 🔐 Seguridad Enterprise

### Autenticación & Autorización

1. **Frontend → Backend**: JWT/Session-based auth
2. **Backend → ServiceNow**: OAuth 2.0 Authorization Code Flow
3. **User Context**: Propagado en todas las capas

### User Context

```java
@Builder
public class UserContext {
    private String userId;           // ServiceNow sys_id
    private String username;         // Login name
    private String email;
    private String fullName;
    private Set<String> roles;       // RBAC roles
    private long requestTimestamp;
}
```

**Propagación**:
```
HTTP Request → AgentController 
             → SecurityContext extraction
             → UserContext creation
             → Pass to Orchestrator
             → Pass to ActionRouter
             → Pass to ServiceNowClient
```

### Consideraciones de Seguridad

- ✅ **Token Storage**: Secure storage en backend, nunca en frontend
- ✅ **RBAC**: Roles verificados antes de acciones críticas
- ✅ **Audit Log**: Todas las acciones loggeadas con user ID
- ✅ **Input Validation**: Sanitización de inputs
- ⚠️ **Rate Limiting**: Pendiente implementación
- ⚠️ **Prompt Injection**: Validación básica, mejorar en Fase 2

---

## 📊 Observabilidad

### Logging Estructurado

```java
logger.info("Processing agent request - Session: {}, Intent: {}, User: {}", 
    sessionId, intent, userId);
logger.debug("Classified intent: {} with confidence: {}", intent, confidence);
logger.error("Action execution failed", exception);
```

**Niveles**:
- `INFO`: Flujo principal, decisiones de negocio
- `DEBUG`: Detalles técnicos, clasificación
- `WARN`: Fallbacks, retries
- `ERROR`: Fallos, excepciones

### Métricas (Preparadas para instrumentación)

```java
// En AgentOrchestrator
long startTime = System.currentTimeMillis();
// ... proceso ...
