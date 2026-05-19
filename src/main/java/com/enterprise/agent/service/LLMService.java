package com.enterprise.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Ollama Implementation of LLMProvider
 *
 * Activated only when:
 * llm.provider=ollama (default if missing)
 */
@Service
@ConditionalOnProperty(name = "llm.provider", havingValue = "ollama", matchIfMissing = true)
public class LLMService implements LLMProvider {

    private final WebClient webClient;

    @Value("${llm.ollama.base-url:http://localhost:11434}")
    private String baseUrl;

    @Value("${llm.ollama.model:qwen2.5:7b}")
    private String model;

    public LLMService(WebClient.Builder builder,
                      @Value("${llm.ollama.base-url:http://localhost:11434}") String baseUrl) {

        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                .responseTimeout(Duration.ofSeconds(120))
                .doOnConnected(conn ->
                        conn.addHandlerLast(new ReadTimeoutHandler(120, TimeUnit.SECONDS)));

        this.webClient = builder
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    // =====================================================
    // AGENTIC CHAT
    // =====================================================
    @Override
    public String agentChat(String systemPrompt, String conversationContext) {

        String fullPrompt = """
%s

Debes razonar paso a paso internamente.
Responde ÚNICAMENTE en JSON válido con el siguiente formato:

{
  "thought": "razonamiento interno",
  "action": "CHAT | GET_INCIDENT | CREATE_INCIDENT | FINISH",
  "parameters": { }
}

Contexto:
%s
""".formatted(systemPrompt, conversationContext);

        return generate(fullPrompt, 0.2, 400);
    }

    // =====================================================
    // CLASSIFICATION (Compatibilidad legacy)
    // =====================================================
    public String classify(String systemPrompt, String userInput) {
        String fullPrompt = systemPrompt + "\n\n" + userInput;
        return generate(fullPrompt, 0.0, 300);
    }

    public String generateIncidentSummary(String contextData) {

        String prompt = """
Eres un asistente empresarial.

Redacta un resumen breve y natural basado SOLO en la información proporcionada.
No saludes.
No hagas preguntas.
No repitas los campos como lista.
Máximo 3 líneas.
Tono profesional y fluido.

Información:
%s
""".formatted(contextData);

        return generate(prompt, 0.3, 150);
    }

    public String generateChatResponse(String userInput) {

        String prompt = """
Eres un asistente empresarial conversacional.

Responde de forma natural, profesional y breve.
No uses JSON.
No seas robótico.
Mantén un tono cercano pero profesional.

Usuario:
%s
""".formatted(userInput);

        return generate(prompt, 0.6, 120);
    }

    // =====================================================
    // GENERATE (OLLAMA)
    // =====================================================
    @Override
    public String generate(String prompt, double temperature, int maxTokens) {

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("prompt", prompt);
        body.put("stream", false);
        body.put("temperature", temperature);
        body.put("num_predict", maxTokens);

        JsonNode response = webClient.post()
                .uri("/api/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        if (response == null || !response.has("response")) {
            return "No se pudo generar respuesta del modelo.";
        }

        return response.get("response").asText().trim();
    }
}
