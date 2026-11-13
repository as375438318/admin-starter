#!/usr/bin/env node
/**
 * 权限矩阵 → 端到端回归用例的生成器（P1-2，`CR-20261114-045`）。
 *
 * 做两件事：
 * 1. 扫后端 Controller，抽出「权限码 → 代表性接口」映射（`@PreAuthorize` + `@RequestMapping`/`@*Mapping`）；
 * 2. 解析 `docs/design/permission-matrix.md`，把「权限码 × 角色」的允许/拒绝期望一并写进
 *    `server/web/src/test/resources/permission-matrix.json`，供 `PermissionMatrixTest` 数据驱动执行。
 *
 * 为什么用生成而不是手写映射：映射写错了会变成"假绿"（例如接口路径错 → 全部 404 → 未命中 403 断言）。
 * 从代码抽取能保证"权限码 ↔ 接口"始终与实现同源；矩阵里出现新权限码但没接口（或反之）时**直接报错**。
 *
 * 用法：node tools/permission-matrix/cli.mjs [--check]
 *   --check：只校验不落盘，发现差异时以非零码退出（CI 用）
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const CONTROLLER_DIRS = ['server/web/src/main/java', 'server/system/src/main/java'];
const MATRIX = 'docs/design/permission-matrix.md';
const OUTPUT = 'server/web/src/test/resources/permission-matrix.json';
const checkOnly = process.argv.includes('--check');

/** 路径占位 id 用这个哨兵值：保证"允许侧"的写操作只撞到"目标不存在"，不破坏种子数据 */
const NON_EXISTENT_ID = '999999';

/** read：GET，可双向严格断言；mutate-missing-target：带 id 的写，允许侧只撞"目标不存在"；create：POST 新建 */
function classify(method, pathWithPlaceholder) {
  if (method === 'GET') {
    return 'read';
  }
  if (pathWithPlaceholder.includes('{')) {
    return 'mutate-missing-target';
  }
  return 'create';
}

// ---------------------------------------------------------------- 1. 扫 Controller
const controllers = [];
for (const dir of CONTROLLER_DIRS) {
  walk(path.join(ROOT, dir), controllers);
}

function walk(dir, acc) {
  if (!fs.existsSync(dir)) {
    return;
  }
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      walk(full, acc);
    } else if (entry.name.endsWith('Controller.java')) {
      acc.push(full);
    }
  }
}

const MAPPING_ANNOTATIONS = {
  GetMapping: 'GET',
  PostMapping: 'POST',
  PutMapping: 'PUT',
  DeleteMapping: 'DELETE',
  PatchMapping: 'PATCH',
};

/** code → [{method, path}]（同一权限码可能有多个接口，取第一个作代表） */
const endpoints = new Map();
const unknownPatterns = [];

for (const file of controllers) {
  const source = fs.readFileSync(file, 'utf8');
  const classBase = firstMatch(source, /@RequestMapping\(\s*"([^"]*)"\s*\)/) ?? '';
  const lines = source.split(/\r?\n/);

  for (let i = 0; i < lines.length; i += 1) {
    const preAuthorize = /@PreAuthorize\("([^"]+)"\)/.exec(lines[i]);
    if (!preAuthorize) {
      continue;
    }
    const expression = preAuthorize[1];
    const codes = [...expression.matchAll(/'([a-zA-Z0-9:_-]+)'/g)].map((m) => m[1]);
    if (!codes.length) {
      continue;
    }
    // 方法映射注解通常在 @PreAuthorize 上方或下方一两行内
    const method = findMethodMapping(lines, i);
    if (!method) {
      unknownPatterns.push(`${path.basename(file)}:${i + 1} 找不到 HTTP 方法映射`);
      continue;
    }
    const fullPath = joinPath(classBase, method.path);
    const exact = codes.length === 1 && expression.includes('hasAuthority(');
    for (const code of codes) {
      if (!endpoints.has(code)) {
        endpoints.set(code, []);
      }
      endpoints.get(code).push({ method: method.verb, path: fullPath, exact });
    }
  }
}

function firstMatch(text, regex) {
  const matched = regex.exec(text);
  return matched ? matched[1] : null;
}

function findMethodMapping(lines, from) {
  const look = (index) => {
    if (index < 0 || index >= lines.length) {
      return null;
    }
    const line = lines[index];
    const annotation = Object.keys(MAPPING_ANNOTATIONS).find((name) => line.includes(`@${name}`));
    if (!annotation) {
      return null;
    }
    const withValue = new RegExp(`@${annotation}\\(\\s*"([^"]*)"\\s*\\)`).exec(line);
    return { verb: MAPPING_ANNOTATIONS[annotation], path: withValue ? withValue[1] : '' };
  };
  // 先往上找（注解通常写在 @PreAuthorize 之前），再往下兜底
  for (let k = from; k >= Math.max(0, from - 6); k -= 1) {
    const hit = look(k);
    if (hit) {
      return hit;
    }
  }
  for (let k = from + 1; k <= Math.min(lines.length - 1, from + 6); k += 1) {
    const hit = look(k);
    if (hit) {
      return hit;
    }
  }
  return null;
}

function joinPath(base, sub) {
  const head = (base ?? '').replace(/\/$/, '');
  const tail = sub === '' ? '' : sub.startsWith('/') ? sub : `/${sub}`;
  return `${head}${tail}`;
}

// ---------------------------------------------------------------- 2. 解析权限矩阵
const matrixSource = fs.readFileSync(path.join(ROOT, MATRIX), 'utf8');
const headerLine = matrixSource.split(/\r?\n/).find((line) => line.startsWith('| 权限码 |'));
if (!headerLine) {
  fail('权限矩阵表头未找到（应以 "| 权限码 |" 开头）');
}
const roles = headerLine
  .split('|')
  .slice(4)
  .map((cell) => cell.trim())
  .filter(Boolean)
  .map((cell) => cell.replace(/（\d+）$/, '').trim());

const rows = [];
for (const line of matrixSource.split(/\r?\n/)) {
  if (!line.startsWith('| `')) {
    continue;
  }
  const cells = line.split('|').slice(1, -1).map((cell) => cell.trim());
  const code = cells[0].replace(/`/g, '');
  // 矩阵文档里还有别的表（配置项、数据表清单），只认 "a:b(:c)" 形式的权限码，避免把 name / sys_oper_log 当权限码
  if (!/^[a-z][a-z0-9]*(:[a-z0-9_*-]+){1,3}$/.test(code)) {
    continue;
  }
  const allowed = {};
  roles.forEach((role, index) => {
    // ✓ 记为允许；空、✗、— 都记为拒绝
    allowed[role] = (cells[3 + index] ?? '').includes('✓');
  });
  rows.push({ code, module: cells[1], type: cells[2], allowed });
}

// ---------------------------------------------------------------- 3. 对账
const warnings = [];
const cases = [];
/** 字段级脱敏权限：不在 @PreAuthorize 上，由 MaskResolverTest / MaskedValueGuardTest 覆盖 */
const FIELD_LEVEL_PATTERN = /^system:user:(phone|email):(view|mask)$/;
const skipped = [];

for (const row of rows) {
  const hits = endpoints.get(row.code);
  if (!hits || hits.length === 0) {
    if (FIELD_LEVEL_PATTERN.test(row.code)) {
      skipped.push({ code: row.code, reason: '字段级脱敏权限，由 MaskResolverTest / MaskedValueGuardTest 覆盖' });
      continue;
    }
    warnings.push(`矩阵里有权限码但代码里找不到接口：${row.code}`);
    continue;
  }
  // 代表接口：先精确匹配（hasAuthority）再看任一匹配（hasAnyAuthority），同类里 GET 优先
  const exactHits = hits.filter((hit) => hit.exact);
  const representative =
    exactHits.find((hit) => hit.method === 'GET')
    ?? hits.find((hit) => hit.method === 'GET')
    ?? exactHits[0]
    ?? hits[0];
  const kind = classify(representative.method, representative.path);
  cases.push({
    kind,
    code: row.code,
    module: row.module,
    type: row.type,
    method: representative.method,
    path: representative.path.replace(/\{[^}]+\}/g, NON_EXISTENT_ID),
    exact: Boolean(representative.exact),
    allowed: row.allowed,
  });
}
for (const code of endpoints.keys()) {
  if (!rows.some((row) => row.code === code)) {
    warnings.push(`代码里有权限码但矩阵里没有：${code}`);
  }
}
for (const entry of unknownPatterns) {
  warnings.push(`无法解析的 controller 写法：${entry}`);
}

const payload = {
  generatedBy: 'tools/permission-matrix/cli.mjs',
  note: '自动生成，禁止手改。矩阵或 @PreAuthorize 变更后重新生成；CI 用 --check 校验一致性。',
  roles,
  platformRoles: { SUPER_ADMIN: 'admin', DEPT_MANAGER: 'deptmgr', EMPLOYEE: 'dev1', CROSS_AUDITOR: 'auditor', CUSTOM_VIEWER: 'viewer', DEMO_DEPT: 'demo_dept', DEMO_OWNER: 'demo_owner' },
  cases,
  skipped,
  warnings,
};

const serialized = `${JSON.stringify(payload, null, 2)}\n`;
if (checkOnly) {
  const current = fs.existsSync(path.join(ROOT, OUTPUT)) ? fs.readFileSync(path.join(ROOT, OUTPUT), 'utf8') : '';
  if (current !== serialized) {
    fail(`权限矩阵用例与实现不一致（${OUTPUT} 需要重新生成）`);
  }
  console.log(`权限矩阵用例校验通过：${cases.length} 条（角色 ${roles.length} 个）`);
  if (warnings.length) {
    console.log(`  提示 ${warnings.length} 条：`);
    warnings.forEach((w) => console.log(`    - ${w}`));
  }
  process.exit(0);
}

fs.mkdirSync(path.dirname(path.join(ROOT, OUTPUT)), { recursive: true });
fs.writeFileSync(path.join(ROOT, OUTPUT), serialized, 'utf8');
console.log(`已生成 ${OUTPUT}：${cases.length} 条用例（角色 ${roles.length} 个）`);
if (warnings.length) {
  console.log(`提示 ${warnings.length} 条：`);
  warnings.forEach((w) => console.log(`  - ${w}`));
}

function fail(message) {
  console.error(message);
  process.exit(1);
}
