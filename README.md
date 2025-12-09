# ECS160 HW4 - Spring Boot Microservices

## Prerequisites

- Java 21 or higher
- Maven 3.6+
- Redis server
- Ollama with deepcoder:1.5b model

## Setup

### 1. Install Ollama and Model
```bash
# Install Ollama from https://ollama.ai
ollama pull deepcoder:1.5b
```

### 2. Start Redis
```bash
redis-server
```

### 3. Build Project
```bash
cd /path/to/ECS160_HW4
mvn clean install -DskipTests
```

## Running the Application

### Start Microservices (3 separate terminals)

Terminal 1 - Issue Summarizer (Port 30000):
```bash
cd microservices/issue-summarizer
mvn spring-boot:run
```

Terminal 2 - Bug Finder (Port 30001):
```bash
cd microservices/bug-finder
mvn spring-boot:run
```

Terminal 3 - Issue Comparator (Port 30002):
```bash
cd microservices/issue-comparator
mvn spring-boot:run
```

Wait for all three to show: "Tomcat started on port XXXXX"

### Run Main Application (Terminal 4)

```bash
cd main-app
mvn exec:java
```

## Project Structure

```
ECS160_HW4/
├── persistence-framework/     # Part A: Redis persistence (unchanged)
├── microservices/            # Part C: Spring Boot microservices
│   ├── issue-summarizer/    # Port 30000
│   ├── bug-finder/          # Port 30001
│   └── issue-comparator/    # Port 30002
└── main-app/                # Part D: Main application
```

## Testing Microservices

Test Issue Summarizer:
```bash
curl "http://localhost:30000/summarize_issue?input=%7B%22description%22%3A%22Bug%22%7D"
```

Test Bug Finder:
```bash
curl "http://localhost:30001/find_bugs?input=%7B%22filename%22%3A%22test.c%22%2C%22content%22%3A%22int%20main()%7Breturn%200%3B%7D%22%7D"
```

Test Issue Comparator:
```bash
curl "http://localhost:30002/check_equivalence?input=%7B%22list1%22%3A%5B%5D%2C%22list2%22%3A%5B%5D%7D"
```

## Troubleshooting

### Port already in use
```bash
lsof -i :30000  # Find process
kill -9 <PID>   # Kill process
```

### Redis not responding
```bash
redis-cli ping  # Should return PONG
```

### Ollama not running
```bash
ollama serve
```
