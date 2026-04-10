import client from './client.js';

export const authApi = {
  login: (payload) => client.post('/auth/login', payload),
  register: (payload, role) => client.post(`/auth/register?role=${encodeURIComponent(role || '')}`, payload)
};

export const orderApi = {
  list: () => client.get('/orders'),
  create: (payload) => client.post('/orders', payload),
  createPlan: (orderId) => client.post(`/orders/${orderId}/create-plan`)
};

export const productionApi = {
  createTask: (payload) => client.post('/production/tasks', payload),
  listByUser: (userId) => client.get(`/production/tasks/user/${userId}`),
  updateStatus: (taskId, status) => client.post(`/production/tasks/${taskId}/status?status=${encodeURIComponent(status)}`)
};

export const inventoryApi = {
  list: () => client.get('/inventory'),
  stockIn: (payload) => client.post('/inventory/stock-in', payload),
  stockOut: (payload) => client.post('/inventory/stock-out', payload)
};

export const procurementApi = {
  listRequests: () => client.get('/procurement/requests'),
  createOrder: (payload) => client.post('/procurement/orders', payload)
};

export const qualityApi = {
  getBatchByNo: (batchNo) => client.get(`/quality/batch/${encodeURIComponent(batchNo)}`),
  getRecords: (batchId) => client.get(`/quality/batch/${batchId}/records`)
};

export const productApi = {
  list: () => client.get('/products'),
  create: (payload) => client.post('/products', payload)
};

export const customerApi = {
  listOrders: (customerId) => client.get(`/customer/${customerId}/orders`),
  listMyOrders: () => client.get('/customer/me/orders'),
  createOrder: (payload) => client.post('/customer/orders', payload),
  getOrder: (orderId) => client.get(`/customer/orders/${orderId}`)
};

