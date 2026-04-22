<template>
  <div class="space-y-6">
    <section v-if="canUpdateProduction" class="bg-white rounded-lg border border-outline-variant/10">
      <div class="p-5 border-b border-surface-container-low flex items-center justify-between">
        <div>
          <h3 class="text-sm font-bold tracking-tight">待处理质检异常</h3>
          <p class="mt-1 text-xs text-on-surface-variant">展示当前生产管理员负责批次中的不合格成品，便于及时返工与跟进。</p>
        </div>
        <button class="text-xs text-primary font-semibold" @click="loadQualityAlerts">刷新</button>
      </div>
      <div class="p-5">
        <div v-if="qualityAlertError" class="mb-3 text-xs text-error">{{ qualityAlertError }}</div>
        <div v-if="qualityAlerts.length === 0" class="text-sm text-on-surface-variant">当前没有待处理质检异常。</div>
        <table v-else class="w-full text-sm">
          <thead class="text-xs text-on-surface-variant">
            <tr class="text-left">
              <th class="pb-2">批次号</th>
              <th class="pb-2">订单号</th>
              <th class="pb-2">产品名称</th>
              <th class="pb-2">SKU</th>
              <th class="pb-2">数量</th>
              <th class="pb-2">问题说明</th>
              <th class="pb-2">质检员</th>
              <th class="pb-2">质检时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="alert in qualityAlerts" :key="alert.id" class="border-t border-outline-variant/20">
              <td class="py-3 font-semibold text-red-700">{{ alert.batchNo }}</td>
              <td class="py-3">{{ alert.orderNo || '-' }}</td>
              <td class="py-3">{{ alert.productName || '-' }}</td>
              <td class="py-3">{{ alert.productSku || '-' }}</td>
              <td class="py-3">{{ formatNumber(alert.quantity) }}</td>
              <td class="py-3">{{ alert.qualityRemark || '未填写原因' }}</td>
              <td class="py-3">{{ alert.inspectorName || '-' }}</td>
              <td class="py-3">{{ formatDate(alert.inspectedAt) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <section v-if="canUpdateProduction" class="bg-white rounded-lg border border-outline-variant/10 p-5">
      <div class="flex items-center justify-between gap-4">
        <div>
          <h3 class="text-sm font-bold tracking-tight">手动生产领料申请</h3>
          <p class="mt-1 text-xs text-on-surface-variant">生产管理员需先提交所需原材料和数量，待仓库管理员审核并完成原料出库后，方可继续生产。</p>
          <p class="mt-2 text-xs text-slate-500">同一张领料申请支持添加多个原材料种类；若重复添加同一原材料，系统会自动合并数量，避免重复行。</p>
        </div>
        <button class="text-xs text-primary font-semibold" @click="loadMaterialRequests">刷新申请</button>
      </div>

      <form class="mt-4 grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-4" @submit.prevent="submitMaterialRequest">
        <select v-model="materialRequestForm.orderId" class="rounded border border-outline-variant/40 px-3 py-2 text-sm" required>
          <option value="">请选择生产订单</option>
          <option v-for="order in productionOrders.filter((item) => item.status === '生产中')" :key="order.id" :value="String(order.id)">
            {{ order.orderNo }} / {{ formatProducts(order.items) }}
          </option>
        </select>
        <select v-model="materialItemForm.productId" class="rounded border border-outline-variant/40 px-3 py-2 text-sm">
          <option value="">请选择原材料</option>
          <option v-for="material in rawMaterialOptions" :key="material.id" :value="String(material.id)">
            {{ material.name }}（{{ material.sku }}）
          </option>
        </select>
        <input v-model.number="materialItemForm.quantity" type="number" min="0.01" step="0.01" placeholder="需求数量" class="rounded border border-outline-variant/40 px-3 py-2 text-sm" />
        <button type="button" class="rounded border border-primary text-primary px-3 py-2 text-sm font-semibold" @click="addMaterialItem">添加原材料</button>
        <textarea v-model="materialRequestForm.note" placeholder="补充说明（选填）" class="md:col-span-2 xl:col-span-4 rounded border border-outline-variant/40 px-3 py-2 text-sm min-h-24"></textarea>
        <div class="md:col-span-2 xl:col-span-4 flex flex-wrap gap-3">
          <button class="rounded bg-primary text-white px-4 py-2 text-sm font-semibold">提交领料申请</button>
          <button type="button" class="rounded border border-outline-variant/40 px-4 py-2 text-sm" @click="resetMaterialRequestForm">清空</button>
        </div>
      </form>

      <div v-if="materialRequestItems.length" class="mt-4 rounded-lg border border-outline-variant/20 bg-slate-50 px-4 py-3">
        <p class="text-xs font-semibold text-on-surface-variant">当前申请原材料（{{ materialRequestItems.length }} 种）：</p>
        <ul class="mt-2 space-y-1 text-xs text-on-surface-variant">
          <li v-for="(item, index) in materialRequestItems" :key="`${item.materialProductId}-${index}`">
            <div class="flex flex-wrap items-center gap-2">
              <span>{{ item.productName }}（{{ item.productSku }}） × {{ formatNumber(item.requiredQuantity) }}</span>
              <button type="button" class="text-red-600" @click="removeMaterialItem(index)">移除</button>
            </div>
          </li>
        </ul>
      </div>

      <div v-if="materialRequestMessage" class="mt-3 text-xs text-emerald-600">{{ materialRequestMessage }}</div>
      <div v-if="materialRequestError" class="mt-3 text-xs text-error">{{ materialRequestError }}</div>
    </section>

    <section class="bg-white rounded-lg border border-outline-variant/10">
      <div class="p-5 border-b border-surface-container-low flex items-center justify-between">
        <div>
          <h3 class="text-sm font-bold tracking-tight">生产领料申请记录</h3>
          <p class="mt-1 text-xs text-on-surface-variant">展示当前生产管理员提交的原材料申请、仓库审核结果及备料状态。</p>
        </div>
        <button class="text-xs text-primary font-semibold" @click="loadMaterialRequests">刷新</button>
      </div>
      <div class="p-5">
        <div v-if="materialRequests.length === 0" class="text-sm text-on-surface-variant">暂无生产领料申请记录。</div>
        <table v-else class="w-full text-sm">
          <thead class="text-xs text-on-surface-variant">
            <tr class="text-left">
              <th class="pb-2">申请单号</th>
              <th class="pb-2">订单号</th>
              <th class="pb-2">成品</th>
              <th class="pb-2">原材料明细</th>
              <th class="pb-2">状态</th>
              <th class="pb-2">仓库说明</th>
              <th class="pb-2">申请时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="request in materialRequests" :key="request.id" class="border-t border-outline-variant/20">
              <td class="py-3 font-semibold">{{ request.requestNo }}</td>
              <td class="py-3">{{ request.salesOrder?.orderNo || '-' }}</td>
              <td class="py-3">{{ request.finishedProduct?.name || '-' }}</td>
              <td class="py-3">{{ summarizeMaterialItems(request.items) }}</td>
              <td class="py-3">{{ formatMaterialRequestStatus(request.status) }}</td>
              <td class="py-3">{{ request.warehouseNote || request.requestNote || '-' }}</td>
              <td class="py-3">{{ formatDate(request.createdAt) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <section class="bg-white rounded-lg border border-outline-variant/10">
      <div class="p-5 border-b border-surface-container-low flex items-center justify-between">
        <h3 class="text-sm font-bold tracking-tight">生产订单</h3>
        <button class="text-xs text-primary font-semibold" @click="loadOrders">刷新</button>
      </div>
      <div class="p-5">
        <div v-if="loading" class="text-sm text-on-surface-variant">加载中...</div>
        <div v-else-if="productionOrders.length === 0" class="text-sm text-on-surface-variant">暂无待生产订单。</div>
        <table v-else class="w-full text-sm">
          <thead class="text-xs text-on-surface-variant">
            <tr class="text-left">
              <th class="pb-2">订单号</th>
              <th class="pb-2">产品名称</th>
              <th class="pb-2">生产数量</th>
              <th class="pb-2">收货地址</th>
              <th class="pb-2">状态</th>
              <th class="pb-2">领料状态</th>
              <th class="pb-2">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="order in productionOrders" :key="order.id" class="border-t border-outline-variant/20">
              <td class="py-3 font-semibold">{{ order.orderNo }}</td>
              <td class="py-3">{{ formatProducts(order.items) }}</td>
              <td class="py-3">{{ formatQuantity(order.items) }}</td>
              <td class="py-3">{{ order.shippingAddress || '-' }}</td>
              <td class="py-3">{{ order.status }}</td>
              <td class="py-3">{{ formatMaterialRequestStatus(latestRequestByOrder[order.id]?.status) }}</td>
              <td class="py-3">
                <button
                  v-if="canUpdateProduction && order.status === '生产中'"
                  class="text-xs text-orange-600"
                  @click="completeProduction(order.id)"
                  :disabled="!canCompleteOrder(order.id)"
                >
                  生产完成并通知仓库
                </button>
                <button
                  v-if="canUpdateProduction && order.status === '生产中' && !latestRequestByOrder[order.id]"
                  class="ml-3 text-xs text-primary"
                  @click="prefillMaterialRequest(order)"
                >
                  先申请原料
                </button>
                <span v-else-if="order.status !== '生产中'" class="text-xs text-on-surface-variant">只读</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-if="statusMessage" class="px-5 pb-4 text-xs text-emerald-600">{{ statusMessage }}</div>
      <div v-if="statusError" class="px-5 pb-4 text-xs text-error">{{ statusError }}</div>
    </section>

    <section class="bg-white rounded-lg border border-outline-variant/10">
      <div class="p-5 border-b border-surface-container-low flex items-center justify-between">
        <div>
          <h3 class="text-sm font-bold tracking-tight">生产记录</h3>
          <p class="mt-1 text-xs text-on-surface-variant">展示所有已完成或已由仓库确认入库的生产计划记录。</p>
        </div>
        <button class="text-xs text-primary font-semibold" @click="loadRecords">刷新</button>
      </div>
      <div class="p-5">
        <div class="mb-4 grid grid-cols-1 md:grid-cols-5 gap-3">
          <input v-model="recordFilter.keyword" placeholder="搜索计划号 / 订单号 / 产品名称 / SKU" class="rounded border border-outline-variant/40 px-3 py-2 text-sm md:col-span-2" @keyup.enter="loadRecords" />
          <input v-model="recordFilter.startDate" type="date" class="rounded border border-outline-variant/40 px-3 py-2 text-sm" />
          <input v-model="recordFilter.endDate" type="date" class="rounded border border-outline-variant/40 px-3 py-2 text-sm" />
          <div class="flex items-center gap-3">
            <button class="rounded border border-primary text-primary px-3 py-2 text-sm font-semibold" @click="loadRecords">筛选</button>
            <button class="text-xs text-on-surface-variant" @click="resetRecordFilter">重置</button>
          </div>
        </div>
        <div v-if="recordError" class="mb-3 text-xs text-error">{{ recordError }}</div>
        <div v-if="productionRecords.length === 0" class="text-sm text-on-surface-variant">暂无生产记录。</div>
        <table v-else class="w-full text-sm">
          <thead class="text-xs text-on-surface-variant">
            <tr class="text-left">
              <th class="pb-2">计划号</th>
              <th class="pb-2">订单号</th>
              <th class="pb-2">产品名称</th>
              <th class="pb-2">SKU</th>
              <th class="pb-2">生产数量</th>
              <th class="pb-2">状态</th>
              <th class="pb-2">计划创建人</th>
              <th class="pb-2">完工生产管理员</th>
              <th class="pb-2">开始时间</th>
              <th class="pb-2">完成时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="record in productionRecords" :key="record.id" class="border-t border-outline-variant/20">
              <td class="py-3 font-semibold">{{ record.planNo }}</td>
              <td class="py-3">{{ record.orderNo || '-' }}</td>
              <td class="py-3">{{ record.productName || '-' }}</td>
              <td class="py-3">{{ record.productSku || '-' }}</td>
              <td class="py-3">{{ formatNumber(record.plannedQuantity) }}</td>
              <td class="py-3">{{ formatRecordStatus(record.status) }}</td>
              <td class="py-3">{{ formatOperator(record.createdByName, record.createdBy) }}</td>
              <td class="py-3">{{ formatOperator(record.completedByName, record.completedById) }}</td>
              <td class="py-3">{{ formatDate(record.startDate) }}</td>
              <td class="py-3">{{ formatDate(record.completedAt) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <WeeklyGantt
      title="生产周甘特概览（全员统计）"
      :items="productionGanttItems"
      empty-text="当前没有可用于统计的生产记录。"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { orderApi, productionApi, productApi } from '../api/services.js';
import { useRealtimeStore } from '../store/realtime.js';
import { useAuthStore } from '../store/auth.js';
import WeeklyGantt from '../components/WeeklyGantt.vue';

const auth = useAuthStore();
const canUpdateProduction = computed(() => auth.hasPermission('production:update'));

const productionOrders = ref([]);
const loading = ref(false);
const statusMessage = ref('');
const statusError = ref('');
const productionRecords = ref([]);
const productionOverviewRecords = ref([]);
const recordError = ref('');
const qualityAlerts = ref([]);
const qualityAlertError = ref('');
const materialRequests = ref([]);
const rawMaterialOptions = ref([]);
const materialRequestMessage = ref('');
const materialRequestError = ref('');
const materialRequestItems = ref([]);
const materialRequestForm = reactive({
  orderId: '',
  note: ''
});
const materialItemForm = reactive({
  productId: '',
  quantity: null
});
const recordFilter = reactive({
  keyword: '',
  startDate: '',
  endDate: ''
});
const realtime = useRealtimeStore();

const loadOrders = async () => {
  loading.value = true;
  try {
    const response = await orderApi.list();
    const all = response.data || [];
    productionOrders.value = all.filter((order) => ['生产中', '待质检'].includes(order.status));
    if (!materialRequestForm.orderId) {
      const firstOpenOrder = productionOrders.value.find((order) => order.status === '生产中');
      materialRequestForm.orderId = firstOpenOrder ? String(firstOpenOrder.id) : '';
    }
  } catch (error) {
    productionOrders.value = [];
  } finally {
    loading.value = false;
  }
};

const completeProduction = async (orderId) => {
  statusMessage.value = '';
  statusError.value = '';
  try {
    await orderApi.productionComplete(orderId, {});
    statusMessage.value = `生产订单 ${orderId} 已完成，并已通知仓库管理员确认入库。`;
    await Promise.all([loadOrders(), loadRecords(), loadMaterialRequests()]);
  } catch (error) {
    statusError.value = error?.response?.data?.message || error?.response?.data || '生产完成回传失败。';
  }
};

const loadRawMaterialOptions = async () => {
  try {
    const response = await productApi.list({ productType: 'RAW_MATERIAL' });
    rawMaterialOptions.value = response.data || [];
  } catch (error) {
    rawMaterialOptions.value = [];
  }
};

const loadMaterialRequests = async () => {
  materialRequestError.value = '';
  try {
    const response = await productionApi.listMaterialRequests();
    materialRequests.value = response.data || [];
  } catch (error) {
    materialRequests.value = [];
    materialRequestError.value = error?.response?.data?.message || error?.response?.data || '生产领料申请加载失败。';
  }
};

const addMaterialItem = () => {
  materialRequestError.value = '';
  const material = rawMaterialOptions.value.find((item) => String(item.id) === String(materialItemForm.productId));
  if (!material || !materialItemForm.quantity || Number(materialItemForm.quantity) <= 0) {
    materialRequestError.value = '请选择原材料并填写正确的需求数量。';
    return;
  }
  const existingItem = materialRequestItems.value.find((item) => String(item.materialProductId) === String(material.id));
  if (existingItem) {
    existingItem.requiredQuantity = Number(existingItem.requiredQuantity || 0) + Number(materialItemForm.quantity || 0);
  } else {
    materialRequestItems.value.push({
      materialProductId: material.id,
      productName: material.name,
      productSku: material.sku,
      requiredQuantity: Number(materialItemForm.quantity)
    });
  }
  materialItemForm.productId = '';
  materialItemForm.quantity = null;
};

const removeMaterialItem = (index) => {
  materialRequestItems.value = materialRequestItems.value.filter((_, itemIndex) => itemIndex !== index);
};

const submitMaterialRequest = async () => {
  materialRequestMessage.value = '';
  materialRequestError.value = '';
  if (!materialRequestForm.orderId) {
    materialRequestError.value = '请选择对应的生产订单。';
    return;
  }
  if (!materialRequestItems.value.length) {
    materialRequestError.value = '请至少添加一条原材料需求。';
    return;
  }
  try {
    const response = await productionApi.createMaterialRequest({
      orderId: Number(materialRequestForm.orderId),
      note: materialRequestForm.note || null,
      items: materialRequestItems.value.map((item) => ({
        materialProductId: item.materialProductId,
        requiredQuantity: item.requiredQuantity
      }))
    });
    materialRequestMessage.value = `领料申请 ${response?.data?.requestNo || ''} 已提交，等待仓库管理员审核。`;
    resetMaterialRequestForm();
    await loadMaterialRequests();
  } catch (error) {
    materialRequestError.value = error?.response?.data?.message || error?.response?.data || '生产领料申请提交失败。';
  }
};

const resetMaterialRequestForm = () => {
  materialRequestForm.orderId = productionOrders.value.find((order) => order.status === '生产中')?.id ? String(productionOrders.value.find((order) => order.status === '生产中').id) : '';
  materialRequestForm.note = '';
  materialRequestItems.value = [];
  materialItemForm.productId = '';
  materialItemForm.quantity = null;
};

const prefillMaterialRequest = (order) => {
  if (!order?.id) {
    return;
  }
  materialRequestForm.orderId = String(order.id);
  materialRequestMessage.value = '';
  materialRequestError.value = '';
};

const loadRecords = async () => {
  recordError.value = '';
  try {
    const response = await productionApi.listRecords({
      keyword: recordFilter.keyword || undefined,
      startDate: recordFilter.startDate || undefined,
      endDate: recordFilter.endDate || undefined
    });
    productionRecords.value = response.data || [];
  } catch (error) {
    productionRecords.value = [];
    recordError.value = error?.response?.data?.message || error?.response?.data || '生产记录加载失败。';
  } finally {
    await loadRecordOverview();
  }
};

const loadRecordOverview = async () => {
  try {
    const response = await productionApi.listRecordOverview({
      keyword: recordFilter.keyword || undefined,
      startDate: recordFilter.startDate || undefined,
      endDate: recordFilter.endDate || undefined
    });
    productionOverviewRecords.value = response.data || [];
  } catch (error) {
    productionOverviewRecords.value = [];
  }
};

const loadQualityAlerts = async () => {
  if (!canUpdateProduction.value) {
    qualityAlerts.value = [];
    return;
  }
  qualityAlertError.value = '';
  try {
    const response = await productionApi.listQualityAlerts();
    qualityAlerts.value = response.data || [];
  } catch (error) {
    qualityAlerts.value = [];
    qualityAlertError.value = error?.response?.data?.message || error?.response?.data || '质检异常加载失败。';
  }
};

const resetRecordFilter = async () => {
  recordFilter.keyword = '';
  recordFilter.startDate = '';
  recordFilter.endDate = '';
  await loadRecords();
};

const formatProducts = (items) => (items || []).map((item) => item.product?.name || `产品#${item.product?.id || '-'}`).join('，') || '-';
const formatQuantity = (items) => (items || []).reduce((sum, item) => sum + Number(item.quantity || 0), 0);
const formatNumber = (value) => Number(value || 0).toFixed(2);
const formatDate = (value) => (value ? new Date(value).toLocaleString() : '-');
const formatMaterialRequestStatus = (value) => value || '未申请';
const summarizeMaterialItems = (items) => (items || []).map((item) => `${item.materialProduct?.name || item.productName || '-'} x ${formatNumber(item.requiredQuantity)}`).join('；') || '-';
const formatRecordStatus = (value) => {
  if (value === 'WAREHOUSED') {
    return '已入库';
  }
  if (value === 'DONE') {
    return '待仓库入库';
  }
  if (value === '待质检') {
    return '待质检';
  }
  return value || '-';
};
const formatOperator = (name, id) => {
  if (name && id != null) {
    return `${name}（${id}）`;
  }
  return name || (id != null ? `#${id}` : '-');
};

const latestRequestByOrder = computed(() => materialRequests.value.reduce((accumulator, request) => {
  const orderId = request?.salesOrder?.id;
  if (orderId != null && !accumulator[orderId]) {
    accumulator[orderId] = request;
  }
  return accumulator;
}, {}));

const canCompleteOrder = (orderId) => latestRequestByOrder.value[orderId]?.status === '已备料待生产';

onMounted(async () => {
  await Promise.all([loadOrders(), loadRecords(), loadRecordOverview(), loadQualityAlerts(), loadMaterialRequests(), loadRawMaterialOptions()]);
});

watch(
  () => realtime.state.lastMessage,
  (message) => {
    if (message?.topic && (message.topic.startsWith('/topic/orders') || message.topic.startsWith('/topic/production') || message.topic.startsWith('/topic/quality') || message.topic.startsWith('/topic/procurement'))) {
      loadOrders();
      loadRecords();
      loadRecordOverview();
      loadQualityAlerts();
      loadMaterialRequests();
    }
  }
);
const productionGanttItems = computed(() => productionOverviewRecords.value.map((record) => ({
  id: record.id,
  label: `${record.planNo} / ${record.productName || '-'}`,
  meta: `${record.completedByName || record.createdByName || '生产管理员'} · ${formatNumber(record.plannedQuantity)}`,
  start: record.startDate || record.createdAt,
  end: record.completedAt || record.createdAt,
  shortText: record.completedByName || '生产',
  color: '#ea580c'
})));
</script>

