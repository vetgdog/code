<template>
  <div class="space-y-6">
    <section v-if="canCreateProduction" class="bg-white rounded-lg border border-outline-variant/10 p-5">
      <h3 class="text-sm font-bold tracking-tight">创建生产任务</h3>
      <form class="mt-4 grid grid-cols-1 md:grid-cols-4 gap-4" @submit.prevent="handleCreate">
        <input v-model="taskForm.taskNo" placeholder="任务编号" class="rounded border border-outline-variant/40 px-3 py-2 text-sm" required />
        <input v-model.number="taskForm.productId" placeholder="产品ID" class="rounded border border-outline-variant/40 px-3 py-2 text-sm" required />
        <input v-model.number="taskForm.planId" placeholder="计划ID" class="rounded border border-outline-variant/40 px-3 py-2 text-sm" />
        <input v-model.number="taskForm.assignedTo" placeholder="负责人ID" class="rounded border border-outline-variant/40 px-3 py-2 text-sm" />
        <input v-model.number="taskForm.scheduledQuantity" placeholder="计划数量" class="rounded border border-outline-variant/40 px-3 py-2 text-sm" />
        <button class="rounded bg-primary text-white px-3 py-2 text-sm font-semibold">创建任务</button>
      </form>
      <div v-if="createMessage" class="mt-3 text-xs text-emerald-600">{{ createMessage }}</div>
      <div v-if="createError" class="mt-3 text-xs text-error">{{ createError }}</div>
    </section>

    <section v-else class="bg-white rounded-lg border border-outline-variant/10 p-5 text-sm text-on-surface-variant">
      当前角色仅支持查看生产任务，无法创建任务。
    </section>

    <section class="bg-white rounded-lg border border-outline-variant/10 p-5">
      <div class="flex flex-col md:flex-row md:items-center md:justify-between gap-4">
        <div>
          <h3 class="text-sm font-bold tracking-tight">任务列表</h3>
          <p class="text-xs text-on-surface-variant">按负责人ID查询</p>
        </div>
        <div class="flex items-center gap-3">
          <input v-model.number="filterUserId" placeholder="负责人ID" class="rounded border border-outline-variant/40 px-3 py-2 text-sm" />
          <button class="text-xs text-primary font-semibold" @click="loadTasks">查询</button>
        </div>
      </div>
      <div class="mt-4">
        <div v-if="loading" class="text-sm text-on-surface-variant">加载中...</div>
        <div v-else-if="tasks.length === 0" class="text-sm text-on-surface-variant">暂无任务。</div>
        <table v-else class="w-full text-sm">
          <thead class="text-xs text-on-surface-variant">
            <tr class="text-left">
              <th class="pb-2">任务编号</th>
              <th class="pb-2">产品</th>
              <th class="pb-2">状态</th>
              <th class="pb-2">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="task in tasks" :key="task.id" class="border-t border-outline-variant/20">
              <td class="py-3 font-semibold">{{ task.taskNo }}</td>
              <td class="py-3">{{ task.product?.id || '-' }}</td>
              <td class="py-3">{{ task.status }}</td>
              <td class="py-3">
                <select v-if="canUpdateProduction" v-model="task.status" class="border border-outline-variant/30 rounded px-2 py-1 text-xs" @change="updateStatus(task)">
                  <option value="PENDING">PENDING</option>
                  <option value="RUNNING">RUNNING</option>
                  <option value="DONE">DONE</option>
                </select>
                <span v-else class="text-xs text-on-surface-variant">只读</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-if="statusMessage" class="mt-3 text-xs text-emerald-600">{{ statusMessage }}</div>
      <div v-if="statusError" class="mt-3 text-xs text-error">{{ statusError }}</div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { productionApi } from '../api/services.js';
import { useRealtimeStore } from '../store/realtime.js';
import { useAuthStore } from '../store/auth.js';

const auth = useAuthStore();
const canCreateProduction = computed(() => auth.hasPermission('production:create'));
const canUpdateProduction = computed(() => auth.hasPermission('production:update'));

const taskForm = reactive({
  taskNo: '',
  productId: null,
  planId: null,
  assignedTo: null,
  scheduledQuantity: null
});

const createMessage = ref('');
const createError = ref('');
const statusMessage = ref('');
const statusError = ref('');

const tasks = ref([]);
const loading = ref(false);
const filterUserId = ref('');
const realtime = useRealtimeStore();

const handleCreate = async () => {
  createMessage.value = '';
  createError.value = '';
  try {
    const payload = {
      taskNo: taskForm.taskNo,
      product: { id: Number(taskForm.productId) },
      productionPlan: taskForm.planId ? { id: Number(taskForm.planId) } : null,
      assignedTo: taskForm.assignedTo ? Number(taskForm.assignedTo) : null,
      scheduledQuantity: taskForm.scheduledQuantity ? Number(taskForm.scheduledQuantity) : null
    };
    await productionApi.createTask(payload);
    createMessage.value = '任务创建成功。';
    taskForm.taskNo = '';
    taskForm.productId = null;
    taskForm.planId = null;
    taskForm.assignedTo = null;
    taskForm.scheduledQuantity = null;
  } catch (error) {
    createError.value = error?.response?.data?.message || '任务创建失败。';
  }
};

const loadTasks = async () => {
  statusMessage.value = '';
  statusError.value = '';
  if (!filterUserId.value) {
    tasks.value = [];
    return;
  }
  loading.value = true;
  try {
    const response = await productionApi.listByUser(filterUserId.value);
    tasks.value = response.data || [];
  } catch (error) {
    tasks.value = [];
  } finally {
    loading.value = false;
  }
};

const updateStatus = async (task) => {
  statusMessage.value = '';
  statusError.value = '';
  try {
    await productionApi.updateStatus(task.id, task.status);
    statusMessage.value = `任务 ${task.taskNo} 状态已更新。`;
  } catch (error) {
    statusError.value = error?.response?.data?.message || '更新失败。';
  }
};

onMounted(() => {
  if (filterUserId.value) {
    loadTasks();
  }
});

watch(
  () => realtime.state.lastMessage,
  (message) => {
    if (message?.topic === '/topic/production' && filterUserId.value) {
      loadTasks();
    }
  }
);
</script>

