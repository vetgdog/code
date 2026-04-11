import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { reactive, readonly } from 'vue';
import { normalizeRole } from '../constants/access.js';

const state = reactive({
  connected: false,
  events: [],
  lastMessage: null,
  error: '',
  unreadCount: 0
});

let stompClient = null;

const ROLE_TOPICS = {
  ROLE_CUSTOMER: ['/topic/orders'],
  ROLE_SALES_MANAGER: ['/topic/orders', '/topic/orders/sales'],
  ROLE_WAREHOUSE_MANAGER: ['/topic/orders', '/topic/orders/warehouse'],
  ROLE_PRODUCTION_MANAGER: ['/topic/orders', '/topic/orders/production', '/topic/production'],
  ROLE_ADMIN: ['/topic/orders', '/topic/orders/sales', '/topic/orders/warehouse', '/topic/orders/production', '/topic/production']
};

const resolveTopicsByRole = (role) => {
  const normalizedRole = normalizeRole(role);
  return ROLE_TOPICS[normalizedRole] || ['/topic/orders'];
};

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

const connect = (role = '') => {
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

        const topics = resolveTopicsByRole(role);
        topics.forEach((topic) => {
          stompClient.subscribe(topic, (message) => {
            const body = JSON.parse(message.body || '{}');
            appendEvent(normalizeMessage(topic, body));
          });
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

