<#
  从 MySQL information_schema 生成 docs/design/data-model.md 的结构骨架。

  用法（在项目根执行）：
    pwsh scripts/gen-data-model.ps1
    $env:APP_DB_PASSWORD = '<数据库口令>'; pwsh scripts/gen-data-model.ps1 -Port 3307 -User admin_app

  设计：
  - 口令**不写在脚本里**：优先取 -Password，缺省读环境变量 APP_DB_PASSWORD，两者都没有则直接报错退出。
  - 只自动生成「表总览」与每张表的「字段表」「索引表」，块用 AUTO 标记包裹。
  - 块外的「用途 / 特殊列 / 表间关系 / 审查发现」为人工维护，脚本不覆盖。
  - 字段表的「含义」列在重跑时按字段名从旧文件回填，不会被脚本清空。
  - 库新增表但文件里没有对应章节时，脚本在文末追加骨架章节。
#>

param(
    [string]$MysqlExe = 'E:\dev\mysql-8.0\bin\mysql.exe',
    [string]$DbHost = '127.0.0.1',
    [int]$Port = 3307,
    [string]$User = 'admin_app',
    [string]$Password = '',
    [string]$Database = 'admin_starter',
    [string]$OutFile = 'docs/design/data-model.md'
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($Password)) {
    $Password = $env:APP_DB_PASSWORD
}
if ([string]::IsNullOrWhiteSpace($Password)) {
    throw '缺少数据库口令：请传 -Password，或先设置环境变量 APP_DB_PASSWORD（不要把口令写回本脚本）。'
}
$utf8 = [System.Text.UTF8Encoding]::new($false)
$today = Get-Date -Format 'yyyy-MM-dd'

function Invoke-Query {
    param([string]$sql)
    $env:MYSQL_PWD = $Password
    $raw = & $MysqlExe -h $DbHost -P $Port -u $User -D $Database --default-character-set=utf8mb4 --batch --raw -N -e $sql 2>&1
    if ($LASTEXITCODE -ne 0) { throw "MySQL 查询失败：$raw" }
    return @($raw | Where-Object { $_ -ne '' })
}

if (-not (Test-Path -LiteralPath $MysqlExe)) {
    throw "找不到 mysql 客户端：$MysqlExe，请用 -MysqlExe 指定"
}

$db = $Database
$tableSql = @"
SELECT t.table_name, COALESCE(t.table_comment,'')
FROM information_schema.tables t
WHERE t.table_schema='$db' AND t.table_type='BASE TABLE'
  AND t.table_name <> 'flyway_schema_history'
ORDER BY t.table_name;
"@
$columnSql = @"
SELECT c.table_name, c.column_name, c.column_type,
       CASE WHEN c.is_nullable='YES' THEN '是' ELSE '否' END,
       COALESCE(c.column_default,''), c.extra, COALESCE(c.column_comment,'')
FROM information_schema.columns c
WHERE c.table_schema='$db' AND c.table_name <> 'flyway_schema_history'
ORDER BY c.table_name, c.ordinal_position;
"@
$indexSql = @"
SELECT s.table_name, s.index_name, s.non_unique, s.seq_in_index, s.column_name
FROM information_schema.statistics s
WHERE s.table_schema='$db' AND s.table_name <> 'flyway_schema_history'
ORDER BY s.table_name, s.index_name, s.seq_in_index;
"@

$tables = @{}
foreach ($line in (Invoke-Query $tableSql)) {
    $p = $line -split "`t"
    $tables[$p[0]] = $p[1]
}

$columns = @{}
foreach ($line in (Invoke-Query $columnSql)) {
    $p = $line -split "`t"
    $name = $p[0]
    if (-not $columns.ContainsKey($name)) { $columns[$name] = @() }
    $columns[$name] += [pscustomobject]@{
        Name    = $p[1]
        Type    = $p[2]
        Nullable= $p[3]
        Default = $p[4]
        Extra   = $p[5]
        Comment = $p[6]
    }
}

$indexes = @{}
foreach ($line in (Invoke-Query $indexSql)) {
    $p = $line -split "`t"
    $name = $p[0]
    if (-not $indexes.ContainsKey($name)) { $indexes[$name] = @{} }
    if (-not $indexes[$name].ContainsKey($p[1])) {
        $indexes[$name][$p[1]] = [pscustomobject]@{
            Name     = $p[1]
            NonUnique= [int]$p[2]
            Columns  = @()
        }
    }
    $indexes[$name][$p[1]].Columns += $p[4]
}

function Get-DefaultText {
    param($col)
    if ($col.Extra -match 'auto_increment') { return '自增' }
    if ([string]::IsNullOrEmpty($col.Default)) { return '—' }
    return $col.Default
}

# 按列名自动识别会误标“豁免表”，这里按代码事实覆盖
$specialOverrides = @{
    'sys_user'       = '数据权限列：`dept_id`、`create_by`（本表**豁免**数据权限拦截；`deptId` 仅作普通筛选参数）；软删除：`deleted`；审计：`create_by`、`created_at`、`updated_at`'
    'sys_post'       = '业务归属列：`dept_id`（岗位挂靠部门，**不是**数据权限受控列）；软删除：`deleted`；审计：`created_at`、`updated_at`'
    'sys_oper_log'   = '数据权限：`dept_id`、`create_by`（**受控表**，实际按 `create_by` 过滤）；审计：`create_by`、`created_at`'
    'sys_file'       = '数据权限：`dept_id`、`create_by`（**受控表**）；审计：`create_by`、`created_at`'
    'biz_demo_task'  = '数据权限：`dept_id`（组织范围）、`create_by`（SELF/CUSTOM_USER）、`owner_id`（OWNER）；软删除：`deleted`；审计：`create_by`、`created_at`、`updated_at`'
}

function Get-SpecialColumns {
    param([string]$table)
    if ($specialOverrides.ContainsKey($table)) { return $specialOverrides[$table] }
    $cols = @($columns[$table] | ForEach-Object { $_.Name })
    $dataPerm = @('dept_id', 'create_by', 'owner_id') | Where-Object { $cols -contains $_ }
    $softDel  = @('deleted') | Where-Object { $cols -contains $_ }
    $audit    = @('create_by', 'created_at', 'updated_at', 'update_by') | Where-Object { $cols -contains $_ }
    $parts = @()
    if ($dataPerm.Count -gt 0) { $parts += '数据权限：' + (($dataPerm | ForEach-Object { '`' + $_ + '`' }) -join '、') }
    if ($softDel.Count -gt 0)  { $parts += '软删除：' + (($softDel | ForEach-Object { '`' + $_ + '`' }) -join '、') }
    if ($audit.Count -gt 0)    { $parts += '审计：' + (($audit | ForEach-Object { '`' + $_ + '`' }) -join '、') }
    if ($parts.Count -eq 0) { return '—' }
    return ($parts -join '；')
}

function New-TableListBlock {
    $lines = @(
        '<!-- AUTO:TABLE-LIST:START -->',
        '',
        '| # | 表 | 表注释 | 字段数 | 索引数 | 特殊列 |',
        '|---|---|---|---|---|---|'
    )
    $i = 0
    foreach ($t in ($tables.Keys | Sort-Object)) {
        $i++
        $colCount = @($columns[$t]).Count
        $idxCount = @($indexes[$t].Keys).Count
        $lines += "| $i | ``$t`` | $($tables[$t]) | $colCount | $idxCount | $(Get-SpecialColumns $t) |"
    }
    $lines += @('', '<!-- AUTO:TABLE-LIST:END -->')
    return ($lines -join "`r`n")
}

function Get-PreservedMeanings {
    param([string]$content, [string]$table)
    $map = @{}
    $start = "<!-- AUTO:COLS:${table}:START -->"
    $end   = "<!-- AUTO:COLS:${table}:END -->"
    $si = $content.IndexOf($start)
    $ei = $content.IndexOf($end)
    if ($si -lt 0 -or $ei -lt 0) { return $map }
    $block = $content.Substring($si, $ei - $si)
    foreach ($line in ($block -split "`r?`n")) {
        if ($line -match '^\|\s*`([^`]+)`\s*\|') {
            $cells = $line.Trim().Trim('|').Split('|')
            $last = $cells[$cells.Count - 1].Trim()
            if ($last -ne '含义') { $map[$Matches[1]] = $last }
        }
    }
    return $map
}

function New-ColumnBlock {
    param([string]$table, [hashtable]$preserved)
    $lines = @(
        "<!-- AUTO:COLS:${table}:START -->",
        '',
        '| 字段 | 类型 | 可空 | 默认 | 含义 |',
        '|---|---|---|---|---|'
    )
    foreach ($c in @($columns[$table])) {
        $meaning = ''
        if ($preserved.ContainsKey($c.Name)) { $meaning = $preserved[$c.Name] }
        elseif (-not [string]::IsNullOrEmpty($c.Comment)) { $meaning = $c.Comment }
        else { $meaning = '—（语义待补）' }
        $lines += "| ``$($c.Name)`` | $($c.Type) | $($c.Nullable) | $(Get-DefaultText $c) | $meaning |"
    }
    $lines += @('', "<!-- AUTO:COLS:${table}:END -->")
    return ($lines -join "`r`n")
}

function New-IndexBlock {
    param([string]$table)
    $lines = @(
        "<!-- AUTO:IDX:${table}:START -->",
        '',
        '| 索引名 | 类型 | 列（按顺序） | 约束说明 |',
        '|---|---|---|---|'
    )
    foreach ($idx in @($indexes[$table].Values | Sort-Object Name)) {
        $colsText = ($idx.Columns | ForEach-Object { '`' + $_ + '`' }) -join '、'
        if ($idx.Name -eq 'PRIMARY') {
            $type = '主键'
            $note = "主键约束落在 $colsText"
        } elseif ($idx.NonUnique -eq 0) {
            $type = '唯一'
            $note = "唯一约束落在 $colsText（$($idx.Columns.Count) 列）"
        } else {
            $type = '普通'
            $note = "非唯一，用于加速查询"
        }
        $lines += "| ``$($idx.Name)`` | $type | $colsText | $note |"
    }
    $lines += @('', "<!-- AUTO:IDX:${table}:END -->")
    return ($lines -join "`r`n")
}

function New-TableSection {
    param([string]$table, [hashtable]$preserved)
    $comment = $tables[$table]
    $special = Get-SpecialColumns $table
    $colBlock = New-ColumnBlock $table $preserved
    $idxBlock = New-IndexBlock $table
    return @"
### ``$table``（$comment）

**用途**：—（语义待补）

**特殊列**：$special

**表间关系**：—（语义待补）

**字段（结构自动生成；含义人工维护）**

$colBlock

**索引与唯一键**

$idxBlock
"@
}

$existing = if (Test-Path -LiteralPath $OutFile) { [System.IO.File]::ReadAllText((Resolve-Path -LiteralPath $OutFile), $utf8) } else { '' }

if ([string]::IsNullOrEmpty($existing)) {
    $sb = [System.Text.StringBuilder]::new()
    [void]$sb.AppendLine('# 数据模型（as-built v1.0）')
    [void]$sb.AppendLine()
    [void]$sb.AppendLine('| 项目 | 内容 |')
    [void]$sb.AppendLine('|---|---|')
    [void]$sb.AppendLine("| 版本 | v1.0（竣工现状，$today） |")
    [void]$sb.AppendLine('| 状态 | 结构与 `admin_starter` 库一致；语义人工核对 |')
    [void]$sb.AppendLine('| 读者 | AI 编码助手、后端、前端 |')
    [void]$sb.AppendLine('| 定位 | **as-is**：系统现在“有哪些表、哪些字段”，不是规范（to-be） |')
    [void]$sb.AppendLine('| 依赖 | `AGENTS.md` §6 第 0 步（读基线）与第 8 步（回填基线） |')
    [void]$sb.AppendLine()
    [void]$sb.AppendLine('<!-- AUTO:HEADER:START -->')
    [void]$sb.AppendLine("> 结构由脚本生成于 $today（``scripts/gen-data-model.ps1``，读 ``information_schema``）；语义人工维护。")
    [void]$sb.AppendLine('> **改代码或迁移（新增/修改表、列、索引）后必须重跑脚本**；代码、库、权限码变更时本文件必须在第 8 步同步。')
    [void]$sb.AppendLine('<!-- AUTO:HEADER:END -->')
    [void]$sb.AppendLine()
    [void]$sb.AppendLine('## 1. 表总览')
    [void]$sb.AppendLine()
    [void]$sb.AppendLine((New-TableListBlock))
    [void]$sb.AppendLine()
    [void]$sb.AppendLine('## 2. 表结构（结构自动生成 · 语义人工维护）')
    [void]$sb.AppendLine()
    foreach ($t in ($tables.Keys | Sort-Object)) {
        [void]$sb.AppendLine((New-TableSection $t @{}))
        [void]$sb.AppendLine()
    }
    [void]$sb.AppendLine('## 3. 审查发现（建了没用 / 索引冗余 / 缺约束）')
    [void]$sb.AppendLine()
    [void]$sb.AppendLine('—（人工维护）')
    [System.IO.File]::WriteAllText((Join-Path (Get-Location) $OutFile), $sb.ToString(), $utf8)
    Write-Host "已生成 $OutFile（$($tables.Count) 张表）"
    exit 0
}

# 已有文件：只替换 AUTO 块，其余人工内容保留
$content = $existing

$headerPattern = '<!-- AUTO:HEADER:START -->[\s\S]*?<!-- AUTO:HEADER:END -->'
$newHeader = @"
<!-- AUTO:HEADER:START -->
> 结构由脚本生成于 $today（``scripts/gen-data-model.ps1``，读 ``information_schema``）；语义人工维护。
> **改代码或迁移（新增/修改表、列、索引）后必须重跑脚本**；代码、库、权限码变更时本文件必须在第 8 步同步。
<!-- AUTO:HEADER:END -->
"@
$content = [regex]::Replace($content, $headerPattern, $newHeader)

$tableListPattern = '<!-- AUTO:TABLE-LIST:START -->[\s\S]*?<!-- AUTO:TABLE-LIST:END -->'
if ($content -match $tableListPattern) {
    $content = [regex]::Replace($content, $tableListPattern, (New-TableListBlock))
} else {
    $content += "`r`n`r`n## 1. 表总览`r`n`r`n" + (New-TableListBlock) + "`r`n"
}

$appended = @()
foreach ($t in ($tables.Keys | Sort-Object)) {
    $preserved = Get-PreservedMeanings $content $t
    if ($content.Contains("<!-- AUTO:COLS:${t}:START -->")) {
        $colPattern = "<!-- AUTO:COLS:${t}:START -->[\s\S]*?<!-- AUTO:COLS:${t}:END -->"
        $content = [regex]::Replace($content, $colPattern, (New-ColumnBlock $t $preserved))
        $idxPattern = "<!-- AUTO:IDX:${t}:START -->[\s\S]*?<!-- AUTO:IDX:${t}:END -->"
        if ($content -match $idxPattern) {
            $content = [regex]::Replace($content, $idxPattern, (New-IndexBlock $t))
        }
    } else {
        $appended += $t
        $content += "`r`n" + (New-TableSection $t @{}) + "`r`n"
    }
}

[System.IO.File]::WriteAllText((Join-Path (Get-Location) $OutFile), $content, $utf8)
Write-Host "已更新 $OutFile（$($tables.Count) 张表）"
if ($appended.Count -gt 0) { Write-Host ("追加新表章节：" + ($appended -join ', ')) }
