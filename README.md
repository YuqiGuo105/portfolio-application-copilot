# Portfolio Application Copilot

Java 21 / Spring Boot domain service for the portfolio's MCP-mediated job application workflow.

## Boundary

```text
Chrome MV3 / Codex / Copilot
          -> https://www.yuqi.site/mcp/admin
          -> portfolio-mcp-gateway (RBAC, confirmation, audit, idempotency)
          -> portfolio-application-copilot (profile snapshot and field rules)
          -> Valkey (encrypted private answers and cached public profile)

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

The extension never submits a job application. It fills only reviewed fields, and sensitive fields remain confirmation-gated.

## MCP tools

| Tool | Mode | Purpose |
| --- | --- | --- |
| `career.get_candidate_profile` | Read | Return the current owner-only candidate snapshot. |
| `career.refresh_candidate_profile` | Write | Rebuild public profile data through `admin.search_content` in the existing MCP cluster. |
| `career.resolve_application_fields` | Read | Resolve a scanned form with source, confidence, and review status. |
| `career.update_private_answers` | Confirmed write | Update encrypted recurring answers such as contact details or user-declared work authorization. |

All four tools require the managed `ADMIN` role and owner capability at `/mcp/admin`. The gateway authenticates the service-to-service hop with `X-Internal-Token`.

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

No crawler, job scraper, autonomous submitter, fixed login credential, or direct Supabase profile query is part of this service.

## Chrome extension

1. Open `chrome://extensions` and enable Developer mode.
2. Select **Load unpacked** and choose the `extension/` directory.
3. Sign in through `https://www.yuqi.site/admin`.
4. Open the extension and press **Connect admin**. The access token is retained only in `chrome.storage.session` and is cleared when the browser session ends.
5. Open an application, scan it, review provenance, and apply approved fields.

The extension requests only `activeTab` for application pages. It has permanent host access only to `yuqi.site` for the authenticated MCP endpoint.

## Deployment

`cloudbuild.yaml` deploys the Java service to Cloud Run with one vCPU, 512 MiB memory, concurrency 20, `min-instances=0`, and `max-instances=1`. This preserves scale-to-zero cost behavior. The Cloud Run ingress can reach the health endpoint, while every `/internal/**` operation fails closed without the shared service token. Secrets are injected from Secret Manager; never commit them or expose them to the extension.

The MCP Gateway also needs:

```text
CAREER_SERVICE_BASE_URL=https://<application-copilot-cloud-run-url>
CAREER_INTERNAL_TOKEN=<same Secret Manager value used by the service>
```
