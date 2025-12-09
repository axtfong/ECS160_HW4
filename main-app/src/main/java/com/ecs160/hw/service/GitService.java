package com.ecs160.hw.service;

import com.ecs160.hw.model.Commit;
import com.ecs160.hw.model.Repo;
import com.ecs160.hw.model.Issue;
import com.ecs160.hw.util.ConfigUtil;
import redis.clients.jedis.Jedis;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.util.*;
import java.net.URL;
import java.net.HttpURLConnection;

public class GitService {
    private Jedis jedis;

    public GitService() {
        try {
            this.jedis = new Jedis("localhost", 6379);
        } catch (Exception e) {
            System.err.println("Error connecting to Redis: " + e.getMessage());
            this.jedis = null;
        }
    }

    public Map<String, Integer> calculateFileModificationCount(Repo repo) {
        Map<String, Integer> fileModificationCount = new HashMap<>();

        for (Commit commit : repo.getRecentCommits()) {
            for (String file : commit.getModifiedFiles()) {
                // increments counter for each modified file
                fileModificationCount.put(file, fileModificationCount.getOrDefault(file, 0) + 1);
            }
        }

        return fileModificationCount;
    }

    public List<String> getTop3ModifiedFiles(Repo repo) {
        Map<String, Integer> fileModificationCount = calculateFileModificationCount(repo);

        List<Map.Entry<String, Integer>> sortedFiles = new ArrayList<>(fileModificationCount.entrySet());

        // sorts by modification count then filename
        sortedFiles.sort((a, b) -> {
            int countComparison = b.getValue().compareTo(a.getValue());
            if (countComparison != 0) {
                return countComparison;
            }

            return a.getKey().compareTo(b.getKey());
        });

        List<String> top3Files = new ArrayList<>();
        for (int i = 0; i < Math.min(3, sortedFiles.size()); i++) {
            top3Files.add(sortedFiles.get(i).getKey());
        }

        return top3Files;
    }

    public boolean isRepoContainingSourceCode(Repo repo) {
        if (repo.getRecentCommits() != null && !repo.getRecentCommits().isEmpty()) {
            List<String> sourceFileExtensions = Arrays.asList(".java", ".cpp", ".c", ".h", ".rs", ".go", ".py", ".js");

            // checks if any file has source code extension
            for (Commit commit : repo.getRecentCommits()) {
                for (String file : commit.getModifiedFiles()) {
                    for (String ext : sourceFileExtensions) {
                        if (file.endsWith(ext)) {
                            return true;
                        }
                    }
                }
            }
            return false;
        } else {
            String name = repo.getName().toLowerCase();
            String language = repo.getLanguage();

            // checks if these words exist to identify tutorial
            boolean isTutorial = name.contains("guide") ||
                    name.contains("tutorial") ||
                    name.contains("awesome") ||
                    name.contains("example") ||
                    name.contains("learn") ||
                    name.contains("book") ||
                    name.contains("course") ||
                    name.contains("doc");

            // returns true if has language and not tutorial
            return language != null && !language.isEmpty() && !isTutorial;
        }
    }

    public void cloneRepository(Repo repo, String destinationDir) {
        try {
            System.out.println("Cloning repository: " + repo.getName());

            File dir = new File(destinationDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            String cloneCommand = "git clone --depth 1 " + repo.getHtmlUrl() + " " + destinationDir + "/" + repo.getName();

            // executes git clone
            Process process = Runtime.getRuntime().exec(cloneCommand);

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }

            int exitCode = process.waitFor();
            System.out.println("Clone completed with exit code: " + exitCode);

        } catch (Exception e) {
            System.err.println("Error cloning repository: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void saveRepoToRedis(Repo repo) {
        // generates repo id if not set
        if (repo.getId() == null || repo.getId().isEmpty()) {
            repo.setId("repo-" + System.currentTimeMillis());
        }
        String repoKey = repo.getId();

        // generates issue ids and saves issues first in database 1
        List<String> issueIds = new ArrayList<>();
        if (repo.getIssues() != null && !repo.getIssues().isEmpty()) {
            jedis.select(1);  // switches to database 1 for issues
            for (int i = 0; i < repo.getIssues().size(); i++) {
                Issue issue = repo.getIssues().get(i);
                // generates issue id if not set
                if (issue.getId() == null || issue.getId().isEmpty()) {
                    issue.setId("iss-" + (System.currentTimeMillis() + i));
                }
                String issueKey = issue.getId();
                issueIds.add(issueKey);

                // saves issue info using issue id as key
                jedis.hset(issueKey, "id", issue.getId());
                if (issue.getCreatedAt() != null) {
                    // formats date as iso 8601 string
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
                    jedis.hset(issueKey, "Date", sdf.format(issue.getCreatedAt()));
                } else {
                    // uses current date if not set
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
                    jedis.hset(issueKey, "Date", sdf.format(new Date()));
                }
                jedis.hset(issueKey, "Description", issue.getDescription() != null ? issue.getDescription() : "");
                
                // saves title and body for reference
                jedis.hset(issueKey, "title", issue.getTitle() != null ? issue.getTitle() : "");
                jedis.hset(issueKey, "body", issue.getBody() != null ? issue.getBody() : "");
                jedis.hset(issueKey, "state", issue.getState() != null ? issue.getState() : "");
                if (issue.getUpdatedAt() != null) {
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
                    jedis.hset(issueKey, "updatedAt", sdf.format(issue.getUpdatedAt()));
                }
            }
        }

        // saves repo info using repo id as key in database 0
        jedis.select(0);  // switches back to database 0 for repos
        jedis.hset(repoKey, "id", repo.getId());
        jedis.hset(repoKey, "Url", repo.getHtmlUrl() != null ? repo.getHtmlUrl() : "");
        
        // formats createdat date
        if (repo.getCreatedAt() != null) {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
            jedis.hset(repoKey, "CreatedAt", sdf.format(repo.getCreatedAt()));
        } else {
            // uses current date if not set
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
            jedis.hset(repoKey, "CreatedAt", sdf.format(new Date()));
        }
        
        // author name is the owner login
        jedis.hset(repoKey, "Author Name", repo.getOwnerLogin() != null ? repo.getOwnerLogin() : "");
        
        // saves comma-separated issue ids
        String issuesList = String.join(",", issueIds);
        jedis.hset(repoKey, "Issues", issuesList);
        
        // also saves other fields for compatibility
        jedis.hset(repoKey, "name", repo.getName());
        jedis.hset(repoKey, "ownerLogin", repo.getOwnerLogin() != null ? repo.getOwnerLogin() : "");
        jedis.hset(repoKey, "htmlUrl", repo.getHtmlUrl() != null ? repo.getHtmlUrl() : "");
        jedis.hset(repoKey, "forksCount", String.valueOf(repo.getForksCount()));
        jedis.hset(repoKey, "language", repo.getLanguage() != null ? repo.getLanguage() : "");
        jedis.hset(repoKey, "openIssuesCount", String.valueOf(repo.getOpenIssuesCount()));
        jedis.hset(repoKey, "starCount", String.valueOf(repo.getStarCount()));
        jedis.hset(repoKey, "commitCount", String.valueOf(repo.getCommitCount()));

        // saves owner info
        if (repo.getOwner() != null) {
            String ownerKey = "owner:" + repo.getOwner().getLogin();
            jedis.hset(ownerKey, "login", repo.getOwner().getLogin());
            jedis.hset(ownerKey, "id", String.valueOf(repo.getOwner().getId()));
            jedis.hset(ownerKey, "htmlUrl", repo.getOwner().getHtmlUrl() != null ? repo.getOwner().getHtmlUrl() : "");
            jedis.hset(ownerKey, "siteAdmin", String.valueOf(repo.getOwner().isSiteAdmin()));
            jedis.hset(repoKey, "owner", ownerKey);
        }
    }

    private String makeGitHubApiRequest(String endpoint) {
        try {
            URL url = new URL("https://api.github.com/" + endpoint);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");

            // adds api key authentication if available
            String apiKey = ConfigUtil.getGitHubApiKey();
            if (apiKey != null && !apiKey.isEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            return response.toString();
        } catch (Exception e) {
            System.err.println("Error making GitHub API request: " + e.getMessage());
            return null;
        }
    }

    public void fetchRecentCommits(Repo repo) {
        try {
            String endpoint = String.format("repos/%s/%s/commits?per_page=50",
                    repo.getOwnerLogin(), repo.getName());
            String response = makeGitHubApiRequest(endpoint);

            if (response != null) {
                parseCommitsFromResponse(repo, response);
            } else {
                repo.setRecentCommits(new ArrayList<>());
            }
        } catch (Exception e) {
            System.err.println("Error fetching commits for " + repo.getName() + ": " + e.getMessage());
            repo.setRecentCommits(new ArrayList<>());
        }
    }

    private void parseCommitsFromResponse(Repo repo, String response) {
        try {
            com.google.gson.Gson gson = new com.google.gson.Gson();
            com.google.gson.JsonArray commitsArray = gson.fromJson(response, com.google.gson.JsonArray.class);

            List<Commit> commits = new ArrayList<>();
            for (com.google.gson.JsonElement element : commitsArray) {
                com.google.gson.JsonObject commitObj = element.getAsJsonObject();
                Commit commit = new Commit();

                // extracts basic commit info
                commit.setSha(commitObj.get("sha").getAsString());
                
                // extracts commit message from nested commit object
                commit.setMessage(commitObj.getAsJsonObject("commit").get("message").getAsString());

                // fetches modified files for this commit
                fetchCommitFiles(repo, commit);

                commits.add(commit);
            }

            repo.setRecentCommits(commits);
        } catch (Exception e) {
            System.err.println("Error parsing commits: " + e.getMessage());
        }
    }

    private void fetchCommitFiles(Repo repo, Commit commit) {
        try {
            // gets the list of files modified in this commit
            String endpoint = String.format("repos/%s/%s/commits/%s",
                    repo.getOwnerLogin(), repo.getName(), commit.getSha());
            String response = makeGitHubApiRequest(endpoint);

            if (response != null) {
                com.google.gson.Gson gson = new com.google.gson.Gson();
                com.google.gson.JsonObject commitObj = gson.fromJson(response, com.google.gson.JsonObject.class);
                com.google.gson.JsonArray filesArray = commitObj.getAsJsonArray("files");

                for (com.google.gson.JsonElement fileElement : filesArray) {
                    com.google.gson.JsonObject fileObj = fileElement.getAsJsonObject();
                    String filename = fileObj.get("filename").getAsString();
                    commit.addModifiedFile(filename);
                }
            }
        } catch (Exception e) {
            System.err.println("Error fetching commit files: " + e.getMessage());
        }
    }

    public void fetchForks(Repo repo) {
        try {
            String endpoint = String.format("repos/%s/%s/forks?per_page=20&sort=newest",
                    repo.getOwnerLogin(), repo.getName());
            String response = makeGitHubApiRequest(endpoint);

            if (response != null) {
                parseForksFromResponse(repo, response);
            } else {
                repo.setForks(new ArrayList<>());
            }
        } catch (Exception e) {
            System.err.println("Error fetching forks for " + repo.getName() + ": " + e.getMessage());
            repo.setForks(new ArrayList<>());
        }
    }

    private void parseForksFromResponse(Repo repo, String response) {
        try {
            com.google.gson.Gson gson = new com.google.gson.Gson();
            com.google.gson.JsonArray forksArray = gson.fromJson(response, com.google.gson.JsonArray.class);

            List<Repo> forks = new ArrayList<>();
            for (com.google.gson.JsonElement element : forksArray) {
                com.google.gson.JsonObject forkObj = element.getAsJsonObject();
                Repo fork = new Repo();

                fork.setName(forkObj.get("name").getAsString());
                fork.setOwnerLogin(forkObj.getAsJsonObject("owner").get("login").getAsString());
                fork.setHtmlUrl(forkObj.get("html_url").getAsString());
                fork.setStarCount(forkObj.get("stargazers_count").getAsInt());
                fork.setForksCount(forkObj.get("forks_count").getAsInt());
                fork.setOpenIssuesCount(forkObj.get("open_issues_count").getAsInt());

                fetchCommitCount(fork);

                forks.add(fork);
            }

            repo.setForks(forks);
        } catch (Exception e) {
            System.err.println("Error parsing forks: " + e.getMessage());
        }
    }

    private void fetchCommitCount(Repo repo) {
        try {
            // gets commits from last 30 days
            String since = java.time.Instant.now()
                    .minus(30, java.time.temporal.ChronoUnit.DAYS)
                    .toString();

            String endpoint = String.format("repos/%s/%s/commits?per_page=100&since=%s",
                    repo.getOwnerLogin(), repo.getName(), since);
            String response = makeGitHubApiRequest(endpoint);

            if (response != null) {
                com.google.gson.Gson gson = new com.google.gson.Gson();
                com.google.gson.JsonArray commitsArray = gson.fromJson(response, com.google.gson.JsonArray.class);
                repo.setCommitCount(commitsArray.size());
            } else {
                repo.setCommitCount(0);
            }
        } catch (Exception e) {
            System.err.println("Error fetching commit count for " + repo.getName() + ": " + e.getMessage());
            repo.setCommitCount(0);
        }
    }

    public void saveCommitDataToFile(Repo repo, String language) {
        try {
            // removes slashes and other invalid chars
            String sanitizedLanguage = language.toLowerCase()
                    .replace("/", "_")
                    .replace("+", "plus")
                    .replace(" ", "_")
                    .replace("\\", "_");
            String filename = String.format("commits/commits_%s_%s.json", sanitizedLanguage, repo.getName());
            File file = new File(filename);

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            JsonObject repoData = new JsonObject();

            // builds json structure for repo data
            repoData.addProperty("name", repo.getName());
            repoData.addProperty("ownerLogin", repo.getOwnerLogin());
            repoData.addProperty("language", repo.getLanguage());

            JsonArray commitsArray = new JsonArray();
            for (Commit commit : repo.getRecentCommits()) {
                JsonObject commitObj = new JsonObject();
                commitObj.addProperty("sha", commit.getSha());
                commitObj.addProperty("message", commit.getMessage());

                JsonArray filesArray = new JsonArray();
                for (String fileName : commit.getModifiedFiles()) {
                    filesArray.add(fileName);
                }
                commitObj.add("modifiedFiles", filesArray);
                commitsArray.add(commitObj);
            }
            repoData.add("commits", commitsArray);

            JsonArray forksArray = new JsonArray();
            for (Repo fork : repo.getForks()) {
                JsonObject forkObj = new JsonObject();
                forkObj.addProperty("name", fork.getName());
                forkObj.addProperty("ownerLogin", fork.getOwnerLogin());
                forkObj.addProperty("commitCount", fork.getCommitCount());
                forksArray.add(forkObj);
            }
            repoData.add("forks", forksArray);

            try (FileWriter writer = new FileWriter(file)) {
                gson.toJson(repoData, writer);
            }

            System.out.println("Saved commit data for " + repo.getName() + " to " + filename);
        } catch (Exception e) {
            System.err.println("Error saving commit data for " + repo.getName() + ": " + e.getMessage());
        }
    }

    public boolean loadCommitDataFromFile(Repo repo, String language) {
        try {
            // removes slashes and other invalid chars
            String sanitizedLanguage = language.toLowerCase()
                    .replace("/", "_")
                    .replace("+", "plus")
                    .replace(" ", "_")
                    .replace("\\", "_");
            String filename = String.format("commits/commits_%s_%s.json", sanitizedLanguage, repo.getName());
            File file = new File(filename);

            if (!file.exists()) {
                return false;
            }

            // reconstructs fork objects from json
            Gson gson = new Gson();
            try (FileReader reader = new FileReader(file)) {
                JsonObject repoData = gson.fromJson(reader, JsonObject.class);

                JsonArray commitsArray = repoData.getAsJsonArray("commits");
                List<Commit> commits = new ArrayList<>();
                for (int i = 0; i < commitsArray.size(); i++) {
                    JsonObject commitObj = commitsArray.get(i).getAsJsonObject();
                    Commit commit = new Commit();
                    commit.setSha(commitObj.get("sha").getAsString());
                    commit.setMessage(commitObj.get("message").getAsString());

                    JsonArray filesArray = commitObj.getAsJsonArray("modifiedFiles");
                    for (int j = 0; j < filesArray.size(); j++) {
                        commit.addModifiedFile(filesArray.get(j).getAsString());
                    }
                    commits.add(commit);
                }
                repo.setRecentCommits(commits);

                JsonArray forksArray = repoData.getAsJsonArray("forks");
                List<Repo> forks = new ArrayList<>();
                for (int i = 0; i < forksArray.size(); i++) {
                    JsonObject forkObj = forksArray.get(i).getAsJsonObject();
                    Repo fork = new Repo();
                    fork.setName(forkObj.get("name").getAsString());
                    fork.setOwnerLogin(forkObj.get("ownerLogin").getAsString());
                    fork.setCommitCount(forkObj.get("commitCount").getAsInt());
                    forks.add(fork);
                }
                repo.setForks(forks);

                System.out.println("Loaded cached commit data for " + repo.getName());
                return true;
            }
        } catch (Exception e) {
            System.err.println("Error loading commit data for " + repo.getName() + ": " + e.getMessage());
            return false;
        }
    }

    public void fetchRecentCommitsWithCache(Repo repo, String language) {
        // tries to load from cache first, fetches from api only if cache miss
        if (loadCommitDataFromFile(repo, language)) {
            return;
        }

        System.out.println("No cached data found for " + repo.getName() + ", fetching from API...");
        fetchRecentCommits(repo);
        fetchForks(repo);

        saveCommitDataToFile(repo, language);
    }

    public void fetchIssues(Repo repo) {
        try {
            String endpoint = String.format("repos/%s/%s/issues?state=open&per_page=10",
                    repo.getOwnerLogin(), repo.getName());
            String response = makeGitHubApiRequest(endpoint);

            if (response != null) {
                parseIssuesFromResponse(repo, response);
            } else {
                repo.setIssues(new ArrayList<>());
            }
        } catch (Exception e) {
            System.err.println("Error fetching issues for " + repo.getName() + ": " + e.getMessage());
            repo.setIssues(new ArrayList<>());
        }
    }

    private void parseIssuesFromResponse(Repo repo, String response) {
        try {
            com.google.gson.Gson gson = new com.google.gson.Gson();
            com.google.gson.JsonArray issuesArray = gson.fromJson(response, com.google.gson.JsonArray.class);

            List<Issue> issues = new ArrayList<>();
            for (com.google.gson.JsonElement element : issuesArray) {
                com.google.gson.JsonObject issueObj = element.getAsJsonObject();
                Issue issue = new Issue();

                issue.setTitle(issueObj.has("title") ? issueObj.get("title").getAsString() : "");
                issue.setBody(issueObj.has("body") && !issueObj.get("body").isJsonNull()
                        ? issueObj.get("body").getAsString() : "");
                issue.setState(issueObj.has("state") ? issueObj.get("state").getAsString() : "");

                // parses iso 8601 date strings to java.util.Date objects
                if (issueObj.has("created_at") && !issueObj.get("created_at").isJsonNull()) {
                    try {
                        String createdAtStr = issueObj.get("created_at").getAsString();
                        issue.setCreatedAt(java.util.Date.from(java.time.Instant.parse(createdAtStr)));
                    } catch (Exception e) {
                        System.err.println("Error parsing created_at date: " + e.getMessage());
                    }
                }

                if (issueObj.has("updated_at") && !issueObj.get("updated_at").isJsonNull()) {
                    try {
                        String updatedAtStr = issueObj.get("updated_at").getAsString();
                        issue.setUpdatedAt(java.util.Date.from(java.time.Instant.parse(updatedAtStr)));
                    } catch (Exception e) {
                        System.err.println("Error parsing updated_at date: " + e.getMessage());
                    }
                }

                issues.add(issue);
            }

            repo.setIssues(issues);
        } catch (Exception e) {
            System.err.println("Error parsing issues: " + e.getMessage());
        }
    }
}