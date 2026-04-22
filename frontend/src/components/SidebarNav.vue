<template>
  <aside class="hidden md:flex flex-col h-screen w-72 fixed left-0 top-0 border-r border-slate-200 bg-white z-40 shadow-sm">
    <div class="px-6 py-8 border-b border-slate-200">
      <div class="inline-flex items-center gap-3 rounded-xl border border-slate-200 bg-slate-50 px-4 py-3">
        <span class="material-symbols-outlined text-blue-600">precision_manufacturing</span>
        <div>
          <h1 class="text-lg font-bold text-slate-900 tracking-tight">SteelOps Precision</h1>
          <p class="text-[10px] uppercase tracking-[0.3em] text-slate-500">Smart Supply Control</p>
        </div>
      </div>
      <div class="mt-5 flex items-center space-x-3 rounded-xl border border-slate-200 bg-slate-50 px-4 py-4">
        <div class="w-11 h-11 rounded-xl bg-blue-50 flex items-center justify-center">
          <span class="material-symbols-outlined text-blue-600">person</span>
        </div>
        <div>
          <p class="text-xs font-semibold uppercase tracking-widest text-slate-900">{{ userDisplayName }}</p>
          <p class="text-[10px] text-slate-500">{{ roleDisplayName }}</p>
        </div>
      </div>
    </div>
    <nav class="flex-1 space-y-2 px-3 py-4 overflow-auto">
      <RouterLink
        v-for="item in items"
        :key="item.to"
        :to="item.to"
        class="px-4 py-3 flex items-center gap-3 rounded-xl text-[12px] font-semibold transition-colors duration-150"
        :class="isActive(item.to) ? 'bg-blue-50 text-blue-700 border border-blue-100' : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900'"
      >
        <span class="material-symbols-outlined text-[20px]">{{ item.icon }}</span>
        <span>{{ item.label }}</span>
      </RouterLink>
    </nav>
    <div class="p-4 border-t border-slate-200">
      <div class="rounded-xl border border-slate-200 bg-slate-50 px-4 py-4 text-slate-700">
        <div class="flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.2em] text-slate-600">
          <span class="material-symbols-outlined text-blue-600">hub</span>
          系统联动
        </div>
        <p class="mt-2 text-[11px] leading-5 text-slate-500">统一查看订单、生产、仓储、采购与质量链路的协同状态。</p>
      </div>
    </div>
  </aside>
</template>

<script setup>
import { useRoute, RouterLink } from 'vue-router';

defineProps({
  items: {
    type: Array,
    default: () => []
  },
  userDisplayName: {
    type: String,
    default: 'SCM Controller'
  },
  roleDisplayName: {
    type: String,
    default: 'Terminal ID: 882-Alpha'
  }
});

const route = useRoute();
const isActive = (path) => route.path === path || route.path.startsWith(`${path}/`);
</script>

