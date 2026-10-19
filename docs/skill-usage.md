---
title: "Utiliser le skill de migration avec Copilot CLI et VS Code"
description: "Installer et essayer le skill local de migration Java RabbitMQ vers Service Bus sans GitHub App."
---

## Perimetre

Le skill `rabbitmq-to-servicebus` accompagne l'analyse et l'adaptation d'une application
Java ou Spring existante. Il commence en lecture seule et demande un plan approuve
avant modification. Il n'est pas un convertisseur automatique de brokers.
Il n'utilise ni GitHub App, ni agent cloud, ni delegation distante.

Le skill est fourni dans `.github\skills\rabbitmq-to-servicebus`.
Pour le parcours complet, de l'installation a la bascule, suivez
le [parcours B : migration de votre application](migration-step-by-step.md).
Pour executer uniquement la demonstration, utilisez le
[parcours A : laboratoire](lab-step-by-step.md).

## Installer dans le depot de l'application

Copiez le dossier `rabbitmq-to-servicebus` entier, y compris ses trois references,
depuis `.github\skills` de ce depot vers `.github\skills` de l'application cliente.
Les commandes de copie, avec refus d'ecrasement, sont dans l'etape 1 du parcours B.
Examinez le contenu avant de l'utiliser.
Il ne contient ni script executable ni permission preapprouvee.
La compatibilite de ce format est documentee pour Copilot CLI et le mode Agent
de VS Code ; cela ne signifie pas qu'un essai natif VS Code a ete effectue.

## Copilot CLI

Depuis le depot client, lancez :

```powershell
copilot
```

Dans la session interactive :

```text
/skills reload
/skills info rabbitmq-to-servicebus
```

Demandez ensuite :

```text
Utilise le skill /rabbitmq-to-servicebus pour analyser cette application Java.
Reste en lecture seule. Cite les fichiers et lignes, identifie les incompatibilites
et propose un plan. Ne lance pas de commande, ne modifie rien et ne contacte pas Azure.
```

Conservez les confirmations d'outils. Pour imposer un essai en lecture seule,
une liste d'outils limitee aux lectures et au chargement du skill est plus forte
que la seule consigne dans le prompt.
L'evaluation locale a utilise cette configuration :

```powershell
copilot --available-tools skill view glob rg --allow-all-tools --deny-tool shell --deny-tool write --disable-mcp-server '*' --no-remote --no-remote-export -p "Utilise le skill /rabbitmq-to-servicebus. Analyse le code en lecture seule, cite les preuves et propose un plan. Ne lance aucune commande ni action externe."
```

Ici `--allow-all-tools` n'expose pas de nouveaux outils : la liste `--available-tools`
limite la session aux quatre outils de lecture/chargement indiques.
Ne reutilisez pas ce flag seul pour une migration : il autoriserait les outils
disponibles sans confirmation.

## VS Code

Ouvrez le depot client avec une version de Copilot prenant en charge les Agent Skills,
puis ouvrez Copilot Chat en mode Agent. Utilisez le meme prompt explicite
`Utilise le skill /rabbitmq-to-servicebus...`.
Verifiez dans les traces du chat que le skill a ete charge avant d'evaluer sa reponse.
Les commandes interactives `/skills reload` et `/skills info` ci-dessus concernent
le CLI ; ne supposez pas leur presence dans VS Code.
Gardez les approbations d'outils actives et n'autorisez pas de terminal ou de
modification pendant un audit qui doit rester en lecture seule.

## Reconnaitre un resultat utile

Le resultat doit citer les declarations et les flux observes, distinguer les inconnus
de l'etat deploye et proposer des tests pour les ecarts. Un message sans identifiant
stable, des priorites ou un binding RabbitMQ ne doivent pas devenir une migration
Service Bus annoncee comme terminee.

Le code genere, les tests unitaires, l'integration, le mouvement de messages et
la bascule sont des etapes distinctes. Une reponse correcte a un audit ne prouve pas
que ces autres etapes fonctionnent.
Les essais locaux portent sur le chargement natif CLI, une analyse de fixture Java,
l'arret avant bascule et un cas sans RabbitMQ. Une comparaison sur quatre modeles
a aussi revele des surinterpretations de configuration externe et des citations
imprecises ; les consignes ont ete precisees pour ces cas. Cela ne constitue pas
une garantie de justesse de chaque reponse.
Aucune validation native VS Code, adaptation automatique de code ou migration
client reelle n'est revendiquee. La demo Java executee sur de vrais brokers est
un test distinct du code que Copilot pourrait generer pour une autre application.

## References

* [Agent Skills et clients compatibles](https://docs.github.com/en/copilot/concepts/agents/about-agent-skills)
* [Installer et utiliser un skill dans Copilot CLI](https://docs.github.com/en/copilot/how-tos/copilot-cli/customize-copilot/add-skills)
