targetScope = 'subscription'

param location string = 'francecentral'
param resourceGroupName string = 'rg-rabbitmq-servicebus-demo'
param namespaceName string = 'sb-rabbit-java-${uniqueString(subscription().id, resourceGroupName)}'
@description('Object ID of the local presenter in the target tenant.')
param principalId string

resource group 'Microsoft.Resources/resourceGroups@2025-04-01' = {
  name: resourceGroupName
  location: location
  tags: {
    purpose: 'rabbitmq-servicebus-java-demo'
    environment: 'demo'
  }
}

module messaging 'messaging.bicep' = {
  name: 'messaging'
  scope: group
  params: {
    location: location
    namespaceName: namespaceName
    principalId: principalId
  }
}

output namespace string = messaging.outputs.namespace
output queue string = messaging.outputs.queue
output resourceGroup string = group.name
