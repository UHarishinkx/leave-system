# Leave Management System

A leave request and approval system built with Camunda 8, Spring Boot, and React.

## Stack
- Camunda 8 (Docker) – workflow engine
- Spring Boot backend
- React with Vite – frontend
- PostgreSQL – database

## How to run

```bash
# 1. Start Camunda
cd camunda && docker compose up -d

# 2. Start backend
cd backend && mvn spring-boot:run

# 3. Start frontend
cd frontend && npm install && npm run dev
```

Frontend → http://localhost:5173  
Camunda Operate → http://localhost:8080 (demo / demo)
