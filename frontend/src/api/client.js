import axios from 'axios';

const client = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1',
  timeout: 15000
});

client.interceptors.request.use((config) => {
  const token = localStorage.getItem('auth_token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

client.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error?.response?.status === 401) {
      localStorage.removeItem('auth_token');
       localStorage.removeItem('auth_email');
      localStorage.removeItem('auth_username');
      localStorage.removeItem('auth_role');
      localStorage.removeItem('auth_user_type');
      localStorage.removeItem('auth_internal_position');
    }
    return Promise.reject(error);
  }
);

export default client;

