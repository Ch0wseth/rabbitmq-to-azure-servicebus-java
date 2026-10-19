<#
.SYNOPSIS
Run and assert crash recovery, deduplication, retry, DLQ and native cutover.
.DESCRIPTION
Uses an isolated orders queue. Does not purge messages or delete resources.
#>
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
$root = Split-Path $PSScriptRoot -Parent
Push-Location $root
try {
    if (-not (Test-Path '.\local.ps1')) { throw 'Deploy first or create local.ps1 as described in README.' }
    . '.\local.ps1'
    if (-not (Test-Path '.\target\migration-demo.jar')) { throw 'Run .\scripts\build.ps1 first.' }
    & docker compose up -d --wait
    if ($LASTEXITCODE -ne 0) { throw 'RabbitMQ failed to start.' }
    $run = 'demo-' + [guid]::NewGuid().ToString('N').Substring(0, 12)
    function Invoke-DemoJava {
        param([string[]]$Arguments)
        & java '-Dorg.slf4j.simpleLogger.defaultLogLevel=warn' -jar '.\target\migration-demo.jar' @Arguments
        if ($LASTEXITCODE -ne 0) { throw "Java step failed: $($Arguments -join ' ')" }
    }
    Invoke-DemoJava -Arguments @('rabbit-depth', '0')
    Invoke-DemoJava -Arguments @('publish-rabbit', $run)
    Invoke-DemoJava -Arguments @('rabbit-depth', '7')
    $crashOutput = & java '-Dorg.slf4j.simpleLogger.defaultLogLevel=warn' -jar '.\target\migration-demo.jar' bridge 1 crash 2>&1 |
        ForEach-Object { Write-Host $_; $_ }
    $crashCode = $LASTEXITCODE
    if ($crashCode -eq 0 -or ($crashOutput -join "`n") -notmatch 'SIMULATED_CRASH_AFTER_SEND_BEFORE_ACK') {
        throw 'Expected simulated crash was not observed.'
    }
    Invoke-DemoJava -Arguments @('rabbit-depth', '7')
    $bridgeOutput = & java '-Dorg.slf4j.simpleLogger.defaultLogLevel=warn' -jar '.\target\migration-demo.jar' bridge 7 2>&1 |
        ForEach-Object { Write-Host $_; $_ }
    $bridgeCode = $LASTEXITCODE
    if ($bridgeCode -ne 0 -or ($bridgeOutput -join "`n") -notmatch 'redelivered=true') {
        throw 'Expected RabbitMQ redelivery was not observed.'
    }
    Invoke-DemoJava -Arguments @('rabbit-depth', '0')
    Invoke-DemoJava -Arguments @('consume', $run, '6', '1')
    Invoke-DemoJava -Arguments @('dlq', $run)
    $native = "$run-native"
    Invoke-DemoJava -Arguments @('publish-azure', $native)
    Invoke-DemoJava -Arguments @('consume', $native, '3', '0')
    Write-Host "DEMO_VERIFIED run=$run migrated=7 completed=6 dlq=1 native=3"
} finally {
    Pop-Location
}
