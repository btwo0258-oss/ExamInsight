<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRouter } from "vue-router";
import AppButton from "@/components/common/AppButton.vue";
import AppIcon from "@/components/common/AppIcon.vue";
import AppModal from "@/components/common/AppModal.vue";
import ConfirmDialog from "@/components/common/ConfirmDialog.vue";
import ResourceTypeIcon from "@/components/common/ResourceTypeIcon.vue";
import StudentShell from "@/components/layout/StudentShell.vue";
import UploadMaterialModal from "@/components/library/UploadMaterialModal.vue";
import V2AssetThumbnail from "@/components/library/V2AssetThumbnail.vue";
import V2KnowledgeBaseModal from "@/components/library/V2KnowledgeBaseModal.vue";
import {
  addAssetToKnowledgeBase,
  fetchAssetContent,
  getAssetPurgeJob,
  retryAssetProcessing,
} from "@/api/assetLibraryV2";
import { useAssetLibraryV2Store } from "@/stores/assetLibraryV2";
import type {
  KnowledgeBase as V2KnowledgeBase,
  LibraryAsset as V2LibraryAsset,
} from "@/types/contracts/assetLibraryV2";
import { downloadBlob } from "@/utils/download";

type LibraryFilter = "all" | "knowledge" | "image" | "file";
type LibraryLocation = "library" | "trash";
type ViewMode = "grid" | "list";
type SourceFilter = "uploaded" | "generated";
type AdvancedFileType = "image" | "document" | "spreadsheet" | "presentation" | "pdf";

type KnowledgeItem = {
  id: string;
  name: string;
  description: string | null;
  updateTime: string;
  assetCount: number;
  raw: V2KnowledgeBase;
};

type LibraryFile = {
  resourceId: string;
  name: string;
  fileType: AdvancedFileType | "mindmap";
  format: string;
  sourceType: SourceFilter;
  sizeBytes: number;
  mimeType: string;
  updatedAt: string;
  raw: V2LibraryAsset;
};

type LibraryAsset =
  | { kind: "knowledge"; id: string; source: KnowledgeItem }
  | { kind: "file"; id: string; source: LibraryFile };

const router = useRouter();
const store = useAssetLibraryV2Store();

const uploadOpen = ref(false);
const newKnowledgeOpen = ref(false);
const editingKnowledgeBase = ref<V2KnowledgeBase | null>(null);
const renameAssetTarget = ref<V2LibraryAsset | null>(null);
const renameValue = ref("");
const newMenuOpen = ref(false);
const activeFilter = ref<LibraryFilter>("all");
const libraryLocation = ref<LibraryLocation>("library");
const viewMode = ref<ViewMode>(
  localStorage.getItem("examinsight.ui.library-view") === "list" ? "list" : "grid",
);
const searchQuery = ref("");
const selectedIds = ref<string[]>([]);
const knowledgeMenuId = ref<string | null>(null);
const fileMenuId = ref<string | null>(null);
const filterMenuOpen = ref(false);
const sourceFilter = ref<SourceFilter | null>(null);
const fileTypeFilter = ref<AdvancedFileType | null>(null);
const moveModalOpen = ref(false);
const moveResourceIds = ref<string[]>([]);
const moveTargetKnowledgeBaseId = ref<string | null>(null);
const deleteTargets = ref<LibraryAsset[]>([]);
const purgeTargets = ref<LibraryAsset[]>([]);
const actionError = ref("");
const notice = ref("");
const purgingAssetIds = ref<string[]>([]);
let refreshTimer: number | undefined;

const filters: Array<{ label: string; value: LibraryFilter }> = [
  { label: "全部", value: "all" },
  { label: "知识库", value: "knowledge" },
  { label: "图片", value: "image" },
  { label: "文件", value: "file" },
];

const sourceFilterOptions: Array<{ value: SourceFilter; label: string; icon: string }> = [
  { value: "uploaded", label: "已上传", icon: "upload-cloud" },
  { value: "generated", label: "已生成", icon: "sparkle" },
];

const fileTypeFilterOptions: Array<{ value: AdvancedFileType; label: string; icon: string }> = [
  { value: "image", label: "图片", icon: "image" },
  { value: "document", label: "文档", icon: "file" },
  { value: "spreadsheet", label: "电子表格", icon: "grid" },
  { value: "presentation", label: "演示文稿", icon: "presentation" },
  { value: "pdf", label: "PDF", icon: "file" },
];

const isTrashView = computed(() => libraryLocation.value === "trash");

function extension(name: string) {
  return name.split(".").pop()?.toLocaleLowerCase() || "";
}

function fileType(asset: V2LibraryAsset): LibraryFile["fileType"] {
  const ext = extension(asset.name);
  const mime = asset.version?.mimeType?.toLocaleLowerCase() || "";
  if (asset.assetType === "MINDMAP") return "mindmap";
  if (mime.startsWith("image/") || ["jpg", "jpeg", "png", "webp"].includes(ext)) return "image";
  if (mime === "application/pdf" || ext === "pdf") return "pdf";
  if (["xlsx", "xls", "csv"].includes(ext)) return "spreadsheet";
  if (["pptx", "ppt"].includes(ext)) return "presentation";
  return "document";
}

function toKnowledgeItem(item: V2KnowledgeBase): KnowledgeItem {
  return {
    id: item.knowledgeBaseId,
    name: item.name,
    description: item.description,
    updateTime: item.updatedAt,
    assetCount: item.assetCount,
    raw: item,
  };
}

function toLibraryFile(asset: V2LibraryAsset): LibraryFile {
  const ext = extension(asset.name);
  return {
    resourceId: asset.assetId,
    name: asset.name,
    fileType: fileType(asset),
    format: ext ? ext.toLocaleUpperCase() : "文件",
    sourceType: asset.sourceType === "AI_GENERATED" ? "generated" : "uploaded",
    sizeBytes: asset.version?.sizeBytes || 0,
    mimeType: asset.version?.mimeType || "",
    updatedAt: asset.updatedAt,
    raw: asset,
  };
}

const sourceKnowledgeBases = computed(() =>
  (isTrashView.value ? store.trashedKnowledgeBases : store.knowledgeBases).map(toKnowledgeItem),
);
const sourceFiles = computed(() =>
  (isTrashView.value ? store.trashedAssets : store.assets).map(toLibraryFile),
);

const knowledgeAssets = computed<LibraryAsset[]>(() =>
  sourceKnowledgeBases.value.map((source) => ({
    kind: "knowledge",
    id: "knowledge-" + source.id,
    source,
  })),
);

const fileAssets = computed<LibraryAsset[]>(() =>
  sourceFiles.value.map((source) => ({
    kind: "file",
    id: "file-" + source.resourceId,
    source,
  })),
);

const visibleAssets = computed(() => {
  let assets: LibraryAsset[];
  if (activeFilter.value === "knowledge") {
    assets = knowledgeAssets.value;
  } else if (activeFilter.value === "image") {
    assets = fileAssets.value.filter(
      (asset) => asset.kind === "file" && asset.source.fileType === "image",
    );
  } else if (activeFilter.value === "file") {
    assets = fileAssets.value.filter(
      (asset) => asset.kind === "file" && asset.source.fileType !== "image",
    );
  } else {
    assets = [...knowledgeAssets.value, ...fileAssets.value];
  }

  if (sourceFilter.value || fileTypeFilter.value) {
    assets = assets.filter(
      (asset) =>
        asset.kind === "file" &&
        (!sourceFilter.value || asset.source.sourceType === sourceFilter.value) &&
        (!fileTypeFilter.value || asset.source.fileType === fileTypeFilter.value),
    );
  }

  const query = searchQuery.value.trim().toLocaleLowerCase();
  if (!query) return assets;
  return assets.filter((asset) => {
    const text = asset.kind === "knowledge"
      ? asset.source.name + " " + (asset.source.description || "")
      : asset.source.name + " " + asset.source.format + " " + sourceLabel(asset.source);
    return text.toLocaleLowerCase().includes(query);
  });
});
const visibleKnowledgeAssets = computed(() => visibleAssets.value.filter((asset) => asset.kind === "knowledge"));
const visibleFileAssets = computed(() => visibleAssets.value.filter((asset) => asset.kind === "file"));

const pageError = computed(() => actionError.value || store.error || "");
const pageLoading = computed(() => store.loading);
const selectedAssets = computed(() =>
  [...knowledgeAssets.value, ...fileAssets.value].filter((asset) =>
    selectedIds.value.includes(asset.id),
  ),
);
const firstFileAfterKnowledgeId = computed(() => {
  if (!visibleAssets.value.some((asset) => asset.kind === "knowledge")) return null;
  return visibleAssets.value.find((asset) => asset.kind === "file")?.id || null;
});
const selectedCount = computed(() => selectedIds.value.length);
const hasSelection = computed(() => selectedCount.value > 0);
const allVisibleSelected = computed(() =>
  visibleAssets.value.length > 0
  && visibleAssets.value.every((asset) => selectedIds.value.includes(asset.id)),
);
const someVisibleSelected = computed(() =>
  visibleAssets.value.some((asset) => selectedIds.value.includes(asset.id)),
);
const activeAdvancedFilterCount = computed(
  () => Number(Boolean(sourceFilter.value)) + Number(Boolean(fileTypeFilter.value)),
);
const activeFilterCount = computed(
  () => activeAdvancedFilterCount.value + Number(isTrashView.value),
);
const availableKnowledgeBases = computed(() => store.knowledgeBases.map(toKnowledgeItem));
const hasMore = computed(() =>
  isTrashView.value
    ? Boolean(store.trashAssetCursor || store.trashKnowledgeBaseCursor)
    : Boolean(store.assetCursor || store.knowledgeBaseCursor),
);

function openNewMenu() {
  newMenuOpen.value = !newMenuOpen.value;
}

function openUpload() {
  uploadOpen.value = true;
  newMenuOpen.value = false;
}

function openNewKnowledge() {
  editingKnowledgeBase.value = null;
  newKnowledgeOpen.value = true;
  newMenuOpen.value = false;
}

function handleKnowledgeSaved() {
  activeFilter.value = "all";
  newKnowledgeOpen.value = false;
  notice.value = "知识库已保存。";
}

function handleUploaded() {
  notice.value = "资料已上传，后台会继续安全检查、解析和索引。";
  void store.loadAssets("library");
}

function toggleSelection(id: string) {
  selectedIds.value = selectedIds.value.includes(id)
    ? selectedIds.value.filter((item) => item !== id)
    : [...selectedIds.value, id];
}

function toggleAllVisible() {
  const visibleIds = new Set(visibleAssets.value.map((asset) => asset.id));
  selectedIds.value = allVisibleSelected.value
    ? selectedIds.value.filter((id) => !visibleIds.has(id))
    : [...new Set([...selectedIds.value, ...visibleIds])];
}

function clearSelection() {
  selectedIds.value = [];
}

function isSelected(id: string) {
  return selectedIds.value.includes(id);
}

function toggleKnowledgeMenu(id: string) {
  knowledgeMenuId.value = knowledgeMenuId.value === id ? null : id;
  fileMenuId.value = null;
}

function closeKnowledgeMenu() {
  knowledgeMenuId.value = null;
}

function toggleFileMenu(id: string) {
  fileMenuId.value = fileMenuId.value === id ? null : id;
  knowledgeMenuId.value = null;
}

function closeFileMenu() {
  fileMenuId.value = null;
}

function openMoveModal(resourceIds?: string[]) {
  const ids = resourceIds || selectedAssets.value
    .filter((asset) => asset.kind === "file")
    .map((asset) => asset.source.resourceId);
  if (!ids.length) return;
  moveResourceIds.value = ids;
  moveTargetKnowledgeBaseId.value = null;
  moveModalOpen.value = true;
  closeFileMenu();
}

function openKnowledgeFromMove() {
  moveModalOpen.value = false;
  openNewKnowledge();
}

function fileSize(file: LibraryFile) {
  if (!file.sizeBytes) return "—";
  if (file.sizeBytes < 1024 * 1024) {
    return Math.max(1, Math.round(file.sizeBytes / 1024)) + " KB";
  }
  return (file.sizeBytes / 1024 / 1024).toFixed(1) + " MB";
}

function fileVisualType(file: LibraryFile) {
  return file.fileType;
}

function sourceLabel(file: LibraryFile) {
  return file.sourceType === "generated" ? "AI 生成" : "资料库上传";
}

function assetStatus(asset: V2LibraryAsset) {
  const version = asset.version;
  if (!version || version.status === "QUARANTINED") return { label: "安全检查中", tone: "pending" };
  if (version.status === "PROCESSING") return { label: "解析中", tone: "pending" };
  if (version.status === "REJECTED") return { label: "安全检查未通过", tone: "error" };
  if (version.status === "WITHDRAWN") return { label: "已撤回", tone: "neutral" };
  if (version.status === "FAILED") {
    return fileType(asset) === "image"
      ? { label: "识别失败", tone: "error" }
      : { label: "解析失败", tone: "error" };
  }
  if (["READY", "KEYWORD_ONLY"].includes(version.indexStatus ?? "")) {
    return { label: "", tone: "neutral" };
  }
  if (version.indexStatus === "EMPTY") return { label: "无可索引文本", tone: "neutral" };
  if (version.indexStatus === "DEGRADED") return { label: "部分索引失败", tone: "error" };
  return { label: "向量化中", tone: "pending" };
}

function canRetryAsset(asset: V2LibraryAsset) {
  return asset.version?.status === "FAILED" || asset.version?.indexStatus === "DEGRADED";
}

async function retryProcessing(asset: V2LibraryAsset) {
  actionError.value = "";
  notice.value = "";
  closeFileMenu();
  try {
    const detail = await retryAssetProcessing(asset.assetId);
    store.upsertUploadedAsset(detail.asset);
    notice.value = "已重新提交处理，可继续浏览资料库。";
  } catch (error) {
    actionError.value = error instanceof Error ? error.message : "重新处理资料失败";
  }
}

function isReadable(asset: V2LibraryAsset) {
  return ["PROCESSING", "READY", "FAILED"].includes(asset.version?.status ?? "");
}

function toggleSourceFilter(value: SourceFilter) {
  sourceFilter.value = sourceFilter.value === value ? null : value;
}

function toggleFileTypeFilter(value: AdvancedFileType) {
  fileTypeFilter.value = fileTypeFilter.value === value ? null : value;
}

function openAsset(asset: LibraryAsset) {
  if (isTrashView.value) return;
  if (asset.kind === "knowledge") {
    void router.push("/library/" + asset.source.id);
    return;
  }
  if (isReadable(asset.source.raw)) {
    void router.push({
      path: `/resources/${asset.source.resourceId}/preview`,
      query: { source: "library-v2", returnTo: "/library" },
    });
  }
}

function startLearning(knowledgeBaseId: string) {
  closeKnowledgeMenu();
  void router.push({ path: "/learning/new", query: { knowledgeBaseId } });
}

function startChatWithSelection() {
  actionError.value = "";
  const knowledgeBases = selectedAssets.value.filter((asset) => asset.kind === "knowledge");
  const files = selectedAssets.value.filter((asset) => asset.kind === "file");
  if (knowledgeBases.length > 1) {
    actionError.value = "一次对话最多关联 1 个知识库。";
    return;
  }
  if (files.length > 20) {
    actionError.value = "一次对话最多直接关联 20 个资料。";
    return;
  }
  const unready = files.find((asset) => asset.kind === "file" && !isReadable(asset.source.raw));
  if (unready) {
    actionError.value = `“${assetName(unready)}”仍在处理中，请完成后再开始聊天。`;
    return;
  }
  const knowledgeBaseId = knowledgeBases[0]?.kind === "knowledge"
    ? knowledgeBases[0].source.id
    : undefined;
  const sourceAssetIds = files
    .filter((asset): asset is Extract<LibraryAsset, { kind: "file" }> => asset.kind === "file")
    .map((asset) => asset.source.resourceId)
    .join(",");
  void router.push({
    path: "/chat",
    query: {
      knowledgeBaseId,
      sourceAssetIds: sourceAssetIds || undefined,
    },
  });
}

async function loadData() {
  actionError.value = "";
  try {
    await store.refresh(isTrashView.value ? "trash" : "library");
  } catch {
    // Store error is rendered by the page.
  }
}

async function loadMore() {
  actionError.value = "";
  try {
    if (isTrashView.value) {
      await Promise.all([
        store.trashAssetCursor ? store.loadAssets("trash", true) : Promise.resolve(),
        store.trashKnowledgeBaseCursor ? store.loadKnowledgeBases("trash", true) : Promise.resolve(),
      ]);
    } else {
      await Promise.all([
        store.assetCursor ? store.loadAssets("library", true) : Promise.resolve(),
        store.knowledgeBaseCursor ? store.loadKnowledgeBases("library", true) : Promise.resolve(),
      ]);
    }
  } catch (error) {
    actionError.value = error instanceof Error ? error.message : "加载更多失败";
  }
}

function renameKnowledge(item: KnowledgeItem) {
  closeKnowledgeMenu();
  editingKnowledgeBase.value = item.raw;
  newKnowledgeOpen.value = true;
}

function renameFile(file: LibraryFile) {
  closeFileMenu();
  renameAssetTarget.value = file.raw;
  renameValue.value = file.name;
}

function renameAsset(asset: LibraryAsset) {
  if (asset.kind === "knowledge") renameKnowledge(asset.source);
  else renameFile(asset.source);
}

async function saveAssetName() {
  const target = renameAssetTarget.value;
  const name = renameValue.value.trim();
  if (!target || !name) return;
  try {
    await store.renameAsset(target.assetId, name);
    renameAssetTarget.value = null;
    notice.value = "资料名称已更新。";
  } catch (error) {
    actionError.value = error instanceof Error ? error.message : "重命名失败";
  }
}

function startLearningFromAsset(asset: LibraryAsset) {
  if (asset.kind === "knowledge") startLearning(asset.source.id);
}

function openMoveForAsset(asset: LibraryAsset) {
  if (asset.kind === "file") openMoveModal([asset.source.resourceId]);
}

async function downloadFiles(assets: LibraryAsset[]) {
  actionError.value = "";
  try {
    for (const asset of assets) {
      if (asset.kind !== "file") continue;
      const blob = await fetchAssetContent(asset.source.resourceId, "attachment");
      downloadBlob(blob, asset.source.name);
    }
  } catch (error) {
    actionError.value = error instanceof Error ? error.message : "下载失败";
  }
}

function requestDelete(assets: LibraryAsset[]) {
  if (!assets.length) return;
  deleteTargets.value = assets;
  closeKnowledgeMenu();
  closeFileMenu();
}

async function confirmDelete() {
  const targets = [...deleteTargets.value];
  deleteTargets.value = [];
  actionError.value = "";
  try {
    for (const asset of targets) {
      if (asset.kind === "knowledge") {
        await store.moveKnowledgeBaseToTrash(asset.source.id);
      } else {
        await store.moveAssetToTrash(asset.source.resourceId);
      }
    }
    selectedIds.value = selectedIds.value.filter(
      (id) => !targets.some((asset) => asset.id === id),
    );
    notice.value = targets.length + " 个项目已移入回收站。";
  } catch (error) {
    actionError.value = error instanceof Error ? error.message : "移入回收站失败";
  }
}

async function moveSelectedResources() {
  if (!moveResourceIds.value.length || !moveTargetKnowledgeBaseId.value) return;
  actionError.value = "";
  try {
    for (const resourceId of moveResourceIds.value) {
      await addAssetToKnowledgeBase(moveTargetKnowledgeBaseId.value, resourceId);
    }
    await Promise.all([store.loadAssets("library"), store.loadKnowledgeBases("library")]);
    moveModalOpen.value = false;
    clearSelection();
    notice.value = "已加入知识库，个人资料库中的原文件仍然保留。";
  } catch (error) {
    actionError.value = error instanceof Error ? error.message : "加入知识库失败";
  }
}

async function restoreAsset(asset: LibraryAsset) {
  if (asset.kind === "knowledge") await store.restoreKnowledgeBase(asset.source.id);
  else await store.restoreAsset(asset.source.resourceId);
  selectedIds.value = selectedIds.value.filter((id) => id !== asset.id);
  notice.value = "已恢复“" + assetName(asset) + "”。";
}

async function restoreSelected() {
  const targets = [...selectedAssets.value];
  if (!targets.length) return;
  actionError.value = "";
  try {
    for (const asset of targets) await restoreAsset(asset);
    clearSelection();
    notice.value = targets.length + " 个项目已恢复。";
  } catch (error) {
    actionError.value = error instanceof Error ? error.message : "恢复失败";
  }
}

async function waitForPurge(asset: LibraryFile) {
  purgingAssetIds.value = [...purgingAssetIds.value, asset.resourceId];
  try {
    for (let attempt = 0; attempt < 40; attempt += 1) {
      await new Promise((resolve) => window.setTimeout(resolve, 1500));
      const job = await getAssetPurgeJob(asset.resourceId);
      if (job.status === "SUCCEEDED") {
        store.forgetPurgedAsset(asset.resourceId);
        notice.value = "已彻底删除“" + asset.name + "”。";
        return;
      }
      if (["FAILED", "CANCELLED"].includes(job.status)) {
        actionError.value = job.errorCode === "ASSET_VERSION_IN_USE"
          ? "该资料已被学习项目或对话引用，暂时不能彻底删除。"
          : "彻底删除失败，资料仍保留在回收站。";
        return;
      }
    }
    notice.value = "删除任务仍在后台执行，可稍后刷新回收站。";
  } catch (error) {
    actionError.value = error instanceof Error ? error.message : "获取删除进度失败";
  } finally {
    purgingAssetIds.value = purgingAssetIds.value.filter((id) => id !== asset.resourceId);
  }
}

async function confirmPurge() {
  const targets = [...purgeTargets.value];
  purgeTargets.value = [];
  if (!targets.length) return;
  actionError.value = "";
  try {
    for (const target of targets) {
      if (target.kind === "knowledge") {
        await store.permanentlyDeleteKnowledgeBase(target.source.id);
      } else {
        await store.requestAssetPurge(target.source.resourceId);
        void waitForPurge(target.source);
      }
    }
    clearSelection();
    notice.value = targets.some((target) => target.kind === "file")
      ? "永久删除任务已提交，文件会在后台清理。"
      : "知识库已彻底删除，个人资料原文件仍然保留。";
  } catch (error) {
    actionError.value = error instanceof Error ? error.message : "彻底删除失败";
  }
}

function knowledgeTitle(item: KnowledgeItem) {
  return item.name;
}

function knowledgeFileCount(item: KnowledgeItem) {
  return item.assetCount;
}

function formatDate(value: string | null) {
  if (!value) return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "—";
  const today = new Date();
  if (date.toDateString() === today.toDateString()) {
    return new Intl.DateTimeFormat("zh-CN", { hour: "2-digit", minute: "2-digit" }).format(date);
  }
  return new Intl.DateTimeFormat("zh-CN", { month: "numeric", day: "numeric" }).format(date);
}

function knowledgeUpdatedAt(item: KnowledgeItem) {
  return formatDate(item.updateTime);
}

function assetModifiedAt(asset: LibraryAsset) {
  return asset.kind === "knowledge"
    ? knowledgeUpdatedAt(asset.source)
    : formatDate(asset.source.updatedAt);
}

function assetSize(asset: LibraryAsset) {
  return asset.kind === "knowledge" ? "—" : fileSize(asset.source);
}

function assetName(asset: LibraryAsset) {
  return asset.kind === "knowledge" ? asset.source.name : asset.source.name;
}

function knowledgeDescription(asset: LibraryAsset) {
  if (asset.kind !== "knowledge") return "";
  return asset.source.description || "整理相关资料，供检索和智能学习使用。";
}

function openTrash() {
  libraryLocation.value = isTrashView.value ? "library" : "trash";
  activeFilter.value = "all";
  filterMenuOpen.value = false;
}

function leaveTrash() {
  libraryLocation.value = "library";
  activeFilter.value = "all";
}

function closeMenus() {
  newMenuOpen.value = false;
  filterMenuOpen.value = false;
  closeKnowledgeMenu();
  closeFileMenu();
}

function startRefreshTimer() {
  window.clearInterval(refreshTimer);
  refreshTimer = window.setInterval(() => {
    if (document.hidden || isTrashView.value) return;
    if (
      store.assets.some(
        (asset) =>
          !["READY", "FAILED", "REJECTED", "WITHDRAWN"].includes(asset.version?.status || "") ||
          asset.version?.indexStatus === "PROCESSING",
      )
    ) {
      void store.loadAssets("library").catch(() => undefined);
    }
  }, 5000);
}

watch(viewMode, (mode) => localStorage.setItem("examinsight.ui.library-view", mode));
watch(activeFilter, () => {
  clearSelection();
  sourceFilter.value = null;
  fileTypeFilter.value = null;
});
watch(libraryLocation, async (location) => {
  clearSelection();
  sourceFilter.value = null;
  fileTypeFilter.value = null;
  if (location === "trash" && !store.trashedAssets.length && !store.trashedKnowledgeBases.length) {
    await loadData();
  }
});

onMounted(() => {
  void loadData();
  startRefreshTimer();
});

onBeforeUnmount(() => window.clearInterval(refreshTimer));
</script>
<template>
  <StudentShell>
    <div class="library-page" @click="closeMenus">
      <header class="library-header">
        <div class="page-title">
          <button v-if="isTrashView" type="button" aria-label="返回资料库" @click="leaveTrash">
            <AppIcon name="chevron-left" :size="20" />
          </button>
          <h1>{{ isTrashView ? "最近删除" : "资料库" }}</h1>
        </div>

        <div class="header-actions" @click.stop>
          <label class="search-box">
            <AppIcon name="search" :size="18" />
            <input v-model="searchQuery" placeholder="搜索" />
          </label>

          <div v-if="!isTrashView" class="new-menu-wrap">
            <button class="new-btn" type="button" @click="openNewMenu">
              新建
              <AppIcon name="chevron-down" :size="15" />
            </button>
            <div v-if="newMenuOpen" class="new-menu ui-menu-panel">
              <button class="ui-menu-item" type="button" @click="openUpload">
                <span class="ui-menu-icon"><AppIcon name="upload-cloud" :size="16" /></span>
                上传资料
              </button>
              <button class="ui-menu-item" type="button" @click="openNewKnowledge">
                <span class="ui-menu-icon"><AppIcon name="folder" :size="16" /></span>
                新建知识库
              </button>
            </div>
          </div>
        </div>
      </header>

      <div class="library-controls">
        <div v-if="selectedCount && isTrashView" class="bulk-actions">
          <button type="button" @click="restoreSelected">
            <AppIcon name="refresh-cw" :size="16" />
            恢复
          </button>
          <button class="danger-outline" type="button" @click="purgeTargets = [...selectedAssets]">
            <AppIcon name="trash" :size="16" />
            彻底删除
          </button>
        </div>
        <div v-else-if="selectedCount" class="bulk-actions">
          <button class="primary-action" type="button" @click="startChatWithSelection">
            <AppIcon name="edit" :size="16" />
            开始聊天
          </button>
          <button
            type="button"
            :disabled="!selectedAssets.some((asset) => asset.kind === 'file')"
            @click="downloadFiles(selectedAssets)"
          >
            <AppIcon name="download" :size="16" />
            下载
          </button>
          <button
            type="button"
            :disabled="!selectedAssets.some((asset) => asset.kind === 'file')"
            @click="openMoveModal()"
          >
            <AppIcon name="folder-move" :size="16" />
            加入知识库
          </button>
          <button class="danger-outline" type="button" @click="requestDelete(selectedAssets)">
            <AppIcon name="trash" :size="16" />
            移入回收站
          </button>
        </div>
        <div v-else class="tabs">
          <button
            v-for="filter in filters"
            :key="filter.value"
            :class="{ active: activeFilter === filter.value }"
            type="button"
            @click="activeFilter = filter.value"
          >
            {{ filter.label }}
          </button>
        </div>

        <div class="view-tools">
          <span v-if="selectedCount">已选 {{ selectedCount }} 个</span>

          <div class="filter-menu-wrap" @click.stop>
            <button
              class="filter-btn ui-icon-action"
              :class="{ active: filterMenuOpen || activeFilterCount > 0 }"
              type="button"
              aria-label="筛选"
              :aria-expanded="filterMenuOpen"
              @click="filterMenuOpen = !filterMenuOpen"
            >
              <AppIcon name="list-filter" :size="18" />
              <span v-if="activeFilterCount" class="filter-count">{{
                activeFilterCount
              }}</span>
            </button>
            <div v-if="filterMenuOpen" class="filter-menu ui-menu-panel">
              <span class="filter-section-label">来源</span>
              <button
                v-for="option in sourceFilterOptions"
                :key="option.value"
                class="ui-menu-item filter-option"
                :class="{ selected: sourceFilter === option.value }"
                type="button"
                @click="toggleSourceFilter(option.value)"
              >
                <span class="ui-menu-icon"><AppIcon :name="option.icon" :size="16" /></span>
                {{ option.label }}
                <AppIcon
                  v-if="sourceFilter === option.value"
                  class="filter-check"
                  name="check"
                  :size="16"
                />
              </button>
              <span class="filter-section-label filter-section-label--divided">文件类型</span>
              <button
                v-for="option in fileTypeFilterOptions"
                :key="option.value"
                class="ui-menu-item filter-option"
                :class="{ selected: fileTypeFilter === option.value }"
                type="button"
                @click="toggleFileTypeFilter(option.value)"
              >
                <span class="ui-menu-icon"><AppIcon :name="option.icon" :size="16" /></span>
                {{ option.label }}
                <AppIcon
                  v-if="fileTypeFilter === option.value"
                  class="filter-check"
                  name="check"
                  :size="16"
                />
              </button>
              <div class="ui-menu-divider" />
              <button
                class="ui-menu-item filter-option"
                :class="{ selected: isTrashView }"
                type="button"
                @click="openTrash"
              >
                <span class="ui-menu-icon"><AppIcon class="filter-trash-icon" name="trash" :size="18" /></span>
                最近删除
              </button>
            </div>
          </div>
          <span class="view-divider" />
          <button
            class="round-icon ui-icon-action"
            :class="{ active: viewMode === 'grid' }"
            type="button"
            aria-label="网格视图"
            @click="viewMode = 'grid'"
          >
            <AppIcon name="grid" :size="18" />
          </button>
          <button
            class="round-icon ui-icon-action"
            :class="{ active: viewMode === 'list' }"
            type="button"
            aria-label="列表视图"
            @click="viewMode = 'list'"
          >
            <AppIcon name="list" :size="18" />
          </button>
        </div>
      </div>

      <div v-if="notice" class="library-notice" role="status">
        <span>{{ notice }}</span>
        <button type="button" aria-label="关闭提示" @click="notice = ''">×</button>
      </div>

      <div v-if="isTrashView" class="trash-notice">
        <span>这里保存你主动删除的资料和知识库；恢复后会回到资料库。</span>
        <button v-if="visibleAssets.length" type="button" @click="purgeTargets = [...visibleAssets]">全部彻底删除</button>
      </div>

      <section v-if="pageError" class="library-state library-state--error" role="alert">
        <strong>资料加载失败</strong>
        <span>{{ pageError }}</span>
        <button type="button" @click="loadData">重试</button>
      </section>
      <section v-else-if="pageLoading" class="library-state" aria-live="polite">
        <strong>正在加载资料…</strong>
      </section>
      <section v-else-if="!visibleAssets.length" class="library-state">
        <strong>{{ searchQuery.trim() ? "没有匹配的资料" : isTrashView ? "回收站为空" : "暂无资料" }}</strong>
        <span>{{ searchQuery.trim() ? "请调整搜索词或筛选条件" : isTrashView ? "移入回收站的资料和知识库会显示在这里" : "可上传资料或新建知识库" }}</span>
      </section>

      <div v-if="!pageLoading && !pageError && visibleAssets.length && viewMode === 'grid'" class="resource-groups">
        <section v-if="visibleKnowledgeAssets.length" class="resource-group">
          <div class="asset-grid asset-grid--knowledge">
            <div v-for="asset in visibleKnowledgeAssets" :key="asset.id" class="asset-card-shell">
              <article class="asset-card asset-card--knowledge" :class="{ 'asset-card--selected': isSelected(asset.id) }" @click="openAsset(asset)">
                <ResourceTypeIcon type="knowledge" variant="plain" :size="24" />
                <div class="asset-card-copy">
                  <strong>{{ assetName(asset) }}</strong>
                  <p>{{ knowledgeDescription(asset) }}</p>
                  <small>{{ knowledgeFileCount(asset.source) }} 个资料 · {{ knowledgeUpdatedAt(asset.source) }}</small>
                </div>
                <button class="asset-more" type="button" aria-label="知识库菜单" @click.stop="toggleKnowledgeMenu(asset.id)"><AppIcon name="more-horizontal" :size="18" /></button>
                <button class="asset-check" :class="{ active: isSelected(asset.id), visible: hasSelection }" type="button" :aria-label="`选择 ${assetName(asset)}`" :aria-pressed="isSelected(asset.id)" @click.stop="toggleSelection(asset.id)">
                  <AppIcon v-if="isSelected(asset.id)" name="check" :size="17" />
                </button>
                <div v-if="knowledgeMenuId === asset.id" class="floating-menu ui-menu-panel" @click.stop>
                  <template v-if="isTrashView">
                    <button class="ui-menu-item" type="button" @click="restoreAsset(asset)"><span class="ui-menu-icon"><AppIcon name="refresh-cw" :size="16" /></span>恢复</button>
                    <button class="ui-menu-item ui-menu-item--danger" type="button" @click="purgeTargets = [asset]"><span class="ui-menu-icon"><AppIcon name="trash" :size="16" /></span>彻底删除</button>
                  </template>
                  <template v-else>
                    <button class="ui-menu-item" type="button" @click="startLearning(asset.source.id)"><span class="ui-menu-icon"><AppIcon name="graduation" :size="16" /></span>开始智能学习</button>
                    <button class="ui-menu-item" type="button" @click="renameKnowledge(asset.source)"><span class="ui-menu-icon"><AppIcon name="edit" :size="16" /></span>重命名</button>
                    <div class="ui-menu-divider" />
                    <button class="ui-menu-item ui-menu-item--danger" type="button" @click="requestDelete([asset])"><span class="ui-menu-icon"><AppIcon name="trash" :size="16" /></span>移入回收站</button>
                  </template>
                </div>
              </article>
            </div>
          </div>
        </section>

        <section v-if="visibleFileAssets.length" class="resource-group">
          <div class="asset-grid">
            <div v-for="asset in visibleFileAssets" :key="asset.id" class="asset-card-shell">
              <article class="asset-card" :class="{ 'asset-card--selected': isSelected(asset.id) }" @click="openAsset(asset)">
                <div class="asset-card-heading">
                  <strong>{{ asset.source.name }}</strong>
                </div>
                <V2AssetThumbnail
                  v-if="asset.source.fileType === 'image' && !isTrashView"
                  :asset="asset.source.raw"
                />
                <div v-else class="asset-card-visual" aria-hidden="true">
                  <ResourceTypeIcon :type="fileVisualType(asset.source)" variant="plain" :size="44" />
                </div>
                <footer>
                  <span>{{ asset.source.format }} · {{ fileSize(asset.source) }}</span>
                  <span
                    v-if="assetStatus(asset.source.raw).label"
                    class="asset-state"
                    :class="`asset-state--${assetStatus(asset.source.raw).tone}`"
                  >{{ assetStatus(asset.source.raw).label }}</span>
                </footer>
                <button class="asset-more" type="button" aria-label="资料菜单" @click.stop="toggleFileMenu(asset.id)"><AppIcon name="more-horizontal" :size="18" /></button>
                <button class="asset-check" :class="{ active: isSelected(asset.id), visible: hasSelection }" type="button" :aria-label="`选择 ${assetName(asset)}`" :aria-pressed="isSelected(asset.id)" @click.stop="toggleSelection(asset.id)">
                  <AppIcon v-if="isSelected(asset.id)" name="check" :size="17" />
                </button>
                <div v-if="fileMenuId === asset.id" class="floating-menu ui-menu-panel" @click.stop>
                  <template v-if="isTrashView">
                    <button class="ui-menu-item" type="button" @click="restoreAsset(asset)"><span class="ui-menu-icon"><AppIcon name="refresh-cw" :size="16" /></span>恢复</button>
                    <button class="ui-menu-item ui-menu-item--danger" type="button" @click="purgeTargets = [asset]"><span class="ui-menu-icon"><AppIcon name="trash" :size="16" /></span>彻底删除</button>
                  </template>
                  <template v-else>
                    <button class="ui-menu-item" type="button" @click="downloadFiles([asset]); closeFileMenu()"><span class="ui-menu-icon"><AppIcon name="download" :size="16" /></span>下载</button>
                    <button v-if="canRetryAsset(asset.source.raw)" class="ui-menu-item" type="button" @click="retryProcessing(asset.source.raw)"><span class="ui-menu-icon"><AppIcon name="refresh-cw" :size="16" /></span>重新处理</button>
                    <button class="ui-menu-item" type="button" @click="renameAsset(asset)"><span class="ui-menu-icon"><AppIcon name="edit" :size="16" /></span>重命名</button>
                    <button class="ui-menu-item" type="button" @click="openMoveForAsset(asset)"><span class="ui-menu-icon"><AppIcon name="folder-move" :size="16" /></span>加入知识库</button>
                    <div class="ui-menu-divider" />
                    <button class="ui-menu-item ui-menu-item--danger" type="button" @click="requestDelete([asset])"><span class="ui-menu-icon"><AppIcon name="trash" :size="16" /></span>移入回收站</button>
                  </template>
                </div>
              </article>
            </div>
          </div>
        </section>
      </div>

      <section v-else-if="!pageLoading && !pageError && visibleAssets.length" class="asset-list">
        <div class="asset-list-head">
          <span class="selection-column">
            <button
              class="asset-row-check asset-row-check--all"
              :class="{ active: someVisibleSelected }"
              type="button"
              aria-label="选择全部"
              :aria-pressed="allVisibleSelected"
              @click="toggleAllVisible"
            >
              <AppIcon v-if="allVisibleSelected" class="selection-check-icon" name="check" :size="13" />
              <span v-else-if="someVisibleSelected" class="selection-dash" />
            </button>
          </span>
          <span>名称</span>
          <span>{{ isTrashView ? "删除时间" : "修改时间" }} ↓</span>
          <span>大小</span>
          <span />
        </div>
        <div v-for="asset in visibleAssets" :key="asset.id" class="asset-list-entry">
          <div class="selection-column">
            <button class="asset-row-check" :class="{ active: isSelected(asset.id) }" type="button" :aria-label="`选择 ${assetName(asset)}`" :aria-pressed="isSelected(asset.id)" @click.stop="toggleSelection(asset.id)">
              <AppIcon v-if="isSelected(asset.id)" class="selection-check-icon" name="check" :size="13" />
            </button>
          </div>
          <article class="asset-row" :class="{ 'asset-row--selected': isSelected(asset.id) }" @click="openAsset(asset)">
            <ResourceTypeIcon :type="asset.kind === 'knowledge' ? 'knowledge' : fileVisualType(asset.source)" variant="plain" :size="20" />
            <div class="row-copy">
              <strong>{{ assetName(asset) }}</strong>
              <small v-if="asset.kind === 'knowledge' || assetStatus(asset.source.raw).label">
                {{ asset.kind === 'knowledge' ? `${knowledgeFileCount(asset.source)} 个资料` : assetStatus(asset.source.raw).label }}
              </small>
            </div>
            <span>{{ assetModifiedAt(asset) }}</span>
            <span>{{ assetSize(asset) }}</span>
            <button class="row-more" type="button" aria-label="项目菜单" @click.stop="asset.kind === 'knowledge' ? toggleKnowledgeMenu(asset.id) : toggleFileMenu(asset.id)"><AppIcon name="more-horizontal" :size="18" /></button>
            <div v-if="knowledgeMenuId === asset.id || fileMenuId === asset.id" class="floating-menu menu--row ui-menu-panel" @click.stop>
              <template v-if="isTrashView">
                <button class="ui-menu-item" type="button" @click="restoreAsset(asset)"><span class="ui-menu-icon"><AppIcon name="refresh-cw" :size="16" /></span>恢复</button>
                <button class="ui-menu-item ui-menu-item--danger" type="button" @click="purgeTargets = [asset]"><span class="ui-menu-icon"><AppIcon name="trash" :size="16" /></span>彻底删除</button>
              </template>
              <template v-else-if="asset.kind === 'knowledge'">
                <button class="ui-menu-item" type="button" @click="startLearningFromAsset(asset)"><span class="ui-menu-icon"><AppIcon name="graduation" :size="16" /></span>开始智能学习</button>
                <button class="ui-menu-item" type="button" @click="renameAsset(asset)"><span class="ui-menu-icon"><AppIcon name="edit" :size="16" /></span>重命名</button>
                <div class="ui-menu-divider" />
                <button class="ui-menu-item ui-menu-item--danger" type="button" @click="requestDelete([asset])"><span class="ui-menu-icon"><AppIcon name="trash" :size="16" /></span>移入回收站</button>
              </template>
              <template v-else>
                <button class="ui-menu-item" type="button" @click="downloadFiles([asset]); closeFileMenu()"><span class="ui-menu-icon"><AppIcon name="download" :size="16" /></span>下载</button>
                <button v-if="canRetryAsset(asset.source.raw)" class="ui-menu-item" type="button" @click="retryProcessing(asset.source.raw)"><span class="ui-menu-icon"><AppIcon name="refresh-cw" :size="16" /></span>重新处理</button>
                <button class="ui-menu-item" type="button" @click="renameAsset(asset)"><span class="ui-menu-icon"><AppIcon name="edit" :size="16" /></span>重命名</button>
                <button class="ui-menu-item" type="button" @click="openMoveForAsset(asset)"><span class="ui-menu-icon"><AppIcon name="folder-move" :size="16" /></span>加入知识库</button>
                <div class="ui-menu-divider" />
                <button class="ui-menu-item ui-menu-item--danger" type="button" @click="requestDelete([asset])"><span class="ui-menu-icon"><AppIcon name="trash" :size="16" /></span>移入回收站</button>
              </template>
            </div>
          </article>
        </div>
      </section>

      <div v-if="hasMore" class="library-load-more">
        <button type="button" :disabled="store.loading" @click="loadMore">加载更多</button>
      </div>
    </div>

    <UploadMaterialModal
      :open="uploadOpen"
      @close="uploadOpen = false"
      @uploaded="handleUploaded"
    />

    <V2KnowledgeBaseModal
      :open="newKnowledgeOpen"
      :knowledge-base="editingKnowledgeBase"
      @close="newKnowledgeOpen = false"
      @saved="handleKnowledgeSaved"
    />

    <div v-if="moveModalOpen" class="modal-backdrop" @click.self="moveModalOpen = false">
      <section class="move-modal">
        <header>
          <h2>加入知识库</h2>
          <button type="button" @click="moveModalOpen = false">×</button>
        </header>
        <span class="move-label">知识库</span>
        <div class="move-list">
          <button
            v-for="item in availableKnowledgeBases"
            :key="item.id"
            type="button"
            :class="{ selected: moveTargetKnowledgeBaseId === item.id }"
            @click="moveTargetKnowledgeBaseId = item.id"
          >
            <span class="move-icon"><AppIcon name="folder" :size="20" /></span>
            <span>{{ knowledgeTitle(item) }}</span>
            <AppIcon name="chevron-right" :size="16" />
          </button>
        </div>
        <footer>
          <button class="outline-btn" type="button" @click="openKnowledgeFromMove">
            新建知识库
          </button>
          <span />
          <button class="outline-btn" type="button" @click="moveModalOpen = false">取消</button>
          <button
            class="move-confirm"
            type="button"
            :disabled="store.mutating || !moveTargetKnowledgeBaseId"
            @click="moveSelectedResources"
          >
            {{ store.mutating ? "加入中…" : "加入这里" }}
          </button>
        </footer>
      </section>
    </div>

    <ConfirmDialog
      :open="deleteTargets.length > 0"
      title="移入回收站"
      :message="`将 ${deleteTargets.length} 个项目移入回收站，之后仍可恢复。`"
      confirm-text="移入回收站"
      confirm-variant="danger"
      @close="deleteTargets = []"
      @confirm="confirmDelete"
    />

    <ConfirmDialog
      :open="purgeTargets.length > 0"
      title="彻底删除"
      :message="purgeTargets.some((target) => target.kind === 'file')
        ? `将永久删除 ${purgeTargets.length} 个项目中的原文件、解析内容和索引，该操作不可撤销。`
        : `将彻底删除 ${purgeTargets.length} 个知识库及其关联，个人资料原文件仍会保留。`"
      confirm-text="彻底删除"
      confirm-variant="danger"
      @close="purgeTargets = []"
      @confirm="confirmPurge"
    />

    <AppModal :open="Boolean(renameAssetTarget)" title="重命名资料" @close="renameAssetTarget = null">
      <label class="rename-field">
        <span>资料名称</span>
        <input v-model="renameValue" maxlength="255" @keyup.enter="saveAssetName" />
      </label>
      <template #footer>
        <div class="rename-actions">
          <AppButton variant="ghost" @click="renameAssetTarget = null">取消</AppButton>
          <AppButton :disabled="!renameValue.trim()" :loading="store.mutating" @click="saveAssetName">
            保存
          </AppButton>
        </div>
      </template>
    </AppModal>
  </StudentShell>
</template>

<style scoped>
.library-page {
  width: min(1180px, calc(100% - 48px));
  min-height: 100%;
  margin: 0 auto;
  padding: 48px 0 72px;
  box-sizing: border-box;
  color: var(--color-text);
}

.library-header,
.library-controls {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
}

.library-header { margin-bottom: 54px; }
.page-title { position: relative; display: flex; align-items: center; }
.page-title h1 { margin: 0; font-size: clamp(32px, 4vw, 46px); font-weight: 700; letter-spacing: -.045em; }
.page-title button,
.library-notice button {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border: 0;
  border-radius: 9px;
  background: transparent;
  color: var(--color-text);
  cursor: pointer;
}
.page-title button {
  position: absolute;
  right: calc(100% + 8px);
}
.page-title button:hover,
.library-notice button:hover { background: var(--color-hover); }

.header-actions { display: flex; align-items: center; gap: 12px; }
.search-box {
  width: min(360px, 34vw);
  height: 48px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 16px;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-surface);
  color: var(--color-text-muted);
  box-sizing: border-box;
}
.search-box input { min-width: 0; flex: 1; border: 0; outline: 0; background: transparent; color: var(--color-text); font: inherit; }
.new-menu-wrap,
.filter-menu-wrap { position: relative; }
.new-btn {
  height: 48px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 0 20px;
  border: 0;
  border-radius: 999px;
  background: var(--color-primary);
  color: var(--color-on-primary);
  cursor: pointer;
  font: inherit;
  font-weight: 600;
}
.new-menu,
.filter-menu {
  position: absolute;
  right: 0;
  top: calc(100% + 8px);
  z-index: 40;
  width: 210px;
}
.filter-menu { width: 230px; padding: 8px; }
.filter-section-label { display: block; padding: 8px 10px 5px; color: var(--color-text-muted); font-size: 12px; }
.filter-section-label--divided { margin-top: 6px; border-top: 1px solid var(--color-border); padding-top: 12px; }
.filter-check { margin-left: auto; }
.filter-trash-icon { color: var(--color-text); }

.library-controls { min-height: 46px; margin-bottom: 24px; }
.tabs,
.bulk-actions,
.view-tools { display: flex; align-items: center; gap: 8px; }
.tabs button,
.bulk-actions button {
  min-height: 38px;
  border: 0;
  border-radius: 999px;
  padding: 0 15px;
  background: transparent;
  color: var(--color-text-muted);
  cursor: pointer;
  font: inherit;
}
.tabs button:hover,
.tabs button.active { background: var(--color-hover); color: var(--color-text); }
.bulk-actions button { display: inline-flex; align-items: center; gap: 7px; border: 1px solid var(--color-border); color: var(--color-text); }
.bulk-actions .primary-action { border-color: #303030; background: #303030; color: #fff; }
.bulk-actions .danger-outline { color: var(--color-danger); border-color: color-mix(in srgb, var(--color-danger) 30%, var(--color-border)); }
.bulk-actions button:disabled { opacity: .4; cursor: not-allowed; }
.view-tools > span { color: var(--color-text-muted); font-size: 13px; }
.round-icon,
.filter-btn {
  position: relative;
  width: 38px;
  height: 38px;
  display: grid;
  place-items: center;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: var(--color-text-muted);
  cursor: pointer;
}
.round-icon:hover,
.round-icon.active,
.filter-btn:hover,
.filter-btn.active { background: var(--color-hover); color: var(--color-text); }
.view-divider { width: 1px; height: 22px; margin: 0 2px; background: var(--color-border); }
.filter-count {
  position: absolute;
  top: -2px;
  right: -2px;
  min-width: 16px;
  height: 16px;
  display: grid;
  place-items: center;
  border-radius: 999px;
  background: var(--color-text);
  color: var(--color-surface);
  font-size: 10px;
}

.library-notice,
.trash-notice {
  min-height: 44px;
  margin-bottom: 22px;
  padding: 10px 14px;
  border-radius: 12px;
  background: var(--color-bg-alt);
  color: var(--color-text);
  box-sizing: border-box;
}
.library-notice,
.trash-notice { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.trash-notice button {
  border: 0;
  background: transparent;
  color: var(--color-text-muted);
  cursor: pointer;
  font: inherit;
}
.trash-notice button:hover { color: var(--color-danger); }
.library-state {
  min-height: 340px;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 8px;
  color: var(--color-text-muted);
  text-align: center;
}
.library-state strong { color: var(--color-text); }
.library-state button {
  min-height: 36px;
  margin-top: 6px;
  padding: 0 14px;
  border: 1px solid var(--color-border);
  border-radius: 9px;
  background: var(--color-surface);
  color: var(--color-text);
  cursor: pointer;
}
.library-state--error span { color: var(--color-danger); }

.resource-groups { display: grid; gap: 18px; }
.asset-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 18px; }
.asset-card-shell { position: relative; min-width: 0; }
.asset-check {
  position: absolute;
  right: 14px;
  bottom: 14px;
  z-index: 4;
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  border: 2px solid var(--color-border);
  border-radius: 50%;
  background: var(--color-surface);
  color: var(--color-text);
  padding: 0;
  cursor: pointer;
  opacity: 0;
  transition: opacity .14s ease, border-color .14s ease, background-color .14s ease;
}
.asset-card:hover > .asset-check,
.asset-check.visible,
.asset-check:focus-visible,
.asset-check.active { opacity: 1; }
.asset-check.active {
  border-color: var(--color-text);
  background: var(--color-surface);
}
.asset-check:focus-visible { outline: 2px solid var(--color-text); outline-offset: 2px; }

.asset-card {
  position: relative;
  min-height: 250px;
  display: grid;
  grid-template-rows: auto 1fr auto;
  gap: 16px;
  padding: 18px;
  border: 1px solid var(--color-border);
  border-radius: 16px;
  background: var(--color-surface);
  box-sizing: border-box;
  cursor: pointer;
  transition: background-color .14s ease, border-color .14s ease;
}
.asset-card:hover { background: var(--color-hover); }
.asset-card--selected {
  border-color: var(--color-text);
  background: var(--color-hover);
  box-shadow: inset 0 0 0 1px var(--color-text);
}
.asset-card--knowledge {
  min-height: 138px;
  grid-template-columns: 30px minmax(0, 1fr);
  grid-template-rows: 1fr;
  align-items: start;
  gap: 13px;
}
.asset-card-copy { min-width: 0; padding-right: 34px; }
.asset-card strong,
.row-copy strong { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--color-text); font-size: 15px; }
.asset-card-copy p {
  height: 40px;
  margin: 8px 0 7px;
  overflow: hidden;
  color: var(--color-text-muted);
  font-size: 13px;
  line-height: 20px;
}
.asset-card small,
.row-copy small { color: var(--color-text-muted); font-size: 12px; }
.asset-card-heading { min-width: 0; padding-right: 32px; }
.asset-card-visual {
  min-height: 148px;
  display: grid;
  place-items: center;
  color: var(--color-text);
}
.asset-card footer { display: flex; align-items: center; justify-content: space-between; gap: 10px; padding-right: 38px; color: var(--color-text-muted); font-size: 12px; }
.asset-state { white-space: nowrap; font-weight: 600; }
.asset-state--error { color: var(--color-danger); }
.asset-state--pending,
.asset-state--neutral { color: var(--color-text-muted); }

.asset-more,
.row-more {
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  border: 0;
  border-radius: 9px;
  background: transparent;
  color: var(--color-text-muted);
  cursor: pointer;
}
.asset-more { position: absolute; top: 10px; right: 10px; opacity: 0; }
.asset-card:hover .asset-more,
.asset-card--selected .asset-more,
.asset-more:focus-visible { opacity: 1; }
.asset-more:hover,
.row-more:hover { background: var(--color-hover-strong); color: var(--color-text); }
.floating-menu { position: absolute; top: 46px; right: 10px; z-index: 30; width: 184px; }
.menu--row { top: 54px; right: 8px; }

.asset-list {
  --asset-list-check-column: 44px;
  --asset-list-date-column: 140px;
  --asset-list-size-column: 110px;
  --asset-list-menu-column: 40px;
  display: grid;
  gap: 4px;
}
.asset-list-head {
  min-height: 42px;
  display: grid;
  grid-template-columns:
    var(--asset-list-check-column)
    minmax(0, 1fr)
    var(--asset-list-date-column)
    var(--asset-list-size-column)
    var(--asset-list-menu-column);
  align-items: center;
  padding-right: 12px;
  box-sizing: border-box;
  color: var(--color-text-muted);
  font-size: 13px;
}
.asset-list-head > span:nth-child(2) { padding-left: 12px; }
.asset-list-entry {
  position: relative;
  min-width: 0;
  display: grid;
  grid-template-columns: var(--asset-list-check-column) minmax(0, 1fr);
  align-items: center;
}
.selection-column { display: grid; place-items: center; }
.asset-row-check {
  width: 26px;
  height: 26px;
  display: grid;
  place-items: center;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  background: var(--color-surface);
  color: transparent;
  padding: 0;
  cursor: pointer;
  box-sizing: border-box;
  transition: border-color .14s ease, background-color .14s ease, color .14s ease;
}
.asset-row-check:hover { border-color: var(--color-text-muted); }
.asset-row-check.active {
  border-color: var(--color-text);
  background: var(--color-text);
  color: var(--color-surface);
}
.asset-row-check:focus-visible { outline: 2px solid var(--color-text); outline-offset: 2px; }
.asset-row-check :deep(.selection-check-icon path) { stroke-width: 3; }
.selection-dash { width: 10px; height: 2px; border-radius: 999px; background: currentColor; }
.asset-row {
  position: relative;
  min-width: 0;
  min-height: 66px;
  display: grid;
  grid-template-columns:
    44px
    minmax(0, 1fr)
    var(--asset-list-date-column)
    var(--asset-list-size-column)
    var(--asset-list-menu-column);
  align-items: center;
  padding: 0 12px;
  border-bottom: 1px solid var(--color-border);
  border-radius: 12px;
  box-sizing: border-box;
  color: var(--color-text);
  cursor: pointer;
  transition: background-color .14s ease, border-color .14s ease;
}
.asset-row:hover,
.asset-row--selected { background: var(--color-hover); border-color: transparent; }
.row-copy { min-width: 0; }
.row-copy small { display: block; margin-top: 3px; }
.row-more { opacity: 0; }
.asset-row:hover .row-more,
.asset-row--selected .row-more,
.row-more:focus-visible { opacity: 1; }

.library-load-more { display: flex; justify-content: center; margin-top: 26px; }
.library-load-more button {
  min-height: 38px;
  padding: 0 18px;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-surface);
  color: var(--color-text);
  cursor: pointer;
}
.library-load-more button:disabled { opacity: .5; }

.rename-field { display: grid; gap: 8px; }
.rename-field input {
  width: 100%;
  min-height: 42px;
  padding: 0 12px;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  background: var(--color-surface);
  color: var(--color-text);
  box-sizing: border-box;
}
.rename-actions { display: flex; justify-content: flex-end; gap: 10px; }

.modal-backdrop {
  position: fixed;
  inset: 0;
  z-index: 220;
  display: grid;
  place-items: center;
  padding: 24px;
  background: var(--color-overlay);
}
.move-modal {
  width: min(760px, 100%);
  min-height: 500px;
  display: grid;
  grid-template-rows: auto auto minmax(0, 1fr) auto;
  padding: 26px;
  border: 1px solid var(--color-border);
  border-radius: 16px;
  background: var(--color-surface);
  box-shadow: var(--shadow-lg);
}
.move-modal header,
.move-modal footer { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.move-modal header { margin-bottom: 18px; }
.move-modal header h2 { margin: 0; font-size: 20px; }
.move-modal header button { width: 32px; height: 32px; border: 0; border-radius: 8px; background: transparent; color: var(--color-text); cursor: pointer; font-size: 22px; }
.move-label { color: var(--color-text-muted); font-size: 13px; }
.move-list { min-height: 0; margin-top: 10px; overflow: auto; }
.move-list button {
  width: 100%;
  min-height: 58px;
  display: grid;
  grid-template-columns: 36px minmax(0, 1fr) 18px;
  align-items: center;
  gap: 10px;
  border: 0;
  border-bottom: 1px solid var(--color-border);
  background: transparent;
  color: var(--color-text);
  cursor: pointer;
  text-align: left;
}
.move-list button:hover,
.move-list button.selected { background: var(--color-hover); }
.move-icon { width: 32px; height: 32px; display: grid; place-items: center; border: 1px solid var(--color-border); border-radius: 8px; color: var(--color-text); }
.move-modal footer { justify-content: flex-end; padding-top: 18px; }
.move-modal footer > span { flex: 1; }
.outline-btn,
.move-confirm {
  min-height: 38px;
  padding: 0 15px;
  border-radius: 9px;
  cursor: pointer;
  font: inherit;
}
.outline-btn { border: 1px solid var(--color-border); background: var(--color-surface); color: var(--color-text); }
.move-confirm { border: 0; background: var(--color-primary); color: var(--color-on-primary); }
.move-confirm:disabled { opacity: .45; cursor: not-allowed; }

@media (hover: none) {
  .asset-check,
  .asset-more,
  .row-more { opacity: 1; }
}

@media (max-width: 980px) {
  .asset-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .asset-list { --asset-list-date-column: 100px; --asset-list-size-column: 0px; }
  .asset-list-head > span:nth-child(4),
  .asset-row > span:nth-child(4) { display: none; }
}

@media (max-width: 720px) {
  .library-page { width: min(100% - 28px, 1180px); padding-top: 28px; }
  .library-header,
  .library-controls { align-items: stretch; flex-direction: column; }
  .library-header { margin-bottom: 30px; }
  .header-actions,
  .search-box { width: 100%; }
  .header-actions { align-items: stretch; }
  .new-menu-wrap { flex: 0 0 auto; }
  .library-controls { gap: 16px; }
  .tabs { overflow-x: auto; }
  .view-tools { justify-content: flex-end; }
  .asset-grid { grid-template-columns: 1fr; }
  .asset-list {
    --asset-list-check-column: 38px;
    --asset-list-date-column: 0px;
    --asset-list-size-column: 0px;
  }
  .asset-list-head > span:nth-child(3),
  .asset-list-head > span:nth-child(4),
  .asset-row > span { display: none; }
  .move-modal { min-height: 440px; padding: 20px; }
}
</style>
