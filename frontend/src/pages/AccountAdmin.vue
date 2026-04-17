<template>
  <div class="space-y-6">
    <section class="bg-white rounded-lg border border-outline-variant/10 p-5">
      <div class="flex items-center justify-between gap-4">
        <div>
          <h3 class="text-sm font-bold tracking-tight">创建内部人员账号</h3>
          <p class="mt-1 text-xs text-on-surface-variant">系统管理员可统一创建和维护内部岗位账号，客户与供应商账号不在此页面管理。</p>
        </div>
      </div>
      <form class="mt-4 grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4" @submit.prevent="handleCreateUser">
        <input v-model="createForm.username" placeholder="用户名" class="rounded border border-outline-variant/40 px-3 py-2 text-sm" required />
        <input v-model="createForm.email" placeholder="邮箱" class="rounded border border-outline-variant/40 px-3 py-2 text-sm" required />
        <input v-model="createForm.fullName" placeholder="姓名" class="rounded border border-outline-variant/40 px-3 py-2 text-sm" required />
        <input v-model="createForm.phone" placeholder="手机号" class="rounded border border-outline-variant/40 px-3 py-2 text-sm" required />
        <input v-model="createForm.password" placeholder="初始密码" type="password" class="rounded border border-outline-variant/40 px-3 py-2 text-sm" required />
        <select v-model="createForm.role" class="rounded border border-outline-variant/40 px-3 py-2 text-sm" required>
          <option value="">请选择岗位</option>
          <option v-for="role in roleOptions" :key="role.role" :value="role.role">{{ role.label }}</option>
        </select>
        <div class="md:col-span-2 xl:col-span-3 flex items-center gap-3">
          <button class="rounded bg-primary text-white px-4 py-2 text-sm font-semibold" :disabled="creating">{{ creating ? '创建中...' : '创建账号' }}</button>
          <button type="button" class="rounded border border-outline-variant/40 px-4 py-2 text-sm" @click="resetCreateForm">清空</button>
        </div>
      </form>
      <div v-if="createMessage" class="mt-3 text-xs text-emerald-600">{{ createMessage }}</div>
      <div v-if="createError" class="mt-3 text-xs text-error">{{ createError }}</div>
    </section>

    <section class="bg-white rounded-lg border border-outline-variant/10">
      <div class="p-5 border-b border-surface-container-low flex items-center justify-between gap-4">
        <div>
          <h3 class="text-sm font-bold tracking-tight">内部人员账号列表</h3>
          <p class="mt-1 text-xs text-on-surface-variant">支持按姓名、邮箱、用户名、岗位和启用状态筛选，并可编辑岗位与基础信息。</p>
        </div>
        <button class="text-xs text-primary font-semibold" @click="loadUsers">刷新</button>
      </div>
      <div class="p-5">
        <div class="mb-4 grid grid-cols-1 md:grid-cols-4 gap-3">
          <input v-model="filters.keyword" placeholder="搜索姓名 / 邮箱 / 用户名 / 电话" class="rounded border border-outline-variant/40 px-3 py-2 text-sm md:col-span-2" @keyup.enter="loadUsers" />
          <select v-model="filters.role" class="rounded border border-outline-variant/40 px-3 py-2 text-sm">
            <option value="">全部岗位</option>
            <option v-for="role in roleOptions" :key="`filter-${role.role}`" :value="role.role">{{ role.label }}</option>
          </select>
          <select v-model="filters.enabled" class="rounded border border-outline-variant/40 px-3 py-2 text-sm">
            <option value="">全部状态</option>
            <option value="true">启用</option>
            <option value="false">禁用</option>
          </select>
        </div>
        <div class="mb-4 flex items-center gap-3">
          <button class="rounded border border-primary text-primary px-3 py-2 text-sm font-semibold" @click="loadUsers">查询</button>
          <button class="text-xs text-on-surface-variant" @click="resetFilters">重置</button>
        </div>
        <div v-if="loading" class="text-sm text-on-surface-variant">加载中...</div>
        <div v-else-if="users.length === 0" class="text-sm text-on-surface-variant">暂无内部人员账号。</div>
        <div v-else class="grid grid-cols-1 xl:grid-cols-[1.25fr_1fr] gap-5">
          <div class="overflow-auto">
            <table class="w-full text-sm">
              <thead class="text-xs text-on-surface-variant">
                <tr class="text-left">
                  <th class="pb-2">姓名</th>
                  <th class="pb-2">岗位</th>
                  <th class="pb-2">邮箱</th>
                  <th class="pb-2">电话</th>
                  <th class="pb-2">状态</th>
                  <th class="pb-2">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="user in users" :key="user.id" class="border-t border-outline-variant/20">
                  <td class="py-3 font-semibold">{{ user.fullName || '-' }}</td>
                  <td class="py-3">{{ formatRole(user.roles?.[0]) }}</td>
                  <td class="py-3">{{ user.email }}</td>
                  <td class="py-3">{{ user.phone || '-' }}</td>
                  <td class="py-3">
                    <span class="text-xs px-2 py-1 rounded-full" :class="user.enabled ? 'bg-emerald-100 text-emerald-700' : 'bg-slate-200 text-slate-600'">
                      {{ user.enabled ? '启用' : '禁用' }}
                    </span>
                  </td>
                  <td class="py-3">
                    <button class="text-xs text-primary" @click="selectUser(user)">编辑</button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <section class="rounded-xl border border-outline-variant/20 bg-slate-50 p-4">
            <div>
              <h4 class="text-sm font-bold tracking-tight">账号编辑</h4>
              <p class="mt-1 text-xs text-on-surface-variant">{{ selectedUser ? `当前编辑：${selectedUser.fullName || selectedUser.email}` : '请选择左侧一个账号进行编辑' }}</p>
            </div>
            <form v-if="selectedUser" class="mt-4 grid grid-cols-1 gap-3" @submit.prevent="handleUpdateUser">
              <input v-model="editForm.fullName" placeholder="姓名" class="rounded border border-outline-variant/40 px-3 py-2 text-sm" required />
              <input v-model="editForm.phone" placeholder="手机号" class="rounded border border-outline-variant/40 px-3 py-2 text-sm" required />
              <div class="rounded border border-dashed border-outline-variant/40 px-3 py-2 text-sm bg-white text-on-surface-variant">用户名：{{ selectedUser.username }}</div>
              <div class="rounded border border-dashed border-outline-variant/40 px-3 py-2 text-sm bg-white text-on-surface-variant">邮箱：{{ selectedUser.email }}</div>
              <select v-model="editForm.role" class="rounded border border-outline-variant/40 px-3 py-2 text-sm" required>
                <option value="">请选择岗位</option>
                <option v-for="role in roleOptions" :key="`edit-${role.role}`" :value="role.role">{{ role.label }}</option>
              </select>
              <select v-model="editForm.enabled" class="rounded border border-outline-variant/40 px-3 py-2 text-sm">
                <option :value="true">启用</option>
                <option :value="false">禁用</option>
              </select>
              <div class="flex items-center gap-3 pt-2">
                <button class="rounded bg-primary text-white px-4 py-2 text-sm font-semibold" :disabled="saving">{{ saving ? '保存中...' : '保存修改' }}</button>
                <button type="button" class="rounded border border-outline-variant/40 px-4 py-2 text-sm" @click="clearSelection">取消</button>
              </div>
            </form>
            <div v-else class="mt-4 text-sm text-on-surface-variant">请选择左侧账号进行编辑。</div>
            <div v-if="saveMessage" class="mt-3 text-xs text-emerald-600">{{ saveMessage }}</div>
            <div v-if="saveError" class="mt-3 text-xs text-error">{{ saveError }}</div>
          </section>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { adminApi } from '../api/services.js';

const roleOptions = ref([]);
const users = ref([]);
const selectedUser = ref(null);
const loading = ref(false);
const creating = ref(false);
const saving = ref(false);
const createMessage = ref('');
const createError = ref('');
const saveMessage = ref('');
const saveError = ref('');

const filters = reactive({
  keyword: '',
  role: '',
  enabled: ''
});

const createForm = reactive({
  username: '',
  email: '',
  fullName: '',
  phone: '',
  password: '',
  role: ''
});

const editForm = reactive({
  fullName: '',
  phone: '',
  role: '',
  enabled: true
});

const loadRoles = async () => {
  try {
    const response = await adminApi.listRoles();
    roleOptions.value = response.data || [];
  } catch (error) {
    roleOptions.value = [];
  }
};

const loadUsers = async () => {
  loading.value = true;
  saveMessage.value = '';
  saveError.value = '';
  try {
    const response = await adminApi.listUsers({
      keyword: filters.keyword || undefined,
      role: filters.role || undefined,
      enabled: filters.enabled === '' ? undefined : filters.enabled === 'true'
    });
    users.value = response.data || [];
    if (selectedUser.value) {
      const latest = users.value.find((item) => item.id === selectedUser.value.id);
      if (latest) {
        selectUser(latest);
      } else {
        clearSelection();
      }
    }
  } catch (error) {
    users.value = [];
  } finally {
    loading.value = false;
  }
};

const handleCreateUser = async () => {
  createMessage.value = '';
  createError.value = '';
  creating.value = true;
  try {
    await adminApi.createUser({
      username: createForm.username,
      email: createForm.email,
      fullName: createForm.fullName,
      phone: createForm.phone,
      password: createForm.password,
      role: createForm.role
    });
    createMessage.value = '内部人员账号创建成功。';
    resetCreateForm();
    await loadUsers();
  } catch (error) {
    createError.value = error?.response?.data?.message || error?.response?.data || '账号创建失败。';
  } finally {
    creating.value = false;
  }
};

const handleUpdateUser = async () => {
  if (!selectedUser.value?.id) {
    return;
  }
  saveMessage.value = '';
  saveError.value = '';
  saving.value = true;
  try {
    const response = await adminApi.updateUser(selectedUser.value.id, {
      fullName: editForm.fullName,
      phone: editForm.phone,
      role: editForm.role,
      enabled: editForm.enabled
    });
    saveMessage.value = '账号更新成功。';
    selectUser(response.data || selectedUser.value);
    await loadUsers();
  } catch (error) {
    saveError.value = error?.response?.data?.message || error?.response?.data || '账号更新失败。';
  } finally {
    saving.value = false;
  }
};

const selectUser = (user) => {
  selectedUser.value = user;
  editForm.fullName = user.fullName || '';
  editForm.phone = user.phone || '';
  editForm.role = user.roles?.[0] || '';
  editForm.enabled = Boolean(user.enabled);
};

const clearSelection = () => {
  selectedUser.value = null;
  editForm.fullName = '';
  editForm.phone = '';
  editForm.role = '';
  editForm.enabled = true;
};

const resetCreateForm = () => {
  createForm.username = '';
  createForm.email = '';
  createForm.fullName = '';
  createForm.phone = '';
  createForm.password = '';
  createForm.role = '';
};

const resetFilters = async () => {
  filters.keyword = '';
  filters.role = '';
  filters.enabled = '';
  await loadUsers();
};

const formatRole = (role) => roleOptions.value.find((item) => item.role === role)?.label || role || '-';

onMounted(async () => {
  await loadRoles();
  await loadUsers();
});
</script>

