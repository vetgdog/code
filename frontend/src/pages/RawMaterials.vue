<template>
  <div class="space-y-6">
    <section class="panel-surface overflow-hidden">
      <div class="panel-header flex items-center justify-between gap-4">
        <div>
          <h3 class="text-base font-bold text-white tracking-tight">{{ isSupplierRole ? '我的原材料' : '原材料档案' }}</h3>
          <p class="mt-1 text-xs text-cyan-100/70">{{ isSupplierRole ? '仅展示当前供应商可维护的原材料数据。' : '统一维护原材料详情并支持 Excel 模板导入。' }}</p>
        </div>
        <div class="flex items-center gap-3">
          <button class="neo-button-secondary" @click="downloadTemplate">下载模板</button>
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
        <div v-if="rawMaterials.length === 0" class="text-sm text-cyan-100/70">{{ rawMaterialEmptyText }}</div>
        <table v-else class="neo-table">
          <thead>
            <tr>
              <th>SKU</th>
              <th>名称</th>
              <th>分类</th>
              <th>规格</th>
              <th>单位</th>
              <th>单价</th>
              <th>供应商</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="material in rawMaterials" :key="material.id" class="cursor-pointer" @click="selectRawMaterial(material.id)">
              <td class="font-semibold">{{ material.sku }}</td>
              <td>{{ material.name }}</td>
              <td>{{ material.materialCategory || '-' }}</td>
              <td>{{ material.specification || '-' }}</td>
              <td>{{ material.unit || '-' }}</td>
              <td>¥{{ formatAmount(material.unitPrice) }}</td>
              <td>{{ material.preferredSupplier || '-' }}</td>
            </tr>
          </tbody>
        </table>

        <div v-if="selectedMaterial" class="mt-5 rounded-3xl border border-cyan-400/20 bg-slate-950/40 p-5 text-sm text-cyan-50">
          <h4 class="text-base font-bold">原材料详情</h4>
          <div class="mt-4 grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-3">
            <div>SKU：{{ selectedMaterial.sku || '-' }}</div>
            <div>名称：{{ selectedMaterial.name || '-' }}</div>
            <div>分类：{{ selectedMaterial.materialCategory || '-' }}</div>
            <div>规格：{{ selectedMaterial.specification || '-' }}</div>
            <div>单位：{{ selectedMaterial.unit || '-' }}</div>
            <div>单价：¥{{ formatAmount(selectedMaterial.unitPrice) }}</div>
            <div>安全库存：{{ formatNumber(selectedMaterial.safetyStock) }}</div>
            <div>供货周期：{{ selectedMaterial.leadTimeDays ?? 0 }} 天</div>
            <div>原产地：{{ selectedMaterial.origin || '-' }}</div>
            <div class="md:col-span-2 xl:col-span-3">描述：{{ selectedMaterial.description || '-' }}</div>
          </div>
        </div>
      </div>
    </section>

    <section v-if="canManageRawMaterials" class="panel-surface p-6">
      <h3 class="text-base font-bold text-white">新增原材料</h3>
      <form class="mt-4 grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4" @submit.prevent="handleCreateRawMaterial">
        <div class="rounded-2xl border border-cyan-400/20 bg-slate-950/40 px-4 py-3 text-sm text-cyan-100/70">原材料编号将由系统自动生成</div>
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
      <div v-if="createError" class="mt-3 text-sm text-rose-300">{{ createError }}</div>
    </section>

    <section v-if="canManageRawMaterials" class="panel-surface p-6">
      <h3 class="text-base font-bold text-white">Excel 批量导入原材料</h3>
      <div class="mt-3 text-sm text-cyan-100/70">建议先下载模板后填写，再上传 `.xlsx` 文件。</div>
      <div class="mt-4 flex flex-col md:flex-row gap-4 items-start">
        <input type="file" accept=".xlsx" class="block text-sm text-cyan-100/80" @change="handleFileChange" />
        <button class="neo-button-primary" :disabled="!importFile || importing" @click="handleImportRawMaterials">{{ importing ? '导入中...' : '开始导入' }}</button>
      </div>
      <div v-if="importMessage" class="mt-3 text-sm text-emerald-300">{{ importMessage }}</div>
      <div v-if="importError" class="mt-3 text-sm text-rose-300">{{ importError }}</div>
      <ul v-if="importErrors.length" class="mt-3 space-y-1 text-sm text-rose-300">
        <li v-for="item in importErrors" :key="item">{{ item }}</li>
      </ul>
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
const rawMaterialEmptyText = computed(() => (isSupplierRole.value ? '当前没有原材料。' : '当前没有原材料记录。'));
const showRawMaterialError = computed(() => Boolean(rawMaterialError.value) && !isSupplierRole.value);

const filter = reactive({ keyword: '', startDate: '', endDate: '' });
const form = reactive({ name: '', materialCategory: '', specification: '', unit: '', unitPrice: null, preferredSupplier: '', origin: '', safetyStock: null, leadTimeDays: null, description: '' });
const rawMaterials = ref([]);
const selectedMaterial = ref(null);
const rawMaterialError = ref('');
const message = ref('');
const createError = ref('');
const importFile = ref(null);
const importing = ref(false);
const importMessage = ref('');
const importError = ref('');
const importErrors = ref([]);

const loadRawMaterials = async () => {
  rawMaterialError.value = '';
  try {
    const response = await procurementApi.listRawMaterials({
      keyword: filter.keyword || undefined,
      startDate: filter.startDate || undefined,
      endDate: filter.endDate || undefined
    });
    rawMaterials.value = response.data || [];
    if (selectedMaterial.value) {
      const latest = rawMaterials.value.find((item) => item.id === selectedMaterial.value.id);
      selectedMaterial.value = latest || null;
    }
    if (!selectedMaterial.value && rawMaterials.value.length) {
      await selectRawMaterial(rawMaterials.value[0].id);
    }
  } catch (error) {
    rawMaterials.value = [];
    rawMaterialError.value = error?.response?.data?.message || error?.response?.data || '原材料列表加载失败。';
  }
};

const selectRawMaterial = async (id) => {
  if (!id) {
    selectedMaterial.value = null;
    return;
  }
  try {
    const response = await procurementApi.getRawMaterial(id);
    selectedMaterial.value = response.data || null;
  } catch (error) {
    selectedMaterial.value = null;
  }
};

const handleCreateRawMaterial = async () => {
  message.value = '';
  createError.value = '';
  try {
    const response = await procurementApi.createRawMaterial({
      name: form.name,
      materialCategory: form.materialCategory || null,
      specification: form.specification || null,
      unit: form.unit || null,
      unitPrice: form.unitPrice == null ? 0 : Number(form.unitPrice),
      preferredSupplier: form.preferredSupplier || null,
      origin: form.origin || null,
      safetyStock: form.safetyStock == null ? 0 : Number(form.safetyStock),
      leadTimeDays: form.leadTimeDays == null ? 0 : Number(form.leadTimeDays),
      description: form.description || null
    });
    message.value = '原材料保存成功。';
    resetForm();
    await loadRawMaterials();
    if (response?.data?.id) {
      await selectRawMaterial(response.data.id);
    }
  } catch (error) {
    createError.value = error?.response?.data?.message || error?.response?.data || '原材料保存失败。';
  }
};

const handleFileChange = (event) => {
  importFile.value = event?.target?.files?.[0] || null;
};

const handleImportRawMaterials = async () => {
  if (!importFile.value) {
    importError.value = '请先选择 Excel 文件。';
    return;
  }
  importing.value = true;
  importMessage.value = '';
  importError.value = '';
  importErrors.value = [];
  try {
    const formData = new FormData();
    formData.append('file', importFile.value);
    const response = await procurementApi.importRawMaterials(formData);
    const result = response.data || {};
    importMessage.value = `导入完成：新增 ${result.createdCount || 0} 条，更新 ${result.updatedCount || 0} 条。`;
    importErrors.value = result.errors || [];
    await loadRawMaterials();
  } catch (error) {
    importError.value = error?.response?.data?.message || error?.response?.data || 'Excel 导入失败。';
  } finally {
    importing.value = false;
  }
};

const downloadTemplate = async () => {
  importMessage.value = '';
  importError.value = '';
  try {
    const response = await procurementApi.downloadRawMaterialTemplate();
    const blob = new Blob([response.data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = 'raw-material-template.xlsx';
    document.body.appendChild(anchor);
    anchor.click();
    document.body.removeChild(anchor);
    URL.revokeObjectURL(url);
  } catch (error) {
    importError.value = '模板下载失败。';
  }
};

const resetForm = () => {
  form.name = '';
  form.materialCategory = '';
  form.specification = '';
  form.unit = '';
  form.unitPrice = null;
  form.preferredSupplier = '';
  form.origin = '';
  form.safetyStock = null;
  form.leadTimeDays = null;
  form.description = '';
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
      if (selectedMaterial.value?.id) {
        selectRawMaterial(selectedMaterial.value.id);
      }
    }
  }
);

onMounted(loadRawMaterials);
</script>

