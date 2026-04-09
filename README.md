# Stainless Steel Supply-Chain Backend (Spring Boot)

This project is a Spring Boot backend prototype for a stainless steel supply-chain collaboration system. It contains core entities, repositories, a simple MRP scheduler, and WebSocket configuration for realtime notifications.

Quick start (local development):

1. Create a MySQL database `stainless_db` and update `src/main/resources/application.yml` with your DB credentials.
2. Ensure you have Java 17 and Maven installed.
3. (Optionally) add MySQL driver dependency in `pom.xml` if your environment cannot fetch it automatically.

Build and run:

```powershell
mvn -DskipTests package
java -jar target/code-1.0-SNAPSHOT.jar
```

APIs:

- `POST /api/v1/orders` - create sales orders (JSON payload matching entity structure)
- `GET /api/v1/orders` - list sales orders

WebSocket:

- STOMP endpoint: `/ws` (SockJS enabled)
- Subscribe to `/topic/orders` or `/topic/mrp` for notifications

Notes:

This is an initial scaffold: authentication/authorization, validation, error handling, transactional boundaries, and complete business logic still need to be implemented.

Authentication (JWT)
--------------------

Endpoints:

- `POST /api/v1/auth/register?role=customer` — register user (role optional, recommended values: customer, supplier, PMC, WAREHOUSE, etc.). Example body:

  {
	"username": "alice",
	"password": "pass123"
  }

- `POST /api/v1/auth/login` — login to receive a JWT. Example body:

  {
	"username": "alice",
	"password": "pass123"
  }

The login response returns JSON {"token":"<jwt>"}. Include it in subsequent requests using the Authorization header:

```
Authorization: Bearer <jwt>
```

Role-based access (examples):
- `/api/v1/customer/**` endpoints require ROLE_CUSTOMER
- `/api/v1/procurement/**` endpoints require ROLE_SUPPLIER

Note: Role names are stored as `ROLE_CUSTOMER`, `ROLE_SUPPLIER`, etc. When using `register`, pass `role=customer` to create `ROLE_CUSTOMER`.

