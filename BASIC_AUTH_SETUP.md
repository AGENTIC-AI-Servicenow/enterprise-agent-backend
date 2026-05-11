# Basic Authentication Setup Guide

## 📋 Overview

Esta guía te ayudará a configurar Basic Authentication para probar el sistema rápidamente sin necesidad de configurar OAuth.

**Cuándo usar Basic Auth:**
- ✅ Desarrollo y testing local
- ✅ Validación rápida de funcionalidad
- ✅ Ambientes de sandbox/dev

**Cuándo NO usar Basic Auth:**
- ❌ Producción
- ❌ Ambientes con múltiples usuarios
- ❌ Requisitos de seguridad enterprise

---

## 🔧 Configuración Paso a Paso

### 1. Obtener Credenciales de ServiceNow

Necesitas:
- **Username**: Tu usuario de ServiceNow (ej: `admin`)
- **Password**: Tu contraseña de ServiceNow

**Instancia de ServiceNow:**
```
https://everisspainsludemo3.service-now.com
```

### 2. Configurar Variables de Entorno

#### Windows (PowerShell)
```powershell
# Navega al directorio del proyecto
cd "e:\Proyectos\2026\NTT DATA\AI\enterprise-agent-backend"

# Establece las variables de entorno
$env:SERVICENOW_USERNAME="tu_usuario"
$env:SERVICENOW_PASSWORD="tu_contraseña"

# Opcional: Verifica que se establecieron correctamente
echo $env:SERVICENOW_USERNAME
```

#### Windows (CMD)
```cmd
set SERVICENOW_USERNAME=tu_usuario
set SERVICENOW_PASSWORD=tu_contraseña
```

#### Linux/Mac
```bash
export SERVICENOW_USERNAME="tu_usuario"
export SERVICENOW_PASSWORD="tu_contraseña"
```

### 3. Actualizar application.yml

El archivo ya está configurado para Basic Auth por defecto:

```yaml
servicenow:
  auth:
    mode: basic  # ✅ Modo configurado correctamente
    username: ${SERVICENOW_USERNAME:}
    password: ${SERVICENOW_PASSWORD:}
```

**NO necesitas modificar nada** si las variables de entorno están configuradas.

### 4. Iniciar la Aplicación

```bash
# Asegúrate de que Ollama está corriendo
ollama serve

# En otra terminal, inicia Spring Boot
mvnw spring-boot:run
```

### 5. Verificar Configuración

Una vez iniciada la aplicación, verifica que la autenticación está configurada:

```bash
curl http://localhost:8080/debug/servicenow/auth
```

**Respuesta esperada:**
```json
{
  "checkedAt": "2026-05-11T18:20:00Z",
  "authMode": "basic",
  "status": "READY",
  "authReady": true,
  "message": "Basic Auth configured and ready"
}
```

Si ves `"status": "NOT_READY"`, verifica que las variables de entorno estén configuradas correctamente.

---

## 🧪 Testing con Basic Auth

### Test 1: Verificar Conectividad con ServiceNow

```bash
curl -X GET http://localhost:8080/api/incidents/list
```

**Respuesta esperada:** Lista de incidentes de ServiceNow

### Test 2: Crear un Incidente

```bash
curl -X POST http://localhost:8080/api/incidents \
  -H "Content-Type: application/json" \
  -d '{
    "short_description": "Test incident from Basic Auth",
    "description": "Testing Basic Auth integration",
    "urgency": "3",
    "impact": "3"
  }'
```

**Respuesta esperada:** Incidente creado con `sys_id`

### Test 3: Interactuar con el Agente

```bash
curl -X POST http://localhost:8080/api/agent/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Show me the latest incidents",
    "conversationId": "test-conversation"
  }'
```

---

## 🚨 Troubleshooting

### Error: "Authentication not ready"

**Problema:** Las variables de entorno no están configuradas

**Solución:**
1. Verifica que configuraste las variables:
   ```powershell
   echo $env:SERVICENOW_USERNAME
   echo $env:SERVICENOW_PASSWORD
   ```
2. Si están vacías, configúralas nuevamente
3. Reinicia la aplicación Spring Boot

### Error: "401 Unauthorized"

**Problema:** Credenciales incorrectas

**Solución:**
1. Verifica tu usuario y contraseña en ServiceNow
2. Intenta loguearte manualmente en: https://everisspainsludemo3.service-now.com
3. Si funciona manualmente, verifica que las variables coincidan exactamente

### Error: "Connection refused"

**Problema:** Ollama no está corriendo

**Solución:**
```bash
ollama serve
```

### Las variables se pierden al cerrar la terminal

**Problema:** Las variables de entorno son temporales

**Solución para PowerShell (persistente para la sesión):**
```powershell
# Crear un archivo .env.ps1
$env:SERVICENOW_USERNAME="tu_usuario"
$env:SERVICENOW_PASSWORD="tu_contraseña"

# Ejecutar antes de iniciar la app
. .\.env.ps1
```

---

## 🔄 Cambiar de Basic Auth a OAuth

Cuando estés listo para usar OAuth:

### 1. Actualizar application.yml

```yaml
servicenow:
  auth:
    mode: oauth  # Cambiar a oauth
```

### 2. Configurar OAuth (ver OAUTH_MIGRATION_SUMMARY.md)

Sigue la guía en `OAUTH_MIGRATION_SUMMARY.md` para:
- Configurar Client ID y Client Secret
- Obtener Authorization Code
- Autenticar usuarios

### 3. Reiniciar la Aplicación

```bash
# Detén la aplicación actual (Ctrl+C)
# Inicia nuevamente
mvnw spring-boot:run
```

---

## 📊 Comparación: Basic Auth vs OAuth

| Característica | Basic Auth | OAuth |
|---------------|------------|-------|
| Configuración | ⚡ Inmediata | ⏱️ Requiere setup |
| Seguridad | ⚠️ Baja (credenciales en claro) | ✅ Alta (tokens temporales) |
| Multi-usuario | ❌ No soportado | ✅ Soportado |
| Producción | ❌ No recomendado | ✅ Recomendado |
| Auditoría | ⚠️ Limitada | ✅ Completa |
| Revocación | ❌ No disponible | ✅ Token revocable |

---

## 🎯 Próximos Pasos

Una vez que hayas validado que Basic Auth funciona:

1. ✅ **Prueba todos los endpoints** - Asegúrate que la integración funciona
2. 📝 **Documenta cualquier issue** - Anota problemas encontrados
3. 🔐 **Migra a OAuth** - Cuando estés listo para producción
4. 🧪 **Implementa tests automatizados** - Ver `COMPREHENSIVE_TESTING_GUIDE.md`

---

## 📚 Recursos Adicionales

- **Testing Guide:** `COMPREHENSIVE_TESTING_GUIDE.md`
- **OAuth Migration:** `OAUTH_MIGRATION_SUMMARY.md`
- **Postman Collection:** `POSTMAN_OAUTH_SERVICE_NOW_FLOW.md`
- **Implementation Guide:** `ENTERPRISE_IMPLEMENTATION_ROADMAP.md`

---

## ⚠️ Consideraciones de Seguridad

**IMPORTANTE: Basic Auth NO debe usarse en producción**

Riesgos:
- Credenciales transmitidas en cada request
- No hay expiración de credenciales
- No hay control granular de permisos
- Difícil auditoría de acciones por usuario
- Mayor superficie de ataque

**Para producción, SIEMPRE usa OAuth 2.0**
