export function reviewedPageTarget(page, tab) {
  if (!page?.documentId || !page.origin || tab?.id !== page.tabId ||
      !/^https?:/.test(tab.url || '') || new URL(tab.url).origin !== page.origin) {
    throw new Error('The application page changed. Scan and review it again before filling.');
  }
  return { tabId: tab.id, documentIds: [page.documentId] };
}
