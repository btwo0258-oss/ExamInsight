import { createRouter, createWebHistory } from 'vue-router'
import ChatView from '@/views/ChatView.vue'
import KnowledgeBaseView from '@/views/KnowledgeBaseView.vue'
import { getStoredToken } from '@/api/request'
import { useAuthStore as useAdminAuthStore } from '@/stores/adminAuth'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', redirect: '/chat' },
    { path: '/chat', name: 'chat', component: ChatView },
    { path: '/chat/:id', name: 'chat-detail', component: ChatView, props: true },
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
  if (!token && to.path !== '/chat' && !to.path.startsWith('/chat')) {
    // If not logged in and trying to access knowledge base, redirect to chat (which acts as home)
    next('/chat')
  } else {
    next()
  }
})

export default router
