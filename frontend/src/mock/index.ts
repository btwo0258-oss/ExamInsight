import { ref } from "vue";
export * from "./student";

export const mockEnabled = ref(false);

export function enableMock(enabled: boolean = true) {
  mockEnabled.value = enabled;
}
