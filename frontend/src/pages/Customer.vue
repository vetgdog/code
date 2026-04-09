<template>
  <div class="space-y-6">
    <section class="bg-white rounded-lg border border-outline-variant/10 p-5">
      <h3 class="text-sm font-bold tracking-tight">客户订单查询</h3>
      <div class="mt-4 flex flex-col md:flex-row gap-3">
        <input v-model.number="customerId" placeholder="客户ID" class="rounded border border-outline-variant/40 px-3 py-2 text-sm w-full md:w-60" />
        <button class="rounded bg-primary text-white px-4 py-2 text-sm font-semibold" @click="loadOrders">查询订单</button>
      </div>
      <div v-if="error" class="mt-3 text-xs text-error">{{ error }}</div>
    </section>

    <section class="bg-white rounded-lg border border-outline-variant/10">
      <div class="p-5 border-b border-surface-container-low flex items-center justify-between">
        <h4 class="text-sm font-bold">订单列表</h4>
        <span class="text-xs text-on-surface-variant">{{ orders.length }} 条</span>
      </div>
      <div class="p-5">
        <div v-if="loading" class="text-sm text-on-surface-variant">加载中...</div>
        <div v-else-if="orders.length === 0" class="text-sm text-on-surface-variant">暂无订单。</div>
        <table v-else class="w-full text-sm">
          <thead class="text-xs text-on-surface-variant">
            <tr class="text-left">
              <th class="pb-2">订单号</th>
              <th class="pb-2">状态</th>
              <th class="pb-2">总额</th>
              <th class="pb-2">查看</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="order in orders" :key="order.id" class="border-t border-outline-variant/20">
              <td class="py-3 font-semibold">{{ order.orderNo }}</td>
              <td class="py-3">{{ order.status }}</td>
              <td class="py-3">¥{{ formatAmount(order.totalAmount) }}</td>
              <td class="py-3">
                <button class="text-xs text-primary" @click="loadOrderDetail(order.id)">详情</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <section v-if="selectedOrder" class="bg-white rounded-lg border border-outline-variant/10 p-5">
      <h4 class="text-sm font-bold">订单详情</h4>
      <div class="mt-3 text-sm">
        <div>订单号: {{ selectedOrder.orderNo }}</div>
        <div>状态: {{ selectedOrder.status }}</div>
        <div>总额: ¥{{ formatAmount(selectedOrder.totalAmount) }}</div>
      </div>
      <div class="mt-4">
        <h5 class="text-xs font-semibold uppercase tracking-widest text-on-surface-variant">明细</h5>
        <table class="w-full text-sm mt-2" v-if="selectedOrder.items?.length">
          <thead class="text-xs text-on-surface-variant">
            <tr class="text-left">
              <th class="pb-2">产品</th>
              <th class="pb-2">数量</th>
              <th class="pb-2">单价</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in selectedOrder.items" :key="item.id" class="border-t border-outline-variant/20">
              <td class="py-3">{{ item.product?.id || '-' }}</td>
              <td class="py-3">{{ item.quantity }}</td>
              <td class="py-3">¥{{ formatAmount(item.unitPrice) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import { customerApi } from '../api/services.js';

const customerId = ref('');
const orders = ref([]);
const selectedOrder = ref(null);
const loading = ref(false);
const error = ref('');

const loadOrders = async () => {
  error.value = '';
  selectedOrder.value = null;
  if (!customerId.value) {
    error.value = '请输入客户ID。';
    return;
  }
  loading.value = true;
  try {
    const response = await customerApi.listOrders(customerId.value);
    orders.value = response.data || [];
  } catch (err) {
    orders.value = [];
    error.value = err?.response?.data?.message || '查询失败，请确认角色权限。';
  } finally {
    loading.value = false;
  }
};

const loadOrderDetail = async (orderId) => {
  try {
    const response = await customerApi.getOrder(orderId);
    selectedOrder.value = response.data;
  } catch (err) {
    error.value = err?.response?.data?.message || '获取详情失败。';
  }
};

const formatAmount = (value) => (value || 0).toFixed(2);
</script>

