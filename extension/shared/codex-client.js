const HOST_NAME = 'site.yuqi.application_copilot';

export async function resolveWithLocalCodex({ page, fields, profile }) {
  if (!fields.length) return [];
  const response = await chrome.runtime.sendNativeMessage(HOST_NAME, {
    type: 'resolve_fields',
    requestId: crypto.randomUUID(),
    page: {
      origin: page.origin,
      title: page.title,
      pageType: page.pageType
    },
    fields,
    profile: sanitizeProfile(profile)
  });
  if (!response?.ok) throw new Error(response?.error || 'Local Codex advisor is unavailable.');
  return response.result?.fields || [];
}

function sanitizeProfile(profile = {}) {
  const pick = (item = {}) => Object.fromEntries(
    ['title', 'name', 'position', 'company', 'organization']
      .filter((key) => item[key] != null)
      .map((key) => [key, String(item[key])])
  );
  return {
    summary: String(profile.summary || ''),
    skills: Array.isArray(profile.skills) ? profile.skills.map(String) : [],
    experience: Array.isArray(profile.experience) ? profile.experience.map(pick) : [],
    projects: Array.isArray(profile.projects) ? profile.projects.map(pick) : []
  };
}
