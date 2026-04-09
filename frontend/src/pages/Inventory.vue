<template>
  <div class="space-y-6">
    <section v-if="canEditInventory" class="bg-white rounded-lg border border-outline-variant/10 p-5">
      <h3 class="text-sm font-bold tracking-tight">库存变动</h3>
      <form class="mt-4 grid grid-cols-1 md:grid-cols-5 gap-4" @submit.prevent>
        <input v-model.number="form.productId" placeholder="产品ID" class="rounded border border-outline-variant/40 px-3 py-2 text-sm" required />
        <input v-model.number="form.warehouseId" placeholder="仓库ID" class="rounded border border-outline-variant/40 px-3 py-2 text-sm" required />
        <input v-model.number="form.changeQuantity" placeholder="变动数量" class="rounded border border-outline-variant/40 px-3 py-2 text-sm" required />
        <input v-model="form.relatedType" placeholder="关联类型" class="rounded border border-outline-variant/40 px-3 py-2 text-sm" />
        <input v-model.number="form.relatedId" placeholder="关联ID" class="rounded border border-outline-variant/40 px-3 py-2 text-sm" />
        <input v-model.number="form.createdBy" placeholder="操作人ID" class="rounded border border-outline-variant/40 px-3 py-2 text-sm" />
        <button class="rounded bg-primary text-white px-3 py-2 text-sm font-semibold" @click="handleStock('IN')">入库</button>
        <button class="rounded border border-primary text-primary px-3 py-2 text-sm" @click="handleStock('OUT')">出库</button>
      </form>
      <div v-if="message" class="mt-3 text-xs text-emerald-600">{{ message }}</div>
      <div v-if="error" class="mt-3 text-xs text-error">{{ error }}</div>
    </section>

    <section v-else class="bg-white rounded-lg border border-outline-variant/10 p-5 text-sm text-on-surface-variant">
      当前角色仅支持查看库存，无法执行入库/出库操作。
    </section>

    <section class="bg-white rounded-lg border border-outline-variant/10">
      <div class="p-5 border-b border-surface-container-low flex items-center justify-between">
        <h3 class="text-sm font-bold tracking-tight">库存列表</h3>
        <button class="text-xs text-primary font-semibold" @click="loadItems">刷新</button>
      </div>
      <div class="p-5">
        <div v-if="loading" class="text-sm text-on-surface-variant">加载中...</div>
        <div v-else-if="items.length === 0" class="text-sm text-on-surface-variant">暂无库存。</div>
        <table v-else class="w-full text-sm">
          <thead class="text-xs text-on-surface-variant">
            <tr class="text-left">
              <th class="pb-2">产品</th>
              <th class="pb-2">仓库</th>
              <th class="pb-2">数量</th>
              <th class="pb-2">更新时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in items" :key="item.id" class="border-t border-outline-variant/20">
              <td class="py-3 font-semibold">{{ item.product?.id || '-' }}</td>
              <td class="py-3">{{ item.warehouse?.id || '-' }}</td>
              <td class="py-3">{{ item.quantity }}</td>
              <td class="py-3">{{ formatDate(item.updatedAt) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { inventoryApi } from '../api/services.js';
import { useAuthStore } from '../store/auth.js';

const auth = useAuthStore();
const canEditInventory = computed(() => auth.hasPermission('inventory:edit'));

const form = reactive({
  productId: null,
  warehouseId: null,
  changeQuantity: null,
  relatedType: '',
  relatedId: null,
  createdBy: null
});

const message = ref('');
const error = ref('');
const items = ref([]);
const loading = ref(false);

const handleStock = async (type) => {
  message.value = '';
  error.value = '';
  try {
    const payload = {
      product: { id: Number(form.productId) },
      warehouse: { id: Number(form.warehouseId) },
      changeQuantity: Number(form.changeQuantity),
      relatedType: form.relatedType || null,
      relatedId: form.relatedId ? Number(form.relatedId) : null,
      createdBy: form.createdBy ? Number(form.createdBy) : null
    };
    if (type === 'IN') {
      await inventoryApi.stockIn(payload);
      message.value = '入库成功。';
    } else {
      await inventoryApi.stockOut(payload);
      message.value = '出库成功。';
    }
    await loadItems();
  } catch (err) {
    error.value = err?.response?.data?.message || '库存变动失败。';
  }
};

const loadItems = async () => {
  loading.value = true;
  try {
    const response = await inventoryApi.list();
    items.value = response.data || [];
  } catch (err) {
    items.value = [];
  } finally {
    loading.value = false;
  }
};

const formatDate = (value) => (value ? new Date(value).toLocaleString() : '-');

onMounted(loadItems);
</script>

