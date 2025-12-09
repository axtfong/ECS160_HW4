package com.ecs160.microservices.issuecomparator.controller;

import com.ecs160.microservices.issuecomparator.model.BugIssue;
import com.ecs160.microservices.issuecomparator.service.OllamaClient;
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

@RestController
public class IssueComparatorController {

    @Autowired
    private OllamaClient ollamaClient;

    private Gson gson = new Gson();

    @GetMapping("/check_equivalence")
    public String checkEquivalence(@RequestParam String input) {
        try {
            JsonObject inputJson;
            try {
                inputJson = JsonParser.parseString(input).getAsJsonObject();
            } catch (Exception e) {
                return gson.toJson(new ArrayList<>());
            }
            JsonArray list1Json = inputJson.has("list1") ? inputJson.get("list1").getAsJsonArray() : new JsonArray();
            JsonArray list2Json = inputJson.has("list2") ? inputJson.get("list2").getAsJsonArray() : new JsonArray();

            List<BugIssue> list1 = new ArrayList<>();
            for (int i = 0; i < list1Json.size(); i++) {
                try {
                    JsonObject issueJson = list1Json.get(i).getAsJsonObject();
                    BugIssue issue = parseBugIssueFromJson(issueJson);
                    list1.add(issue);
                } catch (Exception e) {
                    System.err.println("Error parsing issue in list1: " + e.getMessage());
                }
            }

            List<BugIssue> list2 = new ArrayList<>();
            for (int i = 0; i < list2Json.size(); i++) {
                try {
                    JsonObject issueJson = list2Json.get(i).getAsJsonObject();
                    BugIssue issue = parseBugIssueFromJson(issueJson);
                    list2.add(issue);
                } catch (Exception e) {
                    System.err.println("Error parsing issue in list2: " + e.getMessage());
                }
            }

            String prompt = String.format(
                "Compare these two lists of bug reports and identify which bugs are the same or very similar.\n\n" +
                "List 1:\n%s\n\n" +
                "List 2:\n%s\n\n" +
                "Return a JSON array containing only the bugs that appear in both lists (or are very similar). " +
                "Each bug should be in the format:\n" +
                "{\n" +
                "  \"bug_type\": \"[type]\",\n" +
                "  \"line\": [line number],\n" +
                "  \"description\": \"[description]\",\n" +
                "  \"filename\": \"[filename]\"\n" +
                "}\n\n" +
                "Return only the JSON array, no other text.",
                gson.toJson(list1), gson.toJson(list2)
            );

            String response = ollamaClient.generate(prompt);

            try {
                JsonArray jsonArray = parseJsonArrayFromResponse(response);
                if (jsonArray != null) {
                    List<BugIssue> commonBugs = new ArrayList<>();
                    for (int i = 0; i < jsonArray.size(); i++) {
                        try {
                            JsonObject bugObj = jsonArray.get(i).getAsJsonObject();
                            BugIssue bug = parseBugIssueFromJson(bugObj);
                            commonBugs.add(bug);
                        } catch (Exception e) {
                            System.err.println("Error parsing bug issue " + i + ": " + e.getMessage());
                        }
                    }

                    return gson.toJson(commonBugs);
                }
            } catch (Exception e) {
                System.err.println("Error parsing Ollama response: " + e.getMessage());
                e.printStackTrace();
            }

            List<BugIssue> commonBugs = findCommonBugs(list1, list2);
            return gson.toJson(commonBugs);
        } catch (Exception e) {
            System.err.println("Error comparing issues: " + e.getMessage());
            e.printStackTrace();
            return gson.toJson(new ArrayList<>());
        }
    }

    private List<BugIssue> findCommonBugs(List<BugIssue> list1, List<BugIssue> list2) {
        List<BugIssue> common = new ArrayList<>();

        for (BugIssue bug1 : list1) {
            for (BugIssue bug2 : list2) {
                if (bug1.getBug_type().equalsIgnoreCase(bug2.getBug_type())) {
                    String desc1 = bug1.getDescription().toLowerCase();
                    String desc2 = bug2.getDescription().toLowerCase();

                    String[] words1 = desc1.split("\\s+");
                    String[] words2 = desc2.split("\\s+");
                    int commonWords = 0;
                    for (String word1 : words1) {
                        if (word1.length() > 3) {
                            for (String word2 : words2) {
                                if (word1.equals(word2)) {
                                    commonWords++;
                                    break;
                                }
                            }
                        }
                    }

                    if (commonWords >= 2) {
                        common.add(bug1);
                        break;
                    }
                }
            }
        }

        return common;
    }

    private JsonArray parseJsonArrayFromResponse(String response) {
        if (response == null || response.trim().isEmpty()) {
            return null;
        }

        try {
            return JsonParser.parseString(response).getAsJsonArray();
        } catch (Exception e) {
            int arrayStart = response.indexOf("[");
            int arrayEnd = response.lastIndexOf("]") + 1;
            if (arrayStart >= 0 && arrayEnd > arrayStart) {
                try {
                    String jsonStr = response.substring(arrayStart, arrayEnd);
                    return JsonParser.parseString(jsonStr).getAsJsonArray();
                } catch (Exception ex) {
                    return null;
                }
            }
        }

        return null;
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
}
