package com.ecs160.hw.util;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.ecs160.hw.model.Repo;
import com.ecs160.hw.model.Owner;

import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class JsonHandler {
    private Gson gson;

    public JsonHandler() {
        this.gson = new Gson();
    }

    public List<Repo> loadReposFromFile(String filePath) {
        List<Repo> repos = new ArrayList<>();
        try (FileReader reader = new FileReader(filePath)) {

            // parse top-level structure
            JsonObject jsonObject = gson.fromJson(reader, JsonObject.class);

            JsonArray items;
            if (jsonObject.has("items")) {
                items = jsonObject.getAsJsonArray("items");
            } else if (jsonObject.has("repositories")) {
                items = jsonObject.getAsJsonArray("repositories");
            } else {
                try (FileReader reReader = new FileReader(filePath)) {
                    items = gson.fromJson(reReader, JsonArray.class);
                }
            }

            System.out.println("Found " + items.size() + " repositories");

            // process repos
            for (JsonElement item : items) {
                Repo repo = new Repo();
                JsonObject repoObj = item.getAsJsonObject();

                repo.setName(getString(repoObj, "name"));
                repo.setHtmlUrl(getString(repoObj, "html_url"));
                repo.setForksCount(getInt(repoObj, "forks_count"));

                // language might be null for some repos
                if (repoObj.has("language") && !repoObj.get("language").isJsonNull()) {
                    repo.setLanguage(repoObj.get("language").getAsString());
                }

                repo.setOpenIssuesCount(getInt(repoObj, "open_issues_count"));
                repo.setStarCount(getInt(repoObj, "stargazers_count"));

                if (repoObj.has("owner") && !repoObj.get("owner").isJsonNull()) {
                    JsonObject ownerObj = repoObj.getAsJsonObject("owner");
                    Owner owner = new Owner();
                    owner.setLogin(getString(ownerObj, "login"));
                    owner.setId(getInt(ownerObj, "id"));
                    owner.setHtmlUrl(getString(ownerObj, "html_url"));
                    owner.setSiteAdmin(getBoolean(ownerObj, "site_admin"));
                    repo.setOwner(owner);
                    repo.setOwnerLogin(owner.getLogin());
                }

                repos.add(repo);
            }

        } catch (IOException e) {
            System.err.println("Error loading repositories from file: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("Unexpected error parsing JSON: " + e.getMessage());
            e.printStackTrace();
        }

        return repos;
    }

    // helpers to get stuff from json
    private String getString(JsonObject obj, String key) {
        try {
            if (obj.has(key) && !obj.get(key).isJsonNull()) {
                return obj.get(key).getAsString();
            }
        } catch (Exception e) {
            System.err.println("Error getting string for key " + key + ": " + e.getMessage());
        }
        return "";
    }

    private int getInt(JsonObject obj, String key) {
        try {
            if (obj.has(key) && !obj.get(key).isJsonNull()) {
                return obj.get(key).getAsInt();
            }
        } catch (Exception e) {
            System.err.println("Error getting int for key " + key + ": " + e.getMessage());
        }
        return 0;
    }

    private boolean getBoolean(JsonObject obj, String key) {
        try {
            if (obj.has(key) && !obj.get(key).isJsonNull()) {
                return obj.get(key).getAsBoolean();
            }
        } catch (Exception e) {
            System.err.println("Error getting boolean for key " + key + ": " + e.getMessage());
        }
        return false;
    }
}