package com.ecs160.hw;

import com.ecs160.microservices.model.BugIssue;
import com.ecs160.hw.model.IssueModel;
import com.ecs160.hw.model.RepoModel;
import com.ecs160.persistence.RedisDB;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class App {
    private static final String SELECTED_REPO_FILE = "selected_repo.dat";
    // Spring Boot microservices run on different ports
    private static final String ISSUE_SUMMARIZER_URL = "http://localhost:30000";
    private static final String BUG_FINDER_URL = "http://localhost:30001";
    private static final String ISSUE_COMPARATOR_URL = "http://localhost:30002";

    private RedisDB redisDB;
    private RedisDB issueRedisDB;
    private Gson gson;

    public App() {
        this.redisDB = new RedisDB("localhost", 6379, 0);
        this.issueRedisDB = new RedisDB("localhost", 6379, 1);
        this.gson = new Gson();
    }
    
    public static void main(String[] args) {
        App app = new App();
        
        // check for cleanup flag
        if (args.length > 0 && "--clean-test-data".equals(args[0])) {
            app.cleanTestData();
            return;
        }
        
        app.run();
    }
    
    public void run() {
        try {
            System.out.println("NOTE: Make sure all three Spring Boot microservices are running:");
            System.out.println("  - Issue Summarizer on port 30000");
            System.out.println("  - Bug Finder on port 30001");
            System.out.println("  - Issue Comparator on port 30002");
            System.out.println();

            System.out.println("Loading selected repository...");
            String repoId = loadSelectedRepo();
            if (repoId == null) {
                System.err.println("No repository selected. Please create " + SELECTED_REPO_FILE);
                return;
            }
            
            // load repo and issues from redis
            System.out.println("Loading repository from Redis...");
            RepoModel repo = loadRepoFromRedis(repoId);
            if (repo == null) {
                System.err.println("Repository not found: " + repoId);
                System.err.println("\nAvailable repositories in Redis:");
                listAvailableRepositories();
                System.err.println("\nPlease update " + SELECTED_REPO_FILE + " with a valid repository ID from the list above.");
                return;
            }
            
            // verify C/C++ repo
            if (repo.getUrl() != null && !repo.getUrl().isEmpty()) {
                System.out.println("Repository URL: " + repo.getUrl());
                System.out.println("Make sure this is a C/C++ repository with .c/.cpp files to analyze.");
            } else {
                System.err.println("Repository URL is null or empty. Cannot clone repository.");
                System.err.println("Repository ID: " + repo.getId());
                System.err.println("Author: " + (repo.getAuthorName() != null ? repo.getAuthorName() : "null"));
                System.err.println("Issues: " + (repo.getIssues() != null ? repo.getIssues() : "null"));
                return;
            }
            
            List<IssueModel> issues = loadIssuesFromRedis(repo.getIssues());
            System.out.println("Loaded " + issues.size() + " issues");

            System.out.println("Cloning repository...");
            String repoPath = cloneRepository(repo.getUrl());

            List<String> filesToAnalyze = loadFilesToAnalyze();
            
            // step 1: microservice A
            System.out.println("Summarizing GitHub issues...");
            List<BugIssue> issueList1 = summarizeIssues(issues);
            
            // step 2: microservice B
            System.out.println("Finding bugs in C files...");
            List<BugIssue> issueList2 = findBugsInFiles(repoPath, filesToAnalyze);
            
            // step 3: microservice C
            System.out.println("Comparing issues...");
            List<BugIssue> commonIssues = compareIssues(issueList1, issueList2);

            System.out.println("\nResults:");
            System.out.println("Issues from GitHub: " + issueList1.size());
            System.out.println("Bugs found by LLM: " + issueList2.size());
            System.out.println("Common issues: " + commonIssues.size());

            for (BugIssue issue : commonIssues) {
                String bugType = issue.getBug_type() != null ? issue.getBug_type() : "Unknown";
                int line = issue.getLine() > 0 ? issue.getLine() : -1;
                String desc = issue.getDescription() != null ? issue.getDescription() : "";
                System.out.println("- " + bugType + " at line " + line + ": " + desc);
            }

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            redisDB.close();
            issueRedisDB.close();
        }
    }
    
    
    private String loadSelectedRepo() {
        // try cwd first, then parent directory
        java.nio.file.Path filePath = Paths.get(SELECTED_REPO_FILE);
        if (!Files.exists(filePath)) {
            // try parent directory
            filePath = Paths.get("..", SELECTED_REPO_FILE).normalize();
            if (!Files.exists(filePath)) {
                // try absolute path from cwd
                filePath = Paths.get(System.getProperty("user.dir"), "..", SELECTED_REPO_FILE).normalize();
            }
        }
        
        try (BufferedReader reader = Files.newBufferedReader(filePath)) {
            return reader.readLine().trim();
        } catch (IOException e) {
            System.err.println("Error reading " + SELECTED_REPO_FILE + ": " + e.getMessage());
            System.err.println("Tried paths: " + Paths.get(SELECTED_REPO_FILE) + ", " + filePath);
            return null;
        }
    }
    
    private RepoModel loadRepoFromRedis(String repoId) {
        RepoModel repo = new RepoModel();
        repo.setId(repoId);
        RepoModel loaded = (RepoModel) redisDB.load(repo);
        return loaded;
    }
    
    private void listAvailableRepositories() {
        try {
            // get all keys that start with "repo-"
            java.util.Set<String> keys = redisDB.listKeys("repo-*");
            if (keys == null || keys.isEmpty()) {
                System.err.println("No repositories found in Redis.");
                System.err.println("Run the HW1 App.java to populate Redis with repositories.");
            } else {
                // filter out test repos
                java.util.Set<String> testRepos = new java.util.HashSet<>();
                java.util.Set<String> realRepos = new java.util.HashSet<>();
                
                for (String key : keys) {
                    if (key.contains("test")) {
                        testRepos.add(key);
                    } else {
                        realRepos.add(key);
                    }
                }
                
                if (!realRepos.isEmpty()) {
                    System.err.println("Found " + realRepos.size() + " repository(ies):");
                    for (String key : realRepos) {
                        System.err.println("  - " + key);
                    }
                }
                
                if (!testRepos.isEmpty()) {
                    System.err.println("Found " + testRepos.size() + " test repository(ies) that should be removed:");
                    for (String key : testRepos) {
                        System.err.println("  - " + key + " (test data)");
                    }
                    System.err.println("Run the application with --clean-test-data to remove test repositories.");
                }
                
                if (realRepos.isEmpty() && !testRepos.isEmpty()) {
                    System.err.println("\nNo real repositories found. Please run the HW1 App.java to populate Redis with repositories.");
                }
            }
        } catch (Exception e) {
            System.err.println("Error listing repositories: " + e.getMessage());
        }
    }
    
    private void cleanTestData() {
        try {
            java.util.Set<String> keys = redisDB.listKeys("repo-*");
            int cleaned = 0;
            for (String key : keys) {
                if (key.contains("test")) {
                    redisDB.deleteKey(key);
                    cleaned++;
                }
            }
            if (cleaned > 0) {
                System.out.println("Cleaned " + cleaned + " test repositories from Redis.");
            }
        } catch (Exception e) {
            System.err.println("Error cleaning test data: " + e.getMessage());
        }
    }
    
    private List<IssueModel> loadIssuesFromRedis(String issueIdsStr) {
        List<IssueModel> issues = new ArrayList<>();
        if (issueIdsStr == null || issueIdsStr.isEmpty()) {
            return issues;
        }
        
        String[] issueIds = issueIdsStr.split(",");
        for (String issueId : issueIds) {
            issueId = issueId.trim();
            if (!issueId.isEmpty()) {
                IssueModel issue = new IssueModel();
                issue.setId(issueId);
                IssueModel loaded = (IssueModel) issueRedisDB.load(issue);
                if (loaded != null) {
                    issues.add(loaded);
                }
            }
        }
        
        return issues;
    }
    
    private String cloneRepository(String repoUrl) throws IOException, InterruptedException {
        String repoName = extractRepoName(repoUrl);
        String cloneDir = "cloned_repos_hw2";
        
        File dir = new File(cloneDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        
        String clonePath = cloneDir + "/" + repoName;
        File repoDir = new File(clonePath);

        if (repoDir.exists()) {
            System.out.println("Repository already cloned at: " + clonePath);
            return clonePath;
        }
        
        String cloneCommand = "git clone --depth 1 " + repoUrl + " " + clonePath;
        Process process = Runtime.getRuntime().exec(cloneCommand);
        process.waitFor();
        
        if (process.exitValue() != 0) {
            throw new IOException("Failed to clone repository");
        }
        
        return clonePath;
    }
    
    private String extractRepoName(String repoUrl) {
        String[] parts = repoUrl.replace(".git", "").split("/");
        return parts[parts.length - 1];
    }
    
    private List<String> loadFilesToAnalyze() {
        List<String> files = new ArrayList<>();
        // try cwd first, then parent directory (project root)
        java.nio.file.Path filePath = Paths.get(SELECTED_REPO_FILE);
        if (!Files.exists(filePath)) {
            // try parent directory
            filePath = Paths.get("..", SELECTED_REPO_FILE).normalize();
            if (!Files.exists(filePath)) {
                // try absolute path from cwd
                filePath = Paths.get(System.getProperty("user.dir"), "..", SELECTED_REPO_FILE).normalize();
            }
        }
        
        try (BufferedReader reader = Files.newBufferedReader(filePath)) {
            String repoId = reader.readLine();
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    files.add(line);
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading files from " + SELECTED_REPO_FILE + ": " + e.getMessage());
        }
        return files;
    }
    
    private List<BugIssue> summarizeIssues(List<IssueModel> issues) throws IOException {
        List<BugIssue> summarizedIssues = new ArrayList<>();

        for (IssueModel issue : issues) {
            // convert IssueModel to json for microservice
            JsonObject issueJson = new JsonObject();
            issueJson.addProperty("description", issue.getDescription());
            issueJson.addProperty("date", issue.getDate() != null ?
                new SimpleDateFormat("yyyy-MM-dd").format(issue.getDate()) : "");

            String input = issueJson.toString();
            String response = callIssueSummarizer(input);

            if (response != null && !response.isEmpty()) {
                try {
                    BugIssue bugIssue = gson.fromJson(response, BugIssue.class);
                    summarizedIssues.add(bugIssue);
                } catch (Exception e) {
                    System.err.println("Error parsing summarized issue: " + e.getMessage());
                }
            }
        }

        return summarizedIssues;
    }
    
    private List<BugIssue> findBugsInFiles(String repoPath, List<String> files) throws IOException {
        List<BugIssue> allBugs = new ArrayList<>();
        
        if (files.isEmpty()) {
            System.out.println("No files specified to analyze.");
            return allBugs;
        }
        
        for (String filePath : files) {
            String fullPath = repoPath + "/" + filePath;
            File file = new File(fullPath);
            
            if (!file.exists() || !file.isFile()) {
                System.err.println("File not found: " + fullPath);
                // try to find similar files in repo
                System.err.println("  Searching for C/C++ files in repository...");
                findAndSuggestFiles(repoPath, filePath);
                continue;
            }

            String content = new String(Files.readAllBytes(Paths.get(fullPath)), StandardCharsets.UTF_8);
            
            // prepare input for microservice
            JsonObject inputJson = new JsonObject();
            inputJson.addProperty("filename", filePath);
            inputJson.addProperty("content", content);

            String input = inputJson.toString();
            String response = callBugFinder(input);

            if (response != null && !response.isEmpty()) {
                try {
                    BugIssue[] bugs = gson.fromJson(response, BugIssue[].class);
                    allBugs.addAll(Arrays.asList(bugs));
                } catch (Exception e) {
                    System.err.println("Error parsing bugs: " + e.getMessage());
                }
            }
        }
        
        return allBugs;
    }
    
    private List<BugIssue> compareIssues(List<BugIssue> list1, List<BugIssue> list2) throws IOException {
        if (list1.isEmpty() && list2.isEmpty()) {
            return new ArrayList<>();
        }
        
        JsonObject inputJson = new JsonObject();
        inputJson.add("list1", gson.toJsonTree(list1));
        inputJson.add("list2", gson.toJsonTree(list2));

        String input = inputJson.toString();
        String response = callIssueComparator(input);

        if (response != null && !response.isEmpty()) {
            try {
                BugIssue[] common = gson.fromJson(response, BugIssue[].class);
                List<BugIssue> result = new ArrayList<>();
                // filter out any empty or invalid issues
                for (BugIssue issue : common) {
                    if (issue != null &&
                        ((issue.getBug_type() != null && !issue.getBug_type().isEmpty()) ||
                         (issue.getDescription() != null && !issue.getDescription().isEmpty()))) {
                        result.add(issue);
                    }
                }
                return result;
            } catch (Exception e) {
                System.err.println("Error parsing common issues: " + e.getMessage());
            }
        }

        return new ArrayList<>();
    }
    
    private String callIssueSummarizer(String input) throws IOException {
        return callSpringBootMicroservice(ISSUE_SUMMARIZER_URL, "summarize_issue", input);
    }

    private String callBugFinder(String input) throws IOException {
        return callSpringBootMicroservice(BUG_FINDER_URL, "find_bugs", input);
    }

    private String callIssueComparator(String input) throws IOException {
        return callSpringBootMicroservice(ISSUE_COMPARATOR_URL, "check_equivalence", input);
    }

    private String callSpringBootMicroservice(String baseUrl, String endpoint, String input) throws IOException {
        try {
            String url = baseUrl + "/" + endpoint + "?input=" +
                java.net.URLEncoder.encode(input, StandardCharsets.UTF_8.toString());

            URL urlObj = new URL(url);
            HttpURLConnection conn = (HttpURLConnection) urlObj.openConnection();
            conn.setRequestMethod("GET");

            int responseCode = conn.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("Microservice returned error code: " + responseCode);
            }

            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            }

            return response.toString();
        } catch (Exception e) {
            System.err.println("Error calling microservice " + endpoint + " at " + baseUrl + ": " + e.getMessage());
            return null;
        }
    }
    
    private void findAndSuggestFiles(String repoPath, String requestedPath) {
        try {
            File repoDir = new File(repoPath);
            if (!repoDir.exists() || !repoDir.isDirectory()) {
                return;
            }
            
            java.util.List<String> foundFiles = new ArrayList<>();
            findCFiles(repoDir, foundFiles, 0, 5); // Limit to 5 suggestions
            
            if (!foundFiles.isEmpty()) {
                System.err.println("  Found these C/C++ files in the repository:");
                for (String found : foundFiles) {
                    String relativePath = new File(repoPath).toURI().relativize(new File(found).toURI()).getPath();
                    System.err.println("    - " + relativePath);
                }
                System.err.println("  Update selected_repo.dat with the correct file paths.");
            } else {
                System.err.println("  No C/C++ files found in this repository.");
                System.err.println("  This appears to be a Java repository. Please select a C/C++ repository from Redis.");
            }
        } catch (Exception e) {

        }
    }

    private void findCFiles(File dir, java.util.List<String> foundFiles, int depth, int maxDepth) {
        if (depth > maxDepth || foundFiles.size() >= 5) {
            return;
        }
        
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        
        for (File file : files) {
            if (file.isDirectory() && !file.getName().startsWith(".")) {
                findCFiles(file, foundFiles, depth + 1, maxDepth);
            } else if (file.isFile()) {
                String name = file.getName().toLowerCase();
                if (name.endsWith(".c") || name.endsWith(".cpp") || name.endsWith(".cc") || 
                    name.endsWith(".cxx") || name.endsWith(".h") || name.endsWith(".hpp")) {
                    foundFiles.add(file.getAbsolutePath());
                    if (foundFiles.size() >= 5) {
                        return;
                    }
                }
            }
        }
    }
}

