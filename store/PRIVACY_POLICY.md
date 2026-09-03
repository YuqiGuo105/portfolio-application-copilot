# Yuqi Application Copilot Privacy Policy

Effective date: September 3, 2026

Yuqi Application Copilot is an assisted job-application tool. It processes only the information needed to scan a user-selected application form, resolve approved answers, attach a resume, and fill the fields the user chooses. It does not submit applications on the user's behalf.

## Information the extension processes

- **Application page content:** visible field labels, available choices, control types, the page title, and the canonical HTTPS origin and path.
- **Profile information:** user-approved contact, education, experience, skills, work authorization, and recurring application answers.
- **Resume files:** the active PDF selected from the private Resume Vault or a one-time local PDF selected by the user.
- **Authentication information:** a short-lived yuqi.site access token retained in Chrome session storage.
- **Operational information:** an application workflow identifier, resolution status, integrity metadata, and error details needed to complete or troubleshoot the requested action.

The extension does not intentionally collect full browsing history, page screenshots, payment information, or information from tabs the user has not selected for application assistance.

## How information is used

Information is used only to:

1. identify fields on the application page selected by the user;
2. retrieve the user's approved profile and active resume;
3. resolve and review potential answers;
4. fill fields explicitly selected or approved by the user; and
5. record an auditable application workflow without performing final submission.

The extension does not sell user data, use it for advertising, transfer it to data brokers, or use it for unrelated lending, employment eligibility, or credit decisions.

## Storage and retention

- The access token is stored in `chrome.storage.session` and is cleared when the browser session ends.
- A short-lived local cache may retain sanitized field classifications to reduce repeated model calls. It does not contain passwords, resume files, immigration answers, or full page content.
- Private profile data and resume metadata are stored by the authenticated yuqi.site Career service. Sensitive values are encrypted at rest.
- Resume downloads use short-lived signed URLs. A one-time local resume remains on the user's device until the user applies it to the selected page.
- Workflow and audit records are retained only for security, reliability, and user-requested application history.

## Data sharing

The extension sends the minimum required field metadata to the first-party yuqi.site MCP and Career services. Unfamiliar non-sensitive labels may be classified by a local Codex process on the user's computer or by the configured first-party model service when local classification is unavailable. Sensitive answers, passwords, resume files, and full page bodies are excluded from local model prompts.

No personal data is sold or shared with advertisers. Application information is disclosed to the job application site only when the user chooses to fill that site's form.

## Security

Network requests use HTTPS. Authentication and authorization are enforced by the yuqi.site MCP gateway. Private data is encrypted at rest, resume downloads are integrity-checked, and privileged operations are confirmation-gated and audited.

## User control

Users can review resolved values before applying them, leave any field blank, use a one-time local resume, sign out to clear the extension session, or request deletion of private profile and workflow data. CAPTCHA, MFA, legal terms, attestations, signatures, and final submission always remain manual.

## Limited use

Use of information received through Chrome APIs follows the Chrome Web Store User Data Policy, including its Limited Use requirements. Data is used only to provide or improve the extension's prominent, user-facing application-assistance feature.

## Contact

Questions or deletion requests may be sent to yuqi.guo17@gmail.com.

