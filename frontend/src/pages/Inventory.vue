<template>
  <div class="space-y-6">
    <section v-if="canEditInventory" class="bg-white rounded-lg border border-outline-variant/10 p-5">
      <div class="flex items-center justify-between gap-4">
        <div>
          <h3 class="text-sm font-bold tracking-tight">出库 / 入库操作</h3>
          <p class="mt-1 text-xs text-on-surface-variant">根据实际业务填写产品、仓库、数量、批次与业务类型，系统将自动记录出入库流水。</p>
        </div>
      </div>
      <form class="mt-4 grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4" @submit.prevent>
        <select v-model="form.productId" class="rounded border border-outline-variant/40 px-3 py-2 text-sm" required>
          <option value="">请选择产品</option>
          <option v-for="product in products" :key="product.id" :value="String(product.id)">
            {{ product.name }}（{{ product.sku }}）
          </option>
        </select>
        <select v-model="form.warehouseId" class="rounded border border-outline-variant/40 px-3 py-2 text-sm" required>
          <option value="">请选择仓库</option>
          <option v-for="warehouse in warehouses" :key="warehouse.id" :value="String(warehouse.id)">
            {{ warehouse.name }}（{{ warehouse.code }}）
          </option>
        </select>
        <input v-model.number="form.changeQuantity" placeholder="变动数量" class="rounded border border-outline-variant/40 px-3 py-2 text-sm" type="number" min="0.01" step="0.01" required />
        <input v-model="form.lot" placeholder="批次号（选填）" class="rounded border border-outline-variant/40 px-3 py-2 text-sm" />
        <select v-model="form.relatedType" class="rounded border border-outline-variant/40 px-3 py-2 text-sm">
          <option v-for="option in relatedTypeOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
        </select>
        <input v-model="form.relatedId" placeholder="关联单号 / 单据ID（选填）" class="rounded border border-outline-variant/40 px-3 py-2 text-sm" />
        <textarea v-model="form.remark" placeholder="备注说明（如手工调整原因、入库来源等）" class="md:col-span-2 xl:col-span-3 rounded border border-outline-variant/40 px-3 py-2 text-sm min-h-24"></textarea>
        <div class="flex flex-wrap gap-3 md:col-span-2 xl:col-span-3">
          <button class="rounded bg-primary text-white px-4 py-2 text-sm font-semibold" @click="handleStock('IN')">入库</button>
          <button class="rounded border border-primary text-primary px-4 py-2 text-sm font-semibold" @click="handleStock('OUT')">出库</button>
          <button type="button" class="rounded border border-outline-variant/40 px-4 py-2 text-sm" @click="resetForm">清空</button>
        </div>
      </form>
      <div v-if="message" class="mt-3 text-xs text-emerald-600">{{ message }}</div>
      <div v-if="error" class="mt-3 text-xs text-error">{{ error }}</div>
    </section>

    <section v-else class="bg-white rounded-lg border border-outline-variant/10 p-5 text-sm text-on-surface-variant">
      当前角色仅支持查看库存，无法执行入库/出库操作。
    </section>

    <section class="bg-white rounded-lg border border-outline-variant/10">
      <div class="p-5 border-b border-surface-container-low flex items-center justify-between">
        <div>
          <h3 class="text-sm font-bold tracking-tight">库存明细</h3>
          <p class="mt-1 text-xs text-on-surface-variant">支持按产品名称、SKU、仓库、批次或编号查询库存。</p>
        </div>
        <button class="text-xs text-primary font-semibold" @click="loadItems">刷新</button>
      </div>
      <div class="p-5">
        <div class="mb-4 grid grid-cols-1 md:grid-cols-4 gap-3">
          <input v-model="inventoryFilter.keyword" placeholder="搜索产品 / SKU / 仓库 / 批次" class="rounded border border-outline-variant/40 px-3 py-2 text-sm" @keyup.enter="loadItems" />
          <select v-model="inventoryFilter.productId" class="rounded border border-outline-variant/40 px-3 py-2 text-sm">
            <option value="">全部产品</option>
            <option v-for="product in products" :key="product.id" :value="String(product.id)">{{ product.name }}</option>
          </select>
          <select v-model="inventoryFilter.warehouseId" class="rounded border border-outline-variant/40 px-3 py-2 text-sm">
            <option value="">全部仓库</option>
            <option v-for="warehouse in warehouses" :key="warehouse.id" :value="String(warehouse.id)">{{ warehouse.name }}</option>
          </select>
          <div class="flex items-center gap-3">
            <button class="rounded border border-primary text-primary px-3 py-2 text-sm font-semibold" @click="loadItems">查询</button>
            <button class="text-xs text-on-surface-variant" @click="resetInventoryFilter">重置</button>
          </div>
        </div>
        <div v-if="loading" class="text-sm text-on-surface-variant">加载中...</div>
        <div v-else-if="items.length === 0" class="text-sm text-on-surface-variant">暂无库存。</div>
        <table v-else class="w-full text-sm">
          <thead class="text-xs text-on-surface-variant">
            <tr class="text-left">
              <th class="pb-2">产品名称</th>
              <th class="pb-2">SKU</th>
              <th class="pb-2">仓库</th>
              <th class="pb-2">总库存</th>
              <th class="pb-2">可用库存</th>
              <th class="pb-2">预留库存</th>
              <th class="pb-2">批次号</th>
              <th class="pb-2">更新时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in items" :key="item.id" class="border-t border-outline-variant/20">
              <td class="py-3 font-semibold">{{ item.product?.name || '-' }}</td>
              <td class="py-3">{{ item.product?.sku || '-' }}</td>
              <td class="py-3">{{ item.warehouse?.name || item.warehouse?.code || '-' }}</td>
              <td class="py-3">{{ formatQuantity(item.quantity) }}</td>
              <td class="py-3">{{ formatAvailable(item) }}</td>
              <td class="py-3">{{ formatQuantity(item.reservedQuantity) }}</td>
              <td class="py-3">{{ item.lot || '-' }}</td>
              <td class="py-3">{{ formatDate(item.updatedAt) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <section class="bg-white rounded-lg border border-outline-variant/10">
      <div class="p-5 border-b border-surface-container-low flex items-center justify-between">
        <div>
          <h3 class="text-sm font-bold tracking-tight">出入库记录</h3>
          <p class="mt-1 text-xs text-on-surface-variant">类似销售记录，集中查看所有入库 / 出库流水。</p>
        </div>
        <button class="text-xs text-primary font-semibold" @click="loadTransactions">刷新</button>
      </div>
      <div class="p-5">
        <div class="mb-4 grid grid-cols-1 md:grid-cols-6 gap-3">
          <input v-model="transactionFilter.keyword" placeholder="搜索流水号 / 产品 / 仓库 / 业务类型 / 批次" class="rounded border border-outline-variant/40 px-3 py-2 text-sm md:col-span-2" @keyup.enter="loadTransactions" />
          <select v-model="transactionFilter.type" class="rounded border border-outline-variant/40 px-3 py-2 text-sm">
            <option value="">全部类型</option>
            <option value="IN">入库</option>
            <option value="OUT">出库</option>
          </select>
          <input v-model="transactionFilter.startDate" type="date" class="rounded border border-outline-variant/40 px-3 py-2 text-sm" />
          <input v-model="transactionFilter.endDate" type="date" class="rounded border border-outline-variant/40 px-3 py-2 text-sm" />
          <div class="flex items-center gap-3">
            <button class="rounded border border-primary text-primary px-3 py-2 text-sm font-semibold" @click="loadTransactions">查询</button>
            <button class="text-xs text-on-surface-variant" @click="resetTransactionFilter">重置</button>
          </div>
        </div>
        <div v-if="transactionError" class="mb-3 text-xs text-error">{{ transactionError }}</div>
        <div v-if="transactions.length === 0" class="text-sm text-on-surface-variant">暂无出入库记录。</div>
        <table v-else class="w-full text-sm">
          <thead class="text-xs text-on-surface-variant">
            <tr class="text-left">
              <th class="pb-2">流水号</th>
              <th class="pb-2">类型</th>
              <th class="pb-2">产品</th>
              <th class="pb-2">仓库</th>
              <th class="pb-2">数量</th>
              <th class="pb-2">批次号</th>
              <th class="pb-2">业务类型</th>
              <th class="pb-2">关联单据</th>
              <th class="pb-2">备注</th>
              <th class="pb-2">时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="transaction in transactions" :key="transaction.id" class="border-t border-outline-variant/20">
              <td class="py-3 font-semibold">{{ transaction.transactionNo }}</td>
              <td class="py-3">{{ formatTransactionType(transaction.transactionType) }}</td>
              <td class="py-3">{{ transaction.product?.name || transaction.product?.sku || '-' }}</td>
              <td class="py-3">{{ transaction.warehouse?.name || transaction.warehouse?.code || '-' }}</td>
              <td class="py-3">{{ formatQuantity(transaction.changeQuantity) }}</td>
              <td class="py-3">{{ transaction.lot || '-' }}</td>
              <td class="py-3">{{ formatRelatedType(transaction.relatedType) }}</td>
              <td class="py-3">{{ transaction.relatedId || '-' }}</td>
              <td class="py-3">{{ transaction.remark || '-' }}</td>
              <td class="py-3">{{ formatDate(transaction.createdAt) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { inventoryApi, productApi } from '../api/services.js';
import { useAuthStore } from '../store/auth.js';
import { useRealtimeStore } from '../store/realtime.js';

const auth = useAuthStore();
const canEditInventory = computed(() => auth.hasPermission('inventory:edit'));
const realtime = useRealtimeStore();

const relatedTypeOptions = [
  { value: 'MANUAL_ADJUST', label: '手工调整' },
  { value: 'PURCHASE_ORDER', label: '采购入库' },
  { value: 'PRODUCTION_PLAN', label: '生产入库' },
  { value: 'SALES_ORDER', label: '销售出库' },
  { value: 'RETURN', label: '退货入库' }
];

const form = reactive({
  productId: '',
  warehouseId: '',
  changeQuantity: null,
  lot: '',
  relatedType: 'MANUAL_ADJUST',
  relatedId: '',
  remark: ''
});

const inventoryFilter = reactive({
  keyword: '',
  productId: '',
  warehouseId: ''
});

const transactionFilter = reactive({
  keyword: '',
  type: '',
  startDate: '',
  endDate: ''
});

const message = ref('');
const error = ref('');
const items = ref([]);
const products = ref([]);
const warehouses = ref([]);
const transactions = ref([]);
const loading = ref(false);
const transactionError = ref('');

const handleStock = async (type) => {
  message.value = '';
  error.value = '';
  try {
    const payload = {
      product: { id: Number(form.productId) },
      warehouse: { id: Number(form.warehouseId) },
      changeQuantity: Number(form.changeQuantity),
      lot: form.lot || null,
      relatedType: form.relatedType || null,
      relatedId: form.relatedId ? Number(form.relatedId) : null,
      remark: form.remark || null
    };
    if (type === 'IN') {
      await inventoryApi.stockIn(payload);
      message.value = '入库成功。';
    } else {
      await inventoryApi.stockOut(payload);
      message.value = '出库成功。';
    }
    resetForm();
    await Promise.all([loadItems(), loadTransactions(), loadWarehouses()]);
  } catch (err) {
    error.value = err?.response?.data?.message || err?.response?.data || '库存变动失败。';
  }
};

const loadItems = async () => {
  loading.value = true;
  try {
    const response = await inventoryApi.list({
      keyword: inventoryFilter.keyword || undefined,
      productId: inventoryFilter.productId || undefined,
      warehouseId: inventoryFilter.warehouseId || undefined
    });
    items.value = response.data || [];
  } catch (err) {
    items.value = [];
  } finally {
    loading.value = false;
  }
};

const loadProducts = async () => {
  try {
    const response = await productApi.list();
    products.value = response.data || [];
  } catch (err) {
    products.value = [];
  }
};

const loadWarehouses = async () => {
  try {
    const response = await inventoryApi.listWarehouses();
    warehouses.value = response.data || [];
  } catch (err) {
    warehouses.value = [];
  }
};

const loadTransactions = async () => {
  transactionError.value = '';
  try {
    const response = await inventoryApi.listTransactions({
      keyword: transactionFilter.keyword || undefined,
      type: transactionFilter.type || undefined,
      startDate: transactionFilter.startDate || undefined,
      endDate: transactionFilter.endDate || undefined
    });
    transactions.value = response.data || [];
  } catch (err) {
    transactions.value = [];
    transactionError.value = err?.response?.data?.message || err?.response?.data || '出入库记录加载失败。';
  }
};

const resetForm = () => {
  form.productId = '';
  form.warehouseId = '';
  form.changeQuantity = null;
  form.lot = '';
  form.relatedType = 'MANUAL_ADJUST';
  form.relatedId = '';
  form.remark = '';
};

const resetInventoryFilter = async () => {
  inventoryFilter.keyword = '';
  inventoryFilter.productId = '';
  inventoryFilter.warehouseId = '';
  await loadItems();
};

const resetTransactionFilter = async () => {
  transactionFilter.keyword = '';
  transactionFilter.type = '';
  transactionFilter.startDate = '';
  transactionFilter.endDate = '';
  await loadTransactions();
};

const formatDate = (value) => (value ? new Date(value).toLocaleString() : '-');
const formatQuantity = (value) => Number(value || 0).toFixed(2);
const formatAvailable = (item) => formatQuantity(Number(item?.quantity || 0) - Number(item?.reservedQuantity || 0));
const formatTransactionType = (value) => (value === 'IN' ? '入库' : value === 'OUT' ? '出库' : value || '-');
const formatRelatedType = (value) => relatedTypeOptions.find((item) => item.value === value)?.label || value || '-';

watch(
  () => realtime.state.lastMessage,
  (message) => {
    if (message?.topic?.startsWith('/topic/orders')) {
      loadItems();
      loadTransactions();
      loadWarehouses();
    }
  }
);

onMounted(async () => {
  await Promise.all([loadProducts(), loadWarehouses()]);
  await Promise.all([loadItems(), loadTransactions()]);
});
</script>

