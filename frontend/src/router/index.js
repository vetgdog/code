import { createRouter, createWebHistory } from 'vue-router';
import Dashboard from '../pages/Dashboard.vue';
import Orders from '../pages/Orders.vue';
import Products from '../pages/Products.vue';
import SalesRecords from '../pages/SalesRecords.vue';
import Production from '../pages/Production.vue';
import ProductionPlan from '../pages/ProductionPlan.vue';
import Inventory from '../pages/Inventory.vue';
import InventoryAlert from '../pages/InventoryAlert.vue';
import Procurement from '../pages/Procurement.vue';
import RawMaterials from '../pages/RawMaterials.vue';
import ProcurementPlan from '../pages/ProcurementPlan.vue';
import Quality from '../pages/Quality.vue';
import Customer from '../pages/Customer.vue';
import AccountAdmin from '../pages/AccountAdmin.vue';
import PasswordSecurity from '../pages/PasswordSecurity.vue';
import Login from '../pages/Login.vue';
import Register from '../pages/Register.vue';
import { canAccessRouteName, getDefaultRouteNameByRole, normalizeRole } from '../constants/access.js';

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: Login,
    meta: { public: true, title: '账号登录', subtitle: 'Sign in to continue' }
  },
  {
    path: '/register',
    name: 'Register',
    component: Register,
    meta: { public: true, title: '账号注册', subtitle: 'Create your account' }
  },
  {
    path: '/',
    name: 'Dashboard',
    component: Dashboard,
    meta: { title: '总经理看板', subtitle: 'Executive overview' }
  },
  {
    path: '/dashboard',
    redirect: '/'
  },
  {
    path: '/orders',
    name: 'Orders',
    component: Orders,
    meta: { title: '订单管理', subtitle: 'Sales orders and plans' }
  },
  {
    path: '/products',
    name: 'Products',
    component: Products,
    meta: { title: '产品档案', subtitle: 'Product catalog management' }
  },
  {
    path: '/sales-records',
    name: 'SalesRecords',
    component: SalesRecords,
    meta: { title: '销售记录', subtitle: 'Sales history and gantt overview' }
  },
  {
    path: '/production',
    name: 'Production',
    component: Production,
    meta: { title: '生产任务', subtitle: 'Tasks and status updates' }
  },
  {
    path: '/production-plan',
    name: 'ProductionPlan',
    component: ProductionPlan,
    meta: { title: '生产计划', subtitle: 'Weekly production planning' }
  },
  {
    path: '/inventory',
    name: 'Inventory',
    component: Inventory,
    meta: { title: '库存管理', subtitle: 'Stock in and out' }
  },
  {
    path: '/inventory-alerts',
    name: 'InventoryAlert',
    component: InventoryAlert,
    meta: { title: '库存预警', subtitle: 'Low-stock alerts and actions' }
  },
  {
    path: '/procurement',
    name: 'Procurement',
    component: Procurement,
    meta: { title: '采购管理', subtitle: 'Requests and orders' }
  },
  {
    path: '/raw-materials',
    name: 'RawMaterials',
    component: RawMaterials,
    meta: { title: '原材料档案', subtitle: 'Materials and supplier data' }
  },
  {
    path: '/procurement-plan',
    name: 'ProcurementPlan',
    component: ProcurementPlan,
    meta: { title: '采购计划', subtitle: 'Weekly raw material planning' }
  },
  {
    path: '/supply',
    name: 'Supply',
    component: Procurement,
    meta: { title: '供应管理', subtitle: 'Supply orders and materials' }
  },
  {
    path: '/quality',
    name: 'Quality',
    component: Quality,
    meta: { title: '质量追溯', subtitle: 'Batch records' }
  },
  {
    path: '/customer',
    name: 'Customer',
    component: Customer,
    meta: { title: '客户门户', subtitle: 'Customer order lookup' }
  },
  {
    path: '/account-admin',
    name: 'AccountAdmin',
    component: AccountAdmin,
    meta: { title: '账号管理', subtitle: 'Internal staff accounts' }
  },
  {
    path: '/password-security',
    name: 'PasswordSecurity',
    component: PasswordSecurity,
    meta: { title: '账户安全', subtitle: 'Change your password securely' }
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/'
  }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});

router.beforeEach((to) => {
  const token = localStorage.getItem('auth_token');
  const role = normalizeRole(localStorage.getItem('auth_role'));
  const hasToken = Boolean(token && token !== 'undefined' && token !== 'null');

  if (!to.meta.public && !hasToken) {
    return { name: 'Login' };
  }
  if ((to.name === 'Login' || to.name === 'Register') && hasToken) {
    return { name: getDefaultRouteNameByRole(role) };
  }
  if (!to.meta.public && to.name && !canAccessRouteName(role, to.name)) {
    return { name: getDefaultRouteNameByRole(role) };
  }
  return true;
});

export default router;

