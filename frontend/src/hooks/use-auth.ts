"use client";

import { useState, useEffect } from 'react';

/**
 * Hook para manejar autenticación OAuth con ServiceNow.
 *
 * Flujo:
 *  1. El usuario hace clic en "Sign in with ServiceNow"
 *  2. El frontend redirige a GET /oauth/authorize (backend)
 *  3. El backend redirige a ServiceNow para que el usuario autorice
 *  4. ServiceNow llama al backend GET /oauth/callback?code=XXX
 *  5. El backend intercambia el código por tokens y redirige a
 *     /auth/callback?userId=YYY&success=true (frontend)
 *  6. La página /auth/callback guarda user_id en localStorage
 *  7. api-client.ts adjunta X-User-Id en cada petición
 *
 * MVP: userId almacenado en localStorage
 * Producción: httpOnly cookies + refresh tokens
 */

interface AuthState {
  isAuthenticated: boolean;
  userId: string | null;
  isLoading: boolean;
}

const USER_ID_KEY = 'user_id';

export function useAuth() {
  const [authState, setAuthState] = useState<AuthState>({
    isAuthenticated: false,
    userId: null,
    isLoading: true,
  });

  useEffect(() => {
    const userId = localStorage.getItem(USER_ID_KEY);
    setAuthState({
      isAuthenticated: !!userId,
      userId,
      isLoading: false,
    });
  }, []);

  const login = (userId: string) => {
    localStorage.setItem(USER_ID_KEY, userId);
    localStorage.setItem('auth_method', 'oauth');
    setAuthState({
      isAuthenticated: true,
      userId,
      isLoading: false,
    });
  };

  const logout = () => {
    localStorage.removeItem(USER_ID_KEY);
    localStorage.removeItem('auth_method');
    setAuthState({
      isAuthenticated: false,
      userId: null,
      isLoading: false,
    });
  };

  /**
   * Inicia el flujo OAuth delegando al backend.
   * El backend redirige a ServiceNow y maneja el callback.
   */
  const initiateOAuth = () => {
    const apiUrl = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';
    window.location.href = `${apiUrl}/oauth/authorize`;
  };

  return {
    ...authState,
    login,
    logout,
    initiateOAuth,
  };
}
