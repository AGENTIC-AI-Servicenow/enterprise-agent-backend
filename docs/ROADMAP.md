# Roadmap - Enterprise Agent Evolution

## 🎯 Estado Actual: Fase 1 MVP - AI Copilot ✅

### Componentes Implementados

- ✅ **Arquitectura base Intent-Driven**
- ✅ **IntentClassifier** con 20+ intenciones ITSM
- ✅ **AgentOrchestrator** (flujo unificado)
- ✅ **ActionRouter** (enrutamiento y ejecución)
- ✅ **Memoria conversacional** (in-memory)
- ✅ **Integración ServiceNow** (OAuth 2.0)
- ✅ **LLM Service** (Ollama/OpenAI ready)
- ✅ **User Context** propagación
- ✅ **API REST** enterprise-ready

### Capacidades Funcionales

- ✅ Consulta de incidentes específicos
- ✅ Búsqueda de incidentes con filtros
- ✅ Listado de tickets del usuario
- ✅ Análisis inteligente de incidentes
- ✅ Resumen ejecutivo de tickets
- ✅ Creación de incidentes
- ✅ Conversación contextual multi-turno
- ✅ Logging y observabilidad básica

---

## 📅 Fase 1.5: Mejoras del Copilot (Próximos 2-4 semanas)

### 1. RAG para Knowledge Base

**Objetivo**: Búsqueda semántica en base de conocimiento de ServiceNow.

**Componentes a Implementar**:

```
VectorStore (Pinecone/Qdrant/Weaviate)
     ↓
EmbeddingService (OpenAI/Ollama)
     ↓
KnowledgeBaseService
     ↓
ActionRouter (nuevas intenciones)
```

**Tasks**:
- [ ] Implementar `EmbeddingService`
- [ ] Integración con vector database
- [ ] Pipeline de ingesta de KB articles
- [ ] `SEARCH_KNOWLEDGE` intent handler
- [ ] `SUGGEST_RESOLUTION` con RAG
- [ ] Hybrid search (keyword + semantic)

**Métricas de Éxito**:
- Relevancia de resultados >80%
- Latencia <3s
- Reducción de tickets "resolvibles con KB" en 30%

---

### 2. Detección de Duplicados Semántica

**Objetivo**: Identificar tickets duplicados mediante embeddings.

```java
@Service
public class DuplicateDetectionService {
    
    public List<SimilarIncident> findSimilar(String incidentDescription) {
        // 1. Generate embedding
        float[] embedding = embeddingService.embed(incidentDescription);
        
        // 2. Vector similarity search
        List<VectorMatch> matches = vectorStore.search(embedding, topK=5);
        
        // 3. Fetch incident details
        // 4. Return ranked results
    }
}
```

**Tasks**:
- [ ] Indexar descripciones de incidentes históricos
- [ ] Implementar similarity search
- [ ] `FIND_SIMILAR_INCIDENTS` handler
- [ ] UI para mostrar duplicados potenciales
- [ ] Workflow de merge/link tickets

---

### 3. Priorización Automática Inteligente

**Objetivo**: Clasificar prioridad con ML basado en historial.

**Modelo**:
```
Input: {
  description: string,
  category: string,
  affected_users: number,
  business_impact: string,
  historical_similar: [...]
}
↓
ML Model (trained on historical data)
↓
Output: {
  priority: 1-5,
  confidence: 0.92,
  reasoning: string
}
```

**Tasks**:
- [ ] Extraer dataset de incidentes históricos
- [ ] Feature engineering (TF-IDF, embeddings, metadata)
- [ ] Entrenar modelo (XGBoost/Random Forest)
- [ ] Endpoint de predicción
- [ ] Integración en `CREATE_INCIDENT`
- [ ] A/B testing vs reglas actuales

---

### 4. Auto-Assignment Inteligente

**Objetivo**: Asignar tickets al agente/equipo óptimo.

```java
@Service
public class SmartAssignmentService {
    
    public AssignmentRecommendation recommend(Incident incident) {
        // Factores:
        // - Skills del equipo
        // - Carga de trabajo actual
        // - Performance histórico
        // - Disponibilidad
        // - Similitud con casos previos resueltos
        
        return AssignmentRecommendation.builder()
            .assignedTo(user)
            .assignedGroup(group)
            .confidence(0.87)
            .reasoning("Top resolver para esta categoría")
            .build();
    }
}
```

---

### 5. Generación Automática de Respuestas

**Objetivo**: Sugerencias de respuesta para analistas.

```
Input: Incident + Latest Comment
↓
RAG (buscar casos similares resueltos)
↓
LLM (generar respuesta personalizada)
↓
Output: Respuesta sugerida + KB articles
```

**Tasks**:
- [ ] `SUGGEST_RESPONSE` intent
- [ ] Template system para respuestas comunes
- [ ] RAG sobre resoluciones exitosas
- [ ] UI: botón "Generar respuesta sugerida"
- [ ] Feedback loop (thumbs up/down)

---

### 6. Métricas y Dashboards

**Objetivo**: Observabilidad completa del sistema.

**Métricas a Implementar**:

```java
// Performance
- agent.request.duration (histogram)
- agent.llm.latency (histogram)
- agent.servicenow.latency (histogram)

// Business
- agent.intent.distribution (counter)
- agent.incident.created.total (counter)
- agent.incident.resolved.auto (counter)
- agent.conversation.turns.avg (gauge)

// Quality
- agent.classification.accuracy (gauge)
- agent.rag.relevance.score (histogram)
- agent.user.satisfaction (gauge)

// Cost
- agent.llm.tokens.total (counter)
- agent.llm.cost.usd (counter)
```

**Dashboard Grafana/Prometheus**:
- Panel de latencias P50/P90/P99
- Distribución de intenciones
- Tasa de éxito por intent
- Costos de inferencia por día/semana
- Tickets creados/resueltos automáticamente

---

## 🤖 Fase 2: Agentes Autónomos (3-6 meses)

### Objetivos Estratégicos

1. **Autonomía**: Agentes que ejecutan tareas sin supervisión constante
2. **Planificación**: Capacidad de descomponer objetivos complejos
3. **Tool Use**: Interacción con múltiples sistemas
4. **Human-in-the-Loop**: Aprobación para acciones críticas
5. **Multi-Agent**: Colaboración entre agentes especializados

---

### Arquitectura Multi-Agente

```
┌─────────────────────────────────────────────┐
│         Agent Orchestrator Layer            │
│                                             │
│  • Task Planning                            │
│  • Agent Selection                          │
│  • Workflow Coordination                    │
│  • Human Approval Management                │
└──────────────┬──────────────────────────────┘
               │
     ┌─────────┼─────────┬─────────┬──────────┐
     ▼         ▼         ▼         ▼          ▼
┌─────────┐ ┌──────┐ ┌────────┐ ┌──────┐ ┌────────┐
│Triage   │ │L1    │ │L2      │ │Change│ │Monitor │
│Agent    │ │Agent │ │Specialist│Agent │ │Agent   │
│         │ │      │ │        │ │      │ │        │
│•Classify│ │•Basic│ │•Advanced││•RFC  │ │•Alert  │
│•Route   │ │fixes │ │diagnosis││•Plan │ │•Predict│
│•Priorit.│ │•KB   │ │•RCA    ││•Execute│•Prevent│
└─────────┘ └──────┘ └────────┘ └──────┘ └────────┘
     │         │         │         │          │
     └─────────┴─────────┴─────────┴──────────┘
                      │
            ┌─────────┴─────────┐
            ▼                   ▼
     ┌─────────────┐     ┌──────────────┐
     │ Tool Registry│     │ Memory Store │
     │              │     │              │
     │• ServiceNow  │     │• Long-term   │
     │• Jira        │     │• Episodic    │
     │• Slack       │     │• Semantic    │
     │• Scripts     │     │              │
     └─────────────┘     └──────────────┘
```

---

### 2.1. Tool Calling Framework

**Objetivo**: Permitir a los agentes ejecutar acciones en sistemas externos.

```java
@Component
public interface AgentTool {
    String getName();
    String getDescription();
    JsonNode getParameterSchema();
    ToolResult execute(Map<String, Object> parameters, UserContext context);
    boolean requiresApproval();
}

// Ejemplo: RestartServerTool
@Component
public class RestartServerTool implements AgentTool {
    
    @Override
    public String getName() {
        return "restart_server";
    }
    
    @Override
    public String getDescription() {
        return "Reinicia un servidor específico. Requiere aprobación.";
    }
    
    @Override
    public boolean requiresApproval() {
        return true; // Human-in-the-loop
    }
    
    @Override
    public ToolResult execute(Map<String, Object> params, UserContext context) {
        String serverId = (String) params.get("server_id");
        
        // Validaciones de seguridad
        if (!hasPermission(context, serverId)) {
            return ToolResult.error("No tienes permisos");
        }
        
        // Ejecutar via API/SSH
        boolean success = serverApi.restart(serverId);
        
        return ToolResult.success("Servidor reiniciado", metadata);
    }
}
```

**Tool Registry**:
```java
@Service
public class ToolRegistry {
    private final Map<String, AgentTool> tools = new ConcurrentHashMap<>();
    
    public void registerTool(AgentTool tool) {
        tools.put(tool.getName(), tool);
    }
    
    public List<ToolDescriptor> getAvailableTools(UserContext context) {
        // Filtrar por permisos del usuario
        return tools.values().stream()
            .filter(tool -> tool.hasAccess(context))
            .map(ToolDescriptor::from)
            .toList();
    }
    
    public ToolResult executeTool(String toolName, Map<String, Object> params, 
                                  UserContext context) {
        AgentTool tool = tools.get(toolName);
        
        if (tool.requiresApproval()) {
            // Solicitar aprobación humana
            return requestApproval(tool, params, context);
        }
        
        return tool.execute(params, context);
    }
}
```

---

### 2.2. Planning & Reasoning

**Objetivo**: Agentes capaces de planificar secuencias de acciones.

```java
@Service
public class AgentPlanner {
    
    public Plan createPlan(Goal goal, AgentContext context) {
        // Usar LLM para descomponer objetivo
        String planningPrompt = """
            Objetivo: %s
            
            Herramientas disponibles: %s
            
            Estado actual del sistema: %s
            
            Genera un plan paso a paso para lograr el objetivo.
            Formato:
            {
              "steps": [
                {
                  "action": "tool_name",
                  "parameters": {...},
                  "reasoning": "por qué este paso"
                }
              ],
              "estimated_duration": "5 minutes",
              "risk_level": "low|medium|high"
            }
            """.formatted(goal, availableTools, systemState);
        
        String llmResponse = llmService.generate(planningPrompt, 0.2, 800);
        Plan plan = parsePlan(llmResponse);
        
        // Validar plan
        ValidationResult validation = validatePlan(plan, context);
        
        if (!validation.isValid()) {
            return replanWithFeedback(goal, validation.getIssues());
        }
        
        return plan;
    }
    
    public ExecutionResult executePlan(Plan plan, UserContext userContext) {
        for (Step step : plan.getSteps()) {
            // Verificar pre-condiciones
            if (!checkPreconditions(step)) {
                return ExecutionResult.failed("Precondición falló en: " + step);
            }
            
            // Ejecutar step
            ToolResult result = toolRegistry.executeTool(
                step.getAction(), 
                step.getParameters(), 
                userContext
            );
            
            if (!result.isSuccess()) {
                // Intentar recovery
                return handleFailure(step, result, plan);
            }
            
            // Actualizar estado
            context.updateState(result);
        }
        
        return ExecutionResult.success();
    }
}
```

---

### 2.3. Human-in-the-Loop

**Objetivo**: Aprobación humana para acciones críticas.

```java
@Service
public class ApprovalService {
    
    public CompletableFuture<ApprovalDecision> requestApproval(
        ApprovalRequest request
    ) {
        // 1. Persistir en DB
        Approval approval = approvalRepository.save(
            Approval.builder()
                .agentId(request.getAgentId())
                .action(request.getAction())
                .parameters(request.getParameters())
                .reasoning(request.getReasoning())
                .riskLevel(request.getRiskLevel())
                .status(ApprovalStatus.PENDING)
                .requestedAt(Instant.now())
                .expiresAt(Instant.now().plus(15, ChronoUnit.MINUTES))
                .build()
        );
        
        // 2. Notificar a aprobadores (Slack/Email/Portal)
        notificationService.notifyApprovers(approval);
        
        // 3. Retornar Future que se completa cuando hay decisión
        return approvalFuture(approval.getId());
    }
    
    @PostMapping("/api/approvals/{id}/decide")
    public ResponseEntity<?> decide(
        @PathVariable String id,
        @RequestBody ApprovalDecision decision,
        @AuthenticationPrincipal UserContext user
    ) {
        Approval approval = approvalRepository.findById(id)
            .orElseThrow();
        
        // Verificar que el usuario tiene permisos
        if (!canApprove(user, approval)) {
            return ResponseEntity.status(403).build();
        }
        
        approval.setStatus(decision.isApproved() 
            ? ApprovalStatus.APPROVED 
            : ApprovalStatus.
