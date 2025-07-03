package com.mcpserver.mcpserverspringboot.controller;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api")
public class McpGptController {

    // In-memory cache to store fetched data
    private final Map<String, Object> cachedData = new ConcurrentHashMap<>();

    private final RestTemplate restTemplate = new RestTemplate();

    // Replace with your actual OpenAI API key
    private final String OPENAI_API_KEY = "OPENAI_API_KEY";

    // Endpoint to fetch and cache data from external API using Bearer token
    @PostMapping("/fetch")
    public ResponseEntity<?> fetchData(@RequestBody Map<String, String> body) {
        String apiUrl = body.get("apiUrl");
        String token = body.get("token");

        if (apiUrl == null || token == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "apiUrl and token are required"));
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(apiUrl, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                // Cache raw JSON string data
                cachedData.put("data", response.getBody());
                return ResponseEntity.ok(Map.of("message", "Data fetched and cached successfully"));
            } else {
                return ResponseEntity.status(response.getStatusCode())
                        .body(Map.of("error", "Failed to fetch data from API"));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Exception: " + e.getMessage()));
        }
    }

    // Endpoint to ask a question about the cached data; returns GPT's answer
    @PostMapping(value = "/ask", consumes = MediaType.TEXT_PLAIN_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> askPlainText(@RequestBody String question) {
        Object data = cachedData.get("data");

        if (data == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "No data fetched yet. Please fetch data first."));
        }

        String prompt = "Given the following data:\n" + data.toString() + "\nAnswer this question:\n" + question;

        try {
            String gptResponse = callOpenAi(prompt);
            return ResponseEntity.ok(Map.of("answer", gptResponse));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to get answer from GPT: " + e.getMessage()));
        }
    }

    // Helper method to call OpenAI Chat API
    private String callOpenAi(String prompt) {
        String openAiUrl = "https://api.openai.com/v1/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(OPENAI_API_KEY);

        // Create JSON body for OpenAI API
        String requestBody = """
                {
                  "model": "gpt-4o-mini",
                  "messages": [
                    {"role": "system", "content": "You are a helpful assistant."},
                    {"role": "user", "content": "%s"}
                  ],
                  "temperature": 0.7,
                  "max_tokens": 500
                }
                """.formatted(prompt.replace("\"", "\\\"").replace("\n", "\\n"));

        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(openAiUrl, entity, Map.class);

        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            var choices = (java.util.List<Map<String, Object>>) response.getBody().get("choices");
            if (choices != null && !choices.isEmpty()) {
                var message = (Map<String, Object>) choices.get(0).get("message");
                if (message != null) {
                    return message.get("content").toString().trim();
                }
            }
        }
        throw new RuntimeException("Invalid response from OpenAI API");
    }
}
