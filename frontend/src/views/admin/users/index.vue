<script setup lang="ts">
import { ref, computed, onMounted } from "vue";
import AppIcon from "@/components/admin/AppIcon.vue";
import AppButton from "@/components/admin/AppButton.vue";
import AppModal from "@/components/admin/AppModal.vue";
import ConfirmDialog from "@/components/admin/ConfirmDialog.vue";
import UserDetailDrawer from "./UserDetailDrawer.vue";
import { getUserList, updateStatus, resetPassword, handleResetRequest } from "@/api/adminUser";
import type { AdminUser as User } from "@/api/adminUser";

// Data
const users = ref<User[]>([]);
const loading = ref(false);
const total = ref(0);
const pageSize = ref(10);
const currentPage = ref(1);

async function fetchUsers() {
  loading.value = true;
  try {
    const data = await getUserList({});
    users.value = data;
    total.value = data.length;
  } catch (err) {
    console.error("Failed to fetch users:", err);
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  fetchUsers();
});

const searchQuery = ref("");

const totalPages = computed(() => Math.ceil(filteredUsers.value.length / pageSize.value) || 1);

const filteredUsers = computed(() => {
  let list = users.value;
  if (searchQuery.value) {
    const q = searchQuery.value.toLowerCase();
    list = list.filter(
      (u) => u.username.toLowerCase().includes(q) || u.nickname.toLowerCase().includes(q),
    );
  }
  return list;
});

const paginatedUsers = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value;
  const end = start + pageSize.value;
  return filteredUsers.value.slice(start, end);
});

const showConfirm = ref(false);
const confirmTarget = ref<any>(null);

const showDrawer = ref(false);
const selectedUser = ref<any>(null);

const showResetSuccess = ref(false);
const resetSuccessUser = ref("");

function handleRowClick(user: any) {
  selectedUser.value = user;
  showDrawer.value = true;
}

function toggleStatus(user: any) {
  confirmTarget.value = user;
  showConfirm.value = true;
}

async function handleConfirm() {
  if (confirmTarget.value) {
    const newStatus = confirmTarget.value.status === "normal" ? "banned" : "normal";
    try {
      await updateStatus(confirmTarget.value.id, newStatus);
      confirmTarget.value.status = newStatus;
    } catch (err) {
      console.error("Failed to update status:", err);
    }
  }
  showConfirm.value = false;
}

function handleResetPassword(userId: number) {
  const user = users.value.find((u) => u.id === userId);
  if (user) {
    user.hasForgotRequest = false;
    if (user.stats) {
      user.stats.hasForgotRequest = 0;
    }
    resetSuccessUser.value = user.username;
    showResetSuccess.value = true;
  }
}

function changePage(page: number) {
  if (page < 1 || page > totalPages.value) return;
  currentPage.value = page;
}

const visiblePages = computed(() => {
  const pages: (number | string)[] = [];
  const total = totalPages.value;
  const current = currentPage.value;

  if (total <= 7) {
    for (let i = 1; i <= total; i++) pages.push(i);
  } else {
    pages.push(1);
    if (current > 4) pages.push("...");

    const start = Math.max(2, current - 2);
    const end = Math.min(total - 1, current + 2);

    for (let i = start; i <= end; i++) pages.push(i);

    if (current < total - 3) pages.push("...");
    pages.push(total);
  }
  return pages;
});
</script>

<template>
  <div class="users-page">
    <header class="page-header">
      <div class="header-info">
        <h2 class="page-title">用户管理</h2>
        <p class="page-subtitle">管理系统用户及其访问权限</p>
      </div>

      <div class="header-actions">
        <div class="search-wrap">
          <AppIcon name="search" :size="16" />
          <input v-model="searchQuery" type="text" placeholder="搜索用户名/昵称" />
        </div>
        <AppButton variant="primary">
          <template #icon><AppIcon name="user-plus" :size="16" /></template>
          新增用户
        </AppButton>
      </div>
    </header>

    <div class="table-card card">
      <table class="user-table">
        <thead>
          <tr>
            <th>ID</th>
            <th>用户名</th>
            <th>昵称</th>
            <th>状态</th>
            <th>注册时间</th>
            <th>最后登录</th>
            <th class="text-right">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="user in paginatedUsers"
            :key="user.id"
            class="clickable-row"
            @click="handleRowClick(user)"
          >
            <td>{{ user.id }}</td>
            <td class="font-bold">
              <div class="name-with-alert">
                {{ user.username }}
                <span v-if="user.hasForgotRequest || user.stats?.hasForgotRequest" class="forgot-dot" title="忘记密码请求"></span>
              </div>
            </td>
            <td>{{ user.nickname }}</td>
            <td>
              <span :class="['status-tag', user.status]">
                {{ user.status === "normal" ? "正常" : "封禁" }}
              </span>
            </td>
            <td class="text-muted">{{ user.registerTime }}</td>
            <td class="text-muted">{{ user.lastLogin }}</td>
            <td class="text-right">
              <AppButton
                :variant="user.status === 'normal' ? 'danger' : 'secondary'"
                size="small"
                @click.stop="toggleStatus(user)"
              >
                {{ user.status === "normal" ? "封禁" : "解封" }}
              </AppButton>
            </td>
          </tr>
        </tbody>
      </table>

      <div class="table-footer">
        <div class="pagination">
          <button
            class="page-btn"
            :class="{ disabled: currentPage === 1 }"
            @click="changePage(currentPage - 1)"
          >
            <AppIcon name="chevron-left" :size="14" />
          </button>
          <template
            v-for="p in visiblePages"
            :key="typeof p === 'number' ? p : 'ellipsis-' + Math.random()"
          >
            <button
              v-if="typeof p === 'number'"
              class="page-btn"
              :class="{ active: currentPage === p }"
              @click="changePage(p)"
            >
              {{ p }}
            </button>
            <span v-else class="page-ellipsis">...</span>
          </template>
          <button
            class="page-btn"
            :class="{ disabled: currentPage === totalPages }"
            @click="changePage(currentPage + 1)"
          >
            <AppIcon name="chevron-right" :size="14" />
          </button>
        </div>
      </div>
    </div>

    <!-- Confirm Dialog -->
    <ConfirmDialog
      :open="showConfirm"
      :title="confirmTarget?.status === 'normal' ? '封禁确认' : '解封确认'"
      :message="`您确定要${confirmTarget?.status === 'normal' ? '封禁' : '解封'}用户 ${confirmTarget?.username} 吗？`"
      :variant="confirmTarget?.status === 'normal' ? 'danger' : 'primary'"
      @close="showConfirm = false"
      @confirm="handleConfirm"
    />

    <!-- Reset Success Modal -->
    <ConfirmDialog
      :open="showResetSuccess"
      title="操作成功"
      :message="`用户 ${resetSuccessUser} 的密码已成功重置为 123456。`"
      confirm-text="知道了"
      :cancel-text="''"
      @confirm="showResetSuccess = false"
      @close="showResetSuccess = false"
    />

    <!-- User Detail Drawer -->
    <UserDetailDrawer
      :open="showDrawer"
      :user="selectedUser"
      @close="showDrawer = false"
      @reset-password="handleResetPassword"
    />
  </div>
</template>

<style scoped>
.users-page {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.page-title {
  font-size: 20px;
  font-weight: 700;
}

.page-subtitle {
  font-size: 14px;
  color: var(--color-text-muted);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 16px;
}

.search-wrap {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 14px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background-color: var(--color-surface);
  width: 280px;
}

.search-wrap input {
  flex: 1;
  border: none;
  background: none;
  outline: none;
  font-size: 14px;
}

.table-card {
  overflow: hidden;
}

.user-table {
  width: 100%;
  border-collapse: collapse;
}

.user-table th,
.user-table td {
  padding: 16px 20px;
  text-align: left;
  border-bottom: 1px solid var(--color-border);
  font-size: 14px;
}

.user-table th {
  background-color: var(--color-bg-alt);
  font-weight: 600;
  color: var(--color-text-muted);
  text-transform: uppercase;
  font-size: 12px;
  letter-spacing: 0.5px;
}

.clickable-row {
  cursor: pointer;
  transition: background-color 0.2s;
}

.clickable-row:hover {
  background-color: var(--color-bg-alt);
}

.name-with-alert {
  display: flex;
  align-items: center;
  gap: 8px;
}

.forgot-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background-color: var(--color-warning);
  box-shadow:
    0 0 0 2px #fff,
    0 0 4px var(--color-warning);
}

.font-bold {
  font-weight: 600;
}
.text-muted {
  color: var(--color-text-muted);
}
.text-right {
  text-align: right !important;
}

.status-tag {
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
}

.status-tag.normal {
  background-color: #ecfdf5;
  color: var(--color-success);
}

.status-tag.banned {
  background-color: #fef2f2;
  color: var(--color-danger);
}

.table-footer {
  padding: 16px 20px;
  display: flex;
  justify-content: flex-end;
}

.pagination {
  display: flex;
  align-items: center;
  gap: 6px;
}

.page-btn {
  min-width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  background-color: var(--color-surface);
  cursor: pointer;
  font-size: 14px;
  transition: all 0.2s;
  padding: 0 8px;
}

.page-btn:hover:not(.disabled) {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.page-btn.active {
  background-color: var(--color-primary);
  border-color: var(--color-primary);
  color: white;
}

.page-btn.disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.page-ellipsis {
  color: var(--color-text-muted);
  font-size: 12px;
}

.confirm-message {
  font-size: 14px;
  line-height: 1.6;
}

.modal-footer-actions {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
}
</style>
