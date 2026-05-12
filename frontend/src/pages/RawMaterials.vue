<template>
  <div class="space-y-6">
    <section class="panel-surface overflow-hidden">
      <div class="panel-header flex items-center justify-between gap-4">
        <div>
          <h3 class="text-base font-bold text-white tracking-tight">{{ isSupplierRole ? '我的原材料' : '原材料档案' }}</h3>
          <p class="mt-1 text-xs text-slate-400">{{ isSupplierRole ? '仅展示当前供应商可维护的原材料资料，并支持直接增删改查。' : '统一维护原材料基础资料，支持按条件查询与增删改查。' }}</p>
        </div>
        <div class="flex items-center gap-3">
          <button class="neo-button-secondary" @click="loadRawMaterials">刷新</button>
        </div>
      </div>
      <div class="p-5">
        <div class="mb-4 grid grid-cols-1 md:grid-cols-5 gap-3">
          <input v-model="filter.keyword" placeholder="搜索 SKU / 名称 / 分类 / 规格 / 供应商" class="neo-input md:col-span-2" @keyup.enter="loadRawMaterials" />
          <input v-model="filter.startDate" type="date" class="neo-input" />
          <input v-model="filter.endDate" type="date" class="neo-input" />
          <div class="flex items-center gap-3">
            <button class="neo-button-primary" @click="loadRawMaterials">查询</button>
            <button class="neo-button-secondary" @click="resetFilter">重置</button>
          </div>
        </div>
        <div v-if="showRawMaterialError" class="mb-3 text-sm text-rose-300">{{ rawMaterialError }}</div>
        <div v-if="rawMaterials.length === 0" class="text-sm text-slate-400">{{ rawMaterialEmptyText }}</div>
        <table v-else class="neo-table">
          <thead>
            <tr>
              <th>SKU</th>
              <th>名称</th>
              <th>分类</th>
              <th>规格</th>
              <th>单位</th>
              <th>单价</th>
              <th>安全库存</th>
              <th>供应商</th>
              <th v-if="canManageRawMaterials || canDeleteRawMaterials">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="material in rawMaterials" :key="material.id">
              <td class="font-semibold">{{ material.sku }}</td>
              <td>{{ material.name }}</td>
              <td>{{ material.materialCategory || '-' }}</td>
              <td>{{ material.specification || '-' }}</td>
              <td>{{ material.unit || '-' }}</td>
              <td>¥{{ formatAmount(material.unitPrice) }}</td>
              <td>{{ formatNumber(material.safetyStock) }}</td>
              <td>{{ material.preferredSupplier || '-' }}</td>
              <td v-if="canManageRawMaterials || canDeleteRawMaterials">
                <button v-if="canManageRawMaterials" type="button" class="text-cyan-300 text-xs mr-3" @click="prepareEditMaterial(material)">编辑</button>
                <button v-if="canDeleteRawMaterials" type="button" class="text-rose-300 text-xs" @click="handleDeleteRawMaterial(material)">删除</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <section v-if="canManageRawMaterials" class="panel-surface p-6">
      <h3 class="text-base font-bold text-white">新增原材料</h3>
      <form class="mt-4 grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4" @submit.prevent="handleCreateRawMaterial">
        <div class="rounded-2xl border border-cyan-400/20 bg-white px-4 py-3 text-sm text-slate-400">原材料编号将由系统自动生成</div>
        <input v-model="form.name" placeholder="原材料名称" class="neo-input" required />
        <input v-model="form.materialCategory" placeholder="原材料分类" class="neo-input" />
        <input v-model="form.specification" placeholder="规格型号" class="neo-input" />
        <input v-model="form.unit" placeholder="单位" class="neo-input" />
        <input v-model.number="form.unitPrice" type="number" min="0" step="0.01" placeholder="默认单价" class="neo-input" />
        <input v-model="form.preferredSupplier" :readonly="isSupplierRole" placeholder="首选供应商" class="neo-input" :class="isSupplierRole ? 'opacity-70' : ''" />
        <input v-model="form.origin" placeholder="原产地" class="neo-input" />
        <input v-model.number="form.safetyStock" type="number" min="0" step="0.01" placeholder="安全库存" class="neo-input" />
        <input v-model.number="form.leadTimeDays" type="number" min="0" step="1" placeholder="供货周期（天）" class="neo-input" />
        <textarea v-model="form.description" placeholder="描述说明" class="neo-input md:col-span-2 xl:col-span-3 min-h-28"></textarea>
        <div class="md:col-span-2 xl:col-span-3 flex items-center gap-3">
          <button class="neo-button-primary">保存原材料</button>
          <button type="button" class="neo-button-secondary" @click="resetForm">清空</button>
        </div>
      </form>
      <div v-if="message" class="mt-3 text-sm text-emerald-300">{{ message }}</div>
      <div v-if="submitError" class="mt-3 text-sm text-rose-300">{{ submitError }}</div>
    </section>

    <section v-if="canManageRawMaterials && editingMaterialId" class="panel-surface p-6">
      <div class="flex items-center justify-between gap-4">
        <div>
          <h3 class="text-base font-bold text-white">编辑原材料</h3>
          <p class="mt-1 text-xs text-slate-400">当前编辑：{{ currentEditingMaterial?.name || '-' }}（{{ currentEditingMaterial?.sku || '-' }}）</p>
        </div>
        <button type="button" class="neo-button-secondary" @click="cancelEdit">取消编辑</button>
      </div>
      <form class="mt-4 grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4" @submit.prevent="handleUpdateRawMaterial">
        <div class="rounded-2xl border border-cyan-400/20 bg-white px-4 py-3 text-sm text-slate-400">原材料编号：{{ currentEditingMaterial?.sku || '-' }}</div>
        <input v-model="editForm.name" placeholder="原材料名称" class="neo-input" required />
        <input v-model="editForm.materialCategory" placeholder="原材料分类" class="neo-input" />
        <input v-model="editForm.specification" placeholder="规格型号" class="neo-input" />
        <input v-model="editForm.unit" placeholder="单位" class="neo-input" />
        <input v-model.number="editForm.unitPrice" type="number" min="0" step="0.01" placeholder="默认单价" class="neo-input" />
        <input v-model="editForm.preferredSupplier" :readonly="isSupplierRole" placeholder="首选供应商" class="neo-input" :class="isSupplierRole ? 'opacity-70' : ''" />
        <input v-model="editForm.origin" placeholder="原产地" class="neo-input" />
        <input v-model.number="editForm.safetyStock" type="number" min="0" step="0.01" placeholder="安全库存" class="neo-input" />
        <input v-model.number="editForm.leadTimeDays" type="number" min="0" step="1" placeholder="供货周期（天）" class="neo-input" />
        <textarea v-model="editForm.description" placeholder="描述说明" class="neo-input md:col-span-2 xl:col-span-3 min-h-28"></textarea>
        <div class="md:col-span-2 xl:col-span-3 flex items-center gap-3">
          <button class="neo-button-primary">保存修改</button>
          <button type="button" class="neo-button-secondary" @click="cancelEdit">取消</button>
        </div>
      </form>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { procurementApi } from '../api/services.js';
import { useAuthStore } from '../store/auth.js';
import { useRealtimeStore } from '../store/realtime.js';

const auth = useAuthStore();
const realtime = useRealtimeStore();
const isSupplierRole = computed(() => auth.state.role === 'ROLE_SUPPLIER');
const canManageRawMaterials = computed(() => auth.hasPermission('procurement:raw-material-manage'));
const canDeleteRawMaterials = computed(() => ['ROLE_ADMIN', 'ROLE_SUPPLIER', 'ROLE_WAREHOUSE_MANAGER'].includes(auth.state.role));
const rawMaterialEmptyText = computed(() => (isSupplierRole.value ? '当前没有原材料。' : '当前没有原材料记录。'));
const showRawMaterialError = computed(() => Boolean(rawMaterialError.value) && !isSupplierRole.value);
const currentEditingMaterial = computed(() => rawMaterials.value.find((item) => item.id === editingMaterialId.value) || null);

const filter = reactive({ keyword: '', startDate: '', endDate: '' });
const form = reactive({ name: '', materialCategory: '', specification: '', unit: '', unitPrice: null, preferredSupplier: '', origin: '', safetyStock: null, leadTimeDays: null, description: '' });
const editForm = reactive({ name: '', materialCategory: '', specification: '', unit: '', unitPrice: null, preferredSupplier: '', origin: '', safetyStock: null, leadTimeDays: null, description: '' });
const rawMaterials = ref([]);
const editingMaterialId = ref(null);
const rawMaterialError = ref('');
const message = ref('');
const submitError = ref('');

const loadRawMaterials = async () => {
  rawMaterialError.value = '';
  try {
    const response = await procurementApi.listRawMaterials({
      keyword: filter.keyword || undefined,
      startDate: filter.startDate || undefined,
      endDate: filter.endDate || undefined
    });
    rawMaterials.value = response.data || [];
    if (editingMaterialId.value && !rawMaterials.value.some((item) => item.id === editingMaterialId.value)) {
      cancelEdit();
    }
  } catch (error) {
    rawMaterials.value = [];
    rawMaterialError.value = error?.response?.data?.message || error?.response?.data || '原材料列表加载失败。';
  }
};

const assignForm = (target, material = {}) => {
  target.name = material.name || '';
  target.materialCategory = material.materialCategory || '';
  target.specification = material.specification || '';
  target.unit = material.unit || '';
  target.unitPrice = material.unitPrice == null ? null : Number(material.unitPrice);
  target.preferredSupplier = material.preferredSupplier || '';
  target.origin = material.origin || '';
  target.safetyStock = material.safetyStock == null ? null : Number(material.safetyStock);
  target.leadTimeDays = material.leadTimeDays == null ? null : Number(material.leadTimeDays);
  target.description = material.description || '';
};

const mapFormToPayload = (source) => ({
  name: source.name,
  materialCategory: source.materialCategory || null,
  specification: source.specification || null,
  unit: source.unit || null,
  unitPrice: source.unitPrice == null ? 0 : Number(source.unitPrice),
  preferredSupplier: source.preferredSupplier || null,
  origin: source.origin || null,
  safetyStock: source.safetyStock == null ? 0 : Number(source.safetyStock),
  leadTimeDays: source.leadTimeDays == null ? 0 : Number(source.leadTimeDays),
  description: source.description || null
});

const handleCreateRawMaterial = async () => {
  message.value = '';
  submitError.value = '';
  try {
    await procurementApi.createRawMaterial(mapFormToPayload(form));
    message.value = '原材料新增成功。';
    resetForm();
    await loadRawMaterials();
  } catch (error) {
    submitError.value = error?.response?.data?.message || error?.response?.data || '原材料新增失败。';
  }
};

const prepareEditMaterial = (material) => {
  editingMaterialId.value = material.id;
  assignForm(editForm, material);
};

const handleUpdateRawMaterial = async () => {
  if (!editingMaterialId.value) {
    return;
  }
  message.value = '';
  submitError.value = '';
  try {
    await procurementApi.updateRawMaterial(editingMaterialId.value, mapFormToPayload(editForm));
    message.value = '原材料更新成功。';
    cancelEdit();
    await loadRawMaterials();
  } catch (error) {
    submitError.value = error?.response?.data?.message || error?.response?.data || '原材料更新失败。';
  }
};

const handleDeleteRawMaterial = async (material) => {
  if (!material?.id) {
    return;
  }
  if (typeof window !== 'undefined' && !window.confirm(`确认删除原材料“${material.name || material.sku || material.id}”吗？`)) {
    return;
  }
  message.value = '';
  submitError.value = '';
  try {
    await procurementApi.deleteRawMaterial(material.id);
    message.value = '原材料删除成功。';
    if (editingMaterialId.value === material.id) {
      cancelEdit();
    }
    await loadRawMaterials();
  } catch (error) {
    submitError.value = error?.response?.data?.message || error?.response?.data || '原材料删除失败。';
  }
};

const resetForm = () => {
  assignForm(form);
};

const cancelEdit = () => {
  editingMaterialId.value = null;
  assignForm(editForm);
};

const resetFilter = async () => {
  filter.keyword = '';
  filter.startDate = '';
  filter.endDate = '';
  await loadRawMaterials();
};

const formatAmount = (value) => Number(value || 0).toFixed(2);
const formatNumber = (value) => Number(value || 0).toFixed(2);

watch(
  () => realtime.state.lastMessage,
  (message) => {
    if (message?.topic?.startsWith('/topic/procurement')) {
      loadRawMaterials();
    }
  }
);

onMounted(loadRawMaterials);
</script>

