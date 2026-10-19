---
title: "Parcours A : executer le laboratoire"
description: "Instructions client pour reproduire la demonstration Java sur son propre abonnement Azure."
---

# Parcours A : reproduire le laboratoire

**Objectif :** observer un transfert de sept messages de RabbitMQ local vers un
vrai Azure Service Bus, puis envoyer directement trois messages avec le SDK Azure.
Ce parcours ne se connecte pas a votre RabbitMQ existant et ne migre pas votre application.
Pour cela, utilisez le [parcours B](migration-step-by-step.md).
Le depot reste sans licence : obtenez une autorisation distincte du titulaire
des droits avant reutilisation, selon la [notice du README](../README.md#licence).

## 1. Preparer votre poste

Utilisez PowerShell 7 sous Windows, Git, JDK 17+, Docker Desktop en mode Linux,
Azure CLI avec Bicep et votre abonnement de laboratoire. Faites approuver les droits
de creation et d'attribution RBAC par votre administrateur.
Les [prerequis du README](../README.md#installer-les-prerequis) sont la reference.
Le namespace Standard est payant tant qu'il existe.

```powershell
git clone https://github.com/Ch0wseth/rabbitmq-to-azure-servicebus-java.git
Set-Location .\rabbitmq-to-azure-servicebus-java
.\scripts\build.ps1
```

**Attendu :** construction reussie et fichier `target\migration-demo.jar`.
**Arret :** en cas d'erreur de compilation ou de dependance, corrigez-la avant Azure.
Le script peut telecharger Maven avec verification SHA512 ; il n'installe pas le JDK.

## 2. Choisir et previsualiser la cible

Remplacez les deux valeurs, sans utiliser celles d'un autre presentateur.
Choisissez un groupe neuf, dedie exclusivement a ce laboratoire.

```powershell
$tenant = '<VOTRE-TENANT-ID>'
$subscription = '<VOTRE-SUBSCRIPTION-ID>'
$group = 'rg-rabbitmq-servicebus-lab-client'
az login --tenant $tenant
if ($LASTEXITCODE -ne 0) { throw 'Connexion Azure echouee.' }
az account show --subscription $subscription --query '{name:name,tenant:tenantId,id:id}' --output table
if ($LASTEXITCODE -ne 0) { throw 'Abonnement inaccessible.' }
.\scripts\deploy.ps1 -TenantId $tenant -SubscriptionId $subscription -ResourceGroupName $group
```

**Attendu :** `what-if` proposant uniquement les ressources du laboratoire.
**Arret :** mauvais tenant, droits insuffisants, provider non enregistre, policies
incompatibles ou modification d'une ressource a conserver. Consultez le
[README](../README.md#choisir-la-cible-azure-et-previsualiser), sans contourner les policies.

## 3. Deployer apres revue des couts et changements

Lancez cette commande uniquement si la previsualisation est acceptee :

```powershell
.\scripts\deploy.ps1 -TenantId $tenant -SubscriptionId $subscription -ResourceGroupName $group -Apply
```

**Attendu :** namespace Standard, file `orders`, roles Sender/Receiver limites a la
file, et fichier local `local.ps1`. Aucun code applicatif n'est heberge dans Azure :
Java et RabbitMQ tournent sur votre poste.
**Arret :** si le deploiement echoue, inspectez ses operations avant de recommencer.
Attendez la propagation RBAC avant le premier envoi.

## 4. Executer et lire le resultat

N'utilisez pas une file partagee. Ne lancez pas un second consommateur ni une autre
execution du scenario en parallele.

```powershell
.\scripts\demo.ps1
```

**Attendu :** une exception volontaire `SIMULATED_CRASH_AFTER_SEND_BEFORE_ACK`,
puis la reprise, six commandes completees, une commande invalide en DLQ,
et trois commandes natives Azure completees. La derniere ligne doit avoir cette forme :

```text
DEMO_VERIFIED run=demo-... migrated=7 completed=6 dlq=1 native=3
```

Conservez le `run` et le resultat pour votre compte rendu, sans publier de configuration
ou de jeton. La coupure est une exception controlee, pas un kill de processus.
La DLQ est inspectee sans suppression ; les anciennes executions restent presentes.
La deduplication a une fenetre de dix minutes et ne garantit pas des effets metier
exactement une fois.

**Arret :** toute autre erreur, absence de la ligne finale ou backlog inattendu
signifie que le scenario n'est pas valide. Ne purgez pas et ne relancez pas aveuglement :
inspectez les messages du run concerne avec le [README](../README.md#limites-importantes).

| Probleme | Action |
|----------|--------|
| Docker indisponible | Demarrer Docker Desktop, moteur Linux, puis verifier son etat |
| 401/403 Azure | Verifier compte/abonnement cible et roles approuves ; attendre leur propagation |
| Connexion AMQP impossible | Faire verifier les WebSockets sortants sur TCP 443 et le proxy |
| Profondeur RabbitMQ initiale non nulle | Identifier le run incomplet ; ne pas effacer ses messages pour faire passer le test |

## 5. Nettoyer avec une approbation distincte

Les variables ci-dessous sont celles de l'etape 2. Dans un nouveau terminal,
redefinissez-les avant de continuer. Arretez les processus Java.

```powershell
.\scripts\cleanup.ps1 -SubscriptionId $subscription -ResourceGroupName $group -WhatIf
```

Verifiez que toutes les ressources listees peuvent etre supprimees, puis :

```powershell
.\scripts\cleanup.ps1 -SubscriptionId $subscription -ResourceGroupName $group
docker compose down
```

Confirmez la suppression Azure uniquement pour ce groupe dedie.
`docker compose down` conserve le volume RabbitMQ. N'effacez pas ce volume avant
d'avoir decide du sort des messages. Verifiez dans Azure que le groupe est supprime :
vider une queue n'arrete pas la facturation du namespace.
