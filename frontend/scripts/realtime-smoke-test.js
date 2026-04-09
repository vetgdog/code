import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

const wsUrl = process.env.WS_BASE_URL || 'http://127.0.0.1:8085/ws';

const run = async () => {
  const timeoutMs = 8000;

  await new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      reject(new Error(`Realtime unreachable: timeout after ${timeoutMs}ms`));
    }, timeoutMs);

    const client = new Client({
      webSocketFactory: () => new SockJS(wsUrl),
      reconnectDelay: 0,
      onConnect: () => {
        clearTimeout(timer);
        client.deactivate();
        resolve();
      },
      onStompError: (frame) => {
        clearTimeout(timer);
        reject(new Error(`Realtime broker error: ${frame?.headers?.message || 'unknown error'}`));
      },
      onWebSocketError: () => {
        clearTimeout(timer);
        reject(new Error('Realtime websocket handshake failed'));
      }
    });

    client.activate();
  });

  console.log('Realtime reachable (websocket endpoint connected).');
};

run().catch((error) => {
  console.error(error.message);
  process.exit(1);
});

