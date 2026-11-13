#!/usr/bin/env node
/**
 * 模块生成器（P0-1）。设计见 `docs/design/module-generator.md`。
 *
 * 定位：**开发期脚手架**，产出源码 → 进仓库 → 走 PR + CI。不是运行时功能，不写服务器磁盘。
 *
 * 用法：
 *   node tools/gen-module/cli.mjs tools/gen-module/examples/customer.json          # 生成
 *   node tools/gen-module/cli.mjs tools/gen-module/examples/customer.json --dry    # 只打印将写入的文件
 *   node tools/gen-module/cli.mjs tools/gen-module/examples/customer.json --check  # 重生成并与现有文件比对（模板腐化检测）
 *
 * 三条设计红线（改之前先看断言 #107）：
 * 1. **一次性生成、之后手改**：不做双向同步，不解析已有代码。生成器只保证"起点正确"。
 * 2. **生成物与手写代码受同一套门禁**：契约/spec-lint/断言/测试不区分来源；`--check` 只比对模板输出，
 *    因此手工改动过的文件会用 `--force` 之外的方式提示，不会静默覆盖。
 * 3. **不猜业务**：输入里没声明的（数据权限、导入导出、脱敏字段）就不生成，宁可少写让人补，
 *    也不能生成一个"看起来完整但语义错"的模块。
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');

const args = process.argv.slice(2);
const descriptorPath = args.find((arg) => !arg.startsWith('--'));
const dryRun = args.includes('--dry');
const checkOnly = args.includes('--check');
const force = args.includes('--force');

if (!descriptorPath) {
  console.error('用法：node tools/gen-module/cli.mjs <module.json> [--dry] [--check] [--force]');
  process.exit(2);
}

const spec = JSON.parse(fs.readFileSync(descriptorPath, 'utf8'));
validateSpec(spec);

const Cap = capitalize(spec.key);            // Customer
const camel = spec.key;                       // customer
const pkg = spec.package ?? 'com.adminstarter.system';
const fields = spec.fields;
const permission = spec.permissionPrefix;     // demo:customer
const restPath = spec.path.replace(/^\/+/, '');            // demo/customers
const apiPath = `/api/v1/${restPath}`;                     // 后端 @RequestMapping
const fePath = `/${restPath}`;                             // 前端 api 层（baseURL 已含 /api/v1）
const javaRoot = 'server/system/src/main/java/com/adminstarter/system';
const webRoot = 'server/web/src/main/java/com/adminstarter/web';
const migrationDir = 'server/web/src/main/resources/db/migration';

const files = new Map();

// ------------------------------------------------------------------ 后端：领域层
files.set(`${javaRoot}/domain/model/${Cap}.java`, `package ${pkg}.domain.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** ${spec.name}（由 tools/gen-module 生成，之后按需手改；生成器不做双向同步）。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ${Cap} extends BaseModel {
${fields.map((f) => `    private ${javaType(f)} ${f.name};`).join('\n')}
}
`);

files.set(`${javaRoot}/domain/query/${Cap}Query.java`, `package ${pkg}.domain.query;

import lombok.Data;

/** ${spec.name}列表查询条件。 */
@Data
public class ${Cap}Query {

    private String keyword;
${fields.filter((f) => f.filter).map((f) => `    private ${javaType(f)} ${f.name};`).join('\n')}${fields.some((f) => f.filter) ? '\n' : ''}    private int page = 1;
    private int pageSize = 20;
    private String sort;
    private String order;
}
`);

files.set(`${javaRoot}/domain/repository/${Cap}Repository.java`, `package ${pkg}.domain.repository;

import com.adminstarter.common.api.PageResult;
import ${pkg}.domain.model.${Cap};
import ${pkg}.domain.query.${Cap}Query;

public interface ${Cap}Repository {

    ${Cap} findById(Long id);

    void insert(${Cap} ${camel});

    void update(${Cap} ${camel});

    void deleteById(Long id);

    PageResult<${Cap}> page(${Cap}Query query);
}
`);

// ------------------------------------------------------------------ 后端：持久化层
files.set(`${javaRoot}/infrastructure/persistence/entity/${Cap}DO.java`, `package ${pkg}.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("${spec.table}")
public class ${Cap}DO extends BaseDO {
${fields.map((f) => `    private ${javaType(f)} ${f.name};`).join('\n')}
}
`);

files.set(`${javaRoot}/infrastructure/persistence/mapper/${Cap}Mapper.java`, `package ${pkg}.infrastructure.persistence.mapper;

import ${pkg}.infrastructure.persistence.entity.${Cap}DO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/** ${spec.name}：单表 CRUD 由 MyBatis-Plus 提供，无自定义 SQL。 */
public interface ${Cap}Mapper extends BaseMapper<${Cap}DO> {
}
`);

files.set(`${javaRoot}/infrastructure/persistence/converter/${Cap}Converter.java`, `package ${pkg}.infrastructure.persistence.converter;

import ${pkg}.domain.model.${Cap};
import ${pkg}.infrastructure.persistence.entity.${Cap}DO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ${Cap}Converter extends TimeConverter {

    ${Cap} toDomain(${Cap}DO source);

    ${Cap}DO toDO(${Cap} source);
}
`);

files.set(`${javaRoot}/infrastructure/persistence/repository/${Cap}RepositoryImpl.java`, `package ${pkg}.infrastructure.persistence.repository;

import com.adminstarter.common.api.PageResult;
import ${pkg}.domain.model.${Cap};
import ${pkg}.domain.query.${Cap}Query;
import ${pkg}.domain.repository.${Cap}Repository;
import ${pkg}.infrastructure.persistence.SortSupport;
import ${pkg}.infrastructure.persistence.converter.${Cap}Converter;
import ${pkg}.infrastructure.persistence.entity.${Cap}DO;
import ${pkg}.infrastructure.persistence.mapper.${Cap}Mapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Map;

@Repository
@RequiredArgsConstructor
public class ${Cap}RepositoryImpl implements ${Cap}Repository {

    /**
     * 可排序字段白名单（前端 \`sortKey\` 只能取这里的值）。
     * 用 \`SortSupport.resolveColumn\` 解析：白名单外与 null 都会回落默认列，
     * **禁止直接写 \`SORTS.get(sort)\`**——不可变 Map 的 get(null) 会抛 NPE（断言 #103）。
     */
    private static final Map<String, String> SORTS = Map.of(
${sortEntries()}
    );

    private final ${Cap}Mapper ${camel}Mapper;
    private final ${Cap}Converter converter;

    @Override
    public ${Cap} findById(Long id) {
        return converter.toDomain(${camel}Mapper.selectById(id));
    }

    @Override
    public void insert(${Cap} ${camel}) {
        ${Cap}DO row = converter.toDO(${camel});
        ${camel}Mapper.insert(row);
        ${camel}.setId(row.getId());
        ${camel}.setCreatedAt(row.getCreatedAt());
        ${camel}.setUpdatedAt(row.getUpdatedAt());
    }

    @Override
    public void update(${Cap} ${camel}) {
        ${camel}Mapper.updateById(converter.toDO(${camel}));
    }

    @Override
    public void deleteById(Long id) {
        ${camel}Mapper.deleteById(id);
    }

    @Override
    public PageResult<${Cap}> page(${Cap}Query query) {
        Page<${Cap}DO> page = ${camel}Mapper.selectPage(new Page<>(query.getPage(), query.getPageSize()), wrapper(query));
        return PageResult.of(page.getRecords().stream().map(converter::toDomain).toList(), page.getTotal());
    }

    private QueryWrapper<${Cap}DO> wrapper(${Cap}Query query) {
        QueryWrapper<${Cap}DO> wrapper = new QueryWrapper<>();
${keywordWrapper()}${filterWrappers()}        String column = SortSupport.resolveColumn(SORTS, query.getSort(), "${defaultSortColumn()}");
        SortSupport.applyOrder(wrapper, column, SortSupport.isAsc(query.getOrder(), SortSupport.NEWEST_FIRST));
        return wrapper;
    }
}
`);

// ------------------------------------------------------------------ 后端：应用层
files.set(`${javaRoot}/application/dto/${Cap}Dtos.java`, `package ${pkg}.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;

/** ${spec.name}的请求/响应模型。 */
public final class ${Cap}Dtos {

    private ${Cap}Dtos() {
    }

    @Data
    public static class SaveRequest {
${saveRequestFields()}
    }

    @Data
    public static class Response {
        private Long id;
${fields.map((f) => `        private ${responseJavaType(f)} ${f.name};`).join('\n')}
        private Instant createdAt;
        private Instant updatedAt;
    }
}
`);

files.set(`${javaRoot}/application/${Cap}AppService.java`, `package ${pkg}.application;

import com.adminstarter.common.api.PageQuery;
import com.adminstarter.common.api.PageResponse;
import com.adminstarter.common.api.PageResult;
import com.adminstarter.common.error.BizException;
import com.adminstarter.common.error.ErrorCode;
import com.adminstarter.common.util.TimeSupport;
import ${pkg}.application.dto.${Cap}Dtos;
import ${pkg}.domain.model.${Cap};
import ${pkg}.domain.query.${Cap}Query;
import ${pkg}.domain.repository.${Cap}Repository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ${Cap}AppService {

    private final ${Cap}Repository ${camel}Repository;

    public PageResponse<${Cap}Dtos.Response> page(PageQuery pageQuery, String keyword${filterParams()}) {
        int page = pageQuery.resolvePage();
        int pageSize = pageQuery.resolvePageSize();
        ${Cap}Query query = new ${Cap}Query();
        query.setKeyword(keyword);
${filterSets()}        query.setPage(page);
        query.setPageSize(pageSize);
        query.setSort(pageQuery.getSort());
        query.setOrder(pageQuery.getOrder());
        PageResult<${Cap}> result = ${camel}Repository.page(query);
        List<${Cap}Dtos.Response> list = result.getList().stream().map(this::toResponse).toList();
        return PageResponse.of(page, pageSize, list, result.getTotal());
    }

    public ${Cap}Dtos.Response get(Long id) {
        return toResponse(require(id));
    }

    @Transactional
    public ${Cap}Dtos.Response create(${Cap}Dtos.SaveRequest request) {
        ${Cap} ${camel} = new ${Cap}();
        apply(${camel}, request);
        ${camel}Repository.insert(${camel});
        return toResponse(${camel});
    }

    @Transactional
    public ${Cap}Dtos.Response update(Long id, ${Cap}Dtos.SaveRequest request) {
        ${Cap} ${camel} = require(id);
        apply(${camel}, request);
        ${camel}Repository.update(${camel});
        return toResponse(${camel});
    }

    @Transactional
    public void delete(Long id) {
        require(id);
        ${camel}Repository.deleteById(id);
    }

    private ${Cap} require(Long id) {
        ${Cap} ${camel} = ${camel}Repository.findById(id);
        if (${camel} == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "${spec.name}不存在");
        }
        return ${camel};
    }

    private void apply(${Cap} target, ${Cap}Dtos.SaveRequest request) {
${applyLines()}    }

    private ${Cap}Dtos.Response toResponse(${Cap} source) {
        ${Cap}Dtos.Response response = new ${Cap}Dtos.Response();
        response.setId(source.getId());
${responseLines()}        response.setCreatedAt(TimeSupport.toInstant(source.getCreatedAt()));
        response.setUpdatedAt(TimeSupport.toInstant(source.getUpdatedAt()));
        return response;
    }
}
`);

// ------------------------------------------------------------------ 后端：接口层
files.set(`${webRoot}/controller/${Cap}Controller.java`, `package com.adminstarter.web.controller;

import com.adminstarter.common.api.ApiResponse;
import com.adminstarter.common.api.PageQuery;
import com.adminstarter.common.api.PageResponse;
import ${pkg}.application.${Cap}AppService;
import ${pkg}.application.dto.${Cap}Dtos;
import com.adminstarter.web.log.OperLogRecord;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${apiPath}")
@RequiredArgsConstructor
public class ${Cap}Controller {

    private final ${Cap}AppService ${camel}AppService;

    @GetMapping
    @PreAuthorize("hasAuthority('${permission}:list')")
    public ApiResponse<PageResponse<${Cap}Dtos.Response>> page(PageQuery pageQuery,
            @RequestParam(required = false) String keyword${controllerFilterParams()}) {
        return ApiResponse.ok(${camel}AppService.page(pageQuery, keyword${controllerFilterArgs()}));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('${permission}:list')")
    public ApiResponse<${Cap}Dtos.Response> get(@PathVariable Long id) {
        return ApiResponse.ok(${camel}AppService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('${permission}:create')")
    @OperLogRecord(module = "${spec.logModule ?? spec.name}", action = "新增${spec.name}")
    public ApiResponse<${Cap}Dtos.Response> create(@Valid @RequestBody ${Cap}Dtos.SaveRequest request) {
        return ApiResponse.ok(${camel}AppService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('${permission}:update')")
    @OperLogRecord(module = "${spec.logModule ?? spec.name}", action = "修改${spec.name}")
    public ApiResponse<${Cap}Dtos.Response> update(@PathVariable Long id,
                                                  @Valid @RequestBody ${Cap}Dtos.SaveRequest request) {
        return ApiResponse.ok(${camel}AppService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('${permission}:delete')")
    @OperLogRecord(module = "${spec.logModule ?? spec.name}", action = "删除${spec.name}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        ${camel}AppService.delete(id);
        return ApiResponse.ok(null);
    }
}
`);

// ------------------------------------------------------------------ 迁移
const version = nextMigrationVersion();
files.set(`${migrationDir}/V${version}__${spec.table}.sql`, migrationSql(version));

// ------------------------------------------------------------------ 前端
files.set(`apps/pc/src/api/${spec.apiFile ?? camel}.ts`, `import { api } from './http';
import type { PageData, PageParams } from './types';

export interface ${Cap}Item {
  id: number;
${fields.map((f) => `  ${f.name}${tsOptional(f)}: ${tsType(f)};`).join('\n')}
  createdAt: string;
  updatedAt: string;
}

export interface ${Cap}Query extends PageParams {
  keyword?: string;
${fields.filter((f) => f.filter).map((f) => `  ${f.name}?: ${tsType(f)} | undefined;`).join('\n')}${fields.some((f) => f.filter) ? '\n' : ''}}

export interface ${Cap}SavePayload {
${fields.map((f) => `  ${f.name}${f.required ? '' : '?'}: ${tsType(f)};`).join('\n')}
}

export const ${camel}Api = {
  page(params: ${Cap}Query) {
    return api.get<PageData<${Cap}Item>>('${fePath}', params);
  },
  create(payload: ${Cap}SavePayload) {
    return api.post<${Cap}Item>('${fePath}', payload);
  },
  update(id: number, payload: ${Cap}SavePayload) {
    return api.put<${Cap}Item>(\`${fePath}/\${id}\`, payload);
  },
  remove(id: number) {
    return api.delete<void>(\`${fePath}/\${id}\`);
  },
};
`);

files.set(`apps/pc/src/views/${spec.viewDir}/index.vue`, viewTemplate());

// ------------------------------------------------------------------ 契约片段（人工合并）
files.set(`docs/api/snippets/${camel}.yaml`, contractSnippet());

// ------------------------------------------------------------------ 执行
const planned = [...files.keys()].sort();
if (dryRun) {
  console.log(`[dry] 将写入 ${planned.length} 个文件：`);
  for (const file of planned) {
    console.log(`  ${file}`);
  }
  process.exit(0);
}

let written = 0;
let skipped = 0;
const conflicts = [];
for (const [relative, content] of files) {
  const target = path.join(ROOT, relative);
  const exists = fs.existsSync(target);
  if (exists) {
    const current = fs.readFileSync(target, 'utf8');
    if (current === content) {
      skipped += 1;
      continue;
    }
    if (checkOnly) {
      conflicts.push(relative);
      continue;
    }
    if (!force) {
      conflicts.push(relative);
      continue;
    }
  }
  fs.mkdirSync(path.dirname(target), { recursive: true });
  fs.writeFileSync(target, content, 'utf8');
  written += 1;
}

console.log(`模块 ${spec.name}（${camel}）：写入 ${written}，未变 ${skipped}，跳过 ${conflicts.length}`);
if (conflicts.length) {
  console.log(`\n以下文件已存在且内容与模板不同（不会被静默覆盖）：`);
  for (const file of conflicts) {
    console.log(`  ${file}`);
  }
  if (checkOnly) {
    console.log(`\n--check 失败：模板输出与仓库现有文件不一致（可能有人手改过，或模板已腐化）。`);
    process.exit(1);
  }
  console.log(`\n如需覆盖请加 --force（会丢掉现有改动）。`);
}

console.log(`
后续手工步骤（生成器不替你做）：
  1. 契约：把 docs/api/snippets/${camel}.yaml 合并进 docs/api/openapi.yaml，跑 scripts/export-openapi.ps1
  2. 前端路由：菜单已在迁移里写入（父目录 ${spec.parentMenuPath}），刷新后由菜单驱动，无需改 router
  3. 断言/文档：把新模块的"踩坑点"补进 docs/baseline/assertions.md；在 docs/audit/impl-notes.md 记一条
  4. 测试：补 ListOrdering/权限拒绝/契约三条用例（模板暂不生成测试，避免生成"永远绿"的假测试）
`);

// ==================================================================== 工具函数
function validateSpec(value) {
  const required = ['key', 'name', 'table', 'path', 'viewDir', 'parentMenuPath', 'permissionPrefix', 'fields'];
  const missing = required.filter((key) => value[key] === undefined || value[key] === '');
  if (missing.length) {
    console.error(`模块描述缺少必填项：${missing.join(', ')}`);
    process.exit(2);
  }
  if (!Array.isArray(value.fields) || value.fields.length === 0) {
    console.error('fields 不能为空');
    process.exit(2);
  }
  const bad = value.fields.filter((field) => !field.name || !field.label || !field.type);
  if (bad.length) {
    console.error('每个字段必须有 name/label/type');
    process.exit(2);
  }
}

function capitalize(text) {
  return text.charAt(0).toUpperCase() + text.slice(1);
}

function javaType(field) {
  switch (field.type) {
    case 'boolean':
      return 'Boolean';
    case 'integer':
      return 'Integer';
    case 'long':
      return 'Long';
    default:
      return 'String';
  }
}

function responseJavaType(field) {
  return javaType(field);
}

function tsType(field) {
  switch (field.type) {
    case 'boolean':
      return 'boolean';
    case 'integer':
    case 'long':
      return 'number';
    default:
      return 'string';
  }
}

function tsOptional(field) {
  return field.type === 'string' || field.optional ? '?' : '?';
}

function columnOf(field) {
  return field.name.replace(/[A-Z]/g, (letter) => `_${letter.toLowerCase()}`);
}

function sortEntries() {
  const sortable = fields.filter((field) => field.sortable);
  const entries = [
    '            "createdAt", "created_at"',
    ...sortable.map((field) => `            "${field.name}", "${columnOf(field)}"`),
    '            "id", "id"',
  ];
  return entries.join(',\n');
}

function defaultSortColumn() {
  return 'created_at';
}

function keywordWrapper() {
  const keywordFields = fields.filter((field) => field.keyword && field.type === 'string');
  if (!keywordFields.length) {
    return '';
  }
  const conditions = keywordFields
    .map((field) => `like("${columnOf(field)}", query.getKeyword())`)
    .join('.or().');
  return `        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            wrapper.and(w -> w.${conditions});
        }
`;
}

function filterWrappers() {
  return fields
    .filter((field) => field.filter)
    .map((field) => {
      const getter = `get${capitalize(field.name)}`;
      return `        if (query.${getter}() != null) {
            wrapper.eq("${columnOf(field)}", query.${getter}());
        }
`;
    })
    .join('');
}

function filterParams() {
  return fields
    .filter((field) => field.filter)
    .map((field) => `, ${javaType(field)} ${field.name}`)
    .join('');
}

function controllerFilterParams() {
  return fields
    .filter((field) => field.filter)
    .map((field) => `
            @RequestParam(required = false) ${javaType(field)} ${field.name}`)
    .join('');
}

function filterSets() {
  return fields
    .filter((field) => field.filter)
    .map((field) => `        query.set${capitalize(field.name)}(${field.name});\n`)
    .join('');
}

function controllerFilterArgs() {
  return fields
    .filter((field) => field.filter)
    .map((field) => `, ${field.name}`)
    .join('');
}

function saveRequestFields() {
  return fields
    .map((field) => {
      const annotations = [];
      if (field.type === 'string') {
        if (field.required) {
          annotations.push('        @NotBlank(message = "' + field.label + '不能为空")');
        }
        annotations.push('        @Size(max = ' + (field.length ?? 255) + ', message = "' + field.label + '过长")');
      }
      return [...annotations, `        private ${javaType(field)} ${field.name};`].join('\n');
    })
    .join('\n\n');
}

function applyLines() {
  return fields
    .map((field) => {
      if (field.type === 'string' && field.trim !== false) {
        return `        target.set${capitalize(field.name)}(request.get${capitalize(field.name)}() == null ? null : request.get${capitalize(field.name)}().trim());\n`;
      }
      return `        target.set${capitalize(field.name)}(request.get${capitalize(field.name)}());\n`;
    })
    .join('');
}

function responseLines() {
  return fields
    .map((field) => `        response.set${capitalize(field.name)}(source.get${capitalize(field.name)}());\n`)
    .join('');
}

function nextMigrationVersion() {
  const dir = path.join(ROOT, migrationDir);
  const names = fs.readdirSync(dir);
  // 幂等：同一张表已经生成过迁移就复用它的版本号。
  // 否则重复执行会产出 V20/V21/V22 三份同表迁移，Flyway 直接 "Found more than one migration with version N"
  // 启动失败（断言 #110）。
  const existing = names.find((name) => name.endsWith(`__${spec.table}.sql`));
  if (existing) {
    const matched = /^V(\d+)__/.exec(existing);
    if (matched) {
      return Number(matched[1]);
    }
  }
  const versions = names
    .map((name) => /^V(\d+)__/.exec(name))
    .filter(Boolean)
    .map((match) => Number(match[1]));
  return Math.max(0, ...versions) + 1;
}

function migrationSql(versionNo) {
  const columns = fields
    .map((field) => {
      const type = field.type === 'boolean'
        ? 'TINYINT      NOT NULL DEFAULT 1'
        : field.type === 'integer' || field.type === 'long'
          ? 'INT          NOT NULL DEFAULT 0'
          : `VARCHAR(${field.length ?? 255}) ${field.required ? 'NOT NULL' : 'NULL'}`;
      return `    ${columnOf(field).padEnd(16)} ${type}`.padEnd(80) + ` COMMENT '${field.label}'`;
    })
    .join(',\n');
  const permissionRows = ['list', 'create', 'update', 'delete']
    .map((action, index) => {
      const actionName = { list: '查询', create: '新增', update: '修改', delete: '删除' }[action];
      // 按钮一定挂在**页面菜单**下（断言 #106：挂到目录上会变成孤儿节点）
      return `    SELECT c.id, '${actionName}${spec.name}', 'F', NULL, NULL, '${spec.permissionPrefix}:${action}', NULL, ${index + 1}, 1, 1,
           UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0
    FROM (SELECT id FROM sys_menu WHERE path = '${spec.path}' AND menu_type = 'C' AND deleted = 0 ORDER BY id LIMIT 1) c
    WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission_code = '${spec.permissionPrefix}:${action}' AND deleted = 0)`;
    })
    .join('\n    UNION ALL\n');

  // parentMenuPath 为空/null → 页面菜单挂顶层（parent_id=0），与「示例业务」同形态
  const topLevel = !spec.parentMenuPath;
  const pageMenuInsert = topLevel
    ? `INSERT INTO sys_menu (parent_id, name, menu_type, path, component, permission_code, icon, sort,
                      is_visible, is_enabled, created_at, updated_at, deleted)
SELECT 0, '${spec.name}', 'C', '${spec.path}', '${spec.viewDir}/index', '${spec.permissionPrefix}:list',
       '${spec.icon ?? 'Document'}', ${spec.menuSort ?? 10}, 1, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = '${spec.path}' AND menu_type = 'C' AND deleted = 0);`
    : `INSERT INTO sys_menu (parent_id, name, menu_type, path, component, permission_code, icon, sort,
                      is_visible, is_enabled, created_at, updated_at, deleted)
SELECT p.id, '${spec.name}', 'C', '${spec.path}', '${spec.viewDir}/index', '${spec.permissionPrefix}:list',
       '${spec.icon ?? 'Document'}', ${spec.menuSort ?? 10}, 1, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP(), 0
FROM (SELECT id FROM sys_menu WHERE path = '${spec.parentMenuPath}' AND menu_type = 'M' AND deleted = 0 ORDER BY id LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = '${spec.path}' AND menu_type = 'C' AND deleted = 0);`;

  return `-- V${versionNo}：${spec.name}模块（由 tools/gen-module 生成，之后按需手改）
-- 三件事：建表 + 菜单/权限码 + 授权。全部按业务键（path / permission_code / 角色 code）匹配，不硬编码 id。

-- 1) 建表
CREATE TABLE IF NOT EXISTS ${spec.table} (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
${columns},
    created_at       DATETIME     NOT NULL,
    updated_at       DATETIME     NOT NULL,
    deleted          TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_${spec.table}_created (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '${spec.name}';

-- 2) 菜单：页面菜单（parentMenuPath 为空则挂顶层）。NOT EXISTS 保证可重复执行
${pageMenuInsert}

-- 3) 按钮权限码：挂在刚建的页面菜单下（不要挂到目录上，见断言 #106）
INSERT INTO sys_menu (parent_id, name, menu_type, path, component, permission_code, icon, sort,
                      is_visible, is_enabled, created_at, updated_at, deleted)
${permissionRows};

-- 4) 授权：SUPER_ADMIN 全量；DEPT_MANAGER 只读（按需调整）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON (m.path = '${spec.path}' OR m.permission_code LIKE '${spec.permissionPrefix}:%') AND m.deleted = 0
WHERE r.code = 'SUPER_ADMIN' AND r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.permission_code = '${spec.permissionPrefix}:list' AND m.deleted = 0
WHERE r.code = 'DEPT_MANAGER' AND r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);
`;
}

function viewTemplate() {
  const items = fields.filter((field) => !field.hidden);
  const keywordFields = fields.filter((field) => field.keyword && field.type === 'string');
  const columns = [
    ...items.map((field) => {
      const flags = [];
      if (field.name === (keywordFields[0]?.name ?? fields[0].name)) {
        flags.push('alwaysVisible: true');
      }
      if (field.sortable) {
        flags.push(`sortKey: '${field.name}'`);
      }
      return `  { key: '${field.name}', label: '${field.label}'${flags.length ? ', ' + flags.join(', ') : ''} },`;
    }),
    "  { key: 'createdAt', label: '创建时间', sortKey: 'createdAt' },",
    "  { key: 'ops', label: '操作', alwaysVisible: true, exportable: false },",
  ].join('\n');

  // 无显式宽度的列会被 el-table 平均分配，整张表看起来"散"、表头像是居中的；
  // 首列给 min-width 让名称类字段可伸展，其余固定宽度（与既有页面口径一致）
  const widthOf = (field) => (field === items[0] ? 'min-width="200"' : 'width="140"');

  const tableColumns = items
    .map((field) => {
      if (field.type === 'boolean') {
        return `        <el-table-column v-if="table.isVisible('${field.name}')" prop="${field.name}" label="${field.label}" width="90">
          <template #default="{ row }">
            <el-tag :type="row.${field.name} ? 'success' : 'info'" size="small" disable-transitions>
              {{ row.${field.name} ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>`;
      }
      const sortable = field.sortable ? `\n          :sortable="table.sortableOf('${field.name}')"` : '';
      return `        <el-table-column
          v-if="table.isVisible('${field.name}')"
          prop="${field.name}"
          label="${field.label}"
          ${widthOf(field)}
          show-overflow-tooltip${sortable}
        />`;
    })
    .join('\n');

  const formItems = fields
    .map((field) => {
      if (field.type === 'boolean') {
        return `        <el-form-item label="${field.label}">
          <el-switch v-model="form.${field.name}" active-text="启用" inactive-text="停用" />
        </el-form-item>`;
      }
      const rule = field.required ? `\n            :rules="[{ required: true, message: '请填写${field.label}', trigger: 'blur' }]"` : '';
      return `        <el-form-item label="${field.label}" prop="${field.name}"${rule}>
          <el-input v-model="form.${field.name}" maxlength="${field.length ?? 255}" />
        </el-form-item>`;
    })
    .join('\n');

  const formDefaults = fields
    .map((field) => `  ${field.name}: ${field.type === 'boolean' ? 'true' : field.type === 'integer' || field.type === 'long' ? '0' : "''"},`)
    .join('\n');

  return `<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, type FormRules } from 'element-plus';
import { ${camel}Api, type ${Cap}Item, type ${Cap}SavePayload } from '@/api/${spec.apiFile ?? camel}';
import { ApiError, saveBlob } from '@/api/http';
import { exportModule } from '@/api/export';
import { confirmDelete, usePagedList } from '@/composables/use-paged-list';
import { useTableColumns, type ColumnDefinition } from '@/composables/use-table-columns';
import ColumnSettings from '@/components/ColumnSettings.vue';
import TableSkeleton from '@/components/TableSkeleton.vue';
import { formatDateTime } from '@/utils/datetime';

interface Filters {
  keyword: string;
}

const { page, filters, rows, loading, loadError, skeletonVisible, hasFilter, sort, order,
  load, search, resetFilters, onPageSizeChange, onPageChange, onSortChange } =
  usePagedList<${Cap}Item, Filters>({
    initialFilters: { keyword: '' },
    fetcher: (params) => ${camel}Api.page({
      page: params.page,
      pageSize: params.pageSize,
      keyword: params.keyword || undefined,
      // 排序参数必须显式转发，否则排序不生效（断言 #101 第 ② 条）
      sort: params.sort,
      order: params.order,
    }),
  });

const COLUMNS: ColumnDefinition[] = [
${columns}
];

const table = useTableColumns({
  storageKey: '${spec.viewDir.replace(/\//g, '-')}',
  columns: COLUMNS,
  onSort: (sort, order) => onSortChange(sort, order),
});

const drawerVisible = ref(false);
const saving = ref(false);
const editingId = ref<number | null>(null);
const exporting = ref(false);
const formRef = ref<{ validate: () => Promise<void> }>();
const form = reactive<${Cap}SavePayload>({
${formDefaults}
});
const formRules: FormRules = {};

function openCreate(): void {
  editingId.value = null;
  Object.assign(form, {
${formDefaults}
  });
  drawerVisible.value = true;
}

function openEdit(row: ${Cap}Item): void {
  editingId.value = row.id;
  Object.assign(form, {
${fields.map((field) => `    ${field.name}: row.${field.name},`).join('\n')}
  });
  drawerVisible.value = true;
}

async function save(): Promise<void> {
  await formRef.value?.validate();
  saving.value = true;
  try {
    if (editingId.value === null) {
      await ${camel}Api.create({ ...form });
    } else {
      await ${camel}Api.update(editingId.value, { ...form });
    }
    ElMessage.success('保存成功');
    drawerVisible.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}

/** 导出：走通用导出中心（一个入口服务所有已注册模块），列白名单取「列设置」的勾选结果 */
async function exportList(): Promise<void> {
  if (exporting.value) {
    return;
  }
  if (table.exportFields.value.length === 0) {
    ElMessage.warning('至少保留一列可导出字段（在「列设置」里勾选）');
    return;
  }
  exporting.value = true;
  try {
    const result = await exportModule('${spec.key}', {
      keyword: filters.keyword || undefined,
      sort: sort.value || undefined,
      order: order.value || undefined,
      fields: table.exportFields.value.join(','),
    });
    if (result.kind === 'file') {
      saveBlob(result.blob, result.fileName ?? '${spec.name}.xlsx');
      ElMessage.success('导出完成');
    } else {
      ElMessage.success(\`数据量较大（\${result.data.totalRows} 行），已转入后台导出，可在「导出任务」中查看\`);
    }
  } catch (error) {
    if (error instanceof ApiError) {
      ElMessage.error(error.message);
    }
  } finally {
    exporting.value = false;
  }
}

async function remove(row: ${Cap}Item): Promise<void> {
  const confirmed = await confirmDelete({
    title: '删除${spec.name}',
    message: '确认删除该${spec.name}？此操作不可恢复。',
  });
  if (!confirmed) {
    return;
  }
  await ${camel}Api.remove(row.id);
  ElMessage.success('已删除');
  await load();
}

onMounted(() => {
  void load();
});
</script>

<template>
  <div class="page">
    <div class="page-toolbar">
      <div class="toolbar-filters">
        <el-input v-model="filters.keyword" class="filter-keyword" placeholder="搜索${keywordFields.map((f) => f.label).join(' / ') || '关键词'}" clearable @change="search" />
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>
      <div class="toolbar-actions">
        <ColumnSettings
          :columns="COLUMNS"
          :is-visible="table.isVisible"
          :hidden-count="table.hiddenCount.value"
          @toggle="table.toggleColumn"
          @show-all="table.showAllColumns"
          @invert="table.invertColumns"
          @reset="table.resetColumns"
        />
        <el-button v-permission="'${permission}:export'" :loading="exporting" @click="exportList">导出</el-button>
        <el-button v-permission="'${permission}:create'" type="primary" @click="openCreate">新增${spec.name}</el-button>
      </div>
    </div>

    <div class="page-card">
      <el-alert v-if="loadError" class="table-error" type="error" :closable="false" show-icon>
        <template #title>列表加载失败：{{ loadError }}</template>
        <el-button class="error-retry" size="small" @click="load">重试</el-button>
      </el-alert>

      <el-table :data="skeletonVisible ? [] : rows" row-key="id" style="width: 100%" @sort-change="table.onSortChange">
        <template #empty>
          <TableSkeleton v-if="skeletonVisible" :rows="6" />
          <div v-else-if="!loading" class="table-empty">
            <p class="empty-title">{{ hasFilter ? '没有匹配的记录' : '暂无数据' }}</p>
            <el-button v-if="hasFilter" size="small" @click="resetFilters">清空筛选</el-button>
          </div>
        </template>
${tableColumns}
        <el-table-column v-if="table.isVisible('createdAt')" prop="createdAt" label="创建时间" width="170" :sortable="table.sortableOf('createdAt')">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column v-if="table.isVisible('ops')" prop="ops" label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button v-permission="'${permission}:update'" link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button v-permission="'${permission}:delete'" link type="danger" @click="remove(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-pagination">
        <el-pagination
          v-model:current-page="page.page"
          v-model:page-size="page.pageSize"
          :total="page.total"
          layout="total, sizes, prev, pager, next"
          @size-change="onPageSizeChange"
          @current-change="onPageChange"
        />
      </div>
    </div>

    <el-drawer v-model="drawerVisible" :title="editingId === null ? '新增${spec.name}' : '编辑${spec.name}'" size="420px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="96px">
${formItems}
      </el-form>
      <template #footer>
        <el-button @click="drawerVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
/* 列表页通用类（page-toolbar / toolbar-filters / row-actions / table-empty / table-pagination）
   在 src/styles/index.css 里是唯一定义来源，页面里只补宽度这类页面级规则。
   漏掉 .filter-keyword 的宽度 → el-input 撑满一行，查询/重置会被挤到第二行。 */
.filter-keyword {
  width: 240px;
}
</style>
`;
}

function contractSnippet() {
  const props = fields
    .map((field) => {
      const type = field.type === 'boolean' ? 'boolean' : field.type === 'integer' || field.type === 'long' ? 'integer' : 'string';
      return `                ${field.name}:\n                  type: ${type}`;
    })
    .join('\n');
  return `# ${spec.name}模块契约片段（生成物，需人工合并进 docs/api/openapi.yaml 后再导出 as-is）
# 合并后记得：契约版本 +0.1、变更记录一条、跑 scripts/export-openapi.ps1
paths:
  ${spec.path}:
    get:
      x-status: IMPLEMENTED
      tags: [${camel}]
      summary: ${spec.name}分页
      operationId: ${camel}Page
      parameters:
        - name: keyword
          in: query
          required: false
          schema:
            type: string
        - name: sort
          in: query
          required: false
          schema:
            type: string
        - name: order
          in: query
          required: false
          schema:
            type: string
            enum: [asc, desc]
      responses:
        '200':
          description: 成功
    post:
      x-status: IMPLEMENTED
      tags: [${camel}]
      summary: 新增${spec.name}
      operationId: ${camel}Create
      responses:
        '200':
          description: 成功

components:
  schemas:
    ${Cap}Response:
      allOf:
        - $ref: '#/components/schemas/Envelope'
        - type: object
          additionalProperties: true
          properties:
            data:
              type: object
              properties:
${props}
`;
}
