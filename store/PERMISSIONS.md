# Chrome Web Store Privacy Disclosures

## Single purpose

Assist an authorized user with reviewing and filling a job application from an owner-managed profile and resume while keeping sensitive answers and final submission under explicit user control.

## Permission justifications

| Permission | Justification |
| --- | --- |
| `activeTab` | Grants temporary access only to the application tab the user has selected and only after the user invokes the extension. |
| `scripting` | Injects the bundled form scanner and field writer into the selected tab after the user starts auto-fill. No remote code is executed. |
| `tabs` | Reads the selected tab's title and HTTPS URL, and opens or focuses the first-party yuqi.site sign-in page when authentication is required. |
| `favicon` | Displays the selected application's site icon in the workflow summary. |
| `storage` | Stores the first-party endpoint setting, a browser-session access token, a bounded classification cache, and the active workflow reference. |
| `nativeMessaging` | Optionally connects to the user's locally installed Codex classifier for unfamiliar, non-sensitive field labels. The extension falls back safely when the host is not installed. |

## Host permission justifications

| Host | Justification |
| --- | --- |
| `https://www.yuqi.site/*` | Authenticates the authorized owner and invokes the first-party MCP endpoint used for profile, policy, workflow, and resume operations. |
| `https://iyvhmpdfrnznxgyvvkvx.supabase.co/*` | Downloads the active private resume only through a short-lived signed URL issued by the first-party Career service. The extension has no service-role key and cannot list the bucket. |

## Remote code

No. All executable JavaScript is packaged with the extension. Network services return data only; the extension does not download or execute remote scripts, WebAssembly, or dynamic code.

## Data-use selections

Disclose the following because the extension processes them for its user-facing purpose:

- Personally identifiable information
- Authentication information
- Website content
- Form data
- Web browsing activity limited to the selected application's HTTPS origin and path

Do not select financial/payment information, health information, personal communications, location, or user-generated content unless a future release begins processing those categories.

