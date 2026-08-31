# Portfolio Application Copilot

Java 21 / Spring Boot domain service plus a Chrome Manifest V3 client for MCP-mediated job application assistance.

## Boundary

```text
Chrome MV3 / Codex / Copilot / other MCP clients
          -> https://www.yuqi.site/mcp/admin
          -> portfolio-mcp-gateway (RBAC, confirmation, audit, idempotency)
          -> portfolio-application-copilot (profile snapshot, rules, account workflow)
          -> Valkey (cached public profile, encrypted answers and per-origin credentials)

Profile cache miss
          -> portfolio-mcp-gateway/admin.search_content
          -> first-party portfolio content projection
```

The service never reads Supabase profile tables. Supabase authentication may identify the admin at the public MCP edge, while all profile reads and application operations remain MCP tools.

Sensitive answers are never inferred. The resolver returns `NEEDS_CONFIRMATION` for work authorization, sponsorship, compensation, relocation, EEO, signature, and background-check fields.

## Runtime ownership

- `extension/` is intentionally plain JavaScript because Chrome Manifest V3 runs in the browser. It scans the active application page, renders a review, and applies approved values. It never owns candidate data or policy decisions.
- This Spring Boot service owns the candidate snapshot, deterministic field resolution, encrypted private-answer memory, and refresh workflow.
- `portfolio-mcp-server` owns Supabase authentication at `/mcp/admin`; Supabase is not a candidate-profile database.
- `portfolio-mcp-gateway` owns RBAC, confirmation, audit, idempotency, and downstream routing for every `career.*` tool.

The extension never silently submits a job application or registration. It fills reviewed fields, and sensitive fields remain confirmation-gated. CAPTCHA, MFA, terms acceptance, attestations, signatures, EEO answers, and the final submit remain user actions.

## MCP tools

| Tool | Mode | Purpose |
| --- | --- | --- |
| `career.get_candidate_profile` | Read | Return the current owner-only candidate snapshot. |
| `career.refresh_candidate_profile` | Write | Rebuild public profile data through `admin.search_content` in the existing MCP cluster. |
| `career.resolve_application_fields` | Read | Resolve a scanned form with source, confidence, and review status. |
| `career.update_private_answers` | Confirmed write | Update encrypted recurring answers such as contact details or user-declared work authorization. |
| `career.get_site_credential` | Confirmed read | Retrieve the owner-only credential for the exact HTTPS origin currently open in Chrome. |
| `career.prepare_site_credential` | Confirmed write | Create or reuse a unique encrypted credential for an owner-reviewed sign-up. |

All six tools require the managed `ADMIN` role and owner capability at `/mcp/admin`. The gateway authenticates the service-to-service hop with `X-Internal-Token`.

## Application workflow

```text
1. User opens an application and presses Scan this page.
2. Chrome injects the form scanner into the active tab under activeTab permission.
3. The extension sends labels and choices, not the page body, to career.resolve_application_fields.
4. The Java rule engine joins the cached public MCP snapshot with encrypted owner memory.
5. Each field returns RESOLVED, NEEDS_CONFIRMATION, or UNSUPPORTED plus source and confidence.
6. The user selects safe results and presses Apply approved fields.
7. The extension dispatches input/change events so React-based ATS forms receive the values.
8. The user reviews and manually submits the application.
```

## Account workflow

```text
1. The extension scans the active page and classifies SIGN_UP, SIGN_IN, APPLICATION, or FORM.
2. On SIGN_UP, the owner reviews the username and explicitly requests a site credential.
3. The MCP gateway enforces owner RBAC and confirmation before calling the Java service.
4. The service canonicalizes the HTTPS origin and generates a unique 24-character password.
5. The credential is AES-GCM encrypted and stored under a SHA-256 origin key in Valkey.
6. The extension fills username, password, and password confirmation fields without persisting plaintext locally.
7. On a later SIGN_IN, another explicit owner action retrieves and fills the credential for that origin.
8. The user completes CAPTCHA, MFA, terms, and submit actions.
```

The page scanner is JavaScript because Chrome Manifest V3 executes browser extensions in JavaScript. Java remains the backend implementation for state, encryption, deterministic field resolution, profile refresh, validation, and MCP downstream operations.

No crawler, job scraper, silent autonomous submitter, shared fixed password, or direct Supabase profile query is part of this service. Credentials are unique per origin so a breach at one ATS does not expose another account.

## Chrome extension

1. Open `chrome://extensions` and enable Developer mode.
2. Select **Load unpacked** and choose the `extension/` directory.
3. Sign in through `https://www.yuqi.site/admin`.
4. Open the extension and press **Connect admin**. The access token is retained only in `chrome.storage.session` and is cleared when the browser session ends.
5. Open an application, registration, or login page and scan it.
6. Review provenance before filling application fields. Registration and login assistance require a separate explicit button click.

The extension requests only `activeTab` for application pages. It has permanent host access only to `yuqi.site` for the authenticated MCP endpoint.

## Deployment

`cloudbuild.yaml` deploys the Java service to Cloud Run with one vCPU, 512 MiB memory, concurrency 20, `min-instances=0`, and `max-instances=1`. This preserves scale-to-zero cost behavior. The Cloud Run ingress can reach the health endpoint, while every `/internal/**` operation fails closed without the shared service token. Secrets are injected from Secret Manager; never commit them or expose them to the extension.

The MCP Gateway also needs:

```text
CAREER_SERVICE_BASE_URL=https://<application-copilot-cloud-run-url>
CAREER_INTERNAL_TOKEN=<same Secret Manager value used by the service>
```
