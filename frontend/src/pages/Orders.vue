<template>
  <div class="space-y-6">
    <section v-if="canManageProducts" class="bg-white rounded-lg border border-outline-variant/10 p-5">
      <h3 class="text-sm font-bold tracking-tight">产品录入</h3>
      <form class="mt-4 grid grid-cols-1 md:grid-cols-5 gap-3" @submit.prevent="handleCreateProduct">
        <input v-model="productForm.sku" placeholder="SKU" class="rounded border border-outline-variant/40 px-3 py-2 text-sm" required />
        <input v-model="productForm.name" placeholder="产品名称" class="rounded border border-outline-variant/40 px-3 py-2 text-sm" required />
        <input v-model="productForm.unit" placeholder="单位(可选)" class="rounded border border-outline-variant/40 px-3 py-2 text-sm" />
        <input v-model.number="productForm.unitPrice" placeholder="默认单价" class="rounded border border-outline-variant/40 px-3 py-2 text-sm" type="number" min="0" step="0.01" />
        <button class="rounded bg-primary text-white px-3 py-2 text-sm font-semibold">保存产品</button>
      </form>
      <div v-if="productMessage" class="mt-3 text-xs text-emerald-600">{{ productMessage }}</div>
      <div v-if="productError" class="mt-3 text-xs text-error">{{ productError }}</div>

      <div class="mt-4 border-t border-outline-variant/20 pt-4">
        <p class="text-xs font-semibold text-on-surface-variant mb-2">已录入产品</p>
        <div v-if="products.length === 0" class="text-xs text-on-surface-variant">暂无产品。</div>
        <table v-else class="w-full text-xs">
          <thead>
            <tr class="text-left text-on-surface-variant">
              <th class="pb-2">SKU</th>
              <th class="pb-2">名称</th>
              <th class="pb-2">单价</th>
              <th class="pb-2">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="p in products" :key="p.id" class="border-t border-outline-variant/20">
              <td class="py-2">{{ p.sku }}</td>
              <td class="py-2">{{ p.name }}</td>
              <td class="py-2">{{ formatAmount(p.unitPrice) }}</td>
              <td class="py-2">
                <button class="text-primary mr-3" @click="prepareEditProduct(p)">编辑</button>
                <button class="text-red-600" @click="handleDeleteProduct(p.id)">删除</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <form v-if="editingProductId" class="mt-4 grid grid-cols-1 md:grid-cols-4 gap-3" @submit.prevent="handleUpdateProduct">
        <input v-model="editProductForm.sku" placeholder="SKU" class="rounded border border-outline-variant/40 px-3 py-2 text-sm" required />
        <input v-model="editProductForm.name" placeholder="产品名称" class="rounded border border-outline-variant/40 px-3 py-2 text-sm" required />
        <input v-model="editProductForm.unit" placeholder="单位" class="rounded border border-outline-variant/40 px-3 py-2 text-sm" />
        <input v-model.number="editProductForm.unitPrice" type="number" min="0" step="0.01" placeholder="单价" class="rounded border border-outline-variant/40 px-3 py-2 text-sm" />
        <button class="rounded bg-primary text-white px-3 py-2 text-sm font-semibold">保存修改</button>
        <button type="button" class="rounded border border-outline-variant/50 px-3 py-2 text-sm" @click="cancelEditProduct">取消</button>
      </form>
    </section>

    <section v-if="canCreateOrder" class="bg-white rounded-lg border border-outline-variant/10 p-5">
      <h3 class="text-sm font-bold tracking-tight">创建销售订单</h3>
      <form class="mt-4 grid grid-cols-1 md:grid-cols-5 gap-4" @submit.prevent="handleCreate">
        <input v-model="form.orderNo" placeholder="订单号" class="rounded border border-outline-variant/40 px-3 py-2 text-sm" required />
        <input v-model.number="form.customerId" placeholder="客户ID" class="rounded border border-outline-variant/40 px-3 py-2 text-sm" required />
        <input v-model.number="form.createdBy" placeholder="创建人ID" class="rounded border border-outline-variant/40 px-3 py-2 text-sm" />
        <input v-model.number="itemForm.productId" placeholder="产品ID" class="rounded border border-outline-variant/40 px-3 py-2 text-sm" required />
        <input v-model.number="itemForm.quantity" placeholder="数量" class="rounded border border-outline-variant/40 px-3 py-2 text-sm" required />
        <input v-model.number="itemForm.unitPrice" placeholder="单价" class="rounded border border-outline-variant/40 px-3 py-2 text-sm" required />
        <button type="button" class="rounded border border-primary text-primary px-3 py-2 text-sm" @click="addItem">添加明细</button>
        <button class="rounded bg-primary text-white px-3 py-2 text-sm font-semibold">提交订单</button>
      </form>
      <div class="mt-4" v-if="formItems.length">
        <p class="text-xs text-on-surface-variant">当前明细：</p>
        <ul class="text-xs mt-2 space-y-1">
          <li v-for="(item, index) in formItems" :key="index">
            产品 {{ item.product.id }} | 数量 {{ item.quantity }} | 单价 {{ item.unitPrice }}
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
              <th class="pb-2">客户</th>
              <th class="pb-2">总额</th>
              <th class="pb-2">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="order in orders" :key="order.id" class="border-t border-outline-variant/20">
              <td class="py-3 font-semibold">{{ order.orderNo }}</td>
              <td class="py-3">{{ order.status }}</td>
              <td class="py-3">{{ order.customer?.id || '-' }}</td>
              <td class="py-3">¥{{ formatAmount(order.totalAmount) }}</td>
              <td class="py-3">
                <button
                  v-if="canSalesReview && order.status === '待销售审核'"
                  class="text-xs text-primary mr-3"
                  @click="handleSalesDecision(order.id, 'ACCEPT')"
                >
                  接受并通知仓库
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
                  库存核查并流转
                </button>
                <button
                  v-if="canWarehouseReview && order.status === '已接单'"
                  class="text-xs text-emerald-700 mr-3"
                  @click="handleWarehouseShip(order.id)"
                >
                  仓库确认发货
                </button>
                <button
                  v-if="canProductionUpdate && order.status === '生产中'"
                  class="text-xs text-orange-600 mr-3"
                  @click="handleProductionComplete(order.id)"
                >
                  生产完成通知仓库
                </button>
                <button
                  v-if="canSalesReview && order.status === '已发货'"
                  class="text-xs text-emerald-700 mr-3"
                  @click="handleSalesStatusUpdate(order.id, '已完成')"
                >
                  标记已完成
                </button>
                <button v-if="canCreatePlan" class="text-xs text-primary" @click="handleCreatePlan(order.id)">生成生产计划</button>
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
const canManageProducts = computed(() => auth.hasPermission('orders:create'));
const products = ref([]);
const editingProductId = ref(null);
const editProductForm = reactive({ sku: '', name: '', unit: '', unitPrice: null });

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
const productMessage = ref('');
const productError = ref('');

const productForm = reactive({
  sku: '',
  name: '',
  unit: '',
  unitPrice: null
});

const loadOrders = async () => {
  loading.value = true;
  try {
    const response = await orderApi.list();
    orders.value = response.data || [];
  } catch (error) {
    orders.value = [];
  } finally {
    loading.value = false;
  }
};

const loadProducts = async () => {
  try {
    const response = await productApi.list();
    products.value = response.data || [];
  } catch (error) {
    products.value = [];
  }
};

onMounted(async () => {
  await loadOrders();
  if (canManageProducts.value) {
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

const addItem = () => {
  if (!itemForm.productId || !itemForm.quantity || !itemForm.unitPrice) {
    return;
  }
  formItems.value.push({
    product: { id: Number(itemForm.productId) },
    quantity: Number(itemForm.quantity),
    unitPrice: Number(itemForm.unitPrice)
  });
  itemForm.productId = null;
  itemForm.quantity = null;
  itemForm.unitPrice = null;
};

const handleCreate = async () => {
  createMessage.value = '';
  createError.value = '';
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
    await orderApi.salesDecision(orderId, decision, {});
    workflowMessage.value = decision === 'REJECT'
      ? `订单 ${orderId} 已拒绝。`
      : `订单 ${orderId} 已通过销售审核并通知仓库核查。`;
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
    workflowMessage.value = `订单 ${orderId} 已由仓库发货并通知销售。`;
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
    workflowMessage.value = `订单 ${orderId} 已通知仓库重新核查库存。`;
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
      ? `订单 ${orderId} 库存不足，已自动通知生产并生成计划。`
      : `订单 ${orderId} 库存充足，已流转为已接单。`;
    await loadOrders();
  } catch (error) {
    workflowError.value = error?.response?.data?.message || error?.response?.data || '库存核查失败。';
  }
};

const handleCreateProduct = async () => {
  productMessage.value = '';
  productError.value = '';
  try {
    await productApi.create({
      sku: productForm.sku,
      name: productForm.name,
      unit: productForm.unit || null,
      unitPrice: productForm.unitPrice == null ? 0 : Number(productForm.unitPrice)
    });
    productMessage.value = '产品录入成功。';
    productForm.sku = '';
    productForm.name = '';
    productForm.unit = '';
    productForm.unitPrice = null;
    await loadProducts();
  } catch (error) {
    productError.value = error?.response?.data?.message || error?.response?.data || '产品录入失败。';
  }
};

const prepareEditProduct = (product) => {
  editingProductId.value = product.id;
  editProductForm.sku = product.sku || '';
  editProductForm.name = product.name || '';
  editProductForm.unit = product.unit || '';
  editProductForm.unitPrice = Number(product.unitPrice || 0);
};

const cancelEditProduct = () => {
  editingProductId.value = null;
  editProductForm.sku = '';
  editProductForm.name = '';
  editProductForm.unit = '';
  editProductForm.unitPrice = null;
};

const handleUpdateProduct = async () => {
  productMessage.value = '';
  productError.value = '';
  try {
    await productApi.update(editingProductId.value, {
      sku: editProductForm.sku,
      name: editProductForm.name,
      unit: editProductForm.unit || null,
      unitPrice: Number(editProductForm.unitPrice || 0)
    });
    productMessage.value = '产品更新成功。';
    cancelEditProduct();
    await loadProducts();
  } catch (error) {
    productError.value = error?.response?.data?.message || error?.response?.data || '产品更新失败。';
  }
};

const handleDeleteProduct = async (id) => {
  productMessage.value = '';
  productError.value = '';
  try {
    await productApi.remove(id);
    productMessage.value = '产品删除成功。';
    await loadProducts();
  } catch (error) {
    productError.value = error?.response?.data?.message || error?.response?.data || '产品删除失败。';
  }
};

const formatAmount = (value) => (value || 0).toFixed(2);
</script>

