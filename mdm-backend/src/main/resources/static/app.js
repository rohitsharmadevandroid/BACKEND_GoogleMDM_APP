// MDM Admin Dashboard - vanilla JS, no build step, served same-origin from
// Spring Boot's static resources (so no CORS setup needed). The JWT is
// kept in sessionStorage; that's readable by any injected script (a real
// XSS risk in general), which is why every piece of server-returned text
// gets run through escapeHtml() before landing in innerHTML below - the
// one XSS vector we do control.

let state = { token: null, role: null, organizationId: null, activeOrgId: null, email: null };

// Polls a device's command history while its detail view is open, so
// status/result updates (SENT -> COMPLETED, etc.) show up without a manual
// page reload. Only one of these ever runs at a time - stopCommandPolling()
// is called before starting a new one and on navigating away.
let commandPollTimer = null;

function stopCommandPolling() {
  if (commandPollTimer) {
    clearInterval(commandPollTimer);
    commandPollTimer = null;
  }
}

// Same idea as commandPollTimer, but for the Enrollment Tokens list - so a
// token flipping ACTIVE -> CONSUMED (a device just enrolled with it) or
// -> REVOKED shows up without a manual reload.
let tokenPollTimer = null;

function stopTokenPolling() {
  if (tokenPollTimer) {
    clearInterval(tokenPollTimer);
    tokenPollTimer = null;
  }
}

function escapeHtml(value) {
  if (value === null || value === undefined) return '';
  return String(value).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

function saveSession() {
  sessionStorage.setItem('mdm_session', JSON.stringify(state));
}

async function api(path, options = {}) {
  const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) };
  if (state.token) headers['Authorization'] = 'Bearer ' + state.token;
  const res = await fetch(path, { ...options, headers });
  if (res.status === 401) {
    logout('Session expired - please log in again.');
    throw new Error('Unauthorized');
  }
  const text = await res.text();
  let data = null;
  if (text) {
    try { data = JSON.parse(text); } catch (e) { data = text; }
  }
  if (!res.ok) {
    const message = (data && typeof data === 'object' && data.error) ? data.error : (res.status + ' ' + res.statusText);
    throw new Error(message);
  }
  return data;
}

// ---- Auth ----

async function login(email, password) {
  const data = await api('/api/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) });
  state = { token: data.token, role: data.role, organizationId: data.organizationId, activeOrgId: data.organizationId, email };
  saveSession();
  showApp();
}

function logout(message) {
  stopCommandPolling();
  stopTokenPolling();
  state = { token: null, role: null, organizationId: null, activeOrgId: null, email: null };
  sessionStorage.removeItem('mdm_session');
  showLogin(message);
}

function showLogin(message) {
  document.getElementById('app-view').style.display = 'none';
  document.getElementById('login-view').style.display = 'flex';
  document.getElementById('login-error').textContent = message || '';
}

function showApp() {
  document.getElementById('login-view').style.display = 'none';
  document.getElementById('app-view').style.display = 'block';
  document.getElementById('header-user').textContent = `${state.email} (${state.role})`;
  renderSidebar();
  navigate(state.role === 'SUPER_ADMIN' && !state.activeOrgId ? 'organizations' : 'devices');
}

// ---- Navigation ----

const RENDERERS = {
  organizations: renderOrganizations,
  devices: renderDevices,
  policies: renderPolicies,
  tokens: renderTokens,
  'org-admins': renderOrgAdmins,
  'all-admins': renderAllAdmins,
  gms: renderGms,
};

function navigate(section) {
  stopCommandPolling();
  stopTokenPolling();
  document.querySelectorAll('.nav-btn[data-section]').forEach((b) => b.classList.toggle('active', b.dataset.section === section));
  const content = document.getElementById('content');
  content.innerHTML = '<p class="loading">Loading…</p>';
  const renderer = RENDERERS[section];
  if (renderer) renderer(); else content.innerHTML = '<p>Unknown section.</p>';
}

function renderSidebar() {
  const nav = document.getElementById('sidebar-nav');
  const links = [];
  if (state.role === 'SUPER_ADMIN') links.push(['organizations', 'Organizations']);
  if (state.activeOrgId) {
    links.push(['devices', 'Devices']);
    links.push(['policies', 'Policies']);
    links.push(['tokens', 'Enrollment Tokens']);
    links.push(['gms', 'GMS Enterprise']);
    links.push(['org-admins', 'Admins (this org)']);
  }
  if (state.role === 'SUPER_ADMIN') links.push(['all-admins', 'All Admin Users']);

  nav.innerHTML = links.map(([id, label]) => `<button class="nav-btn" data-section="${id}">${escapeHtml(label)}</button>`).join('')
    // Both buttons do the exact same thing under the hood - clear the
    // session and show the login form - there's no real "switch role"
    // concept (that would mean a client-side privilege escalation, which
    // this app deliberately never allows: your role comes from which
    // account you authenticate as, nothing else). "Switch account" is just
    // a more honestly-labeled entry point for "I want to log in as someone
    // else right now", sitting next to "Log out" rather than replacing it.
    + '<button class="nav-btn switch-account" id="switch-account-btn">Switch account</button>'
    + '<button class="nav-btn logout" id="logout-btn">Log out</button>';
  nav.querySelectorAll('.nav-btn[data-section]').forEach((btn) => btn.addEventListener('click', () => navigate(btn.dataset.section)));
  document.getElementById('switch-account-btn').addEventListener('click', () => logout());
  document.getElementById('logout-btn').addEventListener('click', () => logout());
}

function enterOrganization(orgId) {
  state.activeOrgId = orgId;
  saveSession();
  renderSidebar();
  navigate('devices');
}

// ---- Organizations (SUPER_ADMIN only) ----

async function renderOrganizations() {
  const content = document.getElementById('content');
  try {
    const orgs = await api('/api/organizations');
    content.innerHTML = `
      <h2>Organizations</h2>
      <form id="create-org-form" class="inline-form">
        <input name="name" placeholder="Name" required>
        <input name="slug" placeholder="slug-like-this" required>
        <button type="submit">Create</button>
      </form>
      <p id="create-org-error" class="error"></p>
      <table>
        <thead><tr><th>Name</th><th>Slug</th><th>Status</th><th></th></tr></thead>
        <tbody>
          ${orgs.map((o) => `
            <tr>
              <td>${escapeHtml(o.name)}</td>
              <td>${escapeHtml(o.slug)}</td>
              <td>${escapeHtml(o.status)}</td>
              <td>
                <button class="manage-btn" data-org-id="${o.id}">Manage</button>
                <button class="delete-org-btn" data-org-id="${o.id}" data-org-name="${escapeHtml(o.name)}">Delete</button>
              </td>
            </tr>
          `).join('')}
        </tbody>
      </table>
      <p id="delete-org-error" class="error"></p>`;

    content.querySelector('#create-org-form').addEventListener('submit', async (e) => {
      e.preventDefault();
      const fd = new FormData(e.target);
      try {
        await api('/api/organizations', { method: 'POST', body: JSON.stringify({ name: fd.get('name'), slug: fd.get('slug') }) });
        renderOrganizations();
      } catch (err) {
        content.querySelector('#create-org-error').textContent = err.message;
      }
    });
    content.querySelectorAll('.manage-btn').forEach((btn) => btn.addEventListener('click', () => enterOrganization(btn.dataset.orgId)));
    content.querySelectorAll('.delete-org-btn').forEach((btn) => btn.addEventListener('click', async () => {
      const name = btn.dataset.orgName;
      if (!confirm(`Permanently delete "${name}" and everything under it - all its devices, policies, commands, and enrollment tokens? This cannot be undone.`)) return;
      try {
        await api(`/api/organizations/${btn.dataset.orgId}`, { method: 'DELETE' });
        renderOrganizations();
      } catch (err) {
        content.querySelector('#delete-org-error').textContent = err.message;
      }
    }));
  } catch (err) {
    content.innerHTML = `<p class="error">${escapeHtml(err.message)}</p>`;
  }
}

// ---- Devices ----

async function renderDevices() {
  const content = document.getElementById('content');
  try {
    const devices = await api(`/api/organizations/${state.activeOrgId}/devices`);
    content.innerHTML = `
      <h2>Devices</h2>
      <table>
        <thead><tr><th>Name</th><th>Type</th><th>Status</th><th>Model</th><th>Policy</th><th>Last seen</th><th></th></tr></thead>
        <tbody>
          ${devices.map((d) => `
            <tr>
              <td>${escapeHtml(d.displayName || '(unnamed)')}</td>
              <td>${escapeHtml(d.deviceType)}</td>
              <td>${escapeHtml(d.status)}</td>
              <td>${escapeHtml(d.model || '-')}</td>
              <td>${escapeHtml(d.policyName || '-')}</td>
              <td>${d.lastSeenAt ? new Date(d.lastSeenAt).toLocaleString() : 'never'}</td>
              <td><button class="detail-btn" data-device-id="${d.id}">Details</button></td>
            </tr>
          `).join('')}
        </tbody>
      </table>
      <div id="device-detail"></div>`;
    content.querySelectorAll('.detail-btn').forEach((btn) => btn.addEventListener('click', () => renderDeviceDetail(btn.dataset.deviceId)));
    if (devices.length === 0) content.insertAdjacentHTML('beforeend', '<p class="loading">No devices yet.</p>');
  } catch (err) {
    content.innerHTML = `<p class="error">${escapeHtml(err.message)}</p>`;
  }
}

function commandRowsHtml(commands) {
  return commands.map((c) => `
    <tr>
      <td>${escapeHtml(c.commandType)}</td>
      <td>${escapeHtml(c.status)}</td>
      <td>${c.createdAt ? new Date(c.createdAt).toLocaleString() : ''}</td>
      <td>${escapeHtml(c.errorMessage || '')}</td>
      <td>${Object.keys(c.resultData || {}).length ? `<pre>${escapeHtml(JSON.stringify(c.resultData, null, 2))}</pre>` : '&mdash;'}</td>
    </tr>
  `).join('');
}

async function refreshCommandHistory(deviceId) {
  const body = document.getElementById('command-history-body');
  if (!body) { stopCommandPolling(); return; }
  try {
    const commands = await api(`/api/devices/${deviceId}/commands`);
    body.innerHTML = commandRowsHtml(commands);
  } catch (err) {
    stopCommandPolling();
  }
}

async function renderDeviceDetail(deviceId) {
  stopCommandPolling();
  const el = document.getElementById('device-detail');
  el.innerHTML = '<p class="loading">Loading…</p>';
  try {
    const [device, commands, policies] = await Promise.all([
      api(`/api/devices/${deviceId}`),
      api(`/api/devices/${deviceId}/commands`),
      api(`/api/organizations/${state.activeOrgId}/policies`),
    ]);
    el.innerHTML = `
      <h3>${escapeHtml(device.displayName || '(unnamed device)')}</h3>
      <p>Status: ${escapeHtml(device.status)} &middot; Type: ${escapeHtml(device.deviceType)} &middot; Policy: ${escapeHtml(device.policyName || 'None')} &middot; ID: <code>${escapeHtml(device.deviceUid || device.gmsDeviceResourceName || device.id)}</code></p>
      <button id="unenroll-btn">Unenroll device</button>
      <p id="unenroll-error" class="error"></p>

      <h4>Display name</h4>
      <form id="display-name-form" class="inline-form">
        <input name="displayName" placeholder="e.g. Primebook - Front Desk" value="${escapeHtml(device.displayName || '')}">
        <button type="submit">Save name</button>
      </form>
      <p id="display-name-error" class="error"></p>

      <h4>Policy</h4>
      <form id="policy-form" class="inline-form">
        <select name="policyId">
          <option value="">None</option>
          ${policies.map((p) => `<option value="${p.id}" ${p.id === device.policyId ? 'selected' : ''}>${escapeHtml(p.name)}${p.isActive ? '' : ' (inactive)'}</option>`).join('')}
        </select>
        <button type="submit">Save policy</button>
      </form>
      <p id="policy-error" class="error"></p>

      <h4>Issue command</h4>
      <form id="command-form" class="inline-form">
        <select name="commandType">
          <option value="LOCK">LOCK</option>
          <option value="WIPE">WIPE</option>
          <option value="REBOOT">REBOOT</option>
          <option value="RESET_PASSWORD">RESET_PASSWORD</option>
          <option value="CLEAR_APP_DATA">CLEAR_APP_DATA</option>
          <option value="REQUEST_DEVICE_INFO">REQUEST_DEVICE_INFO</option>
        </select>
        <input name="params" placeholder='optional params JSON, e.g. {"lockDurationSeconds":300}' style="width:320px">
        <button type="submit">Issue</button>
      </form>
      <p id="command-error" class="error"></p>

      <h4>Command history <span class="loading">(auto-refreshes every 5s)</span></h4>
      <table>
        <thead><tr><th>Type</th><th>Status</th><th>Created</th><th>Error</th><th>Result data</th></tr></thead>
        <tbody id="command-history-body">${commandRowsHtml(commands)}</tbody>
      </table>

      ${device.deviceType === 'NON_GMS' ? `
        <details>
          <summary>GMS Migration (advanced)</summary>
          <p class="loading">Moves this device from our custom DPC to being managed for real via Google's Android Management API / Android Device Policy - a separate thing from a fresh GMS enrollment. playDeviceId and playUserId come from the device's own AMAPI SDK (AccountSetupClient's resulting EnterpriseAccount) - they can't be looked up here, the app must report them to you first.</p>
          <form id="migration-token-form" class="stacked-form">
            <input name="playDeviceId" placeholder="playDeviceId (from EnterpriseAccount.getDeviceId())" required>
            <input name="playUserId" placeholder="playUserId (from EnterpriseAccount.getUserId())" required>
            <select name="policyId" required>
              <option value="">Select a policy to apply after migration</option>
              ${policies.map((p) => `<option value="${p.id}">${escapeHtml(p.name)}${p.isActive ? '' : ' (inactive)'}</option>`).join('')}
            </select>
            <input name="ttlSeconds" type="number" min="1" placeholder="Token TTL in seconds (optional, max 7 days)">
            <button type="submit">Create migration token</button>
          </form>
          <p id="migration-token-error" class="error"></p>
          <div id="migration-token-result"></div>
        </details>
      ` : ''}`;

    el.querySelector('#unenroll-btn').addEventListener('click', async () => {
      if (!confirm('Unenroll this device? For GMS devices this also wipes it; for non-GMS devices, queue and confirm a WIPE command first if you need data wiped too.')) return;
      try {
        await api(`/api/devices/${deviceId}`, { method: 'DELETE', body: JSON.stringify({}) });
        stopCommandPolling();
        renderDevices();
      } catch (err) {
        el.querySelector('#unenroll-error').textContent = err.message;
      }
    });

    el.querySelector('#display-name-form').addEventListener('submit', async (e) => {
      e.preventDefault();
      const fd = new FormData(e.target);
      try {
        await api(`/api/devices/${deviceId}/display-name`, {
          method: 'PUT',
          body: JSON.stringify({ displayName: fd.get('displayName') || null }),
        });
        renderDeviceDetail(deviceId);
      } catch (err) {
        el.querySelector('#display-name-error').textContent = err.message;
      }
    });

    el.querySelector('#policy-form').addEventListener('submit', async (e) => {
      e.preventDefault();
      const fd = new FormData(e.target);
      const policyId = fd.get('policyId') || null;
      try {
        await api(`/api/devices/${deviceId}/policy`, { method: 'PUT', body: JSON.stringify({ policyId }) });
        renderDeviceDetail(deviceId);
      } catch (err) {
        el.querySelector('#policy-error').textContent = err.message;
      }
    });

    el.querySelector('#command-form').addEventListener('submit', async (e) => {
      e.preventDefault();
      const fd = new FormData(e.target);
      let params = {};
      const raw = (fd.get('params') || '').trim();
      if (raw) {
        try { params = JSON.parse(raw); } catch (err) {
          el.querySelector('#command-error').textContent = 'Params must be valid JSON';
          return;
        }
      }
      try {
        await api(`/api/devices/${deviceId}/commands?commandType=${encodeURIComponent(fd.get('commandType'))}`, {
          method: 'POST',
          body: JSON.stringify(params),
        });
        renderDeviceDetail(deviceId);
      } catch (err) {
        el.querySelector('#command-error').textContent = err.message;
      }
    });

    const migrationForm = el.querySelector('#migration-token-form');
    if (migrationForm) {
      migrationForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const fd = new FormData(e.target);
        const ttlRaw = (fd.get('ttlSeconds') || '').trim();
        try {
          const result = await api(`/api/devices/${deviceId}/gms-migration-token`, {
            method: 'POST',
            body: JSON.stringify({
              playDeviceId: fd.get('playDeviceId'),
              playUserId: fd.get('playUserId'),
              policyId: fd.get('policyId'),
              ttlSeconds: ttlRaw ? parseInt(ttlRaw, 10) : null,
            }),
          });
          document.getElementById('migration-token-result').innerHTML = `
            <p>Created (expires ${escapeHtml(result.expireTime || 'unknown')}). Token value: <code>${escapeHtml(result.value)}</code> <button class="copy-btn" data-copy-value="${escapeHtml(result.value)}">Copy</button></p>
            <p class="loading">Send this value to the device's DpcMigrationClient.migrate() call to complete the migration.</p>`;
          el.querySelectorAll('#migration-token-result .copy-btn').forEach((btn) => btn.addEventListener('click', async () => {
            try {
              await navigator.clipboard.writeText(btn.dataset.copyValue);
              const original = btn.textContent;
              btn.textContent = 'Copied!';
              setTimeout(() => { btn.textContent = original; }, 1500);
            } catch (err) {
              alert('Could not copy to clipboard: ' + err.message);
            }
          }));
        } catch (err) {
          el.querySelector('#migration-token-error').textContent = err.message;
        }
      });
    }

    commandPollTimer = setInterval(() => refreshCommandHistory(deviceId), 5000);
  } catch (err) {
    el.innerHTML = `<p class="error">${escapeHtml(err.message)}</p>`;
  }
}

// ---- Policies ----

const DEFAULT_POLICY_DEFINITION = `{
  "cameraDisabled": false,
  "factoryResetDisabled": false,
  "passwordPolicy": null,
  "kioskMode": null,
  "appRestrictions": [],
  "wifiConfig": null
}`;

async function renderPolicies() {
  const content = document.getElementById('content');
  try {
    const [policies, devices] = await Promise.all([
      api(`/api/organizations/${state.activeOrgId}/policies`),
      api(`/api/organizations/${state.activeOrgId}/devices`),
    ]);
    const devicesByPolicy = {};
    devices.forEach((d) => {
      if (!d.policyId) return;
      if (!devicesByPolicy[d.policyId]) devicesByPolicy[d.policyId] = [];
      devicesByPolicy[d.policyId].push(d);
    });
    content.innerHTML = `
      <h2>Policies</h2>
      <details>
        <summary>Create policy</summary>
        <form id="create-policy-form" class="stacked-form">
          <input name="name" placeholder="Name" required>
          <input name="description" placeholder="Description (optional)">
          <textarea name="definition" rows="7">${escapeHtml(DEFAULT_POLICY_DEFINITION)}</textarea>
          <button type="submit">Create</button>
        </form>
        <p id="create-policy-error" class="error"></p>
      </details>
      <table>
        <thead><tr><th>Name</th><th>Version</th><th>Active</th><th>Devices using it</th><th></th></tr></thead>
        <tbody>
          ${policies.map((p) => `
            <tr>
              <td>${escapeHtml(p.name)}</td>
              <td>${p.version}</td>
              <td>${p.isActive}</td>
              <td>${(devicesByPolicy[p.id] || []).map((d) => escapeHtml(d.displayName || d.model || d.deviceUid || d.id)).join(', ') || '&mdash;'}</td>
              <td>
                <button class="edit-policy-btn" data-policy-id="${p.id}">Edit</button>
                <button class="sync-policy-btn" data-policy-id="${p.id}">Sync</button>
                ${p.isActive ? `<button class="delete-policy-btn" data-policy-id="${p.id}">Delete</button>` : ''}
              </td>
            </tr>
          `).join('')}
        </tbody>
      </table>
      <div id="policy-detail"></div>`;

    content.querySelector('#create-policy-form').addEventListener('submit', async (e) => {
      e.preventDefault();
      const fd = new FormData(e.target);
      let definition;
      try { definition = JSON.parse(fd.get('definition')); } catch (err) {
        content.querySelector('#create-policy-error').textContent = 'Definition must be valid JSON';
        return;
      }
      try {
        await api(`/api/organizations/${state.activeOrgId}/policies`, {
          method: 'POST',
          body: JSON.stringify({ name: fd.get('name'), description: fd.get('description') || null, definition }),
        });
        renderPolicies();
      } catch (err) {
        content.querySelector('#create-policy-error').textContent = err.message;
      }
    });
    content.querySelectorAll('.edit-policy-btn').forEach((btn) => btn.addEventListener('click', () => renderPolicyEdit(btn.dataset.policyId)));
    content.querySelectorAll('.sync-policy-btn').forEach((btn) => btn.addEventListener('click', () => syncPolicy(btn.dataset.policyId)));
    content.querySelectorAll('.delete-policy-btn').forEach((btn) => btn.addEventListener('click', async () => {
      if (!confirm('Delete this policy? Devices currently assigned to it will keep it until reassigned.')) return;
      try {
        await api(`/api/policies/${btn.dataset.policyId}`, { method: 'DELETE' });
        renderPolicies();
      } catch (err) {
        alert(err.message);
      }
    }));
  } catch (err) {
    content.innerHTML = `<p class="error">${escapeHtml(err.message)}</p>`;
  }
}

async function syncPolicy(policyId) {
  const el = document.getElementById('policy-detail');
  el.innerHTML = '<p class="loading">Syncing…</p>';
  try {
    const result = await api(`/api/policies/${policyId}/sync`, { method: 'POST' });
    el.innerHTML = `<h4>Sync result</h4><pre>${escapeHtml(JSON.stringify(result, null, 2))}</pre>`;
  } catch (err) {
    el.innerHTML = `<p class="error">${escapeHtml(err.message)}</p>`;
  }
}

async function renderPolicyEdit(policyId) {
  const el = document.getElementById('policy-detail');
  el.innerHTML = '<p class="loading">Loading…</p>';
  try {
    const policy = await api(`/api/policies/${policyId}`);
    el.innerHTML = `
      <h4>Edit ${escapeHtml(policy.name)} (currently v${policy.version})</h4>
      <form id="update-policy-form" class="stacked-form">
        <textarea name="definition" rows="9">${escapeHtml(JSON.stringify(policy.definition, null, 2))}</textarea>
        <input name="changeNote" placeholder="Change note (optional)">
        <button type="submit">Save (bumps to v${policy.version + 1})</button>
      </form>
      <p id="update-policy-error" class="error"></p>`;

    el.querySelector('#update-policy-form').addEventListener('submit', async (e) => {
      e.preventDefault();
      const fd = new FormData(e.target);
      let definition;
      try { definition = JSON.parse(fd.get('definition')); } catch (err) {
        el.querySelector('#update-policy-error').textContent = 'Definition must be valid JSON';
        return;
      }
      try {
        await api(`/api/policies/${policyId}`, {
          method: 'PUT',
          body: JSON.stringify({ definition, changeNote: fd.get('changeNote') || null }),
        });
        renderPolicies();
      } catch (err) {
        el.querySelector('#update-policy-error').textContent = err.message;
      }
    });
  } catch (err) {
    el.innerHTML = `<p class="error">${escapeHtml(err.message)}</p>`;
  }
}

// ---- Enrollment tokens ----

// Renders a scannable QR code as an <img> tag (data: URL, generated fully
// client-side via the vendored qrcode.js - no external service, works
// offline). typeNumber=0 lets the library auto-pick the smallest QR version
// that fits the data; GMS qrCodeData in particular can be a large JSON blob.
function qrImgTag(data) {
  try {
    const qr = qrcode(0, 'M');
    qr.addData(data);
    qr.make();
    return qr.createImgTag(4, 8);
  } catch (err) {
    return `<p class="error">Could not render QR: ${escapeHtml(err.message)}</p>`;
  }
}

// Shared by every plain "copy this value" button across the dashboard that
// isn't already covered by attachTokenRowListeners' own scoped binding.
function attachCopyButtons(root) {
  if (!root) return;
  root.querySelectorAll('.copy-btn').forEach((btn) => btn.addEventListener('click', async () => {
    try {
      await navigator.clipboard.writeText(btn.dataset.copyValue);
      const original = btn.textContent;
      btn.textContent = 'Copied!';
      setTimeout(() => { btn.textContent = original; }, 1500);
    } catch (err) {
      alert('Could not copy to clipboard: ' + err.message);
    }
  }));
}

function tokenRowsHtml(tokens) {
  return tokens.map((t) => `
    <tr>
      <td>${escapeHtml(t.deviceType)}</td>
      <td><code>${escapeHtml(t.tokenValue)}</code> <button class="copy-btn" data-copy-value="${escapeHtml(t.tokenValue)}">Copy</button></td>
      <td>${escapeHtml(t.status)}</td>
      <td>${t.usedCount} / ${t.maxUses}</td>
      <td>${t.createdAt ? new Date(t.createdAt).toLocaleString() : ''}</td>
      <td>
        ${t.qrCodeData && t.status === 'ACTIVE' && (!t.expiresAt || new Date(t.expiresAt) > new Date()) ? `<button class="qr-btn" data-token-id="${t.id}">Show QR</button>` : ''}
        ${t.status === 'ACTIVE' ? `<button class="revoke-btn" data-token-id="${t.id}">Revoke</button>` : ''}
      </td>
    </tr>
  `).join('');
}

// Rebinds the row-level button listeners against #token-list-body specifically
// (not the whole #content) - called both after the initial render and after
// every polling refresh, since replacing a tbody's innerHTML destroys its
// old listeners.
function attachTokenRowListeners(tokens) {
  const body = document.getElementById('token-list-body');
  if (!body) return;
  body.querySelectorAll('.revoke-btn').forEach((btn) => btn.addEventListener('click', async () => {
    try {
      await api(`/api/enrollment-tokens/${btn.dataset.tokenId}/revoke`, { method: 'POST' });
      renderTokens();
    } catch (err) {
      alert(err.message);
    }
  }));
  body.querySelectorAll('.copy-btn').forEach((btn) => btn.addEventListener('click', async () => {
    try {
      await navigator.clipboard.writeText(btn.dataset.copyValue);
      const original = btn.textContent;
      btn.textContent = 'Copied!';
      setTimeout(() => { btn.textContent = original; }, 1500);
    } catch (err) {
      alert('Could not copy to clipboard: ' + err.message);
    }
  }));
  body.querySelectorAll('.qr-btn').forEach((btn) => btn.addEventListener('click', () => {
    const t = tokens.find((x) => x.id === btn.dataset.tokenId);
    const el = document.getElementById('token-qr-display');
    if (!t) return;
    el.innerHTML = `<h4>QR for ${escapeHtml(t.deviceType)} token</h4>${qrImgTag(t.qrCodeData)}`;
  }));
}

async function refreshTokenRows() {
  const body = document.getElementById('token-list-body');
  if (!body) { stopTokenPolling(); return; }
  try {
    const tokens = await api(`/api/organizations/${state.activeOrgId}/enrollment-tokens`);
    body.innerHTML = tokenRowsHtml(tokens);
    attachTokenRowListeners(tokens);
  } catch (err) {
    stopTokenPolling();
  }
}

async function renderTokens() {
  stopTokenPolling();
  const content = document.getElementById('content');
  try {
    const [tokens, policies, migrationTokens] = await Promise.all([
      api(`/api/organizations/${state.activeOrgId}/enrollment-tokens`),
      api(`/api/organizations/${state.activeOrgId}/policies`),
      api(`/api/organizations/${state.activeOrgId}/gms-migration-tokens`),
    ]);
    content.innerHTML = `
      <h2>Enrollment Tokens <span class="loading">(auto-refreshes every 5s)</span></h2>
      <div class="two-col">
        <form id="create-gms-token-form" class="stacked-form">
          <h4>New GMS token</h4>
          ${policies.length ? `
            <select name="policyId" required>
              ${policies.map((p) => `<option value="${p.id}">${escapeHtml(p.name)}${p.isActive ? '' : ' (inactive)'}</option>`).join('')}
            </select>
            <label><input type="checkbox" name="oneTimeOnly" checked> One-time only</label>
            <label><input type="checkbox" name="allowPersonalUsage" checked> Allow Work Profile / BYOD (uncheck for fully-managed only - requires EMM certification, currently blocked)</label>
            <button type="submit">Create GMS token</button>
          ` : `<p class="loading">Create a policy first - a GMS token needs one to sync to Google.</p>`}
          <p id="gms-token-error" class="error"></p>
        </form>
        <form id="create-nongms-token-form" class="stacked-form">
          <h4>New non-GMS token</h4>
          <input name="maxUses" type="number" min="1" value="1" placeholder="Max uses">
          <button type="submit">Create non-GMS token</button>
          <p id="nongms-token-error" class="error"></p>
        </form>
      </div>
      <div id="new-token-result"></div>
      <table>
        <thead><tr><th>Type</th><th>Token</th><th>Status</th><th>Uses</th><th>Created</th><th></th></tr></thead>
        <tbody id="token-list-body">${tokenRowsHtml(tokens)}</tbody>
      </table>
      <div id="token-qr-display"></div>

      <h3>GMS Migration Tokens</h3>
      <p class="loading">Created from a device's own detail page (GMS Migration section) - listed here too so every token type is in one place.</p>
      <table>
        <thead><tr><th>Device</th><th>Policy</th><th>Token</th><th>Expires</th><th>Created</th></tr></thead>
        <tbody id="migration-token-list-body">
          ${migrationTokens.length ? migrationTokens.map((t) => `
            <tr>
              <td>${escapeHtml(t.deviceName || t.deviceId)}</td>
              <td>${escapeHtml(t.policyName)}</td>
              <td><code>${escapeHtml(t.tokenValue)}</code> <button class="copy-btn" data-copy-value="${escapeHtml(t.tokenValue)}">Copy</button></td>
              <td>${t.expiresAt ? new Date(t.expiresAt).toLocaleString() : ''}</td>
              <td>${t.createdAt ? new Date(t.createdAt).toLocaleString() : ''}</td>
            </tr>
          `).join('') : '<tr><td colspan="5" class="loading">None yet</td></tr>'}
        </tbody>
      </table>`;

    content.querySelector('#create-gms-token-form').addEventListener('submit', async (e) => {
      e.preventDefault();
      const fd = new FormData(e.target);
      try {
        const allowPersonalUsage = fd.get('allowPersonalUsage') === 'on' ? 'PERSONAL_USAGE_ALLOWED' : 'PERSONAL_USAGE_DISALLOWED';
        const result = await api(`/api/organizations/${state.activeOrgId}/gms-enrollment-tokens`, {
          method: 'POST',
          body: JSON.stringify({ policyId: fd.get('policyId'), oneTimeOnly: fd.get('oneTimeOnly') === 'on', allowPersonalUsage }),
        });
        document.getElementById('new-token-result').innerHTML = `
          <p>Created (${allowPersonalUsage === 'PERSONAL_USAGE_ALLOWED' ? 'Work Profile / BYOD allowed' : 'fully-managed only'}). Token value: <code>${escapeHtml(result.tokenValue)}</code> <button class="copy-btn" data-copy-value="${escapeHtml(result.tokenValue)}">Copy</button></p>
          ${result.qrCodeData ? `<p><strong>Scan to provision:</strong></p>${qrImgTag(result.qrCodeData)}` : ''}`;
        renderTokens();
      } catch (err) {
        content.querySelector('#gms-token-error').textContent = err.message;
      }
    });
    content.querySelector('#create-nongms-token-form').addEventListener('submit', async (e) => {
      e.preventDefault();
      const fd = new FormData(e.target);
      try {
        const result = await api(`/api/organizations/${state.activeOrgId}/non-gms-enrollment-tokens`, {
          method: 'POST',
          body: JSON.stringify({ maxUses: parseInt(fd.get('maxUses'), 10) || 1 }),
        });
        document.getElementById('new-token-result').innerHTML = `
          <p>Created. Token value: <code>${escapeHtml(result.tokenValue)}</code> <button class="copy-btn" data-copy-value="${escapeHtml(result.tokenValue)}">Copy</button></p>
          ${result.qrCodeData ? `<p><strong>QR (placeholder token payload - not a real Device Owner provisioning QR yet):</strong></p>${qrImgTag(result.qrCodeData)}` : ''}`;
        renderTokens();
      } catch (err) {
        content.querySelector('#nongms-token-error').textContent = err.message;
      }
    });

    attachTokenRowListeners(tokens);
    attachCopyButtons(document.getElementById('migration-token-list-body'));
    tokenPollTimer = setInterval(refreshTokenRows, 5000);
  } catch (err) {
    content.innerHTML = `<p class="error">${escapeHtml(err.message)}</p>`;
  }
}

// ---- Admin users ----

async function renderOrgAdmins() {
  const content = document.getElementById('content');
  try {
    const admins = await api(`/api/organizations/${state.activeOrgId}/admin-users`);
    content.innerHTML = `
      <h2>Admin Users (this organization)</h2>
      <table>
        <thead><tr><th>Email</th><th>Role</th><th>Active</th></tr></thead>
        <tbody>
          ${admins.map((a) => `<tr><td>${escapeHtml(a.email)}</td><td>${escapeHtml(a.role)}</td><td>${a.isActive}</td></tr>`).join('')}
        </tbody>
      </table>
      <p class="loading">Manage (create/deactivate) from "All Admin Users" if you're a SUPER_ADMIN.</p>`;
  } catch (err) {
    content.innerHTML = `<p class="error">${escapeHtml(err.message)}</p>`;
  }
}

async function renderAllAdmins() {
  const content = document.getElementById('content');
  try {
    const [admins, orgs] = await Promise.all([api('/api/admin-users'), api('/api/organizations')]);
    // org id -> name, so the table can show a readable org name instead of a raw UUID
    const orgNameById = {};
    orgs.forEach((o) => { orgNameById[o.id] = o.name; });

    content.innerHTML = `
      <h2>All Admin Users</h2>
      <details>
        <summary>Create admin user</summary>
        <form id="create-admin-form" class="stacked-form">
          <input name="email" type="email" placeholder="Email" required>
          <input name="password" type="password" placeholder="Password" required>
          <select name="role" id="create-admin-role">
            <option value="SUPER_ADMIN">SUPER_ADMIN</option>
            <option value="ORG_ADMIN">ORG_ADMIN</option>
            <option value="ORG_VIEWER">ORG_VIEWER</option>
          </select>
          <select name="organizationId" id="create-admin-org">
            <option value="">(none - SUPER_ADMIN only)</option>
            ${orgs.map((o) => `<option value="${o.id}">${escapeHtml(o.name)}</option>`).join('')}
          </select>
          <button type="submit">Create</button>
        </form>
        <p id="create-admin-error" class="error"></p>
      </details>
      <table>
        <thead><tr><th>Email</th><th>Role</th><th>Org</th><th>Active</th><th></th></tr></thead>
        <tbody>
          ${admins.map((a) => `
            <tr>
              <td>${escapeHtml(a.email)}</td>
              <td>${escapeHtml(a.role)}</td>
              <td>${escapeHtml(a.organizationId ? (orgNameById[a.organizationId] || a.organizationId) : '-')}</td>
              <td>${a.isActive}</td>
              <td>${a.isActive ? `<button class="deactivate-btn" data-admin-id="${a.id}">Deactivate</button>` : ''}</td>
            </tr>
          `).join('')}
        </tbody>
      </table>`;

    content.querySelector('#create-admin-form').addEventListener('submit', async (e) => {
      e.preventDefault();
      const fd = new FormData(e.target);
      const role = fd.get('role');
      const orgId = fd.get('organizationId') || null;
      const errorEl = content.querySelector('#create-admin-error');
      // Mirrors the backend's own validation (AdminUserService.create) so
      // the mistake that caused this feature to be added - an ORG_ADMIN
      // created with no org, whose dashboard then 500s on the very first
      // API call after login - gets caught here instead of round-tripping.
      if (role === 'SUPER_ADMIN' && orgId) {
        errorEl.textContent = 'SUPER_ADMIN accounts must not be assigned to an organization.';
        return;
      }
      if (role !== 'SUPER_ADMIN' && !orgId) {
        errorEl.textContent = `${role} accounts must be assigned to an organization.`;
        return;
      }
      try {
        await api('/api/admin-users', {
          method: 'POST',
          body: JSON.stringify({ email: fd.get('email'), password: fd.get('password'), role, organizationId: orgId }),
        });
        renderAllAdmins();
      } catch (err) {
        errorEl.textContent = err.message;
      }
    });
    content.querySelectorAll('.deactivate-btn').forEach((btn) => btn.addEventListener('click', async () => {
      if (!confirm('Deactivate this admin user?')) return;
      try {
        await api(`/api/admin-users/${btn.dataset.adminId}/deactivate`, { method: 'POST' });
        renderAllAdmins();
      } catch (err) {
        alert(err.message);
      }
    }));
  } catch (err) {
    content.innerHTML = `<p class="error">${escapeHtml(err.message)}</p>`;
  }
}

// ---- GMS enterprise ----

async function renderGms() {
  const content = document.getElementById('content');
  content.innerHTML = `
    <h2>GMS Enterprise</h2>
    <p>Starts the Android Enterprise sign-up flow for this organization. Opens a Google-hosted page in a new tab;
       after your Google account completes it, Google redirects back to this server's callback endpoint automatically
       (you may see a self-signed certificate warning on that redirect in local dev - click through it).</p>
    <button id="start-signup-btn">Start sign-up</button>
    <div id="gms-result"></div>`;
  document.getElementById('start-signup-btn').addEventListener('click', async () => {
    const result = document.getElementById('gms-result');
    result.innerHTML = '<p class="loading">Requesting signup URL…</p>';
    try {
      const res = await api(`/api/organizations/${state.activeOrgId}/gms-enterprise/signup-url`, { method: 'POST' });
      result.innerHTML = `<p>Open this to complete sign-up: <a href="${escapeHtml(res.url)}" target="_blank" rel="noopener">${escapeHtml(res.url)}</a></p>`;
    } catch (err) {
      result.innerHTML = `<p class="error">${escapeHtml(err.message)}</p>`;
    }
  });
}

// ---- Bootstrap ----

window.addEventListener('DOMContentLoaded', () => {
  const saved = sessionStorage.getItem('mdm_session');
  if (saved) {
    try {
      state = JSON.parse(saved);
      if (state.token) { showApp(); }
      else { showLogin(); }
    } catch (e) {
      showLogin();
    }
  } else {
    showLogin();
  }

  document.getElementById('login-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const fd = new FormData(e.target);
    const errorEl = document.getElementById('login-error');
    errorEl.textContent = '';
    try {
      await login(fd.get('email'), fd.get('password'));
    } catch (err) {
      errorEl.textContent = err.message;
    }
  });
});
