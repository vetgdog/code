<template>
  <section class="rounded-xl border border-outline-variant/20 bg-white p-4">
    <div class="flex items-center justify-between gap-3">
      <div>
        <h4 class="text-sm font-bold tracking-tight">{{ title }}</h4>
        <p class="mt-1 text-xs text-on-surface-variant">展示当前统计周内的时间分布，便于快速查看工作负载。</p>
      </div>
      <span class="text-xs text-on-surface-variant">{{ weekLabel }}</span>
    </div>

    <div v-if="!visibleItems.length" class="mt-4 text-sm text-on-surface-variant">{{ emptyText }}</div>
    <div v-else class="mt-4 overflow-x-auto">
      <div class="min-w-[780px]">
        <div class="grid grid-cols-[240px_repeat(7,minmax(0,1fr))] gap-2 text-xs text-on-surface-variant font-semibold">
          <div>任务 / 责任人</div>
          <div v-for="day in days" :key="day.key" class="text-center">{{ day.label }}</div>
        </div>
        <div class="mt-3 space-y-3">
          <div v-for="item in visibleItems" :key="item.id" class="grid grid-cols-[240px_1fr] gap-2 items-center">
            <div class="pr-3">
              <div class="text-sm font-semibold truncate">{{ item.label }}</div>
              <div class="mt-1 text-xs text-on-surface-variant truncate">{{ item.meta || '-' }}</div>
            </div>
            <div class="relative h-10 rounded-lg bg-slate-50 border border-outline-variant/20 overflow-hidden">
              <div class="absolute inset-0 grid grid-cols-7">
                <div v-for="day in days" :key="`${item.id}-${day.key}`" class="border-r border-outline-variant/10 last:border-r-0" />
              </div>
              <div class="absolute top-1/2 h-6 -translate-y-1/2 rounded-md px-2 text-xs font-semibold text-white flex items-center"
                   :style="barStyle(item)">
                {{ item.barText || item.shortText || '进行中' }}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue';

const props = defineProps({
  title: { type: String, default: '周甘特图' },
  items: { type: Array, default: () => [] },
  emptyText: { type: String, default: '当前没有可展示的数据。' }
});

const toDate = (value) => {
  const date = value ? new Date(value) : null;
  return date && !Number.isNaN(date.getTime()) ? date : null;
};

const latestDate = computed(() => {
  const timestamps = props.items
    .flatMap((item) => [toDate(item.start)?.getTime(), toDate(item.end)?.getTime()])
    .filter((value) => Number.isFinite(value));
  return timestamps.length ? new Date(Math.max(...timestamps)) : new Date();
});

const startOfWeek = computed(() => {
  const base = new Date(latestDate.value);
  const day = base.getDay() || 7;
  base.setHours(0, 0, 0, 0);
  base.setDate(base.getDate() - day + 1);
  return base;
});

const endOfWeek = computed(() => {
  const end = new Date(startOfWeek.value);
  end.setDate(end.getDate() + 6);
  end.setHours(23, 59, 59, 999);
  return end;
});

const days = computed(() => Array.from({ length: 7 }, (_, index) => {
  const date = new Date(startOfWeek.value);
  date.setDate(date.getDate() + index);
  return {
    key: date.toISOString(),
    label: `${date.getMonth() + 1}/${date.getDate()}`
  };
}));

const visibleItems = computed(() => props.items
  .map((item, index) => ({
    id: item.id ?? `${index}-${item.label}`,
    label: item.label || '-',
    meta: item.meta || '',
    shortText: item.shortText || '',
    barText: item.barText || '',
    color: item.color || '#2563eb',
    start: toDate(item.start) || startOfWeek.value,
    end: toDate(item.end) || toDate(item.start) || startOfWeek.value
  }))
  .filter((item) => item.end >= startOfWeek.value && item.start <= endOfWeek.value)
  .slice(0, 12));

const barStyle = (item) => {
  const rangeStart = startOfWeek.value.getTime();
  const rangeEnd = endOfWeek.value.getTime();
  const start = Math.max(item.start.getTime(), rangeStart);
  const end = Math.max(start, Math.min(item.end.getTime(), rangeEnd));
  const total = rangeEnd - rangeStart || 1;
  const left = ((start - rangeStart) / total) * 100;
  const width = Math.max(6, ((end - start) / total) * 100);
  return {
    left: `${left}%`,
    width: `${width}%`,
    backgroundColor: item.color
  };
};

const formatDate = (date) => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
const weekLabel = computed(() => `${formatDate(startOfWeek.value)} ~ ${formatDate(endOfWeek.value)}`);
</script>

