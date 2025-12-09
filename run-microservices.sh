#!/bin/bash

# ECS160 HW4 - Spring Boot Microservices Launcher
# This script helps run all three microservices

echo "======================================"
echo "ECS160 HW4 - Spring Boot Microservices"
echo "======================================"
echo ""

# Check if we're in the right directory
if [ ! -d "microservices" ]; then
    echo "Error: Please run this script from the ECS160_HW4 directory"
    exit 1
fi

# Function to check if a port is in use
check_port() {
    local port=$1
    if lsof -Pi :$port -sTCP:LISTEN -t >/dev/null 2>&1; then
        return 0  # Port is in use
    else
        return 1  # Port is free
    fi
}

# Check if ports are already in use
echo "Checking ports..."
if check_port 30000; then
    echo "⚠️  Port 30000 is already in use (Issue Summarizer)"
fi
if check_port 30001; then
    echo "⚠️  Port 30001 is already in use (Bug Finder)"
fi
if check_port 30002; then
    echo "⚠️  Port 30002 is already in use (Issue Comparator)"
fi
echo ""

echo "Choose an option:"
echo "  1) Run Issue Summarizer (port 30000)"
echo "  2) Run Bug Finder (port 30001)"
echo "  3) Run Issue Comparator (port 30002)"
echo "  4) Run all three microservices (requires 3 terminals)"
echo "  5) Build all microservices"
echo "  6) Test microservices"
echo ""
read -p "Enter choice [1-6]: " choice

case $choice in
    1)
        echo "Starting Issue Summarizer on port 30000..."
        cd microservices/issue-summarizer
        mvn spring-boot:run
        ;;
    2)
        echo "Starting Bug Finder on port 30001..."
        cd microservices/bug-finder
        mvn spring-boot:run
        ;;
    3)
        echo "Starting Issue Comparator on port 30002..."
        cd microservices/issue-comparator
        mvn spring-boot:run
        ;;
    4)
        echo ""
        echo "To run all three microservices, you need 3 separate terminals:"
        echo ""
        echo "Terminal 1:"
        echo "  cd microservices/issue-summarizer && mvn spring-boot:run"
        echo ""
        echo "Terminal 2:"
        echo "  cd microservices/bug-finder && mvn spring-boot:run"
        echo ""
        echo "Terminal 3:"
        echo "  cd microservices/issue-comparator && mvn spring-boot:run"
        echo ""
        ;;
    5)
        echo "Building all microservices..."
        mvn clean install -DskipTests
        ;;
    6)
        echo ""
        echo "Testing microservices (make sure they're running first)..."
        echo ""

        echo "Testing Issue Summarizer (port 30000)..."
        curl -s "http://localhost:30000/summarize_issue?input=%7B%22description%22%3A%22Test%22%7D" | head -c 100
        echo ""

        echo "Testing Bug Finder (port 30001)..."
        curl -s "http://localhost:30001/find_bugs?input=%7B%22filename%22%3A%22test.c%22%2C%22content%22%3A%22int%20main()%20%7Breturn%200%3B%7D%22%7D" | head -c 100
        echo ""

        echo "Testing Issue Comparator (port 30002)..."
        curl -s "http://localhost:30002/check_equivalence?input=%7B%22list1%22%3A%5B%5D%2C%22list2%22%3A%5B%5D%7D" | head -c 100
        echo ""
        ;;
    *)
        echo "Invalid choice"
        exit 1
        ;;
esac
