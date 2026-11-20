# Prompt d’annotation COCO

À coller dans Claude avec la photo du spécimen. Le même texte sert pour toute pièce et toute face. Claude mesure lui-même la taille de l’image.

Le JSON produit s’appelle `annotations.coco.json`. Il se range avec `reference.jpg` ou `reference.png`, aux mêmes dimensions, dans `api/src/main/resources/authenticity/CODE_VERSION_FACE/`. La face est `FRONT` ou `BACK`.

```text
Tu produis un seul fichier COCO pour la photo jointe. C’est le spécimen d’une édition et d’une face d’une pièce d’identité. Mesure toi-même sa largeur et sa hauteur en pixels, et écris ces deux entiers dans images[0].width et images[0].height. Toutes les boîtes sont dans ce repère : origine en haut à gauche, x vers la droite, y vers le bas. N’invente pas une autre taille et ne suppose pas de quelle pièce il s’agit : annote ce que tu vois.

Réponds uniquement avec le JSON, sans markdown et sans commentaire.

But : gabarit de vérification visuelle. Le moteur ne lit pas le texte. Il vérifie que l’encre des champs variables est à la bonne place, et que les éléments imprimés identiques sur chaque pièce de cette édition ressemblent au spécimen. Ne transcris aucun nom, numéro, date, adresse ou contenu de code.

Structure exacte :

{
  "info": {
    "description": "Gabarit de vérification de la face photographiée. Sans transcription.",
    "version": "2.0",
    "bbox_format": "[x, y, largeur, hauteur] en pixels, origine en haut à gauche, pour bbox, attributes.bbox_value et attributes.bbox_label",
    "roles": {
      "document": "Rectangle de la pièce sur la référence. Origine de la projection.",
      "fixed": "Ressemblance du crop (bbox) avec la référence.",
      "value": "Position de attributes.bbox_value. attributes.bbox_label (si présent) est traité comme fixed.",
      "region": "Position et présence. Pas de comparaison du contenu personnel."
    }
  },
  "licenses": [],
  "images": [{ "id": 1, "file_name": "reference.jpg", "width": 0, "height": 0 }],
  "categories": [],
  "annotations": []
}

Remplace width et height par la taille réelle de la photo jointe.

Règles qui font refuser le fichier s’il les viole :

- Une seule image. Une seule catégorie de rôle "document". Une annotation par catégorie, et autant d’annotations que de catégories.
- Rôles autorisés : document, fixed, value, region. Le champ de la catégorie s’appelle "role". N’utilise pas "supercategory".
- Noms de catégories en snake_case, uniques, descriptifs de la zone, sans donnée personnelle.
- Chaque annotation : id, image_id = 1, category_id, bbox [x, y, largeur, hauteur] d’entiers, area = largeur × hauteur, iscrowd = 0, segmentation = le rectangle [x, y, x+largeur, y, x+largeur, y+hauteur, x, y+hauteur].
- Toute bbox est à l’intérieur de l’image, avec largeur et hauteur > 0.
- Toute bbox autre que document est à l’intérieur de la bbox document.
- attributes n’existe que sur les catégories value. Les rôles document, fixed et region n’ont pas d’attributes.
- Pour un value : attributes.bbox_value est le rectangle de l’encre qui change d’un titulaire à l’autre. attributes.bbox_label est le libellé imprimé identique sur chaque pièce, ou null s’il n’y a pas de libellé. Les deux, quand ils ne sont pas null, sont à l’intérieur de la bbox parente. La bbox parente englobe le libellé et la valeur.
- fixed : imprimé identique sur chaque pièce de cette édition (logo, titre, phrase fixe, symbole, filet).
- value : champ dont seule la position de l’encre compte.
- region : zone dont on vérifie la présence et la place, sans comparer son contenu au spécimen (portrait, signature, code-barres, puce).
- Commence par document : le rectangle de la pièce seule, sans le fond de la photo. Puis une catégorie et une annotation pour chaque bloc distinct visible.
```
