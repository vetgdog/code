<template>
  <div class="space-y-6">
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
              <td class="py-3">
                <button
                  v-if="canUpdateProduction && order.status === '生产中'"
                  class="text-xs text-orange-600"
                  @click="completeProduction(order.id)"
                >
                  生产完成并通知仓库
                </button>
                <span v-else class="text-xs text-on-surface-variant">只读</span>
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
          <p class="mt-1 text-xs text-on-surface-variant">展示所有已完成并完成入库的生产计划记录。</p>
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
              <td class="py-3">{{ formatDate(record.startDate) }}</td>
              <td class="py-3">{{ formatDate(record.completedAt) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { orderApi, productionApi } from '../api/services.js';
import { useRealtimeStore } from '../store/realtime.js';
import { useAuthStore } from '../store/auth.js';

const auth = useAuthStore();
const canUpdateProduction = computed(() => auth.hasPermission('production:update'));

const productionOrders = ref([]);
const loading = ref(false);
const statusMessage = ref('');
const statusError = ref('');
const productionRecords = ref([]);
const recordError = ref('');
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
    productionOrders.value = all.filter((order) => ['生产中', '待仓库核查'].includes(order.status));
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
    statusMessage.value = `生产订单 ${orderId} 已完成，系统已自动入库并通知仓库管理员。`;
    await Promise.all([loadOrders(), loadRecords()]);
  } catch (error) {
    statusError.value = error?.response?.data?.message || error?.response?.data || '生产完成回传失败。';
  }
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
const formatRecordStatus = (value) => (value === 'WAREHOUSED' ? '已入库' : value || '-');

onMounted(async () => {
  await Promise.all([loadOrders(), loadRecords()]);
});

watch(
  () => realtime.state.lastMessage,
  (message) => {
    if (message?.topic && (message.topic.startsWith('/topic/orders') || message.topic === '/topic/production')) {
      loadOrders();
      loadRecords();
    }
  }
);
</script>

