<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { AUTH_NOTICE_KEY } from '@/api/http';
import { useLoginForm } from '@/composables/use-login-form';
import { useAppConfigStore } from '@/stores/appConfig';

const { username, password, submitting, errorText, fieldErrors, remember, submit } = useLoginForm();
const appConfig = useAppConfigStore();

const showPassword = ref(false);
const usernameInput = ref<HTMLInputElement>();
const passwordInput = ref<HTMLInputElement>();

onMounted(() => {
  // 停用/删除/密码重置等 401 原因由请求层暂存，登录页统一展示一次，避免一律提示“请重新登录”
  try {
    const notice = sessionStorage.getItem(AUTH_NOTICE_KEY);
    if (notice) {
      sessionStorage.removeItem(AUTH_NOTICE_KEY);
      errorText.value = notice;
    }
  } catch {
    // 隐私模式下 sessionStorage 不可用
  }
});

function onSubmit(): void {
  void submit({ username: usernameInput.value, password: passwordInput.value });
}

function onForgot(): void {
  ElMessage.info('请联系管理员重置密码');
}
</script>

<template>
  <div class="login-page">
    <!-- 建筑线稿：纯内联 SVG，坐标与 docs/design/login-mockups-v4/mock-2.html 一致 -->
    <svg class="login-skyline" viewBox="0 0 1440 300" preserveAspectRatio="none" aria-hidden="true">
      <!-- 远景剪影 fill 取自 --color-primary-light-8（#dbe7fd），叠加 opacity 还原雾感 -->
      <g fill="#dbe7fd" opacity=".85">
        <rect x="30" y="150" width="110" height="150" />
        <rect x="358" y="118" width="76" height="182" />
        <rect x="700" y="166" width="150" height="134" />
        <rect x="1000" y="132" width="84" height="168" />
        <rect x="1250" y="172" width="130" height="128" />
      </g>
      <!-- 近景楼体：fill 取自 --color-primary-light-9（#eff6ff），stroke 取自 --color-primary-light-8（#dbe7fd） -->
      <g fill="#eff6ff" stroke="#dbe7fd" stroke-width="1">
        <rect x="92" y="190" width="92" height="110" />
        <rect x="210" y="158" width="50" height="142" />
        <rect x="222" y="140" width="28" height="18" />
        <rect x="300" y="216" width="126" height="84" />
        <rect x="452" y="162" width="62" height="138" />
        <rect x="580" y="196" width="136" height="104" />
        <rect x="776" y="136" width="54" height="164" />
        <rect x="868" y="188" width="102" height="112" />
        <rect x="1044" y="160" width="60" height="140" />
        <rect x="1138" y="204" width="146" height="96" />
        <rect x="1320" y="174" width="72" height="126" />
      </g>
      <!-- 塔尖线条 stroke 取自 --color-primary-light-7（#c3d7fb） -->
      <g stroke="#c3d7fb" stroke-width="1">
        <path d="M222 140h28M205 158v-14M205 144h12" />
        <path d="M496 162v-26M482 136h28" />
        <path d="M1104 160v-22M1094 138h20" />
      </g>
      <!-- 窗格线条 stroke 取自 --color-primary-light-8（#dbe7fd） -->
      <g stroke="#dbe7fd" stroke-width="1">
        <path d="M106 210h64M106 228h64M106 246h64M106 264h64" />
        <path d="M224 176h22M224 194h22M224 212h22" />
        <path d="M316 232h94M316 250h94M316 268h94" />
        <path d="M466 182h34M466 200h34M466 218h34M466 236h34" />
        <path d="M790 158h26M790 176h26M790 194h26M790 212h26" />
        <path d="M1058 182h32M1058 200h32M1058 218h32M1058 236h32" />
        <path d="M1152 222h118M1152 240h118M1152 258h118M1152 276h118" />
        <path d="M1334 194h46M1334 212h46M1334 230h46M1334 248h46" />
      </g>
    </svg>

    <main class="login-stage">
      <section class="login-card">
        <div class="login-brand">
          <span class="login-mark">A</span>
          <div>
            <p class="login-brand-name">{{ appConfig.config.name }}</p>
            <p class="login-brand-sub">{{ appConfig.config.slogan }}</p>
          </div>
        </div>

        <h1 class="login-title">登录</h1>
        <p class="login-sub">使用管理员分配的账号登录系统</p>

        <form class="login-form" novalidate @submit.prevent="onSubmit">
          <div class="login-field" :class="{ 'is-error': fieldErrors.username }">
            <label class="login-label" for="login-username">账号</label>
            <div class="login-control">
              <input
                id="login-username"
                ref="usernameInput"
                v-model="username"
                type="text"
                placeholder="请输入账号"
                autocomplete="username"
                :aria-invalid="Boolean(fieldErrors.username)"
                :aria-describedby="fieldErrors.username ? 'login-username-error' : undefined"
              />
            </div>
            <p v-if="fieldErrors.username" id="login-username-error" class="login-field-error" role="alert">
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round">
                <circle cx="12" cy="12" r="9" /><path d="M12 7.5v5.5M12 16.5v.5" />
              </svg>
              {{ fieldErrors.username }}
            </p>
          </div>

          <div class="login-field" :class="{ 'is-error': fieldErrors.password }">
            <label class="login-label" for="login-password">密码</label>
            <div class="login-control login-control--eye">
              <input
                id="login-password"
                ref="passwordInput"
                v-model="password"
                :type="showPassword ? 'text' : 'password'"
                placeholder="请输入密码"
                autocomplete="current-password"
                :aria-invalid="Boolean(fieldErrors.password)"
                :aria-describedby="fieldErrors.password ? 'login-password-error' : undefined"
              />
              <button
                class="login-eye"
                type="button"
                :aria-label="showPassword ? '隐藏密码' : '显示密码'"
                :aria-pressed="showPassword"
                @click="showPassword = !showPassword"
              >
                <svg v-if="showPassword" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round">
                  <path d="M3 3l18 18" />
                  <path d="M10.6 6.1A9.7 9.7 0 0 1 12 6c6 0 9.5 6 9.5 6a17 17 0 0 1-2.4 3.2M6.4 6.9A16.6 16.6 0 0 0 2.5 12S6 18 12 18a9.8 9.8 0 0 0 3.5-.7" />
                  <path d="M9.9 9.9a3 3 0 0 0 4.2 4.2" />
                </svg>
                <svg v-else width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round">
                  <path d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12Z" />
                  <circle cx="12" cy="12" r="2.6" />
                </svg>
              </button>
            </div>
            <p v-if="fieldErrors.password" id="login-password-error" class="login-field-error" role="alert">
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round">
                <circle cx="12" cy="12" r="9" /><path d="M12 7.5v5.5M12 16.5v.5" />
              </svg>
              {{ fieldErrors.password }}
            </p>
          </div>

          <div class="login-row">
            <label class="login-check">
              <input v-model="remember" type="checkbox" />
              <span class="login-box" aria-hidden="true"></span>
              <span>记住我 7 天</span>
            </label>
            <a class="login-link" href="#" @click.prevent="onForgot">忘记密码？</a>
          </div>

          <button class="login-submit" type="submit" :disabled="submitting">
            {{ submitting ? '登录中…' : '登 录' }}
          </button>

          <p v-if="errorText" class="login-error" role="alert">{{ errorText }}</p>
          <p class="login-hint">首次登录请及时修改初始密码</p>
        </form>
      </section>
    </main>

    <footer class="login-foot">
      <span>{{ appConfig.config.copyright }}</span>
      <span>{{ appConfig.config.icp }}</span>
    </footer>
  </div>
</template>

<style scoped>
.login-page {
  position: relative;
  width: 100%;
  min-height: 100vh;
  overflow-x: hidden;
  color: var(--color-text-primary);
  background:
    radial-gradient(760px 480px at 18% 6%, color-mix(in srgb, var(--color-surface) 90%, transparent), transparent 70%),
    radial-gradient(720px 460px at 82% 28%, color-mix(in srgb, var(--color-primary) 10%, transparent), transparent 72%),
    linear-gradient(180deg, var(--color-primary-light-9) 0%, var(--color-primary-light-8) 46%, var(--color-canvas) 100%);
}

.login-skyline {
  position: fixed;
  right: 0;
  bottom: 0;
  left: 0;
  width: 100%;
  height: 300px;
  pointer-events: none;
}

.login-stage {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: var(--space-8) var(--space-4) var(--space-16);
}

.login-card {
  width: 400px;
  max-width: 100%;
  padding: 36px 40px 30px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-sm), var(--shadow-xl);
}

.login-brand {
  display: flex;
  align-items: center;
  gap: 10px;
}

.login-mark {
  display: grid;
  flex: none;
  place-items: center;
  width: 44px;
  height: 44px;
  color: var(--color-text-inverse);
  font-size: var(--font-size-xl);
  font-weight: 700;
  background: var(--color-primary);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-md);
}

.login-brand-name {
  margin: 0;
  font-size: var(--font-size-lg);
  font-weight: 600;
  letter-spacing: 0.02em;
}

.login-brand-sub {
  margin: 2px 0 0;
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.login-title {
  margin: 24px 0 0;
  font-size: var(--font-size-2xl);
  font-weight: 600;
  line-height: 1.35;
}

.login-sub {
  margin: 8px 0 0;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  line-height: 1.6;
}

.login-form {
  margin-top: 26px;
}

.login-field {
  margin-bottom: 18px;
}

.login-label {
  display: block;
  margin-bottom: 8px;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.login-control {
  position: relative;
}

.login-control input {
  width: 100%;
  height: 42px;
  padding: 0 14px;
  color: var(--color-text-primary);
  font: inherit;
  font-size: var(--font-size-md);
  outline: none;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  transition: border-color 0.18s, box-shadow 0.18s;
}

.login-control input::placeholder {
  color: var(--color-text-tertiary);
}

.login-control input:focus {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--color-primary) 12%, transparent);
}

.login-control--eye input {
  padding-right: 42px;
}

.login-eye {
  position: absolute;
  top: 50%;
  right: 8px;
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  padding: 0;
  color: var(--color-text-tertiary);
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: var(--radius-sm);
  transform: translateY(-50%);
}

.login-eye:hover,
.login-eye:focus-visible {
  color: var(--color-primary);
}

.login-field.is-error input {
  border-color: var(--color-danger);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--color-danger) 7%, transparent);
}

.login-field-error {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 7px 0 0;
  color: var(--color-danger);
  font-size: var(--font-size-xs);
}

.login-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 2px 0 22px;
}

.login-check {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  cursor: pointer;
}

.login-check input {
  position: absolute;
  width: 0;
  height: 0;
  opacity: 0;
}

.login-box {
  position: relative;
  flex: none;
  width: 16px;
  height: 16px;
  background: var(--color-surface);
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
}

.login-check input:checked + .login-box {
  background: var(--color-primary);
  border-color: var(--color-primary);
}

.login-check input:checked + .login-box::after {
  position: absolute;
  top: 1px;
  left: 4px;
  width: 5px;
  height: 9px;
  content: "";
  border: 1.5px solid var(--color-text-inverse);
  border-top: 0;
  border-left: 0;
  border-radius: 1px;
  transform: rotate(42deg);
}

.login-check input:focus-visible + .login-box {
  box-shadow: 0 0 0 2px var(--color-primary);
}

.login-link {
  color: var(--color-primary);
  font-size: var(--font-size-sm);
  text-decoration: none;
}

.login-link:hover,
.login-link:focus-visible {
  text-decoration: underline;
}

.login-submit {
  width: 100%;
  height: 44px;
  color: var(--color-text-inverse);
  font: inherit;
  font-size: var(--font-size-md);
  font-weight: 500;
  letter-spacing: 0.1em;
  cursor: pointer;
  background: var(--color-primary);
  border: 0;
  border-radius: var(--radius-md);
  box-shadow: 0 8px 20px color-mix(in srgb, var(--color-primary) 26%, transparent);
}

.login-submit:hover {
  background: var(--color-primary-hover);
}

.login-submit:disabled {
  cursor: not-allowed;
  opacity: 0.72;
}

.login-error {
  margin: 14px 0 0;
  padding: 8px 10px;
  color: var(--color-danger);
  font-size: var(--font-size-xs);
  text-align: center;
  background: var(--color-danger-soft);
  border-radius: var(--radius-md);
}

.login-hint {
  margin: 18px 0 0;
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
  text-align: center;
}

.login-foot {
  position: fixed;
  right: 0;
  bottom: 22px;
  left: 0;
  z-index: 1;
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
  letter-spacing: 0.02em;
  text-align: center;
}

.login-foot span {
  margin: 0 6px;
}

@media (max-width: 480px) {
  .login-stage {
    padding: var(--space-6) var(--space-4) var(--space-16);
  }

  .login-card {
    padding: 28px 20px 24px;
  }

  .login-skyline {
    height: 220px;
  }

  .login-foot {
    bottom: 14px;
  }
}
</style>
