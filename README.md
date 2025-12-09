# ECS160 HW2 - LLMs for Bug Detection

This project implements a bug detection validation system using LLMs (Large Language Models) to cross-reference findings with real GitHub issue reports.

## Project Structure

- `persistence-framework/` - Redis persistence framework with annotation-based persistence
- `microservice-framework/` - Microservice framework with annotation-based routing
- `application/` - Main application that uses both frameworks to analyze repositories

## Prerequisites

- Java 11 or higher
- Maven 3.6+
- Redis server running on localhost:6379
- Ollama server running on localhost:11434 with deepcoder:1.5b model installed

### Installing Ollama and the Model

1. Install Ollama from https://ollama.ai
2. Pull the deepcoder:1.5b model:
   ```bash
   ollama pull deepcoder:1.5b
   ```

## Setup

1. Make sure Redis is running:
   ```bash
   redis-server
   ```

2. Build and install the frameworks:
   ```bash
   ./script.sh
   ```

   This will:
   - Build the persistence framework
   - Build the microservice framework
   - Build the application
   - Run tests

## Usage

1. First, run HW1 to populate Redis with repository and issue data in the required format.

2. Create a `selected_repo.dat` file with:
   - First line: Repository ID (e.g., "repo-101")
   - Subsequent lines: Paths to C files to analyze (e.g., "src/main.c", "src/utils.c")

3. Run the application:
   ```bash
   cd application
   mvn exec:java
   ```

The application will:
- Load the selected repository from Redis
- Start microservices
- Summarize GitHub issues using Microservice A
- Find bugs in C files using Microservice B
- Compare and find common issues using Microservice C
- Generate an ANALYSIS.md file with results

## Components

### Part A: Redis Persistence Framework
- `@PersistableObject` - Class-level annotation
- `@PersistableField` - Field-level annotation
- `@Id` - ID field annotation
- `@LazyLoad` - Lazy loading annotation (extra credit)
- `RedisDB` - Main persistence class with `persist()` and `load()` methods

### Part B: Microservice Framework
- `@Microservice` - Class-level annotation
- `@Endpoint` - Method-level annotation with URL mapping
- `MicroserviceLauncher` - Launches HTTP server and routes requests

### Part C: Microservices
- **Issue Summarizer** (`summarize_issue`) - Summarizes GitHub issues
- **Bug Finder** (`find_bugs`) - Finds bugs in C files
- **Issue Comparator** (`check_equivalence`) - Compares two lists of issues

## Notes

- The microservice server runs on port 8080 by default
- Ollama must be running and accessible at http://localhost:11434
- Redis databases: 0 for repos, 1 for issues
- The ANALYSIS.md file is automatically generated after running the application

