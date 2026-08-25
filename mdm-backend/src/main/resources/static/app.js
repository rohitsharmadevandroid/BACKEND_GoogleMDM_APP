// MDM Admin Dashboard - vanilla JS, no build step, served same-origin from
// Spring Boot's static resources (so no CORS setup needed). The JWT is
// kept in sessionStorage; that's readable by any injected script (a real
// XSS risk in general), which is why every piece of server-returned text
// gets run through escapeHtml() before landing in innerHTML below - the
// one XSS vector we do control.

let state = { token: null, role: null, organizationId: null, activeOrgId: null, email: null };

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
    + '<button class="nav-btn logout" id="logout-btn">Log out</button>';
  nav.querySelectorAll('.nav-btn[data-section]').forEach((btn) => btn.addEventListener('click', () => navigate(btn.dataset.section)));
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
              <td><button class="manage-btn" data-org-id="${o.id}">Manage</button></td>
            </tr>
          `).join('')}
        </tbody>
      </table>`;

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

async function renderDeviceDetail(deviceId) {
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

      <h4>Command history</h4>
      <table>
        <thead><tr><th>Type</th><th>Status</th><th>Created</th><th>Error</th></tr></thead>
        <tbody>
          ${commands.map((c) => `
            <tr>
              <td>${escapeHtml(c.commandType)}</td>
              <td>${escapeHtml(c.status)}</td>
              <td>${c.createdAt ? new Date(c.createdAt).toLocaleString() : ''}</td>
              <td>${escapeHtml(c.errorMessage || '')}</td>
            </tr>
          `).join('')}
        </tbody>
      </table>`;

    el.querySelector('#unenroll-btn').addEventListener('click', async () => {
      if (!confirm('Unenroll this device? For GMS devices this also wipes it; for non-GMS devices, queue and confirm a WIPE command first if you need data wiped too.')) return;
      try {
        await api(`/api/devices/${deviceId}`, { method: 'DELETE', body: JSON.stringify({}) });
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

async function renderTokens() {
  const content = document.getElementById('content');
  try {
    const tokens = await api(`/api/organizations/${state.activeOrgId}/enrollment-tokens`);
    content.innerHTML = `
      <h2>Enrollment Tokens</h2>
      <div class="two-col">
        <form id="create-gms-token-form" class="stacked-form">
          <h4>New GMS token</h4>
          <input name="policyName" placeholder="enterprises/.../policies/default" required>
          <label><input type="checkbox" name="oneTimeOnly" checked> One-time only</label>
          <button type="submit">Create GMS token</button>
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
        <tbody>
          ${tokens.map((t) => `
            <tr>
              <td>${escapeHtml(t.deviceType)}</td>
              <td><code>${escapeHtml(t.tokenValue)}</code> <button class="copy-btn" data-copy-value="${escapeHtml(t.tokenValue)}">Copy</button></td>
              <td>${escapeHtml(t.status)}</td>
              <td>${t.usedCount} / ${t.maxUses}</td>
              <td>${t.createdAt ? new Date(t.createdAt).toLocaleString() : ''}</td>
              <td>${t.status === 'ACTIVE' ? `<button class="revoke-btn" data-token-id="${t.id}">Revoke</button>` : ''}</td>
            </tr>
          `).join('')}
        </tbody>
      </table>`;

    content.querySelector('#create-gms-token-form').addEventListener('submit', async (e) => {
      e.preventDefault();
      const fd = new FormData(e.target);
      try {
        const result = await api(`/api/organizations/${state.activeOrgId}/gms-enrollment-tokens`, {
          method: 'POST',
          body: JSON.stringify({ policyName: fd.get('policyName'), oneTimeOnly: fd.get('oneTimeOnly') === 'on' }),
        });
        document.getElementById('new-token-result').innerHTML =
          `<p>Created. Token value: <code>${escapeHtml(result.tokenValue)}</code> <button class="copy-btn" data-copy-value="${escapeHtml(result.tokenValue)}">Copy</button></p>`;
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
        document.getElementById('new-token-result').innerHTML =
          `<p>Created. Token value: <code>${escapeHtml(result.tokenValue)}</code> <button class="copy-btn" data-copy-value="${escapeHtml(result.tokenValue)}">Copy</button></p>`;
        renderTokens();
      } catch (err) {
        content.querySelector('#nongms-token-error').textContent = err.message;
      }
    });
    content.querySelectorAll('.revoke-btn').forEach((btn) => btn.addEventListener('click', async () => {
      try {
        await api(`/api/enrollment-tokens/${btn.dataset.tokenId}/revoke`, { method: 'POST' });
        renderTokens();
      } catch (err) {
        alert(err.message);
      }
    }));
    content.querySelectorAll('.copy-btn').forEach((btn) => btn.addEventListener('click', async () => {
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
    const admins = await api('/api/admin-users');
    content.innerHTML = `
      <h2>All Admin Users</h2>
      <details>
        <summary>Create admin user</summary>
        <form id="create-admin-form" class="stacked-form">
          <input name="email" type="email" placeholder="Email" required>
          <input name="password" type="password" placeholder="Password" required>
          <select name="role">
            <option value="SUPER_ADMIN">SUPER_ADMIN</option>
            <option value="ORG_ADMIN">ORG_ADMIN</option>
            <option value="ORG_VIEWER">ORG_VIEWER</option>
          </select>
          <input name="organizationId" placeholder="Organization ID (blank for platform SUPER_ADMIN)">
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
              <td>${escapeHtml(a.organizationId || '-')}</td>
              <td>${a.isActive}</td>
              <td>${a.isActive ? `<button class="deactivate-btn" data-admin-id="${a.id}">Deactivate</button>` : ''}</td>
            </tr>
          `).join('')}
        </tbody>
      </table>`;

    content.querySelector('#create-admin-form').addEventListener('submit', async (e) => {
      e.preventDefault();
      const fd = new FormData(e.target);
      const orgId = (fd.get('organizationId') || '').trim();
      try {
        await api('/api/admin-users', {
          method: 'POST',
          body: JSON.stringify({ email: fd.get('email'), password: fd.get('password'), role: fd.get('role'), organizationId: orgId || null }),
        });
        renderAllAdmins();
      } catch (err) {
        content.querySelector('#create-admin-error').textContent = err.message;
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
