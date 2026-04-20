<template>
  <div class="space-y-6">
    <section class="panel-surface p-6">
      <div class="flex items-center justify-between gap-4">
        <div>
          <h3 class="text-lg font-bold text-white tracking-tight">产品档案管理</h3>
          <p class="mt-2 text-sm text-cyan-100/80">维护客户可下单的成品资料，前台展示与销售侧产品档案保持同步。</p>
        </div>
      </div>
      <form class="mt-6 grid grid-cols-1 md:grid-cols-5 gap-3" @submit.prevent="handleCreateProduct">
        <input v-model="productForm.sku" placeholder="SKU" class="neo-input" required />
        <input v-model="productForm.name" placeholder="产品名称" class="neo-input" required />
        <input v-model="productForm.unit" placeholder="单位(可选)" class="neo-input" />
        <input v-model.number="productForm.unitPrice" type="number" min="0" step="0.01" placeholder="默认单价" class="neo-input" />
        <button class="neo-button-primary">保存产品</button>
      </form>
      <div v-if="productMessage" class="mt-3 text-sm text-emerald-300">{{ productMessage }}</div>
      <div v-if="productError" class="mt-3 text-sm text-rose-300">{{ productError }}</div>
    </section>

    <section class="panel-surface overflow-hidden">
      <div class="panel-header flex items-center justify-between">
        <div>
          <h3 class="text-base font-bold text-white">产品列表</h3>
          <p class="mt-1 text-xs text-cyan-100/70">共 {{ products.length }} 个产品</p>
        </div>
        <button class="neo-button-secondary" @click="loadProducts">刷新</button>
      </div>
      <div class="p-5">
        <div v-if="products.length === 0" class="text-sm text-cyan-100/70">暂无产品。</div>
        <table v-else class="neo-table">
          <thead>
            <tr>
              <th>SKU</th>
              <th>名称</th>
              <th>单位</th>
              <th>单价</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="product in products" :key="product.id">
              <td class="font-semibold">{{ product.sku }}</td>
              <td>{{ product.name }}</td>
              <td>{{ product.unit || '-' }}</td>
              <td>¥{{ formatAmount(product.unitPrice) }}</td>
              <td>
                <button class="text-cyan-300 text-xs mr-3" @click="prepareEditProduct(product)">编辑</button>
                <button class="text-rose-300 text-xs" @click="handleDeleteProduct(product.id)">删除</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <section v-if="editingProductId" class="panel-surface p-6">
      <h3 class="text-base font-bold text-white">编辑产品</h3>
      <form class="mt-4 grid grid-cols-1 md:grid-cols-4 gap-3" @submit.prevent="handleUpdateProduct">
        <input v-model="editProductForm.sku" placeholder="SKU" class="neo-input" required />
        <input v-model="editProductForm.name" placeholder="产品名称" class="neo-input" required />
        <input v-model="editProductForm.unit" placeholder="单位" class="neo-input" />
        <input v-model.number="editProductForm.unitPrice" type="number" min="0" step="0.01" placeholder="单价" class="neo-input" />
        <div class="md:col-span-4 flex items-center gap-3">
          <button class="neo-button-primary">保存修改</button>
          <button type="button" class="neo-button-secondary" @click="cancelEditProduct">取消</button>
        </div>
      </form>
    </section>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { productApi } from '../api/services.js';

const products = ref([]);
const productMessage = ref('');
const productError = ref('');
const editingProductId = ref(null);
const productForm = reactive({ sku: '', name: '', unit: '', unitPrice: null });
const editProductForm = reactive({ sku: '', name: '', unit: '', unitPrice: null });

const loadProducts = async () => {
  try {
    const response = await productApi.list({ productType: 'FINISHED_GOOD' });
    products.value = response.data || [];
  } catch (error) {
    products.value = [];
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

const formatAmount = (value) => Number(value || 0).toFixed(2);

onMounted(loadProducts);
</script>

