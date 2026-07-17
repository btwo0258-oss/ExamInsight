import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore as useAdminAuthStore } from '@/stores/adminAuth'
import { useAuthStore } from '@/stores/auth'

const requiresAuth = { requiresAuth: true }

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', redirect: '/chat' },
    { path: '/chat', name: 'chat', component: () => import('@/views/student/chat/StudentChatView.vue') },
    { path: '/chat/:id', name: 'chat-detail', component: () => import('@/views/student/chat/StudentChatView.vue'), props: true, meta: requiresAuth },
    { path: '/presentations/new', name: 'presentation-new', component: () => import('@/views/student/presentation/PresentationWorkspaceView.vue'), meta: requiresAuth },
    { path: '/presentations/:id', name: 'presentation-detail', component: () => import('@/views/student/presentation/PresentationWorkspaceView.vue'), props: true, meta: requiresAuth },
    {
      path: '/spreadsheets/new',
      name: 'spreadsheet-new',
      component: () => import('@/views/student/chat/StudentChatView.vue'),
      meta: requiresAuth,
    },
    { path: '/learning', redirect: '/learning/projects', meta: requiresAuth },
    { path: '/learning/new', name: 'learning-new', component: () => import('@/views/student/chat/StudentChatView.vue'), meta: requiresAuth },
    { path: '/learning/setup/:id', name: 'learning-setup', component: () => import('@/views/student/chat/StudentChatView.vue'), props: true, meta: requiresAuth },
    { path: '/learning/projects', name: 'learning-projects', component: () => import('@/views/student/learning/LearningProjectsView.vue'), meta: requiresAuth },
    { path: '/learning/:id', name: 'learning-plan', component: () => import('@/views/student/learning/LearningPlanView.vue'), props: true, meta: requiresAuth },
    { path: '/learning/:id/study', name: 'learning-study', component: () => import('@/views/student/learning/LearningStudyView.vue'), props: true, meta: requiresAuth },
    { path: '/learning/:id/practice', redirect: (to) => `/learning/${String(to.params.id)}/study` },
    { path: '/learning/:id/mistakes', name: 'learning-mistakes', component: () => import('@/views/student/learning/LearningMistakesView.vue'), props: true, meta: requiresAuth },
    { path: '/learning/:id/resources', name: 'learning-resources', component: () => import('@/views/student/learning/LearningResourcesView.vue'), props: true, meta: requiresAuth },
    { path: '/library', name: 'library-home', component: () => import('@/views/student/library/LibraryHomeView.vue'), meta: requiresAuth },
    { path: '/library/:id', name: 'library-detail', component: () => import('@/views/student/library/LibraryDetailView.vue'), props: true, meta: requiresAuth },
    { path: '/resources/:resourceId/preview', name: 'resource-preview', component: () => import('@/views/student/resource/ResourcePreviewView.vue'), props: true, meta: requiresAuth },
    { path: '/resource', name: 'resource-center', component: () => import('@/views/ResourceCenterView.vue'), meta: requiresAuth },
    { path: '/exam-analysis', name: 'exam-analysis-list', component: () => import('@/views/ExamAnalysisListView.vue'), meta: requiresAuth },
    { path: '/exam-analysis/:id', name: 'exam-analysis-detail', component: () => import('@/views/ExamAnalysisView.vue'), props: true, meta: requiresAuth },
    { path: '/knowledge', redirect: '/library' },
    { path: '/knowledge/:id', redirect: (to) => `/library/${String(to.params.id)}` },
    { path: '/mindmap', name: 'mindmap-list', component: () => import('@/views/mindmap/MindMapListView.vue'), meta: requiresAuth },
    { path: '/mindmap/:id', name: 'mindmap-detail', component: () => import('@/views/mindmap/MindMapView.vue'), meta: requiresAuth },
    // Admin routes
    {
      path: '/admin/login',
      name: 'admin-login',
      component: () => import('@/views/admin/login/index.vue'),
      meta: { isAdmin: true, requiresAdminAuth: false }
    },
    {
      path: '/admin',
      component: () => import('@/layout_admin/AdminLayout.vue'),
      meta: { isAdmin: true, requiresAdminAuth: true },
      children: [
        {
          path: '',
          redirect: '/admin/dashboard'
        },
        {
          path: 'dashboard',
          name: 'admin-dashboard',
          component: () => import('@/views/admin/dashboard/index.vue')
        },
        {
          path: 'users',
          name: 'admin-users',
          component: () => import('@/views/admin/users/index.vue')
        },
        {
          path: 'system-config',
          name: 'admin-system-config',
          component: () => import('@/views/admin/system-config/index.vue')
        },
        {
          path: 'resources',
          name: 'admin-resources',
          component: () => import('@/views/admin/resources/index.vue')
        }
      ]
    }
  ],
})

router.beforeEach((to, from, next) => {
  if (to.meta.isAdmin) {
    const adminAuthStore = useAdminAuthStore()
    if (to.meta.requiresAdminAuth && !adminAuthStore.isAuthenticated) {
      next('/admin/login')
    } else if (to.name === 'admin-login' && adminAuthStore.isAuthenticated) {
      next('/admin/dashboard')
    } else {
      next()
    }
    return
  }

  const authStore = useAuthStore()
  authStore.init()
  if (to.meta.requiresAuth && !authStore.isAuthed) {
    authStore.openAuthModal(to.fullPath)
    next({ name: 'chat', replace: true })
    return
  }

  next()
})

export default router
