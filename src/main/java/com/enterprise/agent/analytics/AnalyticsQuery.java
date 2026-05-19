package com.enterprise.agent.analytics;

import lombok.Data;

import java.util.Map;

/**
 * AnalyticsQuery
 *
 * Representa una consulta analítica estructurada
 * generada por el LLM Planner.
 */
@Data
public class AnalyticsQuery {

    /**
     * count | average | list | group
     */
    private String metric;

    /**
     * Filtros dinámicos:
     * priority, state, category, assigned_to, etc.
     */
    private Map<String, String> filters;

    /**
     * today | yesterday | last_week | until_now | this_week
     */
    private String dateRange;

    /**
     * assigned_to | category | state | none
     */
    private String groupBy;

    /**
     * numeric_only | summary | detailed
     */
    private String outputMode;

    /**
     * Limite de resultados (para listados)
     */
    private Integer limit;
}
