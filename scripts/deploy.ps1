<#
.SYNOPSIS
Preview or deploy dedicated demo resources with queue-scoped Entra permissions.
.DESCRIPTION
The default action is what-if only. Pass -Apply to provision billable resources.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][guid]$SubscriptionId,
    [Parameter(Mandatory)][guid]$TenantId,
    [string]$Location = 'francecentral',
    [ValidatePattern('^rg-[a-z0-9-]+$')][string]$ResourceGroupName = 'rg-rabbitmq-servicebus-demo',
    [switch]$Apply
)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$accountText = & az account show --subscription $SubscriptionId --output json
if ($LASTEXITCODE -ne 0) { throw "Run az login --tenant $TenantId first." }
$account = $accountText | ConvertFrom-Json
if ($account.tenantId -ne $TenantId.ToString()) { throw 'Subscription tenant does not match TenantId.' }
if ($account.user.type -ne 'user') { throw 'This local demo expects an interactive user login.' }
$exists = & az group exists --subscription $SubscriptionId --name $ResourceGroupName --output tsv
if ($LASTEXITCODE -ne 0) { throw 'Cannot check whether the resource group already exists.' }
if ($exists -eq 'true') {
    $purpose = & az group show --subscription $SubscriptionId --name $ResourceGroupName --query tags.purpose --output tsv
    if ($LASTEXITCODE -ne 0 -or $purpose -ne 'rabbitmq-servicebus-java-demo') {
        throw 'Refusing to use an existing group not owned by this demo. Choose a new dedicated group name.'
    }
}
$token = & az account get-access-token --subscription $SubscriptionId --resource 'https://management.azure.com/' --query accessToken --output tsv
if ($LASTEXITCODE -ne 0 -or -not $token) { throw 'Cannot resolve presenter identity in target tenant.' }
try {
    # Graph /me can resolve a home-tenant object; the ARM token identifies the target-tenant principal.
    $payload = $token.Split('.')[1].Replace('-', '+').Replace('_', '/')
    $payload = $payload.PadRight($payload.Length + ((4 - $payload.Length % 4) % 4), '=')
    $claims = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($payload)) | ConvertFrom-Json
    if ($claims.tid -ne $TenantId.ToString()) { throw 'ARM token belongs to a different tenant.' }
    $principal = ([guid]$claims.oid).ToString()
    if ($principal -eq [guid]::Empty.ToString()) { throw 'ARM token has no user object ID.' }
} finally {
    $token = $null
    $payload = $null
    $claims = $null
}
$parameters = @("principalId=$principal", "location=$Location", "resourceGroupName=$ResourceGroupName")
$template = Join-Path $root 'infra\main.bicep'
$deployment = 'rabbitmq-servicebus-java-demo'
& az deployment sub what-if --subscription $SubscriptionId --name $deployment --location $Location --template-file $template --parameters @parameters --no-pretty-print
if ($LASTEXITCODE -ne 0) { throw 'Azure what-if failed; no deployment started.' }
if (-not $Apply) {
    Write-Host 'Preview only. Re-run with -Apply after reviewing the proposed resources and costs.'
    return
}
Write-Host 'Creating billable Service Bus Standard resources with -Apply approval.'
$result = & az deployment sub create --subscription $SubscriptionId --name $deployment --location $Location --template-file $template --parameters @parameters --query properties.outputs --output json
if ($LASTEXITCODE -ne 0) { throw 'Deployment failed; inspect Azure deployment operations.' }
$outputs = $result | ConvertFrom-Json
$namespace = $outputs.namespace.value
$queue = $outputs.queue.value
if ($namespace -notmatch '^[a-z0-9-]+\.servicebus\.windows\.net$' -or $queue -ne 'orders') {
    throw 'Unexpected deployment output; local configuration was not written.'
}
@(
    "`$env:AZURE_TENANT_ID = '$TenantId'"
    "`$env:AZURE_SUBSCRIPTION_ID = '$SubscriptionId'"
    "`$env:SERVICEBUS_NAMESPACE = '$namespace'"
    "`$env:SERVICEBUS_QUEUE = '$queue'"
) | Set-Content (Join-Path $root 'local.ps1') -Encoding utf8
Write-Host 'Deployment complete. Dot-source .\local.ps1 in each demo terminal.'
Write-Host 'RBAC propagation can take several minutes before the first send.'
