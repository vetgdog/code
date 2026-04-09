# SteelOps Precision Frontend

A Vue 3 + Vite frontend converted from the Stitch HTML screens. It connects to the Spring Boot backend under `src/main/java/com/code`.

## Setup

1. Install dependencies.
2. Configure the backend URL if needed.
3. Run the dev server.

```bash
npm install
npm run dev
```

### API Base URL

By default the app uses `/api/v1` and Vite proxies `/api` to `http://localhost:8085`.
If your backend runs on a different port, set:

```bash
setx VITE_API_BASE_URL "http://localhost:8085/api/v1"
```

Restart the dev server after updating the environment.

### Realtime URL

The app subscribes to backend push messages using STOMP over SockJS.
Default websocket endpoint is `/ws` (proxied to `http://localhost:8085/ws`).

If needed, set:

```bash
setx VITE_WS_URL "http://localhost:8085/ws"
```

## Smoke Test

This checks if the backend is reachable (any HTTP status counts as reachable).

```bash
npm run test:api
```

This checks if realtime push endpoint is reachable.

```bash
npm run test:realtime
```

This runs a full e2e flow (register/login, create order, receive `/topic/orders` push):

```bash
npm run test:e2e
```

Optional env vars:

```bash
set API_BASE_URL=http://127.0.0.1:8085/api/v1
set WS_BASE_URL=http://127.0.0.1:8085/ws
set E2E_USERNAME=my_test_user
set E2E_PASSWORD=my_test_pass
```

If the backend is not running, you will see an `API unreachable` error.

