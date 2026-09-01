export function calculateApplicationProgress({ fields = [], files = [], resolutions = [], hasResume = false }) {
  const requiredGroups = groupFields(fields.filter((field) => field.required));
  const requiredFiles = files.filter((file) => file.required);
  const resolutionById = new Map(resolutions.map((item) => [item.fieldId, item]));

  if (requiredGroups.size || requiredFiles.length) {
    const preparedFields = [...requiredGroups.values()].filter((fieldIds) =>
      fieldIds.some((fieldId) => resolutionById.get(fieldId)?.value != null)).length;
    const preparedFiles = hasResume ? requiredFiles.length : 0;
    return progressResult(preparedFields + preparedFiles, requiredGroups.size + requiredFiles.length, true);
  }

  const detectedGroups = groupFields(fields);
  const preparedFields = [...detectedGroups.values()].filter((fieldIds) =>
    fieldIds.some((fieldId) => resolutionById.get(fieldId)?.value != null)).length;
  return progressResult(preparedFields, detectedGroups.size, false);
}

function groupFields(fields) {
  const groups = new Map();
  fields.forEach((field) => {
    const groupId = field.requirementId || field.id;
    if (!groups.has(groupId)) groups.set(groupId, []);
    groups.get(groupId).push(field.id);
  });
  return groups;
}

function progressResult(prepared, total, required) {
  return {
    prepared,
    total,
    required,
    percent: total ? Math.round((prepared / total) * 100) : 0
  };
}
