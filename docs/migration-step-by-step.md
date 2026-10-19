---
title: "Parcours B : migrer votre application avec Copilot"
description: "Installation, prompts a copier, preuves et validations pour une migration Java/Spring guidee de RabbitMQ vers Azure Service Bus."
---

# Parcours B : migrer votre application

**Suivez les etapes dans l'ordre, dans votre propre depot.** Copilot analyse votre
code, prepare une proposition adaptee, puis implemente les changements approuves.
Vous ne devez pas concevoir seuls le bridge ou l'IaC : demandez-les avec les prompts
ci-dessous. En revanche, votre equipe doit valider les choix metier, les changements,
les couts et les actions sur les messages.

Ce parcours couvre les applications Java/Spring utilisant RabbitMQ. Il utilise
Copilot CLI **ou** VS Code, sans GitHub App, issue, PR ou agent cloud.
Il ne garantit pas qu'une topologie quelconque soit automatiquement convertible.
Un resultat `Blocked` exige de resoudre la cause, pas de passer a l'etape suivante.
Les scripts `deploy.ps1` et `demo.ps1` de ce depot sont reserves au
[laboratoire A](lab-step-by-step.md), pas a votre application.

## Regles de progression

| Resultat | Ce que cela signifie |
|----------|---------------------|
| `Assessed` | Code analyse ; aucune preuve de migration executee |
| `Implemented` | Changements ecrits ; pas encore tous valides |
| `Verified` | Seulement l'etape et l'environnement explicitement testes |
| `Blocked` | Decision, autorisation ou preuve manquante ; arreter cette etape |

Ne reutilisez pas les comptes ni ressources de l'auteur. Ne collez pas de secrets,
de chaines de connexion, de payloads clients ou de fichiers `.env` dans le chat.
Le skill ne contient aucun script de migration ni droit preapprouve. Gardez les
confirmations d'outils ; refusez tout outil hors de l'etape autorisee.
Une consigne textuelle seule ne force pas la lecture seule.

## 0. Preparer les acces et le point de retour

Votre equipe doit disposer :

* Du depot applicatif complet, de ses instructions et d'un etat Git sauvegarde
  selon votre processus ; n'ecrasez pas de changements non committes.
* D'un acces Copilot autorise par votre organisation et d'un client CLI ou VS Code
  prenant en charge les Agent Skills.
* Des responsables applicatif, RabbitMQ, Azure et metier pouvant approuver leurs etapes.
* D'un environnement de test isole et d'un abonnement Azure approuve, avant les
  validations distantes. Aucun acces broker/Azure n'est necessaire pour l'audit local.

**Attendu :** depot identifie, responsable de validation nomme, aucun risque pour
la production. **Arret :** si les politiques interdisent cet usage de Copilot ou si
vous n'avez pas les sources necessaires, obtenez l'accord ou les elements manquants.

## 1. Installer le skill dans votre depot

Lisez d'abord le [skill](../.github/skills/rabbitmq-to-servicebus/SKILL.md)
et ses trois references. Verifiez aussi les
[conditions d'utilisation du depot](../README.md#licence) avant reutilisation.
Dans PowerShell 7, adaptez les deux chemins absolus :

```powershell
$ErrorActionPreference = 'Stop'
$kit = 'C:\Dev\rabbitmq-to-azure-servicebus-java'
$app = 'C:\Dev\mon-application'
# Si le kit n'est pas deja clone, adaptez le chemin puis executez :
# git clone https://github.com/Ch0wseth/rabbitmq-to-azure-servicebus-java.git $kit
$source = Join-Path $kit '.github\skills\rabbitmq-to-servicebus'
$parent = Join-Path $app '.github\skills'
$target = Join-Path $parent 'rabbitmq-to-servicebus'
if (!(Test-Path -LiteralPath $app -PathType Container)) { throw 'Depot applicatif introuvable.' }
if (Test-Path -LiteralPath $target) { throw 'Skill deja present : comparer les versions sans ecraser.' }
$required = @(
    'SKILL.md'
    'references\topology-mapping.md'
    'references\java-migration.md'
    'references\cutover-and-rollback.md'
)
foreach ($file in $required) {
    if (!(Test-Path -LiteralPath (Join-Path $source $file) -PathType Leaf)) {
        throw "Fichier manquant dans le kit : $file"
    }
}
New-Item -ItemType Directory -Path $parent -Force | Out-Null
Copy-Item -LiteralPath $source -Destination $target -Recurse
foreach ($file in $required) {
    $original = (Get-FileHash -LiteralPath (Join-Path $source $file) -Algorithm SHA256).Hash
    $copied = (Get-FileHash -LiteralPath (Join-Path $target $file) -Algorithm SHA256).Hash
    if ($original -ne $copied) { throw "Copie non conforme : $file" }
}
Set-Location -LiteralPath $app
```

**Attendu :** quatre fichiers identiques au kit dans
`.github\skills\rabbitmq-to-servicebus` de votre application.
**Arret :** toute erreur de copie ou version deja installee. Ne supprimez pas le
skill existant ; comparez et faites approuver la mise a jour.

## 2. Ouvrir Copilot et verifier le chargement

**CLI :** lancez `copilot` depuis `$app`, connectez-vous si necessaire avec `/login`.
Utilisez `/skills reload`, puis `/skills info rabbitmq-to-servicebus`.
**VS Code :** ouvrez le dossier `$app`, puis Copilot Chat en mode Agent.
Dans les deux cas, utilisez le prompt explicite de l'etape 3 et verifiez dans les
traces que `rabbitmq-to-servicebus` est charge. Sinon, arretez et consultez le
[guide d'installation](skill-usage.md) et les docs officielles qui y sont liees.
Les commandes `/skills reload` et `/skills info` de ce guide sont celles du CLI.

Pour imposer l'audit CLI sans terminal, ecriture ni MCP, vous pouvez utiliser,
depuis le depot applicatif, cette session limitee :

```powershell
copilot --available-tools skill view glob rg --allow-all-tools --deny-tool shell --deny-tool write --disable-mcp-server '*' --no-remote --no-remote-export -p "Utilise le skill /rabbitmq-to-servicebus. Audite cette application en lecture seule. Cite les sources et lignes, distingue observe, infere et inconnu, propose un plan sans rien executer. Ne lis pas les fichiers secrets. Ne pose pas de question interactive : liste les decisions manquantes."
```

Ici `--allow-all-tools` autorise uniquement les quatre outils exposes ; ne
l'utilisez pas seul lors des etapes suivantes. Pour l'implementation, ouvrez une
session normale avec confirmations actives, pas une session d'audit privee d'ecriture.
Dans VS Code, desactivez les outils de modification, terminal et services distants
pendant l'audit ; ne les reactivez qu'aux etapes approuvees.

## 3. Auditer l'application, sans la modifier

Copiez dans le chat :

```text
Utilise le skill /rabbitmq-to-servicebus. Etape 1 : audit local en lecture seule.
Inspecte mon application, pas le code de demonstration du kit. Ne modifie rien,
ne lance ni commande, ni build, ni service et ne contacte ni broker ni Azure.
Ne lis pas les fichiers secrets. Cite les chemins et lignes exacts.
Identifie producteurs, consommateurs, topologie, routage, protocole, versions,
ACK/confirms, QoS, TTL, priorites, DLX, IDs, ordre, retries et effets metier.
Si un Channel est fourni par un appelant non disponible, sa configuration effective
reste inconnue. N'infere pas classic/quorum des seuls flags de durabilite.
Retourne Assessed, les incompatibilites, les inconnus bloquants, les decisions a
prendre et un plan avec fichiers concernes. Aucune migration n'est declaree verifiee.
```

**Attendu :** tableau source -> cible -> ecart -> test, et questions ciblees.
Ouvrez les fichiers cites : les lignes doivent contenir les faits annonces.
Faites corriger l'audit s'il affirme l'etat d'un broker non inspecte, des confirms
desactives sur un canal fourni par l'appelant, ou un paiement reel dans un stub.
Une reponse assuree de Copilot ne remplace pas ces verifications.
**Arret :** aucun usage RabbitMQ dans ce perimetre, langage non couvert, sources
manquantes ou comportement sans solution approuvee. Indiquez le bon module ou faites
intervenir le responsable concerne ; ne fabriquez pas une topologie pour continuer.

## 4. Fixer les decisions et enregistrer le plan

Repondez aux questions de l'audit avec vos responsables. Ne choisissez pas un SKU,
une perte acceptable, une priorite ou une politique de paiement par defaut pour
supprimer un blocage. Utilisez les [explications techniques](client-migration.md).

| A faire confirmer | Informations a obtenir |
|-------------------|------------------------|
| Perimetre | Producteurs, queues, consommateurs, proprietaire et environnement |
| Routage | Definitions expurgees exchanges/bindings/queues et policies effectives |
| Contrat | Schema, ID stable, correlation, ordre, TTL/deadline et priorites |
| Metier | Idempotence durable, stockage/transaction et politique des anciens messages sans ID |
| Exploitation | Ready + unacked, DLQ, debits, tailles, durees, seuils et fenetre d'observation |
| Azure | Tenant, abonnement, region, reseau, SKU/couts, identites et droits minimaux |
| Bascule | Gel/drain ou bridge, arret des consommateurs, rollback, RTO/RPO et decideur |

Un export broker doit etre expurge de secrets avant d'etre fourni a Copilot.
Si vous voulez que Copilot le collecte, faites approuver une inspection distante
en lecture seule avec ressources et commandes exactes ; cela n'autorise aucune consommation.
Pour un backlog avec TTL court, le choix de drainer/laisser expirer doit aussi etre
valide metier, pas pris silencieusement par le bridge.

Apres accord sur ces decisions, copiez :

```text
Utilise le skill /rabbitmq-to-servicebus. A partir de l'audit et des decisions que
nous venons de valider, je t'autorise uniquement a creer docs/migration/plan.md et
docs/migration/validation.md, s'ils n'existent pas. S'ils existent, propose leur
mise a jour sans les ecraser. N'ecris aucun secret ou payload.
Enregistre perimetre, sources, choix et inconnus, fichiers a modifier, tests,
IaC et couts a verifier, strategie drain/bridge, reconciliation, bascule et rollback.
Dans validation.md, prepare les etapes avec statut Non execute, environnement,
commande exacte a approuver, resultat attendu, preuve obtenue et responsable.
Ne remplis aucune preuve comme reussie sans execution observee.
Signale les decisions encore bloquantes ; ne modifie pas l'application, ne lance rien.
```

**Attendu :** plan relu et valide, registre de preuves initialement non execute.
**Arret :** decision manquante pour le composant concerne. Conservez les inconnus ;
le simple enregistrement du plan ne les resout pas.
Gardez ces documents dans un emplacement prive approuve : meme sans secrets, ils
peuvent contenir des informations internes. Aucune publication automatique.

## 5. Faire adapter le code et preparer l'IaC

Dans une session avec outils de modification disponibles et confirmations actives :

```text
Utilise le skill /rabbitmq-to-servicebus et le plan valide dans docs/migration/plan.md.
Presente d'abord la liste exacte des fichiers et modifications proposes et attends
mon accord sur ce perimetre. Apres cet accord, adapte localement producteurs,
consommateurs et configuration ; ajoute les tests et l'IaC approuves.
Verifie les versions SDK et limites actuelles dans les documentations officielles,
sans acces a nos ressources Azure. Si cette verification est impossible, bloque
la partie concernee plutot que d'inventer une version ou une compatibilite.
Preserve les contrats, IDs et deadlines ; implemente l'idempotence durable retenue,
le settlement PeekLock apres succes, les erreurs explicites, retries bornes et DLQ.
Prepare Entra, les roles scopes et les parametres d'environnement sans secrets.
Si le plan retient un bridge, implemente-le pour NOTRE topologie avec send confirme
avant ACK, source non acquittee sur echec, politique pour messages non migrables et
registre de reconciliation. Ne copie pas le bridge limite du laboratoire tel quel.
Ne telecharge aucune dependance, ne lance aucun build, test, service ou deploiement.
Retourne Implemented seulement pour les changements termines, et la liste des
validations non executees. Ne committe et ne publie rien.
```

**Attendu :** changements reels dans votre depot, tests et IaC correspondant au
plan, revue humaine du diff. **Arret :** incompatibilite non resolue, simulation
presentee comme effet metier reel, placeholders dans un chemin critique, identite
instable, TTL rafraichi ou fonctionnalite perdue sans accord.

## 6. Valider localement, puis previsualiser Azure

```text
Utilise le skill /rabbitmq-to-servicebus. Propose les commandes exactes pour les
build/tests existants et les tests ajoutes, puis la validation statique de l'IaC.
Indique les telechargements et effets de bord. Attends mon accord avant execution.
Apres accord, execute uniquement ces controles locaux sans broker ni Azure.
Corrige les defauts causes par l'adaptation dans le perimetre approuve et relance
les controles affectes. Si un test exige un service, marque-le Non execute.
Actualise docs/migration/validation.md avec les commandes, resultats reels et limites.
Separe tests simules, integration et preuves metier. N'annonce pas la migration verifiee.
```

**Attendu :** build et tests locaux passes, aucun test ignore dissimule, template
valide autant que l'outillage le permet. **Arret :** echec ou verification requise
impossible. Corrigez avant de deployer.

Puis, pour **votre environnement de test approuve**, pas la production :

```text
Utilise le skill /rabbitmq-to-servicebus. Prepare les commandes de previsualisation
de NOTRE IaC en nommant tenant, abonnement, groupe, region, entites, identites,
roles, reseau et couts attendus a partir du plan. Ne reprends pas les cibles du kit.
Presente les acces distants necessaires et attends mon accord pour cette
previsualisation uniquement. Apres accord, verifie le contexte et execute le
what-if/plan adapte a notre outil. Ne cree aucune ressource ni role.
Resume les changements et bloque toute suppression ou modification inattendue.
```

**Attendu :** previsualisation comprise et approuvee avec votre equipe plateforme.
Une previsualisation accede au control-plane ; elle n'est pas un audit local.
Les plans Terraform et sorties d'outils peuvent contenir des donnees sensibles :
stockage prive, pas de copie brute dans une documentation publique.

## 7. Deployer le test et verifier l'integration

```text
Utilise le skill /rabbitmq-to-servicebus. Pour le seul environnement de test du
plan, presente les commandes de deploiement et les attributions RBAC separees,
leurs cibles et impacts. Attends mon accord explicite pour chaque categorie.
Apres accord, deploie les changements approuves, verifie les sorties non secretes,
la disponibilite des entites et les droits. Aucun acces data-plane n'est encore autorise.
Prepare ensuite les commandes de tests d'integration, les services a demarrer,
les messages synthetiques et leur nettoyage ; attends mon accord distinct avant
demarrage ou envoi/reception. N'utilise aucun message de production.
```

Apres revue et approbation des tests proposes :

```text
Execute uniquement les tests d'integration approuves et enregistre les preuves
dans docs/migration/validation.md. Couvre routage positif/negatif, contrats et TTL,
echec d'envoi sans ACK source, crash apres send avant ACK, IDs conserves,
deduplication dans et hors fenetre, redemarrage apres effet avant settlement,
idempotence durable, erreurs transitoires/permanentes, locks et DLQ.
Adapte la liste a la strategie retenue ; justifie les cas non applicables.
Ne transforme pas une validation sur mock en preuve sur broker ou paiement reel.
Ne purge rien et ne declare pas le cutover autorise.
```

**Attendu :** criteres du plan atteints, preuves par identifiant et resultat metier,
pas seulement un total de queue. **Arret :** test requis non execute/echec, effet
duplique, messages inexpliques ou configuration differente du plan.
Un prestataire de paiement peut demander un environnement sandbox et une preuve
specifique ; ne declenchez pas un debit reel pour valider le parcours.

## 8. Repeter le transfert et le rollback en test

```text
Utilise le skill /rabbitmq-to-servicebus. Prepare une repetition de notre strategie
drain/bridge sur les seuls brokers de test : gel des publications si requis,
arret/detachement des consommateurs historiques, ready + unacked et DLQ initiaux,
traitement des messages sans ID/expirables, commandes exactes et bilan par ID.
Prepare aussi le retour arriere AVANT de proposer la bascule : ou arrivent les
nouveaux messages, qui les traite, et quel transfert inverse serait necessaire.
Le transfert inverse doit etre implemente et teste si notre strategie le requiert.
Presente sources/destinations et effets de bord ; attends mon accord avant tout
arret, mouvement, ACK ou rejeu. Execute ensuite seulement le scenario approuve.
Enregistre le bilan, les ecarts, les resultats du rollback et le go/no-go de test.
```

**Attendu :** repetition et rollback requis prouves. Deux consommateurs sur la
meme queue se partagent le travail ; ils ne font pas une copie miroir.
Apres publication sur Service Bus, remettre l'ancienne URL ne rapatrie pas le
backlog Azure. **Arret :** ecart de reconciliation ou rollback non praticable.
Ne purgez jamais la source pour obtenir un bilan vert.

## 9. Faire approuver et executer la bascule reelle

Le succes en test n'autorise pas la production. Faites revoir et provisionner
sa cible avec des approbations distinctes, comme aux etapes 6 et 7, sans reutiliser
implicitement les autorisations de test.

```text
Utilise le skill /rabbitmq-to-servicebus. Prepare le dossier go/no-go de production
sans executer : revision testee, differences test/production, tenant/abonnement,
sources/destinations, perimetre, commandes exactes, responsables, fenetre,
seuils d'arret, preuves de test et rollback, metriques et duree d'observation.
N'invente aucun seuil et laisse Non verifie tout element sans preuve.
Liste separement les autorisations : acces, provisionnement/RBAC si necessaires,
arret/gel, transfert/rejeu, puis changement des endpoints.
Apres approbation du decideur, execute seulement le bloc d'operations explicitement
autorise. Presente la reconciliation actualisee avant de demander une approbation
fraiche du changement d'endpoints. Si un controle echoue, arrete et expose les
options du rollback approuve ; ne lance pas de transfert inverse implicitement.
```

**Attendu :** decideur et operateurs valident les preuves, autorisent chaque bloc
et observent les nouvelles publications et les effets metier selon le plan.
Si l'organisation exige un operateur pour la production, demandez a Copilot de
restituer le runbook exact ; l'operateur execute les commandes, Copilot ne le remplace pas.
**Arret :** critere go/no-go non atteint, changement non teste, perte/doublon metier
inexplique ou autorisation absente.

## 10. Clore, puis retirer l'ancien systeme separement

```text
Utilise le skill /rabbitmq-to-servicebus. Consolide le bilan du perimetre migre :
ready/unacked/DLQ source, acceptations et traitements cible, expirations/quarantaines,
effets metier, metriques sur la fenetre convenue et ecarts restants.
Actualise docs/migration/validation.md avec les preuves et limites reelles.
Prepare la checklist de decommissionnement et retention, sans supprimer ni purger.
Demande une approbation distincte avant toute suppression, retrait de role ou
arret definitif. Aucun commit, push ou publication sans accord de notre equipe.
```

**Termine pour le perimetre choisi** uniquement apres reconciliation, observation,
validation metier et prise en charge operationnelle. Les archives et la source
restent conservees selon le plan ; le decommissionnement peut intervenir plus tard.
Laissez le statut `Blocked` ou les controles `Non execute` visibles si necessaire.

## Reprendre une session sans perdre le contexte

```text
Utilise le skill /rabbitmq-to-servicebus. Lis docs/migration/plan.md et
docs/migration/validation.md ainsi que les instructions du depot.
Verifie les sources actuelles avant de reutiliser des preuves liees a une revision.
Indique la derniere etape effectivement prouvee et le prochain bloc a approuver.
Ne considere pas une ancienne approbation de session comme une autorisation
fraiche de transfert, de bascule ou de suppression. Ne lance rien pour l'instant.
```

## Ce qui a ete teste dans le kit

Le laboratoire Java a ete execute avec RabbitMQ et Azure Service Bus reels.
Le skill a ete evalue en CLI sur de petites fixtures : audit, refus de bascule
non prouvee et absence de RabbitMQ. Ces essais ne valident ni une execution
native VS Code, ni la generation complete pour votre application, ni sa production.
Faites les controles de ce parcours sur **votre** revision et **vos** environnements.
