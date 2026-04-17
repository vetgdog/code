<template>
  <div class="space-y-6">
    <section class="bg-white rounded-lg border border-outline-variant/10 p-5">
      <h3 class="text-sm font-bold tracking-tight">客户下单</h3>
      <form class="mt-4 grid grid-cols-1 md:grid-cols-2 gap-3" @submit.prevent="submitOrder">
        <div>
          <label class="text-xs text-on-surface-variant">1. 选择产品名称</label>
          <select v-model="orderForm.productId" class="mt-1 w-full rounded border border-outline-variant/40 px-3 py-2 text-sm" required>
            <option :value="''" disabled>请选择产品</option>
            <option v-for="product in products" :key="product.id" :value="String(product.id)">{{ product.name }}</option>
          </select>
        </div>

        <div>
          <label class="text-xs text-on-surface-variant">2. 产品单价(自动展示)</label>
          <input :value="formatAmount(unitPrice)" class="mt-1 w-full rounded border border-outline-variant/40 px-3 py-2 text-sm bg-slate-50" readonly />
        </div>

        <div>
          <label class="text-xs text-on-surface-variant">3. 产品数量</label>
          <input
            v-model.number="orderForm.quantity"
            type="number"
            min="0.01"
            step="0.01"
            :disabled="!isProductSelected"
            class="mt-1 w-full rounded border border-outline-variant/40 px-3 py-2 text-sm disabled:bg-slate-100"
            placeholder="请输入数量"
            required
          />
        </div>

        <div>
          <label class="text-xs text-on-surface-variant">4. 总价格(自动计算)</label>
          <input :value="formatAmount(totalPrice)" class="mt-1 w-full rounded border border-outline-variant/40 px-3 py-2 text-sm bg-slate-50" readonly />
        </div>

        <div class="md:col-span-2">
          <label class="text-xs text-on-surface-variant">5. 下单地址</label>
          <input
            v-model="orderForm.shippingAddress"
            :disabled="!isQuantityValid"
            class="mt-1 w-full rounded border border-outline-variant/40 px-3 py-2 text-sm disabled:bg-slate-100"
            placeholder="请输入收货地址"
            required
          />
        </div>

        <div>
          <label class="text-xs text-on-surface-variant">订单号(可选)</label>
          <input v-model="orderForm.orderNo" class="mt-1 w-full rounded border border-outline-variant/40 px-3 py-2 text-sm" placeholder="留空自动生成" />
        </div>

        <div class="flex items-end">
          <button
            class="w-full rounded bg-primary text-white px-4 py-2 text-sm font-semibold disabled:opacity-50 disabled:cursor-not-allowed"
            :disabled="!canSubmit || submitting"
          >
            {{ submitting ? '提交中...' : '提交订单' }}
          </button>
        </div>
      </form>

      <div v-if="validationHint" class="mt-3 text-xs text-amber-600">{{ validationHint }}</div>
      <div v-if="createMessage" class="mt-3 text-xs text-emerald-600">{{ createMessage }}</div>
      <div v-if="createError" class="mt-3 text-xs text-error">{{ createError }}</div>
    </section>

    <section class="bg-white rounded-lg border border-outline-variant/10">
      <div class="p-5 border-b border-surface-container-low flex items-center justify-between">
        <h4 class="text-sm font-bold">我的订单</h4>
        <div class="flex items-center gap-3">
          <span class="text-xs text-on-surface-variant">{{ orders.length }} 条</span>
          <button class="text-xs text-primary font-semibold" @click="loadOrders">刷新</button>
        </div>
      </div>
      <div class="p-5">
        <div v-if="error" class="mb-3 text-xs text-error">{{ error }}</div>
        <div v-if="loading" class="text-sm text-on-surface-variant">加载中...</div>
        <div v-else-if="orders.length === 0" class="text-sm text-on-surface-variant">用户未下过单。</div>
        <table v-else class="w-full text-sm">
          <thead class="text-xs text-on-surface-variant">
            <tr class="text-left">
              <th class="pb-2">订单号</th>
              <th class="pb-2">产品信息</th>
              <th class="pb-2">数量</th>
              <th class="pb-2">状态</th>
              <th class="pb-2">总额</th>
              <th class="pb-2">收货地址</th>
              <th class="pb-2">下单时间</th>
              <th class="pb-2">查看</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="order in orders" :key="order.id" class="border-t border-outline-variant/20">
              <td class="py-3 font-semibold">{{ order.orderNo }}</td>
              <td class="py-3">{{ formatProducts(order.items) }}</td>
              <td class="py-3">{{ formatQuantity(order.items) }}</td>
              <td class="py-3">{{ order.status }}</td>
              <td class="py-3">¥{{ formatAmount(order.totalAmount) }}</td>
              <td class="py-3">{{ order.shippingAddress || '-' }}</td>
              <td class="py-3">{{ formatDate(order.orderDate || order.createdAt) }}</td>
              <td class="py-3">
                <button class="text-xs text-primary" @click="loadOrderDetail(order.id)">详情</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <section class="bg-white rounded-lg border border-outline-variant/10 p-5">
      <div class="flex items-center justify-between gap-4">
        <div>
          <h4 class="text-sm font-bold">质量追溯</h4>
          <p class="mt-1 text-xs text-on-surface-variant">输入自己的订单号，可查看对应产品批次是否质检合格及详细检验记录。</p>
        </div>
      </div>
      <div class="mt-4 flex flex-col md:flex-row gap-3">
        <input v-model="qualityTraceOrderNo" class="rounded border border-outline-variant/40 px-3 py-2 text-sm flex-1" placeholder="请输入订单号，如 SO-XXXX" @keyup.enter="loadQualityTrace" />
        <button class="rounded bg-primary text-white px-4 py-2 text-sm font-semibold disabled:opacity-50" :disabled="!qualityTraceOrderNo || traceLoading" @click="loadQualityTrace">
          {{ traceLoading ? '查询中...' : '查询质检结果' }}
        </button>
      </div>
      <div v-if="traceError" class="mt-3 text-xs text-error">{{ traceError }}</div>
      <div v-if="qualityTraceResult" class="mt-4 space-y-4">
        <div class="rounded-lg border border-outline-variant/20 bg-slate-50 p-4 text-sm">
          <div>订单号：{{ qualityTraceResult.orderNo }}</div>
          <div class="mt-1">订单状态：{{ qualityTraceResult.status || '-' }}</div>
        </div>
        <div v-if="!(qualityTraceResult.batches || []).length" class="text-sm text-on-surface-variant">当前订单尚未生成可追溯批次。</div>
        <div v-for="batch in qualityTraceResult.batches || []" :key="batch.id" class="rounded-lg border border-outline-variant/20 p-4">
          <div class="flex items-center justify-between gap-3">
            <div class="font-semibold text-sm">批次 {{ batch.batchNo }}</div>
            <div class="text-xs px-2 py-1 rounded-full" :class="traceStatusClass(batch.qualityStatus)">{{ batch.qualityStatus || '待检' }}</div>
          </div>
          <div class="mt-3 grid grid-cols-1 md:grid-cols-2 gap-2 text-sm">
            <div>产品：{{ batch.productName || '-' }}（{{ batch.productSku || '-' }}）</div>
            <div>数量：{{ formatNumber(batch.quantity) }}</div>
            <div>质检员：{{ batch.inspectorName || '-' }}</div>
            <div>质检时间：{{ formatDate(batch.inspectedAt) }}</div>
            <div class="md:col-span-2">质检结论：{{ batch.qualityRemark || '-' }}</div>
          </div>
          <div class="mt-3">
            <div class="text-xs font-semibold text-on-surface-variant mb-2">质检记录</div>
            <div v-if="!(batch.records || []).length" class="text-sm text-on-surface-variant">暂无质检记录。</div>
            <table v-else class="w-full text-sm">
              <thead class="text-xs text-on-surface-variant">
                <tr class="text-left">
                  <th class="pb-2">检验人</th>
                  <th class="pb-2">结果</th>
                  <th class="pb-2">备注</th>
                  <th class="pb-2">时间</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="record in batch.records" :key="record.id" class="border-t border-outline-variant/20">
                  <td class="py-2">{{ record.inspectorName || '-' }}</td>
                  <td class="py-2">{{ record.result || '-' }}</td>
                  <td class="py-2">{{ record.remarks || '-' }}</td>
                  <td class="py-2">{{ formatDate(record.inspectionDate) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </section>

    <section v-if="selectedOrder" class="bg-white rounded-lg border border-outline-variant/10 p-5">
      <h4 class="text-sm font-bold">订单详情</h4>
      <div class="mt-3 text-sm">
        <div>订单号: {{ selectedOrder.orderNo }}</div>
        <div>状态: {{ selectedOrder.status }}</div>
        <div>总额: ¥{{ formatAmount(selectedOrder.totalAmount) }}</div>
        <div>总数量: {{ formatQuantity(selectedOrder.items) }}</div>
        <div>收货地址: {{ selectedOrder.shippingAddress || '-' }}</div>
        <div>下单时间: {{ formatDate(selectedOrder.orderDate || selectedOrder.createdAt) }}</div>
      </div>
      <div class="mt-4">
        <h5 class="text-xs font-semibold uppercase tracking-widest text-on-surface-variant">明细</h5>
        <table class="w-full text-sm mt-2" v-if="selectedOrder.items?.length">
          <thead class="text-xs text-on-surface-variant">
            <tr class="text-left">
              <th class="pb-2">产品</th>
              <th class="pb-2">数量</th>
              <th class="pb-2">单价</th>
              <th class="pb-2">金额</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in selectedOrder.items" :key="item.id" class="border-t border-outline-variant/20">
              <td class="py-3">{{ item.product?.name || item.product?.sku || '-' }}</td>
              <td class="py-3">{{ formatNumber(item.quantity) }}</td>
              <td class="py-3">¥{{ formatAmount(item.unitPrice) }}</td>
              <td class="py-3">¥{{ formatAmount(item.lineTotal ?? (Number(item.quantity || 0) * Number(item.unitPrice || 0))) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { customerApi, productApi } from '../api/services.js';
import { useRealtimeStore } from '../store/realtime.js';

const realtime = useRealtimeStore();
const orders = ref([]);
const selectedOrder = ref(null);
const loading = ref(false);
const error = ref('');
const createMessage = ref('');
const createError = ref('');
const products = ref([]);
const submitting = ref(false);
const qualityTraceOrderNo = ref('');
const qualityTraceResult = ref(null);
const traceError = ref('');
const traceLoading = ref(false);

const orderForm = reactive({
  productId: '',
  quantity: null,
  shippingAddress: '',
  orderNo: '',
  unitPrice: 0
});

const isProductSelected = computed(() => Boolean(orderForm.productId));
const isQuantityValid = computed(() => Number(orderForm.quantity) > 0);
const isAddressValid = computed(() => orderForm.shippingAddress.trim().length > 0);
const unitPrice = computed(() => {
  const selected = products.value.find((item) => String(item.id) === String(orderForm.productId));
  return selected?.unitPrice == null ? 0 : Number(selected.unitPrice);
});
const totalPrice = computed(() => unitPrice.value * Number(orderForm.quantity || 0));
const validationHint = computed(() => {
  if (!isProductSelected.value) {
    return '请先选择产品名称。';
  }
  if (!isQuantityValid.value) {
    return '请填写有效的产品数量。';
  }
  if (!isAddressValid.value) {
    return '请填写下单地址后再提交。';
  }
  return '';
});
const canSubmit = computed(() => !validationHint.value);

const sortOrdersByLatest = (source = []) => [...source].sort((left, right) => {
  const rightTime = new Date(right?.orderDate || right?.createdAt || 0).getTime();
  const leftTime = new Date(left?.orderDate || left?.createdAt || 0).getTime();
  return rightTime - leftTime;
});

const loadOrders = async () => {
  error.value = '';
  selectedOrder.value = null;
  loading.value = true;
  try {
    const response = await customerApi.listMyOrders();
    orders.value = sortOrdersByLatest(response.data || []);
  } catch (err) {
    orders.value = [];
    error.value = '';
  } finally {
    loading.value = false;
  }
};

const loadProducts = async () => {
  try {
    const response = await productApi.list({ productType: 'FINISHED_GOOD' });
    products.value = response.data || [];
  } catch (err) {
    products.value = [];
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

const loadQualityTrace = async () => {
  traceError.value = '';
  qualityTraceResult.value = null;
  if (!qualityTraceOrderNo.value) {
    traceError.value = '请输入订单号后再查询。';
    return;
  }
  traceLoading.value = true;
  try {
    const response = await customerApi.traceQuality(qualityTraceOrderNo.value);
    qualityTraceResult.value = response.data || null;
  } catch (err) {
    traceError.value = err?.response?.data?.message || err?.response?.data || '质量追溯查询失败。';
  } finally {
    traceLoading.value = false;
  }
};

const submitOrder = async () => {
  createMessage.value = '';
  createError.value = '';
  if (!canSubmit.value) {
    createError.value = validationHint.value;
    return;
  }

  submitting.value = true;
  try {
    await customerApi.createOrder({
      orderNo: orderForm.orderNo || undefined,
      shippingAddress: orderForm.shippingAddress,
      items: [
        {
          productId: Number(orderForm.productId),
          quantity: Number(orderForm.quantity)
        }
      ]
    });
    createMessage.value = '下单成功。';
    qualityTraceOrderNo.value = orderForm.orderNo || '';
    orderForm.productId = '';
    orderForm.quantity = null;
    orderForm.shippingAddress = '';
    orderForm.orderNo = '';
    await loadOrders();
  } catch (err) {
    if (err?.response?.status === 403) {
      createError.value = '当前账号没有客户下单权限，请重新登录客户账号后重试。';
    } else {
      createError.value = err?.response?.data?.message || err?.response?.data || '下单失败，请稍后重试。';
    }
  } finally {
    submitting.value = false;
  }
};

watch(
  () => realtime.state.lastMessage,
  (message) => {
    if (message?.topic?.startsWith('/topic/orders/customer')) {
      loadOrders();
    }
  }
);

onMounted(() => {
  loadProducts();
  loadOrders();
});

const formatAmount = (value) => (value || 0).toFixed(2);
const formatDate = (value) => (value ? new Date(value).toLocaleString() : '-');
const formatNumber = (value) => Number(value || 0).toFixed(2);
const formatProducts = (items) => (items || []).map((item) => item.product?.name || item.product?.sku || '-').join('，') || '-';
const formatQuantity = (items) => formatNumber((items || []).reduce((sum, item) => sum + Number(item.quantity || 0), 0));
const traceStatusClass = (status) => {
  if (status === '合格') {
    return 'bg-emerald-100 text-emerald-700';
  }
  if (status === '不合格') {
    return 'bg-red-100 text-red-700';
  }
  return 'bg-amber-100 text-amber-700';
};
</script>

