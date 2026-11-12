export interface ApiEnvelope<T> {
  code: number;
  message: string;
  data: T;
  traceId?: string | null;
}

export interface PageData<T> {
  page: number;
  pageSize: number;
  list: T[];
  total: number;
}

export interface FieldError {
  field: string;
  code: string;
  message: string;
}

export interface LoginResult {
  userId: number;
  username: string;
  nickname: string;
  accessExpiresIn: number;
  refreshExpiresIn: number;
  /** true = 必须先修改密码（管理员重置 / 首登 / 口令过期），除改密与登出外的接口都会被 403+20302 拦下 */
  mustChangePassword: boolean;
  /** true = 口令已超过有效期 */
  passwordExpired: boolean;
}

export type MaskState = 'MASKED' | 'HIDDEN';

/** 字段级脱敏状态：只登记被掩码/隐藏的字段，明文不出现。 */
export type MaskStates = Partial<Record<string, MaskState>>;

export interface ProfileInfo {
  userId: number;
  username: string;
  nickname: string;
  email: string | null;
  phone: string | null;
  deptId: number | null;
  deptName: string | null;
  roles: string[];
  permissions: string[];
  maskStates?: MaskStates;
}

export interface MenuRouteMeta {
  title: string;
  icon?: string | null;
  hidden?: boolean;
  permission?: string | null;
}

export interface MenuRoute {
  id: number;
  path: string;
  name: string;
  component: string | null;
  meta: MenuRouteMeta;
  children?: MenuRoute[];
}

export interface UserItem {
  id: number;
  username: string;
  nickname: string;
  deptId: number | null;
  deptName: string | null;
  email: string | null;
  phone: string | null;
  isEnabled: boolean;
  roleIds: number[];
  roleNames: string[];
  createdAt: string;
  updatedAt: string;
  maskStates?: MaskStates;
}

export interface UserOption {
  id: number;
  username: string;
  nickname: string;
  deptId: number | null;
  deptName: string | null;
}

export interface RoleItem {
  id: number;
  name: string;
  code: string;
  sort: number;
  dataScope: DataScope;
  ownFilter: OwnFilter;
  remark: string | null;
  isEnabled: boolean;
  menuIds: number[];
  deptIds: number[] | null;
  userIds: number[] | null;
  createdAt: string;
  updatedAt: string;
}

export type DataScope = 'ALL' | 'DEPT' | 'DEPT_AND_CHILD' | 'SELF' | 'CUSTOM_DEPT' | 'CUSTOM_USER';
export type OwnFilter = 'NONE' | 'CREATOR' | 'OWNER';

export interface MenuItem {
  id: number;
  parentId: number;
  name: string;
  menuType: 'M' | 'C' | 'F';
  path: string | null;
  component: string | null;
  permissionCode: string | null;
  icon: string | null;
  sort: number;
  isVisible: boolean;
  isEnabled: boolean;
  createdAt: string;
  updatedAt: string;
  children?: MenuItem[];
}

export interface DeptItem {
  id: number;
  parentId: number;
  ancestors: string;
  name: string;
  sort: number;
  leader: string | null;
  phone: string | null;
  isEnabled: boolean;
  createdAt: string;
  updatedAt: string;
  children?: DeptItem[];
}

export interface DeptOption {
  id: number;
  name: string;
  parentId: number;
  isEnabled: boolean;
  children?: DeptOption[];
}

export interface PostItem {
  id: number;
  deptId: number;
  deptName: string | null;
  code: string;
  name: string;
  sort: number;
  isEnabled: boolean;
  createdAt: string;
  updatedAt: string;
}

export type DemoTaskStatus = 'TODO' | 'DOING' | 'DONE';

export interface DemoTaskItem {
  id: number;
  title: string;
  status: DemoTaskStatus;
  deptId: number;
  deptName: string | null;
  ownerId: number | null;
  ownerName: string | null;
  createBy: number | null;
  createdAt: string;
  updatedAt: string;
}

/** 导入成功结果：totalRows 为数据行数（不含表头），imported 为实际写入行数。 */
export interface ImportResult {
  totalRows: number;
  imported: number;
}

export type DemoTaskImportResult = ImportResult;

/** 导入校验失败：三个数字各自独立，不得合并（errorRows 按行去重，errorCount 按字段错误计数）。 */
export interface ImportErrorData {
  errors: FieldError[];
  totalRows: number;
  errorRows: number;
  errorCount: number;
  remainingErrors: number;
}

/** 导出行数超过同步快通道上限时的受理结果。 */
export interface ExportAccepted {
  mode: 'ASYNC';
  taskId: number;
  totalRows: number;
}

export type DemoTaskExportAccepted = ExportAccepted;

export type ExportTaskStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED' | 'CANCELED' | 'EXPIRED';

export type DemoTaskExportStatus = ExportTaskStatus;

export interface ExportTaskItem {
  id: number;
  fileName: string;
  totalRows: number;
  progress: number;
  status: ExportTaskStatus;
  cancelRequested: boolean;
  errorMessage: string | null;
  fileSize: number | null;
  createdAt: string;
  expiresAt: string | null;
}

export type DemoTaskExportTaskItem = ExportTaskItem;

export interface DictTypeItem {
  id: number;
  name: string;
  type: string;
  remark: string | null;
  isEnabled: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface DictItemRecord {
  id: number;
  dictTypeId: number;
  label: string;
  value: string;
  sort: number;
  isEnabled: boolean;
  remark: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface DictBundle {
  type: string;
  name: string;
  items: DictItemRecord[];
}

export interface ConfigItem {
  id: number;
  configKey: string;
  configValue: string;
  name: string;
  remark: string | null;
  isSensitive: boolean;
  isEnabled: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface OperLogItem {
  id: number;
  traceId: string | null;
  userId: number | null;
  username: string | null;
  deptId: number | null;
  deptName?: string | null;
  module: string | null;
  action: string | null;
  method: string | null;
  requestUri: string | null;
  requestMethod: string | null;
  ip: string | null;
  params: string | null;
  resultCode: number;
  resultMessage: string | null;
  elapsedMs: number | null;
  createdAt: string;
}

export interface LoginLogItem {
  id: number;
  userId: number | null;
  username: string | null;
  ip: string | null;
  userAgent: string | null;
  isSuccess: boolean;
  message: string | null;
  createdAt: string;
}

export interface FileItem {
  id: number;
  originalName: string;
  contentType: string | null;
  size: number;
  uploaderId: number | null;
  createdAt: string;
  downloadUrl: string;
  previewUrl: string | null;
  instant: boolean | null;
  deletedAt: string | null;
  deletedBy: number | null;
}

export interface PageParams {
  page: number;
  pageSize: number;
  sort?: string;
  order?: 'asc' | 'desc';
}

export type NoticeLevel = 'NORMAL' | 'IMPORTANT' | 'URGENT';
export type NoticeType = 'SYSTEM' | 'BUSINESS' | 'SECURITY' | 'TASK';
export type NoticeScopeType = 'ALL' | 'ROLE' | 'DEPT' | 'USER';
export type NoticeStatus = 'PUBLISHED' | 'REVOKED';

export interface NoticeScope {
  scopeType: NoticeScopeType;
  targetId: number | null;
  targetName?: string | null;
}

export interface NoticeReadStats {
  totalRecipients: number;
  readCount: number;
  unreadCount: number;
}

export interface NoticeItem {
  id: number;
  title: string;
  content: string | null;
  level: NoticeLevel;
  type: NoticeType;
  revocable: boolean;
  revoked: boolean;
  publishedAt: string;
  publisherId: number | null;
  publisherName: string;
  deptId: number | null;
  deptName: string | null;
  revokedAt: string | null;
  revokedBy: number | null;
  revokedByName: string | null;
  scopes: NoticeScope[];
  stats: NoticeReadStats;
  createdAt: string;
}

export interface NoticeReaderItem {
  userId: number;
  username: string;
  nickname: string;
  deptName: string | null;
  read: boolean;
  readAt: string | null;
}

export interface InboxNoticeItem {
  id: number;
  title: string;
  level: NoticeLevel;
  type: NoticeType;
  publishedAt: string;
  publisherName: string;
  read: boolean;
  readAt: string | null;
}

export interface InboxNoticeDetail extends InboxNoticeItem {
  content: string;
}

export type JobStatus = 'ENABLED' | 'DISABLED';
export type JobConcurrencyPolicy = 'DISALLOW' | 'ALLOW';
export type JobNotifyPolicy = 'NEVER' | 'ON_FAILURE' | 'ON_CONSECUTIVE';
export type JobRunResult = 'SUCCESS' | 'FAIL' | 'TIMEOUT' | 'SKIPPED';
export type JobTriggerType = 'AUTO' | 'MANUAL';

export interface JobItem {
  id: number;
  name: string;
  groupName: string;
  jobKey: string;
  cronExpression: string;
  description: string | null;
  status: JobStatus;
  concurrencyPolicy: JobConcurrencyPolicy;
  timeoutSeconds: number;
  retryTimes: number;
  retryIntervalSeconds: number;
  notifyPolicy: JobNotifyPolicy;
  notifyThreshold: number;
  notifyTargets: string | null;
  lastRunAt: string | null;
  lastRunResult: JobRunResult | null;
  nextRunAt: string | null;
  running: boolean;
  registered: boolean;
  createdBy: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface JobKeyOption {
  key: string;
  description: string;
}

export interface JobRunResultItem {
  logId: number;
  result: JobRunResult;
  elapsedMs: number;
  attemptCount: number;
  affectedRows: number | null;
  errorSummary: string | null;
  startedAt: string;
  finishedAt: string;
}

export interface JobLogItem {
  id: number;
  jobId: number;
  jobName: string;
  jobKey: string;
  triggerType: JobTriggerType;
  startedAt: string;
  finishedAt: string;
  elapsedMs: number;
  result: JobRunResult;
  attemptCount: number;
  errorSummary: string | null;
  affectedRows: number | null;
}

export interface JobLogDetail extends JobLogItem {
  errorStack: string | null;
}

export type SessionDeviceType = 'PC' | 'MOBILE' | 'TABLET' | 'UNKNOWN';
export type SessionInvalidateReason =
  | 'LOGOUT'
  | 'KICKED_BY_ADMIN'
  | 'PASSWORD_CHANGED'
  | 'ACCOUNT_DISABLED'
  | 'USER_DELETED';

export interface SessionItem {
  id: number;
  userId: number;
  username: string;
  nickname: string | null;
  ip: string | null;
  userAgent: string | null;
  deviceType: SessionDeviceType;
  browser: string | null;
  os: string | null;
  loginAt: string;
  lastActiveAt: string;
  invalidatedAt: string | null;
  invalidateReason: SessionInvalidateReason | null;
  online: boolean;
  current: boolean | null;
}

/** 缓存命名空间的 key 数量（概览用） */
export interface CacheNamespaceCount {
  code: string;
  name: string;
  prefix: string;
  keyCount: number;
}

export interface CacheOverview {
  connected: boolean;
  cacheType: string;
  usedMemoryBytes: number;
  usedMemoryHuman: string | null;
  maxMemoryBytes: number;
  maxMemoryHuman: string | null;
  maxMemoryPolicy: string | null;
  totalKeys: number;
  unregisteredKeys: number;
  namespaces: CacheNamespaceCount[];
}

export type CacheNamespaceMode = 'EXACT' | 'PREFIX';

export interface CacheNamespace {
  code: string;
  name: string;
  prefix: string;
  mode: CacheNamespaceMode;
  purpose: string;
  clearEffect: string;
  sensitive: boolean;
  clearable: boolean;
  keyCount: number;
}

export interface CacheKeyItem {
  name: string;
  /** -1 永久；-2 已不存在 */
  ttlSeconds: number;
  type: string;
}

export interface CacheKeyPageData {
  list: CacheKeyItem[];
  total: number;
  page: number;
  pageSize: number;
  truncated: boolean;
  namespace: CacheNamespace;
}

export interface CacheAffect {
  deleted: number;
}

export interface CacheCleanLog {
  id: number;
  action: string;
  namespaceCode: string | null;
  namespacePrefix: string | null;
  keyName: string | null;
  deletedCount: number;
  username: string | null;
  ip: string | null;
  createdAt: string;
}


