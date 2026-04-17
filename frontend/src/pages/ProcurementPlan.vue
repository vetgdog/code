<template>
  <div class="space-y-6">
    <section class="bg-white rounded-lg border border-outline-variant/10">
      <div class="p-5 border-b border-surface-container-low flex items-center justify-between gap-4">
        <div>
          <h3 class="text-sm font-bold tracking-tight">本周采购计划</h3>
          <p class="mt-1 text-xs text-on-surface-variant">系统会结合本周生产计划 BOM 需求、上周采购量、当前原料库存和在途采购量自动生成本周采购计划。</p>
        </div>
        <div class="flex items-center gap-3">
          <input v-model="referenceDate" type="date" class="rounded border border-outline-variant/40 px-3 py-2 text-sm" />
          <button class="rounded border border-primary text-primary px-3 py-2 text-sm font-semibold" @click="loadCurrentPlan">刷新</button>
          <button class="rounded bg-primary text-white px-3 py-2 text-sm font-semibold" @click="regeneratePlan">重新生成</button>
        </div>
      </div>
      <div class="p-5">
        <div v-if="message" class="mb-3 text-xs text-emerald-600">{{ message }}</div>
        <div v-if="error" class="mb-3 text-xs text-error">{{ error }}</div>
        <div v-if="loading" class="text-sm text-on-surface-variant">加载中...</div>
        <div v-else-if="!currentPlan" class="text-sm text-on-surface-variant">当前没有可用的采购计划。</div>
        <div v-else class="space-y-5">
          <div class="grid grid-cols-1 md:grid-cols-4 gap-4 text-sm">
            <div class="rounded-xl border border-outline-variant/20 bg-slate-50 p-4">
              <div class="text-xs text-on-surface-variant">计划周次</div>
              <div class="mt-2 font-bold">{{ formatWeek(currentPlan.weekStart, currentPlan.weekEnd) }}</div>
            </div>
            <div class="rounded-xl border border-outline-variant/20 bg-slate-50 p-4">
              <div class="text-xs text-on-surface-variant">基于历史周</div>
              <div class="mt-2 font-bold">{{ formatWeek(currentPlan.basedOnWeekStart, currentPlan.basedOnWeekEnd) }}</div>
            </div>
            <div class="rounded-xl border border-outline-variant/20 bg-slate-50 p-4">
              <div class="text-xs text-on-surface-variant">计划条目数</div>
              <div class="mt-2 font-bold">{{ currentPlan.items?.length || 0 }}</div>
            </div>
            <div class="rounded-xl border border-outline-variant/20 bg-slate-50 p-4">
              <div class="text-xs text-on-surface-variant">建议采购总量</div>
              <div class="mt-2 font-bold">{{ formatNumber(totalPlannedQuantity) }}</div>
            </div>
          </div>

          <div class="rounded-xl border border-outline-variant/20 bg-amber-50 p-4 text-sm text-slate-700">
            <div class="font-semibold">生成逻辑</div>
            <div class="mt-2 text-xs">{{ currentPlan.algorithmNote || '-' }}</div>
            <div class="mt-2 text-xs text-on-surface-variant">生成时间：{{ formatDate(currentPlan.generatedAt) }} ｜ 生成人：{{ currentPlan.generatedBy || '-' }}</div>
          </div>

          <div v-if="!(currentPlan.items || []).length" class="text-sm text-on-surface-variant">本周暂无建议采购项目。</div>
          <table v-else class="w-full text-sm">
            <thead class="text-xs text-on-surface-variant">
              <tr class="text-left">
                <th class="pb-2">原材料名称</th>
                <th class="pb-2">SKU</th>
                <th class="pb-2">建议采购量</th>
                <th class="pb-2">上周采购量</th>
                <th class="pb-2">BOM需求</th>
                <th class="pb-2">当前库存</th>
                <th class="pb-2">在途量</th>
                <th class="pb-2">供应商</th>
                <th class="pb-2">说明</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in currentPlan.items" :key="item.id" class="border-t border-outline-variant/20 align-top">
                <td class="py-3 font-semibold">{{ item.product?.name || '-' }}</td>
                <td class="py-3">{{ item.product?.sku || '-' }}</td>
                <td class="py-3 text-primary font-semibold">{{ formatNumber(item.suggestedQuantity) }}</td>
                <td class="py-3">{{ formatNumber(item.lastWeekProcuredQuantity) }}</td>
                <td class="py-3">{{ formatNumber(item.bomDemandQuantity) }}</td>
                <td class="py-3">{{ formatNumber(item.availableInventoryQuantity) }}</td>
                <td class="py-3">{{ formatNumber(item.inTransitQuantity) }}</td>
                <td class="py-3">{{ item.preferredSupplier || '-' }}</td>
                <td class="py-3 text-xs text-on-surface-variant">{{ item.suggestionReason || '-' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </section>

    <section class="bg-white rounded-lg border border-outline-variant/10">
      <div class="p-5 border-b border-surface-container-low flex items-center justify-between">
        <div>
          <h3 class="text-sm font-bold tracking-tight">历史采购计划</h3>
          <p class="mt-1 text-xs text-on-surface-variant">保留每周自动生成结果，便于采购管理员对比原材料需求变化。</p>
        </div>
        <button class="text-xs text-primary font-semibold" @click="loadPlanHistory">刷新</button>
      </div>
      <div class="p-5">
        <div v-if="history.length === 0" class="text-sm text-on-surface-variant">暂无历史采购计划。</div>
        <table v-else class="w-full text-sm">
          <thead class="text-xs text-on-surface-variant">
            <tr class="text-left">
              <th class="pb-2">计划周次</th>
              <th class="pb-2">基于历史周</th>
              <th class="pb-2">条目数</th>
              <th class="pb-2">总量</th>
              <th class="pb-2">生成时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="plan in history" :key="plan.id" class="border-t border-outline-variant/20 cursor-pointer hover:bg-slate-50" @click="selectHistory(plan)">
              <td class="py-3 font-semibold">{{ formatWeek(plan.weekStart, plan.weekEnd) }}</td>
              <td class="py-3">{{ formatWeek(plan.basedOnWeekStart, plan.basedOnWeekEnd) }}</td>
              <td class="py-3">{{ plan.items?.length || 0 }}</td>
              <td class="py-3">{{ formatNumber(sumPlanQuantity(plan.items)) }}</td>
              <td class="py-3">{{ formatDate(plan.generatedAt) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue';
import { procurementApi } from '../api/services.js';
import { useRealtimeStore } from '../store/realtime.js';

const realtime = useRealtimeStore();
const today = new Date().toISOString().slice(0, 10);

const referenceDate = ref(today);
const currentPlan = ref(null);
const history = ref([]);
const loading = ref(false);
const error = ref('');
const message = ref('');

const totalPlannedQuantity = computed(() => sumPlanQuantity(currentPlan.value?.items || []));

const loadCurrentPlan = async () => {
  loading.value = true;
  error.value = '';
  try {
    const response = await procurementApi.getCurrentWeeklyPlan({ referenceDate: referenceDate.value || undefined });
    currentPlan.value = response.data || null;
  } catch (err) {
    currentPlan.value = null;
    error.value = err?.response?.data?.message || err?.response?.data || '采购计划加载失败。';
  } finally {
    loading.value = false;
  }
};

const regeneratePlan = async () => {
  message.value = '';
  error.value = '';
  try {
    const response = await procurementApi.generateWeeklyPlan({ referenceDate: referenceDate.value || undefined });
    currentPlan.value = response.data || null;
    message.value = '采购计划已按最新数据重新生成。';
    await loadPlanHistory();
  } catch (err) {
    error.value = err?.response?.data?.message || err?.response?.data || '采购计划生成失败。';
  }
};

const loadPlanHistory = async () => {
  try {
    const response = await procurementApi.listWeeklyPlans();
    history.value = response.data || [];
  } catch (err) {
    history.value = [];
  }
};

const selectHistory = (plan) => {
  currentPlan.value = plan || null;
  if (plan?.weekStart) {
    referenceDate.value = plan.weekStart;
  }
};

const sumPlanQuantity = (items = []) => (items || []).reduce((sum, item) => sum + Number(item?.suggestedQuantity || 0), 0);
const formatNumber = (value) => Number(value || 0).toFixed(2);
const formatDate = (value) => (value ? new Date(value).toLocaleString() : '-');
const formatWeek = (start, end) => (start && end ? `${start} ~ ${end}` : '-');

watch(
  () => realtime.state.lastMessage,
  (event) => {
    if (event?.messageType === 'PROCUREMENT_WEEKLY_PLAN_GENERATED' || event?.topic?.startsWith('/topic/procurement')) {
      loadCurrentPlan();
      loadPlanHistory();
    }
  }
);

onMounted(async () => {
  await Promise.all([loadCurrentPlan(), loadPlanHistory()]);
});
</script>

