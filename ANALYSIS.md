# HW4 Analysis: Spring Boot Microservices Implementation

### Implementation
- Three independent Spring Boot applications
- Ports: 30000 (Issue Summarizer), 30001 (Bug Finder), 30002 (Issue Comparator)
- Spring Boot annotations: @SpringBootApplication, @RestController, @GetMapping
- Each microservice runs independently with embedded Tomcat

## Microservices

### Issue Summarizer (Port 30000)
Endpoint: GET /summarize_issue

Converts GitHub issues into structured bug reports using LLM.

Components:
- IssueSummarizerApplication.java
- IssueSummarizerController.java
- OllamaClient.java
- BugIssue.java

### Bug Finder (Port 30001)
Endpoint: GET /find_bugs

Analyzes C/C++ code to detect bugs using LLM.

Components:
- BugFinderApplication.java
- BugFinderController.java
- JSON parsing and cleaning for LLM responses
- JavaScript object notation fixing

### Issue Comparator (Port 30002)
Endpoint: GET /check_equivalence

Compares two lists of bug issues to find common bugs.

Components:
- IssueComparatorApplication.java
- IssueComparatorController.java
- Fallback comparison algorithm

## Code Changes

### Modified Files
- microservices/pom.xml - Added Spring Boot parent and dependencies
- microservices/*/pom.xml - Individual microservice POMs with mainClass configuration
- main-app/src/main/java/com/ecs160/hw/App.java - Updated to call three separate endpoints

### Unchanged Components
- persistence-framework/ - Redis persistence implementation
- main-app business logic - LLM prompts, JSON parsing, bug comparison algorithms
- BugIssue model structure
- OllamaClient implementation logic

### New Files Created
Each microservice module contains:
- Application.java - Spring Boot main class
- Controller.java - REST endpoint handler
- application.properties - Port configuration
- model/BugIssue.java - Data model (duplicated across modules)
- service/OllamaClient.java - LLM client (duplicated across modules)

## Build and Deployment

Build command:
```
mvn clean install -DskipTests
```

Run commands:
```
cd microservices/issue-summarizer && mvn spring-boot:run
cd microservices/bug-finder && mvn spring-boot:run
cd microservices/issue-comparator && mvn spring-boot:run
cd main-app && mvn exec:java
```

Build results: All modules compiled successfully.

## API Compatibility

The REST API remains backward compatible with HW2:
- Same endpoint names: /summarize_issue, /find_bugs, /check_equivalence
- Same request format: GET with URL-encoded JSON input parameter
- Same response format: JSON strings
- Only difference: Different ports (30000, 30001, 30002 instead of 8080)

## Advantages of Spring Boot Implementation

1. Independent deployment - Each service can be deployed separately
2. Port isolation - No port conflicts between services
3. Industry standard framework - Spring Boot is widely used in production
4. Built-in features - Health checks, metrics, auto-configuration
5. Better tooling - Spring Boot DevTools, Actuator support
6. Easier testing - Each service can be tested independently

## Trade-offs

1. More complex startup - Requires three separate processes
2. Port management - Must track multiple ports
3. Code duplication - BugIssue and OllamaClient duplicated across modules
4. Coordination overhead - Main app must ensure all services are running

## Bug Detection Results

Repository: repo-1764046390110
URL: https://github.com/bitcoin/bitcoin

The LLM did not successfully detect bugs that matched GitHub issues. The model identified code patterns it considered problematic, but these did not correspond to actual reported bugs. The LLM focused heavily on returning properly formatted JSON (as instructed in the prompts) but failed to identify real bugs.

Key observations:
1. LLM found 2 issues that were not actual bugs
2. No overlap between LLM-detected issues and GitHub-reported issues
3. LLM performance limited by training data on similar codebases
4. Prompt engineering trade-off between output format and bug detection accuracy

Conclusion: LLMs show limited effectiveness for bug detection in novel codebases. The model lacks understanding of project-specific context and relies on pattern matching from training data rather than true code comprehension.

## Technical Details

Spring Boot version: 3.2.0
Java version: 21
Tomcat version: 10.1.16
Maven version: 3.9+

Key Spring Boot features used:
- spring-boot-starter-web - REST API and embedded server
- @Autowired - Dependency injection
- @Service - Service layer components
- application.properties - External configuration
- spring-boot-maven-plugin - Executable JAR packaging

