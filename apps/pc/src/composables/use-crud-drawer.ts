import { reactive, ref, type Ref } from 'vue';
import type { FormInstance } from 'element-plus';
import { scrollToFirstError } from '@/utils/form';
import { useFieldErrors } from './use-field-errors';

export interface SubmitContext<F extends object> {
  isEdit: boolean;
  editingId: number | null;
  form: F;
}

export interface UseCrudDrawerOptions<F extends object> {
  /** 新增时的空白表单，同时作为编辑前重置基准 */
  emptyForm: () => F;
  /** 抽屉打开后的额外动作，例如拉取下拉选项 */
  onOpen?: () => void;
}

export interface UseCrudDrawerResult<F extends object> {
  visible: Ref<boolean>;
  isEdit: Ref<boolean>;
  editingId: Ref<number | null>;
  submitting: Ref<boolean>;
  formRef: Ref<FormInstance | undefined>;
  form: F;
  fieldErrors: Ref<Record<string, string>>;
  fieldError: (field: string) => string;
  openCreate: () => void;
  openEdit: (id: number, values: Partial<F>) => void;
  close: () => void;
  /** 校验 + 提交中状态 + 422 回填；返回是否成功，调用方据此决定是否刷新列表 */
  submit: (handler: (ctx: SubmitContext<F>) => Promise<void>) => Promise<boolean>;
}

/**
 * 新增/编辑抽屉的共享状态与提交流程。
 * 各页面的差异（payload 组装、接口调用、成功文案）留在页面 handler 里。
 */
export function useCrudDrawer<F extends object>(options: UseCrudDrawerOptions<F>): UseCrudDrawerResult<F> {
  const visible = ref(false);
  const isEdit = ref(false);
  const editingId = ref<number | null>(null);
  const submitting = ref(false);
  const formRef = ref<FormInstance>();
  const form = reactive(options.emptyForm()) as unknown as F;
  const { fieldErrors, fieldError, clearErrors, captureError } = useFieldErrors();

  function openCreate(): void {
    isEdit.value = false;
    editingId.value = null;
    Object.assign(form, options.emptyForm());
    clearErrors();
    visible.value = true;
    options.onOpen?.();
  }

  function openEdit(id: number, values: Partial<F>): void {
    isEdit.value = true;
    editingId.value = id;
    Object.assign(form, options.emptyForm(), values);
    clearErrors();
    visible.value = true;
    options.onOpen?.();
  }

  function close(): void {
    visible.value = false;
  }

  async function submit(handler: (ctx: SubmitContext<F>) => Promise<void>): Promise<boolean> {
    const valid = await formRef.value?.validate().catch(() => false);
    if (!valid) {
      await scrollToFirstError(formRef.value);
      return false;
    }
    submitting.value = true;
    clearErrors();
    try {
      await handler({ isEdit: isEdit.value, editingId: editingId.value, form });
      visible.value = false;
      return true;
    } catch (error) {
      captureError(error);
      await scrollToFirstError(formRef.value);
      return false;
    } finally {
      submitting.value = false;
    }
  }

  return {
    visible,
    isEdit,
    editingId,
    submitting,
    formRef,
    form,
    fieldErrors,
    fieldError,
    openCreate,
    openEdit,
    close,
    submit,
  };
}
