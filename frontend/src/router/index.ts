import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const requiresAuth = { requiresAuth: true }

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', redirect: '/chat' },
    { path: '/terms', name: 'terms', component: () => import('@/views/legal/LegalDocumentView.vue'), props: { documentType: 'terms' } },
    { path: '/privacy', name: 'privacy', component: () => import('@/views/legal/LegalDocumentView.vue'), props: { documentType: 'privacy' } },
    { path: '/chat', name: 'chat', component: () => import('@/views/student/chat/StudentChatView.vue') },
    { path: '/chat/:id', name: 'chat-detail', component: () => import('@/views/student/chat/StudentChatView.vue'), props: true, meta: requiresAuth },
    { path: '/artifacts/:artifactId/edit', name: 'artifact-editor', component: () => import('@/views/student/artifact/ArtifactEditorView.vue'), props: true, meta: requiresAuth },
    { path: '/library', name: 'library-home', component: () => import('@/views/student/library/LibraryHomeView.vue'), meta: requiresAuth },
    { path: '/library/:id', name: 'library-detail', component: () => import('@/views/student/library/LibraryDetailView.vue'), props: true, meta: requiresAuth },
    { path: '/resources/:resourceId/preview', name: 'resource-preview', component: () => import('@/views/student/resource/ResourcePreviewView.vue'), props: true, meta: requiresAuth },
    { path: '/learning', name: 'learning-projects', component: () => import('@/views/student/learning/SmartLearningProjectsView.vue'), meta: requiresAuth },
    { path: '/learning/:id/setup', name: 'learning-setup', component: () => import('@/views/student/learning/SmartLearningSetupView.vue'), props: true, meta: requiresAuth },
    { path: '/learning/:id/mistakes', name: 'learning-mistakes', component: () => import('@/views/student/learning/SmartLearningMistakesView.vue'), props: true, meta: requiresAuth },
    { path: '/learning/:id/resources', name: 'learning-resources', component: () => import('@/views/student/learning/SmartLearningResourcesView.vue'), props: true, meta: requiresAuth },
    { path: '/learning/:id/task/:taskId', name: 'learning-task', component: () => import('@/views/student/learning/SmartLearningTaskView.vue'), props: true, meta: requiresAuth },
    { path: '/learning/:id', name: 'learning-workbench', component: () => import('@/views/student/learning/SmartLearningWorkbenchView.vue'), props: true, meta: requiresAuth },
    { path: '/knowledge', redirect: '/library' },
    { path: '/knowledge/:id', redirect: (to) => `/library/${String(to.params.id)}` },
    { path: '/:pathMatch(.*)*', redirect: '/chat' },
  ],
})

router.beforeEach(async (to, from, next) => {
  const authStore = useAuthStore()
  await authStore.init()
  if (to.meta.requiresAuth && !authStore.isAuthed) {
    authStore.openAuthModal(to.fullPath)
    next({ name: 'chat', replace: true })
    return
  }

  next()
})

export default router
