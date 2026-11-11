import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router';
import { useAuthStore } from '@/stores/auth';
import Layout from '@/layout/index.vue';
import LoginView from '@/views/login/index.vue';
import DashboardView from '@/views/dashboard/index.vue';
import MessageView from '@/views/message/index.vue';
import ForbiddenView from '@/views/error/403.vue';
import NotFoundView from '@/views/error/404.vue';

const staticRoutes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: LoginView,
    meta: { title: '登录' },
  },
  {
    path: '/',
    name: 'Root',
    component: Layout,
    redirect: '/dashboard',
    children: [
      {
        path: '/dashboard',
        name: 'Dashboard',
        component: DashboardView,
        meta: { title: '工作台', icon: 'HomeFilled' },
      },
      {
        // 我的消息：所有登录用户可用，不进侧栏菜单，由顶栏铃铛进入
        path: '/messages',
        name: 'MyMessages',
        component: MessageView,
        meta: { title: '我的消息', hidden: true },
      },
    ],
  },
  {
    path: '/403',
    name: 'Forbidden',
    component: ForbiddenView,
    meta: { title: '无权访问' },
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: NotFoundView,
    meta: { title: '页面不存在' },
  },
];

export const router = createRouter({
  history: createWebHistory(),
  routes: staticRoutes,
});

const viewModules = import.meta.glob('../views/**/*.vue');

let dynamicRegistered = false;

export function registerDynamicRoutes(): void {
  const auth = useAuthStore();
  if (dynamicRegistered) {
    return;
  }
  const flat: Array<{ path: string; name: string; component: string; meta: Record<string, unknown> }> = [];
  const walk = (items: import('@/api/types').MenuRoute[]): void => {
    for (const item of items) {
      const component = item.component;
      if (component && component !== 'Layout' && item.path) {
        flat.push({
          path: item.path,
          name: `menu-${item.id}`,
          component,
          meta: {
            title: item.meta?.title ?? item.name,
            icon: item.meta?.icon ?? null,
            hidden: Boolean(item.meta?.hidden),
            permission: item.meta?.permission ?? null,
          },
        });
      }
      if (item.children?.length) {
        walk(item.children);
      }
    }
  };
  walk(auth.menuRoutes);

  for (const item of flat) {
    const key = `../views/${item.component}.vue`;
    const loader = viewModules[key];
    if (!loader) {
      console.warn(`[router] 视图文件不存在：src/views/${item.component}.vue`);
      continue;
    }
    if (router.hasRoute(item.name)) {
      continue;
    }
    router.addRoute('Root', {
      path: item.path,
      name: item.name,
      component: loader,
      meta: item.meta,
    });
  }
  dynamicRegistered = true;
}

export function resetDynamicRoutes(): void {
  dynamicRegistered = false;
}

// 公开路由：未登录也能访问。
const PUBLIC_PATHS = ['/login'];

router.beforeEach(async (to) => {
  const auth = useAuthStore();
  if (PUBLIC_PATHS.includes(to.path)) {
    return to.path === '/login' && auth.isLoggedIn ? { path: '/' } : true;
  }
  if (!auth.loaded) {
    try {
      await auth.bootstrap();
      registerDynamicRoutes();
      return { path: to.fullPath, replace: true };
    } catch {
      auth.reset();
      return { path: '/login', query: { redirect: to.fullPath } };
    }
  }
  registerDynamicRoutes();
  const permission = to.meta.permission as string | null | undefined;
  if (permission && !auth.hasPermission(permission)) {
    return { path: '/403' };
  }
  return true;
});
