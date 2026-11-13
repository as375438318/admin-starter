# 用途：部署后冒烟——确认实例真的活着、探针可用、指标能抓到。
# 用法：pwsh -File scripts/smoke-deployment.ps1 -BaseUrl http://127.0.0.1:8082
#
# 为什么不塞进单元测试：/actuator/prometheus 在 Spring 测试上下文里不注册
# （webEndpointDiscoverer 只发现 health/info），但真实进程正常。
# 部署可用性只能对着真实例验——脚本在指标缺失时直接失败，不做"看起来通过"的软断言。
param(
    [string]$BaseUrl = 'http://127.0.0.1:8082',
    [int]$TimeoutSec = 10
)

$ErrorActionPreference = 'Stop'
$failures = @()

function Check($name, [scriptblock]$probe) {
    try {
        $detail = & $probe
        Write-Host ("  [OK]   {0}{1}" -f $name, $(if ($detail) { " -> $detail" } else { '' }))
    } catch {
        $failures += "$name：$($_.Exception.Message)"
        Write-Host ("  [FAIL] {0} -> {1}" -f $name, $_.Exception.Message) -ForegroundColor Red
    }
}

Write-Host "部署冒烟：$BaseUrl"
Check '业务健康接口' {
    $r = Invoke-WebRequest -Uri "$BaseUrl/api/v1/health" -TimeoutSec $TimeoutSec -UseBasicParsing
    "HTTP $($r.StatusCode)"
}
Check 'readiness 探针' {
    $r = Invoke-RestMethod -Uri "$BaseUrl/actuator/health/readiness" -TimeoutSec $TimeoutSec
    if ($r.status -ne 'UP') { throw "readiness=$($r.status)" }
    "status=$($r.status)"
}
Check 'liveness 探针' {
    $r = Invoke-RestMethod -Uri "$BaseUrl/actuator/health/liveness" -TimeoutSec $TimeoutSec
    if ($r.status -ne 'UP') { throw "liveness=$($r.status)" }
    "status=$($r.status)"
}
Check 'health 不泄露详情' {
    $raw = (Invoke-WebRequest -Uri "$BaseUrl/actuator/health" -TimeoutSec $TimeoutSec -UseBasicParsing).Content
    if ($raw -match '"db"|"redis"') { throw '匿名 health 暴露了组件详情' }
    'ok'
}
Check 'Prometheus 指标' {
    $raw = (Invoke-WebRequest -Uri "$BaseUrl/actuator/prometheus" -TimeoutSec $TimeoutSec -UseBasicParsing).Content
    if ($raw.Length -lt 1000) { throw "指标内容过短（$($raw.Length) 字节），可能端点未注册" }
    if ($raw -notmatch 'jvm_memory_used_bytes') { throw '缺少 jvm_memory_used_bytes，Prometheus 注册表可能没生效' }
    if ($raw -notmatch 'application="admin-starter"') { throw '缺少 application 标签' }
    "$([math]::Round($raw.Length / 1KB, 1)) KB 指标"
}

if ($failures.Count -gt 0) {
    Write-Host "`n冒烟失败 $($failures.Count) 项：" -ForegroundColor Red
    $failures | ForEach-Object { Write-Host "  - $_" }
    exit 1
}
Write-Host "`n全部通过。" -ForegroundColor Green