"use client";

import { useState, useEffect } from 'react';

/**
 * Hook para manejar autenticación OAuth con ServiceNow
 * 
 * MVP: Almacena token en localStorage
 * Producción: Migrar a httpOnly cookies + refresh tokens
 */

interface AuthState {
  isAuthenticated: boolean;
  accessToken: string | null;
  isLoading: boolean;
}

export function useAuth() {
  const [authState, setAuthState] = useState<AuthState>({
    isAuthenticated: false,
    accessToken: null,
    isLoading: true,
  });

  useEffect(() => {
    // Verificar si hay token al montar el componente
    const token = localStorage.getItem('servicenow_access_token');
    setAuthState({
      isAuthenticated: !!token,
      accessToken: token,
      isLoading: false,
    });
  }, []);

  const login = (accessToken: string) => {
    localStorage.setItem('servicenow_access_token', accessToken);
    setAuthState({
      isAuthenticated: true,
      accessToken,
      isLoading: false,
    });
  };

  const logout = () => {
    localStorage.removeItem('servicenow_access_token');
    setAuthState({
      isAuthenticated: false,
      accessToken: null,
      isLoading: false,
    });
  };

  const initiateOAuth = () => {
    const clientId = process.env.NEXT_PUBLIC_OAUTH_CLIENT_ID;
    const instance = process.env.NEXT_PUBLIC_SERVICENOW_INSTANCE;
    const redirectUri = `${window.location.origin}/auth/callback`;

    if (!clientId || !instance) {
      console.error('OAuth configuration missing');
      return;
    }

    const authUrl = `${instance}/oauth_auth.do?response_type=code&client_id=${clientId}&redirect_uri=${encodeURIComponent(redirectUri)}`;
    window.location.href = authUrl;
  };

  return {
    ...authState,
    login,
    logout,
    initiateOAuth,
  };
}
