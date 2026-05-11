package com.enterprise.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class LLMService {

    private final WebClient webClient;

    @Value("${ollama.model}")
    private String model;

    public LLMService(WebClient.Builder builder) {

        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                .responseTimeout(Duration.ofSeconds(120))
                .doOnConnected(conn ->
                        conn.addHandlerLast(new ReadTimeoutHandler(120, TimeUnit.SECONDS)));

        this.webClient = builder
                .baseUrl("http://localhost:11434")
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    // =====================================================
    // 1️⃣ CLASIFICADOR DE INTENCIÓN (JSON ESTRICTO)
    // =====================================================
    
    /**
     * Método genérico para clasificación con system prompt personalizado
     * Usado por IntentClassifier
     */
    public String classify(String systemPrompt, String userInput) {
        String fullPrompt = systemPrompt + "\n\n" + userInput;
        return generate(fullPrompt, 0.0, 300);
    }
    
    /**
     * Método legacy para clasificación simple
     */
    public String classifyIntent(String userInput) {

        String systemPrompt = """
Eres un clasificador estricto de intención.

Responde SOLO en JSON válido.
No escribas texto adicional.
No expliques nada.

Acciones posibles:

1) GET_INCIDENT
   Parámetro opcional: number

2) CREATE_INCIDENT
   Parámetros: short_description, description, priority

3) CHAT

Reglas:

- Si el usuario menciona un número que empiece por INC → GET_INCIDENT con number.
- Si el usuario habla de "mi última incidencia", "mi incidente más reciente" o similar → GET_INCIDENT sin number.
- Si el usuario quiere crear un incidente → CREATE_INCIDENT.
- Si no requiere acción técnica → CHAT.
- Nunca escribas texto fuera del JSON.

Formatos obligatorios:

GET_INCIDENT con número:
{ "action": "GET_INCIDENT", "number": "INC0012345" }

GET_INCIDENT sin número:
{ "action": "GET_INCIDENT" }

CREATE_INCIDENT:
{ 
  "action": "CREATE_INCIDENT",
  "short_description": "...",
  "description": "...",
  "priority": "3"
}

CHAT:
{ "action": "CHAT" }
""";

        return generate(systemPrompt + "\n\nUsuario: " + userInput, 0, 200);
    }

    // =====================================================
    // 2️⃣ RESUMEN DE INCIDENTE
    // =====================================================
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

    // =====================================================
    // 3️⃣ CONVERSACIÓN LIBRE
    // =====================================================
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
    // MÉTODO CENTRALIZADO
    // =====================================================
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
