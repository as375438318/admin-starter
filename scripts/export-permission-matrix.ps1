<#
  从数据库导出权限矩阵：docs/design/permission-matrix.md

  用法（在项目根执行）：
    pwsh scripts/export-permission-matrix.ps1
    $env:APP_DB_PASSWORD = '<数据库口令>'; pwsh scripts/export-permission-matrix.ps1 -Port 3307 -User admin_app

  说明：
  - 口令**不写在脚本里**：优先取 -Password，缺省读环境变量 APP_DB_PASSWORD，两者都没有则直接报错退出。
  - 权限码来源 sys_menu.permission_code；角色授权来源 sys_role_menu；角色数据范围来源 sys_role。
  - 除「人工备注」块外，全文由脚本生成；库变了（菜单/角色/授权/数据范围）必须重跑。
  - 「人工备注」块用 MANUAL:NOTES 标记包裹，重跑时原样保留。
#>

param(
    [string]$MysqlExe = 'E:\dev\mysql-8.0\bin\mysql.exe',
    [string]$DbHost = '127.0.0.1',
    [int]$Port = 3307,
    [string]$User = 'admin_app',
    [string]$Password = '',
    [string]$Database = 'admin_starter',
    [string]$OutFile = 'docs/design/permission-matrix.md'
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

if (-not (Test-Path -LiteralPath $MysqlExe)) { throw "找不到 mysql 客户端：$MysqlExe，请用 -MysqlExe 指定" }
$db = $Database

# ---------- 取数 ----------
$menus = @{}
foreach ($line in (Invoke-Query "SELECT id,parent_id,name,menu_type,COALESCE(path,''),COALESCE(permission_code,''),is_enabled,deleted FROM sys_menu ORDER BY id;")) {
    $p = $line -split "`t"
    $menus[[long]$p[0]] = [pscustomobject]@{
        Id = [long]$p[0]; ParentId = [long]$p[1]; Name = $p[2]; Type = $p[3]
        Path = $p[4]; Code = $p[5]; Enabled = [int]$p[6]; Deleted = [int]$p[7]
    }
}

$roles = @()
foreach ($line in (Invoke-Query "SELECT id,code,name,data_scope,own_filter,is_enabled,deleted FROM sys_role ORDER BY id;")) {
    $p = $line -split "`t"
    $roles += [pscustomobject]@{
        Id = [long]$p[0]; Code = $p[1]; Name = $p[2]; DataScope = $p[3]
        OwnFilter = $p[4]; Enabled = [int]$p[5]; Deleted = [int]$p[6]
    }
}

$grants = @{}
foreach ($line in (Invoke-Query "SELECT role_id,menu_id FROM sys_role_menu ORDER BY role_id,menu_id;")) {
    $p = $line -split "`t"
    $rid = [long]$p[0]
    if (-not $grants.ContainsKey($rid)) { $grants[$rid] = New-Object System.Collections.Generic.HashSet[long] }
    [void]$grants[$rid].Add([long]$p[1])
}

$targets = @{}
foreach ($line in (Invoke-Query "SELECT role_id,target_type,COUNT(*) FROM role_scope_target GROUP BY role_id,target_type ORDER BY role_id,target_type;")) {
    $p = $line -split "`t"
    $rid = [long]$p[0]
    if (-not $targets.ContainsKey($rid)) { $targets[$rid] = @() }
    $targets[$rid] += "$($p[1])×$($p[2])"
}

$userCounts = @{}
foreach ($line in (Invoke-Query "SELECT role_id,COUNT(*) FROM sys_user_role GROUP BY role_id;")) {
    $p = $line -split "`t"
    $userCounts[[long]$p[0]] = [int]$p[1]
}

function Get-ModuleName {
    param([long]$menuId)
    $cur = $menus[$menuId]
    if ($null -eq $cur) { return '（父级缺失）' }
    $guard = 0
    while ($cur.ParentId -ne 0 -and $guard -lt 10) {
        $parent = $menus[$cur.ParentId]
        if ($null -eq $parent) { return '（父级缺失）' }
        $cur = $parent
        $guard++
    }
    return $cur.Name
}

function Get-ModuleRootId {
    param([long]$menuId)
    $cur = $menus[$menuId]
    if ($null -eq $cur) { return 999999 }
    $guard = 0
    while ($cur.ParentId -ne 0 -and $guard -lt 10) {
        $parent = $menus[$cur.ParentId]
        if ($null -eq $parent) { return 999999 }
        $cur = $parent
        $guard++
    }
    return $cur.Id
}

function Get-MenuTypeLabel {
    param([string]$t)
    switch ($t) { 'M' { '目录' } 'C' { '菜单' } 'F' { '按钮/接口' } default { $t } }
}

$activeMenus = @($menus.Values | Where-Object { $_.Deleted -eq 0 })
$permMenus = @($activeMenus | Where-Object { $_.Code -ne '' } | Sort-Object { Get-ModuleRootId $_.Id }, { $_.Id })
$activeRoles = @($roles | Where-Object { $_.Deleted -eq 0 })

# ---------- 1. 权限码清单 ----------
function New-PermissionListBlock {
    $lines = @('<!-- AUTO:PERM-CODES:START -->', '',
        '| 模块 | 类型 | 名称 | 权限码 | 菜单 id |', '|---|---|---|---|---|')
    foreach ($m in $permMenus) {
        $lines += "| $(Get-ModuleName $m.Id) | $(Get-MenuTypeLabel $m.Type) | $($m.Name) | ``$($m.Code)`` | $($m.Id) |"
    }
    $lines += @('', "共 **$($permMenus.Count)** 个权限码（菜单级 $(@($permMenus | Where-Object { $_.Type -ne 'F' }).Count) 个 / 按钮接口级 $(@($permMenus | Where-Object { $_.Type -eq 'F' }).Count) 个）。", '', '<!-- AUTO:PERM-CODES:END -->')
    return ($lines -join "`r`n")
}

# ---------- 2. 角色 × 权限码矩阵 ----------
function New-RoleMatrixBlock {
    $header = '| 权限码 | 模块 | 类型 | ' + (($activeRoles | ForEach-Object { "$($_.Code)（$($_.Id)）" }) -join ' | ') + ' |'
    $sep = '|---|---|---|' + (($activeRoles | ForEach-Object { '---' }) -join '|') + '|'
    $lines = @('<!-- AUTO:ROLE-MATRIX:START -->', '', $header, $sep)
    foreach ($m in $permMenus) {
        $cells = @()
        foreach ($r in $activeRoles) {
            $has = $grants.ContainsKey($r.Id) -and $grants[$r.Id].Contains([long]$m.Id)
            $cells += $(if ($has) { '✓' } else { '' })
        }
        $lines += "| ``$($m.Code)`` | $(Get-ModuleName $m.Id) | $(Get-MenuTypeLabel $m.Type) | " + ($cells -join ' | ') + ' |'
    }
    $counts = @()
    foreach ($r in $activeRoles) {
        $n = 0
        foreach ($m in $permMenus) {
            if ($grants.ContainsKey($r.Id) -and $grants[$r.Id].Contains([long]$m.Id)) { $n++ }
        }
        $counts += $n
    }
    $lines += '| **合计（权限码）** |  |  | ' + (($counts | ForEach-Object { "**$_**" }) -join ' | ') + ' |'
    $lines += @('', '<!-- AUTO:ROLE-MATRIX:END -->')
    return ($lines -join "`r`n")
}

# ---------- 3. 角色数据权限 ----------
function New-RoleScopeBlock {
    $lines = @('<!-- AUTO:ROLE-SCOPE:START -->', '',
        '| 角色 id | 编码 | 名称 | 组织范围（6 档） | 归属过滤（3 档） | 自定义目标 | 关联用户数 | 授权菜单数 | 授权权限码数 |',
        '|---|---|---|---|---|---|---|---|---|')
    foreach ($r in $activeRoles) {
        $menuCount = if ($grants.ContainsKey($r.Id)) { $grants[$r.Id].Count } else { 0 }
        $codeCount = 0
        foreach ($m in $permMenus) { if ($grants.ContainsKey($r.Id) -and $grants[$r.Id].Contains([long]$m.Id)) { $codeCount++ } }
        $targetText = if ($targets.ContainsKey($r.Id)) { $targets[$r.Id] -join '、' } else { '—' }
        $userText = if ($userCounts.ContainsKey($r.Id)) { $userCounts[$r.Id] } else { 0 }
        $lines += "| $($r.Id) | ``$($r.Code)`` | $($r.Name) | ``$($r.DataScope)`` | ``$($r.OwnFilter)`` | $targetText | $userText | $menuCount | $codeCount |"
    }
    $lines += @('', '<!-- AUTO:ROLE-SCOPE:END -->')
    return ($lines -join "`r`n")
}

# ---------- 4. 一致性核对 ----------
function New-IntegrityBlock {
    $issues = @()
    # 4.1 授权指向不存在/已删除的菜单
    $orphan = @()
    foreach ($rid in $grants.Keys) {
        foreach ($mid in $grants[$rid]) {
            $m = $menus[[long]$mid]
            if ($null -eq $m) { $orphan += "角色 $rid → 菜单 $mid（不存在）" }
            elseif ($m.Deleted -eq 1) { $orphan += "角色 $rid → 菜单 $mid（已软删除：$($m.Name)）" }
        }
    }
    if ($orphan.Count -eq 0) { $issues += '- 授权指向不存在/已删除菜单：**0 条**。' }
    else { $issues += '- 授权指向不存在/已删除菜单：' + ($orphan -join '；') }

    # 4.2 重复权限码
    $dup = @($permMenus | Group-Object Code | Where-Object { $_.Count -gt 1 })
    if ($dup.Count -eq 0) { $issues += '- 启用的重复权限码：**0 组**（`sys_menu.permission_code` 已由 `uk_menu_permission_code` 唯一约束兜底，见 `docs/design/data-model.md` §3.3）。' }
    else { $issues += '- 启用的重复权限码：' + (($dup | ForEach-Object { "``$($_.Name)`` ×$($_.Count)" }) -join '、') }

    # 4.3 F 按钮父级缺失
    $badParent = @($activeMenus | Where-Object { $_.Type -eq 'F' -and (-not $menus.ContainsKey($_.ParentId) -or $menus[$_.ParentId].Deleted -eq 1) })
    if ($badParent.Count -eq 0) { $issues += '- 按钮（F）父级缺失/被删：**0 个**。' }
    else { $issues += '- 按钮（F）父级缺失/被删：' + (($badParent | ForEach-Object { "``$($_.Code)``" }) -join '、') }

    # 4.4 零授权权限码
    $ungranted = @($permMenus | Where-Object {
            $m = $_
            $hit = $false
            foreach ($r in $activeRoles) { if ($grants.ContainsKey($r.Id) -and $grants[$r.Id].Contains([long]$m.Id)) { $hit = $true; break } }
            -not $hit
        })
    if ($ungranted.Count -eq 0) { $issues += '- 未被任何启用角色授予的权限码：**0 个**。' }
    else { $issues += '- 未被任何启用角色授予的权限码：' + (($ungranted | ForEach-Object { "``$($_.Code)``" }) -join '、') }

    # 4.5 授权给无权限码菜单（菜单/路由授权）
    $codeLessGrants = @()
    foreach ($r in $activeRoles) {
        if (-not $grants.ContainsKey($r.Id)) { continue }
        foreach ($mid in $grants[$r.Id]) {
            $m = $menus[[long]$mid]
            if ($null -ne $m -and $m.Deleted -eq 0 -and $m.Code -eq '') { $codeLessGrants += "$($r.Code)→$($m.Name)" }
        }
    }
    if ($codeLessGrants.Count -eq 0) { $issues += '- 授权给无权限码菜单（仅路由可见）：**0 条**。' }
    else { $issues += '- 授权给无权限码菜单（目录/个人中心，仅控制路由可见，属正常）：' + ($codeLessGrants -join '、') + '。' }

    # 4.6 软删除但仍占 id 的菜单/角色
    $deletedMenus = @($menus.Values | Where-Object { $_.Deleted -eq 1 })
    $deletedRoles = @($roles | Where-Object { $_.Deleted -eq 1 })
    $menuText = if ($deletedMenus.Count -eq 0) { '**0 条**' } else {
        (($deletedMenus | ForEach-Object { if ($_.Code -ne '') { "$($_.Id)（``$($_.Code)``）" } else { "$($_.Id)（无权限码）" } }) -join '、')
    }
    $roleText = if ($deletedRoles.Count -eq 0) { '**0 条**' } else {
        (($deletedRoles | ForEach-Object { "$($_.Id)（``$($_.Code)``）" }) -join '、')
    }
    $issues += "- 软删除菜单仍占物理 id：$menuText。"
    $issues += "- 软删除角色仍占物理 id：$roleText。"
    $roleMenuRows = @(Invoke-Query 'SELECT COUNT(*) FROM sys_role_menu;')[0]
    $issues += "- ``sys_role_menu`` 物理行数：**$roleMenuRows**；其中启用角色授权行：见上表。"

    return (@('<!-- AUTO:INTEGRITY:START -->', '') + $issues + @('', '<!-- AUTO:INTEGRITY:END -->')) -join "`r`n"
}

# ---------- 组装 ----------
$defaultNotes = @'
- **权限模型观察（需人工确认）**
  1. 部门管理员（`DEPT_MANAGER`）持有 `system:user:assign`、`system:role:menu`、`system:role:scope`：理论上可给自己或他人扩权（包括授予 `SUPER_ADMIN`）。是否属于预期由人工判定，本次未改代码。
  2. 普通员工（`EMPLOYEE`）持有 `system:user:list`、`system:role:list`：可查看全部用户与角色列表（只读）。是否符合业务需确认。
  3. 软删除的测试菜单/角色仍占物理 id（见上文第 5 节），与 `docs/baseline/assertions.md` #12 的踩坑同源；当前鉴权查询均过滤 `deleted=0`，不影响线上权限。
'@

$manualPattern = '<!-- MANUAL:NOTES:START -->[\s\S]*?<!-- MANUAL:NOTES:END -->'
$existing = if (Test-Path -LiteralPath $OutFile) { [System.IO.File]::ReadAllText((Resolve-Path -LiteralPath $OutFile), $utf8) } else { '' }
$manualBlock = "<!-- MANUAL:NOTES:START -->`r`n$defaultNotes`r`n<!-- MANUAL:NOTES:END -->"
if ($existing -match $manualPattern) { $manualBlock = $Matches[0] }

$docTemplate = @'
# 权限矩阵（as-built v1.0）

| 项目 | 内容 |
|---|---|
| 版本 | v1.0（竣工现状，{TODAY}） |
| 状态 | 由脚本从 `admin_starter` 库导出；人工备注单独标记 |
| 读者 | AI 编码助手、后端、前端 |
| 定位 | **as-is**：当前库里“谁有哪些权限码、数据范围是什么”，不是规范（to-be） |
| 依赖 | `AGENTS.md` §6 第 0 步（读基线）与第 8 步（回填基线） |

> **库变了要重跑**：菜单、角色、`sys_role_menu`、`sys_role` 的 `data_scope` / `own_filter`、`role_scope_target` 任一变化，都必须重跑
> `pwsh scripts/export-permission-matrix.ps1`；代码/库/权限码变更时本文件必须在第 8 步同步。
> 权限码事实源是 `sys_menu.permission_code`；接口鉴权口径以 `docs/api/openapi.generated.yaml` 与后端注解为准。

<!-- AUTO:HEADER:START -->
> 本文件由脚本导出于 {TODAY}，除「人工备注」外请勿手工编辑。
<!-- AUTO:HEADER:END -->

## 1. 权限码清单（来源 `sys_menu`，`deleted=0`）

{PERM}

## 2. 角色 × 权限码矩阵（来源 `sys_role_menu`）

> `✓` = 该角色被授予该菜单/按钮；空白 = 未授予。列标题格式为 `角色编码（角色 id）`。

{MATRIX}

## 3. 角色数据权限（来源 `sys_role` / `role_scope_target` / `sys_user_role`）

{SCOPE}

**规则（实现口径，不是规范）**

- 组织范围 6 档：`ALL` 全部 / `DEPT` 本部门 / `DEPT_AND_CHILD` 本部门及下级 / `SELF` 仅本人创建 / `CUSTOM_DEPT` 指定部门 / `CUSTOM_USER` 指定人员。
- 归属过滤 3 档：`NONE` 不限 / `CREATOR` 仅本人创建 / `OWNER` 仅本人负责（`ownerColumn`，如 `biz_demo_task.owner_id`）。
- **多角色取并集**：组织范围按 `deptIds` / `userIds` 合并；任一角色 `ALL` 即放行全部。
- **归属取最宽松档**：任一角色 `own_filter=NONE` 即不加归属限制；否则 `CREATOR` 与 `OWNER` 条件按 `OR` 合并。
- **无角色**：默认只能看本人创建的数据（`create_by = 当前用户`）。
- 两个维度叠加为 `组织条件 AND 归属条件`；某一维度启用但条件为空时输出 `1 = 0`（拒绝，不退化为全量）。
- 条件按用户缓存：`admin:datascope:v1:{userId}`，TTL 5 分钟；角色/用户角色/菜单授权变更后调用 `evictUser` 立即失效。

## 4. 例外单列

### 4.1 `sys_user` 豁免数据权限

- `sys_user` 在 `DataPermissionTables.EXEMPT` 中显式登记，实体/Mapper 标注 `@DataPermissionExempt`。
- 按部门查看用户使用列表普通筛选参数 `deptId`（`GET /api/v1/users?deptId=`），不经过数据权限层。
- 理由：能进入用户管理页的人已由功能权限 `system:user:list` 拦截；叠加数据权限会出现“管理员看不到自己要管的人”。

### 4.2 公开接口免鉴权字段（5 项）

`GET /api/v1/public/configs` 免鉴权，返回且仅返回：

| 字段 | 对应配置键 |
|---|---|
| `name` | `sys.name` |
| `slogan` | `sys.name.slogan` |
| `copyright` | `sys.copyright` |
| `icp` | `sys.icp` |
| `uploadMaxSize` | `sys.upload.max-size` |

白名单在 `PublicConfigAppService.PUBLIC_KEYS`；敏感项（`is_sensitive=1`）与停用项双重兜底不返回。新增字段必须同时改白名单与本文件。

### 4.3 `DataPermissionTables` 受控 / 豁免分类（15 张业务表）

| 分类 | 表 | 数量 |
|---|---|---|
| 受控 | `sys_oper_log`、`sys_file`、`biz_demo_task` | 3 |
| 豁免 | `sys_user`、`sys_user_role`、`sys_role`、`sys_role_menu`、`role_scope_target`、`sys_menu`、`sys_dept`、`sys_post`、`sys_dict_type`、`sys_dict_item`、`sys_config`、`sys_login_log` | 12 |
| 未登记 | 新增表若未显式登记，受控查询**直接报错**（不是静默跳过） | — |

受控表的归属列：

| 表 | 组织范围列 | 归属列（SELF/CUSTOM_USER） | 负责人列（OWNER） |
|---|---|---|---|
| `sys_oper_log` | `dept_id` | `create_by` | `create_by` |
| `sys_file` | `dept_id` | `create_by` | `create_by` |
| `biz_demo_task` | `dept_id` | `create_by` | `owner_id` |

## 5. 一致性核对（自动）

{INTEGRITY}

## 6. 人工备注（重跑保留）

{MANUAL}

## 7. 自检清单

- [x] 权限码清单与 `sys_menu.permission_code` 一致（MUST, STATIC）
- [x] 矩阵与 `sys_role_menu` 一致，空白与 `✓` 可逐格核对（MUST, STATIC）
- [x] 数据范围每角色一行，写明多角色并集与归属最宽松档（MUST, STATIC）
- [x] 例外项单列：`sys_user` 豁免、公开 5 字段、受控/豁免分类（MUST, STATIC）
- [x] 文件头写明“库变了要重跑”并给出脚本（MUST, STATIC）
'@

$doc = $docTemplate.Replace('{TODAY}', $today)
$doc = $doc.Replace('{PERM}', (New-PermissionListBlock))
$doc = $doc.Replace('{MATRIX}', (New-RoleMatrixBlock))
$doc = $doc.Replace('{SCOPE}', (New-RoleScopeBlock))
$doc = $doc.Replace('{INTEGRITY}', (New-IntegrityBlock))
$doc = $doc.Replace('{MANUAL}', $manualBlock)

[System.IO.File]::WriteAllText((Join-Path (Get-Location) $OutFile), $doc, $utf8)
Write-Host "已导出 $OutFile（权限码 $($permMenus.Count) 个，启用角色 $($activeRoles.Count) 个）"
