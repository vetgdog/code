import axios from 'axios';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

const apiBaseUrl = process.env.API_BASE_URL || 'http://127.0.0.1:8085/api/v1';
const wsUrl = process.env.WS_BASE_URL || 'http://127.0.0.1:8085/ws';
const runTag = Date.now();

const accounts = {
  customer: {
    email: process.env.E2E_CUSTOMER_EMAIL || `e2e_customer_${runTag}@example.com`,
    username: process.env.E2E_CUSTOMER_USERNAME || `e2e_customer_${runTag}`,
    fullName: 'E2E Customer',
    phone: '13800000001',
    password: process.env.E2E_PASSWORD || 'e2e_pass_123',
    role: 'ROLE_CUSTOMER'
  },
  sales: {
    email: process.env.E2E_SALES_EMAIL || `e2e_sales_${runTag}@example.com`,
    username: process.env.E2E_SALES_USERNAME || `e2e_sales_${runTag}`,
    fullName: 'E2E Sales',
    phone: '13800000002',
    password: process.env.E2E_PASSWORD || 'e2e_pass_123',
    role: 'ROLE_SALES_MANAGER'
  },
  warehouse: {
    email: process.env.E2E_WAREHOUSE_EMAIL || `e2e_warehouse_${runTag}@example.com`,
    username: process.env.E2E_WAREHOUSE_USERNAME || `e2e_warehouse_${runTag}`,
    fullName: 'E2E Warehouse',
    phone: '13800000003',
    password: process.env.E2E_PASSWORD || 'e2e_pass_123',
    role: 'ROLE_WAREHOUSE_MANAGER'
  },
  production: {
    email: process.env.E2E_PRODUCTION_EMAIL || `e2e_prod_${runTag}@example.com`,
    username: process.env.E2E_PRODUCTION_USERNAME || `e2e_prod_${runTag}`,
    fullName: 'E2E Production',
    phone: '13800000004',
    password: process.env.E2E_PASSWORD || 'e2e_pass_123',
    role: 'ROLE_PRODUCTION_MANAGER'
  }
};

const http = axios.create({
  baseURL: apiBaseUrl,
  timeout: 15000
});

const createAuthedClient = (token) =>
  axios.create({
    baseURL: apiBaseUrl,
    timeout: 15000,
    headers: { Authorization: `Bearer ${token}` }
  });

const ensureRegistered = async (account) => {
  try {
    await http.post(`/auth/register?role=${encodeURIComponent(account.role)}`, {
      email: account.email,
      username: account.username,
      fullName: account.fullName,
      phone: account.phone,
      password: account.password
    });
    console.log(`Registered: ${account.email} (${account.role})`);
  } catch (error) {
    const status = error?.response?.status;
    if (status === 400 || status === 409) {
      console.log(`Register skipped for ${account.email} (already exists).`);
      return;
    }
    throw new Error(`Register failed for ${account.email}: ${error.message}`);
  }
};

const login = async (account) => {
  const response = await http.post('/auth/login', {
    email: account.email,
    password: account.password
  });
  if (!response?.data?.token) {
    throw new Error(`Login token missing for ${account.email}`);
  }
  return response.data.token;
};

const createRealtimeCollector = () => {
  const events = [];
  let connectedResolve;
  const connected = new Promise((resolve) => {
    connectedResolve = resolve;
  });

  const client = new Client({
    webSocketFactory: () => new SockJS(wsUrl),
    reconnectDelay: 0,
    onConnect: () => {
      ['/topic/orders', '/topic/orders/sales', '/topic/orders/warehouse', '/topic/orders/production', '/topic/production'].forEach((topic) => {
        client.subscribe(topic, (frame) => {
          const body = JSON.parse(frame.body || '{}');
          events.push({
            topic,
            messageType: body?.messageType || null,
            payload: body?.payload ?? body,
            timestamp: body?.timestamp || new Date().toISOString()
          });
        });
      });
      connectedResolve();
    },
    onStompError: (frame) => {
      throw new Error(`Realtime broker error: ${frame?.headers?.message || 'unknown'}`);
    }
  });

  client.activate();

  const waitFor = async (predicate, timeoutMs, label) => {
    const startedAt = Date.now();
    while (Date.now() - startedAt < timeoutMs) {
      const found = events.find(predicate);
      if (found) {
        return found;
      }
      await new Promise((resolve) => setTimeout(resolve, 150));
    }
    throw new Error(`Timed out waiting for event: ${label}`);
  };

  return {
    connected,
    waitFor,
    snapshot: () => events.slice(),
    close: async () => client.deactivate()
  };
};

const findWarehouseId = async (warehouseClient) => {
  if (process.env.E2E_WAREHOUSE_ID) {
    return Number(process.env.E2E_WAREHOUSE_ID);
  }
  const inventoryResponse = await warehouseClient.get('/inventory');
  const firstWarehouseId = inventoryResponse.data?.find((row) => row?.warehouse?.id)?.warehouse?.id;
  if (firstWarehouseId) {
    return Number(firstWarehouseId);
  }
  throw new Error('No warehouse id available. Set E2E_WAREHOUSE_ID before running this script.');
};

const run = async () => {
  console.log(`API: ${apiBaseUrl}`);
  console.log(`WS : ${wsUrl}`);

  await ensureRegistered(accounts.customer);
  await ensureRegistered(accounts.sales);
  await ensureRegistered(accounts.warehouse);
  await ensureRegistered(accounts.production);

  const customerToken = await login(accounts.customer);
  const salesToken = await login(accounts.sales);
  const warehouseToken = await login(accounts.warehouse);
  const productionToken = await login(accounts.production);

  const customerClient = createAuthedClient(customerToken);
  const salesClient = createAuthedClient(salesToken);
  const warehouseClient = createAuthedClient(warehouseToken);
  const productionClient = createAuthedClient(productionToken);

  const realtime = createRealtimeCollector();
  await realtime.connected;

  const productResponse = await salesClient.post('/products', {
    sku: `E2E-SKU-${runTag}`,
    name: `E2E Product ${runTag}`,
    unit: 'pcs',
    unitPrice: 10
  });
  const productId = productResponse?.data?.id;
  if (!productId) {
    throw new Error('Failed to create product for workflow test.');
  }

  const orderResponse = await customerClient.post('/customer/orders', {
    orderNo: `E2E-SO-${runTag}`,
    shippingAddress: 'E2E Test Address',
    items: [{ productId, quantity: 5 }]
  });
  const orderId = orderResponse?.data?.id;
  if (!orderId) {
    throw new Error('Customer order creation did not return order id.');
  }

  await realtime.waitFor(
    (event) => event.topic === '/topic/orders/sales' && event.messageType === 'ORDER_SUBMITTED' && Number(event.payload?.order?.id || event.payload?.id) === Number(orderId),
    15000,
    'sales ORDER_SUBMITTED'
  );

  await salesClient.post(`/orders/${orderId}/sales-decision?decision=ACCEPT`, {});

  await realtime.waitFor(
    (event) => event.topic === '/topic/orders/warehouse' && event.messageType === 'WAREHOUSE_ACTION_REQUIRED' && Number(event.payload?.order?.id || event.payload?.id) === Number(orderId),
    15000,
    'warehouse WAREHOUSE_ACTION_REQUIRED'
  );

  await warehouseClient.post(`/orders/${orderId}/warehouse-review`, { note: 'e2e shortage check' });

  await realtime.waitFor(
    (event) => event.topic === '/topic/orders/production' && event.messageType === 'ORDER_PRODUCTION_REQUIRED' && Number(event.payload?.order?.id || event.payload?.id) === Number(orderId),
    15000,
    'production ORDER_PRODUCTION_REQUIRED'
  );

  await productionClient.post(`/orders/${orderId}/production-complete`, { note: 'e2e done' });

  await realtime.waitFor(
    (event) => event.topic === '/topic/orders/warehouse' && event.messageType === 'ORDER_PRODUCTION_DONE' && Number(event.payload?.order?.id || event.payload?.id) === Number(orderId),
    15000,
    'warehouse ORDER_PRODUCTION_DONE'
  );

  const warehouseId = await findWarehouseId(warehouseClient);
  await warehouseClient.post('/inventory/stock-in', {
    transactionNo: `E2E-IN-${runTag}`,
    product: { id: productId },
    warehouse: { id: warehouseId },
    changeQuantity: 10,
    relatedType: 'E2E_WORKFLOW'
  });

  await warehouseClient.post(`/orders/${orderId}/warehouse-review`, { note: 'e2e stock available' });
  await warehouseClient.post(`/orders/${orderId}/warehouse-ship`, { note: 'e2e ship' });
  await salesClient.post(`/orders/${orderId}/sales-status?status=${encodeURIComponent('已完成')}`);

  const myOrders = await customerClient.get('/customer/me/orders');
  const finalOrder = (myOrders.data || []).find((item) => Number(item.id) === Number(orderId));
  if (!finalOrder || finalOrder.status !== '已完成') {
    throw new Error('Final order status verification failed.');
  }

  await realtime.close();

  console.log(`Workflow E2E success for order ${orderId}.`);
  console.log(`Captured realtime events: ${realtime.snapshot().length}`);
};

run().catch((error) => {
  console.error(`Workflow E2E failed: ${error.message}`);
  process.exit(1);
});

