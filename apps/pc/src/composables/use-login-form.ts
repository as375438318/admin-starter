import { reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ApiError } from '@/api/http';
import { useAuthStore } from '@/stores/auth';

const REMEMBER_KEY = 'login:last-username';

export type LoginField = 'username' | 'password';

interface FocusTargets {
  username?: HTMLInputElement | null;
  password?: HTMLInputElement | null;
}

/**
 * 登录页共用逻辑：校验、loading、错误落到输入框、记住我、回车提交。
 * 只记忆账号名（localStorage），不保存密码，也不延长令牌有效期（后端未改）。
 */
export function useLoginForm() {
  const authStore = useAuthStore();
  const router = useRouter();
  const route = useRoute();

  const username = ref('');
  const password = ref('');
  const submitting = ref(false);
  const errorText = ref('');
  const remember = ref(true);
  const fieldErrors = reactive<Record<LoginField, string>>({ username: '', password: '' });

  const savedUsername = localStorage.getItem(REMEMBER_KEY);
  if (savedUsername) {
    username.value = savedUsername;
    remember.value = true;
  }

  watch(username, () => {
    if (fieldErrors.username) {
      fieldErrors.username = '';
    }
  });
  watch(password, () => {
    if (fieldErrors.password) {
      fieldErrors.password = '';
    }
  });

  function validate(): boolean {
    fieldErrors.username = username.value.trim() ? '' : '请输入账号';
    fieldErrors.password = password.value ? '' : '请输入密码';
    return !fieldErrors.username && !fieldErrors.password;
  }

  function focusFirstInvalid(els: FocusTargets): void {
    if (fieldErrors.username) {
      els.username?.focus();
    } else if (fieldErrors.password) {
      els.password?.focus();
    }
  }

  function mapFieldErrors(error: ApiError): boolean {
    let mapped = false;
    for (const item of error.errors) {
      const field = item.field.toLowerCase();
      if (field.includes('username') || field.includes('account')) {
        fieldErrors.username = item.message;
        mapped = true;
      } else if (field.includes('password')) {
        fieldErrors.password = item.message;
        mapped = true;
      }
    }
    return mapped;
  }

  async function submit(els: FocusTargets = {}): Promise<void> {
    if (submitting.value) {
      return;
    }
    errorText.value = '';
    if (!validate()) {
      focusFirstInvalid(els);
      return;
    }
    submitting.value = true;
    try {
      await authStore.login(username.value.trim(), password.value);
      if (remember.value) {
        localStorage.setItem(REMEMBER_KEY, username.value.trim());
      } else {
        localStorage.removeItem(REMEMBER_KEY);
      }
      const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/dashboard';
      await router.replace(redirect);
    } catch (error) {
      if (error instanceof ApiError) {
        const mapped = mapFieldErrors(error);
        if (!mapped) {
          errorText.value = error.message || '登录失败，请稍后重试';
        }
        if (mapped && error.errors.length === 1) {
          els.password?.focus();
        }
      } else {
        errorText.value = '登录失败，请稍后重试';
      }
    } finally {
      submitting.value = false;
    }
  }

  return {
    username,
    password,
    submitting,
    errorText,
    fieldErrors,
    remember,
    submit,
  };
}
