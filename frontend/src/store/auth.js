import { reactive, readonly } from 'vue';
import { authApi } from '../api/services.js';
import {
  canAccessRouteName,
  getDefaultRouteNameByRole,
  getProfileByRole,
  hasRolePermission,
  normalizeRole,
  resolveRoleFromSelection
} from '../constants/access.js';

const normalizeToken = (token) => {
  if (!token || token === 'undefined' || token === 'null') {
    return '';
  }
  return token;
};

const PROFILES_KEY = 'auth_profiles';

const normalizeUserType = (value) => {
  if (!value) {
    return '';
  }
  return value.toUpperCase();
};

const readProfiles = () => {
  try {
    return JSON.parse(localStorage.getItem(PROFILES_KEY) || '{}');
  } catch (error) {
    return {};
  }
};

const saveProfileForUsername = (username, profile) => {
  if (!username) {
    return;
  }
  const profiles = readProfiles();
  profiles[username] = {
    userType: normalizeUserType(profile?.userType),
    role: normalizeRole(profile?.role),
    internalPosition: profile?.internalPosition || ''
  };
  localStorage.setItem(PROFILES_KEY, JSON.stringify(profiles));
};

const getProfileForUsername = (username) => {
  const profiles = readProfiles();
  return profiles[username] || null;
};

const state = reactive({
  username: localStorage.getItem('auth_username') || '',
  token: normalizeToken(localStorage.getItem('auth_token')),
  role: normalizeRole(localStorage.getItem('auth_role')),
  userType: normalizeUserType(localStorage.getItem('auth_user_type')),
  internalPosition: localStorage.getItem('auth_internal_position') || ''
});

const setSession = (username, token, profile = null) => {
  state.username = username;
  state.token = token;

  if (profile) {
    state.role = normalizeRole(profile.role);
    state.userType = normalizeUserType(profile.userType);
    state.internalPosition = profile.internalPosition || '';
  }

  if (token) {
    localStorage.setItem('auth_token', token);
    localStorage.setItem('auth_username', username || '');
    localStorage.setItem('auth_role', state.role || '');
    localStorage.setItem('auth_user_type', state.userType || '');
    localStorage.setItem('auth_internal_position', state.internalPosition || '');
    saveProfileForUsername(username, {
      userType: state.userType,
      role: state.role,
      internalPosition: state.internalPosition
    });
  } else {
    localStorage.removeItem('auth_token');
    localStorage.removeItem('auth_username');
    localStorage.removeItem('auth_role');
    localStorage.removeItem('auth_user_type');
    localStorage.removeItem('auth_internal_position');
    state.role = '';
    state.userType = '';
    state.internalPosition = '';
  }
};

const login = async (payload) => {
  const response = await authApi.login(payload);

  const roleFromBackend = normalizeRole(response?.data?.role || response?.data?.roles?.[0]);
  const role = roleFromBackend || normalizeRole(getProfileForUsername(payload.username)?.role);
  const profileFromRole = getProfileByRole(role);

  setSession(payload.username, response.data.token, {
    role,
    userType: profileFromRole.userType,
    internalPosition: profileFromRole.internalPosition
  });
  return response.data;
};

const register = async (payload, profile) => {
  const role = normalizeRole(profile?.role || resolveRoleFromSelection(profile?.userType, profile?.internalPosition));
  const response = await authApi.register(payload, role);
  saveProfileForUsername(payload.username, {
    userType: profile?.userType,
    role,
    internalPosition: profile?.internalPosition || ''
  });
  return response.data;
};

const logout = () => {
  setSession('', '');
};

export const useAuthStore = () => ({
  state: readonly(state),
  login,
  register,
  logout,
  getProfileForUsername,
  canAccessRoute: (routeName) => canAccessRouteName(state.role, routeName),
  hasPermission: (permission) => hasRolePermission(state.role, permission),
  resolveHomeRouteName: () => getDefaultRouteNameByRole(state.role)
});

