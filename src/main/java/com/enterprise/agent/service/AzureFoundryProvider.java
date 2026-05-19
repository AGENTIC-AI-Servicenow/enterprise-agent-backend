package com.enterprise.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

/**
 * Azure Foundry (Azure OpenAI) implementation of LLMProvider.
 *
 * Activated when:
 * llm.provider=azure
 */
@Service
@ConditionalOnProperty(name = "llm.provider", havingValue = "azure")
public class AzureFoundryProvider implements LLMProvider {

    private final WebClient webClient;

    @Value("${llm.azure.endpoint:}")
    private String endpoint;

    @Value("${llm.azure.deployment:gpt-4o}")
    private String deployment;

    @Value("${llm.azure.api-key:}")
    private String apiKey;

    @Value("${llm.azure.api-version:2024-12-01-preview}")
    private String apiVersion;

    /**
     * IMPORTANT:
     * We intentionally create a "clean" WebClient here (without app-wide filters).
     *
     * The project also configures a WebClient for ServiceNow that includes
     * ServiceNowWebClientFilter (adds ServiceNow auth headers). If we reuse that
     * builder here, the filter would incorrectly try to add ServiceNow headers
     * to Azure OpenAI calls and fail when the user is not authenticated in ServiceNow.
     */
    public AzureFoundryProvider(WebClient.Builder builder) {
        this.webClient = WebClient.builder().build();
    }

    @Override
    public String agentChat(String systemPrompt, String context) {
        return generate(systemPrompt + "\n\n" + context, 0.2, 400);
    }

    @Override
    public String generate(String prompt, double temperature, int maxTokens) {

        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalStateException(
                "Azure Foundry endpoint not configured. Set llm.azure.endpoint or AZURE_OPENAI_ENDPOINT env var.");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                "Azure Foundry API key not configured. Set AZURE_OPENAI_KEY env var.");
        }

        Map<String, Object> body = Map.of(
                "messages", List.of(
                        Map.of("role", "system", "content", "Eres un asistente empresarial."),
                        Map.of("role", "user", "content", prompt)
                ),
                "temperature", temperature,
                "max_tokens", maxTokens
        );

        String uri = endpoint +
                "/openai/deployments/" + deployment +
                "/chat/completions?api-version=" + apiVersion;

        JsonNode response = webClient.post()
                .uri(uri)
                .header("api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        if (response == null
                || !response.has("choices")
                || response.get("choices").isEmpty()) {
            return "No se pudo generar respuesta del modelo Azure.";
        }

        return response
                .get("choices")
                .get(0)
                .get("message")
                .get("content")
                .asText()
                .trim();
    }
}
