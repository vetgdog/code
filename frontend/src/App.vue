<template>
  <div class="min-h-screen bg-slate-50 text-slate-900">
    <div v-if="showShell" class="flex">
      <SidebarNav :items="visibleNavItems" :user-display-name="auth.state.username || 'SCM Controller'" :role-display-name="roleLabel" />
      <div class="flex-1 min-h-screen md:ml-72">
        <TopBar :title="pageTitle" :subtitle="pageSubtitle || roleLabel" :realtime-connected="realtime.state.connected" @logout="handleLogout" @open-security="openSecurityPage" />
        <main class="p-6 lg:p-8">
          <RouterView />
        </main>
      </div>
    </div>
    <div v-else class="min-h-screen">
      <RouterView />
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, watch } from 'vue';
import { useRoute, useRouter, RouterView } from 'vue-router';
import SidebarNav from './components/SidebarNav.vue';
import TopBar from './components/TopBar.vue';
import { useAuthStore } from './store/auth.js';
import { useRealtimeStore } from './store/realtime.js';
import { canAccessRouteName, getDashboardMetaByRole, getRoleLabel } from './constants/access.js';

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const realtime = useRealtimeStore();

const showShell = computed(() => route.name !== 'Login' && route.name !== 'Register');
const dashboardMeta = computed(() => getDashboardMetaByRole(auth.state.role));
const pageTitle = computed(() => (route.name === 'Dashboard' ? dashboardMeta.value.title : (route.meta?.title || 'SteelOps Precision')));
const pageSubtitle = computed(() => (route.name === 'Dashboard' ? dashboardMeta.value.subtitle : (route.meta?.subtitle || '')));
const roleLabel = computed(() => getRoleLabel(auth.state.role));

const navItems = computed(() => {
  const procurementNavItem = auth.state.role === 'ROLE_SUPPLIER'
    ? { label: '供应管理', icon: 'local_shipping', to: '/supply', routeName: 'Supply' }
    : { label: '采购管理', icon: 'local_shipping', to: '/procurement', routeName: 'Procurement' };

  return [
    { label: '总览', icon: 'dashboard', to: '/', routeName: 'Dashboard' },
    { label: '账号管理', icon: 'manage_accounts', to: '/account-admin', routeName: 'AccountAdmin' },
    { label: '订单管理', icon: 'assignment', to: '/orders', routeName: 'Orders' },
    { label: '产品档案', icon: 'deployed_code', to: '/products', routeName: 'Products' },
    { label: '销售记录', icon: 'monitoring', to: '/sales-records', routeName: 'SalesRecords' },
    { label: '生产任务', icon: 'factory', to: '/production', routeName: 'Production' },
    { label: '生产计划', icon: 'calendar_month', to: '/production-plan', routeName: 'ProductionPlan' },
    { label: '库存管理', icon: 'inventory_2', to: '/inventory', routeName: 'Inventory' },
    { label: '库存预警', icon: 'warning', to: '/inventory-alerts', routeName: 'InventoryAlert' },
    procurementNavItem,
    { label: '原材料档案', icon: 'category', to: '/raw-materials', routeName: 'RawMaterials' },
    { label: '采购计划', icon: 'event_note', to: '/procurement-plan', routeName: 'ProcurementPlan' },
    { label: '质量追溯', icon: 'verified', to: '/quality', routeName: 'Quality' },
    { label: '客户门户', icon: 'support_agent', to: '/customer', routeName: 'Customer' },
    { label: '账户安全', icon: 'shield_lock', to: '/password-security', routeName: 'PasswordSecurity' }
  ];
});

const visibleNavItems = computed(() => navItems.value.filter((item) => canAccessRouteName(auth.state.role, item.routeName)));

const handleLogout = () => {
  realtime.disconnect();
  realtime.clearEvents();
  auth.logout();
  router.push({ name: 'Login' });
};

const openSecurityPage = () => {
  router.push({ name: 'PasswordSecurity' });
};

watch(
  () => [auth.state.token, auth.state.role, auth.state.email],
  ([token, role, email]) => {
    if (token) {
      realtime.connect(role, email);
      return;
    }
    realtime.disconnect();
  },
  { immediate: true }
);

onBeforeUnmount(() => {
  realtime.disconnect();
});
</script>

