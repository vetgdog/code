export const USER_TYPES = {
  CUSTOMER: 'CUSTOMER',
  SUPPLIER: 'SUPPLIER',
  INTERNAL: 'INTERNAL'
};

export const INTERNAL_POSITIONS = [
  { value: 'ADMIN', label: '系统管理员', role: 'ROLE_ADMIN' },
  { value: 'SALES_MANAGER', label: '销售管理员', role: 'ROLE_SALES_MANAGER' },
  { value: 'PROCUREMENT_MANAGER', label: '采购管理员', role: 'ROLE_PROCUREMENT_MANAGER' },
  { value: 'PRODUCTION_MANAGER', label: '生产管理员', role: 'ROLE_PRODUCTION_MANAGER' },
  { value: 'WAREHOUSE_MANAGER', label: '仓库管理员', role: 'ROLE_WAREHOUSE_MANAGER' },
  { value: 'QUALITY_INSPECTOR', label: '质检员', role: 'ROLE_QUALITY_INSPECTOR' }
];

const ROLE_CONFIG = {
  ROLE_ADMIN: {
    defaultRouteName: 'Dashboard',
    routeNames: ['Dashboard', 'Orders', 'Production', 'Inventory', 'Procurement', 'Quality', 'Customer'],
    permissions: [
      'dashboard:view',
      'orders:view',
      'orders:create',
      'orders:plan',
      'orders:review',
      'orders:warehouse-check',
      'production:view',
      'production:create',
      'production:update',
      'inventory:view',
      'inventory:edit',
      'procurement:view',
      'procurement:create',
      'quality:view',
      'customer:view'
    ]
  },
  ROLE_CUSTOMER: {
    defaultRouteName: 'Customer',
    routeNames: ['Customer'],
    permissions: ['customer:view']
  },
  ROLE_SUPPLIER: {
    defaultRouteName: 'Procurement',
    routeNames: ['Procurement', 'Inventory'],
    permissions: ['procurement:view', 'procurement:create', 'inventory:view']
  },
  ROLE_SALES_MANAGER: {
    defaultRouteName: 'Orders',
    routeNames: ['Dashboard', 'Orders', 'Customer'],
    permissions: ['dashboard:view', 'orders:view', 'orders:create', 'orders:plan', 'orders:review', 'customer:view']
  },
  ROLE_PROCUREMENT_MANAGER: {
    defaultRouteName: 'Procurement',
    routeNames: ['Dashboard', 'Procurement', 'Inventory'],
    permissions: ['dashboard:view', 'procurement:view', 'procurement:create', 'inventory:view']
  },
  ROLE_PRODUCTION_MANAGER: {
    defaultRouteName: 'Production',
    routeNames: ['Dashboard', 'Production', 'Orders'],
    permissions: ['dashboard:view', 'production:view', 'production:create', 'production:update', 'orders:view']
  },
  ROLE_WAREHOUSE_MANAGER: {
    defaultRouteName: 'Inventory',
    routeNames: ['Dashboard', 'Inventory', 'Orders'],
    permissions: ['dashboard:view', 'inventory:view', 'inventory:edit', 'orders:view', 'orders:warehouse-check']
  },
  ROLE_QUALITY_INSPECTOR: {
    defaultRouteName: 'Quality',
    routeNames: ['Dashboard', 'Quality'],
    permissions: ['dashboard:view', 'quality:view']
  }
};

export const normalizeRole = (role) => {
  if (!role) {
    return '';
  }
  const normalized = role.toUpperCase();
  return normalized.startsWith('ROLE_') ? normalized : `ROLE_${normalized}`;
};

export const resolveRoleFromSelection = (userType, internalPosition) => {
  if (userType === USER_TYPES.CUSTOMER) {
    return 'ROLE_CUSTOMER';
  }
  if (userType === USER_TYPES.SUPPLIER) {
    return 'ROLE_SUPPLIER';
  }
  const selectedInternal = INTERNAL_POSITIONS.find((item) => item.value === internalPosition);
  return selectedInternal ? selectedInternal.role : '';
};

export const getRoleConfig = (role) => {
  const normalizedRole = normalizeRole(role);
  return ROLE_CONFIG[normalizedRole] || ROLE_CONFIG.ROLE_CUSTOMER;
};

export const getDefaultRouteNameByRole = (role) => getRoleConfig(role).defaultRouteName;

export const canAccessRouteName = (role, routeName) => getRoleConfig(role).routeNames.includes(routeName);

export const hasRolePermission = (role, permission) => getRoleConfig(role).permissions.includes(permission);

export const getRoleLabel = (role) => {
  const normalizedRole = normalizeRole(role);
  const internal = INTERNAL_POSITIONS.find((item) => item.role === normalizedRole);
  if (internal) {
    return internal.label;
  }
  if (normalizedRole === 'ROLE_CUSTOMER') {
    return '客户';
  }
  if (normalizedRole === 'ROLE_SUPPLIER') {
    return '供应商';
  }
  return '内部人员';
};

export const getProfileByRole = (role) => {
  const normalizedRole = normalizeRole(role);
  if (normalizedRole === 'ROLE_CUSTOMER') {
    return { userType: USER_TYPES.CUSTOMER, internalPosition: '' };
  }
  if (normalizedRole === 'ROLE_SUPPLIER') {
    return { userType: USER_TYPES.SUPPLIER, internalPosition: '' };
  }

  const internal = INTERNAL_POSITIONS.find((item) => item.role === normalizedRole);
  return {
    userType: USER_TYPES.INTERNAL,
    internalPosition: internal?.value || ''
  };
};

