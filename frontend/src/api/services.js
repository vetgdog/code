import client from './client.js';

export const authApi = {
  login: (payload) => client.post('/auth/login', payload),
  register: (payload, role) => client.post(`/auth/register?role=${encodeURIComponent(role || '')}`, payload)
};

export const profileApi = {
  changePassword: (payload) => client.post('/profile/change-password', payload)
};

export const orderApi = {
  list: () => client.get('/orders'),
  listSalesRecords: (params = {}) => client.get('/orders/sales-records', { params }),
  listSalesRecordOverview: (params = {}) => client.get('/orders/sales-records/overview', { params }),
  exportSalesRecords: (params = {}) => client.get('/orders/sales-records/export', { params, responseType: 'blob' }),
  create: (payload) => client.post('/orders', payload),
  createPlan: (orderId) => client.post(`/orders/${orderId}/create-plan`),
  routeToWarehouse: (orderId, payload = {}) => client.post(`/orders/${orderId}/route-to-warehouse`, payload),
  warehouseReview: (orderId, payload = {}) => client.post(`/orders/${orderId}/warehouse-review`, payload),
  listPendingProductionStockIn: () => client.get('/orders/pending-production-stock-in'),
  warehouseShip: (orderId, payload = {}) => client.post(`/orders/${orderId}/warehouse-ship`, payload),
  warehouseStockIn: (orderId, payload = {}) => client.post(`/orders/${orderId}/warehouse-stock-in`, payload),
  productionComplete: (orderId, payload = {}) => client.post(`/orders/${orderId}/production-complete`, payload),
  salesDecision: (orderId, decision, payload = {}) => client.post(`/orders/${orderId}/sales-decision?decision=${encodeURIComponent(decision)}`, payload),
  updateSalesStatus: (orderId, status) => client.post(`/orders/${orderId}/sales-status?status=${encodeURIComponent(status)}`)
};

export const productionApi = {
  createTask: (payload) => client.post('/production/tasks', payload),
  listByUser: (userId) => client.get(`/production/tasks/user/${userId}`),
  listRecords: (params = {}) => client.get('/production/records', { params }),
  listRecordOverview: (params = {}) => client.get('/production/records/overview', { params }),
  listQualityAlerts: () => client.get('/production/quality-alerts'),
  listWeeklyPlans: () => client.get('/production/weekly-plans'),
  getCurrentWeeklyPlan: (params = {}) => client.get('/production/weekly-plans/current', { params }),
  generateWeeklyPlan: (params = {}) => client.post('/production/weekly-plans/generate', null, { params }),
  updateStatus: (taskId, status) => client.post(`/production/tasks/${taskId}/status?status=${encodeURIComponent(status)}`)
};

export const inventoryApi = {
  list: (params = {}) => client.get('/inventory', { params }),
  listWarehouses: () => client.get('/inventory/warehouses'),
  listTransactions: (params = {}) => client.get('/inventory/transactions', { params }),
  listAlerts: () => client.get('/inventory/alerts'),
  createAlertProductionPlan: (productId, payload = {}) => client.post(`/inventory/alerts/finished-goods/${productId}/production-plan`, payload),
  createAlertPurchaseRequest: (productId, payload = {}) => client.post(`/inventory/alerts/raw-materials/${productId}/purchase-request`, payload),
  stockIn: (payload) => client.post('/inventory/stock-in', payload),
  stockOut: (payload) => client.post('/inventory/stock-out', payload)
};

export const adminApi = {
  listRoles: () => client.get('/admin/roles'),
  listUsers: (params = {}) => client.get('/admin/users', { params }),
  createUser: (payload) => client.post('/admin/users', payload),
  updateUser: (userId, payload) => client.put(`/admin/users/${userId}`, payload)
};

export const procurementApi = {
  listSuppliers: () => client.get('/procurement/suppliers'),
  getDashboard: () => client.get('/procurement/dashboard'),
  listRequests: () => client.get('/procurement/requests'),
  listWeeklyPlans: () => client.get('/procurement/weekly-plans'),
  getCurrentWeeklyPlan: (params = {}) => client.get('/procurement/weekly-plans/current', { params }),
  generateWeeklyPlan: (params = {}) => client.post('/procurement/weekly-plans/generate', null, { params }),
  listOrders: (params = {}) => client.get('/procurement/orders', { params }),
  exportOrdersCsv: (params = {}) => client.get('/procurement/orders/export', { params: { ...params, format: 'csv' }, responseType: 'blob' }),
  exportOrdersExcel: (params = {}) => client.get('/procurement/orders/export', { params: { ...params, format: 'xlsx' }, responseType: 'blob' }),
  listPendingWarehouseReceipts: () => client.get('/procurement/orders/pending-warehouse-receipt'),
  createOrder: (payload) => client.post('/procurement/orders', payload),
  supplierDecision: (orderId, decision, payload = {}) => client.post(`/procurement/orders/${orderId}/supplier-decision?decision=${encodeURIComponent(decision)}`, payload),
  supplierShip: (orderId, payload = {}) => client.post(`/procurement/orders/${orderId}/supplier-ship`, payload),
  notifyWarehouse: (orderId, payload = {}) => client.post(`/procurement/orders/${orderId}/notify-warehouse`, payload),
  warehouseReceive: (orderId, payload = {}) => client.post(`/procurement/orders/${orderId}/warehouse-receive`, payload),
  listRawMaterials: (params = {}) => client.get('/procurement/raw-materials', { params }),
  getRawMaterial: (id) => client.get(`/procurement/raw-materials/${id}`),
  createRawMaterial: (payload) => client.post('/procurement/raw-materials', payload),
  downloadRawMaterialTemplate: () => client.get('/procurement/raw-materials/template', { responseType: 'blob' }),
  importRawMaterials: (formData) => client.post('/procurement/raw-materials/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
};

export const qualityApi = {
  listBatches: (params = {}) => client.get('/quality/batches', { params }),
  getBatchByNo: (batchNo) => client.get(`/quality/batch/${encodeURIComponent(batchNo)}`),
  getRecords: (batchId) => client.get(`/quality/batch/${batchId}/records`),
  listMyRecords: (params = {}) => client.get('/quality/my-records', { params }),
  inspectBatch: (batchId, payload) => client.post(`/quality/batch/${batchId}/inspect`, payload)
};

export const productApi = {
  list: (params = {}) => client.get('/products', { params }),
  create: (payload) => client.post('/products', payload),
  update: (id, payload) => client.put(`/products/${id}`, payload),
  remove: (id) => client.delete(`/products/${id}`)
};

export const customerApi = {
  listOrders: (customerId) => client.get(`/customer/${customerId}/orders`),
  listMyOrders: () => client.get('/customer/me/orders'),
  createOrder: (payload) => client.post('/customer/orders', payload),
  getOrder: (orderId) => client.get(`/customer/orders/${orderId}`),
  traceQuality: (orderNo) => client.get('/customer/quality-trace', { params: { orderNo } })
};

