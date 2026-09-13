# Console

Back-office client Recogniz-Me. Session cookie, pas de clé API. Port **3000**.

Next.js 15 (App Router) + React 19. Charte via `@kyc/brand`. Appels serveur vers l’API Spring (`API_BASE_URL`, défaut `http://localhost:8080`).

## Démarrage

L’API doit tourner (`:8080`). Depuis la racine du monorepo :

```bash
npm install
npm run dev:console
```

Ou dans ce dossier : `npm run dev`. Ouvrir `http://localhost:3000`.

| Variable | Défaut | Effet |
|---|---|---|
| `API_BASE_URL` | `http://localhost:8080` | Proxy des appels `/v1/account/**` et `/v1/console/**` |
| `NEXT_PUBLIC_SITE_URL` | `http://localhost:3002` | Liens vitrine (légal, contact) depuis les pages auth |

Build : `npm run build` puis `npm start`.

## Auth

Cookie `rm_session` (posé par l’API au login / signup). Le middleware redirige vers `/login` si absent.

| Route | Rôle |
|---|---|
| `/signup` | Création de compte (ou acceptation d’invite `?invite=`) |
| `/signup/setup` | Prénom, nom, mot de passe après e-mail vérifié |
| `/verify` · `/verify/pending` | Confirmation d’e-mail |
| `/login` | Connexion |
| `/forgot` · `/reset` | Mot de passe oublié |

## Application

| Route | Contenu |
|---|---|
| `/` | Accueil org : usage sandbox / live, crédit, catalogue produits |
| `/identity` | IDV (seul produit live) : overview, configuration, intégrations, vérifications |
| `/biometrics` · `/aml` | Teasers « bientôt » |
| `/settings/account` | Profil, mot de passe |
| `/settings/team` | Membres, invites, rôles |
| `/settings/keys` | Clés `ky_test_` / `ky_live_` |
| `/settings/billing` | Crédit d’organisation |
| `/settings/activity` | Journal d’audit |

Sur les chemins produit, `?env=sandbox\|live` (cookie `rm_console_env`, défaut sandbox).

## i18n

Pas de `next-intl` : module maison dans `i18n/`. Pas de préfixe d’URL (`/fr`, `/en`) — contrairement à la vitrine. La langue vit dans le cookie `rm_locale` (1 an, `path=/`, `SameSite=Lax`). Absent ou invalide → **fr**. Pas de lecture de `Accept-Language`.

Le layout racine pose `lang` sur `<html>` et enveloppe l’arbre dans `I18nProvider`. Le sélecteur (`LanguageMenu`, pages auth et AppShell) appelle l’action serveur `setLocaleAction`, puis `router.refresh()` : le cookie est relu, le provider remonte (`key={locale}`).

### Fichiers

```
i18n/
  locales.ts      # LOCALES, DEFAULT_LOCALE, cookie, isLocale
  fr.json         # source de vérité des clés
  en.json         # doit coller à fr.json
  messages.ts     # type Messages = typeof fr, createT, getMessages
  get-locale.ts   # serveur : cookie → Locale (React cache)
  get-t.ts        # serveur : createT(getMessages(locale))
  provider.tsx    # client : I18nProvider, useT, useLocale
  actions.ts      # setLocaleAction (cookie)
  index.ts        # barrel serveur (pages / layouts RSC)
  client.ts       # barrel client (ne pas importer index.ts dans un "use client")
```

`t("a.b.c")` traverse l’objet JSON. Clé inconnue → la clé est renvoyée telle quelle. Pas d’interpolateur : les `{placeholders}` se remplacent à l’appel.

```ts
t("setup.leadWithEmail").replace("{email}", email)
```

### APIs

| Contexte | Import | Usage |
|---|---|---|
| Server Component | `from ".../i18n"` | `const t = await getT()` · `const locale = await getLocale()` |
| Client Component | `from ".../i18n/client"` | `const t = useT()` · `const locale = useLocale()` |
| Helper partagé | `import type { Translate } from ".../i18n"` | `function tRole(t: Translate, role: string)` |

Le barrel `i18n/index.ts` tire `cookies()` : un client qui l’importe casse le bundle. D’où `i18n/client.ts`.

`useLocale()` sert aussi à `Intl` (montants, dates) : tags `fr-FR` / `en-GB` dans `lib/pricing.ts`.

### Typage

`Messages` = forme de `fr.json`. `en.json` est typé `Record<Locale, Messages>` : une clé présente en français et absente en anglais **casse TypeScript**. `t()` n’accepte que les chemins en notation pointée dérivés de cet arbre (`console.nav.idv`, `login.submit`, …).

Espaces de noms dans les JSON : `common`, `locale`, `signup`, `setup`, `verifyPending`, `login`, `forgot`, `reset`, `verify`, `console.*` (shell, produits, settings).

### Ajouter une chaîne

1. Clé dans `i18n/fr.json`, même chemin dans `i18n/en.json`.
2. `t("namespace.key")` côté serveur ou client selon le composant.

Pour une locale de plus : l’ajouter à `LOCALES`, fournir un JSON de même forme, l’enregistrer dans `DICTIONARIES`, et étendre `LOCALE_TAGS` dans `lib/pricing.ts`.

## Structure

```
app/(auth)/     # login, signup, verify, reset
app/(app)/      # shell authentifié, produits, settings
components/     # AppShell, chrome produit, menus
i18n/           # locale, messages, provider
lib/            # api.ts, session, produits, environnement
```
