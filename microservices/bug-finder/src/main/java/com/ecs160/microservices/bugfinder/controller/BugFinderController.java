package com.ecs160.microservices.bugfinder.controller;

import com.ecs160.microservices.bugfinder.model.BugIssue;
import com.ecs160.microservices.bugfinder.service.OllamaClient;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
public class BugFinderController {

    @Autowired
    private OllamaClient ollamaClient;

    private Gson gson = new Gson();

    @GetMapping("/find_bugs")
    public String findBugs(@RequestParam String input) {
        try {
            JsonObject inputJson;
            try {
                inputJson = JsonParser.parseString(input).getAsJsonObject();
            } catch (Exception e) {
                return gson.toJson(new ArrayList<>());
            }
            String filename = inputJson.has("filename") ? inputJson.get("filename").getAsString() : "unknown.c";
            String code = inputJson.has("content") ? inputJson.get("content").getAsString() : input;

            String prompt = String.format(
                "Analyze the following C code and identify all bugs. " +
                "You MUST return ONLY valid JSON. Return a JSON array of bug reports. " +
                "Each bug report must be a valid JSON object with these exact fields:\n" +
                "{\n" +
                "  \"bug_type\": \"[type of bug like NullPointerException, MemoryLeak, etc.]\",\n" +
                "  \"line\": [line number where the bug occurs as a number],\n" +
                "  \"description\": \"[brief description of the bug]\",\n" +
                "  \"filename\": \"%s\"\n" +
                "}\n\n" +
                "Example valid JSON response:\n" +
                "[{\"bug_type\": \"MemoryLeak\", \"line\": 42, \"description\": \"Missing free() call\", \"filename\": \"%s\"}]\n\n" +
                "C Code:\n%s\n\n" +
                "IMPORTANT: Return ONLY valid JSON array. All string values must be in double quotes. " +
                "Do not return JavaScript object notation. Return only JSON, no markdown, no code blocks, no other text.",
                filename, filename, code
            );

            String response = ollamaClient.generate(prompt);

            try {
                int arrayStart = response.indexOf("[");
                int arrayEnd = response.lastIndexOf("]") + 1;
                if (arrayStart >= 0 && arrayEnd > arrayStart) {
                    String jsonStr = response.substring(arrayStart, arrayEnd);
                    jsonStr = cleanJsonString(jsonStr);
                    try {
                        JsonArray jsonArray = JsonParser.parseString(jsonStr).getAsJsonArray();

                        List<BugIssue> bugs = new ArrayList<>();
                        for (int i = 0; i < jsonArray.size(); i++) {
                            try {
                                JsonObject bugObj = jsonArray.get(i).getAsJsonObject();
                                BugIssue bug = parseBugIssueFromJson(bugObj);
                                bugs.add(bug);
                            } catch (Exception e) {
                                System.err.println("Error parsing bug issue " + i + ": " + e.getMessage());
                            }
                        }

                        return gson.toJson(bugs);
                    } catch (Exception e) {
                        System.err.println("Error parsing JSON array: " + e.getMessage());
                        System.err.println("Raw JSON string (first 200 chars): " +
                            (jsonStr.length() > 200 ? jsonStr.substring(0, 200) + "..." : jsonStr));
                    }
                }

                int objStart = response.indexOf("{");
                int objEnd = response.lastIndexOf("}") + 1;
                if (objStart >= 0 && objEnd > objStart) {
                    String jsonStr = response.substring(objStart, objEnd);
                    try {
                        jsonStr = cleanJsonString(jsonStr);
                        JsonObject bugObj = JsonParser.parseString(jsonStr).getAsJsonObject();
                        BugIssue bug = parseBugIssueFromJson(bugObj);
                        List<BugIssue> bugs = new ArrayList<>();
                        bugs.add(bug);
                        return gson.toJson(bugs);
                    } catch (Exception e) {
                        System.err.println("Error parsing single bug object: " + e.getMessage());
                    }
                }

                return gson.toJson(new ArrayList<>());
            } catch (Exception e) {
                System.err.println("Error parsing Ollama response: " + e.getMessage());
                e.printStackTrace();
                return gson.toJson(new ArrayList<>());
            }
        } catch (Exception e) {
            System.err.println("Error finding bugs: " + e.getMessage());
            e.printStackTrace();
            return gson.toJson(new ArrayList<>());
        }
    }

    private BugIssue parseBugIssueFromJson(JsonObject json) {
        BugIssue bugIssue = new BugIssue();

        if (json.has("bug_type")) {
            try {
                bugIssue.setBug_type(json.get("bug_type").getAsString());
            } catch (Exception e) {
                bugIssue.setBug_type("Unknown");
            }
        } else {
            bugIssue.setBug_type("Unknown");
        }

        if (json.has("line")) {
            try {
                String lineStr = json.get("line").getAsString().trim();
                if (lineStr.equalsIgnoreCase("None") || lineStr.equalsIgnoreCase("null") ||
                    lineStr.isEmpty() || lineStr.equals("-")) {
                    bugIssue.setLine(-1);
                } else {
                    bugIssue.setLine(Integer.parseInt(lineStr));
                }
            } catch (Exception e) {
                try {
                    bugIssue.setLine(json.get("line").getAsInt());
                } catch (Exception ex) {
                    bugIssue.setLine(-1);
                }
            }
        } else {
            bugIssue.setLine(-1);
        }

        if (json.has("description")) {
            try {
                bugIssue.setDescription(json.get("description").getAsString());
            } catch (Exception e) {
                bugIssue.setDescription("");
            }
        } else {
            bugIssue.setDescription("");
        }

        if (json.has("filename")) {
            try {
                String filename = json.get("filename").getAsString();
                if (filename.equalsIgnoreCase("None") || filename.equalsIgnoreCase("null")) {
                    bugIssue.setFilename("");
                } else {
                    bugIssue.setFilename(filename);
                }
            } catch (Exception e) {
                bugIssue.setFilename("");
            }
        } else {
            bugIssue.setFilename("");
        }

        return bugIssue;
    }

    private String cleanJsonString(String jsonStr) {
        if (jsonStr == null || jsonStr.isEmpty()) return "";

        jsonStr = jsonStr.replaceAll("(?i)```json\\s*", "");
        jsonStr = jsonStr.replaceAll("```\\s*", "");

        int firstBracket = Math.min(
            jsonStr.indexOf('[') >= 0 ? jsonStr.indexOf('[') : Integer.MAX_VALUE,
            jsonStr.indexOf('{') >= 0 ? jsonStr.indexOf('{') : Integer.MAX_VALUE
        );
        if (firstBracket > 0 && firstBracket < Integer.MAX_VALUE) {
            jsonStr = jsonStr.substring(firstBracket);
        }

        int lastBracket = Math.max(
            jsonStr.lastIndexOf(']'),
            jsonStr.lastIndexOf('}')
        );
        if (lastBracket >= 0 && lastBracket < jsonStr.length() - 1) {
            jsonStr = jsonStr.substring(0, lastBracket + 1);
        }

        jsonStr = fixJavaScriptObjectNotation(jsonStr);

        StringBuilder sb = new StringBuilder();
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < jsonStr.length(); i++) {
            char c = jsonStr.charAt(i);
            if (escaped) {
                sb.append(c);
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                sb.append(c);
                continue;
            }
            if (c == '"') {
                inString = !inString;
                sb.append(c);
                continue;
            }
            if (!inString && c == '/' && i + 1 < jsonStr.length()) {
                if (jsonStr.charAt(i + 1) == '/') {
                    while (i < jsonStr.length() && jsonStr.charAt(i) != '\n' && jsonStr.charAt(i) != '\r') {
                        i++;
                    }
                    continue;
                } else if (jsonStr.charAt(i + 1) == '*') {
                    i += 2;
                    while (i + 1 < jsonStr.length()) {
                        if (jsonStr.charAt(i) == '*' && jsonStr.charAt(i + 1) == '/') {
                            i += 2;
                            break;
                        }
                        i++;
                    }
                    continue;
                }
            }
            sb.append(c);
        }
        jsonStr = sb.toString();

        jsonStr = jsonStr.replaceAll("\"line\"\\s*:\\s*\\[\\s*(\\d+)\\s*\\]\\s*,?", "\"line\": $1,");
        jsonStr = jsonStr.replaceAll("\"line\"\\s*:\\s*\\[\\s*(\\d+)\\s*\\]\\s*}", "\"line\": $1}");
        jsonStr = jsonStr.replaceAll("^\\s*\\[\\s*\\d+\\s*\\]\\s*,\\s*", "");
        jsonStr = jsonStr.replaceAll("\\r\\n", "\\\\n");
        jsonStr = jsonStr.replaceAll("\\n", "\\\\n");
        jsonStr = jsonStr.replaceAll("\\r", "\\\\r");
        jsonStr = jsonStr.replaceAll("\\t", "\\\\t");

        jsonStr = jsonStr.trim();

        jsonStr = jsonStr.replaceAll(",\\s*}", "}");
        jsonStr = jsonStr.replaceAll(",\\s*]", "]");

        jsonStr = jsonStr.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "");

        return jsonStr;
    }

    private String fixJavaScriptObjectNotation(String jsonStr) {
        String pattern = "\"([^\"]+)\"\\s*:\\s*([a-zA-Z_][a-zA-Z0-9_]*)";
        Pattern p = Pattern.compile(pattern);
        Matcher m = p.matcher(jsonStr);

        StringBuffer result = new StringBuffer();
        while (m.find()) {
            String key = m.group(1);
            String value = m.group(2);

            if (!value.equals("true") && !value.equals("false") && !value.equals("null")) {
                m.appendReplacement(result, "\"" + key + "\": \"" + value + "\"");
            } else {
                m.appendReplacement(result, m.group(0));
            }
        }
        m.appendTail(result);

        return result.toString();
    }
}
