import { computed, ref, watch } from 'vue';

/**
 * 列表列模型与显示控制（设计见 `docs/design/list-sort-and-export-fields.md`）。
 *
 * 一份列定义同时驱动四件事：表格渲染、列显隐（勾选）、点表头排序、导出字段。
 * 页面仍手写 `<el-table-column>`（保留各自的自定义单元格模板），但列元信息只在这里声明一次。
 *
 * 三条硬约定（改之前先看断言 #101/#102）：
 * 1. `el-table-column` 的 `prop` 必须等于列的 `key`——排序事件靠 `prop` 反查列定义。
 * 2. `sortKey` 必须是**后端 SORTS 白名单里的字段名**（camelCase，如 `createdAt`）；不写则不可排序。
 *    白名单不在前端，写错不会报错、只会静默按默认排序——因此集中在 `sort-whitelist.ts` 维护。
 * 3. `exportFields` 只包含「可见且可导出」的列，且**只能减少后端导出的列**，不能绕过字段级脱敏。
 */

export type SortOrder = 'asc' | 'desc' | undefined;

/** el-table 的 sort-change 回调载荷（Element Plus 类型见 TableColumnCtx） */
export interface TableSortChangeEvent {
  prop: string;
  order: 'ascending' | 'descending' | null;
}

export interface ColumnDefinition {
  /** 列标识；同时是导出字段名（必须与后端 `ExcelField.key` 对齐） */
  key: string;
  /** 表头文案 */
  label: string;
  /** 后端排序白名单字段名；缺省表示该列不可排序 */
  sortKey?: string;
  /** 初始是否显示，缺省 true */
  defaultVisible?: boolean;
  /** 是否常显不可取消勾选（主键列、操作列） */
  alwaysVisible?: boolean;
  /** 是否参与导出勾选，缺省 true（操作列必须 false） */
  exportable?: boolean;
  /** 该列需要的权限码；无权限则不渲染也不参与导出 */
  permission?: string;
}

export interface UseTableColumnsOptions {
  /** localStorage 键后缀，建议用页面路由语义，如 `system-user`；同一 key 共享列偏好 */
  storageKey: string;
  columns: ColumnDefinition[];
  /** 权限判定，页面传入 `auth.hasPermission`；缺省视为全部有权限 */
  hasPermission?: (code: string) => boolean;
  /** 排序变化回调，页面接 `usePagedList().onSortChange` */
  onSort?: (sort: string | undefined, order: SortOrder) => void;
}

const STORAGE_PREFIX = 'list-columns:';

export function useTableColumns(options: UseTableColumnsOptions) {
  const hasPermission = options.hasPermission ?? (() => true);

  const defaultHidden = options.columns
    .filter((column) => column.defaultVisible === false && column.alwaysVisible !== true)
    .map((column) => column.key);

  const hidden = ref<string[]>(loadHidden(options.storageKey, options.columns, defaultHidden));

  watch(hidden, (value) => saveHidden(options.storageKey, value), { deep: true });

  /** 权限过滤后的列（无权限的列对外不存在） */
  const permittedColumns = computed(() =>
    options.columns.filter((column) => !column.permission || hasPermission(column.permission)),
  );

  const visibleColumns = computed(() =>
    permittedColumns.value.filter((column) => column.alwaysVisible === true || !hidden.value.includes(column.key)),
  );

  const hiddenCount = computed(() => permittedColumns.value.length - visibleColumns.value.length);

  /** 导出字段：可见 + 可导出；顺序沿用列定义顺序（与后端 schema 顺序一致，避免不同人导出结构不同） */
  const exportFields = computed(() =>
    visibleColumns.value
      .filter((column) => column.exportable !== false)
      .map((column) => column.key),
  );

  function isVisible(key: string): boolean {
    const column = permittedColumns.value.find((item) => item.key === key);
    if (!column) {
      return false;
    }
    return column.alwaysVisible === true || !hidden.value.includes(key);
  }

  /** 传给 `el-table-column` 的 `sortable`：可排序用 'custom'（服务端排序），否则 false */
  function sortableOf(key: string): 'custom' | false {
    const column = permittedColumns.value.find((item) => item.key === key);
    return column?.sortKey ? 'custom' : false;
  }

  function toggleColumn(key: string): void {
    const column = options.columns.find((item) => item.key === key);
    if (!column || column.alwaysVisible === true) {
      return;
    }
    hidden.value = hidden.value.includes(key)
      ? hidden.value.filter((item) => item !== key)
      : [...hidden.value, key];
  }

  function showAllColumns(): void {
    hidden.value = [];
  }

  function invertColumns(): void {
    hidden.value = permittedColumns.value
      .filter((column) => column.alwaysVisible !== true && !hidden.value.includes(column.key))
      .map((column) => column.key);
  }

  /** 恢复默认：把 defaultVisible === false 的列重新隐藏 */
  function resetColumns(): void {
    hidden.value = [...defaultHidden];
  }

  /**
   * el-table 的 `@sort-change`。
   * 三态由 el-table 自带的 sort-orders（升→降→取消）负责，这里只做「prop → 白名单字段名」的翻译。
   */
  function onSortChange(event: TableSortChangeEvent): void {
    if (!event.order) {
      // 取消排序：回到后端默认排序
      options.onSort?.(undefined, undefined);
      return;
    }
    const column = options.columns.find((item) => item.key === event.prop);
    if (!column?.sortKey) {
      // 理论上不会发生（不可排序列不带 sortable），兜底按取消处理
      options.onSort?.(undefined, undefined);
      return;
    }
    options.onSort?.(column.sortKey, event.order === 'ascending' ? 'asc' : 'desc');
  }

  return {
    visibleColumns,
    hiddenCount,
    exportFields,
    isVisible,
    sortableOf,
    onSortChange,
    toggleColumn,
    showAllColumns,
    invertColumns,
    resetColumns,
  };
}

function loadHidden(storageKey: string, columns: ColumnDefinition[], defaultHidden: string[]): string[] {
  try {
    const raw = localStorage.getItem(STORAGE_PREFIX + storageKey);
    if (!raw) {
      return [...defaultHidden];
    }
    const parsed: unknown = JSON.parse(raw);
    if (!Array.isArray(parsed)) {
      return [...defaultHidden];
    }
    // 只保留当前仍存在的列 key：列定义改动后旧偏好自动失效，不会出现"隐藏了一个不存在的列"
    const known = new Set(columns.map((column) => column.key));
    return parsed.filter((key): key is string => typeof key === 'string' && known.has(key));
  } catch {
    // 隐私模式 / 存储被禁用 / 脏数据：按默认处理，不影响列表可用
    return [...defaultHidden];
  }
}

function saveHidden(storageKey: string, hidden: string[]): void {
  try {
    localStorage.setItem(STORAGE_PREFIX + storageKey, JSON.stringify(hidden));
  } catch {
    // 存储不可用时不报错：列偏好只是体验增强，不能因此让列表挂掉
  }
}
