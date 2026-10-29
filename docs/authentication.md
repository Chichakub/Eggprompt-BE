# Authentication and role navigation

The browser calls `/api/v1/auth/*`. Vite (and the production reverse proxy)
removes `/api`, so Spring serves `/v1/auth/*`.

- `POST /v1/auth/login`: public; returns an access token and user profile.
- `GET /v1/auth/me`: requires a valid Bearer token; returns the profile directly.
- `POST /v1/auth/logout`: revokes the presented token and returns 204.
- `/v1/users/**` and `/v1/audit-logs/**`: require Super Admin or Farm Admin.

JWT authentication loads the user by the numeric subject, rejects inactive or
deleted accounts, and uses the current database role for API authorization.
Each login has a unique JWT ID, so logging out one session does not revoke another.
Revoked tokens are stored as SHA-256 hashes in `revoked_tokens`; expired entries
are removed on logout. The existing Hibernate `ddl-auto=update` configuration
creates this table on application startup. Environments using schema validation
must provision the equivalent table before deploying.

Security integration tests use the real Spring Security chain and JWT signing,
with mocked user and revocation repositories; they do not connect to PostgreSQL.

```sh
./mvnw -Dtest=AuthSecurityTests test
```

Frontend session, API-interceptor and route-guard tests:

```sh
cd ../Eggprompt-FE
npm test
npm run build
```

A live browser/PostgreSQL end-to-end test remains separate from these checks.

After changing Java authentication code, restart the backend (IntelliJ Stop/Run,
or `./mvnw spring-boot:run`). Vite reloads JavaScript automatically, but an existing
Java process keeps the previous security configuration until restarted.

To verify the currently running backend through Vite and the real database:

```sh
EGGPROMPT_LIVE_AUTH_TEST=true ./mvnw -Dtest=LiveAuthenticationTests test
```

This opt-in test creates a uniquely named temporary account and removes it in
`finally`. It checks login, profile, logout and rejection of the revoked token.
The test targets Vite's local IPv6 listener on port 5173.
