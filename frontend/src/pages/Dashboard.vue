<template>
  <div class="space-y-6">
    <section class="grid grid-cols-1 md:grid-cols-4 gap-6">
      <div v-for="card in cards" :key="card.label" class="bg-white p-5 rounded-lg shadow-sm border border-outline-variant/10">
        <p class="text-on-surface-variant text-[10px] font-bold uppercase tracking-widest mb-1">{{ card.label }}</p>
        <div class="flex items-end justify-between gap-3">
          <h3 class="text-2xl font-black tracking-tighter">{{ card.value }}</h3>
          <span class="text-xs font-bold" :class="card.emphasisClass || 'text-on-surface-variant'">{{ card.hint }}</span>
        </div>
      </div>
    </section>

    <section class="grid grid-cols-1 lg:grid-cols-3 gap-6">
      <div class="lg:col-span-2 bg-white rounded-lg border border-outline-variant/10">
        <div class="p-5 border-b border-surface-container-low flex items-center justify-between">
          <div>
            <h4 class="text-sm font-bold tracking-tight">{{ sectionTitle }}</h4>
            <p class="text-[10px] text-on-surface-variant uppercase tracking-widest">{{ sectionSubtitle }}</p>
          </div>
          <button class="text-xs text-primary font-semibold" @click="reload">刷新</button>
        </div>
        <div class="p-5">
          <div v-if="loading" class="text-sm text-on-surface-variant">加载中...</div>
          <div v-else-if="entries.length === 0" class="text-sm text-on-surface-variant">{{ emptyText }}</div>
          <ul v-else class="space-y-3">
            <li v-for="item in entries" :key="item.key" class="rounded-xl border border-outline-variant/20 bg-slate-50 px-4 py-4">
              <div class="flex items-start justify-between gap-4">
                <div class="min-w-0">
                  <div class="flex flex-wrap items-center gap-2">
                    <span class="rounded-full bg-white px-2.5 py-1 text-[11px] font-semibold text-primary border border-outline-variant/20">{{ item.category }}</span>
                    <span class="font-semibold break-all">{{ item.title }}</span>
                  </div>
                  <div class="mt-2 text-sm text-slate-700">{{ item.detail }}</div>
                  <div v-if="item.subDetail" class="mt-1 text-xs text-on-surface-variant">{{ item.subDetail }}</div>
                </div>
                <div class="shrink-0 text-right">
                  <div class="text-xs font-semibold" :class="item.statusClass || 'text-primary'">{{ item.status }}</div>
                  <div v-if="item.amount" class="mt-2 text-sm font-bold text-slate-900">{{ item.amount }}</div>
                  <div class="mt-2 text-xs text-on-surface-variant">{{ formatDate(item.time) }}</div>
                </div>
              </div>
            </li>
          </ul>
        </div>
      </div>
      <div class="bg-white rounded-lg border border-outline-variant/10 p-5 space-y-3">
        <h4 class="text-sm font-bold tracking-tight">岗位提示</h4>
        <div v-for="tip in tips" :key="tip" class="text-xs text-slate-500">{{ tip }}</div>
      </div>
    </section>

    <section class="bg-white rounded-lg border border-outline-variant/10">
      <div class="p-5 border-b border-surface-container-low flex items-center justify-between">
        <h4 class="text-sm font-bold tracking-tight">实时消息</h4>
        <span class="text-xs text-on-surface-variant">{{ realtime.state.events.length }} 条</span>
      </div>
      <div class="p-5">
        <div v-if="realtime.state.events.length === 0" class="text-sm text-on-surface-variant">等待消息推送...</div>
        <ul v-else class="space-y-2">
          <li v-for="event in realtimeEvents" :key="`${event.topic}-${event.timestamp}-${event.entityId || 'none'}`" class="text-xs border border-outline-variant/20 rounded px-3 py-2">
            <div class="flex items-center justify-between gap-3">
              <span class="font-semibold">{{ event.topic }}</span>
              <span class="text-on-surface-variant">{{ formatDate(event.timestamp) }}</span>
            </div>
            <div class="text-on-surface-variant mt-1">{{ event.messageType || 'MESSAGE' }} {{ event.entity ? `| ${event.entity}` : '' }} {{ event.entityId ? `#${event.entityId}` : '' }}</div>
          </li>
        </ul>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { inventoryApi, orderApi, procurementApi, productionApi, qualityApi } from '../api/services.js';
import { useAuthStore } from '../store/auth.js';
import { useRealtimeStore } from '../store/realtime.js';

const PROCUREMENT_WAITING_SUPPLIER = '待供应商确认';
const PROCUREMENT_SUPPLIER_ACCEPTED = '供应商已接单';
const PROCUREMENT_SUPPLIER_SHIPPED = '供应商已发货';
const PROCUREMENT_WAITING_WAREHOUSE = '待仓库收货';
const PROCUREMENT_WAREHOUSED = '已入库';
const PRODUCTION_IN_PROGRESS = '生产中';
const PRODUCTION_READY = '已备料待生产';
const PRODUCTION_PENDING_MATERIAL = '待仓库备料';
const PRODUCTION_PENDING_PROCUREMENT = '待采购补料';

const auth = useAuthStore();
const realtime = useRealtimeStore();
const loading = ref(false);
const apiStatus = ref('待检查');

const state = reactive({
  cards: [],
  entries: [],
  tips: [],
  sectionTitle: '最新工作',
  sectionSubtitle: 'Latest tasks',
  emptyText: '暂无数据。'
});

const roleKey = computed(() => {
  if (auth.state.role === 'ROLE_ADMIN') {
    return 'admin';
  }
  if (auth.hasPermission('procurement:create')) {
    return 'procurement';
  }
  if (auth.hasPermission('production:update')) {
    return 'production';
  }
  if (auth.hasPermission('inventory:receive')) {
    return 'warehouse';
  }
  if (auth.hasPermission('quality:view')) {
    return 'quality';
  }
  if (auth.hasPermission('orders:review')) {
    return 'sales';
  }
  return 'admin';
});

const cards = computed(() => state.cards);
const entries = computed(() => state.entries.slice(0, 8));
const tips = computed(() => state.tips);
const sectionTitle = computed(() => state.sectionTitle);
const sectionSubtitle = computed(() => state.sectionSubtitle);
const emptyText = computed(() => state.emptyText);
const realtimeEvents = computed(() => realtime.state.events.slice(0, 8));

const setDashboardState = (payload) => {
  state.cards = payload.cards || [];
  state.entries = payload.entries || [];
  state.tips = payload.tips || [];
  state.sectionTitle = payload.sectionTitle || '最新工作';
  state.sectionSubtitle = payload.sectionSubtitle || 'Latest tasks';
  state.emptyText = payload.emptyText || '暂无数据。';
};

const toCurrency = (value) => `¥${Number(value || 0).toFixed(2)}`;
const formatNumber = (value) => Number(value || 0).toFixed(2);
const formatDate = (value) => (value ? new Date(value).toLocaleString() : '-');
const sumOrderItems = (items) => (items || []).reduce((total, item) => total + Number(item?.quantity || 0), 0);
const summarizePurchaseItems = (items) => (items || []).map((item) => `${item.product?.name || item.product?.sku || '-'} x ${formatNumber(item.quantity)}`).join('；') || '-';
const summarizeMaterialItems = (items) => (items || []).map((item) => `${item.materialProduct?.name || item.productName || '-'} x ${formatNumber(item.requiredQuantity)}`).join('；') || '-';
const sortByLatest = (items, getter) => [...items].sort((left, right) => new Date(getter(right) || 0).getTime() - new Date(getter(left) || 0).getTime());

const buildAdminDashboard = async () => {
  const [ordersResponse, procurementResponse, alertsResponse] = await Promise.all([
    orderApi.list(),
    procurementApi.listOrders(),
    inventoryApi.listAlerts()
  ]);
  const orders = ordersResponse.data || [];
  const procurementOrders = procurementResponse.data || [];
  const alerts = alertsResponse.data || {};
  const openProcurement = procurementOrders.filter((item) => ![PROCUREMENT_WAREHOUSED, '供应商已拒绝'].includes(item.status));
  const recentEntries = sortByLatest([
    ...orders.map((order) => ({
      key: `sales-${order.id}`,
      category: '销售订单',
      title: order.orderNo || `订单#${order.id}`,
      detail: `${(order.items || []).map((item) => item.product?.name || item.product?.sku || '-').join('，') || '-'} · 数量 ${formatNumber(sumOrderItems(order.items))}`,
      subDetail: `收货地址：${order.shippingAddress || '-'}`,
      status: order.status || '-',
      statusClass: 'text-blue-700',
      amount: toCurrency(order.totalAmount),
      time: order.orderDate || order.createdAt
    })),
    ...procurementOrders.map((order) => ({
      key: `po-${order.id}`,
      category: '采购单',
      title: order.poNo || `采购单#${order.id}`,
      detail: summarizePurchaseItems(order.items),
      subDetail: `供应商：${order.supplier?.name || '-'}`,
      status: order.status || '-',
      statusClass: 'text-emerald-700',
      amount: toCurrency(order.totalAmount),
      time: order.orderDate || order.createdAt
    }))
  ], (item) => item.time);

  setDashboardState({
    cards: [
      { label: '销售订单总数', value: String(orders.length), hint: '系统全局', emphasisClass: 'text-primary' },
      { label: '采购在途单数', value: String(openProcurement.length), hint: '跨部门', emphasisClass: 'text-emerald-700' },
      { label: '原材料预警数', value: String((alerts.rawMaterials || []).length), hint: '库存监控', emphasisClass: 'text-amber-700' },
      { label: '系统连接', value: apiStatus.value, hint: 'API', emphasisClass: apiStatus.value === '在线' ? 'text-primary' : 'text-red-600' }
    ],
    entries: recentEntries,
    tips: [
      '管理员总览按业务流展示销售、采购与库存动态，便于快速发现跨岗位阻塞点。',
      '原材料预警与采购在途应联合观察，避免重复下单或错过补料窗口。',
      '如看到生产或采购异常积压，建议进入对应模块继续处理。'
    ],
    sectionTitle: '最新业务动态',
    sectionSubtitle: 'Cross-module activity',
    emptyText: '当前没有可展示的业务动态。'
  });
};

const buildSalesDashboard = async () => {
  const [ordersResponse, salesRecordsResponse] = await Promise.all([
    orderApi.list(),
    orderApi.listSalesRecords()
  ]);
  const orders = ordersResponse.data || [];
  const salesRecords = salesRecordsResponse.data || [];
  const waitingReview = orders.filter((item) => item.status === '待销售审核').length;
  const inProgress = orders.filter((item) => ['待仓库核查', '已接单', PRODUCTION_IN_PROGRESS, '待质检', '待发货', '已发货'].includes(item.status)).length;

  setDashboardState({
    cards: [
      { label: '订单总数', value: String(orders.length), hint: '销售视角', emphasisClass: 'text-primary' },
      { label: '待销售审核', value: String(waitingReview), hint: '优先处理', emphasisClass: 'text-amber-700' },
      { label: '履约中订单', value: String(inProgress), hint: '全流程跟踪', emphasisClass: 'text-blue-700' },
      { label: '销售记录', value: String(salesRecords.length), hint: '已归档', emphasisClass: 'text-emerald-700' }
    ],
    entries: sortByLatest((orders || []).map((order) => ({
      key: `sales-${order.id}`,
      category: '销售订单',
      title: order.orderNo || `订单#${order.id}`,
      detail: `${(order.items || []).map((item) => item.product?.name || item.product?.sku || '-').join('，') || '-'} · 数量 ${formatNumber(sumOrderItems(order.items))}`,
      subDetail: `客户：${order.customer?.name || order.customer?.contact || '-'} · 地址：${order.shippingAddress || '-'}`,
      status: order.status || '-',
      statusClass: order.status === '待销售审核' ? 'text-amber-700' : 'text-blue-700',
      amount: toCurrency(order.totalAmount),
      time: order.orderDate || order.createdAt
    })), (item) => item.time),
    tips: [
      '优先处理待销售审核订单，避免后续仓储与生产环节被动等待。',
      '对已发货订单及时完成归档，可保持销售记录与周统计同步。',
      '若库存核验后转入生产，请在订单详情中关注生产进度。'
    ],
    sectionTitle: '最新销售订单',
    sectionSubtitle: 'Recent sales orders',
    emptyText: '当前没有销售订单数据。'
  });
};

const buildProcurementDashboard = async () => {
  const [ordersResponse, requestsResponse] = await Promise.all([
    procurementApi.listOrders(),
    procurementApi.listRequests()
  ]);
  const orders = ordersResponse.data || [];
  const requests = requestsResponse.data || [];
  const inTransit = orders.filter((item) => [PROCUREMENT_SUPPLIER_ACCEPTED, PROCUREMENT_SUPPLIER_SHIPPED, PROCUREMENT_WAITING_WAREHOUSE].includes(item.status)).length;

  setDashboardState({
    cards: [
      { label: '待处理采购申请', value: String(requests.length), hint: '来自仓库', emphasisClass: 'text-amber-700' },
      { label: '待供应商确认', value: String(orders.filter((item) => item.status === PROCUREMENT_WAITING_SUPPLIER).length), hint: '待跟进', emphasisClass: 'text-primary' },
      { label: '在途采购单', value: String(inTransit), hint: '物流中', emphasisClass: 'text-blue-700' },
      { label: '待仓库收货', value: String(orders.filter((item) => item.status === PROCUREMENT_WAITING_WAREHOUSE).length), hint: '待闭环', emphasisClass: 'text-emerald-700' }
    ],
    entries: sortByLatest([
      ...requests.map((request) => ({
        key: `request-${request.id}`,
        category: '采购申请',
        title: request.requestNo || `申请#${request.id}`,
        detail: `${request.product?.name || request.product?.sku || '-'} · 申请数量 ${formatNumber(request.requestedQuantity)}`,
        subDetail: `发起人：${request.requestedByName || request.requestedBy || '-'} · ${request.notes || '等待采购管理员处理'}`,
        status: request.status || 'OPEN',
        statusClass: 'text-amber-700',
        amount: '',
        time: request.requestDate
      })),
      ...orders.map((order) => ({
        key: `po-${order.id}`,
        category: '采购单',
        title: order.poNo || `采购单#${order.id}`,
        detail: summarizePurchaseItems(order.items),
        subDetail: `供应商：${order.supplier?.name || '-'} · 创建人：${order.createdByName || '-'}`,
        status: order.status || '-',
        statusClass: [PROCUREMENT_WAITING_SUPPLIER, PROCUREMENT_WAITING_WAREHOUSE].includes(order.status) ? 'text-primary' : 'text-emerald-700',
        amount: toCurrency(order.totalAmount),
        time: order.orderDate || order.createdAt
      }))
    ], (item) => item.time),
    tips: [
      '仓库补采申请会直接出现在这里，处理完成后建议立即带入采购单，避免遗漏。',
      '若同一供应商可覆盖多种原材料，建议合并成一张采购单，降低沟通成本。',
      '待仓库收货的采购单需要继续跟踪到入库闭环。'
    ],
    sectionTitle: '最新采购任务',
    sectionSubtitle: 'Purchase requests and orders',
    emptyText: '当前没有待处理的采购任务。'
  });
};

const buildProductionDashboard = async () => {
  const [ordersResponse, requestsResponse, alertsResponse] = await Promise.all([
    orderApi.list(),
    productionApi.listMaterialRequests(),
    productionApi.listQualityAlerts()
  ]);
  const orders = (ordersResponse.data || []).filter((item) => ['生产中', '待质检'].includes(item.status));
  const requests = requestsResponse.data || [];
  const qualityAlerts = alertsResponse.data || [];

  setDashboardState({
    cards: [
      { label: '生产中订单', value: String(orders.filter((item) => item.status === PRODUCTION_IN_PROGRESS).length), hint: '执行中', emphasisClass: 'text-primary' },
      { label: '待领料申请', value: String(requests.filter((item) => item.status === PRODUCTION_PENDING_MATERIAL).length), hint: '待仓库处理', emphasisClass: 'text-amber-700' },
      { label: '已备料待生产', value: String(requests.filter((item) => item.status === PRODUCTION_READY).length), hint: '可开工', emphasisClass: 'text-emerald-700' },
      { label: '质检异常', value: String(qualityAlerts.length), hint: '需关注', emphasisClass: 'text-red-600' }
    ],
    entries: sortByLatest([
      ...requests.map((request) => ({
        key: `pmr-${request.id}`,
        category: '领料申请',
        title: request.requestNo || `申请#${request.id}`,
        detail: summarizeMaterialItems(request.items),
        subDetail: `订单：${request.salesOrder?.orderNo || '-'} · 成品：${request.finishedProduct?.name || '-'} · ${request.warehouseNote || request.requestNote || '等待仓库处理'}`,
        status: request.status || '-',
        statusClass: request.status === PRODUCTION_PENDING_PROCUREMENT ? 'text-red-600' : (request.status === PRODUCTION_READY ? 'text-emerald-700' : 'text-amber-700'),
        amount: '',
        time: request.createdAt
      })),
      ...orders.map((order) => ({
        key: `production-order-${order.id}`,
        category: '生产订单',
        title: order.orderNo || `订单#${order.id}`,
        detail: `${(order.items || []).map((item) => item.product?.name || item.product?.sku || '-').join('，') || '-'} · 数量 ${formatNumber(sumOrderItems(order.items))}`,
        subDetail: `状态：${order.status || '-'} · 地址：${order.shippingAddress || '-'}`,
        status: order.status || '-',
        statusClass: order.status === '待质检' ? 'text-blue-700' : 'text-primary',
        amount: toCurrency(order.totalAmount),
        time: order.orderDate || order.createdAt
      }))
    ], (item) => item.time),
    tips: [
      '一张领料申请可同时维护多个原材料种类，建议一次性补齐，减少往返沟通。',
      '若领料申请进入“待采购补料”，请同步关注采购侧到料进度。',
      '质检异常需要优先排查，避免影响后续仓储入库与订单交付。'
    ],
    sectionTitle: '最新生产任务',
    sectionSubtitle: 'Production tasks and material requests',
    emptyText: '当前没有生产任务。'
  });
};

const buildWarehouseDashboard = async () => {
  const [alertsResponse, productionStockInResponse, purchaseReceiptResponse, materialRequestsResponse] = await Promise.all([
    inventoryApi.listAlerts(),
    orderApi.listPendingProductionStockIn(),
    procurementApi.listPendingWarehouseReceipts(),
    productionApi.listMaterialRequests()
  ]);
  const alerts = alertsResponse.data || {};
  const pendingProductionStockIn = productionStockInResponse.data || [];
  const pendingPurchaseReceipts = purchaseReceiptResponse.data || [];
  const materialRequests = (materialRequestsResponse.data || []).filter((item) => [PRODUCTION_PENDING_MATERIAL, PRODUCTION_PENDING_PROCUREMENT].includes(item.status));

  setDashboardState({
    cards: [
      { label: '待处理领料申请', value: String(materialRequests.length), hint: '仓库审核', emphasisClass: 'text-primary' },
      { label: '待确认采购入库', value: String(pendingPurchaseReceipts.length), hint: '原料入库', emphasisClass: 'text-emerald-700' },
      { label: '待确认生产入库', value: String(pendingProductionStockIn.length), hint: '成品入库', emphasisClass: 'text-blue-700' },
      { label: '原材料预警', value: String((alerts.rawMaterials || []).length), hint: '缺料风险', emphasisClass: 'text-amber-700' }
    ],
    entries: sortByLatest([
      ...materialRequests.map((request) => ({
        key: `warehouse-pmr-${request.id}`,
        category: '领料申请',
        title: request.requestNo || `申请#${request.id}`,
        detail: summarizeMaterialItems(request.items),
        subDetail: `订单：${request.salesOrder?.orderNo || '-'} · 申请人：${request.createdByName || '-'} · ${request.requestNote || '待仓库审核'}`,
        status: request.status || '-',
        statusClass: request.status === PRODUCTION_PENDING_PROCUREMENT ? 'text-red-600' : 'text-primary',
        amount: '',
        time: request.createdAt
      })),
      ...pendingPurchaseReceipts.map((order) => ({
        key: `warehouse-po-${order.id}`,
        category: '采购入库',
        title: order.poNo || `采购单#${order.id}`,
        detail: summarizePurchaseItems(order.items),
        subDetail: `供应商：${order.supplier?.name || '-'} · 等待仓库确认收货`,
        status: order.status || PROCUREMENT_WAITING_WAREHOUSE,
        statusClass: 'text-emerald-700',
        amount: toCurrency(order.totalAmount),
        time: order.notifiedWarehouseAt || order.orderDate || order.createdAt
      })),
      ...pendingProductionStockIn.map((order) => ({
        key: `warehouse-so-${order.id}`,
        category: '生产入库',
        title: order.orderNo || `订单#${order.id}`,
        detail: `${(order.items || []).map((item) => item.product?.name || item.product?.sku || '-').join('，') || '-'} · 数量 ${formatNumber(sumOrderItems(order.items))}`,
        subDetail: '生产已完工，等待仓库确认成品入库。',
        status: order.status || '-',
        statusClass: 'text-blue-700',
        amount: toCurrency(order.totalAmount),
        time: order.updatedAt || order.orderDate || order.createdAt
      }))
    ], (item) => item.time),
    tips: [
      '仓库管理员不再进入采购管理页，采购到货确认请在库存管理内直接完成。',
      '遇到生产领料短缺时，系统会自动通知采购管理员补料，无需重复创建采购单。',
      '处理完入库/出库后建议同步刷新库存明细，核对可用库存与预警变化。'
    ],
    sectionTitle: '最新仓储任务',
    sectionSubtitle: 'Warehouse receiving and material prep',
    emptyText: '当前没有待处理的仓储任务。'
  });
};

const buildQualityDashboard = async () => {
  const [batchesResponse, myRecordsResponse] = await Promise.all([
    qualityApi.listBatches(),
    qualityApi.listMyRecords()
  ]);
  const batches = batchesResponse.data || [];
  const myRecords = myRecordsResponse.data || [];

  setDashboardState({
    cards: [
      { label: '待检批次', value: String(batches.filter((item) => item.qualityStatus === '待检').length), hint: '优先处理', emphasisClass: 'text-amber-700' },
      { label: '不合格批次', value: String(batches.filter((item) => item.qualityStatus === '不合格').length), hint: '需复核', emphasisClass: 'text-red-600' },
      { label: '合格批次', value: String(batches.filter((item) => item.qualityStatus === '合格').length), hint: '已放行', emphasisClass: 'text-emerald-700' },
      { label: '我的质检记录', value: String(myRecords.length), hint: '个人处理量', emphasisClass: 'text-primary' }
    ],
    entries: sortByLatest((batches || []).map((batch) => ({
      key: `batch-${batch.id}`,
      category: '质检批次',
      title: batch.batchNo || `批次#${batch.id}`,
      detail: `${batch.product?.name || '-'}（${batch.product?.sku || '-'}） · 数量 ${formatNumber(batch.quantity)}`,
      subDetail: `来源订单：${batch.sourceOrderNo || '-'} · 生产管理员：${batch.productionManagerName || batch.productionManagerEmail || '-'}`,
      status: batch.qualityStatus || '待检',
      statusClass: batch.qualityStatus === '不合格' ? 'text-red-600' : (batch.qualityStatus === '合格' ? 'text-emerald-700' : 'text-amber-700'),
      amount: '',
      time: batch.qualityInspectedAt || batch.manufactureDate || batch.createdAt
    })), (item) => item.time),
    tips: [
      '待检批次应优先处理，避免影响仓库入库与客户交付。',
      '不合格批次需要填写具体原因，系统会自动回传生产侧进行返工闭环。',
      '建议结合“我的质检记录”回顾常见问题，持续优化检验标准。'
    ],
    sectionTitle: '最新质检批次',
    sectionSubtitle: 'Recent inspection batches',
    emptyText: '当前没有待检或已检批次。'
  });
};

const loaders = {
  admin: buildAdminDashboard,
  sales: buildSalesDashboard,
  procurement: buildProcurementDashboard,
  production: buildProductionDashboard,
  warehouse: buildWarehouseDashboard,
  quality: buildQualityDashboard
};

const reload = async () => {
  loading.value = true;
  try {
    apiStatus.value = '在线';
    await (loaders[roleKey.value] || buildAdminDashboard)();
  } catch (error) {
    apiStatus.value = '离线';
    setDashboardState({
      cards: [
        { label: '系统连接', value: '离线', hint: 'API', emphasisClass: 'text-red-600' },
        { label: '当前岗位', value: roleKey.value, hint: '角色', emphasisClass: 'text-primary' },
        { label: '待同步消息', value: String(realtime.state.events.length), hint: '实时流', emphasisClass: 'text-amber-700' },
        { label: '最近刷新', value: '-', hint: '稍后重试', emphasisClass: 'text-on-surface-variant' }
      ],
      entries: [],
      tips: ['系统数据暂时不可用，请稍后刷新重试。'],
      sectionTitle: '岗位工作台',
      sectionSubtitle: 'Role dashboard',
      emptyText: '当前无法获取总览数据。'
    });
  } finally {
    loading.value = false;
  }
};

onMounted(reload);

watch(() => auth.state.role, reload);

watch(
  () => realtime.state.lastMessage,
  (message) => {
    if (!message?.topic?.startsWith('/topic/')) {
      return;
    }
    reload();
  }
);
</script>

