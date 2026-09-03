# Chrome Web Store Review Instructions

These instructions are intended for the private **Test instructions** field in the Chrome Web Store Developer Dashboard. Do not commit reviewer credentials to the repository or include them in the extension ZIP.

## Prerequisites

1. Use the temporary reviewer account supplied in the Developer Dashboard test-instructions field.
2. The reviewer account must have access to the yuqi.site MCP Career tools and a non-sensitive sample profile.
3. A sample PDF must be active in the reviewer Resume Vault.

## Core review path

1. Install the extension and pin **Yuqi Application Copilot**.
2. Open the extension and select **Sign in**.
3. Sign in to yuqi.site with the temporary reviewer account, then reopen the extension.
4. Open the reviewer-safe sample application URL supplied in the dashboard.
5. Select **Auto-fill Application**.
6. Confirm that the extension detects visible fields, resolves sample values, and displays provenance and review status.
7. Confirm that deterministic sample fields are filled and the sample PDF is attached.
8. Confirm that sensitive or uncertain fields remain review-gated.
9. Confirm that the extension identifies the final action but does not click it.

## Optional local classifier

The `nativeMessaging` permission supports an optional local Codex classifier for unfamiliar, non-sensitive labels. The extension remains functional when the native host is absent by using the authenticated server-side fallback. Reviewer credentials, passwords, resume files, sensitive answers, and full page content are never sent to the local classifier.

## Expected safety behavior

- No automatic final submission.
- No CAPTCHA or MFA completion.
- No automatic acceptance of terms, attestations, or signatures.
- No silent filling of sensitive answers.
- No access to unrelated tabs or browsing history.

