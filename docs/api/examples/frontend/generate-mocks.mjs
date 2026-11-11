#!/usr/bin/env node
// 从 OpenAPI 生成前端类型与 MSW mock handler 的示例脚本
//
// 用途：让前端的请求/响应类型与 mock 数据都从 docs/api/openapi.yaml 单向生成，
//      避免“契约改了口径、前端还在手写类型”的双事实源问题。
//
// 依赖（见同目录 package-snippet.json，复制到项目 package.json 后执行 npm i）：
//   openapi-typescript ^7.13.0  —— 生成 TypeScript 类型
//   msw              ^3.0.4     —— 生成 mock handler，要求 Node >= 22.12
//   js-yaml          ^4.1.0     —— 读取 openapi.yaml（本脚本唯一第三方依赖）
//
// 复制到项目后需要改的 3 处：
//   1. SPEC / TYPE_OUT / MOCK_OUT 三个路径常量（默认按 docs/api/openapi.yaml → src/api/schema.d.ts → src/mocks/handlers.generated.ts）；
//   2. 生成的 handler 里 data 字段需要按真实业务补示例（脚本只能生成结构，生成不出可信的业务数据）；
//   3. 浏览器端要在 src/mocks/browser.ts 用 setupWorker(...handlers) 启动，Node 测试用 setupServer(...handlers)。
//
// 运行：node generate-mocks.mjs
// 产物：src/api/schema.d.ts（类型）、src/mocks/handlers.generated.ts（handlers）

import fs from 'node:fs';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
import yaml from 'js-yaml';

const SPEC = process.env.OPENAPI_SPEC ?? 'docs/api/openapi.yaml';
const TYPE_OUT = process.env.OPENAPI_TYPES_OUT ?? 'src/api/schema.d.ts';
const MOCK_OUT = process.env.MOCK_OUT ?? 'src/mocks/handlers.generated.ts';
const BASE_URL = process.env.MOCK_BASE_URL ?? '';

const HTTP_METHODS = ['get', 'post', 'put', 'patch', 'delete'];

function resolveRef(doc, node, depth = 0) {
  if (!node || depth > 6) return null;
  if (typeof node.$ref === 'string') {
    const name = node.$ref.replace('#/components/schemas/', '');
    return resolveRef(doc, doc.components?.schemas?.[name] ?? null, depth + 1);
  }
  return node;
}

// 从 schema 推导一个结构正确的示例值；有 example 优先用 example
function exampleOf(doc, schema, depth = 0) {
  const s = resolveRef(doc, schema, depth);
  if (!s || depth > 6) return null;
  if (s.example !== undefined) return s.example;
  if (Array.isArray(s.allOf)) {
    let merged = {};
    for (const part of s.allOf) {
      const v = exampleOf(doc, part, depth + 1);
      if (v && typeof v === 'object' && !Array.isArray(v)) merged = { ...merged, ...v };
    }
    return merged;
  }
  if (Array.isArray(s.oneOf) || Array.isArray(s.anyOf)) {
    return exampleOf(doc, (s.oneOf ?? s.anyOf)[0], depth + 1);
  }
  if (s.type === 'array') return [exampleOf(doc, s.items, depth + 1)];
  if (s.type === 'object' || s.properties) {
    const out = {};
    for (const [key, value] of Object.entries(s.properties ?? {})) {
      out[key] = exampleOf(doc, value, depth + 1);
    }
    return out;
  }
  if (s.type === 'integer' || s.type === 'number') return 0;
  if (s.type === 'boolean') return false;
  if (s.type === 'string') return s.format === 'date-time' ? '2026-01-01T00:00:00Z' : 'string';
  return null;
}

function pickSuccessResponse(doc, operation) {
  const responses = operation.responses ?? {};
  for (const code of ['200', '201']) {
    const r = responses[code];
    if (!r) continue;
    const schema = r.content?.['application/json']?.schema;
    return { status: Number(code), data: schema ? exampleOf(doc, schema) : null };
  }
  return { status: 200, data: null };
}

function toMswPath(p) {
  return p.replace(/\{([^}]+)\}/g, ':$1');
}

function main() {
  if (!fs.existsSync(SPEC)) {
    throw new Error(`找不到契约文件：${SPEC}（从 docs/api/openapi.yaml 复制或改 OPENAPI_SPEC）`);
  }
  const doc = yaml.load(fs.readFileSync(SPEC, 'utf8'));
  const paths = doc.paths ?? {};

  // 1) 生成 TypeScript 类型：直接调用 openapi-typescript 的 CLI
  fs.mkdirSync(path.dirname(TYPE_OUT), { recursive: true });
  execFileSync('npx', ['openapi-typescript', SPEC, '-o', TYPE_OUT], { stdio: 'inherit' });

  // 2) 生成 MSW handler：每个 operation 一个 http.<method>()，返回标准信封
  const lines = [
    '// 自动生成文件，请勿手改。重新生成：node generate-mocks.mjs',
    '// 契约来源：docs/api/openapi.yaml（唯一事实源）',
    "import { http, HttpResponse } from 'msw';",
    '',
    'export const handlers = [',
  ];
  for (const [rawPath, item] of Object.entries(paths)) {
    for (const method of HTTP_METHODS) {
      const operation = item[method];
      if (!operation) continue;
      const success = pickSuccessResponse(doc, operation);
      const url = `${BASE_URL}${toMswPath(rawPath)}`;
      const dataLiteral = JSON.stringify(success.data ?? null, null, 2).replace(/\n/g, '\n    ');
      lines.push(`  // ${operation.operationId ?? method + ' ' + rawPath}（x-status: ${operation['x-status'] ?? 'DRAFT'}）`);
      lines.push(`  http.${method}('${url}', () => {`);
      lines.push('    const body = {');
      lines.push('      code: 0,');
      lines.push("      message: 'ok',");
      lines.push(`      data: ${dataLiteral},`);
      lines.push("      traceId: 'mock-trace-000000000000',");
      lines.push('    };');
      lines.push('    // data 里是结构示例，业务字段请按真实用例替换；traceId 必须同时出现在响应头');
      lines.push(`    return HttpResponse.json(body, { status: ${success.status}, headers: { 'X-Trace-Id': body.traceId } });`);
      lines.push('  }),');
    }
  }
  lines.push('];');
  lines.push('');

  fs.mkdirSync(path.dirname(MOCK_OUT), { recursive: true });
  fs.writeFileSync(MOCK_OUT, lines.join('\n'), 'utf8');
  console.log(`已生成：${TYPE_OUT}（类型）、${MOCK_OUT}（handlers，共 ${lines.filter((l) => l.includes('http.')).length} 个）`);
}

try {
  main();
} catch (err) {
  console.error(`生成失败：${err.message}`);
  process.exitCode = 1;
}
