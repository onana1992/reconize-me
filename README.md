# Recogniz-Me

SaaS de vérification d’identité (document + liveness + face match). Une identité numérique reconnue comme réelle. Monorepo : Spring Boot, MySQL, Next.js.

## Structure

```
api/                  # Spring Boot unique (pom.xml) → :8080
web/console           # Back-office client (session) → :3000
web/site              # Vitrine publique, FR + EN, SSG → :3002
packages/brand        # Charte M0 — tokens CSS, logo, badges (@kyc/brand)
infra/                # Docker Compose
docs/                 # CDC, roadmap, specs
```

Le produit Identity & Document Verification (hosted flow `:3001`, sessions, consentement) est **à reconstruire**. Contrat : [`docs/specs/specification-implementation-idv.md`](docs/specs/specification-implementation-idv.md).

## Prérequis

- JDK 17 (le CDC vise 21 ; passer `java.version` dans `api/pom.xml` après install JDK 21)
- Maven 3.9
- Node 22
- Docker

## Démarrage local

```bash
docker compose -f infra/docker-compose.yml up -d
cd api && mvn spring-boot:run
```

Health : `GET http://localhost:8080/v1/health`

Front :

```bash
npm install
npm run dev:console
npm run dev:site
```

Vitrine : `http://localhost:3002` — redirige vers `/fr` ou `/en` selon le navigateur.

### Vitrine

Les textes vivent dans `web/site/messages/fr.json` et `en.json`. Le français porte le type : une clé absente de l’anglais **casse la compilation**.

```bash
npm run check -w web/site   # parité des traductions, typecheck, build SSG, contrôle SEO
```

| Variable | Défaut | Effet |
|---|---|---|
| `NEXT_PUBLIC_SITE_URL` | `http://localhost:3002` | Base des canoniques, `hreflang`, OG |
| `NEXT_PUBLIC_CONSOLE_URL` | `http://localhost:3000` | Cible du CTA « Créer un compte » |
| `NEXT_PUBLIC_API_URL` | `http://localhost:8080` | Liens OpenAPI de la page docs |
| `NEXT_PUBLIC_SITE_INDEXABLE` | absente | `true` autorise l’indexation ; sinon `robots.txt` interdit tout |
| `CONTACT_WEBHOOK_URL` | absente | Acheminement du formulaire de contact. Sans elle, le formulaire refuse d’envoyer et renvoie vers l’e-mail |

## Contrats

- **MVP SaaS (contrat d’implémentation actuel)** : `docs/MVP/cahier-des-charges-mvp.md` — marque, vitrine, comptes, souscription, IDV via AWS / stubs
- Roadmap MVP : `docs/MVP/roadmap-implementation-mvp.md`
- Charte visuelle (M0 livré) : `docs/MVP/charte-visuelle.md`
- Cahier des charges (vision produit) : `docs/cahier-des-charges.md`
- Identity & Document Verification (métier) : `docs/specs/specification-fonctionnelle-idv.md`
- **IDV — reconstruction (implémentation)** : `docs/specs/specification-implementation-idv.md`
- Roadmap IDV (vision SageMaker) : `docs/specs/roadmap-implementation-idv.md`
- Coût unitaire d’une vérification : `docs/cout-unitaire-verification.md`
