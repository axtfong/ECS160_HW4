package com.ecs160.microservices.issuecomparator.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

@Service
public class OllamaClient {
    private static final String OLLAMA_URL = "http://localhost:11434/api/generate";
    private static final String MODEL = "deepcoder:1.5b";

    public String generate(String prompt) {
        try {
            URL url = new URI(OLLAMA_URL).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            String jsonInputString = String.format(
                "{\"model\": \"%s\", \"prompt\": %s, \"stream\": false}",
                MODEL,
                escapeJsonString(prompt)
            );

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new RuntimeException("Ollama API returned error code: " + responseCode);
            }

            StringBuilder response = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
            }

            String responseStr = response.toString();
            try {
                JsonObject jsonResponse = JsonParser.parseString(responseStr).getAsJsonObject();
                if (jsonResponse.has("response")) {
                    return jsonResponse.get("response").getAsString();
                }
            } catch (Exception e) {
                if (responseStr.contains("\"response\"")) {
                    int start = responseStr.indexOf("\"response\"") + 11;
                    int end = responseStr.indexOf("\"", start);
                    if (end > start) {
                        String extracted = responseStr.substring(start, end);
                        extracted = extracted.replace("\\n", "\n").replace("\\\"", "\"");
                        return extracted;
                    }
                }
            }

            return responseStr;
        } catch (Exception e) {
            System.err.println("Error calling Ollama: " + e.getMessage());
            e.printStackTrace();
            return "Error: " + e.getMessage();
        }
    }

    private String escapeJsonString(String str) {
        return "\"" + str.replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", "\\n")
                        .replace("\r", "\\r")
                        .replace("\t", "\\t") + "\"";
    }
}
