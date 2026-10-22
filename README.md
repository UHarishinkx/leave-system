# Leave Application System
Camunda 8 + Spring Boot 3.5 (Java 21) + React (Vite/TypeScript) + Docker.

| Step | Folder | Notes |
|---|---|---|
| 1 BPMN | `bpmn/` | `leave-request.bpmn`, `leave-approval.form` (copied to backend resources) |
| 2 Frontend | `frontend/` | `npm install && npm run dev` → http://localhost:5173 |
| 3 Backend | `backend/` | `mvn spring-boot:run` → http://localhost:8081 |
| 4 Testing | – | Scenarios A–F in the Step 4 guide |
| 5 Docker | `docker-compose.yml` | `docker compose up -d --build` → http://localhost:3000 |

Run order: Camunda (`camunda/`) → backend → frontend.
Local dev uses H2; the Docker stack uses PostgreSQL. No authentication (learning MVP).
