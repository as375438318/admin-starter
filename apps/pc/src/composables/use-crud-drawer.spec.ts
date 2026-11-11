import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '@/api/http';

const { scrollSpy } = vi.hoisted(() => ({ scrollSpy: vi.fn(async () => undefined) }));
vi.mock('@/utils/form', () => ({ scrollToFirstError: scrollSpy }));
vi.mock('element-plus', () => ({ ElMessage: { error: vi.fn() } }));

import { useCrudDrawer } from './use-crud-drawer';

interface DemoForm {
  username: string;
  nickname: string;
}

const emptyForm = (): DemoForm => ({ username: '', nickname: '' });

/** 只模拟 useCrudDrawer 真正用到的 FormInstance 形状 */
function fakeFormRef(valid: boolean | 'reject') {
  return {
    validate: () =>
      valid === 'reject' ? Promise.reject(new Error('invalid')) : Promise.resolve(valid),
    $el: document.createElement('div'),
  };
}

describe('useCrudDrawer（新增/编辑抽屉）', () => {
  beforeEach(() => {
    scrollSpy.mockClear();
  });

  it('openCreate 重置表单、清空编辑态并触发 onOpen', () => {
    const onOpen = vi.fn();
    const drawer = useCrudDrawer<DemoForm>({ emptyForm, onOpen });
    drawer.form.username = '残留值';

    drawer.openCreate();

    expect(drawer.visible.value).toBe(true);
    expect(drawer.isEdit.value).toBe(false);
    expect(drawer.editingId.value).toBeNull();
    expect(drawer.form.username).toBe('');
    expect(onOpen).toHaveBeenCalledTimes(1);
  });

  it('openEdit 在空白表单上叠加传入值，并记录 id', () => {
    const drawer = useCrudDrawer<DemoForm>({ emptyForm });
    drawer.form.username = '旧值';

    drawer.openEdit(9, { username: 'admin' });

    expect(drawer.isEdit.value).toBe(true);
    expect(drawer.editingId.value).toBe(9);
    expect(drawer.form.username).toBe('admin');
    // 未传入的字段回到空白基准，而不是保留上一次的值
    expect(drawer.form.nickname).toBe('');
  });

  it('close 只关闭抽屉，不清表单（避免关灯动画期间闪空）', () => {
    const drawer = useCrudDrawer<DemoForm>({ emptyForm });
    drawer.openEdit(1, { username: 'admin' });

    drawer.close();

    expect(drawer.visible.value).toBe(false);
    expect(drawer.form.username).toBe('admin');
  });

  it('校验不通过时不调用 handler，返回 false 并滚动到首个错误', async () => {
    const drawer = useCrudDrawer<DemoForm>({ emptyForm });
    drawer.formRef.value = fakeFormRef(false) as never;
    const handler = vi.fn();

    await expect(drawer.submit(handler)).resolves.toBe(false);

    expect(handler).not.toHaveBeenCalled();
    expect(scrollSpy).toHaveBeenCalledTimes(1);
  });

  it('validate 抛错（如未渲染表单）按校验失败处理，不冒泡异常', async () => {
    const drawer = useCrudDrawer<DemoForm>({ emptyForm });
    drawer.formRef.value = fakeFormRef('reject') as never;
    const handler = vi.fn();

    await expect(drawer.submit(handler)).resolves.toBe(false);
    expect(handler).not.toHaveBeenCalled();
  });

  it('提交成功：把编辑态与表单传给 handler，关闭抽屉并返回 true', async () => {
    const drawer = useCrudDrawer<DemoForm>({ emptyForm });
    drawer.formRef.value = fakeFormRef(true) as never;
    drawer.openEdit(3, { username: 'admin' });
    const handler = vi.fn(async () => undefined);

    await expect(drawer.submit(handler)).resolves.toBe(true);

    expect(handler).toHaveBeenCalledWith({ isEdit: true, editingId: 3, form: drawer.form });
    expect(drawer.visible.value).toBe(false);
    expect(drawer.submitting.value).toBe(false);
  });

  it('提交期间 submitting 为 true，结束后复位', async () => {
    const drawer = useCrudDrawer<DemoForm>({ emptyForm });
    drawer.formRef.value = fakeFormRef(true) as never;
    let during = false;
    const handler = async () => {
      during = drawer.submitting.value;
    };

    await drawer.submit(handler);

    expect(during).toBe(true);
    expect(drawer.submitting.value).toBe(false);
  });

  it('422 失败：回填 fieldErrors、保持抽屉打开、返回 false', async () => {
    const drawer = useCrudDrawer<DemoForm>({ emptyForm });
    drawer.formRef.value = fakeFormRef(true) as never;
    drawer.openCreate();
    const handler = vi.fn(async () => {
      throw new ApiError(30201, '参数校验失败', 422, null, [
        { field: 'username', code: 'REQUIRED', message: '账号不能为空' },
      ]);
    });

    await expect(drawer.submit(handler)).resolves.toBe(false);

    expect(drawer.visible.value).toBe(true);
    expect(drawer.fieldError('username')).toBe('账号不能为空');
    expect(drawer.submitting.value).toBe(false);
    expect(scrollSpy).toHaveBeenCalledTimes(1);
  });

  it('再次打开抽屉会清掉上一次的字段错误', async () => {
    const drawer = useCrudDrawer<DemoForm>({ emptyForm });
    drawer.formRef.value = fakeFormRef(true) as never;
    await drawer.submit(async () => {
      throw new ApiError(30201, '参数校验失败', 422, null, [
        { field: 'username', code: 'REQUIRED', message: '账号不能为空' },
      ]);
    });
    expect(drawer.fieldError('username')).not.toBe('');

    drawer.openCreate();

    expect(drawer.fieldError('username')).toBe('');
  });
});
