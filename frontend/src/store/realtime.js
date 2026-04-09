import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { reactive, readonly } from 'vue';

const state = reactive({
  connected: false,
  events: [],
  lastMessage: null,
  error: '',
  unreadCount: 0
});

let stompClient = null;

const normalizeMessage = (topic, body) => {
  const payload = body?.payload !== undefined ? body.payload : body;
  return {
    topic,
    payload,
    messageType: body?.messageType || null,
    entity: body?.entity || null,
    entityId: body?.entityId || payload?.id || null,
    timestamp: body?.timestamp || new Date().toISOString()
  };
};

const appendEvent = (event) => {
  state.lastMessage = event;
  state.events.unshift(event);
  state.unreadCount += 1;
  if (state.events.length > 50) {
    state.events.length = 50;
  }
};

const markAllRead = () => {
  state.unreadCount = 0;
};

const clearEvents = () => {
  state.events = [];
  state.lastMessage = null;
  state.unreadCount = 0;
};

const connect = () => {
  if (stompClient?.active || state.connected) {
    return;
  }

  state.error = '';

  try {
    stompClient = new Client({
      webSocketFactory: () => new SockJS(import.meta.env.VITE_WS_URL || '/ws'),
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        state.connected = true;

        stompClient.subscribe('/topic/orders', (message) => {
          const body = JSON.parse(message.body || '{}');
          appendEvent(normalizeMessage('/topic/orders', body));
        });

        stompClient.subscribe('/topic/production', (message) => {
          const body = JSON.parse(message.body || '{}');
          appendEvent(normalizeMessage('/topic/production', body));
        });
      },
      onStompError: (frame) => {
        state.error = frame?.headers?.message || 'Realtime broker error';
      },
      onWebSocketClose: () => {
        state.connected = false;
      }
    });

    stompClient.activate();
  } catch (error) {
    state.error = error instanceof Error ? error.message : 'Realtime init failed';
    state.connected = false;
    stompClient = null;
  }
};

const disconnect = () => {
  if (stompClient) {
    stompClient.deactivate();
    stompClient = null;
  }
  state.connected = false;
};

export const useRealtimeStore = () => ({
  state: readonly(state),
  connect,
  disconnect,
  markAllRead,
  clearEvents
});

