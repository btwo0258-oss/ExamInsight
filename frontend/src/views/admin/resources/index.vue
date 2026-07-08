<template>
  <div class="admin-resources">
    <div class="page-header">
      <h1>资料管理</h1>
      <p class="desc">管理资料中心的文件，用户端只能查看和下载</p>
    </div>

    <div class="toolbar">
      <div class="toolbar-left">
        <div
          class="custom-select"
          :class="{ 'custom-select--open': showFilterDropdown }"
          @click="showFilterDropdown = !showFilterDropdown"
        >
          <span class="custom-select__value">{{ filterCategory || "全部分类" }}</span>
          <AppIcon name="chevron-right" :size="16" />
          <div class="custom-select__dropdown" v-if="showFilterDropdown">
            <div
              class="custom-select__option"
              :class="{ 'custom-select__option--selected': filterCategory === '' }"
              @click.stop="
                filterCategory = '';
                showFilterDropdown = false;
              "
            >
              全部分类
            </div>
            <div
              v-for="cat in categories"
              :key="cat"
              class="custom-select__option"
              :class="{ 'custom-select__option--selected': filterCategory === cat }"
              @click.stop="
                filterCategory = cat;
                showFilterDropdown = false;
              "
            >
              {{ cat }}
            </div>
          </div>
        </div>
        <input type="text" v-model="searchText" placeholder="搜索资料..." class="search-input" />
      </div>
      <div class="toolbar-right">
        <button class="btn btn-primary" @click="showAddModal = true">
          <span>+ 上传资料</span>
        </button>
      </div>
    </div>

    <div class="table-container">
      <table class="data-table">
        <thead>
          <tr>
            <th>ID</th>
            <th>标题</th>
            <th>分类</th>
            <th>年份</th>
            <th>文件类型</th>
            <th>大小</th>
            <th>下载次数</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in filteredList" :key="item.id">
            <td>{{ item.id }}</td>
            <td class="title-cell">{{ item.title }}</td>
            <td>
              <span class="tag">{{ item.category }}</span>
            </td>
            <td>{{ item.year }}</td>
            <td>{{ item.fileType }}</td>
            <td>{{ formatFileSize(item.fileSize) }}</td>
            <td>{{ item.downloadCount }}</td>
            <td class="actions-cell">
              <button class="btn-text" @click="editResource(item)">编辑</button>
              <button class="btn-text btn-danger" @click="deleteResource(item)">删除</button>
            </td>
          </tr>
          <tr v-if="filteredList.length === 0">
            <td colspan="8" class="empty-row">暂无资料</td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="showAddModal" class="modal-overlay" @click.self="showAddModal = false">
      <div class="modal">
        <div class="modal-header">
          <h3>{{ editingResource ? "编辑资料" : "上传资料" }}</h3>
          <button class="modal-close" @click="closeModal">&times;</button>
        </div>
        <div class="modal-body">
          <div class="form-group">
            <label>标题</label>
            <input type="text" v-model="form.title" placeholder="输入资料标题" />
          </div>
          <div class="form-group">
            <label>分类</label>
            <div
              class="custom-select form-select"
              :class="{ 'custom-select--open': showFormCategoryDropdown }"
              @click="showFormCategoryDropdown = !showFormCategoryDropdown"
            >
              <span class="custom-select__value">{{ form.category }}</span>
              <AppIcon name="chevron-right" :size="16" />
              <div class="custom-select__dropdown" v-if="showFormCategoryDropdown">
                <div
                  v-for="cat in categories"
                  :key="cat"
                  class="custom-select__option"
                  :class="{ 'custom-select__option--selected': form.category === cat }"
                  @click.stop="
                    form.category = cat;
                    showFormCategoryDropdown = false;
                  "
                >
                  {{ cat }}
                </div>
              </div>
            </div>
          </div>
          <div class="form-group">
            <label>年份</label>
            <input type="number" v-model.number="form.year" :min="2000" :max="2030" />
          </div>
          <div class="form-group">
            <label>描述</label>
            <textarea
              v-model="form.description"
              rows="3"
              placeholder="输入资料描述（可选）"
            ></textarea>
          </div>
          <div class="form-group" v-if="!editingResource">
            <label>文件</label>
            <input
              type="file"
              ref="fileInput"
              @change="onFileChange"
              accept=".pdf,.docx,.doc,.txt"
            />
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn btn-secondary" @click="closeModal">取消</button>
          <button
            class="btn btn-primary"
            @click="submitForm"
            :disabled="!form.title || !form.category"
          >
            {{ editingResource ? "保存" : "上传" }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from "vue";
import { adminRequest } from "@/api/adminRequest";
import AppIcon from "@/components/admin/AppIcon.vue";

const categories = [
  "英语四六级",
  "研究生考试",
  "公务员考试",
  "英语专四专八",
  "教师资格证",
  "计算机二级",
  "普通话等级考试",
];

const resourceList = ref<any[]>([]);
const filterCategory = ref("");
const searchText = ref("");
const showAddModal = ref(false);
const editingResource = ref<any>(null);
const fileInput = ref<HTMLInputElement | null>(null);
const showFilterDropdown = ref(false);
const showFormCategoryDropdown = ref(false);

function handleClickOutside(e: MouseEvent) {
  const target = e.target as HTMLElement;
  if (!target.closest(".custom-select")) {
    showFilterDropdown.value = false;
    showFormCategoryDropdown.value = false;
  }
}

const form = ref({
  title: "",
  category: "计算机二级",
  year: new Date().getFullYear(),
  description: "",
  file: null as File | null,
});

const filteredList = computed(() => {
  let list = resourceList.value;
  if (filterCategory.value) {
    list = list.filter((r) => r.category === filterCategory.value);
  }
  if (searchText.value) {
    const q = searchText.value.toLowerCase();
    list = list.filter((r) => r.title.toLowerCase().includes(q));
  }
  return list;
});

function formatFileSize(bytes: number) {
  if (!bytes) return "-";
  if (bytes < 1024) return bytes + " B";
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + " KB";
  return (bytes / (1024 * 1024)).toFixed(1) + " MB";
}

async function fetchResources() {
  try {
    const res = await adminRequest.get("/api/admin/resource/list");
    resourceList.value = Array.isArray(res) ? res : (res?.data ?? []);
  } catch (error) {
    console.error("Failed to fetch resources:", error);
    resourceList.value = [];
  }
}

function editResource(item: any) {
  editingResource.value = item;
  form.value = {
    title: item.title,
    category: item.category,
    year: item.year,
    description: item.description || "",
    file: null,
  };
  showAddModal.value = true;
}

async function deleteResource(item: any) {
  if (!confirm(`确定要删除「${item.title}」吗？`)) return;
  try {
    await adminRequest.delete(`/api/admin/resource/${item.id}`);
    await fetchResources();
  } catch (error) {
    console.error("Failed to delete resource:", error);
    alert("删除失败");
  }
}

function closeModal() {
  showAddModal.value = false;
  editingResource.value = null;
  form.value = {
    title: "",
    category: "计算机二级",
    year: new Date().getFullYear(),
    description: "",
    file: null,
  };
}

function onFileChange(e: Event) {
  const target = e.target as HTMLInputElement;
  if (target.files && target.files.length > 0) {
    form.value.file = target.files[0];
  }
}

async function submitForm() {
  if (!form.value.title || !form.value.category) return;

  try {
    if (editingResource.value) {
      await adminRequest.put(`/api/admin/resource/${editingResource.value.id}`, {
        title: form.value.title,
        category: form.value.category,
        year: form.value.year,
        description: form.value.description,
      });
    } else {
      if (!form.value.file) {
        alert("请选择要上传的文件");
        return;
      }
      const fd = new FormData();
      fd.append("title", form.value.title);
      fd.append("category", form.value.category);
      fd.append("year", String(form.value.year));
      fd.append("description", form.value.description);
      fd.append("file", form.value.file);
      await adminRequest.post("/api/admin/resource/upload", fd);
    }
    closeModal();
    await fetchResources();
  } catch (error: any) {
    console.error("Failed to submit form:", error);
    const msg = error?.response?.data?.message || error?.message || "操作失败，请重试";
    alert(msg);
  }
}

onMounted(() => {
  fetchResources();
  window.addEventListener("click", handleClickOutside);
});

onUnmounted(() => {
  window.removeEventListener("click", handleClickOutside);
});
</script>

<style scoped>
.admin-resources {
  padding: 32px;
  max-width: 1200px;
}

.page-header {
  margin-bottom: 32px;
}

.page-header h1 {
  font-size: 24px;
  font-weight: 700;
  color: var(--color-text);
  margin: 0 0 8px 0;
}

.desc {
  font-size: 14px;
  color: var(--color-text-muted);
  margin: 0;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  gap: 16px;
  flex-wrap: wrap;
}

.toolbar-left {
  display: flex;
  gap: 12px;
  align-items: center;
}

.search-input {
  padding: 8px 12px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-bg);
  color: var(--color-text);
  font-size: 14px;
  outline: none;
  min-width: 200px;
}

.custom-select {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-bg);
  color: var(--color-text);
  font-size: 14px;
  cursor: pointer;
  min-width: 140px;
  user-select: none;
  transition: all 0.2s;
}

.custom-select:hover {
  background: var(--color-bg-alt);
  border-color: var(--color-border);
}

.custom-select--open {
  background: var(--color-bg-alt);
  border-color: var(--color-border);
}

.custom-select--open .icon {
  transform: rotate(90deg);
  transition: transform 0.2s;
}

.custom-select__value {
  flex: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.custom-select__arrow {
  font-size: 10px;
  color: var(--color-text-muted);
  flex-shrink: 0;
}

.custom-select__dropdown {
  position: absolute;
  top: calc(100% + 4px);
  left: 0;
  right: 0;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  z-index: 100;
  overflow: hidden;
}

.custom-select__option {
  padding: 8px 12px;
  font-size: 14px;
  color: var(--color-text);
  cursor: pointer;
  transition: background 0.15s;
}

.custom-select__option:hover {
  background: var(--color-bg-alt) !important;
}

.custom-select__option--selected {
  background: var(--color-bg-alt);
  font-weight: 600;
}

.custom-select__option--selected:hover {
  background: var(--color-border) !important;
}

.form-select {
  width: 100%;
  box-sizing: border-box;
}

.form-select .custom-select__dropdown {
  max-width: 100%;
  overflow-x: hidden;
}

.btn {
  padding: 8px 16px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  border: none;
  transition: all 0.2s;
}

.btn-primary {
  background: var(--color-primary);
  color: white;
}

.btn-primary:hover {
  opacity: 0.9;
}

.btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-secondary {
  background: var(--color-bg-alt);
  color: var(--color-text);
  border: 1px solid var(--color-border);
}

.table-container {
  border: 1px solid var(--color-border);
  border-radius: 12px;
  overflow: hidden;
}

.data-table {
  width: 100%;
  border-collapse: collapse;
}

.data-table th,
.data-table td {
  padding: 12px 16px;
  text-align: left;
  font-size: 14px;
  border-bottom: 1px solid var(--color-border);
}

.data-table th {
  background: var(--color-bg-alt);
  font-weight: 600;
  color: var(--color-text-muted);
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.title-cell {
  font-weight: 500;
  max-width: 300px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
  background: rgba(59, 130, 246, 0.1);
  color: var(--color-primary);
}

.actions-cell {
  display: flex;
  gap: 8px;
}

.btn-text {
  background: none;
  border: none;
  color: var(--color-primary);
  cursor: pointer;
  font-size: 13px;
  padding: 4px 8px;
  border-radius: 4px;
}

.btn-text:hover {
  background: rgba(59, 130, 246, 0.1);
}

.btn-danger {
  color: #ef4444;
}

.btn-danger:hover {
  background: rgba(239, 68, 68, 0.1);
}

.empty-row {
  text-align: center;
  color: var(--color-text-muted);
  padding: 40px !important;
}

.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 200;
}

.modal {
  background: var(--color-surface);
  border-radius: 12px;
  width: 500px;
  max-width: 90vw;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.2);
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 24px;
  border-bottom: 1px solid var(--color-border);
}

.modal-header h3 {
  margin: 0;
  font-size: 18px;
  color: var(--color-text);
}

.modal-close {
  background: none;
  border: none;
  font-size: 24px;
  color: var(--color-text-muted);
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 4px;
}

.modal-close:hover {
  background: var(--color-bg-alt);
}

.modal-body {
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-group label {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-muted);
}

.form-group input,
.form-group select,
.form-group textarea {
  padding: 10px 12px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-bg);
  color: var(--color-text);
  font-size: 14px;
  outline: none;
  font-family: inherit;
}

.form-group input:focus,
.form-group select:focus,
.form-group textarea:focus {
  border-color: var(--color-primary);
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 24px;
  border-top: 1px solid var(--color-border);
}
</style>
