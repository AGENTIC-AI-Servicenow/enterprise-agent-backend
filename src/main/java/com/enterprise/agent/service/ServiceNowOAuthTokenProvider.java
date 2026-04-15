package com.enterprise.agent.service;

/**
 * LEGACY CLASS REMOVED.
 *
 * This class previously implemented Password Grant logic.
 * It has been intentionally neutralized as part of the
 * migration to OAuth 2.0 Authorization Code Flow.
 *
 * DO NOT USE.
 */
import org.springframework.stereotype.Component;

/**
 * LEGACY STUB — Maintained only to avoid breaking
 * dependencies during migration.
 *
 * All legacy Password Grant logic has been removed.
 * This class now acts as a temporary compatibility layer.
 */
@Component
public class ServiceNowOAuthTokenProvider {

    public String getValidAccessToken() {
        throw new UnsupportedOperationException(
                "Legacy Password Grant flow removed. Use Authorization Code Flow."
        );
    }

    public String getAuthenticatedUsername() {
        return "authorization-code-user";
    }

    public void forceTokenRefresh() {
        throw new UnsupportedOperationException(
                "Legacy refresh removed. Authorization Code Flow handles refresh automatically."
        );
    }

    public void clearCache() {
        // no-op
    }
}
