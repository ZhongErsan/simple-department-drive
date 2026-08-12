import { createRouter, createWebHistory } from 'vue-router'
import { authState } from '../store/auth'

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/LoginView.vue')
  },
  {
    path: '/',
    component: () => import('../layout/AppLayout.vue'),
    children: [
      {
        path: '',
        redirect: '/dashboard'
      },
      {
        path: 'dashboard',
        name: 'dashboard',
        component: () => import('../views/DashboardView.vue')
      },
      {
        path: 'drive',
        name: 'drive',
        component: () => import('../views/DriveView.vue')
      },
      {
        path: 'trash',
        name: 'trash',
        component: () => import('../views/TrashView.vue')
      },
      {
        path: 'profile',
        name: 'profile',
        component: () => import('../views/ProfileView.vue')
      },
      {
        path: 'users',
        name: 'users',
        component: () => import('../views/UsersView.vue'),
        meta: {
          roles: ['ADMIN', 'MINISTER']
        }
      },
      {
        path: 'departments',
        name: 'departments',
        component: () => import('../views/DepartmentsView.vue'),
        meta: {
          roles: ['ADMIN']
        }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    component: () => import('../views/NotFoundView.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  if (to.path === '/login') {
    return authState.token ? '/dashboard' : true
  }

  if (!authState.token) {
    return {
      path: '/login',
      query: {
        redirect: to.fullPath
      }
    }
  }

  const roles = to.meta.roles
  if (Array.isArray(roles) && !roles.includes(authState.role)) {
    return '/dashboard'
  }

  return true
})

export default router
