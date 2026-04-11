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
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue';
import { orderApi } from '../api/services.js';
import { useRealtimeStore } from '../store/realtime.js';
import { useAuthStore } from '../store/auth.js';

const auth = useAuthStore();
const canUpdateProduction = computed(() => auth.hasPermission('production:update'));

const productionOrders = ref([]);
const loading = ref(false);
const statusMessage = ref('');
const statusError = ref('');
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
    statusMessage.value = `生产订单 ${orderId} 生产订单已完成。`;
    await loadOrders();
  } catch (error) {
    statusError.value = error?.response?.data?.message || error?.response?.data || '生产完成回传失败。';
  }
};

const formatProducts = (items) => (items || []).map((item) => item.product?.name || `产品#${item.product?.id || '-'}`).join('，') || '-';
const formatQuantity = (items) => (items || []).reduce((sum, item) => sum + Number(item.quantity || 0), 0);

onMounted(loadOrders);

watch(
  () => realtime.state.lastMessage,
  (message) => {
    if (message?.topic && (message.topic.startsWith('/topic/orders') || message.topic === '/topic/production')) {
      loadOrders();
    }
  }
);
</script>

