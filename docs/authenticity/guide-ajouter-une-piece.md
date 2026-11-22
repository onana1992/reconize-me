# Ajouter une pièce au catalogue d’authenticité

Ce guide ajoute une face (recto ou verso) au moteur `template-1`. La photo ne va pas dans Git. Le dépôt ne reçoit que la géométrie et la somme. Le processus télécharge la photo au démarrage.

La photo est un spécimen ou une carte de test. Elle ne porte ni nom, ni numéro, ni portrait d’une personne réelle.

## 1. Nommer le dossier

Un dossier par face :

```
api/src/main/resources/authenticity/{CODE}_{VERSION}_{FACE}/
```

`{FACE}` vaut `FRONT` ou `BACK`. Exemple de nom : `ONTARIO_DRIVER_LICENSE_2018_FRONT`. Ce nom devient l’identifiant `ONTARIO_DRIVER_LICENSE:2018:FRONT`.

## 2. Déposer la géométrie

Dans ce dossier :

- `annotations.coco.json` — une seule image, une annotation par catégorie, les rôles déjà compris par le moteur (`document`, `mark`, `fixed`, `value`, `region`).
- rien d’autre qui soit une photo.

Le fichier COCO décrit la largeur et la hauteur de la photo. Ces deux nombres devront être ceux du fichier décodé, orientation EXIF `1` ou absente.

## 3. Calculer la somme

À côté du COCO, sur le poste seulement, placez `reference.jpg` (ou `reference.png`, un seul des deux).

PowerShell, depuis `api/` :

```powershell
Get-FileHash -Algorithm SHA256 src/main/resources/authenticity/ONTARIO_DRIVER_LICENSE_2018_FRONT/reference.jpg |
  ForEach-Object { $_.Hash.ToLower() } |
  Set-Content -NoNewline -Encoding ascii src/main/resources/authenticity/ONTARIO_DRIVER_LICENSE_2018_FRONT/reference.sha256
```

`reference.sha256` est une seule ligne de 64 caractères hexadécimaux en minuscules. Pas de nom de fichier sur cette ligne. Ce fichier-là est versionné.

## 4. Envoyer la photo dans le bucket

Même bucket que les dossiers, autres clés. Le préfixe média `kyc/` ne reçoit pas de spécimen.

```
templates/authenticity/{DOSSIER}/{sha256}.jpg
```

L’extension est `.png` si le fichier est un PNG. La somme dans la clé est celle du fichier envoyé, identique à `reference.sha256`.

Exemple, avec les variables déjà utilisées par l’API (`KYC_AWS_S3_BUCKET`, clés, région) :

```powershell
aws s3 cp src/main/resources/authenticity/ONTARIO_DRIVER_LICENSE_2018_FRONT/reference.jpg `
  "s3://$env:KYC_AWS_S3_BUCKET/templates/authenticity/ONTARIO_DRIVER_LICENSE_2018_FRONT/$somme.jpg" `
  --region $env:KYC_AWS_S3_REGION
```

`$somme` est le contenu de `reference.sha256`. L’objet précédent, s’il y en a un, reste dans le bucket : un ancien commit le retrouve encore.

## 5. Ce qui est commité

Commités :

- `annotations.coco.json`
- `reference.sha256`

Ignorés par Git, et exclus du JAR :

- `reference.jpg`
- `reference.png`

Sur le poste, vous pouvez laisser le JPEG à côté du COCO. Si sa somme est celle du fichier `reference.sha256`, le démarrage le copie dans le cache et n’appelle pas le bucket. Un clone sans ce JPEG démarre quand même, dès que les clés S3 sont là : le processus télécharge l’objet.

## 6. Démarrage

Le cache par défaut est le répertoire temporaire de la JVM, sous `kyc-specimens/{DOSSIER}/{sha256}.jpg`. Pour le déplacer :

```
kyc.authenticity.specimen-cache=C:\chemin\cache
```

ou la variable `KYC_AUTHENTICITY_SPECIMEN_CACHE`.

Au démarrage, chaque dossier du classpath est vérifié. Somme du cache déjà bonne : pas de téléchargement. Sinon : téléchargement, vérification, remplacement atomique. Somme fausse, objet absent, ou lecture impossible : le processus s’arrête et le message nomme le dossier. `/actuator/health` ne passe pas prêt.

Le démarrage ne décode pas les photos. La première mesure d’une face décode son fichier. Au plus huit images décodées restent en mémoire. La neuvième fait oublier la plus ancienne. Elle est relue depuis le disque à la prochaine mesure de cette face.

Après un changement Java ou un nouveau `reference.sha256`, redémarrer l’API lancée par l’IDE (répertoire `api/`, classe `com.kyc.RecognizMeApplication`). Le Dockerfile ne change pas. Sur Render, les clés `KYC_AWS_S3_*` existent déjà au lancement du conteneur : le premier démarrage remplit le cache du disque éphémère.

## 7. Tests

`mvn test` charge les gabarits livrés. En local, le JPEG à côté du COCO suffit. Sur GitHub Actions, le job API a besoin des secrets du dépôt :

- `KYC_AWS_S3_BUCKET`
- `KYC_AWS_S3_REGION`
- `KYC_AWS_S3_ACCESS_KEY_ID`
- `KYC_AWS_S3_SECRET_ACCESS_KEY`

Sans ces secrets et sans JPEG local, les tests qui chargent les gabarits livrés échouent. Ils ne sont pas ignorés.

Les photos synthétiques de test, si vous en générez, vont dans `api/target/`. Elles ne sont ni commitées ni envoyées sous `templates/`.
