# Portfolio Application Copilot

Java 21 / Spring Boot domain service plus a Chrome Manifest V3 client for MCP-mediated job application assistance.

**[Install from the Chrome Web Store](https://chromewebstore.google.com/detail/yuqi-application-copilot/kgebalpnomjfemfeeiaphpaomkkccebd)**

## Boundary

```text
Chrome MV3 / Codex / Copilot / other MCP clients
          -> https://www.yuqi.site/mcp/admin
          -> portfolio-mcp-gateway (RBAC, confirmation, audit, idempotency)
          -> portfolio-application-copilot (profile snapshot, rules, account workflow)
          -> dedicated Career PostgreSQL (encrypted private resumes, answers, audit)
          -> private Supabase Storage (versioned application PDF assets)
          -> Valkey (bounded cache and per-origin credentials)

Profile cache miss
          -> portfolio-mcp-gateway/admin.search_content
          -> first-party portfolio content projection
```

For unresolved, non-sensitive form questions, the Chrome extension can call a local Java Native Messaging host. The host invokes the Codex CLI through the user's existing Codex session and returns schema-validated suggestions. It does not require a separate model API key and never receives passwords, private application preferences, immigration answers, resume files, or the full page body.

The service never reads Supabase profile tables. Supabase authentication may identify the admin at the public MCP edge, while all profile reads and application operations remain MCP tools.

Sensitive answers are never inferred. The resolver returns `NEEDS_CONFIRMATION` for work authorization, sponsorship, compensation, relocation, EEO, signature, and background-check fields.

## Runtime ownership

- `extension/` is intentionally plain JavaScript because Chrome Manifest V3 runs in the browser. It scans the active application page, renders a review, and applies approved values. It never owns candidate data or policy decisions.
- This Spring Boot service owns the candidate snapshot, deterministic field resolution, encrypted private-answer memory, and refresh workflow.
- The Application Workflow aggregate owns lifecycle transitions and an immutable receipt timeline; the browser only reports observed facts.
- `portfolio-mcp-server` owns Supabase authentication at `/mcp/admin`; Supabase is not a candidate-profile database.
- `portfolio-mcp-gateway` owns RBAC, confirmation, audit, idempotency, and downstream routing for every `career.*` tool.

The extension never silently submits a job application or registration. It fills reviewed fields, and sensitive fields remain confirmation-gated. CAPTCHA, MFA, terms acceptance, attestations, signatures, EEO answers, and the final submit remain user actions. This boundary applies even when Codex can produce a plausible answer.

## MCP tools

| Tool | Mode | Purpose |
| --- | --- | --- |
| `career.get_candidate_profile` | Read | Return the current owner-only candidate snapshot. |
| `career.refresh_candidate_profile` | Write | Rebuild public profile data through `admin.search_content` in the existing MCP cluster. |
| `career.resolve_application_fields` | Read | Resolve a scanned form with source, confidence, and review status. |
| `career.start_application_workflow` / `career.get_application_workflow` | Command / query | Create an idempotent attempt and inspect its state plus event timeline. |
| `career.record_application_resolution` / `career.record_application_review` | Command | Persist policy output and explicit owner approval. |
| `career.record_application_fill` | Command | Record fields written to the page without submitting it. |
| `career.record_submission_receipt` | Command | Record a same-origin ATS success page after manual submit. |
| `career.confirm_application_submission` | Confirmed command | Attach an independent provider confirmation such as an email message ID. |
| `career.update_private_answers` | Confirmed write | Update encrypted recurring answers such as contact details or user-declared work authorization. |
| `career.get_site_credential` | Confirmed read | Retrieve the owner-only credential for the exact HTTPS origin currently open in Chrome. |
| `career.prepare_site_credential` | Confirmed write | Create or reuse a unique encrypted credential for an owner-reviewed sign-up. |
| `career.list_private_resumes` / `career.get_private_resume` | Confirmed read | Read owner-only application resume variants from the encrypted Career Vault. |
| `career.create_private_resume` / `career.update_private_resume` | Confirmed write | Create or revise an application-only resume without changing the public CV. |
| `career.delete_private_resume` | Confirmed write | Soft-delete an application resume while retaining its mutation audit. |
| `career.get_private_answers` | Confirmed read | Read owner-approved answers for recurring application questions. |
| `career.upsert_private_answer` / `career.delete_private_answer` | Confirmed write | Maintain private application memory such as H-1B or I-140 status. |
| `career.list_resume_assets` / `career.get_active_resume_asset` | Owner read | List private PDF versions or read active metadata without exposing file content. |
| `career.prepare_resume_upload` / `career.complete_resume_upload` | Confirmed write | Issue a signed upload, then validate PDF magic bytes, size, and SHA-256 before atomic activation. |
| `career.activate_resume_asset` | Confirmed write | Roll back the active pointer to a previously validated PDF version. |
| `career.get_active_resume_download` | Confirmed read | Issue a short-lived signed URL for the active application PDF. |
| `resume.get_current` / `resume.list_versions` / `resume.get_version` | Owner read | Read the public CV and its version history from the Admin Service. |
| `resume.create_revision` / `resume.update_draft` / `resume.publish_revision` | Confirmed write | Stage and publish a versioned public CV revision. |
| `resume.rollback_revision` / `resume.delete_draft` | Confirmed write | Roll back to an earlier public revision or archive an unpublished draft. |

All tools above require the managed `ADMIN` role and owner capability at `/mcp/admin`. The gateway authenticates the service-to-service hop with `X-Internal-Token`.

## Application workflow

```text
SCANNED -> RESOLVED -> REVIEWED -> READY_TO_SUBMIT -> SUBMITTED -> CONFIRMED
   |           |           |              |               |
   +-----------+-----------+--------------+---------------> FAILED / CANCELLED
```

1. Chrome scans labels, choices and controls, creates an `applicationId`, and starts the aggregate through MCP.
2. An ordered `Policy + Chain of Responsibility` resolves private memory first, then sensitive-field policy, then public profile rules.
3. Each result carries provenance, confidence and `RESOLVED`, `NEEDS_CONFIRMATION`, or `UNSUPPORTED` status.
4. Only unsupported, non-sensitive fields may reach the local Codex advisor; suggestions remain unchecked.
5. Explicit owner selections transition the aggregate to `REVIEWED`; native browser events then fill approved values.
6. A successful fill records `READY_TO_SUBMIT`. The extension identifies Continue or Submit controls but never clicks them.
7. After manual submission, a same-origin ATS success page records `SUBMITTED`; query strings and fragments are removed before persistence.
8. `CONFIRMED` requires a second signal, such as a provider email message ID. Missing email therefore remains visible rather than being misreported as success.

### Design patterns and invariants

- **Aggregate + State Machine:** one application attempt owns valid transitions and count invariants; stages cannot be skipped.
- **Policy + Chain of Responsibility:** sensitive policy is deterministic and evaluated before profile fallback; new field strategies are independently testable.
- **Repository + Optimistic Lock:** PostgreSQL is authoritative and `@Version` rejects stale concurrent mutations.
- **Command/Query separation:** MCP mutations are explicit workflow commands; timeline retrieval is a read-only query.
- **Transactional Audit Log:** each state mutation and event append share one database transaction.
- **Idempotency:** the gateway deduplicates writes, while the aggregate tolerates repeated delivery of an already-recorded stage.
- **Privacy by design:** only field metadata crosses the browser boundary; ATS URLs are reduced to HTTPS origin and path before storage.

### Local Codex advisor

```text
Chrome extension
    -> Native Messaging (length-prefixed JSON)
    -> Java Native Host
    -> sanitize + sensitive-field exclusion
    -> local Codex CLI (ephemeral, read-only, low reasoning)
    -> strict JSON schema validation
    -> unchecked suggestion in the review UI
```

Install the native host after loading the unpacked extension and copying its Chrome extension ID:

```bash
./scripts/install-native-host.sh <chrome-extension-id>
```

The generated Native Messaging manifest is written to the current macOS user's Chrome profile. `YUQI_CODEX_PATH`, `YUQI_CODEX_TIMEOUT_SECONDS`, and `YUQI_CODEX_REASONING_EFFORT` may be set in the host environment; the default reasoning effort is `low`. Re-run the installer when the extension ID or project path changes.

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
5. Open an application, registration, or login page and scan it. The active managed PDF is selected by default when the page has a resume field.
6. Optionally choose a local PDF as a one-time override. The file remains local until **Apply** and is never persisted by the extension.
7. Review provenance before filling application fields. Registration and login assistance require a separate explicit button click.

The extension requests only `activeTab` for application pages. Permanent host access is limited to `yuqi.site` for the authenticated MCP endpoint and the configured Supabase project for short-lived signed downloads. It never receives the Storage service-role key.

### Resume Asset Vault

```text
MCP prepare upload -> private signed upload URL -> browser/client uploads PDF
                   -> MCP complete upload -> backend downloads with service role
                   -> PDF/size/SHA-256 validation -> atomic active-version swap

Chrome scan -> active metadata -> explicit Apply -> confirmed signed download
            -> browser verifies size + SHA-256 -> attaches PDF -> manual submit
```

Resume binaries live in the private `career-resumes` bucket. PostgreSQL stores metadata, version state, integrity hashes, and audit records; it does not store public object URLs. Old validated files remain archived for rollback. Signed upload URLs expire after two hours and signed downloads use the configured short TTL. No resume binary or private answer is projected into public CV, Search, RAG, OpenSearch, analytics, logs, or notification content.

Run the deterministic Workable fixture before loading the extension:

```bash
cd extension
npm ci
npm test
```

The fixture asserts that the scanner detects the application and resume controls, applies four reviewed values, identifies the final action, and leaves the form unsubmitted.

## Deployment

`cloudbuild.yaml` deploys the Java service to Cloud Run with one vCPU, 512 MiB memory, concurrency 20, `min-instances=0`, and `max-instances=1`. This preserves scale-to-zero cost behavior. The service uses routable Cloud Run ingress so the MCP Gateway can call it without a VPC connector, while Cloud Run IAM rejects anonymous callers and only the gateway runtime service account receives `roles/run.invoker`. Every `/internal/**` operation additionally fails closed without the shared service token. Secrets are injected from Secret Manager; never commit them or expose them to the extension.

The Career Vault uses a dedicated `career` schema in the managed PostgreSQL instance. Cloud Run maps the existing `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD` secrets to the service's `CAREER_DATABASE_*` environment variables, avoiding duplicate database credentials. `CAREER_OWNER_KEY` encrypts resume bodies and private answers with AES-GCM before persistence. PostgreSQL is authoritative; Valkey is only a bounded cache, so cache eviction never loses the application profile. Flyway owns only the `career` schema and applies its private-vault migrations at startup.

`SUPABASE_SERVICE_ROLE_KEY` is injected only into the Cloud Run backend. The `career-resumes` bucket is private, capped at 2 MiB per object, and accepts only `application/pdf`. The extension can use only backend-issued signed URLs; it cannot list the bucket or mint URLs itself.

The public CV remains owned by `portfolio-admin-service`. Its public API exposes only the country-level work location `United States`; application resumes and immigration answers are never copied into `cv_content`, public search, RAG, or OpenSearch projections.

The MCP Gateway also needs:

```text
CAREER_SERVICE_BASE_URL=https://<application-copilot-cloud-run-url>
CAREER_INTERNAL_TOKEN=<same Secret Manager value used by the service>
```
