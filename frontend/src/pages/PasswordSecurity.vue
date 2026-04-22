<template>
  <div class="space-y-6">
    <section class="panel-surface p-6">
      <div class="max-w-2xl">
        <h3 class="text-lg font-bold text-white tracking-tight">账户安全 / 修改密码</h3>
        <p class="mt-2 text-sm text-slate-300">为保证系统安全，修改密码后将自动退出当前登录，请使用新密码重新登录。</p>
      </div>
      <form class="mt-6 max-w-2xl grid grid-cols-1 gap-4" @submit.prevent="submitChangePassword">
        <input v-model="form.currentPassword" type="password" placeholder="请输入当前密码" class="neo-input" required />
        <input v-model="form.newPassword" type="password" placeholder="请输入新密码（至少 6 位）" class="neo-input" required />
        <input v-model="form.confirmPassword" type="password" placeholder="请再次输入新密码" class="neo-input" required />
        <div class="flex items-center gap-3 pt-2">
          <button class="neo-button-primary" :disabled="submitting">{{ submitting ? '提交中...' : '修改密码' }}</button>
          <button type="button" class="neo-button-secondary" @click="resetForm">清空</button>
        </div>
      </form>
      <div v-if="message" class="mt-4 text-sm text-emerald-300">{{ message }}</div>
      <div v-if="error" class="mt-4 text-sm text-rose-300">{{ error }}</div>
      <ul class="mt-6 grid grid-cols-1 md:grid-cols-3 gap-3 text-xs text-slate-300">
        <li class="rounded-2xl border border-slate-500/20 bg-slate-950/40 px-4 py-3">建议定期修改密码，避免长期使用默认密码。</li>
        <li class="rounded-2xl border border-slate-500/20 bg-slate-950/40 px-4 py-3">密码建议包含数字与字母，长度至少 6 位。</li>
        <li class="rounded-2xl border border-slate-500/20 bg-slate-950/40 px-4 py-3">修改成功后系统会自动要求重新登录。</li>
      </ul>
    </section>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { profileApi } from '../api/services.js';
import { useAuthStore } from '../store/auth.js';
import { useRealtimeStore } from '../store/realtime.js';

const router = useRouter();
const auth = useAuthStore();
const realtime = useRealtimeStore();
const submitting = ref(false);
const message = ref('');
const error = ref('');

const form = reactive({
  currentPassword: '',
  newPassword: '',
  confirmPassword: ''
});

const resetForm = () => {
  form.currentPassword = '';
  form.newPassword = '';
  form.confirmPassword = '';
};

const submitChangePassword = async () => {
  message.value = '';
  error.value = '';
  if (form.newPassword.length < 6) {
    error.value = '新密码长度不能少于 6 位。';
    return;
  }
  if (form.newPassword !== form.confirmPassword) {
    error.value = '两次输入的新密码不一致。';
    return;
  }
  submitting.value = true;
  try {
    const response = await profileApi.changePassword({
      currentPassword: form.currentPassword,
      newPassword: form.newPassword
    });
    message.value = response.data || '密码修改成功，请重新登录。';
    resetForm();
    setTimeout(() => {
      realtime.disconnect();
      realtime.clearEvents();
      auth.logout();
      router.push({ name: 'Login' });
    }, 800);
  } catch (err) {
    error.value = err?.response?.data?.message || err?.response?.data || '密码修改失败。';
  } finally {
    submitting.value = false;
  }
};
</script>

