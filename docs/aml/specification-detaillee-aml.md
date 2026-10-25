# Spécification détaillée — AML Screening

**Produit :** Recogniz-Me, AML Screening  
**Version du document :** 1.0  
**Date :** 19 octobre 2026  
**Statut :** spécification pédagogique — le contrat expliqué  
**Document qui prime :** [`cahier-des-charges-aml.md`](./cahier-des-charges-aml.md)  
**Ordre de construction :** [`roadmap-implementation-aml.md`](./roadmap-implementation-aml.md)

Ce texte s’adresse à trois lecteurs : l’intégrateur qui appelle l’API, l’analyste qui qualifie une correspondance, et la personne qui implémente le moteur. Il reprend le cahier des charges et le raconte dans l’ordre où on s’en sert. Les chiffres, les codes et les issues sont ceux du contrat. Si une phrase d’ici et une ligne du cahier se contredisent, le cahier gagne.

---

## 1. L’idée en une page

Une organisation a un client. Elle veut savoir si ce nom figure sur une liste qui l’oblige à s’arrêter : sanctions, personne politiquement exposée, proche d’une telle personne, déchéance.

Elle envoie le nom, et ce qu’elle connaît d’autre (date de naissance, nationalité, numéro de pièce). Elle reçoit, dans la même réponse, un dossier déjà tranché.

Trois réponses possibles.

| Ce que le client lit | Ce que cela veut dire pour lui |
|---|---|
| `clear` | Rien au-dessus du seuil. Il peut continuer son parcours, selon sa propre politique. |
| `potential_match` | Quelqu’un sur une liste ressemble à ce nom. Un humain doit regarder. |
| `confirmed_match` | La ressemblance est assez forte, et la catégorie est une sanction, ou un analyste a dit « c’est bien cette personne ». |

Une quatrième situation existe, et ce n’est pas une quatrième réponse : `status = failed`. Le moteur n’a pas pu conclure. Le dossier est vide. Continuer comme si la personne était écartée serait une erreur.

Le produit ne pousse rien après coup. Créer un criblage renvoie le dossier. Qualifier une correspondance renvoie le dossier mis à jour. Relire se fait avec `GET`.

Deux moteurs se succèdent derrière la même porte.

| | Version 1, `aml-1` | Version 2, `aml-2` |
|---|---|---|
| Quand | Dès maintenant, sur une clé de test | Quand le live s’ouvre |
| Où sont les personnes listées | Quatre fiches inventées, publiées dans le cahier | L’index OpenSanctions, collection `default` |
| Qui calcule le score | Recogniz-Me | OpenSanctions |
| Qui décide `clear` / `potential_match` / `confirmed_match` | Recogniz-Me | Recogniz-Me |
| Ce qu’un `clear` prouve | Le nom n’atteint pas le seuil dans le corpus d’exercice | Le nom n’atteint pas le seuil dans l’index lu à cet instant |

Un `clear` de la version 1 ne dit pas que la personne est absente des listes officielles. Le corpus d’exercice sert à brancher l’appel, à vérifier qu’on lit bien `outcome`, et à rejouer les mêmes résultats demain.

## 2. Les mots

| Mot | Sens dans ce produit |
|---|---|
| Sujet | La personne envoyée par le client : prénom, nom, et le reste s’il l’a. |
| Entité | Une fiche de personne listée. Dans l’exercice, `fx-sanction-ivan`. Chez OpenSanctions, leur identifiant stable. |
| Correspondance (`hit`) | Une entité dont le score atteint le seuil de revue. |
| Score | Un rang entre 0 et 1. 0,910 se lit « ressemblance haute ». Cela ne se lit pas « 91 % de chances que ce soit la même personne ». |
| Trait (`feature`) | La raison lisible du score : nom exact, année différente, numéro identique. |
| Issue (`outcome`) | `clear`, `potential_match` ou `confirmed_match`. |
| Statut (`status`) | `completed` si l’issue existe. `failed` si le moteur n’a pas conclu. |
| Politique | Les seuils et les catégories choisis par l’organisation. Figés sur le dossier au moment de l’appel. |
| Qualification | Le geste de l’analyste : faux positif ou vrai positif, avec un motif. |
| Corpus | L’ensemble des fiches contre lesquelles on compare. `fixture-1` puis l’index OpenSanctions. |

Sanction, PEP, RCA et déchéance ne disent pas la même chose.

| Catégorie | En une phrase | Confirmation automatique |
|---|---|---|
| `sanction` | La personne figure sur une liste de sanctions ou un gel d’avoirs. | Oui, si le nom et un second indice fort concordent. |
| `pep` | La personne occupe, ou a occupé, une fonction politique exposée. Être PEP n’est pas une infraction. | Jamais. Un humain regarde. |
| `rca` | Proche ou apparenté d’une PEP. | Jamais. |
| `debarment` | Exclusion d’un marché, d’une profession, d’un registre. | Jamais. |
| `crime` | Notice criminelle portée par la source. Inactive tant que l’organisation ne l’allume pas. | Jamais. |

L’ordre d’affichage, quand une fiche porte plusieurs catégories, est : sanction, déchéance, crime, PEP, proche. Une personne à la fois sanctionnée et PEP s’affiche d’abord comme une sanction. C’est la sanction qui peut se confirmer seule.

## 3. Ce qui se passe pendant un appel

```
clé API
  → le corps est-il un personne avec un prénom et un nom ?
  → cette clé a-t-elle le droit de cribler maintenant ?
  → figer la politique active
  → comparer le sujet au corpus du moteur
  → garder au plus 10 correspondances au seuil de revue
  → poser l'issue
  → enregistrer le dossier
  → renvoyer ce dossier dans la réponse
```

Rien n’est renvoyé tant que cette chaîne n’est pas finie. L’intégrateur attend la réponse. En version 1, l’attente est celle d’un calcul local. En version 2, il prévoit 30 secondes : l’appel OpenSanctions a 8 secondes, une seule reprise, puis on conclut.

Avant même le calcul, l’appel peut s’arrêter sans créer de dossier.

| Situation | Réponse |
|---|---|
| Prénom ou nom absent, initiale seule, date impossible, type autre que `person` | `400`, pas de dossier |
| Clé absente, révoquée, ou clé d’un autre produit | `401` |
| Intégration live alors que `aml-2` n’est pas en service | `409` `engine_unavailable` |
| Live sans solde, moteur `aml-2` en service | `403` `insufficient_credit` |
| Même `Idempotency-Key` qu’un appel déjà fait, autre corps | `409` `idempotency_key_conflict` |

Si le corps est valide et que le moteur échoue ensuite (version 2 seulement), le dossier est créé quand même. La réponse est `201`, avec `status = failed` et `outcome` nul. Le `201` veut dire « le dossier existe ». Il ne veut pas dire « la personne est écartée ».

## 4. Lire la réponse

Prenons l’appel d’exercice le plus net : Ivan Sanctionexample, né le 12 avril 1971.

```json
{
  "status": "completed",
  "outcome": "confirmed_match",
  "failure_reason": null,
  "engine": "aml-1",
  "policy_version": "aml-policy-0",
  "corpus_version": "fixture-1",
  "hits": [
    {
      "entity_id": "fx-sanction-ivan",
      "category": "sanction",
      "score": 1.0,
      "features": ["name_exact", "birth_date_exact"],
      "disposition": "unreviewed"
    }
  ]
}
```

On lit dans cet ordre.

1. `status`. S’il vaut `failed`, on s’arrête. `failure_reason` dit pourquoi. On n’ouvre pas le compte.
2. `outcome`. Ici `confirmed_match` : une sanction est confirmée par la règle, avant même qu’un analyste parle.
3. `engine` et `corpus_version`. Ici on est dans l’exercice. Le même JSON en live porterait `aml-2` et `opensanctions`.
4. `hits`. Une ligne par personne listée retenue. `features` dit pourquoi le score est là. `datasets` dira, en live, le nom de la liste source.
5. `disposition`. `unreviewed` tant qu’aucun analyste n’a qualifié la ligne.

`external_id` est la référence du client (`customer-42`). Elle n’est pas unique : le même client peut être criblé plusieurs fois. Elle sert à retrouver les dossiers, pas à les dédupliquer.

## 5. Le score, sur trois cas

Le seuil de revue par défaut est 0,70. En dessous, la fiche n’apparaît pas. Le seuil de confirmation par défaut est 0,95. Au-dessus, une sanction peut se confirmer seule, si un second indice fort est là.

Les deux seuils se règlent. L’organisation peut descendre la revue jusqu’à 0,50 (plus de dossiers à regarder). Elle ne peut pas la monter au-delà de 0,85. La confirmation reste entre 0,90 et 1, et toujours au-dessus de la revue. La catégorie sanction ne s’éteint pas.

### 5.1 Ivan, date exacte

Le nom normalisé `IVAN SANCTIONEXAMPLE` égale la fiche. La date égale `1971-04-12`.

Score de nom = 1. Facteur de date = 1. Score affiché = 1,000.

Traits : `name_exact`, `birth_date_exact`. Catégorie sanction. Score au-dessus de 0,95. Les deux traits forts sont là.

Issue : `confirmed_match`.

### 5.2 Ivan, autre année

Même nom, date `1999-01-01`. Le facteur d’années différentes est 0,15.

Score = 1 × 0,15 = 0,150. Sous 0,70 : la fiche n’est pas une correspondance.

Issue : `clear`. Aucune ligne dans `hits`.

C’est voulu. Une homonymie avec vingt-huit ans d’écart ne doit pas bloquer un dossier. L’analyste ne voit pas cette fiche, parce qu’elle n’a pas passé le seuil.

### 5.3 Ivan, sans date

Même nom, pas de date envoyée. Le facteur de date absente vaut 1. Le score reste 1,000. Le trait est `birth_date_absent`.

La confirmation automatique demande une date complète identique, ou un numéro de pièce identique. Ici les deux manquent.

Issue : `potential_match`. La sanction est visible. Elle attend un humain.

### 5.4 Une lettre en moins

`Ivan Sanctionexampl`, date exacte. Le jeton `SANCTIONEXAMPL` a au moins 5 lettres et une seule lettre d’écart avec `SANCTIONEXAMPLE`. Le moteur les apparie. Le score de nom vaut encore 1. La date est exacte.

Le cahier traite ce cas comme un nom exact au sens du calcul de jetons, et l’issue de référence est `confirmed_match`. Une initiale, elle, ne bénéficie pas de cet écart : un jeton de moins de 5 lettres n’apparie que s’il est identique. `IVAN` ne rencontre pas `IBAN`.

### 5.5 Le numéro de pièce

Ivan, mauvaise année, numéro `X1234567` (celui de la fiche). Le nom est reconnaissable (score de nom 1, donc au-dessus de 0,50) et le numéro égale. Le score est relevé à 1,000. Le conflit d’année reste écrit dans les traits, pour que l’analyste le voie. L’issue est `confirmed_match`.

Le même Ivan avec le numéro `Z999` : les deux numéros existent et diffèrent, le facteur tombe à 0,20. Score 0,200. Issue `clear`.

Un numéro seul ne suffit pas. S’il est posé sur un nom sans rapport (score de nom sous 0,50), il ne confirme pas.

### 5.6 Nora, la PEP

`Nora Potentialexample`, sans date. Le nom égale la fiche. Score 1,000. Catégorie `pep`.

Une PEP ne se confirme jamais d’office, même à 1,000. Issue : `potential_match`.

Avec la nationalité `US` alors que la fiche est `FR`, le facteur est 0,90. Score 0,900. Toujours au-dessus de 0,70. La PEP reste dans la liste. Une nationalité différente affaiblit le score. Elle ne cache pas la personne.

`Nora`, nom intermédiaire `P`, nom `Example` égale l’alias `Nora P Example`. Même issue.

### 5.7 Mina, l’année seule

La fiche de Mina ne porte que l’année 1990. Le client envoie `1990-06-01`. L’année concorde, la fiche n’a pas de jour. Trait : `birth_year_match`. Score de nom 1. Catégorie déchéance, qui ne se confirme pas seule.

Issue : `potential_match`.

### 5.8 Ada, personne inconnue du corpus

`Ada Clear`, `1990-01-01`. Aucune fiche ne passe 0,70.

Issue : `clear`, tableau `hits` vide, `status = completed`. C’est un vrai écart, dans ce corpus. En version 1, ce n’est rien d’autre.

## 6. Comment le nom est comparé

Avant la comparaison, prénom, nom intermédiaire et nom sont collés, puis normalisés.

1. Les accents tombent (`É` devient `E`).
2. Tout passe en majuscules.
3. La ponctuation devient un espace. Les espaces doubles se réduisent.
4. Un numéro de pièce ne garde que les lettres et les chiffres : `X-123 4567` et `X1234567` sont le même numéro.

Le nom obtenu est comparé au nom de la fiche, puis à chaque alias. On garde la meilleure variante.

Les mots (les jetons) s’apparient un à un. Deux mots s’apparient s’ils sont égaux, ou s’ils ont tous les deux au moins 5 lettres et une seule insertion, suppression ou substitution. Chaque mot ne sert qu’une fois.

Le score de nom est `2 × mots appariés / (mots de la requête + mots de la fiche)`.

`IVAN` seul contre `IVAN SANCTIONEXAMPLE` apparie un mot sur trois côtés utiles : `2 × 1 / 3 = 0,667`, sous 0,70. Un prénom seul ne fait pas une correspondance. C’est pour cela qu’une initiale est refusée dès l’entrée : le moteur a besoin d’un nom.

Ensuite les facteurs se multiplient : date, nationalité, numéro. Le résultat est arrondi au demi-supérieur, à trois décimales, en arithmétique décimale. C’est cette valeur affichée qui est comparée aux seuils. Deux implémentations qui suivent cette règle trouvent le même score.

Le pays de résidence est gardé dans le dossier. En version 2 il est envoyé à OpenSanctions. Il ne change pas le score, et il ne retire pas une ligne.

## 7. Les deux moteurs, la même décision

```
sujet + politique figée
        │
        ├── aml-1 : corpus fixture-1, score calculé ici
        │
        └── aml-2 : POST OpenSanctions /match/default
                    score copié, traits recalculés ici
        │
        ▼
   mêmes bandes, même issue, même JSON
```

La clé `ky_test_` reste sur `aml-1` pour toujours, y compris le jour où le live existe. Elle ne contacte pas OpenSanctions et ne débite rien. Rejouer Ivan demain renvoie le même dossier, aux identifiants et à l’heure près.

La clé `ky_live_` n’existe que lorsque `aml-2` est le moteur de production. Avant cela, créer l’intégration live répond `engine_unavailable`, même si l’organisation a du crédit. Le crédit ne débloque pas un criblage d’exercice vendu comme une liste officielle.

En version 2, OpenSanctions propose des fiches et un score. Le booléen `match` de leur réponse n’est pas lu. Notre seuil de revue décide si la fiche entre. Notre seuil de confirmation et nos traits décident si une sanction se confirme seule. Une PEP à score 1 chez eux reste `potential_match` chez nous.

On leur envoie une seule personne, schéma `Person`, clé de requête `q1`. Le nom part tel que le client l’a écrit. La date part telle quelle. Nationalité et pays de résidence partent en minuscules. Les champs vides ne partent pas.

On ne garde une fiche que si c’est une personne, que OpenSanctions la marque comme cible du criblage (`target`), et qu’elle a un identifiant et un score. Si une ligne renvoyée au-dessus du seuil est illisible, le dossier entier est `failed`. On ne fabrique pas un `clear` en jetant une ligne qu’on n’a pas comprise.

Une fiche lisible dont le thème ne correspond à aucune catégorie active est ignorée. Le crime, inactif par défaut, disparaît ainsi. S’il est porté par une fiche qui est aussi une sanction, la sanction reste.

Le délai d’un appel fournisseur est de 8 secondes. Un `429` ou un `5xx` est rejoué une fois. Un refus de clé fournisseur n’est pas rejoué. Dans tous ces échecs, la réponse au client est un dossier `failed`, sans débit.

| `failure_reason` | Situation |
|---|---|
| `provider_timeout` | Les 8 secondes, ou la reprise, sont dépassées |
| `provider_unavailable` | Refus de clé, ou erreur de service après la reprise |
| `provider_invalid_response` | Corps illisible, ou fiche au-dessus du seuil sans identifiant ni score |

Un HTTP 200 avec une liste vide est un vrai `clear`.

Rejouer la décision sur les fiches déjà enregistrées redonne la même issue, sans rappeler OpenSanctions. Un nouveau `POST` relit l’index. L’index bouge. Ce nouvel appel est un nouveau dossier, avec son propre `retrieved_at`.

## 8. Qualifier, sur le dossier de Nora

Nora revient `potential_match`, une PEP, `disposition = unreviewed`.

L’analyste ouvre la fiche. Il voit le nom listé, le score, les traits, le jeu de données. Il conclut que ce n’est pas sa cliente.

```json
{
  "disposition": "false_positive",
  "reason": "different_person",
  "note": "Date de naissance incompatible avec le client."
}
```

`POST /v1/screenings/{id}/hits/{hit_id}/disposition` répond `200` avec le dossier entier. L’unique correspondance est maintenant un faux positif. L’issue recalculée est `clear`.

Les motifs sont fermés.

| Geste | Motifs |
|---|---|
| `false_positive` | `different_person`, `date_of_birth_conflict`, `nationality_conflict`, `name_only` |
| `true_positive` | `same_person`, `listed_alias` |

La note est libre, jusqu’à 500 caractères. Le motif, lui, reste un code : c’est lui qu’on peut compter.

S’il y a deux correspondances et qu’on n’en écarte qu’une, l’issue reste `potential_match` tant que l’autre n’est pas qualifiée. Un seul vrai positif, sur n’importe quelle catégorie, passe le dossier en `confirmed_match`. Une confirmation automatique écartée ensuite peut revenir à `clear` : la règle de la revue remplace alors la règle du score. L’historique des deux gestes reste dans l’audit.

Un dossier `failed` ne se qualifie pas (`409` `invalid_status`). Il n’y a rien à regarder.

Le membre, l’administrateur et le propriétaire qualifient dans la console. Le développeur branche l’API et ne qualifie pas à l’écran. La clé d’API peut qualifier : l’outil de conformité du client est un analyste autant que l’écran.

### Ce qu’un écart ne fait pas

Écarter Nora pour ce client n’enlève pas Nora de la liste. Le prochain criblage du même sujet (même nom normalisé, même date, même nationalité, même numéro, les absents comptant comme égaux) montrera `prior_disposition` et l’identifiant de l’ancien dossier. La nouvelle ligne repart `unreviewed`. L’analyste voit qu’on a déjà tranché. Le moteur ne recopie pas l’écart, et ne saute pas la fiche.

Un autre client qui s’appelle aussi Nora Potentialexample n’hérite pas de cet écart.

## 9. À côté d’une vérification d’identité

Le criblage et l’identité sont deux produits. L’identité prouve que la pièce et le visage vont ensemble. Le criblage demande si le nom lu sur la pièce est sur une liste.

On ne crible pas le nom tapé à l’ouverture de la session. On crible le nom lu sur la pièce, après une lecture réussie. S’il manque le prénom ou le nom sur la pièce, il n’y a pas de criblage : la vérification part en revue, motif `aml_unavailable`.

Le rattachement est éteint par défaut (`aml-policy-0`). L’organisation l’allume dans les règles AML, pas dans les règles d’identité, et elle choisit une des trois conduites.

| Conduite | Ce que fait la vérification |
|---|---|
| `ignore` | Elle tranche l’identité sans attendre. Le criblage est lancé s’il peut l’être. Son échec ne retient pas l’identité. |
| `review` | Elle attend le dossier. `clear` : elle continue sur ses propres règles. `potential_match` ou `confirmed_match` : elle part en revue. Échec, intégration absente, nom inexploitable : elle part en revue, motif `aml_unavailable`. |
| `block` | Elle attend aussi. `confirmed_match` : elle refuse. `potential_match` : elle part en revue. `clear` : elle continue. Échec : revue, `aml_unavailable`. |

Une PEP, qui ne se confirme jamais seule, envoie donc l’identité en revue dans les deux conduites `review` et `block`. Seule une sanction confirmée, sous `block`, refuse la vérification sans passer par un humain.

Le criblage créé appartient à l’intégration AML du même mode. Une vérification de test parle à `aml-1`. Une vérification live parlera à `aml-2`. Les deux dossiers se citent : `verification_id` d’un côté, `aml_screening_id` de l’autre. On les relit par `GET`.

Si l’analyste écarte ensuite la correspondance, le criblage peut passer à `clear`. La décision d’identité déjà rendue ne change pas. Le client relit le criblage s’il veut en tenir compte.

Chaque produit live se débite à part. Une vérification et son criblage sont deux unités.

## 10. Ce que l’intégrateur écrit

La clé est celle d’une intégration du produit `aml`, en-tête `Authorization: Bearer`. Une clé Identité sur cette route reçoit `401`.

```http
POST /v1/screenings
Authorization: Bearer ky_test_…
Idempotency-Key: screen-customer-42-001
Content-Type: application/json

{
  "external_id": "customer-42",
  "first_name": "Nora",
  "last_name": "Potentialexample",
  "date_of_birth": "1982-11-03",
  "nationality": "FR"
}
```

Il range la réponse seulement après avoir lu `status`, puis `outcome`. Il conserve `id` pour la fiche et pour une qualification ultérieure.

La clé d’idempotence dure 24 heures, 8 à 64 caractères ASCII imprimables. Le même couple clé + corps renvoie le dossier déjà créé, sans second calcul et sans second débit. Pour réessayer après un `failed`, il faut une nouvelle clé : l’ancienne continue de rendre l’échec.

`GET /v1/screenings/{id}` rend le même corps. `GET /v1/screenings?external_id=customer-42` liste les dossiers de cette intégration, du plus récent au plus ancien, vingt par page sauf `limit` autre (maximum 100). La page suivante s’appelle avec `cursor`.

Un identifiant d’une autre organisation, ou d’une autre intégration de la même organisation, répond `404`. L’absence et l’interdiction se répondent de la même façon.

## 11. Ce que l’analyste voit

La console est en français. Quatre écrans.

| Écran | À quoi il sert |
|---|---|
| Liste | Retrouver un dossier par date, issue, référence client. La liste vide explique que le criblage naît d’un appel, ou d’une vérification rattachée. |
| Fiche | Lire le sujet, l’issue, le moteur, le corpus, chaque correspondance et ses traits. Qualifier si le rôle le permet. |
| Règles | Lire la politique en vigueur, en publier une nouvelle, relire les anciennes. |
| Intégrations | Créer la clé de test, puis, le jour venu, la clé live. L’installation montre l’appel et le corps de la réponse. |

Publier une politique crée une version. Les dossiers déjà créés gardent la leur. `aml-policy-0` est la version d’usine : revue 0,70, confirmation 0,95, sanction, PEP, proches et déchéance allumés, crime éteint, rattachement éteint.

## 12. Règles à ne pas inverser

| Id | Règle |
|---|---|
| RG-AML-01 | L’issue se lit dans `outcome`. Le code HTTP `201` dit seulement que le dossier a été créé. |
| RG-AML-02 | `failed` n’est pas un `clear`. Aucun débit. Aucune qualification. |
| RG-AML-03 | Une PEP, un proche, une déchéance ou un crime ne se confirment pas sans analyste. |
| RG-AML-04 | Une sanction se confirme seule seulement au-dessus du seuil de confirmation, avec date complète et nom au score 1, ou avec un numéro identique et un nom encore reconnaissable. |
| RG-AML-05 | Le score comparé aux seuils est le score affiché, trois décimales, demi-supérieur. |
| RG-AML-06 | La politique active au moment de l’appel est celle du dossier pour toujours. |
| RG-AML-07 | Une clé de test n’appelle pas OpenSanctions. |
| RG-AML-08 | Une clé live n’est pas émise avant `aml-2`. Ce refus passe avant le contrôle du solde. |
| RG-AML-09 | Le booléen `match` d’OpenSanctions ne décide pas. |
| RG-AML-10 | Une fiche fournisseur au-dessus du seuil, sans identifiant ou sans score, fait échouer le dossier. |
| RG-AML-11 | Un faux positif lie ce sujet à cette entité. Il ne retire pas l’entité du corpus. |
| RG-AML-12 | Le nom tapé à la création d’une vérification d’identité n’est pas un sujet de criblage. |
| RG-AML-13 | Une qualification postérieure ne réécrit pas la décision d’identité. |
| RG-AML-14 | Les journaux portent les identifiants, l’issue, le nombre de lignes, la durée et le statut fournisseur. Ils ne portent ni le nom, ni la date, ni le numéro, ni la clé OpenSanctions. |

## 13. Les treize appels de référence

Politique d’usine. Moteur `aml-1`. Ce tableau est le même que celui du cahier, §11.2. Une implémentation qui en déplace une ligne a changé de version de moteur.

| Appel | Issue |
|---|---|
| Ivan Sanctionexample, 1971-04-12 | `confirmed_match` |
| Ivan Sanctionexample, 1999-01-01 | `clear` |
| Ivan Sanctionexample, sans date | `potential_match` |
| Ivan Sanctionexampl, 1971-04-12 | `confirmed_match` |
| Ivan Sanctionexample, 1999-01-01, numéro X1234567 | `confirmed_match` |
| Ivan Sanctionexample, 1971-04-12, numéro Z999 | `clear` |
| Nora Potentialexample | `potential_match` |
| Nora, P, Example | `potential_match` |
| Nora Potentialexample, nationalité US | `potential_match` |
| Leo Potentialexample, 2005-01-20 | `potential_match` |
| Mina Debarexample, 1990-06-01 | `potential_match` |
| Ada Clear, 1990-01-01 | `clear` |
| Prénom Ivan, sans nom de famille | `400`, pas de dossier |

Les quatre fiches derrière ces appels sont Ivan Sanctionexample (sanction, 1971-04-12, numéro X1234567), Nora Potentialexample (PEP, alias Nora P Example, 1982-11-03, FR), Leo Potentialexample (proche, 2005-01-20, FR) et Mina Debarexample (déchéance, année 1990, CA).

## 14. Ordre de lecture utile

| Si vous êtes | Lisez d’abord |
|---|---|
| Intégrateur | Les sections 3, 4 et 10, puis les treize appels |
| Analyste conformité | Les sections 2, 5 et 8 |
| Implémenteur du moteur `aml-1` | Les sections 5, 6, 12 et 13, puis le cahier §7 |
| Implémenteur du moteur `aml-2` | La section 7, puis le cahier §12 |
