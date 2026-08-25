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
```

Flow consentement : `http://localhost:3001/flow/{token}`

## Contrats

- Cahier des charges : `docs/cahier-des-charges.md`
- Architecture d’implémentation : `docs/architecture-implementation.md`
- Tutoriel pipeline : `docs/tutoriel-pipeline-kyc.md`
- Roadmap : `docs/roadmap-implementation.md`
- Sprint 1 : `docs/specs/sprint-01-verifications-multi-tenant.md`
