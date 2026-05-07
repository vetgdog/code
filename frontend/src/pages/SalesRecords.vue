<template>
  <div class="space-y-6">
    <section class="panel-surface overflow-hidden">
      <div class="panel-header flex items-center justify-between gap-4">
        <div>
          <h3 class="text-base font-bold text-white">销售记录</h3>
          <p class="mt-1 text-xs text-slate-400">销售管理员仅查看自己的销售记录；下方柱状图固定统计上一个周的全员销售明细，按产品名称展示销量分布。</p>
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

    <div v-if="chartError" class="rounded-lg border border-rose-500/20 bg-rose-500/10 px-4 py-3 text-sm text-rose-200">
      {{ chartError }}
    </div>

    <WeeklyBarChart
      title="上周产品销量柱状图（全员统计）"
      description="柱状图数据固定取上一个周的全部销售记录，支持按产品名称搜索、悬停查看详情，并按页码每页浏览 5 个产品。"
      :records="overviewRecords"
      empty-text="上一个周没有可用于统计的销售记录。"
    />
  </div>
</template>

<script setup>
import { onMounted, reactive, ref, watch } from 'vue';
import { orderApi } from '../api/services.js';
import WeeklyBarChart from '../components/WeeklyBarChart.vue';
import { useRealtimeStore } from '../store/realtime.js';

const realtime = useRealtimeStore();
const records = ref([]);
const overviewRecords = ref([]);
const error = ref('');
const chartError = ref('');
const filter = reactive({ startDate: '', endDate: '' });

const sortByLatest = (source = []) => [...source].sort((left, right) => new Date(right?.createdAt || 0).getTime() - new Date(left?.createdAt || 0).getTime());

const hydrateOverviewRecords = (salesRecords = [], orders = []) => {
  const orderMap = new Map((orders || [])
    .filter((order) => order?.orderNo)
    .map((order) => [order.orderNo, order]));

  return (salesRecords || []).map((record) => {
    const matchedOrder = orderMap.get(record?.orderNo);
    if (!matchedOrder) {
      return record;
    }
    return {
      ...record,
      salesOrder: {
        ...(record?.salesOrder || {}),
        orderNo: record?.salesOrder?.orderNo || matchedOrder.orderNo,
        items: Array.isArray(record?.salesOrder?.items) && record.salesOrder.items.length
          ? record.salesOrder.items
          : (matchedOrder.items || [])
      }
    };
  });
};

const formatDateParam = (date) => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;

const resolveLastWeekRange = () => {
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const currentDay = today.getDay() || 7;
  const currentWeekMonday = new Date(today);
  currentWeekMonday.setDate(today.getDate() - currentDay + 1);

  const lastWeekStart = new Date(currentWeekMonday);
  lastWeekStart.setDate(currentWeekMonday.getDate() - 7);

  const lastWeekEnd = new Date(currentWeekMonday);
  lastWeekEnd.setDate(currentWeekMonday.getDate() - 1);

  return {
    startDate: formatDateParam(lastWeekStart),
    endDate: formatDateParam(lastWeekEnd)
  };
};

const loadRecords = async () => {
  error.value = '';
  try {
    const params = { startDate: filter.startDate || undefined, endDate: filter.endDate || undefined };
    const recordResponse = await orderApi.listSalesRecords(params);
    records.value = sortByLatest(recordResponse.data || []);
  } catch (err) {
    records.value = [];
    error.value = err?.response?.data?.message || err?.response?.data || '销售记录加载失败。';
  }
};

const loadWeeklyOverview = async () => {
  chartError.value = '';
  try {
    const params = resolveLastWeekRange();
    const [overviewResponse, orderResponse] = await Promise.all([
      orderApi.listSalesRecordOverview(params),
      orderApi.list()
    ]);
    overviewRecords.value = sortByLatest(hydrateOverviewRecords(overviewResponse.data || [], orderResponse.data || []));
  } catch (err) {
    overviewRecords.value = [];
    chartError.value = err?.response?.data?.message || err?.response?.data || '上周产品销量统计加载失败。';
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

const formatAmount = (value) => Number(value || 0).toFixed(2);
const formatDate = (value) => (value ? new Date(value).toLocaleString() : '-');

watch(
  () => realtime.state.lastMessage,
  (message) => {
    if (message?.topic?.startsWith('/topic/orders')) {
      loadRecords();
      loadWeeklyOverview();
    }
  }
);

onMounted(async () => {
  await Promise.all([loadRecords(), loadWeeklyOverview()]);
});
</script>

