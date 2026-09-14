# API Recogniz-Me

API Spring Boot de vérification d’identité (document, liveness, face match). Port **8080**.

Java 17, Spring Boot 3.4, Security, JPA, Flyway, MySQL, Redis, springdoc OpenAPI. Les secrets SMTP se chargent depuis `application-secrets.properties` (non versionné) ou des variables d’environnement.

## Démarrage

MySQL et Redis doivent tourner. Depuis la racine du monorepo :

```bash
docker compose -f infra/docker-compose.yml up -d
cd api && mvn spring-boot:run
```

Health : `GET http://localhost:8080/v1/health`  
Swagger : `http://localhost:8080/swagger-ui.html`

SMTP (optionnel) : copier `application-secrets.properties.example` vers `application-secrets.properties` à la racine de `api/`. Ne pas committer le fichier réel.

| Propriété / variable | Défaut | Effet |
|---|---|---|
| `server.port` | `8080` | Port HTTP |
| `kyc.public-console-base-url` | `http://localhost:3000` | Liens e-mail (vérif, reset, invite) |
| `kyc.public-flow-base-url` | `http://localhost:3001` | URL du hosted flow |
| `kyc.cors-allowed-origin-patterns` | localhost, LAN, tunnels | Origines CORS `/v1/**` (`allowCredentials`) |
| `kyc.session-cookie-secure` | `false` | Flag `Secure` du cookie `rm_session` |
| `kyc.object-storage-root` | `./data/media` | Stockage local des médias |
| `KYC_MAIL_MODE` | `smtp` | `smtp` ou `log` |
| `KYC_SMTP_PASSWORD` | vide | Mot de passe SMTP |

Tests : `mvn test` (H2 in-memory, Redis désactivé, mail en `log`).

## Surfaces HTTP

| Préfixe | Client | Auth |
|---|---|---|
| `/v1/health`, `/actuator/**`, OpenAPI | monitoring / docs | aucune |
| `/v1/account/**` | console (signup, login, reset) | publique sauf `POST /logout` |
| `/v1/console/**` | back-office Next.js | cookie `rm_session` |
| `/v1/verifications/**` | intégration partenaire | `Authorization: Bearer ky_test_…` / `ky_live_…` |
| `/v1/flow/**`, `/v1/objects` | hosted flow (token d’URL) | token de session hosted, pas Spring Security |

## Packages

```
com.kyc
  config/       SecurityConfig, CORS, stores (session, hosted token)
  security/     filtres, principals, rôles, permissions
  controllers/  HTTP
  services/     métier (Account, Console, Session, IDV…)
  ports/        interfaces (session store, storage, IA)
  adapters/     Redis, filesystem, stubs AWS
  entities/     JPA
```

---

## Spring Security

Pas de login HTML, pas de HTTP Basic, pas de `HttpSession` Spring. L’API est **stateless** : l’identité est reconstruite à **chaque** requête, soit par cookie console, soit par clé API Bearer.

Fichier d’entrée : `config/SecurityConfig.java`.

### Chaîne de filtres

```
requête
  → CORS
  → ApiKeyAuthenticationFilter      (Bearer, routes tenant)
  → SessionAuthenticationFilter     (cookie, /v1/console/** et logout)
  → FilterSecurityInterceptor       (permitAll / authenticated)
  → controller
```

`SecurityFilterChain` :

- CSRF, HTTP Basic et formLogin **désactivés**
- `SessionCreationPolicy.STATELESS`
- CORS `/v1/**` avec credentials (`kyc.cors-allowed-origin-patterns`)
- `BCryptPasswordEncoder` pour mots de passe **et** hash des clés API
- filtres custom branchés autour de `UsernamePasswordAuthenticationFilter` (présent mais inutilisé)

`permitAll()` dans `authorizeHttpRequests` **n’empêche pas** un filtre de tourner. C’est `shouldNotFilter` qui saute un filtre. Les deux listes (matcher Spring + skip filtre) doivent rester alignées.

### Qui est authentifié, et comment

**1. Console — cookie de session** (`SessionAuthenticationFilter`)

Ne s’applique qu’à `/v1/console/**` et `POST /v1/account/logout`. Lit le cookie `rm_session`, le valide via `SessionService`, puis pose un `ConsolePrincipal` (`ROLE_CONSOLE`) dans le `SecurityContext`. Cookie absent ou invalide → **401**.

Le login (`POST /v1/account/login`) est **public**. C’est `AccountController` + `SessionService` qui vérifient e-mail / mot de passe et posent le cookie. Spring ne « logue » pas.

Cookie `rm_session` :

- HttpOnly, `Path=/`, `SameSite=Lax`, TTL 7 jours
- valeur = token opaque aléatoire ; seul le **hash poivré** (SHA-256 + `kyc.ip-hash-pepper`) est stocké
- Redis si disponible (`RedisConsoleSessionStore`), sinon mémoire (`InMemoryConsoleSessionStore`)
- à l’auth, le membership actif est relu en base : rôle et org à jour, y compris après révocation

**2. Partenaire — clé API** (`ApiKeyAuthenticationFilter`)

Ignore volontairement health, account, console, flow, objects, Swagger, actuator. Sur le reste (ex. `/v1/verifications`), **exige** `Authorization: Bearer <clé>`.

`ApiKeyAuthenticator` n’accepte que les préfixes `ky_test_` et `ky_live_`. Lookup par préfixe 12 caractères, puis `passwordEncoder.matches` sur le hash BCrypt. Succès → `ApiPrincipal(organizationId, apiKeyId)` + `ROLE_TENANT`. Échec → **401**.

Les routes tenant sont en `anyRequest().permitAll()` côté matcher : c’est le filtre, pas `authenticated()`, qui bloque l’absence de Bearer. Sans ça, une requête sans header passerait jusqu’au controller.

### Principals

L’objet posé dans `SecurityContextHolder` n’est pas un `UserDetails` Spring. C’est un record métier :

| Type | Contenu | Lecture |
|---|---|---|
| `ConsolePrincipal` | `userId`, `organizationId`, `role` | `CurrentConsole.require()` |
| `ApiPrincipal` | `organizationId`, `apiKeyId` | `CurrentApiKey.require()` |

`UsernamePasswordAuthenticationToken` est uniquement le véhicule Spring (« cette requête est authentifiée »). Le 2ᵉ argument (credentials) est `null` une fois l’auth faite.

### Autorisation : matcher vs RBAC métier

Deux couches distinctes.

**Couche Spring (URL)** — `authorizeHttpRequests` :

| Matcher | Règle |
|---|---|
| health, Swagger, actuator | `permitAll` |
| signup, verify, login, forgot/reset, invites | `permitAll` |
| `/v1/flow/**`, `/v1/objects` | `permitAll` (auth par token hosted, hors Security) |
| `/v1/account/**`, `/v1/console/**` | `authenticated` |
| le reste (API partenaire) | `permitAll` + filtre Bearer |

**Couche métier (permissions)** — pas de `@PreAuthorize`. Après `CurrentConsole.require()`, le controller / service appelle `ConsoleAuth.require(principal, Permission.…)`.

Rôles (`ConsoleRole`) → ensembles de `Permission` :

| Rôle | Droits |
|---|---|
| `readonly` | `TEAM_READ`, `VERIFICATION_READ` |
| `member` | + `VERIFICATION_WRITE` |
| `developer` | `TEAM_READ`, `API_KEY_READ`, `API_KEY_WRITE` |
| `admin` | équipe, vérifs, clés, `AUDIT_READ` |
| `owner` | tout, y compris `OWNERSHIP`, `BILLING_*` |

`ROLE_CONSOLE` / `ROLE_TENANT` distinguent le **canal** (session vs clé). Ils ne portent pas les droits métier.

Exemple (`ConsoleVerificationController`) :

```java
ConsolePrincipal principal = CurrentConsole.require();
ConsoleAuth.require(principal, Permission.VERIFICATION_READ);
return verifications.list(principal.organizationId(), ...);
```

### Erreurs HTTP

| Cas | Status | Handler |
|---|---|---|
| Pas / mauvaise session ou clé | **401** `unauthorized` | `RestAuthenticationEntryPoint` |
| Session OK, permission insuffisante | **403** `forbidden` | `ConsoleAuth` → `ApiException` |
| `AccessDeniedException` Spring | **404** `not_found` | `RestAccessDeniedHandler` (existence masquée) |

Message 401 : « Invalid or missing session » sur `/v1/console` et `/v1/account`, sinon « Invalid or missing API key ».

### Fichiers

| Fichier | Rôle |
|---|---|
| `config/SecurityConfig.java` | `SecurityFilterChain`, CORS, `PasswordEncoder` |
| `security/ApiKeyAuthenticationFilter.java` | Bearer → `ApiPrincipal` |
| `security/SessionAuthenticationFilter.java` | cookie → `ConsolePrincipal` |
| `security/RestAuthenticationEntryPoint.java` | 401 JSON |
| `security/RestAccessDeniedHandler.java` | 404 JSON |
| `security/ConsolePrincipal.java` / `ApiPrincipal.java` | identités |
| `security/CurrentConsole.java` / `CurrentApiKey.java` | lecture du contexte |
| `security/ConsoleRole.java` / `Permission.java` / `ConsoleAuth.java` | RBAC |
| `services/SessionService.java` | émission, hash, cookie, invalidation |
| `services/ApiKeyAuthenticator.java` | validation `ky_test_` / `ky_live_` |

### Pièges

1. **`permitAll` ≠ le filtre ne tourne pas.** Ajouter une route publique implique de la déclarer dans `SecurityConfig` **et** dans `PUBLIC` de `ApiKeyAuthenticationFilter` (sinon 401 « missing bearer »).
2. **`STATELESS` + cookie** : le cookie est *notre* session, pas celle de Spring.
3. **Ne jamais logger** la valeur de `rm_session` ni le Bearer.
4. Une nouvelle route `/v1/account/**` authentifiée doit aussi entrer dans `PROTECTED` de `SessionAuthenticationFilter` (aujourd’hui seul `logout` l’est, avec toute la console).
