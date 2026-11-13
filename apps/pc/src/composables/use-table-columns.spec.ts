import { beforeEach, describe, expect, it, vi } from 'vitest';
import { SORT_WHITELIST } from '@/constants/sort-whitelist';
import { useTableColumns, type ColumnDefinition } from './use-table-columns';

/**
 * 列模型：显隐、持久化、排序翻译、导出字段。
 *
 * 重点覆盖三类「静默失败」：
 * 1. sortKey 写错 → 后端白名单回落，点表头没反应；
 * 2. 导出字段把「不可导出列」（操作列）算进去 → 导出多一列空数据；
 * 3. 列定义变更后旧偏好残留 → 出现隐藏了不存在的列、或该显示的被隐藏。
 */

const COLUMNS: ColumnDefinition[] = [
  { key: 'nickname', label: '姓名', sortKey: 'nickname' },
  { key: 'username', label: '账号', sortKey: 'username' },
  { key: 'deptName', label: '部门' },
  { key: 'createdAt', label: '创建时间', sortKey: 'createdAt', defaultVisible: false },
  { key: 'ops', label: '操作', alwaysVisible: true, exportable: false },
];

function setup(onSort = vi.fn(), columns: ColumnDefinition[] = COLUMNS, storageKey = 'test-list') {
  const table = useTableColumns({ storageKey, columns, onSort });
  return { table, onSort };
}

describe('useTableColumns（列模型）', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('默认隐藏 defaultVisible=false 的列，常显列不可隐藏', () => {
    const { table } = setup();
    expect(table.isVisible('nickname')).toBe(true);
    expect(table.isVisible('createdAt')).toBe(false);
    expect(table.isVisible('ops')).toBe(true);
    expect(table.hiddenCount.value).toBe(1);
  });

  it('勾选切换控制显隐并写入 localStorage', async () => {
    const { table } = setup();
    table.toggleColumn('createdAt');
    expect(table.isVisible('createdAt')).toBe(true);
    await Promise.resolve();
    expect(localStorage.getItem('list-columns:test-list')).toBe(JSON.stringify([]));

    table.toggleColumn('ops');
    // 常显列点了也不生效
    expect(table.isVisible('ops')).toBe(true);
  });

  it('全选 / 反选 / 恢复默认', () => {
    const { table } = setup();
    table.showAllColumns();
    expect(table.hiddenCount.value).toBe(0);

    table.invertColumns();
    expect(table.isVisible('nickname')).toBe(false);
    expect(table.isVisible('ops')).toBe(true);

    table.resetColumns();
    expect(table.isVisible('createdAt')).toBe(false);
    expect(table.isVisible('nickname')).toBe(true);
  });

  it('导出字段只含「可见且可导出」的列，顺序沿用列定义', () => {
    const { table } = setup();
    expect(table.exportFields.value).toEqual(['nickname', 'username', 'deptName']);
    table.toggleColumn('createdAt');
    expect(table.exportFields.value).toEqual(['nickname', 'username', 'deptName', 'createdAt']);
  });

  it('排序翻译：prop → 后端白名单字段名，取消排序传 undefined', () => {
    const { table, onSort } = setup();
    table.onSortChange({ prop: 'nickname', order: 'ascending' });
    expect(onSort).toHaveBeenLastCalledWith('nickname', 'asc');

    table.onSortChange({ prop: 'createdAt', order: 'descending' });
    expect(onSort).toHaveBeenLastCalledWith('createdAt', 'desc');

    table.onSortChange({ prop: 'nickname', order: null });
    expect(onSort).toHaveBeenLastCalledWith(undefined, undefined);
  });

  it('不可排序列返回 sortable=false，不会给后端发无意义的 sort', () => {
    const { table } = setup();
    expect(table.sortableOf('nickname')).toBe('custom');
    expect(table.sortableOf('deptName')).toBe(false);
  });

  it('无权限的列既不渲染也不参与导出', () => {
    const table = useTableColumns({
      storageKey: 'perm-list',
      columns: [...COLUMNS, { key: 'phone', label: '手机号', permission: 'system:user:phone' }],
      hasPermission: (code) => code !== 'system:user:phone',
    });
    expect(table.isVisible('phone')).toBe(false);
    expect(table.exportFields.value).not.toContain('phone');
  });

  it('列定义变更后旧偏好自动失效（不隐藏不存在的列、不误删新列）', () => {
    localStorage.setItem('list-columns:test-list', JSON.stringify(['ghost', 'username']));
    const { table } = setup();
    expect(table.isVisible('username')).toBe(false);
    // ghost 不在列定义里 → 被过滤掉，只剩 username 一个隐藏项
    expect(table.hiddenCount.value).toBe(1);
    // 用户已有显式偏好时，defaultVisible=false 不再生效（尊重用户选择）
    expect(table.isVisible('createdAt')).toBe(true);
  });

  it('localStorage 脏数据不抛错，按默认处理', () => {
    localStorage.setItem('list-columns:test-list', 'not-json');
    const { table } = setup();
    expect(table.isVisible('nickname')).toBe(true);
  });

  it('页面用到的每个 sortKey 都必须出现在后端白名单镜像里', () => {
    // 断言 #101：白名单不在前端，写错只会「点了没反应」，用这条测试顶上
    const usedSortKeys = COLUMNS.filter((column) => column.sortKey).map((column) => column.sortKey as string);
    for (const key of usedSortKeys) {
      expect(SORT_WHITELIST.user).toContain(key);
    }
  });
  it('exportKey：展示字段与后端导出字段不一致时按 exportKey 输出，并去重', () => {
    const table = useTableColumns({
      storageKey: 'export-key-list',
      columns: [
        { key: 'title', label: '标题' },
        // 页面展示 deptName，后端导出 schema 的列 key 是 deptId
        { key: 'deptName', label: '部门', exportKey: 'deptId' },
        { key: 'deptNameCopy', label: '部门（重复展示）', exportKey: 'deptId' },
        { key: 'ops', label: '操作', alwaysVisible: true, exportable: false },
      ],
    });
    // 去重：deptId 只出现一次
    expect(table.exportFields.value).toEqual(['title', 'deptId']);
  });
});