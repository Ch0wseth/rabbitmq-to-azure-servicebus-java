---
title: "Parcours client pour adapter la migration"
description: "Prerequis, correspondances, criteres de bascule et limites avant passage en production."
---

## Ce que le client peut reutiliser

Clonez le depot et executez le README avec votre tenant et votre abonnement.
L'IaC est parametree ; aucun acces a l'environnement de l'auteur n'est requis.
Commencez dans un groupe dedie de laboratoire, jamais sur une file de production.

Le pont illustre un invariant reutilisable : conserver une identite stable,
attendre l'envoi confirme avant d'acquitter la source, et supporter les renvois.
Les scripts permettent une demonstration reproductible, pas une migration
automatique des donnees ou de la configuration d'un RabbitMQ existant.
Le client RabbitMQ est volontairement limite a localhost et a la file `orders`.
Pour connecter un broker existant, implementez et revoyez d'abord la configuration
TLS, les credentials, le vhost, les droits et les declarations d'entites.

## Inventorier avant de modifier

Relevez les exchanges, types et bindings ; les files classiques ou quorum ;
les producteurs et consommateurs ; les headers, schemas et identifiants ;
les TTL, priorites, politiques de requeue et DLX ; les besoins d'ordre ;
la taille, les debits, les pics et le temps de traitement ; les exigences
reseau, de residence et de reprise.

Mesurez la profondeur des files, les messages non acquittes, les taux de redelivery,
le temps de traitement p95/p99 et la part de messages invalides.
Ne choisissez pas un SKU de production sur la base des sept messages de la demo.

## Traduire les semantiques, pas les noms

| RabbitMQ                                | Cible Service Bus a evaluer                                      |
|-----------------------------------------|-----------------------------------------------------------------|
| Queue de travail                        | Queue Service Bus                                               |
| Fanout / plusieurs copies               | Topic avec plusieurs subscriptions                              |
| Direct ou topic exchange                | Topic/subscriptions avec filtres adaptes, pas traduction 1:1      |
| Consumer ACK                            | Complete apres traitement sous PeekLock                         |
| NACK avec requeue                        | Abandon ou strategie de retry planifiee selon le besoin          |
| Dead-letter exchange                    | DLQ de chaque queue/subscription et workflow de remediation      |
| Publisher confirms                      | Resultat d'envoi SDK, avec identifiant stable lors des renvois    |
| Message ID                              | MessageId, sous reserve de stabilite et de longueur              |
| Besoin d'ordre par client ou commande    | Sessions, a concevoir sur une entite avec sessions activees      |

Le pont fourni ne copie pas les headers arbitraires, TTL individuels,
priorites ni routing keys. Le type RabbitMQ devient `subject`.
La retention cible de laboratoire est d'un jour.
Les messages sans ID stable bloquent le pont sans etre acquittes.
Definissez leur traitement avec le client, par exemple quarantaine explicite
ou cle metier fiable, au lieu de generer un UUID different a chaque tentative.

## Preparer une trajectoire de bascule

1. Creer les entites Azure et les roles de moindre privilege dans un environnement isole.
2. Adapter le consommateur et valider les schemas, l'idempotence et les erreurs.
3. Arreter ou detacher le consommateur historique pour le perimetre migre.
4. Lancer le pont et observer les transferts, redeliveries et delais.
5. Figer brievement les publications historiques si une reconciliation exacte est requise.
6. Drainer le backlog et les messages non acquittes ; comparer les identifiants et totaux.
7. Basculer le producteur de ce perimetre vers Azure, via une configuration controlee.
8. Observer pendant une periode convenue, puis retirer le pont et decommissionner la source.

Deux consommateurs sur la meme queue RabbitMQ se partagent le travail.
Pour une lecture parallele ou un shadow test, prevoyez une copie vers une file
dediee via des bindings adaptes, pas un second consommateur sur la file originale.
Ne traitez pas une dual publication naive comme une transaction atomique :
un outbox et une reconciliation sont necessaires selon le risque metier.

## Ajouter les garanties de production

* Inbox/idempotence durable autour des effets metier et outbox cote publication
* Schema versionne, validation et politique explicite pour les messages non migrables
* Retries bornes avec backoff, timeouts et politique de DLQ/replay auditable
* Renouvellement des locks pour les traitements longs et gestion des locks perdus
* Limites de taille et debit, tests de charge, prefetch et concurrence mesures
* Supervision des erreurs, age du backlog, locks perdus, redeliveries et DLQ
* Identites managees pour les composants Azure et RBAC par composant
* Decision reseau explicite, controle des acces et exigences de conformite
* Plan de reprise, couts, SLA et choix Standard/Premium selon les exigences

Le retry du transport SDK ne remplace pas le retry metier.
Un message peut etre traite puis relivre si son `complete` n'a pas ete confirme.
La fenetre de deduplication de dix minutes ne couvre pas tous les replays,
et n'empeche pas deux effets metier si le consommateur n'est pas idempotent.
Le `HashSet` en memoire de la demo est un detecteur de doublons du test,
pas une inbox durable ni une garantie apres redemarrage.

## Fixer les criteres de validation

Convenez des seuils avec le client : taux de perte admissible, duplications
tolerees, p95/p99, age maximum du backlog, erreurs, debit et duree de reprise.
Pour une commande de paiement, aucun effet duplique peut etre un critere metier,
mais il doit etre prouve au niveau de la base ou du service de paiement,
pas deduit de la seule deduplication du broker.

Avant la bascule, testez une panne reseau lors du send, une coupure apres send
avant ACK, un redemarrage du consommateur apres effet metier avant complete,
des messages sans ID, des messages invalides et un replay hors fenetre.
Le laboratoire fourni ne couvre automatiquement que la coupure controlee du pont,
la redelivery/deduplication, un abandon transitoire, une DLQ explicite et la bascule native.

## Prevoir le rollback

Avant le drainage final, documentez ou les nouveaux messages arrivent et qui peut les traiter.
Apres la bascule producteur, les messages Azure ne reviennent pas automatiquement
dans RabbitMQ. Un rollback demande un gel, une reconciliation et eventuellement
un flux inverse dedie avec conservation des identifiants.
N'alternez pas les deux producteurs sans maitriser l'ordre et les doublons.

Conservez la source jusqu'a validation du metier. Separez la suppression des ressources
de la decision de bascule et faites approuver le nettoyage des donnees.
