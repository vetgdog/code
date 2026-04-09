<template>
  <div class="space-y-6">
    <section class="bg-white rounded-lg border border-outline-variant/10 p-5">
      <h3 class="text-sm font-bold tracking-tight">批次追溯</h3>
      <div class="mt-4 flex flex-col md:flex-row gap-3">
        <input v-model="batchNo" placeholder="输入批次号" class="rounded border border-outline-variant/40 px-3 py-2 text-sm w-full md:w-80" />
        <button class="rounded bg-primary text-white px-4 py-2 text-sm font-semibold" @click="handleSearch">查询</button>
      </div>
      <div v-if="error" class="mt-3 text-xs text-error">{{ error }}</div>
    </section>

    <section v-if="batch" class="bg-white rounded-lg border border-outline-variant/10 p-5">
      <h4 class="text-sm font-bold">批次信息</h4>
      <div class="mt-3 grid grid-cols-1 md:grid-cols-4 gap-4 text-sm">
        <div>批次号: {{ batch.batchNo }}</div>
        <div>产品ID: {{ batch.product?.id || '-' }}</div>
        <div>数量: {{ batch.quantity }}</div>
        <div>生产任务: {{ batch.productionTask?.id || '-' }}</div>
      </div>
    </section>

    <section class="bg-white rounded-lg border border-outline-variant/10">
      <div class="p-5 border-b border-surface-container-low flex items-center justify-between">
        <h4 class="text-sm font-bold">检验记录</h4>
        <span class="text-xs text-on-surface-variant">{{ records.length }} 条</span>
      </div>
      <div class="p-5">
        <div v-if="loading" class="text-sm text-on-surface-variant">加载中...</div>
        <div v-else-if="records.length === 0" class="text-sm text-on-surface-variant">暂无记录。</div>
        <table v-else class="w-full text-sm">
          <thead class="text-xs text-on-surface-variant">
            <tr class="text-left">
              <th class="pb-2">检验人</th>
              <th class="pb-2">结果</th>
              <th class="pb-2">备注</th>
              <th class="pb-2">日期</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="record in records" :key="record.id" class="border-t border-outline-variant/20">
              <td class="py-3">{{ record.inspector || '-' }}</td>
              <td class="py-3">{{ record.result }}</td>
              <td class="py-3">{{ record.remarks || '-' }}</td>
              <td class="py-3">{{ formatDate(record.inspectionDate) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import { qualityApi } from '../api/services.js';

const batchNo = ref('');
const batch = ref(null);
const records = ref([]);
const loading = ref(false);
const error = ref('');

const handleSearch = async () => {
  error.value = '';
  batch.value = null;
  records.value = [];
  if (!batchNo.value) {
    error.value = '请输入批次号。';
    return;
  }
  loading.value = true;
  try {
    const batchResponse = await qualityApi.getBatchByNo(batchNo.value);
    batch.value = batchResponse.data;
    if (batch.value?.id) {
      const recordsResponse = await qualityApi.getRecords(batch.value.id);
      records.value = recordsResponse.data || [];
    }
  } catch (err) {
    error.value = err?.response?.data?.message || '批次查询失败。';
  } finally {
    loading.value = false;
  }
};

const formatDate = (value) => (value ? new Date(value).toLocaleString() : '-');
</script>

