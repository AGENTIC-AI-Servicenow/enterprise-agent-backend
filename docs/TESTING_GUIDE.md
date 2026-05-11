# Guía de Testing con Postman - Enterprise Agent Backend

## 📋 Pre-requisitos

### 1. Configuración Actual
```yaml
ServiceNow Instance: everisspainsludemo3.service-now.com
OAuth Client ID: 7d069f603a114972b00d6b7d89d66de6
Backend URL: http://localhost:8080
Ollama Model: phi3-manual:latest
```

### 2. Servicios Requeridos

**Verificar que estén corriendo**:

```bash
# 1. Verificar Ollama
curl http://localhost:11434/api/tags

# Debe retornar lista de modelos incluyendo phi3-manual:latest

# 2. Iniciar backend
cd e:\Proyectos\2026\NTT DATA\AI\enterprise-agent-backend
mvn spring-boot:run

# Debe iniciar en puerto 8080
```

---

## 🔐 Paso 1: Obtener Token de ServiceNow

### Opción A: Via Browser (Recomendado para primer test)

1. **Abrir en navegador**:
```
https://everisspainsludemo3.service-now.com/oauth_auth.do?response_type=code&client_id=7d069f603a114972b00d6b7d89d66de6&redirect_uri=http://localhost:8080/oauth/callback&state=test123
```

2. **Iniciar sesión** con tus credenciales de ServiceNow

3. **Autorizar** la aplicación

4. **Copiar el authorization code** de la URL de redirección:
```
http://localhost:8080/oauth/callback?code=XXXXXXX&state=test123
                                            ↑
                                    Copiar este código
```

5. **Intercambiar por token** (usar Postman o cURL):

**POST** `http://localhost:8080/oauth/token`

Body (form-data):
```
code: [EL_CODIGO_COPIADO]
```

**Respuesta esperada**:
```json
{
  "access_token": "eyJ0eXAiOiJKV1QiLCJhbGc...",
  "token_type": "Bearer",
  "expires_in": 1800,
  "refresh_token": "abc123..."
}
```

6. **Guardar el access_token** - lo usarás en todas las requests siguientes

---

### Opción B: Via Backend Endpoint (Simplificado)

Si ya tienes credenciales válidas, usa el endpoint de test:

**GET** `http://localhost:8080/api/oauth/test-connection`

Esto te retornará el estado de la conexión OAuth.

---

## 📮 Paso 2: Configurar Postman

### Crear Collection

1. **New Collection** → "Enterprise Agent API"
2. **Variables** (a nivel de collection):

```
base_url: http://localhost:8080
servicenow_token: [TU_ACCESS_TOKEN]
user_id: test_user_001
session_id: {{$guid}}
```

### Authorization (Collection level)

- **Type**: Bearer Token
- **Token**: `{{servicenow_token}}`

---

## 🧪 Paso 3: Casos de Uso - Testing

### Test 1: Consultar Incidente Específico

**Intent**: `GET_INCIDENT`

**POST** `{{base_url}}/api/agent/chat`

**Headers**:
```
Content-Type: application/json
Authorization: Bearer {{servicenow_token}}
```

**Body**:
```json
{
  "message": "Muéstrame el incidente INC0010001",
  "userId": "{{user_id}}",
  "sessionId": "{{session_id}}"
}
```

**Respuesta Esperada**:
```json
{
  "response": "Aquí está la información del incidente INC0010001:\n\n**Número**: INC0010001\n**Estado**: In Progress\n**Prioridad**: 2 - High\n**Descripción**: Cannot access email...",
  "intent": "GET_INCIDENT",
  "confidence": 0.95,
  "metadata": {
    "incident_number": "INC0010001",
    "state": "2",
    "priority": "2"
  }
}
```

**Validaciones**:
- ✅ Status code: 200
- ✅ Intent clasificado correctamente
- ✅ Datos del incidente extraídos de ServiceNow
- ✅ Respuesta en lenguaje natural

---

### Test 2: Buscar Incidentes con Filtros

**Intent**: `SEARCH_INCIDENTS`

**POST** `{{base_url}}/api/agent/chat`

**Body**:
```json
{
  "message": "Busca todos los incidentes de alta prioridad que están abiertos",
  "userId": "{{user_id}}",
  "sessionId": "{{session_id}}"
}
```

**Respuesta Esperada**:
```json
{
  "response": "Encontré 5 incidentes de alta prioridad abiertos:\n\n1. **INC0010002** - Cannot connect to VPN (Priority: 1)\n2. **INC0010005** - Database down (Priority: 1)\n...",
  "intent": "SEARCH_INCIDENTS",
  "confidence": 0.92,
  "metadata": {
    "total_count": 5,
    "filters": {
      "priority": "high",
      "state": "open"
    }
  }
}
```

---

### Test 3: Análisis Inteligente de Incidente

**Intent**: `ANALYZE_INCIDENT`

**POST** `{{base_url}}/api/agent/chat`

**Body**:
```json
{
  "message": "Analiza el incidente INC0010001 y dame recomendaciones",
  "userId": "{{user_id}}",
  "sessionId": "{{session_id}}"
}
```

**Respuesta Esperada**:
```json
{
  "response": "**Análisis del incidente INC0010001:**\n\n**Resumen**: Usuario no puede acceder a email...\n\n**Patrón Identificado**: Problema recurrente con credenciales...\n\n**Recomendaciones**:\n1. Verificar que el usuario tenga acceso activo\n2. Resetear password de email\n3. Comprobar estado del servidor de correo...",
  "intent": "ANALYZE_INCIDENT",
  "confidence": 0.89,
  "metadata": {
    "incident_number": "INC0010001",
    "analysis_performed": true,
    "recommendations_count": 3
  }
}
```

---

### Test 4: Crear Nuevo Incidente

**Intent**: `CREATE_INCIDENT`

**POST** `{{base_url}}/api/agent/chat`

**Body**:
```json
{
  "message": "Necesito reportar un problema: la impresora del piso 3 no está funcionando. Es urgente porque necesitamos imprimir documentos para una reunión.",
  "userId": "{{user_id}}",
  "sessionId": "{{session_id}}"
}
```

**Respuesta Esperada**:
```json
{
  "response": "He creado el incidente **INC0010025** con la siguiente información:\n\n**Descripción**: Impresora del piso 3 no está funcionando\n**Prioridad**: 2 - High (por urgencia mencionada)\n**Categoría**: Hardware\n**Estado**: New\n\nEl equipo de soporte ha sido notificado.",
  "intent": "CREATE_INCIDENT",
  "confidence": 0.91,
  "metadata": {
    "created_incident_number": "INC0010025",
    "priority": "2",
    "category": "hardware"
  }
}
```

---

### Test 5: Conversación Multi-Turno

**Turno 1**: Iniciar conversación

**POST** `{{base_url}}/api/agent/chat`

```json
{
  "message": "Muéstrame mis incidentes abiertos",
  "userId": "{{user_id}}",
  "sessionId": "session_multiturno_001"
}
```

**Respuesta**:
```json
{
  "response": "Tienes 3 incidentes abiertos:\n1. INC0010001 - Cannot access email\n2. INC0010003 - Laptop running slow\n3. INC0010007 - VPN connection issues",
  "intent": "LIST_MY_INCIDENTS"
}
```

---

**Turno 2**: Hacer referencia al contexto anterior

**POST** `{{base_url}}/api/agent/chat`

```json
{
  "message": "Dame más detalles del primero",
  "userId": "{{user_id}}",
  "sessionId": "session_multiturno_001"
}
```

**Respuesta Esperada**:
```json
{
  "response": "Detalles del incidente INC0010001:\n\n**Descripción completa**: Cannot access email since this morning...\n**Creado**: 2026-05-10 08:30:00\n**Asignado a**: John Smith\n**Última actualización**: Hace 2 horas...",
  "intent": "GET_INCIDENT",
  "metadata": {
    "incident_number": "INC0010001",
    "context_aware": true
  }
}
```

**Validación**: El agente debe recordar que "el primero" se refiere a INC0010001.

---

### Test 6: Intent Ambiguo / Conversación General

**POST** `{{base_url}}/api/agent/chat`

```json
{
  "message": "Hola, ¿cómo estás?",
  "userId": "{{user_id}}",
  "sessionId": "{{session_id}}"
}
```

**Respuesta Esperada**:
```json
{
  "response": "Hola! Estoy aquí para ayudarte con tus tickets e incidentes de ServiceNow. Puedo ayudarte a:\n\n- Consultar el estado de incidentes\n- Buscar tickets\n- Crear nuevos incidentes\n- Analizar problemas\n- Ver tus tickets pendientes\n\n¿En qué puedo asistirte hoy?",
  "intent": "CHAT",
  "confidence": 0.78
}
```

---

### Test 7: Resumen de Múltiples Incidentes

**Intent**: `SUMMARIZE_INCIDENTS`

**POST** `{{base_url}}/api/agent/chat`

```json
{
  "message": "Dame un resumen de todos los incidentes de red de esta semana",
  "userId": "{{user_id}}",
  "sessionId": "{{session_id}}"
}
```

**Respuesta Esperada**:
```json
{
  "response": "**Resumen de incidentes de red (última semana)**:\n\n**Total**: 12 incidentes\n\n**Desglose por estado**:\n- Resueltos: 8 (67%)\n- En progreso: 3 (25%)\n- Nuevos: 1 (8%)\n\n**Tendencias**:\n- Pico de incidentes el martes (5 tickets)\n- Causa principal: Problemas con VPN (6 casos)\n- Tiempo promedio de resolución: 4.5 horas",
  "intent": "SUMMARIZE_INCIDENTS",
  "confidence": 0.88
}
```

---

## 🔍 Paso 4: Validaciones de Calidad

### Checklist de Testing

Para cada test, verificar:

#### ✅ Funcionalidad
- [ ] Status code 200
- [ ] Intent clasificado correctamente
- [ ] Parámetros extraídos correctamente
- [ ] Datos obtenidos de ServiceNow son correctos
- [ ] Respuesta en lenguaje natural fluido

#### ✅ Performance
- [ ] Latencia total <5s
- [ ] Latencia LLM <2s
- [ ] Latencia ServiceNow <1s

#### ✅ Seguridad
- [ ] Token OAuth válido requerido
- [ ] UserID se propaga correctamente
- [ ] No hay leaks de información entre sesiones

#### ✅ Memoria Conversacional
- [ ] Contexto se mantiene entre turnos
- [ ] Referencias anafóricas funcionan ("el primero", "ese ticket")
- [ ] Sesión aislada por sessionId

---

## 🐛 Troubleshooting

### Problema 1: "Unauthorized" (401)

**Causa**: Token OAuth expirado o inválido

**Solución**:
```bash
# Obtener nuevo token
# Paso 1: Authorization code via browser
https://everisspainsludemo3.service-now.com/oauth_auth.do?response_type=code&client_id=7d069f603a114972b00d6b7d89d66de6&redirect_uri=http://localhost:8080/oauth/callback&state=test123

# Paso 2: Intercambiar por access token
POST http://localhost:8080/oauth/token
Body: code=NUEVO_CODIGO
```

---

### Problema 2: "Ollama connection refused"

**Causa**: Ollama no está corriendo

**Solución**:
```bash
# Windows
ollama serve

# Verificar
curl http://localhost:11434/api/tags

# Debe listar phi3-manual:latest
```

---

### Problema 3: Intent mal clasificado

**Ejemplo**: 
- Input: "Muéstrame INC0010001"
- Intent clasificado: CHAT (incorrecto, debería ser GET_INCIDENT)

**Diagnóstico**:
```bash
# Revisar logs
# Buscar línea: "LLM Response for intent classification:"
```

**Posibles causas**:
1. Token expirado → Renovar
2. Incidente no existe → Verificar número
3. Permisos insuficientes → Verificar roles en ServiceNow

---

### Problema 5: Latencia muy alta (>10s)

**Diagnóstico**:
- Revisar logs para identificar bottleneck
- Buscar líneas con tiempos de ejecución

**Optimizaciones**:
```yaml
# En application.yml, ajustar timeouts
spring:
  webflux:
    timeout: 10000  # 10s
```

---

## 📊 Paso 5: Métricas de Testing

### Planilla de Resultados

Documenta los resultados de cada test:

| Test Case | Intent Esperado | Intent Obtenido | Latencia (s) | Precisión | Estado |
|-----------|-----------------|-----------------|--------------|-----------|--------|
| GET_INCIDENT | GET_INCIDENT | GET_INCIDENT | 2.3 | ✅ 100% | PASS |
| SEARCH_INCIDENTS | SEARCH_INCIDENTS | SEARCH_INCIDENTS | 3.1 | ✅ 100% | PASS |
| ANALYZE_INCIDENT | ANALYZE_INCIDENT | ANALYZE_INCIDENT | 4.2 | ⚠️ 85% | PASS |
| CREATE_INCIDENT | CREATE_INCIDENT | CREATE_INCIDENT | 2.8 | ✅ 95% | PASS |
| Multi-turno | GET_INCIDENT | CHAT | 2.1 | ❌ 0% | FAIL |

### Métricas Objetivo

**Fase MVP**:
- ✅ Intent accuracy: >85%
- ✅ P95 latency: <5s
- ✅ Success rate: >90%
- ✅ Context retention: >3 turnos

---

## 🚀 Quick Start - Primera Ejecución

### Checklist Rápido

```bash
# 1. ✅ Ollama corriendo
ollama serve
ollama pull phi3-manual:latest

# 2. ✅ Backend corriendo
cd e:\Proyectos\2026\NTT DATA\AI\enterprise-agent-backend
mvn spring-boot:run

# 3. ✅ Obtener token OAuth (via browser)
# Abrir: https://everisspainsludemo3.service-now.com/oauth_auth.do?response_type=code&client_id=7d069f603a114972b00d6b7d89d66de6&redirect_uri=http://localhost:8080/oauth/callback&state=test123

# 4. ✅ Intercambiar código por token
# POST http://localhost:8080/oauth/token
# Body: code=CODIGO_OBTENIDO

# 5. ✅ Test básico con cURL
curl -X POST http://localhost:8080/api/agent/chat \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer TU_ACCESS_TOKEN" \
  -d '{
    "message": "Muéstrame el incidente INC0010001",
    "userId": "test_user",
    "sessionId": "test_session_001"
  }'

# 6. ✅ Verificar respuesta JSON
```

### Primera Request de Prueba

**Objetivo**: Validar que todo funciona end-to-end

```bash
curl -X POST http://localhost:8080/api/agent/chat \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN_HERE" \
  -d '{
    "message": "Hola",
    "userId": "test_user_001",
    "sessionId": "quickstart_001"
  }'
```

**Respuesta esperada** (intent: CHAT):
```json
{
  "response": "Hola! Estoy aquí para ayudarte con tus tickets...",
  "intent": "CHAT",
  "confidence": 0.87,
  "metadata": {}
}
```

---

## 📦 Postman Collection (Importable)

Crea un archivo `postman_collection.json` con:

```json
{
  "info": {
    "name": "Enterprise Agent API",
    "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
  },
  "variable": [
    {
      "key": "base_url",
      "value": "http://localhost:8080",
      "type": "string"
    },
    {
      "key": "servicenow_token",
      "value": "YOUR_TOKEN_HERE",
      "type": "string"
    },
    {
      "key": "user_id",
      "value": "test_user_001",
      "type": "string"
    }
  ],
  "item": [
    {
      "name": "OAuth - Get Authorization Code",
      "request": {
        "method": "GET",
        "header": [],
        "url": {
          "raw": "https://everisspainsludemo3.service-now.com/oauth_auth.do?response_type=code&client_id=7d069f603a114972b00d6b7d89d66de6&redirect_uri=http://localhost:8080/oauth/callback&state=test123",
          "protocol": "https",
          "host": ["everisspainsludemo3", "service-now", "com"],
          "path": ["oauth_auth.do"],
          "query": [
            {"key": "response_type", "value": "code"},
            {"key": "client_id", "value": "7d069f603a114972b00d6b7d89d66de6"},
            {"key": "redirect_uri", "value": "http://localhost:8080/oauth/callback"},
            {"key": "state", "value": "test123"}
          ]
        }
      }
    },
    {
      "name": "OAuth - Exchange Code for Token",
      "request": {
        "method": "POST",
        "header": [],
        "body": {
          "mode": "urlencoded",
          "urlencoded": [
            {"key": "code", "value": "PASTE_CODE_HERE", "type": "text"}
          ]
        },
        "url": {
          "raw": "{{base_url}}/oauth/token",
          "host": ["{{base_url}}"],
          "path": ["oauth", "token"]
        }
      }
    },
    {
      "name": "Test 1 - GET_INCIDENT",
      "request": {
        "method": "POST",
        "header": [
          {"key": "Content-Type", "value": "application/json"},
          {"key": "Authorization", "value": "Bearer {{servicenow_token}}"}
        ],
        "body": {
          "mode": "raw",
          "raw": "{\n  \"message\": \"Muéstrame el incidente INC0010001\",\n  \"userId\": \"{{user_id}}\",\n  \"sessionId\": \"{{$guid}}\"\n}"
        },
        "url": {
          "raw": "{{base_url}}/api/agent/chat",
          "host": ["{{base_url}}"],
          "path": ["api", "agent", "chat"]
        }
      }
    },
    {
      "name": "Test 2 - SEARCH_INCIDENTS",
      "request": {
        "method": "POST",
        "header": [
          {"key": "Content-Type", "value": "application/json"},
          {"key": "Authorization", "value": "Bearer {{servicenow_token}}"}
        ],
        "body": {
          "mode": "raw",
          "raw": "{\n  \"message\": \"Busca incidentes de alta prioridad abiertos\",\n  \"userId\": \"{{user_id}}\",\n  \"sessionId\": \"{{$guid}}\"\n}"
        },
        "url": {
          "raw": "{{base_url}}/api/agent/chat",
          "host": ["{{base_url}}"],
          "path": ["api", "agent", "chat"]
        }
      }
    },
    {
      "name": "Test 3 - ANALYZE_INCIDENT",
      "request": {
        "method": "POST",
        "header": [
          {"key": "Content-Type", "value": "application/json"},
          {"key": "Authorization", "value": "Bearer {{servicenow_token}}"}
        ],
        "body": {
          "mode": "raw",
          "raw": "{\n  \"message\": \"Analiza el incidente INC0010001 y dame recomendaciones\",\n  \"userId\": \"{{user_id}}\",\n  \"sessionId\": \"{{$guid}}\"\n}"
        },
        "url": {
          "raw": "{{base_url}}/api/agent/chat",
          "host": ["{{base_url}}"],
          "path": ["api", "agent", "chat"]
        }
      }
    },
    {
      "name": "Test 4 - CREATE_INCIDENT",
      "request": {
        "method": "POST",
        "header": [
          {"key": "Content-Type", "value": "application/json"},
          {"key": "Authorization", "value": "Bearer {{servicenow_token}}"}
        ],
        "body": {
          "mode": "raw",
          "raw": "{\n  \"message\": \"Necesito reportar un problema: la impresora del piso 3 no funciona. Es urgente.\",\n  \"userId\": \"{{user_id}}\",\n  \"sessionId\": \"{{$guid}}\"\n}"
        },
        "url": {
          "raw": "{{base_url}}/api/agent/chat",
          "host": ["{{base_url}}"],
          "path": ["api", "agent", "chat"]
        }
      }
    }
  ]
}
```

**Para importar**:
1. Abrir Postman
2. Import → Upload file → seleccionar `postman_collection.json`
3. Editar variable `servicenow_token` con tu token real

---

## ✅ Criterios de Éxito del Testing

### Fase 1: Validación Funcional (Semana 1)

- [ ] 7/7 casos de uso funcionan correctamente
- [ ] Intent accuracy >85%
- [ ] P95 latency <5s
- [ ] 0 errores críticos
- [ ] OAuth funciona establemente

### Fase 2: Validación con Usuarios (Semana 2)

- [ ] 3-5 usuarios beta han probado el sistema
- [ ] User satisfaction score >4/5
- [ ] Identificados 3 casos de uso de mayor valor
- [ ] Feedback documentado
- [ ] Métricas de baseline capturadas

### Fase 3: Pre-Producción

- [ ] 100+ requests ejecutadas sin fallos críticos
- [ ] No memory leaks detectados
- [ ] Logs de auditoría completos
- [ ] Security testing passed
- [ ] Performance testing passed

---

## 📈 Próximos Pasos Post-Testing

Una vez completado el testing exitosamente:

1. **Documentar resultados** en reporte ejecutivo
2. **Presentar a stakeholders** con demos en vivo
3. **Priorizar mejoras** basadas en feedback
4. **Planificar Fase 1.5** (RAG, duplicados, auto-priorización)
5. **Setup de ambiente de staging** para usuarios beta extendidos

---

## 🎯 Contacto y Soporte

Para issues durante testing:
- Revisar logs en consola del backend
- Verificar logs de Ollama
- Consultar documentación en `/docs`
- Ejecutar en modo DEBUG para más detalles

**Happy Testing!** 🚀
1. **Prompt ambiguo** → Mejorar ejemplos en IntentClassifier
2. **Modelo muy pequeño** → Considerar usar llama3.2 en vez de phi3
3. **Temperature muy alta** → Reducir a 0.1

**Solución temporal**:
```java
// En IntentClassifier.java, ajustar temperature
double temperature = 0.1; // Más determinista
```

---

### Problema 4: ServiceNow no retorna datos

**Error**: `"No se pudo obtener información del incidente"`

**Verificar**:
```bash
# Test directo a ServiceNow API
curl -X GET \
  "https://everisspainsludemo3.service-now.com/api/now/table/incident?sysparm_query=number=INC0010001" \
  -H "Authorization: Bearer TU_TOKEN" \
  -H "Content-Type: application/json"
```

**Posibles causas**:
