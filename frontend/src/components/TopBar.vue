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
            <p class="font-semibold">{{ event.messageType || 'MESSAGE' }} · {{ event.entity || 'Event' }}</p>
            <p class="text-slate-500 mt-1">{{ event.topic }} {{ event.entityId ? `#${event.entityId}` : '' }}</p>
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

