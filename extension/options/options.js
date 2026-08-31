const endpoint = document.querySelector('#endpoint');
const status = document.querySelector('#status');
chrome.storage.local.get({ mcpEndpoint: endpoint.value }).then((stored) => { endpoint.value = stored.mcpEndpoint; });
document.querySelector('#save').addEventListener('click', async () => {
  const value = endpoint.value.trim().replace(/\/+$/, '');
  if (!/^https:\/\//.test(value)) { status.textContent = 'Use an HTTPS endpoint.'; return; }
  await chrome.storage.local.set({ mcpEndpoint: value });
  status.textContent = 'Saved.';
});
