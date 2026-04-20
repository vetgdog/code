<template>
  <aside class="hidden md:flex flex-col h-screen w-72 fixed left-0 top-0 border-r border-cyan-400/10 bg-slate-950/70 backdrop-blur-2xl z-40 shadow-[0_0_60px_rgba(6,182,212,0.08)]">
    <div class="px-6 py-8 border-b border-cyan-400/10">
      <div class="inline-flex items-center gap-3 rounded-2xl border border-cyan-400/20 bg-slate-950/60 px-4 py-3 shadow-[inset_0_1px_0_rgba(125,211,252,0.12)]">
        <span class="material-symbols-outlined text-cyan-300">precision_manufacturing</span>
        <div>
          <h1 class="text-lg font-black text-white tracking-tight">SteelOps Precision</h1>
          <p class="text-[10px] uppercase tracking-[0.3em] text-cyan-200/60">Smart Supply Control</p>
        </div>
      </div>
      <div class="mt-5 flex items-center space-x-3 rounded-2xl border border-cyan-400/12 bg-slate-950/40 px-4 py-4">
        <div class="w-11 h-11 rounded-2xl bg-gradient-to-br from-cyan-500/25 to-blue-500/25 flex items-center justify-center shadow-[0_0_24px_rgba(34,211,238,0.18)]">
          <span class="material-symbols-outlined text-cyan-200">person</span>
        </div>
        <div>
          <p class="text-xs font-semibold uppercase tracking-widest text-slate-100">{{ userDisplayName }}</p>
          <p class="text-[10px] text-cyan-200/60">{{ roleDisplayName }}</p>
        </div>
      </div>
    </div>
    <nav class="flex-1 space-y-2 px-3 py-4 overflow-auto">
      <RouterLink
        v-for="item in items"
        :key="item.to"
        :to="item.to"
        class="px-4 py-3.5 flex items-center gap-3 rounded-2xl text-[11px] font-semibold uppercase tracking-[0.18em] transition-all duration-200"
        :class="isActive(item.to) ? 'bg-gradient-to-r from-cyan-500/18 to-blue-500/18 text-cyan-100 shadow-[inset_0_0_0_1px_rgba(34,211,238,0.28),0_0_30px_rgba(14,165,233,0.08)]' : 'text-slate-300 hover:bg-slate-900/80 hover:text-cyan-100'"
      >
        <span class="material-symbols-outlined text-[20px]">{{ item.icon }}</span>
        <span>{{ item.label }}</span>
      </RouterLink>
    </nav>
    <div class="p-4 border-t border-cyan-400/10">
      <div class="rounded-2xl border border-cyan-400/12 bg-slate-950/40 px-4 py-4 text-cyan-100/70">
        <div class="flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.2em]">
          <span class="material-symbols-outlined text-cyan-300">hub</span>
          System Mesh
        </div>
        <p class="mt-2 text-[11px] leading-5 text-slate-400">实时监控订单、生产、仓储、采购与质量链路的协同状态。</p>
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

