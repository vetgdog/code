<template>
  <div class="space-y-6">
    <section class="panel-surface overflow-hidden">
      <div class="panel-header flex items-center justify-between gap-4">
        <div>
          <h3 class="text-base font-bold text-white">销售记录</h3>
          <p class="mt-1 text-xs text-slate-400">销售管理员仅查看自己的销售记录；周甘特统计基于所有销售记录统一生成。</p>
        </div>
        <div class="flex items-center gap-2">
          <input v-model="filter.startDate" type="date" class="neo-input !py-2" />
          <input v-model="filter.endDate" type="date" class="neo-input !py-2" />
          <button class="neo-button-secondary" @click="loadRecords">筛选</button>
          <button class="neo-button-secondary" @click="exportRecords">导出 Excel 兼容 CSV</button>
        </div>
      </div>
      <div class="p-5">
        <div v-if="error" class="mb-3 text-sm text-rose-300">{{ error }}</div>
        <div v-if="records.length === 0" class="text-sm text-cyan-100/70">暂无销售记录。</div>
        <table v-else class="neo-table">
          <thead>
            <tr>
              <th>记录号</th>
              <th>订单号</th>
              <th>客户</th>
              <th>总额</th>
              <th>销售管理员ID</th>
              <th>销售管理员</th>
              <th>时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="record in records" :key="record.id">
              <td class="font-semibold">{{ record.recordNo }}</td>
              <td>{{ record.orderNo }}</td>
              <td>{{ record.customerName || '-' }}</td>
              <td>¥{{ formatAmount(record.totalAmount) }}</td>
              <td>{{ record.createdBy || '-' }}</td>
              <td>{{ record.createdByName || '-' }}</td>
              <td>{{ formatDate(record.createdAt) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <WeeklyGantt
      title="销售周甘特概览（全员统计）"
      :items="ganttItems"
      empty-text="当前没有可用于统计的销售记录。"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { orderApi } from '../api/services.js';
import WeeklyGantt from '../components/WeeklyGantt.vue';
import { useRealtimeStore } from '../store/realtime.js';

const realtime = useRealtimeStore();
const records = ref([]);
const overviewRecords = ref([]);
const error = ref('');
const filter = reactive({ startDate: '', endDate: '' });

const sortByLatest = (source = []) => [...source].sort((left, right) => new Date(right?.createdAt || 0).getTime() - new Date(left?.createdAt || 0).getTime());

const loadRecords = async () => {
  error.value = '';
  try {
    const params = { startDate: filter.startDate || undefined, endDate: filter.endDate || undefined };
    const [recordResponse, overviewResponse] = await Promise.all([
      orderApi.listSalesRecords(params),
      orderApi.listSalesRecordOverview(params)
    ]);
    records.value = sortByLatest(recordResponse.data || []);
    overviewRecords.value = sortByLatest(overviewResponse.data || []);
  } catch (err) {
    records.value = [];
    overviewRecords.value = [];
    error.value = err?.response?.data?.message || err?.response?.data || '销售记录加载失败。';
  }
};

const exportRecords = async () => {
  error.value = '';
  try {
    const response = await orderApi.exportSalesRecords({ startDate: filter.startDate || undefined, endDate: filter.endDate || undefined });
    const blob = new Blob([response.data], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = `sales-records-${new Date().toISOString().slice(0, 10)}.csv`;
    document.body.appendChild(anchor);
    anchor.click();
    document.body.removeChild(anchor);
    URL.revokeObjectURL(url);
  } catch (err) {
    error.value = err?.response?.data?.message || err?.response?.data || '销售记录导出失败。';
  }
};

const ganttItems = computed(() => overviewRecords.value.map((record) => ({
  id: record.id,
  label: `${record.orderNo} / ${record.customerName || '客户'}`,
  meta: `${record.createdByName || '销售管理员'} · ¥${formatAmount(record.totalAmount)}`,
  start: record.createdAt,
  end: record.createdAt,
  shortText: record.createdByName || '销售',
  color: '#22c55e'
})));

const formatAmount = (value) => Number(value || 0).toFixed(2);
const formatDate = (value) => (value ? new Date(value).toLocaleString() : '-');

watch(
  () => realtime.state.lastMessage,
  (message) => {
    if (message?.topic?.startsWith('/topic/orders')) {
      loadRecords();
    }
  }
);

onMounted(loadRecords);
</script>

