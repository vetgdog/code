<template>
  <section class="bg-white rounded-lg border border-outline-variant/10 overflow-hidden">
    <div class="p-5 border-b border-surface-container-low flex items-center justify-between gap-4">
      <div>
        <h3 class="text-sm font-bold tracking-tight">{{ title }}</h3>
        <p class="mt-1 text-xs text-on-surface-variant">{{ description }}</p>
      </div>
      <div class="text-right text-xs text-on-surface-variant">
        <div>统计范围：{{ rangeLabel }}</div>
        <div class="mt-1">当前页：{{ currentPage }} / {{ totalPages }}</div>
      </div>
    </div>

    <div class="p-5">
      <div class="grid grid-cols-1 gap-4 xl:grid-cols-[minmax(0,1fr)_280px] xl:items-start">
        <div class="space-y-4">
          <div class="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
            <div class="grid grid-cols-1 gap-3 sm:grid-cols-3 flex-1">
              <div class="rounded-xl border border-outline-variant/20 bg-slate-50 px-4 py-3">
                <div class="text-xs text-on-surface-variant">产品总数</div>
                <div class="mt-1 text-lg font-bold text-slate-800">{{ filteredProducts.length }}</div>
              </div>
              <div class="rounded-xl border border-outline-variant/20 bg-slate-50 px-4 py-3">
                <div class="text-xs text-on-surface-variant">上周总产量</div>
                <div class="mt-1 text-lg font-bold text-slate-800">{{ formatQuantity(totalQuantity) }}</div>
              </div>
              <div class="rounded-xl border border-outline-variant/20 bg-slate-50 px-4 py-3">
                <div class="text-xs text-on-surface-variant">主力产品</div>
                <div class="mt-1 truncate text-sm font-bold text-slate-800" :title="peakProductLabel">{{ peakProductLabel }}</div>
              </div>
            </div>
            <div class="w-full md:w-72">
              <input
                v-model.trim="keyword"
                type="text"
                class="w-full rounded-lg border border-outline-variant/40 bg-white px-3 py-2 text-sm text-slate-700 shadow-sm outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/10"
                placeholder="搜索产品名称"
              />
            </div>
          </div>

          <div v-if="!filteredProducts.length" class="rounded-xl border border-outline-variant/20 bg-slate-50 px-4 py-6 text-sm text-on-surface-variant">
            {{ emptyStateText }}
          </div>

          <div v-else class="rounded-2xl border border-slate-300 bg-[#f4f4f4] p-4 shadow-sm">
            <div class="mb-4 flex items-center justify-between gap-3">
              <div>
                <h4 class="text-sm font-semibold text-slate-800">上周产品产量柱状图</h4>
                <p class="mt-1 text-xs text-slate-500">横坐标为产品名称，纵坐标为产品产量；每页展示 5 个产品。</p>
              </div>
              <div class="text-xs text-slate-500">已按产量从高到低排序</div>
            </div>

            <div ref="chartContainerRef" class="relative rounded-xl border border-slate-400 bg-white px-4 py-4 shadow-inner">
              <div
                v-if="tooltip.visible && tooltip.product"
                class="pointer-events-none absolute z-20 w-56 rounded-lg border border-slate-300 bg-white/95 px-3 py-2 text-xs text-slate-700 shadow-lg backdrop-blur-sm"
                :style="tooltipStyle"
              >
                <div class="font-semibold text-slate-900">{{ tooltip.product.label }}</div>
                <div class="mt-1 text-slate-500">{{ tooltip.product.sku }}</div>
                <div class="mt-2">上周产量：<span class="font-semibold text-slate-900">{{ formatQuantity(tooltip.product.quantity) }}</span></div>
                <div class="mt-1">记录数：<span class="font-semibold text-slate-900">{{ tooltip.product.recordCount }}</span></div>
                <div class="mt-1">生产管理员：<span class="font-semibold text-slate-900">{{ tooltip.product.managerSummary }}</span></div>
              </div>

              <div class="grid grid-cols-[56px_minmax(0,1fr)] gap-3">
                <div class="relative h-80">
                  <div
                    v-for="tick in yAxisTicks"
                    :key="`tick-${tick}`"
                    class="absolute right-0 -translate-y-1/2 text-xs text-slate-600"
                    :style="{ bottom: `${(tick / yAxisMax) * 100}%` }"
                  >
                    {{ formatTick(tick) }}
                  </div>
                </div>

                <div class="relative h-80 border-b border-l border-slate-500 bg-white">
                  <div
                    v-for="tick in yAxisTicks"
                    :key="`line-${tick}`"
                    class="absolute left-0 right-0 border-t border-slate-200"
                    :style="{ bottom: `${(tick / yAxisMax) * 100}%` }"
                  />

                  <div class="absolute inset-0 flex items-end justify-around gap-4 px-4 pb-0 pt-3">
                    <div v-for="product in pagedProducts" :key="product.key" class="flex h-full w-full max-w-[132px] flex-col items-center justify-end">
                      <div class="mb-2 text-xs font-semibold text-slate-700">{{ formatQuantity(product.quantity) }}</div>
                      <div
                        class="w-14 rounded-t-sm border border-[#a05a18] bg-[#d98634] shadow-[inset_0_1px_0_rgba(255,255,255,0.35)] transition-all duration-300 hover:bg-[#c97826]"
                        :style="{ height: `${barHeight(product.quantity)}%` }"
                        @mouseenter="showTooltip($event, product)"
                        @mousemove="moveTooltip($event, product)"
                        @mouseleave="hideTooltip"
                      />
                      <div class="mt-3 w-full truncate text-center text-xs font-medium text-slate-800" :title="product.label">{{ product.label }}</div>
                      <div class="mt-1 text-[11px] text-slate-500">{{ product.sku }}</div>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <div class="mt-4 flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
              <div class="text-xs text-slate-500">共 {{ filteredProducts.length }} 个产品，当前显示第 {{ pageStart }} - {{ pageEnd }} 个。</div>
              <div class="flex items-center gap-2">
                <button
                  type="button"
                  class="rounded border border-slate-300 bg-white px-3 py-1.5 text-xs font-semibold text-slate-700 shadow-sm transition hover:border-primary hover:text-primary disabled:cursor-not-allowed disabled:opacity-50"
                  :disabled="currentPage <= 1"
                  @click="currentPage -= 1"
                >
                  上一页
                </button>
                <div class="flex items-center gap-1">
                  <template v-for="item in pageItems" :key="`page-${item}`">
                    <span v-if="item === 'ellipsis'" class="px-2 text-xs text-slate-400">...</span>
                    <button
                      v-else
                      type="button"
                      class="min-w-8 rounded border px-2 py-1 text-xs font-semibold shadow-sm transition"
                      :class="Number(item) === currentPage ? 'border-primary bg-primary text-white' : 'border-slate-300 bg-white text-slate-700 hover:border-primary hover:text-primary'"
                      @click="goToPage(Number(item))"
                    >
                      {{ item }}
                    </button>
                  </template>
                </div>
                <button
                  type="button"
                  class="rounded border border-slate-300 bg-white px-3 py-1.5 text-xs font-semibold text-slate-700 shadow-sm transition hover:border-primary hover:text-primary disabled:cursor-not-allowed disabled:opacity-50"
                  :disabled="currentPage >= totalPages"
                  @click="currentPage += 1"
                >
                  下一页
                </button>
              </div>
            </div>
          </div>
        </div>

        <aside class="rounded-2xl border border-outline-variant/20 bg-white p-4 shadow-sm">
          <h4 class="text-sm font-semibold text-slate-800">产品产量概览</h4>
          <p class="mt-1 text-xs text-on-surface-variant">当前基于上周全部生产记录统计，可快速查看重点生产产品与管理人员分布。</p>

          <div class="mt-4 space-y-3">
            <div v-for="product in filteredProducts.slice(0, 5)" :key="`summary-${product.key}`" class="rounded-lg border border-slate-100 bg-slate-50 px-3 py-3">
              <div class="flex items-start justify-between gap-3">
                <div class="min-w-0">
                  <div class="truncate text-sm font-semibold text-slate-800" :title="product.label">{{ product.label }}</div>
                  <div class="mt-1 text-[11px] text-slate-500">{{ product.sku }}</div>
                </div>
                <div class="text-right text-[11px] text-slate-500">记录数 {{ product.recordCount }}</div>
              </div>
              <div class="mt-2 text-sm text-slate-700">产量：<span class="font-semibold text-slate-900">{{ formatQuantity(product.quantity) }}</span></div>
              <div class="mt-1 text-sm text-slate-700">管理员：<span class="font-semibold text-slate-900">{{ product.managerSummary }}</span></div>
            </div>
          </div>
        </aside>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue';

const props = defineProps({
  title: { type: String, default: '上周产品产量柱状图（全员统计）' },
  description: { type: String, default: '展示上一个周全部生产记录的产品产量分布。' },
  records: { type: Array, default: () => [] },
  emptyText: { type: String, default: '上一个周没有可用于统计的生产记录。' }
});

const PAGE_SIZE = 5;

const keyword = ref('');
const currentPage = ref(1);
const chartContainerRef = ref(null);
const tooltip = ref({ visible: false, product: null, left: 0, top: 0 });

const productStats = computed(() => {
  const grouped = new Map();
  for (const record of props.records || []) {
    const productKey = `${record?.productSku || '-'}::${record?.productName || '-'}`;
    if (!record?.productName) {
      continue;
    }
    if (!grouped.has(productKey)) {
      grouped.set(productKey, {
        key: productKey,
        label: record.productName,
        sku: record.productSku || '-',
        quantity: 0,
        recordCount: 0,
        managers: new Set(),
        latestAt: record.completedAt || record.createdAt || null
      });
    }
    const current = grouped.get(productKey);
    current.quantity += Number(record?.plannedQuantity || 0);
    current.recordCount += 1;
    if (record?.completedByName) {
      current.managers.add(record.completedByName);
    } else if (record?.createdByName) {
      current.managers.add(record.createdByName);
    }
    const candidateTime = record?.completedAt || record?.createdAt || null;
    if (candidateTime && (!current.latestAt || new Date(candidateTime).getTime() > new Date(current.latestAt).getTime())) {
      current.latestAt = candidateTime;
    }
  }

  return [...grouped.values()]
    .map((item) => ({
      ...item,
      managerSummary: [...item.managers].filter(Boolean).join('、') || '未登记',
      sortLabel: item.label.toLowerCase()
    }))
    .sort((left, right) => right.quantity - left.quantity || right.recordCount - left.recordCount || left.sortLabel.localeCompare(right.sortLabel, 'zh-CN'));
});

const filteredProducts = computed(() => {
  const normalizedKeyword = keyword.value.trim().toLowerCase();
  if (!normalizedKeyword) {
    return productStats.value;
  }
  return productStats.value.filter((product) => product.label.toLowerCase().includes(normalizedKeyword));
});

const totalPages = computed(() => Math.max(1, Math.ceil(filteredProducts.value.length / PAGE_SIZE)));
const pagedProducts = computed(() => {
  const start = (currentPage.value - 1) * PAGE_SIZE;
  return filteredProducts.value.slice(start, start + PAGE_SIZE);
});
const pageStart = computed(() => (filteredProducts.value.length ? (currentPage.value - 1) * PAGE_SIZE + 1 : 0));
const pageEnd = computed(() => Math.min(currentPage.value * PAGE_SIZE, filteredProducts.value.length));
const totalQuantity = computed(() => filteredProducts.value.reduce((sum, product) => sum + Number(product.quantity || 0), 0));
const peakProduct = computed(() => filteredProducts.value[0] || null);
const peakProductLabel = computed(() => peakProduct.value ? `${peakProduct.value.label}（${formatQuantity(peakProduct.value.quantity)}）` : '-');
const rangeLabel = computed(() => {
  const timestamps = (props.records || [])
    .map((record) => record?.completedAt || record?.createdAt)
    .filter(Boolean)
    .map((value) => new Date(value))
    .filter((date) => !Number.isNaN(date.getTime()))
    .sort((left, right) => left.getTime() - right.getTime());
  if (!timestamps.length) {
    return '-';
  }
  return `${formatDateOnly(timestamps[0])} ~ ${formatDateOnly(timestamps[timestamps.length - 1])}`;
});

const yAxisMax = computed(() => {
  const maxValue = Math.max(0, ...pagedProducts.value.map((product) => Number(product.quantity || 0)));
  if (maxValue <= 5) {
    return 5;
  }
  return Math.ceil(maxValue / 5) * 5;
});
const yAxisTicks = computed(() => {
  const step = Math.max(1, Math.ceil(yAxisMax.value / 4));
  return [0, step, step * 2, step * 3, yAxisMax.value];
});
const tooltipStyle = computed(() => ({ left: `${tooltip.value.left}px`, top: `${tooltip.value.top}px` }));
const emptyStateText = computed(() => keyword.value ? '没有找到匹配的产品，请尝试修改搜索关键字。' : props.emptyText);
const pageItems = computed(() => {
  const total = totalPages.value;
  if (total <= 7) {
    return Array.from({ length: total }, (_, index) => index + 1);
  }
  const pages = new Set([1, total, currentPage.value, currentPage.value - 1, currentPage.value + 1]);
  const normalized = [...pages]
    .filter((page) => page >= 1 && page <= total)
    .sort((left, right) => left - right);
  const result = [];
  for (let index = 0; index < normalized.length; index += 1) {
    const page = normalized[index];
    const previous = normalized[index - 1];
    if (index > 0 && previous != null && page - previous > 1) {
      result.push('ellipsis');
    }
    result.push(page);
  }
  return result;
});

watch(keyword, () => {
  currentPage.value = 1;
  hideTooltip();
});
watch(totalPages, (value) => {
  if (currentPage.value > value) {
    currentPage.value = value;
  }
  hideTooltip();
});
watch(pagedProducts, () => {
  hideTooltip();
});

const barHeight = (quantity) => {
  const percent = (Number(quantity || 0) / yAxisMax.value) * 100;
  return Math.max(6, percent);
};

const updateTooltipPosition = (event, product) => {
  const container = chartContainerRef.value;
  if (!container || !product) {
    return;
  }
  const rect = container.getBoundingClientRect();
  const tooltipWidth = 224;
  const tooltipHeight = 118;
  const rawLeft = event.clientX - rect.left + 12;
  const rawTop = event.clientY - rect.top - tooltipHeight - 12;
  const clampedLeft = Math.min(Math.max(8, rawLeft), Math.max(8, rect.width - tooltipWidth - 8));
  const clampedTop = rawTop < 8 ? event.clientY - rect.top + 16 : rawTop;
  tooltip.value = {
    visible: true,
    product,
    left: clampedLeft,
    top: Math.min(Math.max(8, clampedTop), Math.max(8, rect.height - tooltipHeight - 8))
  };
};

const showTooltip = (event, product) => updateTooltipPosition(event, product);
const moveTooltip = (event, product) => updateTooltipPosition(event, product);
const hideTooltip = () => {
  tooltip.value = { visible: false, product: null, left: 0, top: 0 };
};
const goToPage = (page) => {
  currentPage.value = Math.min(Math.max(1, page), totalPages.value);
};

const formatQuantity = (value) => Number(value || 0).toFixed(2);
const formatTick = (value) => Number(value || 0).toFixed(0);
const formatDateOnly = (value) => {
  const date = value instanceof Date ? value : new Date(value);
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
};
</script>

