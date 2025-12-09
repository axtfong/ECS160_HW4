package com.ecs160.microservices;

import com.ecs160.annotations.Endpoint;
import com.ecs160.annotations.Microservice;
import com.ecs160.microservices.model.BugIssue;
import com.ecs160.microservices.service.OllamaClient;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

@Microservice
public class IssueSummarizerMicroservice {
    private OllamaClient ollamaClient;
    private Gson gson;

    public IssueSummarizerMicroservice() {
        this.ollamaClient = new OllamaClient();
        this.gson = new Gson();
    }

    @Endpoint(url = "summarize_issue")
    public String handleRequest(String input) {
        try {
            // parses input json (github issue)
            JsonObject issueJson;
            try {
                issueJson = JsonParser.parseString(input).getAsJsonObject();
            } catch (Exception e) {
                // handles invalid JSON input gracefully
                BugIssue errorIssue = new BugIssue();
                errorIssue.setBug_type("Unknown");
                errorIssue.setLine(-1);
                errorIssue.setDescription(input != null && !input.isEmpty() ? input : "No input provided");
                errorIssue.setFilename("");
                return gson.toJson(errorIssue);
            }
            
            // extracts relevant fields
            String title = issueJson.has("title") ? issueJson.get("title").getAsString() : "";
            String body = issueJson.has("body") ? issueJson.get("body").getAsString() : "";
            String description = issueJson.has("description") ? issueJson.get("description").getAsString() : 
                               (body != null && !body.isEmpty() ? body : title);
            
            // creates prompt for ollama
            String prompt = String.format(
                "Summarize this GitHub issue into a bug report format. " +
                "Extract the bug type, estimated line number if mentioned, description, and filename if mentioned.\n\n" +
                "Title: %s\n" +
                "Description: %s\n\n" +
                "Return a JSON object with the following format:\n" +
                "{\n" +
                "  \"bug_type\": \"[type of bug]\",\n" +
                "  \"line\": [line number or -1 if not specified],\n" +
                "  \"description\": \"[brief description]\",\n" +
                "  \"filename\": \"[filename or empty string if not specified]\"\n" +
                "}\n" +
                "Only return the JSON object, no other text.",
                title, description
            );
            
            // gets response from ollama
            String response = ollamaClient.generate(prompt);
            
            // tries to parse response as json
            try {
                JsonObject jsonResponse = parseJsonFromResponse(response);
                if (jsonResponse != null) {
                    BugIssue bugIssue = parseBugIssueFromJson(jsonResponse);
                    return gson.toJson(bugIssue);
                }
            } catch (Exception e) {
                System.err.println("Error parsing JSON: " + e.getMessage());
            }
            
            // fallback: creates a basic bug issue
            BugIssue bugIssue = new BugIssue();
            bugIssue.setBug_type("Unknown");
            bugIssue.setLine(-1);
            bugIssue.setDescription(description);
            bugIssue.setFilename("");
            return gson.toJson(bugIssue);
        } catch (Exception e) {
            System.err.println("Error summarizing issue: " + e.getMessage());
            e.printStackTrace();
            
            // returns error response
            BugIssue errorIssue = new BugIssue();
            errorIssue.setBug_type("Error");
            errorIssue.setLine(-1);
            errorIssue.setDescription("Error processing issue: " + e.getMessage());
            errorIssue.setFilename("");
            return gson.toJson(errorIssue);
        }
    }
    
    // safely parses json from llm response, handling various formats
    private JsonObject parseJsonFromResponse(String response) {
        if (response == null || response.trim().isEmpty()) {
            return null;
        }
        
        try {
            // tries parsing directly first
            return JsonParser.parseString(response).getAsJsonObject();
        } catch (Exception e) {
            // tries extracting json object from response
            int jsonStart = response.indexOf("{");
            int jsonEnd = response.lastIndexOf("}") + 1;
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                try {
                    String jsonStr = response.substring(jsonStart, jsonEnd);
                    return JsonParser.parseString(jsonStr).getAsJsonObject();
                } catch (Exception ex) {
                    // continues to return null
                }
            }
        }
        
        return null;
    }
    
    // safely parses bug issue from json, handling none values and other edge cases
    private BugIssue parseBugIssueFromJson(JsonObject json) {
        BugIssue bugIssue = new BugIssue();
        
        // handles bug_type
        if (json.has("bug_type")) {
            try {
                bugIssue.setBug_type(json.get("bug_type").getAsString());
            } catch (Exception e) {
                bugIssue.setBug_type("Unknown");
            }
        } else {
            bugIssue.setBug_type("Unknown");
        }
        
        // handles line, converts none or invalid values to -1
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
                    // tries as integer
                    bugIssue.setLine(json.get("line").getAsInt());
                } catch (Exception ex) {
                    bugIssue.setLine(-1);
                }
            }
        } else {
            bugIssue.setLine(-1);
        }
        
        // handles description
        if (json.has("description")) {
            try {
                bugIssue.setDescription(json.get("description").getAsString());
            } catch (Exception e) {
                bugIssue.setDescription("");
            }
        } else {
            bugIssue.setDescription("");
        }
        
        // handles filename
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
}


