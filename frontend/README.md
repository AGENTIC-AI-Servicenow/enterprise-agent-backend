# Enterprise AI Agent - Frontend

Frontend moderno para el sistema de agentes IA integrado con ServiceNow, construido con Next.js 14, TypeScript y Tailwind CSS.

## 🏗️ Arquitectura

- **Framework**: Next.js 14 (App Router)
- **Lenguaje**: TypeScript
- **Estilos**: Tailwind CSS + shadcn/ui
- **Estado**: React Query (TanStack Query)
- **Temas**: next-themes (Dark/Light mode)
- **API Client**: Fetch API con tipos TypeScript

## 📦 Estructura del Proyecto

```
frontend/
├── src/
│   ├── app/                      # App Router de Next.js
│   │   ├── layout.tsx           # Layout principal
│   │   ├── page.tsx             # Página home (redirect)
│   │   ├── globals.css          # Estilos globales
│   │   ├── dashboard/           # Dashboard principal
│   │   ├── incidents/           # Gestión de incidentes
│   │   ├── chat/                # Chat con AI Agent
│   │   └── auth/                # Flujo de autenticación
│   ├── components/              # Componentes React
│   │   ├── ui/                  # Componentes UI base (shadcn)
│   │   ├── layout/              # Layout components
│   │   ├── incidents/           # Componentes de incidentes
│   │   ├── chat/                # Componentes de chat
│   │   └── providers.tsx        # Context providers
│   ├── lib/                     # Utilidades y configuración
│   │   ├── api-client.ts        # Cliente API
│   │   └── utils.ts             # Funciones de utilidad
│   ├── hooks/                   # Custom React hooks
│   │   ├── use-incidents.ts
│   │   ├── use-agent.ts
│   │   └── use-auth.ts
│   └── types/                   # TypeScript types
│       └── index.ts             # Tipos del dominio
├── public/                      # Assets estáticos
├── .env.example                 # Variables de entorno ejemplo
├── tailwind.config.ts           # Configuración Tailwind
├── tsconfig.json                # Configuración TypeScript
└── package.json                 # Dependencias
```

## 🚀 Inicio Rápido

### Requisitos Previos

- Node.js 18+ y npm/yarn/pnpm
- Backend ejecutándose en `http://localhost:8080`
- Instancia de ServiceNow configurada

### Instalación

```bash
# Clonar el repositorio
cd frontend

# Instalar dependencias
npm install

# Copiar variables de entorno
cp .env.example .env.local

# Configurar variables en .env.local
# NEXT_PUBLIC_API_URL=http://localhost:8080
# NEXT_PUBLIC_OAUTH_CLIENT_ID=your_client_id
# NEXT_PUBLIC_SERVICENOW_INSTANCE=https://your-instance.service-now.com
```

### Desarrollo

```bash
# Modo desarrollo (hot reload)
npm run dev

# Acceder a http://localhost:3000
```

### Build y Producción

```bash
# Crear build optimizado
npm run build

# Ejecutar build de producción
npm start

# Linter y formato
npm run lint
npm run lint:fix
```

## 🔐 Autenticación

El sistema utiliza OAuth 2.0 para autenticación con ServiceNow:

1. Usuario hace clic en "Login with ServiceNow"
2. Redirección a ServiceNow para autorización
3. Callback con código de autorización
4. Frontend intercambia código por access token
5. Token se almacena en localStorage (MVP - mejorar en producción)
6. Todas las requests incluyen header Authorization

**⚠️ Nota de Seguridad MVP**: En producción, usar httpOnly cookies y refresh tokens.

## 📱 Páginas Principales

### Dashboard (`/dashboard`)
- Métricas en tiempo real
- Resumen de incidentes
- Acceso rápido a funcionalidades principales

### Incidents (`/incidents`)
- Lista de incidentes con filtros
- Búsqueda y ordenamiento
- Vista detallada de incidente
- Clasificación y priorización automática

### AI Chat (`/chat`)
- Interface de chat con agente IA
- Sugerencias contextuales
- Historial de conversaciones
- Acciones sugeridas

## 🎨 Componentes UI

Usamos **shadcn/ui** para componentes base:

```bash
# Instalar componentes según necesidad
npx shadcn-ui@latest add button
npx shadcn-ui@latest add card
npx shadcn-ui@latest add dialog
npx shadcn-ui@latest add input
npx shadcn-ui@latest add select
npx shadcn-ui@latest add badge
npx shadcn-ui@latest add table
```

## 🔌 Integración con Backend

### API Client

```typescript
import { agentApi, incidentsApi } from '@/lib/api-client';

// Usar en componentes con React Query
const { data, isLoading } = useQuery({
  queryKey: ['incidents'],
  queryFn: () => incidentsApi.getAll(),
});

// Chat con agente
const mutation = useMutation({
  mutationFn: (request: AgentRequest) => agentApi.chat(request),
});
```

### Endpoints Disponibles

- `POST /api/agent/chat` - Chat con AI agent
- `GET /api/incidents` - Listar incidentes
- `GET /api/incidents/{id}` - Detalle de incidente
- `GET /api/incidents/summary` - Resumen y métricas
- `POST /api/oauth/authorize` - Iniciar OAuth
- `POST /api/oauth/callback` - Callback OAuth

## 🌙 Dark Mode

El sistema incluye soporte para dark mode usando `next-themes`:

```typescript
import { useTheme } from 'next-themes';

function ThemeToggle() {
  const { theme, setTheme } = useTheme();
  
  return (
    <button onClick={() => setTheme(theme === 'dark' ? 'light' : 'dark')}>
      Toggle Theme
    </button>
  );
}
```

## 📊 State Management

### React Query (TanStack Query)

Gestión de estado del servidor:

```typescript
// Query para datos
const { data, isLoading, error } = useQuery({
  queryKey: ['incidents', filters],
  queryFn: () => incidentsApi.getAll(filters),
  staleTime: 60000, // 1 minuto
});

// Mutation para acciones
const mutation = useMutation({
  mutationFn: incidentsApi.update,
  onSuccess: () => {
    queryClient.invalidateQueries({ queryKey: ['incidents'] });
  },
});
```

## 🧪 Testing

```bash
# Unit tests (configurar Jest)
npm test

# E2E tests (configurar Playwright)
npm run test:e2e
```

## 🚀 Deployment

### Vercel (Recomendado)

```bash
# Deploy a Vercel
npx vercel

# Variables de entorno en Vercel dashboard
NEXT_PUBLIC_API_URL=https://api.your-domain.com
NEXT_PUBLIC_OAUTH_CLIENT_ID=...
NEXT_PUBLIC_SERVICENOW_INSTANCE=...
```

### Docker

```bash
# Build imagen
docker build -t enterprise-agent-frontend .

# Run container
docker run -p 3000:3000 \
  -e NEXT_PUBLIC_API_URL=http://backend:8080 \
  enterprise-agent-frontend
```

## 📈 Mejoras Futuras

### Fase 1 - MVP Actual
- [x] Estructura base Next.js 14
- [x] Tailwind CSS + tema enterprise
- [x] TypeScript types completos
- [x] API client con manejo de errores
- [ ] Componentes UI base (shadcn)
- [ ] Dashboard con métricas
- [ ] Lista de incidentes
- [ ] Chat con AI agent
- [ ] OAuth flow completo

### Fase 2 - Producción
- [ ] Refresh tokens y seguridad mejorada
- [ ] Server Components optimizados
- [ ] Streaming de respuestas del agente
- [ ] Optimistic updates
- [ ] Error boundaries robustos
- [ ] Analytics y observabilidad
- [ ] PWA support
- [ ] Internacionalización (i18n)

### Fase 3 - Features Avanzados
- [ ] Real-time updates (WebSockets)
- [ ] Notificaciones push
- [ ] Offline support
- [ ] Advanced search con Algolia
- [ ] Data visualization avanzada
- [ ] Workflow builder visual
- [ ] Multi-tenant support

## 🤝 Contribución

Este es un MVP interno. Seguir:
- TypeScript strict mode
- ESLint rules
- Conventional commits
- Feature branches + PR reviews

## 📝 Notas para Producción

**Seguridad**:
- Implementar CSP headers
- Usar httpOnly cookies para tokens
- Validación de input en frontend y backend
- Rate limiting en API calls
- CORS configurado correctamente

**Performance**:
- Code splitting automático (Next.js)
- Image optimization (next/image)
- Lazy loading de componentes
- React Query cache strategies
- CDN para assets estáticos

**Monitoreo**:
- Sentry para error tracking
- Analytics (Posthog/Mixpanel)
- Lighthouse CI en pipeline
- Core Web Vitals monitoring

## 📞 Soporte

Para dudas o issues: contactar al equipo de AI Engineering.

---

**Enterprise AI Agent** | NTT DATA | 2026
