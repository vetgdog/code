<template>
  <aside class="hidden md:flex flex-col h-screen w-64 fixed left-0 top-0 border-r border-slate-800/80 bg-gradient-to-b from-slate-950 to-slate-900 z-40 shadow-xl">
    <div class="px-6 py-8">
      <h1 class="text-xl font-black text-white tracking-tight">SteelOps Precision</h1>
      <div class="mt-4 flex items-center space-x-3">
        <div class="w-10 h-10 rounded-lg bg-slate-800 flex items-center justify-center">
          <span class="material-symbols-outlined text-blue-300">person</span>
        </div>
        <div>
          <p class="text-xs font-semibold uppercase tracking-widest text-slate-100">{{ userDisplayName }}</p>
          <p class="text-[10px] text-slate-400">{{ roleDisplayName }}</p>
        </div>
      </div>
    </div>
    <nav class="flex-1 space-y-1.5 px-1">
      <RouterLink
        v-for="item in items"
        :key="item.to"
        :to="item.to"
        class="mx-2 px-4 py-3 flex items-center gap-3 rounded-lg text-xs font-semibold uppercase tracking-widest transition-all"
        :class="isActive(item.to) ? 'bg-blue-500/25 text-blue-100 shadow-[inset_0_0_0_1px_rgba(59,130,246,0.35)]' : 'text-slate-300 hover:bg-slate-800/70'"
      >
        <span class="material-symbols-outlined">{{ item.icon }}</span>
        <span>{{ item.label }}</span>
      </RouterLink>
    </nav>
    <div class="p-4 border-t border-slate-800/80">
      <div class="flex items-center gap-3 px-4 py-2 rounded-lg text-slate-400 hover:bg-slate-800/70 transition-colors">
        <span class="material-symbols-outlined">help_outline</span>
        <span class="text-xs font-semibold uppercase tracking-widest">Support</span>
      </div>
      <div class="flex items-center gap-3 px-4 py-2 rounded-lg text-slate-400 hover:bg-slate-800/70 transition-colors">
        <span class="material-symbols-outlined">terminal</span>
        <span class="text-xs font-semibold uppercase tracking-widest">Logs</span>
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
const isActive = (path) => route.path === path;
</script>

