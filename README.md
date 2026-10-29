<img width="1902" height="560" alt="image" src="https://github.com/user-attachments/assets/a9c17fd9-e6d8-4cf5-972b-061c0ea54c91" />

# AI-Powered Request Processing

A full-stack request management application built with **Java 21, Spring Boot, React, TypeScript, PostgreSQL, Camunda 8, and Ollama**.

This project combines AI-powered request classification, BPMN workflow orchestration, and human-in-the-loop review to demonstrate how administrative requests can be processed automatically while keeping people involved when needed.

## Overview

Tramitador receives requests written in natural language, classifies them using a locally running language model, and coordinates their processing through Camunda 8.

Depending on the classification and configured workflow rules, a request can proceed through automatic processing or be routed to a human review task in Camunda Tasklist. The application tracks request status as the workflow progresses.

The project explores the integration of AI, backend development, database persistence, workflow automation, and a web-based user interface.

## Features

- **Request management:** Submit requests and retrieve stored records through a REST API and web interface.
- **AI-powered classification:** Use Ollama and Qwen 2.5 3B to classify natural-language requests.
- **Information extraction:** Extract relevant information, including monetary amounts when present.
- **Workflow orchestration:** Coordinate processing steps using Camunda 8 and BPMN.
- **Human-in-the-loop review:** Route requests requiring additional attention to human tasks in Camunda Tasklist.
- **Status tracking:** Display request categories, confidence scores, extracted amounts, and processing statuses.
- **Persistent storage:** Store request information and processing results in PostgreSQL.
- **Local AI inference:** Run the language model locally without relying on a paid cloud AI API.

## How It Works

1. **Request submission:** A user submits a request through the React frontend or REST API.
2. **Persistence:** Spring Boot stores the request in PostgreSQL.
3. **Workflow initiation:** Camunda 8 orchestrates the request-processing lifecycle.
4. **AI classification:** A Spring Boot worker sends the request text to Ollama and processes the model's response.
5. **Routing decision:** The workflow determines whether the request can proceed automatically or requires human review.
6. **Human review:** Requests requiring intervention wait for a user task to be completed in Camunda Tasklist.
7. **Finalisation:** The workflow continues, and the backend updates the request status.
8. **Frontend update:** The result becomes visible in the request management interface.

## Architecture

```text
                         User
                          |
                          v
                React + TypeScript
                     Frontend
                          |
                          v
                 Spring Boot REST API
                          |
                  +-------+-------+
                  |               |
                  v               v
              PostgreSQL      Camunda 8
                                  |
                                  v
                         BPMN Process Execution
                                  |
                                  v
                         Spring Boot Workers
                                  |
                                  v
                           Ollama + Qwen
                                  |
                                  v
                         Classification Result
                                  |
                                  v
                            BPMN Routing
                           /            \
                          v              v
                   Automatic         Human Review
                   Processing        Camunda Tasklist
                          \              /
                           v            v
                              Finalisation
                                   |
                                   v
                            Updated Status
```

## Workflow Design with Camunda Modeler

The request-processing lifecycle is modelled using BPMN 2.0 in Camunda Modeler. The workflow coordinates AI-based classification, automatic processing, and human review through Camunda Tasklist.

<p align="center">
  <img width="1160" alt="BPMN request-processing workflow designed in Camunda Modeler" src="https://github.com/user-attachments/assets/7d1e10d4-4219-429f-a4cc-27d56231ac48" />
</p>

The process routes requests according to the configured workflow rules. Requests requiring human intervention wait for review in Tasklist before the process continues.

## Technology Stack

| Technology | Purpose |
|---|---|
| Java 21 | Backend programming |
| Spring Boot | REST API and application services |
| Spring Data JPA / Hibernate | Database persistence |
| PostgreSQL | Relational data storage |
| Camunda 8 | BPMN workflow orchestration |
| Camunda Tasklist | Human task management |
| Camunda Modeler | BPMN process design |
| Ollama | Local language model execution |
| Qwen 2.5 3B | Request classification |
| React | Frontend interface |
| TypeScript | Frontend type safety |
| Vite | Frontend development and build tooling |
| Maven | Backend dependency management and builds |
| Docker | Local service containerisation |

## Example Use Cases

| Example request | Expected behaviour |
|---|---|
| Duplicate electricity bill charge with a reimbursement request | Classify as an invoice-related request and extract the amount when available |
| Complaint about an unresolved issue | Identify the complaint category |
| Request with an unfamiliar or ambiguous purpose | Route for human review when the workflow rules require it |
| Request requiring additional validation | Wait for the corresponding human task to be completed in Tasklist |

Classification results depend on the input text and model response. The predicted category and confidence score do not guarantee that a classification is correct.

## Getting Started

### Prerequisites

Install the following tools:

- JDK 21
- Docker Desktop
- Node.js and npm
- Ollama
- Camunda 8 Run compatible with the deployed BPMN process
- Git

### 1. Clone the repository

```bash
git clone https://github.com/ClaudiaCalero/application-processor-ia-camunda.git
cd application-processor-ia-camunda
```

### 2. Start PostgreSQL

If the existing development container is available, start it with:

```powershell
docker start pg-tramitador
```

Otherwise, create a PostgreSQL container using the database name, username, password, and port configured in the backend.

Ensure the database configuration matches your local environment.

### 3. Start Ollama

Download the model if it is not already installed:

```bash
ollama pull qwen2.5:3b
```

Ensure Ollama is running and the model is available locally.

Default Ollama endpoint:

```text
http://localhost:11434
```

### 4. Start Camunda 8 Run

Start your configured Camunda 8 Run environment and confirm that the Orchestration Cluster is healthy.

Use Camunda Modeler to inspect or deploy the BPMN process required by the application. The deployed process must match the worker types and configuration expected by the backend.

Open Camunda Tasklist to access tasks requiring human review.

### 5. Configure and start the backend

Check the application configuration for the PostgreSQL connection, Camunda connection, Ollama endpoint, and server port.

On Windows, run the backend from the project root:

```powershell
.\mvnw.cmd spring-boot:run
```

The REST API uses port `8081` in the local development setup.

### 6. Start the frontend

Open a separate terminal:

```powershell
cd frontend
npm install
npm run dev
```

Open the local URL printed by Vite, usually:

```text
http://localhost:5173
```

Keep the required backend services running while testing the application.

## API Endpoints

### Create a request

`POST /api/requests`

Example request body:

```json
{
  "text": "I received an electricity bill for €350 and believe I was charged twice."
}
```

PowerShell example:

```powershell
$body = @{
    text = "I received an electricity bill for €350 and believe I was charged twice."
} | ConvertTo-Json

Invoke-RestMethod `
    -Uri "http://localhost:8081/api/requests" `
    -Method Post `
    -ContentType "application/json" `
    -Body $body
```

### Retrieve requests

`GET /api/requests`

```powershell
Invoke-RestMethod -Uri "http://localhost:8081/api/requests"
```

The API returns stored requests and their available processing information.

## Human-in-the-Loop Processing

Not every request should be processed automatically.

When the configured workflow rules determine that additional review is needed, the process routes the request to a human task in Camunda Tasklist.

The human-review lifecycle is:

1. The request is classified.
2. The workflow routes it to a human task when required.
3. The process waits for the task to be completed.
4. The workflow continues and the backend updates the request status.

This approach combines AI-assisted processing with human oversight instead of treating every model prediction as a final decision.

## Local Development Ports

| Service | Default port |
|---|---:|
| React / Vite frontend | `5173` |
| Spring Boot API | `8081` |
| Camunda 8 gateway | `8080` |
| PostgreSQL | `5432` |
| Ollama | `11434` |

These are the ports used by the current local development setup. Actual values depend on the local configuration.

## Testing

Run the backend test suite:

```powershell
.\mvnw.cmd test
```

Build the frontend:

```powershell
cd frontend
npm run build
```

Useful areas for testing include:

- Request creation and retrieval.
- Persistence and validation.
- AI classification and extracted values.
- Routing decisions and confidence thresholds.
- Camunda worker execution and process variables.
- Human task completion and final status updates.
- Error handling when a dependent service is unavailable.

## Project Status

The application is under active development. Request submission, AI classification, workflow processing, human review through Tasklist, status updates, and the web interface have been exercised in the local development environment.

Potential future improvements include expanded automated tests, more robust error handling, improved validation, and additional integrations.

## Disclaimer

This project is intended for learning and demonstration purposes. It is not a production-ready request-processing system.

AI classifications and extracted information may be inaccurate. The application does not perform real financial transactions or issue actual reimbursements.

## Contact

**Claudia Calero**

- [GitHub](https://github.com/ClaudiaCalero)
- [LinkedIn](https://www.linkedin.com/in/claudia-calero/)

## License

This project is intended for educational and development purposes. Check the repository for the applicable license before reusing or distributing the code.


Further improvements may include expanded automated tests, more robust error handling, improved validation, and additional integrations.

## Disclaimer

This project is intended for learning and demonstration purposes. It is not a production-ready request-processing system.

AI classifications and extracted information may be inaccurate. The application does not perform real financial transactions or issue actual reimbursements.
