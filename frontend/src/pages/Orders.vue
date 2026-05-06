<template>
  <div class="space-y-6">
    <section v-if="canCreateOrder" class="bg-white rounded-lg border border-outline-variant/10 p-5">
      <h3 class="text-sm font-bold tracking-tight">创建销售订单</h3>
      <form class="mt-4 grid grid-cols-1 md:grid-cols-5 gap-4" @submit.prevent="handleCreate">
        <input v-model="form.orderNo" placeholder="订单号" class="rounded border border-outline-variant/40 px-3 py-2 text-sm" required />
        <input v-model.number="form.customerId" placeholder="客户ID" class="rounded border border-outline-variant/40 px-3 py-2 text-sm" required />
        <input v-model.number="form.createdBy" placeholder="创建人ID" class="rounded border border-outline-variant/40 px-3 py-2 text-sm" />
        <select v-model="itemForm.productId" class="rounded border border-outline-variant/40 px-3 py-2 text-sm" required>
          <option value="">请选择产品</option>
          <option v-for="product in products" :key="product.id" :value="String(product.id)">{{ formatProductLabel(product) }}</option>
        </select>
        <input v-model.number="itemForm.quantity" placeholder="数量" class="rounded border border-outline-variant/40 px-3 py-2 text-sm" required />
        <input v-model.number="itemForm.unitPrice" placeholder="单价" class="rounded border border-outline-variant/40 px-3 py-2 text-sm" required />
        <button type="button" class="rounded border border-primary text-primary px-3 py-2 text-sm" @click="addItem">添加明细</button>
        <button class="rounded bg-primary text-white px-3 py-2 text-sm font-semibold">提交订单</button>
      </form>
      <div v-if="products.length === 0" class="mt-3 text-xs text-amber-700">
        当前没有可下单产品，请先在
        <RouterLink class="font-semibold text-primary" :to="{ name: 'Products' }">产品档案页面</RouterLink>
        维护成品资料。
      </div>
      <div class="mt-4" v-if="formItems.length">
        <p class="text-xs text-on-surface-variant">当前明细：</p>
        <ul class="text-xs mt-2 space-y-1">
          <li v-for="(item, index) in formItems" :key="index">
            <div class="flex flex-wrap items-center gap-2">
              <span>{{ item.product.name }}（{{ item.product.sku }}） | 数量 {{ item.quantity }} | 单价 {{ item.unitPrice }}</span>
              <button type="button" class="text-red-600" @click="removeOrderItem(index)">移除</button>
            </div>
          </li>
        </ul>
      </div>
      <div v-if="createMessage" class="mt-3 text-xs text-emerald-600">{{ createMessage }}</div>
      <div v-if="createError" class="mt-3 text-xs text-error">{{ createError }}</div>
    </section>

    <section v-else class="bg-white rounded-lg border border-outline-variant/10 p-5 text-sm text-on-surface-variant">
      当前角色仅支持查看订单，无法创建新订单。
    </section>

    <section class="bg-white rounded-lg border border-outline-variant/10">
      <div class="p-5 border-b border-surface-container-low flex items-center justify-between">
        <h3 class="text-sm font-bold tracking-tight">订单列表</h3>
        <button class="text-xs text-primary font-semibold" @click="loadOrders">刷新</button>
      </div>
      <div class="p-5">
        <div v-if="loading" class="text-sm text-on-surface-variant">加载中...</div>
        <div v-else-if="orders.length === 0" class="text-sm text-on-surface-variant">暂无订单。</div>
        <table v-else class="w-full text-sm">
          <thead class="text-xs text-on-surface-variant">
            <tr class="text-left">
              <th class="pb-2">订单号</th>
              <th class="pb-2">状态</th>
              <th class="pb-2">产品信息</th>
              <th class="pb-2">数量</th>
              <th class="pb-2">下单人</th>
              <th class="pb-2">收货地址</th>
              <th class="pb-2">总额</th>
              <th class="pb-2">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="order in orders" :key="order.id" class="border-t border-outline-variant/20">
              <td class="py-3 font-semibold">{{ order.orderNo }}</td>
              <td class="py-3">{{ order.status }}</td>
              <td class="py-3">{{ formatProducts(order.items) }}</td>
              <td class="py-3">{{ formatQuantity(order.items) }}</td>
              <td class="py-3">{{ order.customer?.name || order.customer?.contact || '-' }}</td>
              <td class="py-3">{{ order.shippingAddress || '-' }}</td>
              <td class="py-3">¥{{ formatAmount(order.totalAmount) }}</td>
              <td class="py-3">
                <button
                  v-if="canSalesReview && order.status === '待销售审核'"
                  class="text-xs text-primary mr-3"
                  @click="handleSalesDecision(order.id, 'ACCEPT')"
                >
                  通知仓库管理员
                </button>
                <button
                  v-if="canSalesReview && order.status === '待销售审核'"
                  class="text-xs text-red-600 mr-3"
                  @click="handleSalesDecision(order.id, 'REJECT')"
                >
                  拒绝
                </button>
                <button
                  v-if="canWarehouseReview && order.status === '待仓库核查'"
                  class="text-xs text-emerald-600 mr-3"
                  @click="handleWarehouseReview(order.id)"
                >
                  核验库存
                </button>
                <button
                  v-if="canWarehouseReview && order.status === '已接单'"
                  class="text-xs text-emerald-700 mr-3"
                  @click="handleWarehouseShip(order.id)"
                >
                  可发货
                </button>
                <span
                  v-if="canProductionUpdate && order.status === '生产中'"
                  class="text-xs text-on-surface-variant mr-3"
                >
                  请在生产任务页完成领料与生产流程
                </span>
                <button
                  v-if="canSalesReview && order.status === '已发货'"
                  class="text-xs text-emerald-700 mr-3"
                  @click="handleSalesStatusUpdate(order.id, '已完成')"
                >
                  标记已完成
                </button>
                <button v-if="canCreatePlan && order.status === '生产中'" class="text-xs text-primary" @click="handleCreatePlan(order.id)">补充生产计划</button>
                <span v-else-if="!canSalesReview && !canWarehouseReview && !canProductionUpdate" class="text-xs text-on-surface-variant">无权限</span>
              </td>
            </tr>
          </tbody>
        </table>
        <div v-if="planMessage" class="mt-3 text-xs text-emerald-600">{{ planMessage }}</div>
        <div v-if="planError" class="mt-3 text-xs text-error">{{ planError }}</div>
        <div v-if="workflowMessage" class="mt-3 text-xs text-emerald-600">{{ workflowMessage }}</div>
        <div v-if="workflowError" class="mt-3 text-xs text-error">{{ workflowError }}</div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { orderApi, productApi } from '../api/services.js';
import { useRealtimeStore } from '../store/realtime.js';
import { useAuthStore } from '../store/auth.js';

const orders = ref([]);
const loading = ref(false);
const realtime = useRealtimeStore();
const auth = useAuthStore();
const canCreateOrder = computed(() => auth.hasPermission('orders:create'));
const canCreatePlan = computed(() => auth.hasPermission('orders:plan'));
const canSalesReview = computed(() => auth.hasPermission('orders:review'));
const canWarehouseReview = computed(() => auth.hasPermission('orders:warehouse-check'));
const canProductionUpdate = computed(() => auth.hasPermission('production:update'));
const products = ref([]);

const form = reactive({
  orderNo: '',
  customerId: null,
  createdBy: null
});

const itemForm = reactive({
  productId: null,
  quantity: null,
  unitPrice: null
});

const formItems = ref([]);
const createMessage = ref('');
const createError = ref('');
const planMessage = ref('');
const planError = ref('');
const workflowMessage = ref('');
const workflowError = ref('');

const sortOrdersByLatest = (source = []) => [...source].sort((left, right) => {
  const rightTime = new Date(right?.orderDate || right?.createdAt || 0).getTime();
  const leftTime = new Date(left?.orderDate || left?.createdAt || 0).getTime();
  return rightTime - leftTime;
});

const loadOrders = async () => {
  loading.value = true;
  try {
    const response = await orderApi.list();
    orders.value = sortOrdersByLatest(response.data || []);
  } catch (error) {
    orders.value = [];
  } finally {
    loading.value = false;
  }
};

const loadProducts = async () => {
  try {
    const response = await productApi.list({ productType: 'FINISHED_GOOD' });
    products.value = [...(response.data || [])].sort((left, right) => String(left?.name || '').localeCompare(String(right?.name || ''), 'zh-CN'));
  } catch (error) {
    products.value = [];
  }
};

onMounted(async () => {
  await loadOrders();
  if (canCreateOrder.value) {
    await loadProducts();
  }
});

watch(
  () => realtime.state.lastMessage,
  (message) => {
    if (message?.topic && message.topic.startsWith('/topic/orders')) {
      loadOrders();
    }
  }
);

watch(
  () => itemForm.productId,
  (productId) => {
    const product = products.value.find((item) => String(item.id) === String(productId));
    if (product) {
      itemForm.unitPrice = Number(product.unitPrice || 0);
    }
  }
);

const addItem = () => {
  if (!itemForm.productId || !itemForm.quantity || !itemForm.unitPrice) {
    return;
  }
  const product = products.value.find((item) => String(item.id) === String(itemForm.productId));
  if (!product) {
    createError.value = '请选择有效产品后再添加明细。';
    return;
  }
  formItems.value.push({
    product: { id: Number(product.id), name: product.name, sku: product.sku },
    quantity: Number(itemForm.quantity),
    unitPrice: Number(itemForm.unitPrice)
  });
  createError.value = '';
  itemForm.productId = '';
  itemForm.quantity = null;
  itemForm.unitPrice = null;
};

const removeOrderItem = (index) => {
  formItems.value = formItems.value.filter((_, itemIndex) => itemIndex !== index);
};

const handleCreate = async () => {
  createMessage.value = '';
  createError.value = '';
  if (!formItems.value.length) {
    createError.value = '请至少添加一条订单明细。';
    return;
  }
  try {
    const payload = {
      orderNo: form.orderNo,
      customer: { id: Number(form.customerId) },
      createdBy: form.createdBy ? Number(form.createdBy) : null,
      items: formItems.value
    };
    await orderApi.create(payload);
    createMessage.value = '订单创建成功。';
    formItems.value = [];
    form.orderNo = '';
    form.customerId = null;
    form.createdBy = null;
    await loadOrders();
      await loadProducts();
  } catch (error) {
    createError.value = error?.response?.data?.message || '订单创建失败。';
  }
};

const handleCreatePlan = async (orderId) => {
  planMessage.value = '';
  planError.value = '';
  try {
    await orderApi.createPlan(orderId);
    planMessage.value = `订单 ${orderId} 已生成生产计划。`;
  } catch (error) {
    planError.value = error?.response?.data?.message || '生成生产计划失败。';
  }
};

const handleSalesDecision = async (orderId, decision) => {
  workflowMessage.value = '';
  workflowError.value = '';
  try {
    let payload = {};
    if (decision === 'REJECT') {
      const reason = window.prompt('请输入拒绝理由：');
      if (!String(reason || '').trim()) {
        workflowError.value = '拒绝订单时必须填写拒绝理由。';
        return;
      }
      payload = { note: reason.trim() };
    }
    await orderApi.salesDecision(orderId, decision, payload);
    workflowMessage.value = decision === 'REJECT'
      ? `订单 ${orderId} 已拒绝。`
      : `订单 ${orderId} 已通知仓库管理员查看。`;
    await loadOrders();
  } catch (error) {
    workflowError.value = error?.response?.data?.message || error?.response?.data || '操作失败。';
  }
};

const handleSalesStatusUpdate = async (orderId, status) => {
  workflowMessage.value = '';
  workflowError.value = '';
  try {
    await orderApi.updateSalesStatus(orderId, status);
    workflowMessage.value = `订单 ${orderId} 已更新为 ${status}。`;
    await loadOrders();
  } catch (error) {
    workflowError.value = error?.response?.data?.message || error?.response?.data || '状态更新失败。';
  }
};

const handleWarehouseShip = async (orderId) => {
  workflowMessage.value = '';
  workflowError.value = '';
  try {
    await orderApi.warehouseShip(orderId, {});
    workflowMessage.value = `订单 ${orderId} 已发货，顾客与销售管理员已收到通知。`;
    await loadOrders();
  } catch (error) {
    workflowError.value = error?.response?.data?.message || error?.response?.data || '发货失败。';
  }
};

const handleProductionComplete = async (orderId) => {
  workflowMessage.value = '';
  workflowError.value = '';
  try {
    await orderApi.productionComplete(orderId, {});
    workflowMessage.value = `订单 ${orderId} 已完成生产，并已通知仓库管理员确认入库。`;
    await loadOrders();
  } catch (error) {
    workflowError.value = error?.response?.data?.message || error?.response?.data || '生产回传失败。';
  }
};

const handleWarehouseReview = async (orderId) => {
  workflowMessage.value = '';
  workflowError.value = '';
  try {
    const response = await orderApi.warehouseReview(orderId, {});
    const shortageCount = response?.data?.shortages?.length || 0;
    workflowMessage.value = shortageCount > 0
      ? `已将生产计划发送给生产管理员处理。`
      : `订单 ${orderId} 库存核验通过，并已通知销售管理员。`;
    await loadOrders();
  } catch (error) {
    workflowError.value = error?.response?.data?.message || error?.response?.data || '库存核查失败。';
  }
};

const formatAmount = (value) => (value || 0).toFixed(2);
const formatDate = (value) => (value ? new Date(value).toLocaleString() : '-');
const formatProducts = (items) => (items || []).map((item) => item.product?.name || `产品#${item.product?.id || '-'}`).join('，') || '-';
const formatQuantity = (items) => (items || []).reduce((sum, item) => sum + Number(item.quantity || 0), 0);
const formatProductLabel = (product) => `${product?.name || '未命名产品'}（${product?.sku || '-'}）`;
</script>

