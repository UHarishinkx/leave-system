# Leave Management System

This is my mini project for automating the employee leave request and approval process using Camunda 8 as the workflow engine. I built the frontend using React and the backend using Spring Boot.

## What it does

Employees can submit a leave request (annual, sick, etc.) and the manager gets it in their inbox to approve or reject. The whole flow runs as a BPMN process inside Camunda so the state is tracked properly without me having to manage it manually in the database.

## Tech stack I used

- **Camunda 8** (Self-Managed via Docker) – for the BPMN workflow engine
- **Spring Boot 3.5 / Java 21** – REST API backend + Zeebe job workers
- **React + Vite + TypeScript** – frontend UI
- **PostgreSQL** – stores leave requests and balances (via Docker)

## Project structure

```
leave-system/
├── backend/           # Spring Boot app, connects to Camunda via gRPC
├── frontend/          # React UI (Apply, My Requests, Manager Inbox tabs)
├── bpmn/              # BPMN process file and approval form
├── camunda/           # Camunda 8 docker-compose setup
└── docker-compose.yml # Full stack (postgres + backend + frontend)
```

## How to run locally

### 1. Start Camunda first
```bash
cd camunda
docker compose up -d
```
Wait about 1-2 mins for it to be healthy. Operate UI will be at http://localhost:8080 (login: demo / demo)

### 2. Start the backend
```bash
cd backend
mvn spring-boot:run
```
Runs on http://localhost:8081

### 3. Start the frontend
```bash
cd frontend
npm install
npm run dev
```
Runs on http://localhost:5173

## How I tested it

1. Opened the leave portal at localhost:5173
2. Filled in name, email, leave type and dates, clicked Submit
3. Checked "My requests" tab – showed as PENDING APPROVAL
4. Opened "Manager inbox" tab and approved the request
5. Status changed to APPROVED
6. Also checked Camunda Operate at localhost:8080 – the process instance was visible and running with no incidents

## Notes

- The BPMN file is in `bpmn/leave-request.bpmn` and also copied inside `backend/src/main/resources/bpmn/` so the backend deploys it automatically on startup
- No login/auth added – this is a learning project
- Local dev uses H2 in-memory DB; the Docker stack switches to PostgreSQL
