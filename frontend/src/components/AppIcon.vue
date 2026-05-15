<template>
  <svg
    xmlns="http://www.w3.org/2000/svg"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    :stroke-width="icon.strokeWidth || 1.8"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <path v-for="(path, index) in icon.paths" :key="index" :d="path" />
  </svg>
</template>

<script setup>
import { computed } from 'vue';

const props = defineProps({
  name: {
    type: String,
    required: true
  }
});

const ICONS = {
  dashboard: {
    paths: ['M4 4h7v7H4z', 'M13 4h7v4h-7z', 'M13 10h7v10h-7z', 'M4 13h7v7H4z']
  },
  manage_accounts: {
    paths: ['M15 19.5c0-2.2-2.7-4-6-4s-6 1.8-6 4', 'M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8', 'M18 8.5h4', 'M20 6.5v4']
  },
  assignment: {
    paths: ['M8 4h8', 'M9 2h6v4H9z', 'M7 4H6a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V6a2 2 0 0 0-2-2h-1', 'M8 10h8', 'M8 14h8']
  },
  deployed_code: {
    paths: ['M12 3 4 7v10l8 4 8-4V7z', 'M12 3v18', 'M4 7l8 4 8-4']
  },
  monitoring: {
    paths: ['M4 18V6', 'M10 18V10', 'M16 18V13', 'M22 18V8', 'M2 20h20']
  },
  factory: {
    paths: ['M3 21V9l6 4V9l6 4V3l6 4v14', 'M3 21h18', 'M7 21v-4', 'M11 21v-3', 'M16 21v-5']
  },
  calendar_month: {
    paths: ['M6 2v4', 'M18 2v4', 'M3 8h18', 'M5 4h14a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2Z', 'M8 12h3', 'M13 12h3', 'M8 16h3']
  },
  inventory_2: {
    paths: ['M3 7.5 12 3l9 4.5-9 4.5-9-4.5Z', 'M3 7.5V17l9 4 9-4V7.5', 'M12 12v9']
  },
  warning: {
    paths: ['M12 3 2.5 20h19L12 3Z', 'M12 9v5', 'M12 17h.01']
  },
  local_shipping: {
    paths: ['M3 7h11v8H3z', 'M14 10h3l3 3v2h-6', 'M7 18a2 2 0 1 1 0-4 2 2 0 0 1 0 4Z', 'M17 18a2 2 0 1 1 0-4 2 2 0 0 1 0 4Z']
  },
  category: {
    paths: ['M4 7V4h5l2 3h9v13H4z', 'M9 12h6', 'M12 9v6']
  },
  event_note: {
    paths: ['M6 2v4', 'M18 2v4', 'M4 5h16a2 2 0 0 1 2 2v13H2V7a2 2 0 0 1 2-2Z', 'M8 11h8', 'M8 15h5']
  },
  verified: {
    paths: ['M12 3 5 6v6c0 4.3 2.9 7.9 7 9 4.1-1.1 7-4.7 7-9V6l-7-3Z', 'm9 12 2 2 4-4']
  },
  support_agent: {
    paths: ['M5 11a7 7 0 0 1 14 0v4', 'M5 15H4a2 2 0 0 1-2-2v-1a2 2 0 0 1 2-2h1', 'M19 15h1a2 2 0 0 0 2-2v-1a2 2 0 0 0-2-2h-1', 'M9 19c.8.7 1.8 1 3 1s2.2-.3 3-1', 'M8 14h8']
  },
  shield_lock: {
    paths: ['M12 3 5 6v5c0 4.3 2.9 7.9 7 9 4.1-1.1 7-4.7 7-9V6l-7-3Z', 'M10 12.5V11a2 2 0 1 1 4 0v1.5', 'M9.5 12.5h5v4h-5z']
  },
  notifications: {
    paths: ['M6 9a6 6 0 1 1 12 0c0 5 2 6 2 6H4s2-1 2-6', 'M10 19a2 2 0 0 0 4 0']
  },
  precision_manufacturing: {
    paths: ['M5 21v-6l4-4', 'M9 11V7l5-4 5 4v4', 'M14 3v5', 'M9 11h10v10H5v-6', 'M11 15h2', 'M15 15h2', 'M11 18h2', 'M15 18h2']
  },
  person: {
    paths: ['M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8', 'M5 20a7 7 0 0 1 14 0']
  },
  hub: {
    paths: ['M12 4v4', 'M6 10h12', 'M8 14v2', 'M16 14v2', 'M12 10v8', 'M4 6h4v4H4z', 'M10 18h4v4h-4z', 'M16 6h4v4h-4z']
  },
  help: {
    paths: ['M12 18h.01', 'M9.1 9a3 3 0 1 1 5.8 1c-.7.6-1.4 1.1-1.4 2.5']
  }
};

const icon = computed(() => ICONS[props.name] || ICONS.help);
</script>

