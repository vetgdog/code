<template>
  <div class="min-h-screen flex items-center justify-center bg-slate-50 px-6 py-10">
    <div class="w-full max-w-lg bg-white rounded-2xl shadow-[0_16px_45px_rgba(15,23,42,0.1)] border border-slate-200 p-8">
      <h1 class="text-2xl font-bold text-slate-900">账号注册</h1>
      <p class="text-sm text-slate-500 mt-1">请选择用户类型并创建账号</p>

      <form class="mt-6 space-y-4" @submit.prevent="handleRegister">
        <div>
          <label class="text-xs font-semibold uppercase tracking-widest text-slate-500">用户名</label>
          <input v-model="form.username" class="mt-2 w-full rounded-lg border border-slate-300 px-3 py-2.5 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100" required />
        </div>

        <div>
          <label class="text-xs font-semibold uppercase tracking-widest text-slate-500">密码</label>
          <input v-model="form.password" type="password" class="mt-2 w-full rounded-lg border border-slate-300 px-3 py-2.5 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100" required />
        </div>

        <div>
          <label class="text-xs font-semibold uppercase tracking-widest text-slate-500">用户类型</label>
          <select v-model="form.userType" class="mt-2 w-full rounded-lg border border-slate-300 px-3 py-2.5 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100" required>
            <option value="">请选择</option>
            <option :value="USER_TYPES.CUSTOMER">客户</option>
            <option :value="USER_TYPES.SUPPLIER">供应商</option>
            <option :value="USER_TYPES.INTERNAL">内部人员</option>
          </select>
        </div>

        <div v-if="form.userType === USER_TYPES.INTERNAL">
          <label class="text-xs font-semibold uppercase tracking-widest text-slate-500">内部岗位</label>
          <select v-model="form.internalPosition" class="mt-2 w-full rounded-lg border border-slate-300 px-3 py-2.5 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100" required>
            <option value="">请选择岗位</option>
            <option v-for="option in INTERNAL_POSITIONS" :key="option.value" :value="option.value">{{ option.label }}</option>
          </select>
        </div>

        <button class="w-full rounded-lg bg-blue-600 hover:bg-blue-700 text-white py-2.5 text-sm font-semibold transition-colors">注册</button>
      </form>

      <div v-if="message" class="mt-3 text-xs text-emerald-600">{{ message }}</div>
      <div v-if="error" class="mt-3 text-xs text-red-600">{{ error }}</div>

      <p class="mt-6 text-xs text-slate-500">
        已有账号？
        <RouterLink class="text-blue-600 font-semibold" :to="{ name: 'Login' }">返回登录</RouterLink>
      </p>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue';
import { RouterLink } from 'vue-router';
import { useAuthStore } from '../store/auth.js';
import { INTERNAL_POSITIONS, resolveRoleFromSelection, USER_TYPES } from '../constants/access.js';

const auth = useAuthStore();

const form = reactive({
  username: '',
  password: '',
  userType: '',
  internalPosition: ''
});

const message = ref('');
const error = ref('');

const handleRegister = async () => {
  message.value = '';
  error.value = '';

  if (form.userType === USER_TYPES.INTERNAL && !form.internalPosition) {
    error.value = '请选择内部岗位。';
    return;
  }

  try {
    const role = resolveRoleFromSelection(form.userType, form.internalPosition);
    await auth.register(
      {
        username: form.username,
        password: form.password
      },
      {
        userType: form.userType,
        internalPosition: form.userType === USER_TYPES.INTERNAL ? form.internalPosition : '',
        role
      }
    );
    message.value = '注册成功，请前往登录页面。';
  } catch (registerError) {
    error.value = registerError?.response?.data || '注册失败，请稍后再试。';
  }
};
</script>

