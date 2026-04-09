<template>
  <div class="space-y-6">
    <section v-if="canCreateProcurement" class="bg-white rounded-lg border border-outline-variant/10 p-5">
      <h3 class="text-sm font-bold tracking-tight">采购订单</h3>
      <form class="mt-4 grid grid-cols-1 md:grid-cols-5 gap-4" @submit.prevent="handleCreate">
        <input v-model="form.poNo" placeholder="采购单号" class="rounded border border-outline-variant/40 px-3 py-2 text-sm" required />
        <input v-model.number="form.supplierId" placeholder="供应商ID" class="rounded border border-outline-variant/40 px-3 py-2 text-sm" required />
        <input v-model.number="form.createdBy" placeholder="创建人ID" class="rounded border border-outline-variant/40 px-3 py-2 text-sm" />
        <input v-model.number="itemForm.productId" placeholder="产品ID" class="rounded border border-outline-variant/40 px-3 py-2 text-sm" required />
        <input v-model.number="itemForm.quantity" placeholder="数量" class="rounded border border-outline-variant/40 px-3 py-2 text-sm" required />
        <input v-model.number="itemForm.unitPrice" placeholder="单价" class="rounded border border-outline-variant/40 px-3 py-2 text-sm" required />
        <button type="button" class="rounded border border-primary text-primary px-3 py-2 text-sm" @click="addItem">添加明细</button>
        <button class="rounded bg-primary text-white px-3 py-2 text-sm font-semibold">提交采购单</button>
      </form>
      <div class="mt-4" v-if="formItems.length">
        <p class="text-xs text-on-surface-variant">当前明细：</p>
        <ul class="text-xs mt-2 space-y-1">
          <li v-for="(item, index) in formItems" :key="index">
            产品 {{ item.product.id }} | 数量 {{ item.quantity }} | 单价 {{ item.unitPrice }}
          </li>
        </ul>
      </div>
      <div v-if="message" class="mt-3 text-xs text-emerald-600">{{ message }}</div>
      <div v-if="error" class="mt-3 text-xs text-error">{{ error }}</div>
    </section>

    <section v-else class="bg-white rounded-lg border border-outline-variant/10 p-5 text-sm text-on-surface-variant">
      当前角色仅支持查看采购申请，无法创建采购单。
    </section>

    <section class="bg-white rounded-lg border border-outline-variant/10">
      <div class="p-5 border-b border-surface-container-low flex items-center justify-between">
        <h3 class="text-sm font-bold tracking-tight">采购申请</h3>
        <button class="text-xs text-primary font-semibold" @click="loadRequests">刷新</button>
      </div>
      <div class="p-5">
        <div v-if="loading" class="text-sm text-on-surface-variant">加载中...</div>
        <div v-else-if="requests.length === 0" class="text-sm text-on-surface-variant">暂无采购申请。</div>
        <table v-else class="w-full text-sm">
          <thead class="text-xs text-on-surface-variant">
            <tr class="text-left">
              <th class="pb-2">申请单号</th>
              <th class="pb-2">状态</th>
              <th class="pb-2">供应商</th>
              <th class="pb-2">备注</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="req in requests" :key="req.id" class="border-t border-outline-variant/20">
              <td class="py-3 font-semibold">{{ req.requestNo }}</td>
              <td class="py-3">{{ req.status }}</td>
              <td class="py-3">{{ req.supplier?.id || '-' }}</td>
              <td class="py-3">{{ req.notes || '-' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { procurementApi } from '../api/services.js';
import { useAuthStore } from '../store/auth.js';

const auth = useAuthStore();
const canCreateProcurement = computed(() => auth.hasPermission('procurement:create'));

const form = reactive({
  poNo: '',
  supplierId: null,
  createdBy: null
});

const itemForm = reactive({
  productId: null,
  quantity: null,
  unitPrice: null
});

const formItems = ref([]);
const message = ref('');
const error = ref('');
const requests = ref([]);
const loading = ref(false);

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
  message.value = '';
  error.value = '';
  try {
    const payload = {
      poNo: form.poNo,
      supplier: { id: Number(form.supplierId) },
      createdBy: form.createdBy ? Number(form.createdBy) : null,
      items: formItems.value
    };
    await procurementApi.createOrder(payload);
    message.value = '采购单已创建。';
    formItems.value = [];
    form.poNo = '';
    form.supplierId = null;
    form.createdBy = null;
  } catch (err) {
    error.value = err?.response?.data?.message || '采购单创建失败。';
  }
};

const loadRequests = async () => {
  loading.value = true;
  try {
    const response = await procurementApi.listRequests();
    requests.value = response.data || [];
  } catch (err) {
    requests.value = [];
  } finally {
    loading.value = false;
  }
};

onMounted(loadRequests);
</script>

