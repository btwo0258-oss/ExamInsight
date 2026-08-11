<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from "vue"
import { fetchAssetContent } from "@/api/assetLibraryV2"
import ResourceTypeIcon from "@/components/common/ResourceTypeIcon.vue"
import type { LibraryAsset } from "@/types/contracts/assetLibraryV2"

const props = defineProps<{
  asset: LibraryAsset
}>()

const root = ref<HTMLElement | null>(null)
const source = ref<string | null>(null)
const state = ref<"idle" | "loading" | "ready" | "error">("idle")

let observer: IntersectionObserver | null = null
let objectUrl: string | null = null

async function loadThumbnail() {
  if (state.value !== "idle") return
  state.value = "loading"
  try {
    const blob = await fetchAssetContent(props.asset.assetId, "inline")
    if (!blob.type.startsWith("image/")) throw new Error("Asset is not an image")
    objectUrl = URL.createObjectURL(blob)
    source.value = objectUrl
    state.value = "ready"
  } catch {
    state.value = "error"
  }
}

onMounted(() => {
  if (!root.value || typeof IntersectionObserver === "undefined") {
    void loadThumbnail()
    return
  }
  observer = new IntersectionObserver(
    (entries) => {
      if (!entries.some((entry) => entry.isIntersecting)) return
      observer?.disconnect()
      observer = null
      void loadThumbnail()
    },
    { rootMargin: "160px" },
  )
  observer.observe(root.value)
})

onBeforeUnmount(() => {
  observer?.disconnect()
  if (objectUrl) URL.revokeObjectURL(objectUrl)
})
</script>

<template>
  <div ref="root" class="asset-thumbnail" :aria-busy="state === 'loading'">
    <img v-if="state === 'ready' && source" :src="source" :alt="asset.name" loading="lazy" />
    <div v-else-if="state === 'idle' || state === 'loading'" class="asset-thumbnail__skeleton" aria-hidden="true" />
    <ResourceTypeIcon v-else type="image" variant="plain" :size="42" />
  </div>
</template>

<style scoped>
.asset-thumbnail {
  width: 100%;
  height: 148px;
  display: grid;
  place-items: center;
  overflow: hidden;
  border-radius: 12px;
  background: var(--color-hover);
}

.asset-thumbnail img {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: cover;
}

.asset-thumbnail__skeleton {
  width: 100%;
  height: 100%;
  background: linear-gradient(
    100deg,
    var(--color-hover) 20%,
    var(--color-hover-strong) 38%,
    var(--color-hover) 56%
  );
  background-size: 220% 100%;
  animation: asset-thumbnail-loading 1.2s ease-in-out infinite;
}

@keyframes asset-thumbnail-loading {
  to { background-position-x: -220%; }
}

@media (prefers-reduced-motion: reduce) {
  .asset-thumbnail__skeleton { animation: none; }
}
</style>
