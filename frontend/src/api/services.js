import client from './client.js';

export const authApi = {
  login: (payload) => client.post('/auth/login', payload),
  register: (payload, role) => client.post(`/auth/register?role=${encodeURIComponent(role || '')}`, payload)
};

export const orderApi = {
  list: () => client.get('/orders'),
  create: (payload) => client.post('/orders', payload),
  createPlan: (orderId) => client.post(`/orders/${orderId}/create-plan`),
  routeToWarehouse: (orderId, payload = {}) => client.post(`/orders/${orderId}/route-to-warehouse`, payload),
  warehouseReview: (orderId, payload = {}) => client.post(`/orders/${orderId}/warehouse-review`, payload),
  warehouseShip: (orderId, payload = {}) => client.post(`/orders/${orderId}/warehouse-ship`, payload),
  productionComplete: (orderId, payload = {}) => client.post(`/orders/${orderId}/production-complete`, payload),
  salesDecision: (orderId, decision, payload = {}) => client.post(`/orders/${orderId}/sales-decision?decision=${encodeURIComponent(decision)}`, payload),
  updateSalesStatus: (orderId, status) => client.post(`/orders/${orderId}/sales-status?status=${encodeURIComponent(status)}`)
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
  create: (payload) => client.post('/products', payload),
  update: (id, payload) => client.put(`/products/${id}`, payload),
  remove: (id) => client.delete(`/products/${id}`)
};

export const customerApi = {
  listOrders: (customerId) => client.get(`/customer/${customerId}/orders`),
  listMyOrders: () => client.get('/customer/me/orders'),
  createOrder: (payload) => client.post('/customer/orders', payload),
  getOrder: (orderId) => client.get(`/customer/orders/${orderId}`)
};

