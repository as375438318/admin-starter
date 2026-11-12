/**
 * 后端列表排序白名单的前端镜像（唯一定义处：各 `*RepositoryImpl` 里的 `SORTS`）。
 *
 * 为什么要镜像一份：后端对白名单外的 `sort` **静默回落**到默认列（不报错），
 * 所以前端把字段名写错时不会失败、只会「点了表头没反应」——这类问题靠肉眼极难发现。
 * 这里集中登记，配合 `use-table-columns.spec.ts` 的断言：页面里用到的每个 `sortKey`
 * 必须出现在本文件里。改后端 `SORTS` 时**必须同步本文件**（见断言 #101）。
 */
export const SORT_WHITELIST = {
  /** UserRepositoryImpl：createdAt / username / nickname / id */
  user: ['createdAt', 'username', 'nickname', 'id'],
  /** RoleRepositoryImpl：createdAt / name / code / sort / id */
  role: ['createdAt', 'name', 'code', 'sort', 'id'],
  /** PostRepositoryImpl：createdAt / name / code / sort / id */
  post: ['createdAt', 'name', 'code', 'sort', 'id'],
  /** DictRepositoryImpl（字典类型）：createdAt / name / type / id */
  dict: ['createdAt', 'name', 'type', 'id'],
  /** ConfigRepositoryImpl：createdAt / name / configKey / id */
  config: ['createdAt', 'name', 'configKey', 'id'],
  /** JobRepositoryImpl：createdAt / name / nextRunAt / id */
  job: ['createdAt', 'name', 'nextRunAt', 'id'],
  /** NoticeRepositoryImpl：createdAt / title / id */
  notice: ['createdAt', 'title', 'id'],
  /** OperLogRepositoryImpl：createdAt / elapsedMs / id */
  operLog: ['createdAt', 'elapsedMs', 'id'],
  /** DemoTaskRepositoryImpl：createdAt / title / status / id */
  demoTask: ['createdAt', 'title', 'status', 'id'],
} as const;

export type SortScope = keyof typeof SORT_WHITELIST;
