# Cahier des charges — Spécimens hors du dépôt

**Produit :** Recogniz-Me, Identity & Document Verification  
**Version du document :** 1.1  
**Date :** 21 novembre 2026  
**Statut :** spécification d’implémentation  
**Moteur :** `template-1`  
**Documents liés :**

- [`cahier-des-charges-authenticite-visuelle.md`](./cahier-des-charges-authenticite-visuelle.md) — gabarit, mesure, verdict
- [`../../render.yaml`](../../render.yaml) — le service API reçoit déjà le bucket et les clés S3 au démarrage du conteneur

---

## 1. Objet

Garder les photos de spécimen hors du dépôt Git, et les rendre disponibles au processus qui vérifie les pièces, en local comme sur Render.

Le fichier COCO reste la géométrie versionnée avec le code. La photo n’est plus une ressource du JAR. Elle est un objet privé, épinglé par une somme SHA-256. Le processus la dépose dans un cache disque au démarrage, puis ne décode que le gabarit dont il a besoin.

## 2. Pourquoi ce chantier

Aujourd’hui `TemplateRegistry` lit `reference.jpg` (ou `reference.png`) et `annotations.coco.json` sur le classpath, au démarrage, et garde l’image décodée. Les quatre spécimens sont donc dans Git et dans l’image Docker.

Ce montage tient pour quatre faces. Il ne tient plus quand le catalogue grandit : les binaires n’ont pas à suivre chaque commit, et cinq cents images décodées d’un coup occuperaient environ 2 Go de mémoire vive. Les plateformes d’IDV séparent le moteur du catalogue de spécimens. Le code porte le catalogue et les sommes. Le stockage objet porte les photos.

La construction Docker sur Render ne voit que le dépôt, et les clés `KYC_AWS_S3_*` n’existent qu’au lancement du conteneur. Le téléchargement se fait donc au démarrage du processus, pas pendant `docker build`.

## 3. Périmètre

**Dans ce chantier**

- Retirer `reference.jpg` et `reference.png` du dépôt et du JAR.
- Épingler chaque photo par `reference.sha256`, versionné à côté du COCO.
- Déposer les photos dans le bucket déjà utilisé par l’API, sous un préfixe réservé aux spécimens.
- Au démarrage, remplir un cache disque et refuser de démarrer si une somme ne correspond pas.
- Décoder l’image au moment de la mesure, et n’en garder que huit en mémoire.
- Faire le même remplissage avant `mvn test` dans GitHub Actions.
- Laisser les photos synthétiques de test hors de Git et hors du bucket : le test les reconstruit.

**Hors de ce chantier**

| Sujet | Sort |
|---|---|
| Mesure, redressement, verdict | Inchangés. Ils reçoivent la même image qu’aujourd’hui. |
| Photos des dossiers (`kyc/…`) | Restent sur `ObjectStoragePort` et le préfixe média. |
| Banc Swagger, parcours, décision | Inchangés. Ils passent toujours par `TemplateRegistry.find`. |
| Publication d’une édition depuis la console | Plus tard. Ce chantier dépose les objets à la main, ou par une commande de déploiement. |
| Liveness, Document IA | Hors sujet. |

## 4. Ancrage dans le code actuel

| Élément | Aujourd’hui | Devenir |
|---|---|---|
| `TemplateRegistry` | Construit sans dépendance. Lit image et COCO sur le classpath. Décode tout de suite. | Lit le COCO sur le classpath. Lit l’image dans le cache disque. Décode au premier `find` qui sert une mesure. |
| `readDirectory` | Lit un dossier déjà complet. Les tests de registre s’en servent. | Inchangé. |
| `AuthenticityTemplate.reference` | Image décodée dès le chargement, gardée sans limite. | Image décodée à la mesure. Au plus huit images retenues. |
| `S3ObjectStorage` | Préfixe `kyc/` pour les dossiers. | Les spécimens n’empruntent pas ce préfixe. |
| `api/Dockerfile` | `mvn package` puis le JAR. | Inchangé. Aucun secret de construction. |
| Render | Clés S3 présentes quand le conteneur tourne. | Le premier démarrage après déploiement remplit le cache. |
| `SyntheticSpecimenTest` | Écrit `positive.jpg` et `negative.jpg` sous `src/test/resources`. | Les écrit dans un dossier de build, pas dans Git. |

## 5. Catalogue

Un gabarit publié dans le JAR contient exactement deux fichiers :

```
authenticity/{DOSSIER}/
  annotations.coco.json
  reference.sha256
```

`{DOSSIER}` reste `CODE_VERSION_FACE`, comme aujourd’hui. Le triplet `code:version:side` n’est pas dans le nom de fichier.

`reference.sha256` contient une seule ligne : les 64 caractères hexadécimaux, en minuscules, de la somme SHA-256 de la photo. Pas de nom de fichier sur cette ligne. Pas d’autre champ.

Le nom de l’objet et du fichier de cache est `reference.jpg` ou `reference.png`, selon l’octet que la somme désigne. Un dossier n’a qu’une somme, donc qu’une photo.

Un COCO sans somme, ou une somme qui n’est pas 64 hexadécimaux, empêche le démarrage. Le message nomme le dossier.

## 6. Objet et cache

### 6.1 Objet

Même bucket et mêmes clés que le stockage des dossiers (`KYC_AWS_S3_BUCKET`, `KYC_AWS_S3_ACCESS_KEY_ID`, `KYC_AWS_S3_SECRET_ACCESS_KEY`, région S3).

Clé d’objet, sans le préfixe média `kyc/` :

```
templates/authenticity/{DOSSIER}/{sha256}.jpg
templates/authenticity/{DOSSIER}/{sha256}.png
```

L’extension suit le contenu. La somme fait partie de la clé : une ancienne photo reste lisible après la publication de la suivante. Un retour à un commit précédent retrouve son objet.

Le préfixe `templates/` ne reçoit aucune photo de vérification. Le préfixe `kyc/` ne reçoit aucun spécimen.

### 6.2 Cache disque

Répertoire par défaut : le répertoire temporaire de la JVM, sous `kyc-specimens/`. Une propriété `kyc.authenticity.specimen-cache` peut le remplacer. Il n’est pas dans le dépôt.

```
{cache}/{DOSSIER}/{sha256}.jpg
```

Le disque de Render est éphémère. Chaque instance nouvelle télécharge une fois. L’instance déjà en vie réutilise son cache tant que les sommes du JAR ne changent pas.

Au démarrage, pour chaque dossier du classpath :

1. La somme du cache égale `reference.sha256` : ne pas télécharger.
2. Sinon, télécharger l’objet, vérifier la somme, puis remplacer le fichier de cache de façon atomique.
3. Somme différente, objet absent, ou lecture impossible : le processus s’arrête. Le contrôle de santé ne passe pas.

Une panne du stockage alors que le cache a déjà la bonne somme ne bloque pas le démarrage. Une panne alors que le cache est vide ou périmé le bloque.

Les fichiers de cache dont la somme n’est plus dans le JAR peuvent rester. Ils ne sont plus lus.

## 7. Mémoire

Le démarrage ne décode aucune photo. Il vérifie les sommes et la présence des fichiers sur le disque du conteneur.

Une photo de 1280×813 décodée occupe environ 4 Mo dans la mémoire vive du processus Java. Le fichier JPEG du cache disque ne change pas de taille. Ce n’est ni Redis, ni une cache Render.

La mémoire ne garde que **huit** images décodées, soit environ 32 Mo. Ce plafond est une constante du moteur, la même pour tous les gabarits. Au-delà, l’image décodée la plus ancienne est oubliée. Elle reste sur le disque : la prochaine mesure de ce gabarit la relit et la décode de nouveau.

Seule la pièce en cours de mesure est décodée. Les copies de travail du redressement et de la comparaison sont libérées à la fin de la mesure. Lister les gabarits ne décode rien.

| Images décodées retenues | Mémoire |
|---|---|
| 8, le plafond | environ 32 Mo |
| 100, sans plafond | environ 400 Mo |
| 1 000, sans plafond | environ 4 Go |

Sur un plan à 512 Mo, le plafond de huit laisse la place à l’application. Sans plafond, quelques dizaines d’éditions mesurées par la même instance rempliraient la mémoire vive.

## 8. Local, test, déploiement

| Lieu | Photos dans Git | Comment elles arrivent |
|---|---|---|
| Poste de développement | Non | Même téléchargement qu’en production, avec les clés du fichier de secrets. |
| `mvn test` sur GitHub Actions | Non | Une étape télécharge le catalogue dans le cache avant les tests, avec le secret du dépôt. |
| Image Docker / Render | Non | Le JAR n’a que les COCO et les sommes. Le conteneur télécharge au démarrage. |

Les tests qui fabriquent un dossier avec `readDirectory` ne passent pas par le stockage. Les tests qui chargent les gabarits livrés ont besoin du cache rempli. Sans secret et sans cache, ces tests échouent. Ils ne sont pas ignorés.

`SyntheticSpecimenTest` reconstruit `positive.jpg` et `negative.jpg` à partir des spécimens du cache, dans `target/`. Ces fichiers ne sont ni commités ni envoyés dans `templates/`.

Retirer les JPEG déjà suivis par Git fait partie de la livraison : `git rm --cached` des `reference.jpg`, et une règle d’ignorance pour `api/src/main/resources/authenticity/**/reference.jpg`, `reference.png`, et les JPEG synthétiques sous `src/test/resources`.

## 9. Publication d’un spécimen

Pour remplacer ou ajouter une face :

1. Calculer la somme SHA-256 de la photo.
2. Écrire l’objet `templates/authenticity/{DOSSIER}/{sha256}.jpg` (ou `.png`).
3. Déposer `annotations.coco.json` et `reference.sha256` dans le JAR.
4. Déployer. L’ancienne clé reste dans le bucket tant qu’un commit encore déployable la cite.

La photo ne contient pas de titulaire réel : pas de nom, de numéro, ni de portrait d’une personne. Un spécimen ou une carte de test seulement.

## 10. Critères d’acceptation

1. Un clone sans les JPEG démarre dès que le cache a été rempli, en local et sur Render, sans modifier `api/Dockerfile`.
2. Le JAR déployé ne contient aucun `reference.jpg` ni `reference.png`.
3. Une somme qui ne correspond pas à l’objet empêche le démarrage, et le message nomme le dossier.
4. Un cache déjà juste permet le démarrage même si le stockage ne répond plus.
5. Un cache vide et un stockage muet empêchent le démarrage. `/actuator/health` ne répond pas prêt.
6. Les quatre gabarits livrés donnent les mêmes verdicts qu’avant sur leurs spécimens et sur les photos synthétiques positive et négative.
7. Seul le gabarit mesuré est décodé. Au plus huit images décodées restent en mémoire. La neuvième fait oublier la plus ancienne, qui est relue depuis le cache disque à la prochaine mesure. Lister les gabarits ne décode pas les photos.
8. Aucune clé d’objet sous `templates/` ne commence par le préfixe média `kyc/`, et aucune photo de dossier n’est écrite sous `templates/`.
9. Les JPEG de spécimen et les photos synthétiques n’apparaissent plus dans `git ls-files`.
