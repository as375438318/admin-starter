import { createApp } from 'vue';
import { createPinia } from 'pinia';
import ElementPlus from 'element-plus';
import zhCn from 'element-plus/es/locale/lang/zh-cn';
import * as ElementPlusIconsVue from '@element-plus/icons-vue';
import 'element-plus/dist/index.css';
import '@admin-starter/ui-tokens/tokens.css';
import '@/styles/index.css';
import AppVue from './App.vue';
import { router } from './router';
import { setupPermissionDirective } from './directives/permission';
import { useAppConfigStore } from './stores/appConfig';

const app = createApp(AppVue);
const pinia = createPinia();

app.use(pinia);
app.use(router);
app.use(ElementPlus, { locale: zhCn });

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component);
}

setupPermissionDirective(app);

// 先取公开配置再挂载，避免登录页标题从默认值闪变；最长等待 1.5s，失败用默认值兜底。
void useAppConfigStore(pinia).load().finally(() => {
  app.mount('#app');
});
