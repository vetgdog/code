<template>
  <div class="space-y-6">
    <section class="bg-white rounded-lg border border-outline-variant/10">
      <div class="p-5 border-b border-surface-container-low flex items-center justify-between gap-4">
        <div>
          <h3 class="text-sm font-bold tracking-tight">成品质检列表</h3>
          <p class="mt-1 text-xs text-on-surface-variant">仓库成品入库后将自动生成待检批次；质检不合格时会自动通知对应生产管理员。</p>
        </div>
        <button class="text-xs text-primary font-semibold" @click="loadBatches">刷新</button>
      </div>
      <div class="p-5">
        <div class="mb-4 grid grid-cols-1 md:grid-cols-5 gap-3">
          <input v-model="filters.keyword" placeholder="搜索批次号 / 订单号 / 产品 / 生产管理员" class="rounded border border-outline-variant/40 px-3 py-2 text-sm md:col-span-2" @keyup.enter="loadBatches" />
          <select v-model="filters.status" class="rounded border border-outline-variant/40 px-3 py-2 text-sm">
            <option value="">全部状态</option>
            <option v-for="status in statuses" :key="status" :value="status">{{ status }}</option>
          </select>
          <div class="rounded border border-dashed border-outline-variant/40 px-3 py-2 text-sm bg-slate-50 text-on-surface-variant">
            待检 {{ pendingCount }} 批
          </div>
          <div class="flex items-center gap-3">
            <button class="rounded border border-primary text-primary px-3 py-2 text-sm font-semibold" @click="loadBatches">查询</button>
            <button class="text-xs text-on-surface-variant" @click="resetFilters">重置</button>
          </div>
        </div>
        <div v-if="error" class="mb-3 text-xs text-error">{{ error }}</div>

        <div class="grid grid-cols-1 xl:grid-cols-[1.1fr_1.4fr] gap-5">
          <div class="rounded-xl border border-outline-variant/20 overflow-hidden">
            <div class="px-4 py-3 border-b border-outline-variant/20 flex items-center justify-between">
              <h4 class="text-sm font-bold">批次列表</h4>
              <span class="text-xs text-on-surface-variant">共 {{ batches.length }} 批</span>
            </div>
            <div v-if="loading" class="p-4 text-sm text-on-surface-variant">加载中...</div>
            <div v-else-if="batches.length === 0" class="p-4 text-sm text-on-surface-variant">当前没有可质检批次。</div>
            <div v-else class="max-h-[28rem] overflow-auto divide-y divide-outline-variant/20">
              <button
                v-for="item in batches"
                :key="item.id"
                class="w-full text-left px-4 py-4 hover:bg-slate-50 transition-colors"
                :class="selectedBatch?.id === item.id ? 'bg-blue-50' : 'bg-white'"
                @click="selectBatch(item)"
              >
                <span class="flex items-center justify-between gap-3">
                  <span class="font-semibold text-sm">{{ item.batchNo }}</span>
                  <span class="text-xs px-2 py-1 rounded-full" :class="statusClass(item.qualityStatus)">{{ item.qualityStatus || '待检' }}</span>
                </span>
                <span class="mt-2 block text-xs text-on-surface-variant">{{ item.product?.name || '-' }}（{{ item.product?.sku || '-' }}）</span>
                <span class="mt-1 block text-xs text-on-surface-variant">来源订单：{{ item.sourceOrderNo || '-' }} ｜ 数量：{{ formatNumber(item.quantity) }}</span>
                <span class="mt-1 block text-xs text-on-surface-variant">生产管理员：{{ item.productionManagerName || item.productionManagerEmail || '-' }}</span>
              </button>
            </div>
          </div>

          <div class="space-y-5">
            <section class="rounded-xl border border-outline-variant/20 bg-white p-5">
              <div class="flex items-center justify-between gap-3">
                <div>
                  <h4 class="text-sm font-bold">批次详情</h4>
                  <p class="mt-1 text-xs text-on-surface-variant">{{ selectedBatch ? `当前批次：${selectedBatch.batchNo}` : '请先从左侧选择一个批次' }}</p>
                </div>
                <button v-if="selectedBatch" class="text-xs text-primary font-semibold" @click="reloadSelected">刷新详情</button>
              </div>

              <div v-if="!selectedBatch" class="mt-4 text-sm text-on-surface-variant">暂无选中批次。</div>
              <div v-else class="mt-4 grid grid-cols-1 md:grid-cols-2 gap-3 text-sm">
                <div><span class="text-on-surface-variant">批次号：</span>{{ selectedBatch.batchNo || '-' }}</div>
                <div><span class="text-on-surface-variant">来源订单：</span>{{ selectedBatch.sourceOrderNo || '-' }}</div>
                <div><span class="text-on-surface-variant">产品名称：</span>{{ selectedBatch.product?.name || '-' }}</div>
                <div><span class="text-on-surface-variant">产品 SKU：</span>{{ selectedBatch.product?.sku || '-' }}</div>
                <div><span class="text-on-surface-variant">入库数量：</span>{{ formatNumber(selectedBatch.quantity) }}</div>
                <div><span class="text-on-surface-variant">当前状态：</span>{{ selectedBatch.qualityStatus || '待检' }}</div>
                <div><span class="text-on-surface-variant">生产管理员：</span>{{ selectedBatch.productionManagerName || selectedBatch.productionManagerEmail || '-' }}</div>
                <div><span class="text-on-surface-variant">生产完成时间：</span>{{ formatDate(selectedBatch.manufactureDate) }}</div>
                <div><span class="text-on-surface-variant">最新质检员：</span>{{ selectedBatch.qualityInspectorName || '-' }}</div>
                <div><span class="text-on-surface-variant">最新质检时间：</span>{{ formatDate(selectedBatch.qualityInspectedAt) }}</div>
                <div class="md:col-span-2"><span class="text-on-surface-variant">最新质检备注：</span>{{ selectedBatch.qualityRemark || '-' }}</div>
              </div>
            </section>

            <section v-if="selectedBatch" class="rounded-xl border border-outline-variant/20 bg-white p-5">
              <h4 class="text-sm font-bold">提交质检结果</h4>
              <form class="mt-4 grid grid-cols-1 md:grid-cols-2 gap-4" @submit.prevent="submitInspection">
                <select v-model="inspectionForm.result" class="rounded border border-outline-variant/40 px-3 py-2 text-sm" required>
                  <option value="">请选择质检结果</option>
                  <option value="合格">合格</option>
                  <option value="不合格">不合格</option>
                </select>
                <div class="rounded border border-dashed border-outline-variant/40 px-3 py-2 text-sm bg-slate-50 text-on-surface-variant">
                  不合格时将自动通知对应生产管理员
                </div>
                <textarea v-model="inspectionForm.remarks" class="md:col-span-2 rounded border border-outline-variant/40 px-3 py-2 text-sm min-h-24" :placeholder="inspectionForm.result === '不合格' ? '请填写不合格原因，如外观划伤 / 尺寸偏差等' : '可填写抽检说明、检验依据等'" />
                <div class="md:col-span-2 flex items-center gap-3">
                  <button class="rounded bg-primary text-white px-4 py-2 text-sm font-semibold" :disabled="submitting">{{ submitting ? '提交中...' : '提交质检' }}</button>
                  <button type="button" class="rounded border border-outline-variant/40 px-4 py-2 text-sm" @click="resetInspectionForm">清空</button>
                </div>
              </form>
              <div v-if="message" class="mt-3 text-xs text-emerald-600">{{ message }}</div>
              <div v-if="submitError" class="mt-3 text-xs text-error">{{ submitError }}</div>
            </section>

            <section class="rounded-xl border border-outline-variant/20 bg-white overflow-hidden">
              <div class="px-5 py-4 border-b border-outline-variant/20 flex items-center justify-between">
                <h4 class="text-sm font-bold">检验记录</h4>
                <span class="text-xs text-on-surface-variant">{{ records.length }} 条</span>
              </div>
              <div class="p-5">
                <div v-if="recordsLoading" class="text-sm text-on-surface-variant">加载中...</div>
                <div v-else-if="records.length === 0" class="text-sm text-on-surface-variant">当前没有检验记录。</div>
                <table v-else class="w-full text-sm">
                  <thead class="text-xs text-on-surface-variant">
                    <tr class="text-left">
                      <th class="pb-2">检验人</th>
                      <th class="pb-2">结果</th>
                      <th class="pb-2">备注</th>
                      <th class="pb-2">通知状态</th>
                      <th class="pb-2">日期</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="record in records" :key="record.id" class="border-t border-outline-variant/20">
                      <td class="py-3">{{ record.inspectorName || record.inspector || '-' }}</td>
                      <td class="py-3">{{ record.result || '-' }}</td>
                      <td class="py-3">{{ record.remarks || '-' }}</td>
                      <td class="py-3">{{ record.notificationSent ? `已通知 ${record.notifiedProductionManagerEmail || '生产管理员'}` : '-' }}</td>
                      <td class="py-3">{{ formatDate(record.inspectionDate) }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </section>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { qualityApi } from '../api/services.js';
import { useRealtimeStore } from '../store/realtime.js';

const realtime = useRealtimeStore();

const statuses = ['待检', '合格', '不合格'];

const filters = reactive({
  keyword: '',
  status: ''
});

const inspectionForm = reactive({
  result: '',
  remarks: ''
});

const batches = ref([]);
const selectedBatch = ref(null);
const records = ref([]);
const loading = ref(false);
const recordsLoading = ref(false);
const submitting = ref(false);
const error = ref('');
const message = ref('');
const submitError = ref('');

const pendingCount = computed(() => batches.value.filter((item) => item.qualityStatus === '待检').length);

const loadBatches = async () => {
  loading.value = true;
  error.value = '';
  try {
    const response = await qualityApi.listBatches({
      keyword: filters.keyword || undefined,
      status: filters.status || undefined
    });
    batches.value = response.data || [];
    if (selectedBatch.value) {
      const latestSelected = batches.value.find((item) => item.id === selectedBatch.value.id);
      selectedBatch.value = latestSelected || null;
    }
    if (!selectedBatch.value && batches.value.length) {
      await selectBatch(batches.value[0]);
    }
    if (!batches.value.length) {
      selectedBatch.value = null;
      records.value = [];
    }
  } catch (err) {
    batches.value = [];
    selectedBatch.value = null;
    records.value = [];
    error.value = err?.response?.data?.message || err?.response?.data || '质检批次加载失败。';
  } finally {
    loading.value = false;
  }
};

const loadRecords = async (batchId) => {
  if (!batchId) {
    records.value = [];
    return;
  }
  recordsLoading.value = true;
  try {
    const response = await qualityApi.getRecords(batchId);
    records.value = response.data || [];
  } catch (err) {
    records.value = [];
  } finally {
    recordsLoading.value = false;
  }
};

const selectBatch = async (item) => {
  if (!item?.batchNo) {
    selectedBatch.value = null;
    records.value = [];
    return;
  }
  message.value = '';
  submitError.value = '';
  try {
    const response = await qualityApi.getBatchByNo(item.batchNo);
    selectedBatch.value = response.data || null;
    await loadRecords(selectedBatch.value?.id);
  } catch (err) {
    selectedBatch.value = null;
    records.value = [];
  }
};

const reloadSelected = async () => {
  if (!selectedBatch.value?.batchNo) {
    return;
  }
  await selectBatch(selectedBatch.value);
};

const submitInspection = async () => {
  if (!selectedBatch.value?.id) {
    submitError.value = '请先选择需要质检的批次。';
    return;
  }
  if (!inspectionForm.result) {
    submitError.value = '请先选择质检结果。';
    return;
  }
  if (inspectionForm.result === '不合格' && !String(inspectionForm.remarks || '').trim()) {
    submitError.value = '不合格时请填写具体原因。';
    return;
  }
  submitting.value = true;
  message.value = '';
  submitError.value = '';
  try {
    const response = await qualityApi.inspectBatch(selectedBatch.value.id, {
      result: inspectionForm.result,
      remarks: inspectionForm.remarks || null
    });
    selectedBatch.value = response.data || selectedBatch.value;
    message.value = inspectionForm.result === '不合格' ? '质检结果已提交，并已通知对应生产管理员。' : '质检结果已提交。';
    resetInspectionForm();
    await Promise.all([loadBatches(), loadRecords(selectedBatch.value.id)]);
  } catch (err) {
    submitError.value = err?.response?.data?.message || err?.response?.data || '质检提交失败。';
  } finally {
    submitting.value = false;
  }
};

const resetInspectionForm = () => {
  inspectionForm.result = '';
  inspectionForm.remarks = '';
};

const resetFilters = async () => {
  filters.keyword = '';
  filters.status = '';
  await loadBatches();
};

const statusClass = (status) => {
  if (status === '合格') {
    return 'bg-emerald-100 text-emerald-700';
  }
  if (status === '不合格') {
    return 'bg-red-100 text-red-700';
  }
  return 'bg-amber-100 text-amber-700';
};

const formatDate = (value) => (value ? new Date(value).toLocaleString() : '-');
const formatNumber = (value) => Number(value || 0).toFixed(2);

watch(
  () => realtime.state.lastMessage,
  async (event) => {
    if (event?.topic?.startsWith('/topic/quality')) {
      await loadBatches();
      if (selectedBatch.value?.id) {
        await loadRecords(selectedBatch.value.id);
      }
    }
  }
);

onMounted(async () => {
  await loadBatches();
});
</script>

