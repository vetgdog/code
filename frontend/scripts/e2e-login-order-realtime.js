import axios from 'axios';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

const apiBaseUrl = process.env.API_BASE_URL || 'http://127.0.0.1:8085/api/v1';
const wsUrl = process.env.WS_BASE_URL || 'http://127.0.0.1:8085/ws';

const username = process.env.E2E_USERNAME || `e2e_user_${Date.now()}`;
const password = process.env.E2E_PASSWORD || 'e2e_pass_123';

const http = axios.create({
  baseURL: apiBaseUrl,
  timeout: 15000
});

const waitForOrderEvent = (targetOrderNo) =>
  new Promise((resolve, reject) => {
    const timeoutMs = 15000;
    const timer = setTimeout(() => {
      reject(new Error(`No realtime order event received within ${timeoutMs}ms.`));
    }, timeoutMs);

    const client = new Client({
      webSocketFactory: () => new SockJS(wsUrl),
      reconnectDelay: 0,
      onConnect: () => {
        client.subscribe('/topic/orders', (message) => {
          try {
            const body = JSON.parse(message.body || '{}');
            const orderNo = body?.payload?.orderNo || body?.orderNo;
            if (orderNo === targetOrderNo) {
              clearTimeout(timer);
              client.deactivate();
              resolve(body);
            }
          } catch (error) {
            clearTimeout(timer);
            client.deactivate();
            reject(error);
          }
        });
      },
      onStompError: (frame) => {
        clearTimeout(timer);
        client.deactivate();
        reject(new Error(`Realtime broker error: ${frame?.headers?.message || 'unknown error'}`));
      },
      onWebSocketError: () => {
        clearTimeout(timer);
        client.deactivate();
        reject(new Error('WebSocket handshake failed.'));
      }
    });

    client.activate();
  });

const run = async () => {
  console.log(`Using API: ${apiBaseUrl}`);
  console.log(`Using WS : ${wsUrl}`);

  try {
    await http.post('/auth/register', { username, password });
    console.log(`Registered test user: ${username}`);
  } catch (error) {
    const status = error?.response?.status;
    if (status) {
      console.log(`Register returned status ${status}, continue with login.`);
    } else {
      throw new Error(`Register failed: ${error.message}`);
    }
  }

  const loginResponse = await http.post('/auth/login', { username, password });
  const token = loginResponse?.data?.token;
  if (!token) {
    throw new Error('Login succeeded but token is missing.');
  }

  const orderNo = `E2E-${Date.now()}`;
  const payload = {
    orderNo,
    createdBy: 1,
    items: []
  };

  const waitEventPromise = waitForOrderEvent(orderNo);

  await http.post('/orders', payload, {
    headers: {
      Authorization: `Bearer ${token}`
    }
  });

  const eventBody = await waitEventPromise;
  console.log(`E2E success: order ${orderNo} created and realtime event received.`);
  console.log(`Realtime event sample: ${JSON.stringify(eventBody).slice(0, 160)}...`);
};

run().catch((error) => {
  console.error(`E2E failed: ${error.message}`);
  process.exit(1);
});

