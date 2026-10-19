<#
.SYNOPSIS
Delete only a tagged demo resource group, with an interactive confirmation.
#>
[CmdletBinding(SupportsShouldProcess, ConfirmImpact = 'High')]
param(
    [Parameter(Mandatory)][guid]$SubscriptionId,
    [ValidatePattern('^rg-[a-z0-9-]+$')][string]$ResourceGroupName = 'rg-rabbitmq-servicebus-demo'
)
$ErrorActionPreference = 'Stop'
$json = & az group show --subscription $SubscriptionId --name $ResourceGroupName --output json
if ($LASTEXITCODE -ne 0) { throw 'Cannot read the specified group; nothing deleted.' }
$group = $json | ConvertFrom-Json
if ($group.tags.purpose -ne 'rabbitmq-servicebus-java-demo') {
    throw 'Refusing deletion: the resource group does not have the expected demo ownership tag.'
}
& az resource list --subscription $SubscriptionId --resource-group $ResourceGroupName --query '[].{name:name,type:type}' --output table
if ($LASTEXITCODE -ne 0) { throw 'Cannot inventory group resources; nothing deleted.' }
if ($PSCmdlet.ShouldProcess("$SubscriptionId/$ResourceGroupName", 'Delete the entire dedicated demo resource group and its messages')) {
    & az group delete --subscription $SubscriptionId --name $ResourceGroupName --yes
    if ($LASTEXITCODE -ne 0) { throw 'Azure resource group deletion failed.' }
}
