import axios from 'axios';

const baseUrl = process.env.API_BASE_URL || 'http://127.0.0.1:8085/api/v1';

const run = async () => {
  try {
    await axios.post(`${baseUrl}/auth/login`, { email: 'smoke@example.com', password: 'smoke' });
    console.log('API reachable (login endpoint responded).');
  } catch (error) {
    if (error.response) {
      console.log(`API reachable (status ${error.response.status}).`);
      return;
    }
    console.error('API unreachable:', error.message);
    process.exit(1);
  }
};

run();

