param location string
param namespaceName string
param principalId string

resource bus 'Microsoft.ServiceBus/namespaces@2026-01-01' = {
  name: namespaceName
  location: location
  tags: {
    purpose: 'rabbitmq-servicebus-java-demo'
    environment: 'demo'
  }
  sku: {
    name: 'Standard'
    tier: 'Standard'
  }
  properties: {
    disableLocalAuth: true
    minimumTlsVersion: '1.2'
    publicNetworkAccess: 'Enabled'
  }
}

resource orders 'Microsoft.ServiceBus/namespaces/queues@2026-01-01' = {
  parent: bus
  name: 'orders'
  properties: {
    requiresDuplicateDetection: true
    duplicateDetectionHistoryTimeWindow: 'PT10M'
    requiresSession: false
    enablePartitioning: false
    maxDeliveryCount: 3
    lockDuration: 'PT1M'
    defaultMessageTimeToLive: 'P1D'
    deadLetteringOnMessageExpiration: true
    maxSizeInMegabytes: 1024
  }
}

var roles = [
  '69a216fc-b8fb-44d8-bc22-1f3c2cd27a39'
  '4f6d3b9b-027b-4f4c-9142-0e5a2a2247e0'
]

resource access 'Microsoft.Authorization/roleAssignments@2022-04-01' = [for role in roles: {
  name: guid(orders.id, principalId, role)
  scope: orders
  properties: {
    principalId: principalId
    principalType: 'User'
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', role)
  }
}]

output namespace string = '${bus.name}.servicebus.windows.net'
output queue string = orders.name
