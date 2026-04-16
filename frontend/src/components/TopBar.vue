<template>
  <header class="flex items-center justify-between px-6 w-full sticky top-0 z-40 h-16 border-b border-slate-200 bg-white/90 backdrop-blur-sm">
    <div class="flex items-center gap-8">
      <div>
        <span class="text-lg font-bold tracking-tight text-slate-900">{{ title }}</span>
        <p v-if="subtitle" class="text-[11px] text-slate-500 mt-0.5">{{ subtitle }}</p>
      </div>
    </div>
    <div class="flex items-center gap-4 relative">
      <div class="hidden md:flex items-center bg-slate-100 rounded-full px-3 py-1.5 gap-2 border border-slate-200">
        <span class="w-2 h-2 rounded-full" :class="realtimeConnected ? 'bg-emerald-500' : 'bg-amber-500'"></span>
        <span class="text-xs font-semibold text-slate-600">实时推送: {{ realtimeConnected ? '已连接' : '重连中' }}</span>
      </div>
      <button class="relative p-2 text-slate-500 hover:bg-slate-100 rounded-full transition-colors" aria-label="Notifications" @click="toggleNotifications">
        <span class="material-symbols-outlined">notifications</span>
        <span v-if="realtime.state.unreadCount" class="absolute -top-0.5 -right-0.5 min-w-4 h-4 px-1 rounded-full bg-primary text-white text-[10px] leading-4 text-center">{{ badgeCount }}</span>
      </button>
      <button class="p-2 text-slate-500 hover:bg-slate-100 rounded-full transition-colors" aria-label="Settings">
        <span class="material-symbols-outlined">settings</span>
      </button>
      <button
        class="px-3 py-1.5 rounded-lg bg-blue-600 hover:bg-blue-700 text-white text-xs font-semibold transition-colors"
        @click="$emit('logout')"
      >
        退出登录
      </button>

      <div v-if="showNotifications" class="absolute top-12 right-0 w-80 bg-white border border-slate-200 rounded-xl shadow-xl z-50">
        <div class="px-4 py-3 border-b border-slate-200 flex items-center justify-between">
          <h4 class="text-sm font-semibold">实时通知</h4>
          <div class="flex items-center gap-3">
            <button class="text-xs text-primary" @click="realtime.markAllRead">全部已读</button>
            <button class="text-xs text-slate-500" @click="showNotifications = false">关闭</button>
          </div>
        </div>
        <div class="max-h-72 overflow-auto p-2 space-y-1">
          <div v-if="!realtime.state.events.length" class="text-xs text-slate-500 px-2 py-3">暂无消息</div>
          <div v-for="event in latestEvents" :key="`${event.topic}-${event.timestamp}-${event.entityId || 'none'}`" class="px-3 py-2 rounded-lg border border-slate-200 bg-slate-50 text-xs">
            <p class="font-semibold">{{ formatNotificationTitle(event) }}</p>
            <p class="text-slate-500 mt-1">{{ formatNotificationMeta(event) }}</p>
            <p class="text-slate-500 mt-1">{{ formatDate(event.timestamp) }}</p>
          </div>
        </div>
      </div>
    </div>
  </header>
</template>

<script setup>
import { computed, ref, watch } from 'vue';
import { useRealtimeStore } from '../store/realtime.js';

defineProps({
  title: { type: String, default: '' },
  subtitle: { type: String, default: '' },
  realtimeConnected: { type: Boolean, default: false }
});

const realtime = useRealtimeStore();
const showNotifications = ref(false);

const latestEvents = computed(() => realtime.state.events.slice(0, 10));
const badgeCount = computed(() => (realtime.state.unreadCount > 99 ? '99+' : String(realtime.state.unreadCount)));

const toggleNotifications = () => {
  showNotifications.value = !showNotifications.value;
  if (showNotifications.value) {
    realtime.markAllRead();
  }
};

const extractOrder = (event) => {
  if (!event?.payload) {
    return null;
  }
  if (event.payload.orderNo) {
    return event.payload;
  }
  if (event.payload.order?.orderNo) {
    return event.payload.order;
  }
  return null;
};

const extractPlans = (event) => {
  if (!event?.payload) {
    return [];
  }
  if (Array.isArray(event.payload.productionPlans)) {
    return event.payload.productionPlans;
  }
  return [];
};

const formatNotificationTitle = (event) => {
  const order = extractOrder(event);
  const orderNo = order?.orderNo || (event?.entityId ? `#${event.entityId}` : '');
  const planNo = extractPlans(event)[0]?.planNo;

   if (event?.payload?.notificationTitle) {
    return event.payload.notificationTitle;
  }

  switch (event?.messageType) {
    case 'ORDER_SUBMITTED':
      return `订单 ${orderNo} 已成功下单，请耐心等候。`;
    case 'ORDER_WORKFLOW_UPDATED':
      return `订单 ${orderNo} 状态已更新，请查看。`;
    case 'WAREHOUSE_ACTION_REQUIRED':
      return `接收一条新的订单 ${orderNo}，请查看。`;
    case 'ORDER_SHIPPED_BY_WAREHOUSE':
      return `订单 ${orderNo} 已发货，请查看。`;
    case 'ORDER_SHIPPED_TO_CUSTOMER':
      return `您的订单 ${orderNo} 已发货。`;
    case 'ORDER_PRODUCTION_REQUIRED':
      return `生产计划订单 ${planNo || '-'} 已下发，请查看。`;
    case 'ORDER_PRODUCTION_DONE':
      return `生产计划订单 ${planNo || '-'} 已完成，请查看！`;
    case 'PRODUCTION_STOCK_IN_CONFIRMED':
      return `${planNo || '生产订单'} 已完成核验并自动入库。`;
    case 'ORDER_READY_TO_SHIP':
      return `订单 ${orderNo} 库存已核验通过，请查看。`;
    case 'SALES_RECORD_CREATED':
      return `订单 ${orderNo} 已归档为销售记录。`;
    case 'PROCUREMENT_ORDER_CREATED':
      return '收到一张新的采购单，请处理。';
    case 'PROCUREMENT_ORDER_UPDATED':
      return '采购单状态已更新，请查看。';
    case 'PROCUREMENT_ORDER_ACCEPTED':
      return '供应商已接单，请查看采购单进度。';
    case 'PROCUREMENT_ORDER_REJECTED':
      return '供应商已拒绝采购单，请查看。';
    case 'PROCUREMENT_ORDER_SHIPPED':
      return '供应商已发货，请采购管理员查看。';
    case 'PROCUREMENT_WAREHOUSE_CONFIRM_REQUIRED':
      return '采购到货待仓库确认入库。';
    case 'PROCUREMENT_ORDER_WAREHOUSED':
      return '采购单已确认收货并自动入库。';
    case 'QUALITY_PENDING':
      return '有新的成品批次待质检，请查看。';
    case 'QUALITY_PASSED':
      return '成品批次质检合格。';
    case 'QUALITY_REWORK_REQUIRED':
      return '发现不合格成品，已通知责任生产管理员。';
    default:
      return `${event?.messageType || '系统消息'} · ${event?.entity || 'Event'}`;
  }
};

const formatNotificationMeta = (event) => {
  if (event?.payload?.notificationMeta) {
    return event.payload.notificationMeta;
  }
  const order = extractOrder(event);
  if (order?.shippingAddress) {
    return `收货地址：${order.shippingAddress}`;
  }
  return `${event.topic} ${event.entityId ? `#${event.entityId}` : ''}`;
};

const formatDate = (value) => (value ? new Date(value).toLocaleString() : '-');

watch(
  () => realtime.state.connected,
  (connected) => {
    if (!connected) {
      showNotifications.value = false;
    }
  }
);
</script>

