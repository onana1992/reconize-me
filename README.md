# Recogniz-Me

SaaS de vérification d’identité (document + liveness + face match). Une identité numérique reconnue comme réelle. Monorepo : Spring Boot, PostgreSQL, Next.js.

## Structure

```
api/                  # Spring Boot unique (pom.xml)
  src/main/java/com/kyc/
    entities/         # Verification, Consent, Organization, ApiKey, AuditEvent
    repositories/
    services/
    controllers/      # REST /v1  → :8080
    config/
    ports/            # interfaces S3 / SQS / Textract / Rekognition
    adapters/         # impl no-op locale
    workers/          # jobs async (SQS plus tard)
web/console           # Dashboard client  → :3000
web/flow              # Hosted flow applicant (Recogniz-Me) → :3001
web/site              # Vitrine publique, FR + EN, SSG → :3002
packages/brand        # Charte M0 — tokens CSS, logo, badges (@kyc/brand)
packages/capture-sdk  # Qualité caméra (sprint 2)
infra/                # Docker Compose
docs/                 # CDC, roadmap, specs
```

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
npm run dev:flow
npm run dev:site
```

Flow consentement : `http://localhost:3001/flow/{token}`

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
- Identity & Document Verification : `docs/specs/specification-fonctionnelle-idv.md`
- Roadmap IDV : `docs/specs/roadmap-implementation-idv.md`
- Guide S1 (fondation livrée) : `docs/specs/guide-implementation-s1.md`
- Guide S2 (capture) : `docs/specs/guide-implementation-s2.md`
- Coût unitaire d’une vérification : `docs/cout-unitaire-verification.md`
