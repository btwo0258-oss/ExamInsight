import { createRouter, createWebHistory } from 'vue-router'
import KnowledgeBaseView from '@/views/KnowledgeBaseView.vue'
import { getStoredToken } from '@/api/request'
import { useAuthStore as useAdminAuthStore } from '@/stores/adminAuth'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', redirect: '/learning/projects' },
    { path: '/chat', name: 'chat', component: () => import('@/views/student/chat/StudentChatView.vue') },
    { path: '/chat/:id', name: 'chat-detail', component: () => import('@/views/student/chat/StudentChatView.vue'), props: true },
    { path: '/learning', redirect: '/learning/projects' },
    { path: '/learning/new', name: 'learning-new', component: () => import('@/views/student/learning/LearningHomeView.vue') },
    { path: '/learning/projects', name: 'learning-projects', component: () => import('@/views/student/learning/LearningProjectsView.vue') },
    { path: '/learning/:id', name: 'learning-plan', component: () => import('@/views/student/learning/LearningPlanView.vue'), props: true },
    { path: '/learning/:id/study', name: 'learning-study', component: () => import('@/views/student/learning/LearningStudyView.vue'), props: true },
    { path: '/learning/:id/practice', redirect: (to) => `/learning/${String(to.params.id)}/study` },
    { path: '/learning/:id/mistakes', name: 'learning-mistakes', component: () => import('@/views/student/learning/LearningMistakesView.vue'), props: true },
    { path: '/learning/:id/resources', name: 'learning-resources', component: () => import('@/views/student/learning/LearningResourcesView.vue'), props: true },
    { path: '/library', name: 'library-home', component: () => import('@/views/student/library/LibraryHomeView.vue') },
    { path: '/library/:id', name: 'library-detail', component: () => import('@/views/student/library/LibraryDetailView.vue'), props: true },
    { path: '/resource', name: 'resource-center', component: () => import('@/views/ResourceCenterView.vue') },
    { path: '/exam-analysis', name: 'exam-analysis-list', component: () => import('@/views/ExamAnalysisListView.vue') },
    { path: '/exam-analysis/:id', name: 'exam-analysis-detail', component: () => import('@/views/ExamAnalysisView.vue'), props: true },
    { path: '/knowledge', name: 'knowledge', component: KnowledgeBaseView },
    { path: '/knowledge/:id', name: 'knowledge-detail', component: KnowledgeBaseView, props: true },
    { path: '/mindmap', name: 'mindmap-list', component: () => import('@/views/mindmap/MindMapListView.vue') },
    { path: '/mindmap/:id', name: 'mindmap-detail', component: () => import('@/views/mindmap/MindMapView.vue') },
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

  const token = getStoredToken()
  const publicStudentPath =
    to.path === '/learning' ||
    to.path.startsWith('/learning/') ||
    to.path === '/library' ||
    to.path.startsWith('/library/')

  if (!token && !publicStudentPath && to.path !== '/chat' && !to.path.startsWith('/chat')) {
    // Student mock pages and quick chat are available before login.
    next('/chat')
  } else {
    next()
  }
})

export default router
