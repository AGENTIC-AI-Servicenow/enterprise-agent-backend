"use client";

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { incidentsApi } from '@/lib/api-client';
import { Incident, IncidentSummary } from '@/types';

/**
 * Hook para gestión de incidentes
 * Usa React Query para cache, invalidación automática y optimistic updates
 */

interface UseIncidentsOptions {
  enabled?: boolean;
  refetchInterval?: number;
}

export function useIncidents(options: UseIncidentsOptions = {}) {
  const queryClient = useQueryClient();

  // Query para listar todos los incidentes
  const {
    data: incidents,
    isLoading,
    error,
    refetch,
  } = useQuery<Incident[]>({
    queryKey: ['incidents'],
    queryFn: async () => {
      const response = await incidentsApi.getAll();
      // Backend returns ApiResponse<Incident[]> with { success, data }
      if (!response.success && !response.data) {
        throw new Error(response.error || 'Failed to load incidents');
      }
      return response.data || [];
    },
    staleTime: 30000,        // 30 segundos
    retry: 1,                 // Solo 1 reintento para reducir ruido en consola
    refetchOnWindowFocus: false,
    enabled: options.enabled !== false,
    refetchInterval: options.refetchInterval,
  });

  // Query para el resumen/métricas
  const {
    data: summary,
    isLoading: isSummaryLoading,
  } = useQuery<IncidentSummary>({
    queryKey: ['incidents', 'summary'],
    queryFn: async () => {
      const response = await incidentsApi.getSummary();
      if (!response.data) {
        throw new Error('No summary data available');
      }
      return response.data;
    },
    staleTime: 60000,         // 1 minuto
    retry: 1,
    refetchOnWindowFocus: false,
    enabled: options.enabled !== false,
  });

  // Mutation para actualizar un incidente
  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: Partial<Incident> }) =>
      incidentsApi.update(id, data),
    onSuccess: () => {
      // Invalidar queries para refrescar datos
      queryClient.invalidateQueries({ queryKey: ['incidents'] });
    },
  });

  return {
    incidents,
    summary,
    isLoading,
    isSummaryLoading,
    error,
    refetch,
    updateIncident: updateMutation.mutate,
    isUpdating: updateMutation.isPending,
  };
}

/**
 * Hook para obtener un incidente específico por ID
 */
export function useIncident(id: string | null) {
  return useQuery<Incident>({
    queryKey: ['incidents', id],
    queryFn: async (): Promise<Incident> => {
      const response: any = await incidentsApi.getById(id!);

      // If backend returns wrapped ApiResponse
      if (response?.success !== undefined) {
        if (!response.data) {
          throw new Error("Incident not found");
        }
        return response.data as Incident;
      }

      // If backend returns raw incident object
      return response as Incident;
    },
    enabled: !!id,
    staleTime: 60000,
  });
}
