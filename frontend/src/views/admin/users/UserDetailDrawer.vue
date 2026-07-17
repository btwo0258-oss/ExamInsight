<script setup lang="ts">
import AppIcon from "@/components/admin/AppIcon.vue";
import AppButton from "@/components/admin/AppButton.vue";
import ConfirmDialog from "@/components/admin/ConfirmDialog.vue";
import { ref, watch } from "vue";
import { getUserDetail, resetPassword } from "@/api/adminUser";

const props = defineProps<{
  open: boolean;
  user: any;
}>();

const emit = defineEmits(["close", "reset-password"]);

const fullUser = ref<any>(null);
const loading = ref(false);

async function fetchDetail() {
  if (!props.user?.id) return;
  loading.value = true;
  try {
    const data = await getUserDetail(props.user.id);
    fullUser.value = data;
  } catch (err) {
    console.error("Failed to fetch user detail:", err);
  } finally {
    loading.value = false;
  }
}

watch(
  () => props.open,
  (newVal) => {
    if (newVal) {
      fetchDetail();
    } else {
      fullUser.value = null;
    }
  },
);

const isResetting = ref(false);
const showResetConfirm = ref(false);

function getThemeText(theme: string | undefined): string {
  if (!theme || theme === "system") return "跟随系统";
  if (theme === "light") return "浅色";
  if (theme === "dark") return "深色";
  return theme;
}

function handleResetPassword() {
  showResetConfirm.value = true;
}

function confirmReset() {
  showResetConfirm.value = false;
  isResetting.value = true;
  resetPassword(props.user.id).then(() => {
    emit('reset-password', props.user.id);
    if (fullUser.value) {
      fullUser.value.hasForgotRequest = false;
      if (fullUser.value.stats) {
        fullUser.value.stats.hasForgotRequest = 0;
      }
    }
  }).finally(() => {
    isResetting.value = false;
  });
}
</script>

<template>
  <div class="drawer-mask" v-if="open" @click="emit('close')">
    <div class="drawer" @click.stop>
      <!-- ... existing content ... -->
      <div class="drawer-header">
        <h3 class="drawer-title">用户详情</h3>
        <button class="close-btn" @click="emit('close')">
          <AppIcon name="close" :size="20" />
        </button>
      </div>

      <div class="drawer-body" v-if="fullUser || user">
        <div v-if="loading" class="loading-state">加载中...</div>
        <template v-else-if="fullUser || user">
          <!-- Basic Info -->
          <div class="detail-section">
            <div class="user-profile">
              <div class="avatar">
                <AppIcon name="user-circle" :size="32" />
              </div>
              <div class="profile-info">
                <div class="nickname">{{ (fullUser || user).nickname }}</div>
                <div class="username">@{{ (fullUser || user).username }}</div>
              </div>
              <span :class="['status-tag', (fullUser || user).status]">
                {{ (fullUser || user).status === "normal" ? "正常" : "封禁" }}
              </span>
            </div>
          </div>

          <!-- Stats -->
          <div class="detail-section">
            <h4 class="section-title">业务统计</h4>
            <div class="stats-grid">
              <div class="stat-item">
                <div class="stat-icon message">
                  <AppIcon name="message-square" :size="18" />
                </div>
                <div class="stat-content">
                  <div class="stat-label">对话总数</div>
                  <div class="stat-value">{{ (fullUser || user).stats?.convCount || 0 }}</div>
                </div>
              </div>
              <div class="stat-item">
                <div class="stat-icon book">
                  <AppIcon name="book" :size="18" />
                </div>
                <div class="stat-content">
                  <div class="stat-label">知识库</div>
                  <div class="stat-value">{{ (fullUser || user).stats?.kbCount || 0 }}</div>
                </div>
              </div>
              <div class="stat-item">
                <div class="stat-icon file">
                  <AppIcon name="paperclip" :size="18" />
                </div>
                <div class="stat-content">
                  <div class="stat-label">附件数量</div>
                  <div class="stat-value">{{ (fullUser || user).stats?.fileCount || 0 }}</div>
                </div>
              </div>
              <div class="stat-item">
                <div class="stat-icon mindmap">
                  <AppIcon name="layers" :size="18" />
                </div>
                <div class="stat-content">
                  <div class="stat-label">思维导图</div>
                  <div class="stat-value">{{ (fullUser || user).stats?.mindMapCount || 0 }}</div>
                </div>
              </div>
            </div>
          </div>

          <!-- User Settings -->
          <div class="detail-section">
            <h4 class="section-title">用户偏好</h4>
            <div class="settings-grid">
              <div class="setting-item">
                <div class="setting-label">界面主题</div>
                <div class="setting-value">
                  {{ getThemeText((fullUser || user).settings?.theme) }}
                </div>
              </div>
              <div class="setting-item">
                <div class="setting-label">默认模型</div>
                <div class="setting-value">
                  {{ (fullUser || user).settings?.defaultModel || "qwen-plus-2025-07-28" }}
                </div>
              </div>
            </div>
          </div>

          <!-- Account Security -->
          <div class="detail-section">
            <h4 class="section-title">账户安全</h4>
            <div class="security-card">
              <div class="security-info">
                <div class="security-label">重置密码</div>
                <div class="security-desc">重置后默认密码为 123456</div>
              </div>
              <AppButton
                variant="secondary"
                size="small"
                :loading="isResetting"
                @click="handleResetPassword"
              >
                重置
              </AppButton>
            </div>

            <div v-if="(fullUser || user).hasForgotRequest || (fullUser || user).stats?.hasForgotRequest" class="forgot-alert">
              <AppIcon name="info" :size="16" />
              <span>该用户发起了“忘记密码”请求，请及时处理</span>
            </div>
          </div>

          <!-- Timeline -->
          <div class="detail-section">
            <h4 class="section-title">时间线</h4>
            <div class="timeline">
              <div class="timeline-item">
                <div class="time">{{ (fullUser || user).registerTime }}</div>
                <div class="event">用户注册</div>
              </div>
              <div class="timeline-item">
                <div class="time">{{ (fullUser || user).lastLogin }}</div>
                <div class="event">最后登录</div>
              </div>
            </div>
          </div>
        </template>
      </div>
    </div>

    <!-- 确认重置密码弹窗 -->
    <ConfirmDialog
      :open="showResetConfirm"
      title="重置密码确认"
      :message="`确定要重置用户 ${user?.username} 的密码吗？重置后默认密码为 123456。`"
      confirm-text="确认重置"
      @close="showResetConfirm = false"
      @confirm="confirmReset"
    />
  </div>
</template>

<style scoped>
.drawer-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.2);
  z-index: 1001;
  display: flex;
  justify-content: flex-end;
}

.drawer {
  width: 400px;
  height: 100%;
  background: var(--color-surface);
  box-shadow: -4px 0 24px rgba(0, 0, 0, 0.1);
  display: flex;
  flex-direction: column;
  animation: slideIn 0.3s ease-out;
}

@keyframes slideIn {
  from {
    transform: translateX(100%);
  }
  to {
    transform: translateX(0);
  }
}

.drawer-header {
  padding: 20px 24px;
  border-bottom: 1px solid var(--color-border);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.drawer-title {
  font-size: 18px;
  font-weight: 600;
  margin: 0;
}

.close-btn {
  background: none;
  border: none;
  cursor: pointer;
  color: var(--color-text-muted);
  padding: 4px;
  border-radius: 4px;
  transition: all 0.2s;
}

.close-btn:hover {
  background: var(--color-bg-alt);
  color: var(--color-text);
}

.drawer-body {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 32px;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin: 0 0 16px 0;
}

.user-profile {
  display: flex;
  align-items: center;
  gap: 16px;
}

.avatar {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: var(--color-bg-alt);
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--color-border);
}

.profile-info {
  flex: 1;
}

.nickname {
  font-size: 18px;
  font-weight: 700;
  color: var(--color-text);
}

.username {
  font-size: 14px;
  color: var(--color-text-muted);
}

.status-tag {
  padding: 4px 10px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 600;
}

.status-tag.normal {
  background: #ecfdf5;
  color: #10b981;
}

.status-tag.banned {
  background: #fef2f2;
  color: #ef4444;
}

.stats-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 12px;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  background: var(--color-bg-alt);
  border-radius: var(--radius-sm);
  border: 1px solid var(--color-border);
}

.stat-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.stat-icon.message {
  background: #e0f2fe;
  color: #0ea5e9;
}
.stat-icon.book {
  background: #fef3c7;
  color: #d97706;
}
.stat-icon.file {
  background: #f3e8ff;
  color: #9333ea;
}
.stat-icon.mindmap {
  background: #ffe4e6;
  color: #f43f5e;
}

.stat-label {
  font-size: 12px;
  color: var(--color-text-muted);
}

.stat-value {
  font-size: 16px;
  font-weight: 700;
  color: var(--color-text);
}

.settings-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.setting-item {
  padding: 12px;
  background-color: var(--color-bg);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
}

.setting-label {
  font-size: 12px;
  color: var(--color-text-light);
  margin-bottom: 4px;
}

.setting-value {
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text);
}

.security-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  background: var(--color-bg-alt);
  border-radius: var(--radius-sm);
  border: 1px solid var(--color-border);
}

.security-label {
  font-size: 14px;
  font-weight: 600;
}

.security-desc {
  font-size: 12px;
  color: var(--color-text-muted);
}

.forgot-alert {
  margin-top: 12px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  background: #fffbeb;
  border: 1px solid #fef3c7;
  border-radius: 8px;
  color: #d97706;
  font-size: 13px;
}

.timeline {
  display: flex;
  flex-direction: column;
  gap: 16px;
  position: relative;
  padding-left: 20px;
}

.timeline::before {
  content: "";
  position: absolute;
  left: 4px;
  top: 4px;
  bottom: 4px;
  width: 2px;
  background: var(--color-border);
}

.timeline-item {
  position: relative;
}

.timeline-item::before {
  content: "";
  position: absolute;
  left: -20px;
  top: 6px;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--color-surface);
  border: 2px solid var(--color-primary);
}

.time {
  font-size: 12px;
  color: var(--color-text-muted);
  margin-bottom: 2px;
}

.event {
  font-size: 14px;
  font-weight: 500;
}
</style>
