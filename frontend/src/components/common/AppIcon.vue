<script setup lang="ts">
// @ts-nocheck

type IconName = string;

type Props = {
  name: IconName;
  size?: number;
  color?: string;
};

const props = withDefaults(defineProps<Props>(), { size: 20 });
</script>

<template>
  <svg
    v-if="name === 'users'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <path
      d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <circle
      cx="9"
      cy="7"
      r="4"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <path
      d="M23 21v-2a4 4 0 0 0-3-3.87"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <path
      d="M16 3.13a4 4 0 0 1 0 7.75"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'pie-chart'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <path
      d="M21.21 15.89A10 10 0 1 1 8 2.83"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <path
      d="M22 12A10 10 0 0 0 12 2v10z"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'logout'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <path
      d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1-2-2h4"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <polyline
      points="16 17 21 12 16 7"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <line
      x1="21"
      y1="12"
      x2="9"
      y2="12"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'message-square'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <path
      d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'menu'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <path d="M4 7H20" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
    <path d="M4 12H20" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
    <path d="M4 17H20" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
  </svg>

  <svg
    v-else-if="name === 'list'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <line x1="8" y1="6" x2="21" y2="6" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
    <line x1="8" y1="12" x2="21" y2="12" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
    <line x1="8" y1="18" x2="21" y2="18" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
    <line x1="3" y1="6" x2="3.01" y2="6" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
    <line x1="3" y1="12" x2="3.01" y2="12" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
    <line x1="3" y1="18" x2="3.01" y2="18" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
  </svg>

  <svg
    v-else-if="name === 'type'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <path d="M4 7V4h16v3M4 17v3h16v-3M9 4v16M15 4v16" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
  </svg>

  <svg
    v-else-if="name === 'sort'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <path d="M12 15V3M5 12l7 7 7-7M5 6l7 3 7-3" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
  </svg>

  <svg
    v-else-if="name === 'sidebar-left'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <rect x="4" y="5" width="16" height="14" rx="3" stroke="currentColor" stroke-width="2" />
    <path d="M9 5v14" stroke="currentColor" stroke-width="2" />
  </svg>

  <svg
    v-else-if="name === 'edit'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <path
      d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <path
      d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'search'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <circle cx="11" cy="11" r="8" stroke="currentColor" stroke-width="2" />
    <path d="M21 21l-4.35-4.35" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
  </svg>

  <svg
    v-else-if="name === 'plus'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <path d="M12 5V19" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
    <path d="M5 12H19" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
  </svg>

  <svg
    v-else-if="name === 'collapse'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <path d="M15 6L9 12L15 18" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
  </svg>

  <svg
    v-else-if="name === 'book'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <path
      d="M4 19.5C4 18.837 4.53726 18.3 5.2 18.3H19M4 19.5C4 20.163 4.53726 20.7 5.2 20.7H19M4 19.5V5.2C4 4.53726 4.53726 4 5.2 4H19V18.3M19 18.3V20.7"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'folder'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <path
      d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'dollar'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="2" />
    <path
      d="M12 6v12M15 9H10.5a2.25 2.25 0 1 0 0 4.5h3a2.25 2.25 0 1 1 0 4.5H9"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'graduation'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <path
      d="M22 10L12 5L2 10L12 15L22 10Z"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <path
      d="M6 12V17C6 17 9 20 12 20C15 20 18 17 18 17V12"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'pen-tool'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <path
      d="M12 19l7-7 3 3-7 7-3-3z"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <path
      d="M18 13l-1.5-7.5L2 2l3.5 14.5L13 18l5-5z"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <path
      d="M2 2l7.586 7.586"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <circle
      cx="11"
      cy="11"
      r="2"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'popcorn'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <path
      d="M18 8a2 2 0 0 0 0-4 2 2 0 0 0-4 0 2 2 0 0 0-4 0 2 2 0 0 0-4 0 2 2 0 0 0 0 4"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <path
      d="M10 22L7 8h10l-3 14"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <path
      d="M10 8l1 14M14 8l-1 14"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'palette'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <path
      d="M12 22C17.5228 22 22 17.5228 22 12C22 6.47715 17.5228 2 12 2C6.47715 2 2 6.47715 2 12C2 17.5228 6.47715 22 12 22Z"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <path
      d="M6 12C6 12 7 11 8 11C9 11 10 12 10 12"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <circle cx="7.5" cy="16.5" r="0.5" fill="currentColor" />
    <circle cx="12" cy="19" r="0.5" fill="currentColor" />
    <circle cx="16.5" cy="16.5" r="0.5" fill="currentColor" />
  </svg>

  <svg
    v-else-if="name === 'stethoscope'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <path
      d="M4.8 23h4.4M7 23v-5M7 11c0-3.314 2.686-6 6-6s6 2.686 6 6v3a3 3 0 0 1-3 3H8a3 3 0 0 1-3-3v-3c0-3.314 2.686-6 6-6"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <circle cx="13" cy="5" r="3" stroke="currentColor" stroke-width="2" />
  </svg>

  <svg
    v-else-if="name === 'flower'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <circle cx="12" cy="12" r="3" stroke="currentColor" stroke-width="2" />
    <path
      d="M12 7c2-4 6-4 6 0s-4 6-6 6-6-2-6-6 4-4 6 0zM12 17c-2 4-6 4-6 0s4-6 6-6 6 2 6 6-4 4-6 0zM7 12c-4-2-4-6 0-6s6 4 6 6-2 6-6 6-4-4 0-6zM17 12c4 2 4 6 0 6s-6-4-6-6 2-6 6-6 4 4 0 6z"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'lotus'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <path
      d="M12 22s-8-3-8-10c0-4 4-8 8-8s8 4 8 8c0 7-8 10-8 10z"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <path
      d="M12 22s-5-2-5-7c0-3 2-6 5-6s5 3 5 6c0 5-5 7-5 7z"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'briefcase'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <rect
      x="2"
      y="7"
      width="20"
      height="14"
      rx="2"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <path
      d="M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'bar-chart'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <line
      x1="12"
      y1="20"
      x2="12"
      y2="10"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <line
      x1="18"
      y1="20"
      x2="18"
      y2="4"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <line
      x1="6"
      y1="20"
      x2="6"
      y2="16"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'dumbbell'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <path
      d="M6 7.5h12M6 16.5h12M18 5v14M6 5v14M3 9v6M21 9v6"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'notebook'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <rect
      x="5"
      y="2"
      width="14"
      height="20"
      rx="2"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <path
      d="M9 2v20M5 7h4M5 12h4M5 17h4"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'scale'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <path
      d="M12 3v18M12 7l10 3M12 7L2 10M22 10s-2 6-5 6-5-6-5-6M7 16s-2-6-5-6"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'airplane'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <path
      d="M22 2L11 13M22 2l-7 20-4-9-9-4 20-7z"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'paw'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <circle cx="12" cy="13" r="5" stroke="currentColor" stroke-width="2" />
    <circle cx="7" cy="6" r="2.5" stroke="currentColor" stroke-width="2" />
    <circle cx="17" cy="6" r="2.5" stroke="currentColor" stroke-width="2" />
    <circle cx="4" cy="11" r="2" stroke="currentColor" stroke-width="2" />
    <circle cx="20" cy="11" r="2" stroke="currentColor" stroke-width="2" />
  </svg>

  <svg
    v-else-if="name === 'flask'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <path
      d="M9 3h6M10 3v6l-4 11a2 2 0 0 0 2 2h8a2 2 0 0 0 2-2l-4-11V3"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <path
      d="M8.5 15h7"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'brain'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <path
      d="M9.5 2a4.5 4.5 0 0 1 4.5 4.5c0 .9-.25 1.75-.7 2.5a4.5 4.5 0 1 1-6.6 6.6c-.75.45-1.6.7-2.5.7a4.5 4.5 0 0 1 0-9c.9 0 1.75.25 2.5.7.45-.45 1.3-.7 2.2-.7a4.5 4.5 0 0 1 0 9"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <path
      d="M12 12h.01"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'heart'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <path
      d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'plant'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <path
      d="M7 22h10M12 22V10M12 10c4 0 7-3 7-7s-3 1-7 7M12 10c-4 0-7-3-7-7s3 1 7 7"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <rect x="8" y="18" width="8" height="4" rx="1" stroke="currentColor" stroke-width="2" />
  </svg>

  <svg
    v-else-if="name === 'trash'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <path
      d="M3 6H5H21M8 6V4C8 3.44772 8.44772 3 9 3H15C15.5523 3 16 3.44772 16 4V6M19 6V20C19 20.5523 18.5523 21 18 21H6C5.44772 21 5 20.5523 5 20V6H19Z"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'upload'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <path
      d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-8l-4-4m0 0L8 8m4-4v12"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'file'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <path
      d="M7 3h6l4 4v10a2 2 0 01-2 2H7a2 2 0 01-2-2V5a2 2 0 012-2z"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'user'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <path
      d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <circle
      cx="12"
      cy="7"
      r="4"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'robot'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <path
      d="M12 2v4M8 12v.01M16 12v.01M5 8h14a2 2 0 012 2v8a2 2 0 01-2 2H5a2 2 0 01-2-2v-8a2 2 0 012-2zM2 14h3M19 14h3M9 18h6"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'copy'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <rect
      x="9"
      y="9"
      width="13"
      height="13"
      rx="2"
      ry="2"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <path
      d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'rotate-cw'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <path
      d="M23 4v6h-6"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <path
      d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'chevron-left'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <path
      d="M15 18l-6-6 6-6"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'chevron-right'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <path
      d="M9 18l6-6-6-6"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'chevron-down'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <path
      d="M6 9l6 6 6-6"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'chevron-up'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <path
      d="M18 15l-6-6-6 6"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'check'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <path
      d="M20 6L9 17l-5-5"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'refresh-cw'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <polyline points="23 4 23 10 17 10" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"></polyline>
    <polyline points="1 20 1 14 7 14" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"></polyline>
    <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"></path>
  </svg>

  <svg
    v-else-if="name === 'refresh-single'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <path d="M21 12a9 9 0 1 1-9-9c2.52 0 4.93 1 6.74 2.74L21 8" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"></path>
    <path d="M21 3v5h-5" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"></path>
  </svg>

  <svg
    v-else-if="name === 'more-horizontal'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <circle
      cx="12"
      cy="12"
      r="1"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <circle
      cx="19"
      cy="12"
      r="1"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <circle
      cx="5"
      cy="12"
      r="1"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'paperclip'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <path
      d="M21.44 11.05l-9.19 9.19a6 6 0 0 1-8.49-8.49l9.19-9.19a4 4 0 0 1 5.66 5.66l-9.2 9.19a2 2 0 0 1-2.83-2.83l8.49-8.48"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'close'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <path
      d="M18 6L6 18M6 6l12 12"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'word'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <path
      d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <path
      d="M14 2v6h6M9 15l2 2 4-4"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'pdf'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <path
      d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <path
      d="M14 2v6h6M7 13h10M7 17h10"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'markdown'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <rect x="3" y="5" width="18" height="14" rx="2" stroke="currentColor" stroke-width="2" />
    <path
      d="M7 15V9l2 2 2-2v6M14 13l2 2 2-2M16 9v6"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'txt'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <path
      d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <path
      d="M14 2v6h6M8 13h8M8 17h8"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'clock'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="2" />
    <polyline
      points="12 6 12 12 16 14"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'home'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <path
      d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <polyline
      points="9 22 9 12 15 12 15 22"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'settings'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <circle cx="12" cy="12" r="3" stroke="currentColor" stroke-width="2" />
    <path
      d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'star'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <polygon
      points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'cpu'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <rect x="4" y="4" width="16" height="14" rx="2" stroke="currentColor" stroke-width="2" />
    <path d="M9 9h6v6H9z" stroke="currentColor" stroke-width="2" />
    <path
      d="M9 1v3M15 1v3M9 20v3M15 20v3M20 9h3M20 14h3M1 9h3M1 14h3"
      stroke="currentColor"
      stroke-width="2"
    />
  </svg>

  <svg
    v-else-if="name === 'database'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <ellipse cx="12" cy="5" rx="9" ry="3" stroke="currentColor" stroke-width="2" />
    <path d="M21 12c0 1.66-4 3-9 3s-9-1.34-9-3" stroke="currentColor" stroke-width="2" />
    <path d="M3 5v14c0 1.66 4 3 9 3s9-1.34 9-3V5" stroke="currentColor" stroke-width="2" />
  </svg>

  <svg
    v-else-if="name === 'cloud'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <path
      d="M18 10h-1.26A8 8 0 1 0 9 20h9a5 5 0 0 0 0-10z"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'globe'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="2" />
    <line x1="2" y1="12" x2="22" y2="12" stroke="currentColor" stroke-width="2" />
    <path
      d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"
      stroke="currentColor"
      stroke-width="2"
    />
  </svg>

  <svg
    v-else-if="name === 'layers'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <polygon
      points="12 2 2 7 12 12 22 7 12 2"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <polyline
      points="2 12 12 17 22 12"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <polyline
      points="2 17 12 22 22 17"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'grid'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <rect x="3" y="3" width="7" height="7" rx="1" stroke="currentColor" stroke-width="2" />
    <rect x="14" y="3" width="7" height="7" rx="1" stroke="currentColor" stroke-width="2" />
    <rect x="3" y="14" width="7" height="7" rx="1" stroke="currentColor" stroke-width="2" />
    <rect x="14" y="14" width="7" height="7" rx="1" stroke="currentColor" stroke-width="2" />
  </svg>

  <svg
    v-else-if="name === 'layout'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <rect x="3" y="3" width="18" height="18" rx="2" ry="2" stroke="currentColor" stroke-width="2" />
    <line x1="3" y1="9" x2="21" y2="9" stroke="currentColor" stroke-width="2" />
    <line x1="9" y1="21" x2="9" y2="9" stroke="currentColor" stroke-width="2" />
  </svg>

  <svg
    v-else-if="name === 'zap'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <polygon
      points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'shield'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <path
      d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'target'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="2" />
    <circle cx="12" cy="12" r="6" stroke="currentColor" stroke-width="2" />
    <circle cx="12" cy="12" r="2" stroke="currentColor" stroke-width="2" />
  </svg>

  <svg
    v-else-if="name === 'help-circle'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="2" />
    <path
      d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <line
      x1="12"
      y1="17"
      x2="12.01"
      y2="17"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'info'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="2" />
    <line
      x1="12"
      y1="16"
      x2="12"
      y2="12"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <line
      x1="12"
      y1="8"
      x2="12.01"
      y2="8"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'alert-circle'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="2" />
    <line
      x1="12"
      y1="8"
      x2="12"
      y2="12"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <line
      x1="12"
      y1="16"
      x2="12.01"
      y2="16"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'bell'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <path
      d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <path
      d="M13.73 21a2 2 0 0 1-3.46 0"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'mail'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <path
      d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <polyline
      points="22,6 12,13 2,6"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'calendar'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <rect
      x="3"
      y="4"
      width="18"
      height="18"
      rx="2"
      ry="2"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <line
      x1="16"
      y1="2"
      x2="16"
      y2="6"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <line
      x1="8"
      y1="2"
      x2="8"
      y2="6"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <line
      x1="3"
      y1="10"
      x2="21"
      y2="10"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'camera'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <path
      d="M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <circle
      cx="12"
      cy="13"
      r="4"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'video'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <polygon
      points="23 7 16 12 23 17 23 7"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <rect
      x="1"
      y="5"
      width="15"
      height="14"
      rx="2"
      ry="2"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'image'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <rect
      x="3"
      y="3"
      width="18"
      height="18"
      rx="2"
      ry="2"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <circle
      cx="8.5"
      cy="8.5"
      r="1.5"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <polyline
      points="21 15 16 10 5 21"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'maximize'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <path
      d="M8 3H5a2 2 0 0 0-2 2v3m18 0V5a2 2 0 0 0-2-2h-3m0 18h3a2 2 0 0 0 2-2v-3M3 16v3a2 2 0 0 0 2 2h3"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'minimize'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <path
      d="M8 3v3a2 2 0 0 1-2 2H3m18 0h-3a2 2 0 0 1-2-2V3m0 18v-3a2 2 0 0 1 2-2h3M3 16h3a2 2 0 0 1 2 2v3"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'monitor'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <rect
      x="2"
      y="3"
      width="20"
      height="14"
      rx="2"
      ry="2"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <line
      x1="8"
      y1="21"
      x2="16"
      y2="21"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <line
      x1="12"
      y1="17"
      x2="12"
      y2="21"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'phone'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <path
      d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.79 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'speaker'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <rect
      x="4"
      y="2"
      width="16"
      height="20"
      rx="2"
      ry="2"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <circle
      cx="12"
      cy="14"
      r="4"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <line
      x1="12"
      y1="6"
      x2="12.01"
      y2="6"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'tag'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <path
      d="M20.59 13.41l-7.17 7.17a2 2 0 0 1-2.83 0L2 12V2h10l8.59 8.59a2 2 0 0 1 0 2.82z"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <line
      x1="7"
      y1="7"
      x2="7.01"
      y2="7"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'tool'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <path
      d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'zap'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <polygon
      points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'download'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <path
      d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <polyline
      points="7 10 12 15 17 10"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <line
      x1="12"
      y1="15"
      x2="12"
      y2="3"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'dollar'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="2" />
    <path
      d="M12 6v12M15 9H10.5a2.25 2.25 0 1 0 0 4.5h3a2.25 2.25 0 1 1 0 4.5H9"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'graduation'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <path
      d="M22 10L12 5L2 10L12 15L22 10Z"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <path
      d="M6 12V17C6 17 9 20 12 20C15 20 18 17 18 17V12"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'pen-tool'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <path
      d="M12 19l7-7 3 3-7 7-3-3z"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <path
      d="M18 13l-1.5-7.5L2 2l3.5 14.5L13 18l5-5z"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <path
      d="M2 2l7.586 7.586"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <circle
      cx="11"
      cy="11"
      r="2"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'popcorn'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <path
      d="M18 8a2 2 0 0 0 0-4 2 2 0 0 0-4 0 2 2 0 0 0-4 0 2 2 0 0 0-4 0 2 2 0 0 0 0 4"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <path
      d="M10 22L7 8h10l-3 14"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <path
      d="M10 8l1 14M14 8l-1 14"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'palette'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <path
      d="M12 22C17.5228 22 22 17.5228 22 12C22 6.47715 17.5228 2 12 2C6.47715 2 2 6.47715 2 12C2 17.5228 6.47715 22 12 22Z"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <path
      d="M6 12C6 12 7 11 8 11C9 11 10 12 10 12"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <circle cx="7.5" cy="16.5" r="0.5" fill="currentColor" />
    <circle cx="12" cy="19" r="0.5" fill="currentColor" />
    <circle cx="16.5" cy="16.5" r="0.5" fill="currentColor" />
  </svg>

  <svg
    v-else-if="name === 'stethoscope'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <path
      d="M4.8 23h4.4M7 23v-5M7 11c0-3.314 2.686-6 6-6s6 2.686 6 6v3a3 3 0 0 1-3 3H8a3 3 0 0 1-3-3v-3c0-3.314 2.686-6 6-6"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <circle cx="13" cy="5" r="3" stroke="currentColor" stroke-width="2" />
  </svg>

  <svg
    v-else-if="name === 'flower'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <circle cx="12" cy="12" r="3" stroke="currentColor" stroke-width="2" />
    <path
      d="M12 7c2-4 6-4 6 0s-4 6-6 6-6-2-6-6 4-4 6 0zM12 17c-2 4-6 4-6 0s4-6 6-6 6 2 6 6-4 4-6 0zM7 12c-4-2-4-6 0-6s6 4 6 6-2 6-6 6-4-4 0-6zM17 12c4 2 4 6 0 6s-6-4-6-6 2-6 6-6 4 4 0 6z"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'lotus'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <path
      d="M12 22s-8-3-8-10c0-4 4-8 8-8s8 4 8 8c0 7-8 10-8 10z"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <path
      d="M12 22s-5-2-5-7c0-3 2-6 5-6s5 3 5 6c0 5-5 7-5 7z"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'briefcase'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <rect
      x="2"
      y="7"
      width="20"
      height="14"
      rx="2"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <path
      d="M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'bar-chart'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <line
      x1="12"
      y1="20"
      x2="12"
      y2="10"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <line
      x1="18"
      y1="20"
      x2="18"
      y2="4"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <line
      x1="6"
      y1="20"
      x2="6"
      y2="16"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'dumbbell'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <path
      d="M6 7.5h12M6 16.5h12M18 5v14M6 5v14M3 9v6M21 9v6"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'notebook'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <rect
      x="5"
      y="2"
      width="14"
      height="20"
      rx="2"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <path
      d="M9 2v20M5 7h4M5 12h4M5 17h4"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'scale'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <path
      d="M12 3v18M12 7l10 3M12 7L2 10M22 10s-2 6-5 6-5-6-5-6M7 16s-2-6-5-6"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'airplane'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <path
      d="M22 2L11 13M22 2l-7 20-4-9-9-4 20-7z"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'paw'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <circle cx="12" cy="13" r="5" stroke="currentColor" stroke-width="2" />
    <circle cx="7" cy="6" r="2.5" stroke="currentColor" stroke-width="2" />
    <circle cx="17" cy="6" r="2.5" stroke="currentColor" stroke-width="2" />
    <circle cx="4" cy="11" r="2" stroke="currentColor" stroke-width="2" />
    <circle cx="20" cy="11" r="2" stroke="currentColor" stroke-width="2" />
  </svg>

  <svg
    v-else-if="name === 'flask'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <path
      d="M9 3h6M10 3v6l-4 11a2 2 0 0 0 2 2h8a2 2 0 0 0 2-2l-4-11V3"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <path
      d="M8.5 15h7"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'brain'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <path
      d="M9.5 2a4.5 4.5 0 0 1 4.5 4.5c0 .9-.25 1.75-.7 2.5a4.5 4.5 0 1 1-6.6 6.6c-.75.45-1.6.7-2.5.7a4.5 4.5 0 0 1 0-9c.9 0 1.75.25 2.5.7.45-.45 1.3-.7 2.2-.7a4.5 4.5 0 0 1 0 9"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <path
      d="M12 12h.01"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'heart'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <path
      d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>

  <svg
    v-else-if="name === 'plant'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <path
      d="M7 22h10M12 22V10M12 10c4 0 7-3 7-7s-3 1-7 7M12 10c-4 0-7-3-7-7s3 1 7 7"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <rect x="8" y="18" width="8" height="4" rx="1" stroke="currentColor" stroke-width="2" />
  </svg>

  <svg
    v-else-if="name === 'mind-topic'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <rect x="2" y="9" width="8" height="6" rx="1.5" stroke="currentColor" stroke-width="2" />
    <rect x="14" y="9" width="8" height="6" rx="1.5" stroke="currentColor" stroke-width="2" />
    <line x1="10" y1="12" x2="14" y2="12" stroke="currentColor" stroke-width="2" />
  </svg>

  <svg
    v-else-if="name === 'mind-subtopic'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <rect x="3" y="4" width="8" height="6" rx="1.5" stroke="currentColor" stroke-width="2" />
    <rect x="13" y="14" width="8" height="6" rx="1.5" stroke="currentColor" stroke-width="2" />
    <path d="M7 10v7h6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
  </svg>

  <svg
    v-else-if="name === 'mind-rel'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <path d="M4 16c4-6 12-6 16 0" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
    <polyline points="16 16 20 16 20 12" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
    <circle cx="4" cy="16" r="1.5" fill="currentColor" />
  </svg>

  <svg
    v-else-if="name === 'upload-cloud'"
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <path d="M16 16l-4-4-4 4" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
    <line x1="12" y1="12" x2="12" y2="21" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
    <path d="M20.39 18.39A5 5 0 0 0 18 9h-1.26A8 8 0 1 0 3 16.3" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
  </svg>

  <svg
    v-else
    class="icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :style="{ color: props.color }"
  >
    <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="2" />
    <line x1="12" y1="8" x2="12" y2="12" stroke="currentColor" stroke-width="2" />
    <line x1="12" y1="16" x2="12.01" y2="16" stroke="currentColor" stroke-width="2" />
  </svg>
</template>

<style scoped>
.icon {
  flex-shrink: 0;
  display: block;
}
</style>
