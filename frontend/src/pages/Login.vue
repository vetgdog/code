<template>
  <div class="min-h-screen flex items-center justify-center bg-slate-50 px-6 py-10">
    <div class="w-full max-w-5xl grid grid-cols-1 lg:grid-cols-2 rounded-2xl overflow-hidden border border-slate-200 shadow-[0_16px_45px_rgba(15,23,42,0.1)] bg-white">
      <section class="hidden lg:flex flex-col justify-between p-10 bg-gradient-to-br from-blue-600 to-cyan-500 text-white">
        <div>
          <p class="text-xs uppercase tracking-[0.24em] text-blue-100">Smart Manufacturing Console</p>
          <h2 class="mt-4 text-3xl font-black tracking-tight">SteelOps Precision</h2>
          <p class="mt-4 text-sm text-blue-100/95 leading-6">连接订单、采购、生产与质量追溯，统一企业协同工作台。</p>
        </div>
        <div class="space-y-2 text-sm text-blue-100">
          <p class="flex items-center gap-2"><span class="w-2 h-2 rounded-full bg-emerald-300"></span> 实时推送在线协作</p>
          <p class="flex items-center gap-2"><span class="w-2 h-2 rounded-full bg-emerald-300"></span> 按角色智能分配功能</p>
        </div>
      </section>

      <section class="p-8 md:p-10">
        <h1 class="text-2xl font-bold text-slate-900">账号登录</h1>
        <p class="text-sm text-slate-500 mt-1">请输入用户名和密码，系统将自动识别角色权限。</p>

        <form class="mt-6 space-y-4" @submit.prevent="handleLogin">
          <div>
            <label class="text-xs font-semibold uppercase tracking-widest text-slate-500">用户名</label>
            <input v-model="loginForm.username" class="mt-2 w-full rounded-lg border border-slate-300 px-3 py-2.5 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100" required />
          </div>
          <div>
            <label class="text-xs font-semibold uppercase tracking-widest text-slate-500">密码</label>
            <input v-model="loginForm.password" type="password" class="mt-2 w-full rounded-lg border border-slate-300 px-3 py-2.5 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100" required />
          </div>
          <button class="w-full rounded-lg bg-blue-600 hover:bg-blue-700 text-white py-2.5 text-sm font-semibold transition-colors">登录</button>
        </form>

        <div v-if="loginError" class="mt-3 text-xs text-red-600">{{ loginError }}</div>

        <p class="mt-6 text-xs text-slate-500">
          还没有账号？
          <RouterLink class="text-blue-600 font-semibold" :to="{ name: 'Register' }">立即注册</RouterLink>
        </p>
      </section>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue';
import { RouterLink, useRouter } from 'vue-router';
import { useAuthStore } from '../store/auth.js';

const router = useRouter();
const auth = useAuthStore();

const loginForm = reactive({
  username: '',
  password: ''
});

const loginError = ref('');

const handleLogin = async () => {
  loginError.value = '';

  try {
    await auth.login({ username: loginForm.username, password: loginForm.password });
    router.push({ name: auth.resolveHomeRouteName() });
  } catch (error) {
    loginError.value = error?.response?.data?.message || '登录失败，请检查用户名或密码。';
  }
};
</script>

