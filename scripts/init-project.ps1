# init-project.ps1 · 用本脚手架初始化一个新项目
#
# 用法：
#   powershell -ExecutionPolicy Bypass -File scripts\init-project.ps1 -Target "..\新项目"
#   或直接运行后按提示输入目标目录
#
# 行为（四步，每步都有确认提示，不做静默删除、不覆盖已有文件）：
#   1. 检查目标目录；非空则停止
#   2. 复制脚手架内容（排除 node_modules / .git / .tmp），并按 AGENTS.md §11.1 把模板落位到项目层
#   3. 打印需要人工完成的事项
#   4. 在目标目录跑一次 spec-lint 并输出结果
#
# 退出码：0 成功；1 前置检查不通过（目标非空等）；2 复制或检查过程异常

param(
    [string]$Target,
    [switch]$Yes
)

$ErrorActionPreference = 'Stop'
$ScaffoldRoot = Split-Path -Parent $PSScriptRoot
$ExcludeDirs = @('node_modules', '.git', '.tmp')

function Info($msg) { Write-Host $msg -ForegroundColor Cyan }
function Warn($msg) { Write-Host $msg -ForegroundColor Yellow }
function Fail($msg) {
    Write-Host $msg -ForegroundColor Red
    exit 1
}

function Confirm($question) {
    if ($Yes) { return $true }
    $answer = Read-Host "$question [y/N]"
    return ($answer -eq 'y' -or $answer -eq 'Y')
}

# ---------- 步骤 1：检查目标目录 ----------
if (-not $Target) {
    $Target = Read-Host "请输入新项目目录（不存在会自动创建）"
}
if (-not $Target) { Fail "未提供目标目录，已停止。" }

$Target = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($Target)

Info "脚手架来源：$ScaffoldRoot"
Info "目标目录：  $Target"

if ((Test-Path $Target) -and -not (Test-Path $Target -PathType Container)) {
    Fail "目标路径已存在且不是目录，已停止：$Target"
}

if (Test-Path $Target) {
    $existing = @(Get-ChildItem -LiteralPath $Target -Force | Where-Object { $_.Name -notin @('.DS_Store', 'Thumbs.db') })
    if ($existing.Count -gt 0) {
        Warn "目标目录非空，包含 $($existing.Count) 项："
        $existing | Select-Object -First 10 | ForEach-Object { Write-Host "  - $($_.Name)" }
        Fail "为避免覆盖已有文件，已停止。请换一个空目录，或先手动备份/清空。"
    }
} else {
    Info "目标目录不存在，将创建。"
}

if (-not (Confirm "确认在 $Target 初始化项目？")) { Fail "已取消。" }

# ---------- 步骤 2：复制脚手架 ----------
if (-not (Test-Path $Target)) {
    New-Item -ItemType Directory -Path $Target -Force | Out-Null
}

$copied = 0
$skipped = 0
$files = Get-ChildItem -LiteralPath $ScaffoldRoot -Recurse -File -Force | Where-Object {
    $relative = $_.FullName.Substring($ScaffoldRoot.Length + 1)
    $parts = $relative.Split([IO.Path]::DirectorySeparatorChar)
    -not ($parts | Where-Object { $_ -in $ExcludeDirs })
}

foreach ($file in $files) {
    $relative = $file.FullName.Substring($ScaffoldRoot.Length + 1)
    $destination = Join-Path $Target $relative
    $destinationDir = Split-Path -Parent $destination
    if (-not (Test-Path $destinationDir)) {
        New-Item -ItemType Directory -Path $destinationDir -Force | Out-Null
    }
    if (Test-Path $destination) {
        $skipped++
        continue
    }
    Copy-Item -LiteralPath $file.FullName -Destination $destination
    $copied++
}

# 空目录（只含 .gitkeep）已随文件复制；这里补建以防脚手架被裁剪
foreach ($dir in @('docs/requirements', 'docs/design', 'docs/decisions', 'docs/plan', 'docs/audit', 'docs/env', 'docs/baseline')) {
    $full = Join-Path $Target $dir
    if (-not (Test-Path $full)) { New-Item -ItemType Directory -Path $full -Force | Out-Null }
}

# 按 AGENTS.md §11.1 把模板复制到项目层；已存在则跳过，不覆盖
$templateMap = [ordered]@{
    'docs/templates/requirement-spec.md'   = 'docs/requirements/requirement-spec.md'
    'docs/templates/glossary.md'           = 'docs/requirements/glossary.md'
    'docs/templates/data-model.md'         = 'docs/design/data-model.md'
    'docs/templates/permission-matrix.md'  = 'docs/design/permission-matrix.md'
    'docs/templates/state-machine.md'      = 'docs/design/state-machine.md'
    'docs/templates/modules.md'            = 'docs/design/modules.md'
    'docs/templates/env-inventory.md'      = 'docs/env/env-inventory.md'
    'docs/templates/errors.yaml'           = 'docs/api/errors.yaml'
    'docs/templates/config-registry.yaml'  = 'docs/design/config-registry.yaml'
    'docs/templates/baseline-modules.md'   = 'docs/baseline/modules.md'
    'docs/templates/baseline-assertions.md' = 'docs/baseline/assertions.md'
}
$templateCopied = 0
foreach ($item in $templateMap.GetEnumerator()) {
    $source = Join-Path $Target $item.Key
    $dest = Join-Path $Target $item.Value
    if (-not (Test-Path $source)) { Warn "缺少模板 $($item.Key)，跳过落位。"; continue }
    if (Test-Path $dest) { continue }
    $destDir = Split-Path -Parent $dest
    if (-not (Test-Path $destDir)) { New-Item -ItemType Directory -Path $destDir -Force | Out-Null }
    Copy-Item -LiteralPath $source -Destination $dest
    $templateCopied++
}
Info "模板落位：新复制 $templateCopied 个；目录 → 目标路径见 AGENTS.md §11.1。"

Info "已复制 $copied 个文件，跳过已存在 $skipped 个。"

# ---------- 步骤 3：人工完成事项 ----------
Info @"

需要人工完成的事项（脚本不代填，填错会导致后续规则判定错误）：

  1. 填 AGENTS.md §9-A 项目约束：项目名称、目标平台、团队技能栈、部署环境与预算限制、合规要求、交付时间与里程碑，六项不得留空。
  2. 确认 AGENTS.md §9-B 技术栈：逐行确认或改写，每行补依据 ADR；未确认的行保持“未定”。
  3. 替换项目层副本的 EXAMPLE-ONLY：
     - docs/api/openapi.yaml（info.title / version / servers / 业务字段）
     - docs/requirements、docs/design、docs/env、docs/baseline 与 docs/api/errors.yaml 中的示例（模板已按 AGENTS.md §11.1 复制到位）
     - docs/templates 保持空壳，不要直接改模板
  4. 填 docs/design/config-registry.yaml（每个配置项的消费点与生效方式）与 docs/baseline/modules.md、docs/baseline/assertions.md（系统现状）。
  5. 从 docs/templates/adr-000-template.md 复制一份为 docs/decisions/ADR-001.md，填写首个决策。

提示：以上五项完成后，再进入《项目启动与变更流程》阶段 1（需求）。
"@ -ForegroundColor Yellow

if (-not (Confirm "以上事项已知悉，继续跑 spec-lint？")) { Warn "已跳过检查。请稍后手动执行：node tools/spec-lint/cli.mjs"; exit 0 }

# ---------- 步骤 4：跑 spec-lint ----------
$node = Get-Command node -ErrorAction SilentlyContinue
if (-not $node) {
    Warn "未检测到 Node.js，跳过 spec-lint。安装 Node 18+ 后在本目录执行：node tools/spec-lint/cli.mjs"
    exit 0
}

$lint = Join-Path $Target 'tools/spec-lint/cli.mjs'
if (-not (Test-Path $lint)) {
    Warn "未找到 $lint，跳过检查。"
    exit 0
}

Push-Location $Target
try {
    & node $lint
    $code = $LASTEXITCODE
} finally {
    Pop-Location
}

Write-Host ""
if ($code -eq 0) {
    Info "spec-lint 通过（退出码 0）。阶段 0 的自动检查项已完成。"
} elseif ($code -eq 1) {
    Warn "spec-lint 报出 error（退出码 1）。按上面的 文件:行 逐条修复后重跑。"
} else {
    Warn "spec-lint 自身异常（退出码 $code）。先确认 Node 版本与目录结构。"
}
exit $code
