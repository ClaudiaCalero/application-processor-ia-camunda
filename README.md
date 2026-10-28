AI-Powered Request Processing

**A request management application built with Java, Spring Boot, Camunda 8, PostgreSQL and a locally running AI model.**

## About the Project

Tramitador is a backend application designed to receive, classify and process requests written in natural language.

The application combines a REST API, a relational database, an AI-powered classification component and a BPMN workflow engine. Its goal is to automate repetitive processing tasks while allowing human intervention when a request requires further review.

The project explores how these technologies can work together in a practical business application.

## Key Features

- **REST API:** Receive requests and retrieve stored request information.
- **AI-powered classification:** Use a locally running language model to identify the type of request and generate a short summary.
- **Request persistence:** Store requests and their processing results in PostgreSQL.
- **Workflow orchestration:** Use Camunda 8 and BPMN to coordinate the request lifecycle.
- **Human review:** Route requests for manual review based on classification confidence or request category.
- **Processing status tracking:** Track requests as they move through the workflow.

> **Project status:** In development. The backend API, PostgreSQL persistence, local AI classification and Camunda workflow integration are being developed and tested. The complete human-review flow and additional application features remain under development.

## Technology Stack

| Technology | Purpose |
|---|---|
| Java | Backend programming language |
| Spring Boot | Application framework and REST API |
| Spring Data JPA / Hibernate | Database persistence |
| PostgreSQL | Relational database |
| Camunda 8 | Workflow orchestration and BPMN |
| Ollama | Local AI model execution |
| Qwen 2.5 3B | Request classification and summarisation |
| Maven | Dependency and build management |
| Camunda Modeler | BPMN process design |

## How It Works

The intended request lifecycle is:

1. **Request submission:** A client submits a request through the REST API.
2. **Persistence:** The application stores the request in PostgreSQL.
3. **Workflow execution:** Camunda starts the corresponding BPMN process.
4. **AI classification:** A worker sends the request text to the local AI model and obtains a category, confidence score, summary and extracted amount.
5. **Review decision:** Requests that meet the configured review criteria are routed to a human task.
6. **Finalisation:** The workflow completes and the application updates the request status and review information.

The classification and workflow stages are implemented incrementally as part of the project.

## Architecture

```text
Client
  |
  v
Spring Boot REST API
  |
  v
PostgreSQL <----> Request Repository
  |
  v
Camunda 8 Workflow
  |
  v
Classification Worker
  |
  v
Ollama + Qwen 2.5 3B
  |
  v
Classification Result
  |
  v
Human Review (when required)
  |
  v
Finalisation Worker
  |
  v
Updated Request Status
```

The diagram represents the intended logical flow. Actual execution and variable mapping depend on the deployed BPMN process configuration.

## Prerequisites

Install and configure the following tools before running the application:

- Java Development Kit (JDK) 21
- Maven
- Docker Desktop
- PostgreSQL, or a PostgreSQL Docker container
- Ollama
- Camunda 8 Run compatible with the deployed process and application configuration
- Camunda Modeler, for inspecting or editing BPMN diagrams

The application uses the following local service ports by default:

| Service | Port |
|---|---:|
| Spring Boot API | `8081` |
| PostgreSQL | `5432` |
| Ollama | `11434` |
| Camunda 8 gateway | `8080` |

Make sure these ports are available and adjust the configuration if your environment uses different values.

## Getting Started

### 1. Clone the repository

```bash
git clone https://github.com/ClaudiaCalero/application-processor-ia-camunda.git
cd tramitador
```

Replace `https://github.com/ClaudiaCalero/application-processor-ia-camunda.git` with the URL of this repository.

### 2. Start PostgreSQL

If you use Docker, create a local PostgreSQL container with the database and credentials configured for the application.

For example:

```bash
docker run --name pg-tramitador \
  -e POSTGRES_DB=tramitador \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 \
  -d postgres
```

This command is intended for local development only. Change the example credentials before using any non-local environment, and configure the application with matching database settings.

If the container already exists, start it instead of creating another one:

```bash
docker start pg-tramitador
```

### 3. Start Ollama

Install Ollama and download the model:

```bash
ollama pull qwen2.5:3b
```

Make sure the Ollama service is running and the model is available locally.

The application expects Ollama to be reachable at:

```text
http://localhost:11434
```

AI inference time depends on the model, hardware and request complexity.

### 4. Start Camunda 8 Run

Start your local Camunda 8 environment and ensure the Orchestration Cluster is available.

Import or deploy the BPMN process using Camunda Modeler. The process must contain the service tasks and worker types expected by the Spring Boot application.

The application and Camunda must use compatible connection settings, including the appropriate gateway address and authentication configuration.

### 5. Configure the application

Review `src/main/resources/application.yml` and configure the database connection, Camunda connection, Ollama endpoint, server port and review threshold.

Example settings for local development may look like:

```yaml
server:
  port: 8081

tramitador:
  umbral: 0.8
```

Keep the actual database and Camunda connection settings consistent with your local environment. Use environment variables or another suitable secrets-management mechanism for credentials rather than committing secrets to Git.

### 6. Run the application

From the project root, execute:

```bash
mvn spring-boot:run
```

The REST API should be available at:

```text
http://localhost:8081
```

Check the application logs to confirm that Spring Boot starts successfully and that its required services are reachable.

## API Usage

### Create a request

**Endpoint:** `POST /api/requests`

Example request body:

```json
{
  "text": "I received an invoice for 150 euros that appears to be incorrect."
}
```

Example PowerShell command:

```powershell
$body = @{
    text = "I received an invoice for 150 euros that appears to be incorrect."
} | ConvertTo-Json

Invoke-RestMethod `
    -Uri "http://localhost:8081/api/requests" `
    -Method Post `
    -ContentType "application/json" `
    -Body $body
```

The API returns the saved request. Depending on the processing stage, classification fields may not be populated immediately.

### Retrieve requests

**Endpoint:** `GET /api/requests`

```powershell
Invoke-RestMethod -Uri "http://localhost:8081/api/requests"
```

This endpoint retrieves stored requests, ordered by their creation time.

### Example processing result

A successfully processed invoice request may contain information such as:

```json
{
  "category": "INVOICE",
  "score": 1.0,
  "summary": "Request about an invoice for 150 euros",
  "amount": 150.0,
  "status": "COMPLETED"
}
```

This is an illustrative example of a processing result, not a guaranteed response for every request. The actual output depends on the AI model and workflow execution.

## Human Review

Human review is an important part of the intended workflow.

The application can use a configurable confidence threshold to identify requests requiring additional attention. Requests classified as `OTHER` can also be routed for review.

The threshold is configured using:

```yaml
tramitador:
  umbral: 0.8
```

A request that requires review should enter the appropriate review status and wait at the corresponding Camunda user task until the review is completed.

The final implementation must ensure that the review outcome and relevant process variables are passed correctly to the finalisation step.

## Project Structure

The following is a conceptual overview of the main components:

```text
tramitador/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── ...
│   │   └── resources/
│   │       └── application.yml
│   └── test/
├── pom.xml
└── README.md
```

The Java source code contains the REST controllers, request domain model, repository, AI classification component and Camunda job workers. BPMN process files define the workflow and its tasks.

The exact package and file structure may evolve as the application develops.

## Testing

Run the project's Maven test suite with:

```bash
mvn test
```

Additional tests should cover:

- Request creation and retrieval.
- Database persistence and validation.
- AI classification results and error handling.
- Confidence-threshold and human-review decisions.
- Camunda worker execution and process-variable mapping.
- Final request status and review information.

Test coverage will expand as these components are implemented.

## Roadmap

- [x] Create and retrieve requests through the REST API.
- [x] Persist request information in PostgreSQL.
- [x] Test local AI classification with Ollama.
- [x] Design and deploy a BPMN workflow in Camunda 8.
- [x] Implement initial classification and finalisation workers.
- [ ] Complete and verify the human-review lifecycle.
- [ ] Improve error handling, validation and automated tests.
- [ ] Document API endpoints and configuration in greater detail.
- [ ] Develop a frontend using React and TypeScript.
- [ ] Improve deployment and production-readiness.

## Learning Objectives

This project is also a practical learning exercise focused on understanding how a backend application is designed, implemented and debugged.

It provides an opportunity to practise:

- Building REST APIs with Java and Spring Boot.
- Working with relational databases and JPA.
- Integrating a locally hosted language model.
- Designing BPMN workflows and asynchronous workers.
- Handling human-in-the-loop processing.
- Testing, debugging and documenting software.

AI assistants, tutorials and technical documentation may be used during development as learning resources. The goal is to understand the implementation, the reasoning behind technical decisions and how the different components interact.

## Disclaimer

This project is a work in progress intended for learning and demonstration purposes. It is not a production-ready request-processing system.

AI-generated classifications and extracted values may be inaccurate and should be validated before being used for consequential decisions.

