# 用途：后端运行中时，从 springdoc 导出真实实现的 OpenAPI 基线。
# 用法：pwsh -ExecutionPolicy Bypass -File scripts/export-openapi.ps1（PowerShell 7+，Windows PowerShell 5.1 读取 UTF-8 中文会解析失败）
#      可选参数：-BaseUrl http://127.0.0.1:8082 -Username admin -Password admin123 -OutFile docs/api/openapi.generated.yaml
# 说明：本脚本只读后端 /v3/api-docs.yaml，不修改任何业务数据；生成文件禁止手工编辑。
param(
    [string]$BaseUrl = 'http://127.0.0.1:8082',
    [string]$Username = 'admin',
    [string]$Password = 'admin123',
    [string]$OutFile = ''
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($OutFile)) {
    $OutFile = Join-Path (Split-Path $PSScriptRoot -Parent) 'docs\api\openapi.generated.yaml'
}

$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$headers = @{ 'Origin' = 'http://127.0.0.1:5173' }
$loginBody = @{ username = $Username; password = $Password } | ConvertTo-Json
Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login" -Method Post -WebSession $session `
    -ContentType 'application/json' -Headers $headers -Body $loginBody | Out-Null

$response = Invoke-WebRequest -Uri "$BaseUrl/v3/api-docs.yaml" -WebSession $session -SkipHttpErrorCheck
if ($response.StatusCode -ne 200) {
    throw "导出失败：GET $BaseUrl/v3/api-docs.yaml 返回 HTTP $($response.StatusCode)"
}

$content = if ($response.Content -is [byte[]]) {
    [Text.Encoding]::UTF8.GetString($response.Content)
} else {
    [string]$response.Content
}
if (-not $content.TrimStart().StartsWith('openapi:')) {
    throw "导出失败：响应不是 OpenAPI YAML（开头为：$($content.Substring(0, [Math]::Min(40, $content.Length)))）"
}

$stamp = Get-Date -Format 'yyyy-MM-dd HH:mm:ss'
$header = @(
    '# 本文件由 scripts/export-openapi.ps1 从运行中的后端 /v3/api-docs.yaml 自动生成，禁止手工编辑。',
    '# 契约源（手写维护）：docs/api/openapi.yaml；本文件只是实现基线（as-built），用于契约差异比对。',
    "# 生成时间：$stamp",
    ''
) -join "`n"

$parent = Split-Path $OutFile -Parent
if (-not (Test-Path $parent)) {
    New-Item -ItemType Directory -Path $parent -Force | Out-Null
}
[IO.File]::WriteAllText($OutFile, $header + "`n" + $content, (New-Object System.Text.UTF8Encoding($false)))

Write-Host "已生成：$OutFile"
