#!/usr/bin/env node
// spec-lint · 规范健康检查（零第三方依赖，只用 Node 内置模块）
//
// 用法：
//   node tools/spec-lint/cli.mjs                     人类可读摘要
//   node tools/spec-lint/cli.mjs --json               结构化输出（给 AI / CI 消费）
//   node tools/spec-lint/cli.mjs --staged             只检查 Git 暂存文件（定义始终取全库）
//   node tools/spec-lint/cli.mjs --root <目录>        指定检查根目录（默认当前目录）
//
// 退出码：0 = 全部通过；1 = 存在 error；2 = 脚本自身异常（参数错误、非 Git 仓库等）。
//
// 判定口径（与 AGENTS.md §3 / §7 对齐）：
//   - 规则编号定义只认 `docs/specs/*.md` 表格首列反引号编号。
//   - 状态 / 流转编号（ST / TR）以 docs/design/state-machine.md 为定义源；项目层缺失时回退同名模板。
//   - 需求（REQ / AC）以 docs/requirements/requirement-spec.md 为定义源；ADR 以 docs/decisions/ADR-*.md 为定义源。
//   - `--staged` 只过滤“引用 / TODO / 路径 / EXAMPLE-ONLY / 引用格式”的检查范围；定义始终从全库收集。
//   - 代码围栏（```…```）内视为示例文本；行内反引号保留，避免状态值被误删。
//   - `APPROVED` 与 TODO 冲突只查项目层 Markdown（`docs/` 下、排除 `docs/templates/`）。
//   - `EXAMPLE-ONLY` 残留：`docs/api/`、`docs/requirements/`、`docs/design/`、`docs/env/`、`docs/decisions/`
//     `docs/baseline/`、`docs/plan/`、`docs/audit/` 属项目层内容产物，命中即 error；`docs/templates/` 豁免。
//   - `TODO(类型)` 的类型必须是 ASCII 大写标识；`TODO(类型)` 中文占位视为文档说明。
//   - 配置项台账优先读 docs/design/config-registry.yaml，缺失时回退 docs/templates/config-registry.yaml；
//     按最小 YAML 子集解析，校验六项必填、key 唯一、effect 三值、STARTUP 与 afterChange 一致。

import fs from 'node:fs';
import path from 'node:path';
import { execFileSync } from 'node:child_process';

// 参与定义 / 引用校验的编号：
//   - 数字编号：前缀 + 模块 + 三位序号（可再带 -数字 子序号，如 AC-001-1）
//   - 状态编号：ST-实体-含义（无数字后缀）
const NUM_PREFIXES = ['CT', 'BE', 'FE', 'UI', 'PRC', 'TR', 'REQ', 'AC', 'ADR'];
const NUM_ID_SRC = `(?:${NUM_PREFIXES.join('|')})(?:-[A-Z0-9]+)*-\\d{3}(?:-\\d+)*`;
const ST_ID_SRC = 'ST-[A-Z0-9_]+(?:-[A-Z0-9_]+)+';
const ID_RE = new RegExp(`\\b(?:${NUM_ID_SRC}|${ST_ID_SRC})\\b`, 'g');
const SPEC_DEF_RE = new RegExp(`^\\|\\s*\`(${NUM_ID_SRC})\``);
const REQ_HEAD_RE = /^#{2,4}\s+`?(REQ-\d{3})`?/;
const CELL_ID_RE = /^\|\s*`([^`]+)`/;
const TODO_RE = /TODO\(([A-Za-z][A-Za-z0-9_]*)\):/g;
const PATH_RE = /(?:docs|tools|scripts)\/[^\s`'"|()（）<>，。；：、]*/g;
const APPROVED_CELL_RE = /\|\s*`?APPROVED`?\s*\|/;

const ALLOWED_TODO_TYPES = ['REQ', 'DATA', 'PERM', 'API', 'UI', 'OPS'];
// 这些目录下的文件在项目初始化后才生成，缺失按 warning 而不是 error
const PROJECT_OUTPUT_DIRS = [
  'docs/requirements/',
  'docs/design/',
  'docs/decisions/',
  'docs/plan/',
  'docs/audit/',
  'docs/env/',
  'docs/baseline/',
];
// EXAMPLE-ONLY 残留按“项目层内容产物”判定的目录（命中即 error）。
// 与 PROJECT_OUTPUT_DIRS 刻意分开：后者只用于 SL-PATH-001 的“路径缺失按 warning”口径，
// 把 docs/api/ 加进去会同时放宽路径检查，故单独维护。docs/api/ 是契约源与错误码表，同属内容产物。
const PROJECT_CONTENT_DIRS = [...PROJECT_OUTPUT_DIRS, 'docs/api/'];
// 初始化后由模板复制生成、脚手架阶段可缺失的文件
const TEMPLATE_TARGETS = [
  ['docs/templates/requirement-spec.md', 'docs/requirements/requirement-spec.md'],
  ['docs/templates/glossary.md', 'docs/requirements/glossary.md'],
  ['docs/templates/data-model.md', 'docs/design/data-model.md'],
  ['docs/templates/permission-matrix.md', 'docs/design/permission-matrix.md'],
  ['docs/templates/state-machine.md', 'docs/design/state-machine.md'],
  ['docs/templates/modules.md', 'docs/design/modules.md'],
  ['docs/templates/env-inventory.md', 'docs/env/env-inventory.md'],
  ['docs/templates/openapi.template.yaml', 'docs/api/openapi.yaml'],
  ['docs/templates/errors.yaml', 'docs/api/errors.yaml'],
  ['docs/templates/config-registry.yaml', 'docs/design/config-registry.yaml'],
  ['docs/templates/baseline-modules.md', 'docs/baseline/modules.md'],
  ['docs/templates/baseline-assertions.md', 'docs/baseline/assertions.md'],
  ['docs/templates/adr-000-template.md', 'docs/decisions/ADR-*.md'],
];

const findings = [];
const stats = {
  'SL-REF-001': { defined: 0, refs: 0 },
  'SL-ID-001': { defined: 0, duplicates: 0 },
  'SL-ID-002': { modules: 0, exempted: 0 },
  'SL-TODO-001': { markers: 0 },
  'SL-TODO-002': { sections: 0 },
  'SL-PATH-001': { paths: 0, skipped: 0 },
  'SL-EXAMPLE-001': { files: 0, hits: 0 },
  'SL-REFMT-001': { refs: 0 },
  'SL-TPL-001': { checked: 0, missing: 0 },
  'SL-FACT-001': { facts: 0, compared: 0 },
  'SL-CFG-001': { file: '', entries: 0 },
};

function add(ruleId, severity, file, line, message) {
  findings.push({ ruleId, severity, file, line, message });
}

function parseArgs(argv) {
  const opt = { json: false, staged: false, root: process.cwd(), help: false };
  for (let i = 0; i < argv.length; i += 1) {
    const a = argv[i];
    if (a === '--json') opt.json = true;
    else if (a === '--staged') opt.staged = true;
    else if (a === '--root') {
      if (i + 1 === argv.length) throw new Error('--root 需要一个目录参数');
      opt.root = argv[i + 1];
      i += 1;
    } else if (a === '--help' || a === '-h') opt.help = true;
    else throw new Error(`未知参数：${a}`);
  }
  return opt;
}

function walk(dir, acc) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    if (entry.name === 'node_modules' || entry.name === '.git') continue;
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) walk(full, acc);
    else if (/\.(md|yaml|yml)$/.test(entry.name)) acc.push(full);
  }
  return acc;
}

function readLines(file) {
  return fs.readFileSync(file, 'utf8').replace(/\r\n/g, '\n').split('\n');
}

// 逐行去掉代码围栏内的内容，保留行号与列位置；行内反引号**保留**
function fenceMasked(lines) {
  const out = [];
  let inFence = false;
  for (const line of lines) {
    if (line.trim().startsWith('```')) {
      inFence = !inFence;
      out.push('');
      continue;
    }
    out.push(inFence ? '' : line);
  }
  return out;
}

function matchIds(line) {
  return line.match(ID_RE) ?? [];
}

function isNumericId(id) {
  return new RegExp(`^(?:${NUM_PREFIXES.join('|')})(?:-[A-Z0-9]+)*-\\d{3}(?:-\\d+)*$`).test(id);
}

function isStateId(id) {
  return id.startsWith('TR-') || new RegExp(`^${ST_ID_SRC}$`).test(id);
}

// 目标路径支持简单通配（仅 `*`，按相对路径整串匹配），用于“复制为任意文件名”的模板（如 ADR）
function globToRe(glob) {
  return new RegExp(`^${glob.split('*').map((s) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')).join('[^/]*')}$`);
}

function firstExisting(root, candidates) {
  for (const c of candidates) {
    const full = path.join(root, c);
    if (fs.existsSync(full)) return { rel: c, full };
  }
  return null;
}

function main() {
  const opt = parseArgs(process.argv.slice(2));
  if (opt.help) {
    console.log('用法：spec-lint [--json] [--staged] [--root <目录>]');
    return 0;
  }
  const root = path.resolve(opt.root);
  if (!fs.existsSync(root) || !fs.statSync(root).isDirectory()) {
    throw new Error(`检查根目录不存在：${root}`);
  }

  const allFiles = walk(root, []);
  let files = allFiles;
  if (opt.staged) {
    let out;
    try {
      out = execFileSync('git', ['diff', '--cached', '--name-only', '--diff-filter=ACM'], {
        cwd: root,
        encoding: 'utf8',
        stdio: ['ignore', 'pipe', 'ignore'],
      });
    } catch (err) {
      throw new Error('--staged 只支持 Git 仓库；请在 Git 工作区内运行，或去掉 --staged');
    }
    const staged = new Set(
      out
        .split(/\r?\n/)
        .filter(Boolean)
        .map((p) => path.resolve(root, p)),
    );
    files = allFiles.filter((f) => staged.has(f));
  }

  const rel = (f) => path.relative(root, f).split(path.sep).join('/');
  const isSpec = (f) => rel(f).startsWith('docs/specs/');
  const relSet = new Set(allFiles.map(rel));
  // 项目层是否已初始化：决定模板是定义源还是纯示例
  const initialized = relSet.has('docs/design/state-machine.md') || relSet.has('docs/requirements/requirement-spec.md');

  // ---------- 定义收集（始终全库） ----------
  const defined = new Map(); // id -> [{ file, line }]
  const define = (id, file, line) => {
    if (!defined.has(id)) defined.set(id, []);
    defined.get(id).push({ file, line });
  };

  for (const f of allFiles) {
    const r = rel(f);
    const lines = readLines(f);
    const masked = fenceMasked(lines);

    // 规则正文：表格首列反引号编号
    if (isSpec(f)) {
      masked.forEach((line, idx) => {
        const m = SPEC_DEF_RE.exec(line);
        if (m) define(m[1], r, idx + 1);
      });
      continue;
    }

    // 状态机：表格首列 ST / TR 编号；项目层文件存在时模板不再作为定义源
    // ST 状态允许在“禁止转移”等表中被再次引用，按首次出现记为定义（跨文件重复仍会报）
    if (r === 'docs/design/state-machine.md' || (r === 'docs/templates/state-machine.md' && !relSet.has('docs/design/state-machine.md'))) {
      const seenInFile = new Set();
      masked.forEach((line, idx) => {
        const m = CELL_ID_RE.exec(line);
        if (!m || !isStateId(m[1])) return;
        if (m[1].startsWith('ST-') && seenInFile.has(m[1])) return;
        seenInFile.add(m[1]);
        define(m[1], r, idx + 1);
      });
      continue;
    }

    // 需求：`### REQ-xxx` 标题定义需求；AC 以首次出现为定义；项目层文件存在时模板不再作为定义源
    if (r === 'docs/requirements/requirement-spec.md' || (r === 'docs/templates/requirement-spec.md' && !relSet.has('docs/requirements/requirement-spec.md'))) {
      const acSeen = new Set();
      masked.forEach((line, idx) => {
        const h = REQ_HEAD_RE.exec(line);
        if (h) define(h[1], r, idx + 1);
        for (const id of matchIds(line)) {
          if (id.startsWith('AC-') && !acSeen.has(id)) {
            acSeen.add(id);
            define(id, r, idx + 1);
          }
        }
      });
      continue;
    }

    // ADR：项目层每个文件的首个 ADR 编号即该文件定义；模板内含多个示例编号，未初始化时全部记为定义
    if (r.startsWith('docs/decisions/')) {
      for (let i = 0; i < masked.length; i += 1) {
        const adr = matchIds(masked[i]).find((id) => id.startsWith('ADR-'));
        if (adr) {
          define(adr, r, i + 1);
          break;
        }
      }
    } else if (r === 'docs/templates/adr-000-template.md') {
      // ADR-000 是模板自身编号，始终算定义；示例中提到的 ADR-001/ADR-002 仅在未初始化时计入
      const seen = new Set();
      masked.forEach((line, idx) => {
        for (const id of matchIds(line)) {
          if (!id.startsWith('ADR-') || seen.has(id)) continue;
          if (initialized && id !== 'ADR-000') continue;
          seen.add(id);
          define(id, r, idx + 1);
        }
      });
    }
  }

  // 模板落位后，项目层文件优先作为定义源；同名模板此时只按引用处理
  stats['SL-REF-001'].defined = defined.size;

  // ---------- 规则 1：悬空引用 ----------
  const refs = [];
  for (const f of files) {
    const r = rel(f);
    // 已初始化后，docs/templates 只保留为示例，不参与引用校验（避免示例编号与项目编号互不匹配）
    if (initialized && r.startsWith('docs/templates/')) continue;
    const lines = readLines(f);
    const masked = fenceMasked(lines);
    masked.forEach((line, idx) => {
      const lineNo = idx + 1;
      const ids = matchIds(line);
      const localDefs = new Set(
        ids.filter((id) => (defined.get(id) ?? []).some((d) => d.file === r && d.line === lineNo)),
      );
      const seen = new Map();
      for (const id of ids) {
        const n = (seen.get(id) ?? 0) + 1;
        seen.set(id, n);
        // 定义行只豁免本行自身的首次定义，其余编号照常校验
        if (localDefs.has(id) && n === 1) continue;
        refs.push({ id, file: r, line: lineNo });
      }
    });
  }
  stats['SL-REF-001'].refs = refs.length;
  for (const r of refs) {
    if (!defined.has(r.id)) {
      add('SL-REF-001', 'error', r.file, r.line, `引用了未定义的编号 ${r.id}`);
    }
  }

  // ---------- 规则 2：编号重复与跳号 ----------
  stats['SL-ID-001'].defined = defined.size;
  for (const [id, list] of defined) {
    if (list.length > 1) {
      stats['SL-ID-001'].duplicates += 1;
      for (const dup of list.slice(1)) {
        add('SL-ID-001', 'error', dup.file, dup.line,
          `编号 ${id} 重复定义（首次在 ${list[0].file}:${list[0].line}）`);
      }
    }
  }
  const modules = new Map(); // 模块前缀 -> { nums:Set, sample:{file,line} }
  for (const [id, list] of defined) {
    if (!isNumericId(id) || /-\d+$/.test(id) === false) continue;
    if (id.startsWith('ST-')) continue;
    const m = /^(.+)-(\d{3})$/.exec(id);
    if (!m) continue;
    if (!modules.has(m[1])) modules.set(m[1], { nums: new Set(), sample: list[0] });
    modules.get(m[1]).nums.add(Number(m[2]));
  }
  stats['SL-ID-002'].modules = modules.size;
  // 豁免登记：编号是历史标识而非排序序号，不得为通过检查而重排既有编号。
  // 只豁免“起始编号”判定，中间跳号仍照常报错。
  const SEQUENCE_START_EXEMPTIONS = new Map([
    ['ADR', {
      start: 3,
      reason: 'ADR-001/002 未使用属历史事实；2026-11-10 曾因跳号重排为 001/002，同日回退，见 ADR-003/ADR-004 编号说明',
    }],
  ]);
  for (const [mod, { nums, sample }] of modules) {
    const max = Math.max(...nums);
    const exemption = SEQUENCE_START_EXEMPTIONS.get(mod);
    const start = exemption ? exemption.start : 1;
    if (exemption) stats['SL-ID-002'].exempted += 1;
    for (let n = start; n <= max; n += 1) {
      if (!nums.has(n)) {
        add('SL-ID-002', 'error', sample.file, sample.line,
          `${mod} 跳号：缺少 ${String(n).padStart(3, '0')}`);
      }
    }
  }

  // ---------- 规则 3：TODO 残留 ----------
  for (const f of files) {
    const raw = readLines(f);
    const masked = fenceMasked(raw);
    const hits = [];
    masked.forEach((line, idx) => {
      for (const m of line.matchAll(TODO_RE)) hits.push({ type: m[1], line: idx + 1 });
    });
    stats['SL-TODO-001'].markers += hits.length;
    for (const h of hits) {
      if (!ALLOWED_TODO_TYPES.includes(h.type)) {
        add('SL-TODO-001', 'error', rel(f), h.line,
          `TODO 类型非法：${h.type}（只允许 ${ALLOWED_TODO_TYPES.join('/')}）`);
      }
    }

    // 同章节既有已签核条目又有未解决 TODO → 错误。按原文匹配 APPROVED 状态单元格，只排除代码围栏。
    // 模板是空壳，允许保留 TODO 占位；项目层 Markdown 才参与判定
    const rf = rel(f);
    if (rf.startsWith('docs/templates/') || !rf.endsWith('.md')) continue;
    let sectionStart = 1;
    let approvedLine = 0;
    let todoLine = 0;
    const flush = (endLine) => {
      stats['SL-TODO-002'].sections += 1;
      if (approvedLine && todoLine) {
        add('SL-TODO-002', 'error', rel(f), todoLine,
          `第 ${sectionStart}–${endLine} 行章节内既有 APPROVED 条目又有未解决 TODO（APPROVED 行 ${approvedLine}；${todoLine} 行 TODO）`);
      }
      approvedLine = 0;
      todoLine = 0;
    };
    for (let i = 0; i < masked.length; i += 1) {
      if (masked[i].startsWith('## ')) {
        flush(i);
        sectionStart = i + 1;
        continue;
      }
      if (!approvedLine && APPROVED_CELL_RE.test(masked[i])) approvedLine = i + 1;
      if ([...masked[i].matchAll(TODO_RE)].length > 0) todoLine = i + 1;
    }
    flush(masked.length);
  }

  // ---------- 规则 4：路径存在性 ----------
  const templateNames = new Set();
  const templatesDir = path.join(root, 'docs/templates');
  if (fs.existsSync(templatesDir)) {
    for (const f of fs.readdirSync(templatesDir)) templateNames.add(f);
  }
  for (const f of files) {
    const lines = readLines(f);
    const masked = fenceMasked(lines);
    masked.forEach((line, idx) => {
      // 表格“合格示例/不合格示例”两列（规则表第 7、8 列）里的路径是示例，可能故意不存在，不校验
      const pipeBefore = (start) => (line.slice(0, start).match(/\|/g) ?? []).length;
      for (const raw of line.matchAll(PATH_RE)) {
        if (line.startsWith('|') && pipeBefore(raw.index) >= 6) {
          stats['SL-PATH-001'].skipped += 1;
          continue;
        }
        let p = raw[0].replace(/[.,;:)\]}>"'`]+$/, '').replace(/[。，；：）】、]+$/, '');
        if (!p || p.endsWith('/')) {
          stats['SL-PATH-001'].skipped += 1;
          continue;
        }
        if (p.includes('*') || p.includes('{') || p.includes('<') || /xxx|XXX/.test(p) || /[-_]$/.test(p)) {
          stats['SL-PATH-001'].skipped += 1;
          continue;
        }
        stats['SL-PATH-001'].paths += 1;
        if (fs.existsSync(path.join(root, p))) continue;
        if (PROJECT_OUTPUT_DIRS.some((d) => p.startsWith(d))) {
          add('SL-PATH-001', 'warning', rel(f), idx + 1,
            `路径 ${p} 在项目初始化后生成，当前不存在（阶段 0 需创建）`);
          continue;
        }
        // 指向模板目标位置的引用（如 docs/api/errors.yaml）：初始化时按 AGENTS.md §11.1 复制
        if (templateNames.has(path.basename(p))) {
          add('SL-PATH-001', 'warning', rel(f), idx + 1,
            `路径 ${p} 尚未创建，需按 AGENTS.md §11.1 从 docs/templates/${path.basename(p)} 复制`);
          continue;
        }
        // 生成产物（如 docs/api/openapi.generated.yaml）：尚未导出时按 warning，不当作路径笔误
        if (/\.generated\.(ya?ml)$/.test(p)) {
          add('SL-PATH-001', 'warning', rel(f), idx + 1,
            `生成产物 ${p} 尚未导出（运行 scripts/export-openapi.ps1 生成）`);
          continue;
        }
        add('SL-PATH-001', 'error', rel(f), idx + 1, `路径不存在：${p}`);
      }
    });
  }

  // ---------- 规则 5：EXAMPLE-ONLY 残留 ----------
  // 只查“内容产物”目录；AGENTS / README / specs 里出现该词是在说明规则本身，不算残留
  const EXAMINE_PREFIXES = ['docs/api/', 'docs/requirements/', 'docs/design/', 'docs/env/', 'docs/decisions/', 'docs/baseline/', 'docs/plan/', 'docs/audit/'];
  for (const f of files) {
    const r = rel(f);
    if (r.startsWith('docs/templates/')) continue; // 空壳模板豁免
    if (!EXAMINE_PREFIXES.some((d) => r.startsWith(d))) continue;
    stats['SL-EXAMPLE-001'].files += 1;
    const lines = readLines(f);
    lines.forEach((line, idx) => {
      if (!line.includes('EXAMPLE-ONLY')) return;
      // 同一行提到规则编号 `SL-EXAMPLE-001` 的，视为“说明规则本身”而非内容残留（如 health-check / 变更记录引用规则）
      if (line.includes('SL-EXAMPLE-001')) return;
      stats['SL-EXAMPLE-001'].hits += 1;
      const isProjectLayer = PROJECT_CONTENT_DIRS.some((d) => r.startsWith(d))
        || r === 'docs/api/errors.yaml';
      add('SL-EXAMPLE-001', isProjectLayer ? 'error' : 'warning', r, idx + 1,
        isProjectLayer
          ? '项目层文件仍残留 EXAMPLE-ONLY，必须替换或清空'
          : '脚手架示例仍含 EXAMPLE-ONLY；复制到项目层后必须替换');
    });
  }

  // ---------- 规则 6：跨文档引用格式 ----------
  for (const f of files) {
    const r = rel(f);
    const selfName = path.basename(r);
    const masked = fenceMasked(readLines(f));
    masked.forEach((line, idx) => {
      if (/^\|\s*v\d/.test(line)) return; // 变更记录是历史事实，不参与引用格式检查
      const mdTokens = [...line.matchAll(/([A-Za-z0-9_\-./]+\.md)/g)].map((m) => path.basename(m[1]));
      const otherDocs = mdTokens.filter((name) => name !== selfName);
      for (const m of line.matchAll(/§\s*[\d.]+/g)) {
        const before = line.slice(Math.max(0, m.index - 30), m.index);
        const after = line.slice(m.index + m[0].length, m.index + m[0].length + 30);
        if (before.includes('.md') || after.includes('.md')) continue; // 邻近文件名，引用明确
        if (/本文件|本规范|本表|本文|本章|本流程/.test(before.slice(-25))) continue; // 同文档自引用
        if (otherDocs.length === 0) continue; // 同文档自引用，允许
        stats['SL-REFMT-001'].refs += 1;
        add('SL-REFMT-001', 'warning', r, idx + 1,
          `跨文档章节引用“${m[0]}”未紧随文件名：应只写规则编号`);
      }
    });
  }

  // ---------- 规则 7：模板落位 ----------
  for (const [source, target] of TEMPLATE_TARGETS) {
    stats['SL-TPL-001'].checked += 1;
    const targetExists = target.includes('*')
      ? allFiles.some((f) => globToRe(target).test(rel(f)))
      : relSet.has(target);
    if (targetExists) continue;
    stats['SL-TPL-001'].missing += 1;
    if (!relSet.has(source)) {
      add('SL-TPL-001', 'error', 'AGENTS.md', 1, `模板落位缺失：${source} 与目标 ${target} 都不存在`);
      continue;
    }
    add('SL-TPL-001', 'warning', 'AGENTS.md', 1,
      `模板 ${source} 尚未落位到 ${target}（脚手架阶段正常；初始化后必须存在）`);
  }

  // ---------- 规则 8：INDEX-facts 与 facts.yaml 一致 ----------
  const indexRel = 'docs/specs/INDEX-facts.md';
  const factsRel = 'docs/specs/facts.yaml';
  if (relSet.has(indexRel) && relSet.has(factsRel)) {
    const indexRows = [];
    readLines(path.join(root, indexRel)).forEach((line, idx) => {
      const m = /^\|\s*([^|]+?)\s*\|\s*([^|]+?)\s*\|\s*([^|]+?)\s*\|\s*(\d{4}-\d{2}-\d{2})\s*\|/.exec(line);
      if (!m || m[1] === '事实' || m[1].startsWith('---')) return;
      indexRows.push({ owner: m[2], refs: m[3], line: idx + 1 });
    });

    const yamlRows = [];
    let current = null;
    readLines(path.join(root, factsRel)).forEach((line, idx) => {
      const idM = /^\s*-\s*id:\s*(F-[A-Za-z0-9-]+)\s*$/.exec(line);
      if (idM) {
        current = { id: idM[1], owner: '', refs: [], line: idx + 1 };
        yamlRows.push(current);
        return;
      }
      if (!current) return;
      const ownerM = /^\s*owner:\s*(.+?)\s*$/.exec(line);
      if (ownerM) current.owner = ownerM[1];
      const refsM = /^\s*refs:\s*\[[^\]]*\]\s*$/.exec(line);
      if (refsM) {
        current.refs = [...line.matchAll(new RegExp(`\\b(?:${NUM_ID_SRC}|${ST_ID_SRC})\\b`, 'g'))]
          .map((x) => x[0]);
      }
    });

    stats['SL-FACT-001'].facts = yamlRows.length;
    const normalizeOwner = (s) => {
      const cleaned = s.split('`').join('').trim();
      // 定义方写成“文档名 + 编号”时取编号；写成项目层路径时保留路径
      const ids = [...cleaned.matchAll(new RegExp(`\\b(?:${NUM_ID_SRC}|${ST_ID_SRC})\\b`, 'g'))].map((x) => x[0]);
      if (ids.length > 0) return ids.join(',');
      return cleaned
        .replace(/\s+/g, '')
        .replace(/AGENTS\.md#/g, 'AGENTS.md§')
        .replace(/AGENTS\.md§/g, 'AGENTS.md§');
    };
    const normalizeRefs = (cellOrList) => {
      const raw = Array.isArray(cellOrList) ? cellOrList.join(' ') : cellOrList;
      return new Set([...raw.matchAll(new RegExp(`\\b(?:${NUM_ID_SRC}|${ST_ID_SRC})\\b`, 'g'))].map((x) => x[0]));
    };
    const signature = (owner, refs) => `${normalizeOwner(owner)} :: [${[...normalizeRefs(refs)].sort().join(',')}]`;

    if (indexRows.length !== yamlRows.length) {
      add('SL-FACT-001', 'error', indexRel, 1,
        `事实条数不一致：INDEX-facts ${indexRows.length} 条，facts.yaml ${yamlRows.length} 条`);
    }
    // 按“定义方 + 引用集合”多重集合比对：允许两份文件行顺序不同，但同一事实内容必须一致
    const mdSigs = new Map();
    for (const row of indexRows) {
      const sig = signature(row.owner, row.refs);
      if (!mdSigs.has(sig)) mdSigs.set(sig, []);
      mdSigs.get(sig).push(row.line);
    }
    const yamlSigs = new Map();
    for (const row of yamlRows) {
      const sig = signature(row.owner, row.refs);
      if (!yamlSigs.has(sig)) yamlSigs.set(sig, []);
      yamlSigs.get(sig).push(row.id);
    }
    const allSigs = new Set([...mdSigs.keys(), ...yamlSigs.keys()]);
    for (const sig of allSigs) {
      const mdCount = (mdSigs.get(sig) ?? []).length;
      const yCount = (yamlSigs.get(sig) ?? []).length;
      if (mdCount === yCount) {
        stats['SL-FACT-001'].compared += mdCount;
        continue;
      }
      const lines = mdSigs.get(sig) ?? [];
      add('SL-FACT-001', 'error', indexRel, lines[0] ?? 1,
        `事实内容不一致：INDEX ${mdCount} 条 vs facts.yaml ${yCount} 条（${sig}）`);
    }
  }

  // ---------- 规则 9：配置项台账 ----------
  // 台账是项目层文件；脚手架阶段回退到模板。只解析最小 YAML 子集：`- key:` 起一条，缩进的 `字段: 值` 为其字段。
  const cfgFile = firstExisting(root, ['docs/design/config-registry.yaml', 'docs/templates/config-registry.yaml']);
  if (cfgFile) {
    stats['SL-CFG-001'].file = cfgFile.rel;
    const CFG_REQUIRED = ['key', 'owner', 'consumedBy', 'effect', 'afterChange', 'verify'];
    const CFG_EFFECTS = ['STARTUP', 'HOT', 'DISPLAY_ONLY'];
    const unquote = (raw) => {
      let v = raw.trim();
      const q = /^(['"])(.*)\1$/.exec(v);
      if (q) return q[2];
      const hash = v.indexOf(' #');
      if (hash >= 0) v = v.slice(0, hash).trim();
      return v;
    };
    const items = [];
    let current = null;
    readLines(cfgFile.full).forEach((line, idx) => {
      if (line.trim().startsWith('#')) return;
      const keyM = /^\s*-\s*key:\s*(.*)$/.exec(line);
      if (keyM) {
        current = { line: idx + 1, values: { key: unquote(keyM[1]) } };
        items.push(current);
        return;
      }
      if (!current) return;
      const fieldM = /^\s{2,}([A-Za-z][A-Za-z0-9_-]*):\s*(.*)$/.exec(line);
      if (!fieldM) return;
      current.values[fieldM[1]] = unquote(fieldM[2]);
    });
    stats['SL-CFG-001'].entries = items.length;

    const seenKeys = new Map();
    for (const item of items) {
      const v = item.values;
      const label = v.key ? `配置项 ${v.key}` : `第 ${item.line} 行配置项`;
      for (const field of CFG_REQUIRED) {
        if (!v[field]) {
          add('SL-CFG-001', 'error', cfgFile.rel, item.line, `${label} 缺少必填字段 ${field}`);
        }
      }
      if (v.key) {
        if (seenKeys.has(v.key)) {
          add('SL-CFG-001', 'error', cfgFile.rel, item.line,
            `配置项 key 重复：${v.key}（首次在 ${cfgFile.rel}:${seenKeys.get(v.key)}）`);
        } else {
          seenKeys.set(v.key, item.line);
        }
      }
      if (v.effect && !CFG_EFFECTS.includes(v.effect)) {
        add('SL-CFG-001', 'error', cfgFile.rel, item.line,
          `${label} 的 effect 非法：${v.effect}（只允许 ${CFG_EFFECTS.join('/')}）`);
      }
      if (v.effect === 'STARTUP' && v.afterChange && !v.afterChange.includes('重启')) {
        add('SL-CFG-001', 'error', cfgFile.rel, item.line,
          `${label} 的 effect=STARTUP，但 afterChange 未写“重启”：${v.afterChange}`);
      }
    }
  }

  // ---------- 输出 ----------
  const errors = findings.filter((x) => x.severity === 'error');
  const warnings = findings.filter((x) => x.severity === 'warning');
  const scanned = files.length;

  if (opt.json) {
    console.log(JSON.stringify({
      root,
      scannedFiles: scanned,
      stats,
      summary: { errors: errors.length, warnings: warnings.length },
      findings,
    }, null, 2));
  } else {
    const rules = [
      ['SL-REF-001', '悬空引用'],
      ['SL-ID-001', '编号重复'],
      ['SL-ID-002', '编号跳号'],
      ['SL-TODO-001', 'TODO 类型非法'],
      ['SL-TODO-002', '已签核条目含未解决 TODO'],
      ['SL-PATH-001', '路径不存在'],
      ['SL-EXAMPLE-001', 'EXAMPLE-ONLY 残留'],
      ['SL-REFMT-001', '跨文档引用格式'],
      ['SL-TPL-001', '模板落位'],
      ['SL-FACT-001', '事实索引 md/yaml 一致'],
      ['SL-CFG-001', '配置项台账'],
    ];
    const detail = (ruleId) => {
      switch (ruleId) {
        case 'SL-REF-001':
          return `定义 ${stats['SL-REF-001'].defined} 个编号，检查 ${stats['SL-REF-001'].refs} 处引用`;
        case 'SL-ID-001':
          return `检查 ${stats['SL-ID-001'].defined} 个编号定义`;
        case 'SL-ID-002':
          return `检查 ${stats['SL-ID-002'].modules} 个模块分组的连续性`;
        case 'SL-TODO-001':
          return `检查 ${stats['SL-TODO-001'].markers} 处 TODO 标记`;
        case 'SL-TODO-002':
          return `检查 ${stats['SL-TODO-002'].sections} 个章节`;
        case 'SL-PATH-001':
          return `检查 ${stats['SL-PATH-001'].paths} 条路径（跳过通配/模板占位 ${stats['SL-PATH-001'].skipped} 条）`;
        case 'SL-EXAMPLE-001':
          return `检查 ${stats['SL-EXAMPLE-001'].files} 个非模板文件，命中 ${stats['SL-EXAMPLE-001'].hits} 处`;
        case 'SL-REFMT-001':
          return `检查 ${stats['SL-REFMT-001'].refs} 处无文件名的章节引用`;
        case 'SL-TPL-001':
          return `检查 ${stats['SL-TPL-001'].checked} 个模板落位，缺失 ${stats['SL-TPL-001'].missing} 个`;
        case 'SL-FACT-001':
          return `比对 ${stats['SL-FACT-001'].compared}/${stats['SL-FACT-001'].facts} 条事实`;
        case 'SL-CFG-001':
          return stats['SL-CFG-001'].file
            ? `读取 ${stats['SL-CFG-001'].file}，检查 ${stats['SL-CFG-001'].entries} 个配置项`
            : '未找到配置项台账（docs/design/config-registry.yaml 与模板都不存在）';
        default:
          return '';
      }
    };
    console.log(`spec-lint · root=${root} · 扫描 ${scanned} 个 md/yaml 文件`);
    for (const [rid, name] of rules) {
      const list = findings.filter((x) => x.ruleId === rid);
      const errs = list.filter((x) => x.severity === 'error');
      const warns = list.filter((x) => x.severity === 'warning');
      const state = errs.length === 0 ? (warns.length ? '通过（有 warning）' : '通过') : '失败';
      console.log(`\n[${rid}] ${name} · ${state} · ${detail(rid)}`);
      for (const x of errs) console.log(`  ✗ ${x.file}:${x.line}  ${x.message}`);
      if (warns.length === 0) continue;
      // warning 按目标目录聚合，避免同一类“项目初始化后生成”刷屏；明细用 --json 获取
      const groups = new Map();
      for (const x of warns) {
        const m = /路径 (docs\/[^/]+)\//.exec(x.message);
        const key = m ? m[1] : '其他';
        if (!groups.has(key)) groups.set(key, 0);
        groups.set(key, groups.get(key) + 1);
      }
      const parts = [...groups.entries()].map(([k, n]) => `${k} ${n} 处`);
      console.log(`  · warning：${parts.join('，')}（初始化阶段出现属预期；明细用 --json）`);
    }
    console.log(`\n结果：error ${errors.length} 项，warning ${warnings.length} 项 → ${errors.length === 0 ? '通过' : '不通过'}`);
  }
  return errors.length === 0 ? 0 : 1;
}

let code;
try {
  code = main();
} catch (err) {
  console.error(`spec-lint 自身异常：${err.message}`);
  code = 2;
}
process.exit(code);
