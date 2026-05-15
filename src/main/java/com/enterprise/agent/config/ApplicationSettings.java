package com.enterprise.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.ZoneId;

/**
 * ApplicationSettings
 *
 * Permite configurar parámetros globales como zona horaria.
 * Preparado para futura vista de Settings en frontend.
 */
@Configuration
@ConfigurationProperties(prefix = "app")
public class ApplicationSettings {

    /**
     * Zona horaria por defecto.
     * Ejemplo: America/Lima
     */
    private String timezone = "America/Lima";

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public ZoneId getZoneId() {
        return ZoneId.of(timezone);
    }
}
