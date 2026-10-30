# Guide — Déployer Recogniz-Me sur Render

**Plateforme :** Recogniz-Me  
**Version :** 1.0  
**Date :** 28 octobre 2026  
**Statut :** prêt à appliquer  
**Dépôt :** `master` sur `https://github.com/onana1992/reconize-me`

Ce guide met l’API, la console, le parcours de capture et le site public sur Render, avec la base Neon déjà migrée. GitHub Actions compile et teste, puis demande à Render de déployer.

**Fichiers liés :**

- [`render.yaml`](../../render.yaml) — description des services Render
- [`.github/workflows/ci-cd.yml`](../../.github/workflows/ci-cd.yml) — CI sur chaque pull request, déploiement sur `master`
- [`api/application-secrets.properties.example`](../../api/application-secrets.properties.example) — liste des secrets, sans les valeurs

---

## 1. Ce qui tourne sur Render

Quatre services, tous dans la région **Ohio**, proche de Neon (`us-east-2`).

| Service | Rôle | Dossier | Santé |
|---|---|---|---|
| `recogniz-me-api` | API Spring Boot 3, Java 17 | `api/` | `GET /actuator/health` |
| `recogniz-me-console` | Console Next.js | `web/console` | page d’accueil |
| `recogniz-me-flow` | Parcours hébergé Next.js | `web/flow` | page d’accueil |
| `recogniz-me-site` | Site public Next.js | `web/site` | page d’accueil |
| `recogniz-me-redis` | Sessions console et jetons de parcours | Key Value | interne |

La base PostgreSQL reste sur **Neon**. Render ne crée pas de Postgres. Le disque Render est éphémère : les images passent par **S3**, pas par `./data/media`.

`autoDeploy` est désactivé. Un push sur `master` ne met rien en production tant que la CI n’est pas verte. Le workflow appelle alors le deploy hook de chaque service.

## 2. Prérequis

- Compte Render relié au dépôt GitHub.
- Compte GitHub avec le droit d’ajouter des secrets sur `onana1992/reconize-me`.
- Base Neon déjà chargée (schéma Flyway en version 47).
- Bucket S3 et clés IAM déjà utilisés en local.
- Clés Stripe, OpenAI et OpenSanctions si ces fonctions doivent être actives.

L’API écoute `${PORT:8080}`. Render fournit `PORT`. En local, sans cette variable, le port reste 8080.

## 3. URL JDBC Neon

Dans le dashboard Neon, prendre l’hôte **direct**, sans `-pooler`. Le pooler casse les requêtes préparées d’Hibernate.

```text
jdbc:postgresql://ep-xxxx.c-6.us-east-2.aws.neon.tech:5432/neondb?sslmode=require
```

Utilisateur et mot de passe sont des variables séparées (`SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`), pas dans l’URL.

Ne pas committer ces valeurs. Les saisir dans Render au moment du Blueprint (`sync: false` dans `render.yaml`).

## 4. Créer les services

1. Pousser `render.yaml` et le workflow sur `master`.
2. Render → **New** → **Blueprint**.
3. Choisir le dépôt et la branche `master`.
4. Render demande les secrets marqués `sync: false`. Les remplir avant de lancer le premier déploiement. Sans l’URL Neon, l’API s’arrête au démarrage.
5. Si un nom est déjà pris, Render le suffixe. Reprendre alors les vraies URL `https://….onrender.com` dans les variables de la section 5, puis redéployer les fronts : Next.js fige `API_BASE_URL` et `NEXT_PUBLIC_*` au **build**.

Le premier apply du Blueprint déploie une fois. Les push suivants attendent GitHub Actions.

## 5. Variables à vérifier après le premier apply

Les URL ci-dessous supposent que les noms du Blueprint sont libres. Les remplacer par les URL affichées dans Render si elles diffèrent.

### API

| Variable | Valeur |
|---|---|
| `SPRING_DATASOURCE_URL` | URL JDBC de la section 3 |
| `SPRING_DATASOURCE_USERNAME` | rôle Neon |
| `SPRING_DATASOURCE_PASSWORD` | mot de passe Neon |
| `KYC_IP_HASH_PEPPER` | chaîne longue et aléatoire, différente de `local-dev-pepper` |
| `KYC_SESSION_COOKIE_SECURE` | `true` |
| `KYC_PUBLIC_API_BASE_URL` | `https://recogniz-me-api.onrender.com` |
| `KYC_PUBLIC_CONSOLE_BASE_URL` | `https://recogniz-me-console.onrender.com` |
| `KYC_PUBLIC_FLOW_BASE_URL` | `https://recogniz-me-flow.onrender.com` |
| `KYC_CORS_ALLOWED_ORIGIN_PATTERNS` | les trois origines HTTPS, séparées par des virgules |
| `KYC_REDIS_ENABLED` | `true` |
| `SPRING_DATA_REDIS_URL` | rempli par le Blueprint depuis Key Value |
| `KYC_AWS_S3_ENABLED` | `true`, avec le bucket et les clés |
| `KYC_SMTP_PASSWORD` | mot de passe SMTP |
| `STRIPE_SECRET_KEY` | `sk_test_…` ou `sk_live_…` |
| `STRIPE_WEBHOOK_SECRET` | `whsec_…` du endpoint `https://recogniz-me-api.onrender.com/v1/webhooks/stripe` |
| `OPENAI_API_KEY` | clé Document IA |
| `OPENSANCTIONS_API_KEY` | clé aml-2 |

`KYC_STRIPE_MODE=stripe` est déjà dans le Blueprint. Le laisser à `log` coupe les vrais paiements.

### Fronts

| Service | Variable | Rôle |
|---|---|---|
| Console et Flow | `API_BASE_URL` | URL publique de l’API, lue au build |
| Site | `NEXT_PUBLIC_SITE_URL` | URL canonique du site |
| Site | `NEXT_PUBLIC_CONSOLE_URL` | lien vers la console |
| Site | `NEXT_PUBLIC_API_URL` | lien vers l’API |
| Site | `NEXT_PUBLIC_SITE_INDEXABLE` | `false` tant que le domaine n’est pas le domaine définitif |

## 6. Deploy hooks et secrets GitHub

Pour chaque service web : **Settings → Deploy Hook → Create**.

GitHub → dépôt → **Settings → Secrets and variables → Actions** :

| Secret | Hook |
|---|---|
| `RENDER_DEPLOY_HOOK_API` | `recogniz-me-api` |
| `RENDER_DEPLOY_HOOK_CONSOLE` | `recogniz-me-console` |
| `RENDER_DEPLOY_HOOK_FLOW` | `recogniz-me-flow` |
| `RENDER_DEPLOY_HOOK_SITE` | `recogniz-me-site` |

Un secret absent n’échoue pas le workflow : ce service est ignoré et le journal l’indique. Tant que les quatre secrets sont vides, la CI passe et rien n’est redéployé.

## 7. Ce que fait le workflow

Fichier : `.github/workflows/ci-cd.yml`.

Sur chaque pull request vers `master`, et sur chaque push de `master` :

1. **API** — Java 17, `mvn -B test` dans `api/`.
2. **Web** — Node 22, `npm ci` puis `npm run build` pour `console`, `flow` et `site`.
3. **Déploiement** — seulement sur un push de `master`, et seulement si les deux jobs précédents sont verts. Un `POST` part vers chaque deploy hook.

Render rebuild alors depuis `master`. Le déploiement Render n’est pas le jar produit par Actions : Actions sert de barrière. Les commandes de build sont celles de `render.yaml`.

Pour relancer un déploiement sans nouveau commit : GitHub → Actions → **CI/CD** → **Run workflow** n’est pas exposé. Faire un push sur `master`, ou appeler le hook à la main.

## 8. Vérifier

1. `GET https://recogniz-me-api.onrender.com/actuator/health` renvoie `{"status":"UP"}`. Si Redis ou Neon est injoignable, le statut passe à `DOWN` et Render marque le déploiement en échec.
2. Ouvrir la console, se connecter avec un compte déjà présent dans Neon.
3. Ouvrir un parcours hébergé : le flow doit joindre l’API.
4. Dans Render, l’onglet **Events** de chaque service montre le déploiement déclenché par le hook.

## 9. Limites à connaître

- Les scripts Flyway du dépôt sont en syntaxe MySQL. Sur Neon ils sont déjà appliqués (version 47) et ne sont pas rejoués. Une **nouvelle** migration doit être écrite pour PostgreSQL. Un Postgres vide ne peut pas être reconstruit avec ces scripts.
- Sans S3, les fichiers déposés disparaissent au prochain déploiement.
- Sans Redis, il faudrait `KYC_REDIS_ENABLED=false`. Les sessions et les jetons de parcours seraient alors en mémoire et perdus à chaque redémarrage. Le Blueprint branche Key Value pour éviter ça.
- Le plan gratuit des services web s’endort. Un webhook Stripe ou un parcours en cours peut tomber sur un réveil lent. Passer l’API en instance toujours allumée dès que des webhooks réels partent.
- Flyway signale que PostgreSQL 18 est plus récent que la version qu’il teste. Le démarrage actuel valide quand même le schéma.

## 10. Dépannage

| Symptôme | Cause probable |
|---|---|
| L’API cherche MySQL au démarrage | `SPRING_DATASOURCE_URL` absent. Le fichier `application.properties` retombe alors sur MariaDB locale. |
| `prepared statement does not exist` | L’URL JDBC pointe vers l’hôte `-pooler`. Utiliser l’hôte direct. |
| Health check `DOWN` | Neon ou Redis injoignable, ou `SPRING_DATA_REDIS_URL` vide alors que `KYC_REDIS_ENABLED=true`. |
| La console appelle `127.0.0.1:8080` | `API_BASE_URL` n’était pas défini au build. Corriger la variable, puis redéployer la console et le flow. |
| Port déjà pris dans les logs Render | Le process n’écoute pas `$PORT`. L’API doit garder `server.port=${PORT:8080}`. Les fronts démarrent avec `npx next start --hostname 0.0.0.0 --port $PORT`. |
| La CI est verte mais rien ne change en ligne | Les secrets `RENDER_DEPLOY_HOOK_*` ne sont pas dans GitHub, ou `autoDeploy` a été remis sur true et un ancien déploiement a écrasé le suivant. |
| Flyway refuse une nouvelle migration | Le SQL est encore du MySQL (`DATETIME`, `LONGTEXT`, `MODIFY COLUMN`). L’écrire en PostgreSQL. |
