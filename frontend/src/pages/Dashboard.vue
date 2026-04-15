<template>
  <div class="space-y-6">
    <section class="grid grid-cols-1 md:grid-cols-4 gap-6">
      <div class="bg-white p-5 rounded-lg shadow-sm border border-outline-variant/10">
        <p class="text-on-surface-variant text-[10px] font-bold uppercase tracking-widest mb-1">订单总数</p>
        <div class="flex items-end justify-between">
          <h3 class="text-2xl font-black tracking-tighter">{{ orderCount }}</h3>
          <span class="text-xs text-primary font-bold flex items-center">实时</span>
        </div>
      </div>
      <div class="bg-white p-5 rounded-lg shadow-sm border border-outline-variant/10">
        <p class="text-on-surface-variant text-[10px] font-bold uppercase tracking-widest mb-1">订单总额</p>
        <div class="flex items-end justify-between">
          <h3 class="text-2xl font-black tracking-tighter">¥{{ totalAmount }}</h3>
          <span class="text-xs text-on-surface-variant">统计</span>
        </div>
      </div>
      <div class="bg-white p-5 rounded-lg shadow-sm border border-outline-variant/10">
        <p class="text-on-surface-variant text-[10px] font-bold uppercase tracking-widest mb-1">活跃订单</p>
        <div class="flex items-end justify-between">
          <h3 class="text-2xl font-black tracking-tighter">{{ activeOrders }}</h3>
          <span class="text-xs text-primary font-bold flex items-center">+{{ activeRatio }}%</span>
        </div>
      </div>
      <div class="bg-white p-5 rounded-lg shadow-sm border border-outline-variant/10">
        <p class="text-on-surface-variant text-[10px] font-bold uppercase tracking-widest mb-1">系统连接</p>
        <div class="flex items-end justify-between">
          <h3 class="text-2xl font-black tracking-tighter">{{ apiStatus }}</h3>
          <span class="text-xs text-on-surface-variant">API</span>
        </div>
      </div>
    </section>

    <section class="grid grid-cols-1 lg:grid-cols-3 gap-6">
      <div class="lg:col-span-2 bg-white rounded-lg border border-outline-variant/10">
        <div class="p-5 border-b border-surface-container-low flex items-center justify-between">
          <div>
            <h4 class="text-sm font-bold tracking-tight">最新订单</h4>
            <p class="text-[10px] text-on-surface-variant uppercase tracking-widest">Recent orders</p>
          </div>
          <button class="text-xs text-primary font-semibold" @click="reload">刷新</button>
        </div>
        <div class="p-5">
          <div v-if="loading" class="text-sm text-on-surface-variant">加载中...</div>
          <div v-else-if="orders.length === 0" class="text-sm text-on-surface-variant">暂无订单数据。</div>
          <table v-else class="w-full text-sm">
            <thead class="text-xs text-on-surface-variant">
              <tr class="text-left">
                <th class="pb-2">订单号</th>
                <th class="pb-2">状态</th>
                <th class="pb-2">总额</th>
                <th class="pb-2">下单时间</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="order in recentOrders" :key="order.id" class="border-t border-outline-variant/20">
                <td class="py-3 font-semibold">{{ order.orderNo }}</td>
                <td class="py-3">{{ order.status }}</td>
                <td class="py-3">¥{{ formatAmount(order.totalAmount) }}</td>
                <td class="py-3">{{ formatDate(order.orderDate) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
      <div class="bg-white rounded-lg border border-outline-variant/10 p-5 space-y-3">
        <h4 class="text-sm font-bold tracking-tight">运行提示</h4>
        <div class="text-xs text-on-surface-variant">1. 采购模块仅 SUPPLIER 角色可访问。</div>
        <div class="text-xs text-on-surface-variant">2. 客户门户仅 CUSTOMER 角色可访问。</div>
        <div class="text-xs text-on-surface-variant">3. 订单创建后可一键生成生产计划。</div>
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
import { computed, onMounted, ref, watch } from 'vue';
import { orderApi } from '../api/services.js';
import { useRealtimeStore } from '../store/realtime.js';

const orders = ref([]);
const loading = ref(false);
const apiStatus = ref('待检查');
const realtime = useRealtimeStore();

const fetchOrders = async () => {
  loading.value = true;
  try {
    const response = await orderApi.list();
    orders.value = response.data || [];
    apiStatus.value = '在线';
  } catch (error) {
    apiStatus.value = '离线';
  } finally {
    loading.value = false;
  }
};

const reload = () => fetchOrders();

onMounted(fetchOrders);

watch(
  () => realtime.state.lastMessage,
  (message) => {
    if (!message) {
      return;
    }
    if (message.topic?.startsWith('/topic/orders')) {
      fetchOrders();
    }
  }
);

const orderCount = computed(() => orders.value.length);
const totalAmount = computed(() => {
  const sum = orders.value.reduce((acc, item) => acc + (item.totalAmount || 0), 0);
  return sum.toFixed(2);
});
const activeOrders = computed(() => orders.value.filter((item) => item.status !== 'DONE').length);
const activeRatio = computed(() => (orderCount.value === 0 ? 0 : Math.round((activeOrders.value / orderCount.value) * 100)));
const recentOrders = computed(() => orders.value.slice(0, 6));
const realtimeEvents = computed(() => realtime.state.events.slice(0, 8));

const formatAmount = (value) => (value || 0).toFixed(2);
const formatDate = (value) => (value ? new Date(value).toLocaleString() : '-');
</script>

