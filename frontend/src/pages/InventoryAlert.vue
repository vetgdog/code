<template>
  <div class="space-y-6">
    <section class="bg-white rounded-lg border border-outline-variant/10 p-5">
      <div class="flex items-center justify-between gap-4">
        <div>
          <h3 class="text-sm font-bold tracking-tight">库存预警总览</h3>
          <p class="mt-1 text-xs text-on-surface-variant">基于安全库存、可用库存以及在制/在途数量，自动提示成品补产和原材料补采需求。</p>
        </div>
        <button class="text-xs text-primary font-semibold" @click="loadAlerts">刷新</button>
      </div>
      <div class="mt-4 grid grid-cols-1 md:grid-cols-4 gap-4 text-sm">
        <div class="rounded-xl border border-outline-variant/20 bg-red-50 p-4">
          <div class="text-xs text-on-surface-variant">成品预警数</div>
          <div class="mt-2 text-lg font-bold text-red-700">{{ finishedGoods.length }}</div>
        </div>
        <div class="rounded-xl border border-outline-variant/20 bg-amber-50 p-4">
          <div class="text-xs text-on-surface-variant">原材料预警数</div>
          <div class="mt-2 text-lg font-bold text-amber-700">{{ rawMaterials.length }}</div>
        </div>
        <div class="rounded-xl border border-outline-variant/20 bg-blue-50 p-4">
          <div class="text-xs text-on-surface-variant">建议补产总量</div>
          <div class="mt-2 text-lg font-bold text-blue-700">{{ formatNumber(totalProductionSuggestion) }}</div>
        </div>
        <div class="rounded-xl border border-outline-variant/20 bg-emerald-50 p-4">
          <div class="text-xs text-on-surface-variant">建议补采总量</div>
          <div class="mt-2 text-lg font-bold text-emerald-700">{{ formatNumber(totalProcurementSuggestion) }}</div>
        </div>
      </div>
      <div v-if="message" class="mt-3 text-xs text-emerald-600">{{ message }}</div>
      <div v-if="error" class="mt-3 text-xs text-error">{{ error }}</div>
      <div class="mt-2 text-xs text-on-surface-variant">最近生成时间：{{ generatedAt ? formatDate(generatedAt) : '-' }}</div>
    </section>

    <section class="bg-white rounded-lg border border-outline-variant/10">
      <div class="p-5 border-b border-surface-container-low flex items-center justify-between gap-4">
        <div>
          <h3 class="text-sm font-bold tracking-tight">成品预警</h3>
          <p class="mt-1 text-xs text-on-surface-variant">仓库管理员可直接根据建议量生成生产计划，并自动通知生产管理员。</p>
        </div>
      </div>
      <div class="p-5">
        <div v-if="finishedGoods.length === 0" class="text-sm text-on-surface-variant">当前没有成品库存预警。</div>
        <table v-else class="w-full text-sm">
          <thead class="text-xs text-on-surface-variant">
            <tr class="text-left">
              <th class="pb-2">产品</th>
              <th class="pb-2">可用库存</th>
              <th class="pb-2">安全库存</th>
              <th class="pb-2">在制量</th>
              <th class="pb-2">缺口</th>
              <th class="pb-2">建议补产</th>
              <th class="pb-2">仓库分布</th>
              <th class="pb-2">等级</th>
              <th class="pb-2">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in finishedGoods" :key="item.productId" class="border-t border-outline-variant/20 align-top">
              <td class="py-3 font-semibold">{{ item.name }}（{{ item.sku }}）</td>
              <td class="py-3">{{ formatNumber(item.availableQuantity) }}</td>
              <td class="py-3">{{ formatNumber(item.safetyStock) }}</td>
              <td class="py-3">{{ formatNumber(item.pipelineQuantity) }}</td>
              <td class="py-3 text-red-700 font-semibold">{{ formatNumber(item.shortageQuantity) }}</td>
              <td class="py-3 text-primary font-semibold">{{ formatNumber(item.recommendedActionQuantity) }}</td>
              <td class="py-3 text-xs text-on-surface-variant">{{ item.warehouseSummary || '-' }}</td>
              <td class="py-3"><span class="text-xs px-2 py-1 rounded-full" :class="severityClass(item.severity)">{{ item.severity }}</span></td>
              <td class="py-3">
                <button class="text-xs text-primary font-semibold" @click="createProductionPlan(item)">生成生产计划</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <section class="bg-white rounded-lg border border-outline-variant/10">
      <div class="p-5 border-b border-surface-container-low flex items-center justify-between gap-4">
        <div>
          <h3 class="text-sm font-bold tracking-tight">原材料预警</h3>
          <p class="mt-1 text-xs text-on-surface-variant">仓库管理员可直接生成采购申请，通知采购管理员处理。</p>
        </div>
      </div>
      <div class="p-5">
        <div v-if="rawMaterials.length === 0" class="text-sm text-on-surface-variant">当前没有原材料库存预警。</div>
        <table v-else class="w-full text-sm">
          <thead class="text-xs text-on-surface-variant">
            <tr class="text-left">
              <th class="pb-2">原材料</th>
              <th class="pb-2">可用库存</th>
              <th class="pb-2">安全库存</th>
              <th class="pb-2">在途量</th>
              <th class="pb-2">缺口</th>
              <th class="pb-2">建议补采</th>
              <th class="pb-2">首选供应商</th>
              <th class="pb-2">仓库分布</th>
              <th class="pb-2">等级</th>
              <th class="pb-2">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in rawMaterials" :key="item.productId" class="border-t border-outline-variant/20 align-top">
              <td class="py-3 font-semibold">{{ item.name }}（{{ item.sku }}）</td>
              <td class="py-3">{{ formatNumber(item.availableQuantity) }}</td>
              <td class="py-3">{{ formatNumber(item.safetyStock) }}</td>
              <td class="py-3">{{ formatNumber(item.pipelineQuantity) }}</td>
              <td class="py-3 text-red-700 font-semibold">{{ formatNumber(item.shortageQuantity) }}</td>
              <td class="py-3 text-primary font-semibold">{{ formatNumber(item.recommendedActionQuantity) }}</td>
              <td class="py-3">{{ item.preferredSupplier || '-' }}</td>
              <td class="py-3 text-xs text-on-surface-variant">{{ item.warehouseSummary || '-' }}</td>
              <td class="py-3"><span class="text-xs px-2 py-1 rounded-full" :class="severityClass(item.severity)">{{ item.severity }}</span></td>
              <td class="py-3">
                <button class="text-xs text-primary font-semibold" @click="createPurchaseRequest(item)">通知采购管理员</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue';
import { inventoryApi } from '../api/services.js';
import { useRealtimeStore } from '../store/realtime.js';

const realtime = useRealtimeStore();
const finishedGoods = ref([]);
const rawMaterials = ref([]);
const generatedAt = ref('');
const message = ref('');
const error = ref('');

const loadAlerts = async () => {
  error.value = '';
  try {
    const response = await inventoryApi.listAlerts();
    finishedGoods.value = response.data?.finishedGoods || [];
    rawMaterials.value = response.data?.rawMaterials || [];
    generatedAt.value = response.data?.generatedAt || '';
  } catch (err) {
    finishedGoods.value = [];
    rawMaterials.value = [];
    generatedAt.value = '';
    error.value = err?.response?.data?.message || err?.response?.data || '库存预警加载失败。';
  }
};

const createProductionPlan = async (item) => {
  message.value = '';
  error.value = '';
  const input = window.prompt(`请输入 ${item.name} 的生产计划数量：`, String(item.recommendedActionQuantity || 0));
  if (input == null) {
    return;
  }
  const quantity = Number(input);
  if (!Number.isFinite(quantity) || quantity <= 0) {
    error.value = '请输入大于 0 的生产计划数量。';
    return;
  }
  try {
    await inventoryApi.createAlertProductionPlan(item.productId, { quantity, note: `库存预警触发，建议补产 ${quantity}` });
    message.value = `${item.name} 的生产计划已创建，并已通知生产管理员。`;
    await loadAlerts();
  } catch (err) {
    error.value = err?.response?.data?.message || err?.response?.data || '生产计划创建失败。';
  }
};

const createPurchaseRequest = async (item) => {
  message.value = '';
  error.value = '';
  const input = window.prompt(`请输入 ${item.name} 的采购申请数量：`, String(item.recommendedActionQuantity || 0));
  if (input == null) {
    return;
  }
  const quantity = Number(input);
  if (!Number.isFinite(quantity) || quantity <= 0) {
    error.value = '请输入大于 0 的采购申请数量。';
    return;
  }
  try {
    await inventoryApi.createAlertPurchaseRequest(item.productId, { quantity, note: `库存预警触发，建议补采 ${quantity}` });
    message.value = `${item.name} 的采购申请已创建，并已通知采购管理员。`;
    await loadAlerts();
  } catch (err) {
    error.value = err?.response?.data?.message || err?.response?.data || '采购申请创建失败。';
  }
};

const totalProductionSuggestion = computed(() => finishedGoods.value.reduce((sum, item) => sum + Number(item.recommendedActionQuantity || 0), 0));
const totalProcurementSuggestion = computed(() => rawMaterials.value.reduce((sum, item) => sum + Number(item.recommendedActionQuantity || 0), 0));

const severityClass = (severity) => {
  if (severity === 'CRITICAL') {
    return 'bg-red-100 text-red-700';
  }
  if (severity === 'HIGH') {
    return 'bg-amber-100 text-amber-700';
  }
  return 'bg-blue-100 text-blue-700';
};

const formatNumber = (value) => Number(value || 0).toFixed(2);
const formatDate = (value) => (value ? new Date(value).toLocaleString() : '-');

watch(
  () => realtime.state.lastMessage,
  (event) => {
    if (event?.topic && (event.topic.startsWith('/topic/orders') || event.topic.startsWith('/topic/production') || event.topic.startsWith('/topic/procurement'))) {
      loadAlerts();
    }
  }
);

onMounted(loadAlerts);
</script>

