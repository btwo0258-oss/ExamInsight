import { ref } from "vue";

export const mockEnabled = ref(false);

export function enableMock(enabled: boolean = true) {
  mockEnabled.value = enabled;
}
