// Optional sync: sign in with Google and the whole backup is kept in your own row in the cloud (Supabase),
// so the phone and the laptop share the same data. Nothing here runs until you tap "Sign in with Google".
// The backup is end-to-end encrypted on the device (see crypto.js) before it is sent: the cloud only holds a sealed envelope.
// No library: plain fetch against Supabase Auth and its REST API. The URL and the publishable key are public by design;
// what keeps data private is row level security (each row can only be read by its owner).
import * as R from './store.js';
import * as K from './crypto.js';

const URL_BASE = 'https://gnbtetbbxekofmjzbzga.supabase.co';
const KEY = 'sb_publishable_rjOgzmPudvrnCbc5nkBevg_xq_jPPFt';
const LS = 'pocketta-sync';
// Things that belong to one device (or change by themselves), so they are not synced and not compared.
const DEVICE_KEYS = ['pinHash', 'pinSalt', 'theme', 'lang', 'onlineRates', 'prevOnlineRates', 'gold', 'prevGold', 'ratesUpdated', 'ratesFetchedAt', 'overAlert', 'limitDismissed', 'monthEndDismissed'];

let st = load();
let hooks = { choose: async () => null, setup: async () => null, unlock: async () => null, done: () => {} };
let busy = false, timer = null, applying = false;
const listeners = new Set();
export const onStatus = (f) => listeners.add(f);
const emit = () => listeners.forEach((f) => f());

function load() { try { return JSON.parse(localStorage.getItem(LS) || '{}'); } catch { return {}; } }
function save() { try { localStorage.setItem(LS, JSON.stringify(st)); } catch { /* private mode */ } }

export const signedIn = () => !!st.session;
export const info = () => ({ email: st.email || '', lastSyncAt: st.lastSyncAt || 0, status: st.status || '', error: st.error || '', busy, encrypted: !!st.dek });

// ---------- What is synced ----------

function snapshot() {
  const d = JSON.parse(R.exportJson());
  delete d.exportedAt;
  d.settings = { ...d.settings };
  for (const k of DEVICE_KEYS) delete d.settings[k];
  return d;
}
// Things the app updates by itself on every open (net worth log) or when you add something (last wallet/category) are not "a change" for sync.
const NOISE = ['wealthHistory', 'lastCategory', 'lastWallet'];
function hash(obj) {
  const c = { ...obj, settings: { ...(obj.settings || {}) } };
  for (const k of NOISE) delete c.settings[k];
  const s = JSON.stringify(c);
  let h1 = 0xdeadbeef, h2 = 0x41c6ce57;
  for (let i = 0; i < s.length; i++) { const c = s.charCodeAt(i); h1 = Math.imul(h1 ^ c, 2654435761); h2 = Math.imul(h2 ^ c, 1597334677); }
  h1 = Math.imul(h1 ^ (h1 >>> 16), 2246822507) ^ Math.imul(h2 ^ (h2 >>> 13), 3266489909);
  h2 = Math.imul(h2 ^ (h2 >>> 16), 2246822507) ^ Math.imul(h1 ^ (h1 >>> 13), 3266489909);
  return (h2 >>> 0).toString(16) + (h1 >>> 0).toString(16) + ':' + s.length;
}
const isEmpty = (d) => !d.settings.onboarded && !(d.txns || []).length;
const counts = (d) => ({ txns: (d.txns || []).length, commitments: (d.bills || []).length + (d.installments || []).length + (d.circles || []).length + (d.loans || []).length });

/** Put cloud data on this device. The old local copy is kept once (localStorage) as a safety copy. */
function applyCloud(cloudData, updatedAt) {
  try { localStorage.setItem('pocketta-safety-copy', JSON.stringify({ at: Date.now(), data: R.exportJson() })); } catch { /* too big: skip */ }
  const keep = {};
  for (const k of DEVICE_KEYS) keep[k] = R.S()[k];
  const merged = { ...cloudData, settings: { ...cloudData.settings, ...keep } };
  applying = true;
  try { if (!R.importJson(JSON.stringify(merged))) throw new Error('bad data'); } finally { applying = false; }
  st.lastHash = hash(snapshot());
  st.cloudAt = updatedAt;
  st.localAt = Date.now();
  save();
}

// ---------- Sign in / out ----------

export function signIn() {
  const back = location.origin + location.pathname;
  location.href = `${URL_BASE}/auth/v1/authorize?provider=google&redirect_to=${encodeURIComponent(back)}`;
}

/** Call once at startup: picks up the answer when Google sends the person back to the app. */
export function handleRedirect() {
  const h = location.hash.replace(/^#/, '');
  if (!h.includes('access_token=') && !h.includes('error=')) return false;
  const p = new URLSearchParams(h);
  history.replaceState(null, '', location.pathname + location.search);
  if (p.get('error')) { st.error = p.get('error_description') || p.get('error'); save(); return true; }
  st = { session: { access_token: p.get('access_token'), refresh_token: p.get('refresh_token'), expires_at: Number(p.get('expires_at')) || Math.floor(Date.now() / 1000) + Number(p.get('expires_in') || 3600) } };
  save();
  return true;
}

export function signOut() {
  clearTimeout(timer);
  st = {};
  save();
  emit();
}

async function token() {
  const s = st.session;
  if (!s) throw new Error('signed out');
  if (s.expires_at - 60 > Date.now() / 1000) return s.access_token;
  const r = await fetch(`${URL_BASE}/auth/v1/token?grant_type=refresh_token`, { method: 'POST', headers: { apikey: KEY, 'Content-Type': 'application/json' }, body: JSON.stringify({ refresh_token: s.refresh_token }) });
  if (!r.ok) { if (r.status === 400 || r.status === 401) { signOut(); throw new Error('Signed out. Please sign in again.'); } throw new Error('offline'); }
  const j = await r.json();
  st.session = { access_token: j.access_token, refresh_token: j.refresh_token, expires_at: j.expires_at || Math.floor(Date.now() / 1000) + j.expires_in };
  save();
  return st.session.access_token;
}

async function api(path, opts = {}) {
  const t = await token();
  const r = await fetch(URL_BASE + path, { ...opts, headers: { apikey: KEY, Authorization: `Bearer ${t}`, 'Content-Type': 'application/json', ...(opts.headers || {}) } });
  if (!r.ok) throw new Error(`${r.status}`);
  return r.status === 204 ? null : r.json();
}

async function whoAmI() {
  if (st.email && st.uid) return;
  const u = await api('/auth/v1/user');
  st.email = u.email || ''; st.uid = u.id; save();
}

const deviceName = () => (/iPhone|iPad|Android/i.test(navigator.userAgent) ? 'Phone' : 'Computer') + ' (web)';

// ---------- Sync ----------

async function fetchCloud() {
  const rows = await api('/rest/v1/backups?select=data,updated_at,device_name&limit=1');
  return rows[0] || null;
}
/** Makes sure this device has the data key: first time it is created from a new password (and a recovery key shown once). */
async function needKeys() {
  if (st.dek && st.keys) return true;
  const recovery = K.newRecoveryKey();
  const r = await hooks.setup({ recovery });
  if (!r) return false;
  const k = await K.createKeys(recovery, recovery); // the app's key is also the unlock secret: no password to choose
  st.dek = k.dek; st.keys = k.keys;
  save();
  return true;
}

/** The key made by the app, or a password chosen in an older version: try both. */
async function openWith(env, secret) {
  try { return await K.unlock(env, 'rec', secret); } catch { return K.unlock(env, 'pass', secret); }
}

/** The cloud row as stored, opened: a sealed copy needs the password (or the recovery key) once per device. */
async function openRow(row) {
  const d = row.data;
  if (!d || !d.enc) return { ...row, raw: d, wasEnc: false };
  let bad = false;
  for (;;) {
    if (!st.dek) {
      const r = await hooks.unlock({ bad });
      if (!r) return null;
      if (r.wipe) { await deleteCloud(); return 'retry'; }
      try {
        const dek = await openWith(d, r.secret);
        st.dek = dek; st.keys = K.keysOf(d);
        save(); bad = false;
      } catch { bad = true; continue; }
    }
    try { return { ...row, data: await K.decryptData(st.dek, d), raw: d, wasEnc: true }; }
    catch { st.dek = ''; st.keys = null; save(); bad = false; } // sealed with another key (cloud was reset elsewhere): ask again
  }
}

async function upload(cloud) {
  const snap = snapshot();
  const sealed = await K.encryptData(st.dek, st.keys, snap);
  const body = { data: sealed, device_name: deviceName(), version: 2 };
  let rows;
  if (!cloud) rows = await api('/rest/v1/backups', { method: 'POST', headers: { Prefer: 'return=representation' }, body: JSON.stringify({ ...body, user_id: st.uid }) });
  else {
    // Keep the sealed version being replaced as a safety copy (an old unsealed one is not kept), and only replace it if nobody changed it meanwhile.
    rows = await api(`/rest/v1/backups?user_id=eq.${st.uid}&updated_at=eq.${encodeURIComponent(cloud.updated_at)}`, { method: 'PATCH', headers: { Prefer: 'return=representation' }, body: JSON.stringify({ ...body, prev_data: cloud.wasEnc ? cloud.raw : null, prev_updated_at: cloud.wasEnc ? cloud.updated_at : null }) });
  }
  if (!rows.length) return false;
  st.lastHash = hash(snap);
  st.cloudAt = rows[0].updated_at;
  st.forceUpload = false;
  return true;
}

/** Runs a sync. It asks only when really needed: setting the encryption password, first upload, or two different copies. */
export async function syncNow() {
  if (!signedIn() || busy || applying) return;
  busy = true; st.status = 'syncing'; st.error = ''; emit();
  try {
    await whoAmI();
    for (let tries = 0; tries < 4; tries++) {
      let cloud = await fetchCloud();
      if (cloud) {
        const o = await openRow(cloud);
        if (o === 'retry') continue;
        if (!o) { st.status = 'paused'; break; }
        cloud = o;
      }
      const local = snapshot();
      const lh = hash(local);
      const summary = (d, at, device) => ({ ...counts(d), at, device });
      let ok = true, uploaded = false, paused = false;
      // Every upload goes through here: it needs the data key first.
      const up = async (cl) => {
        if (!(await needKeys())) { paused = true; return true; }
        const r = await upload(cl);
        if (r) uploaded = true;
        return r;
      };
      if (!cloud) {
        if (!isEmpty(local)) {
          const pick = st.lastHash ? 'local' : await hooks.choose({ kind: 'first-upload', local: summary(local, st.localAt || Date.now()) });
          if (!pick) paused = true; else ok = await up(null);
        }
      } else if (!st.cloudAt) {
        // This device never synced with this account.
        if (isEmpty(local)) applyCloud(cloud.data, cloud.updated_at);
        else if (hash(stripDevice(cloud.data)) === lh) { st.lastHash = lh; st.cloudAt = cloud.updated_at; }
        else {
          const pick = await hooks.choose({ kind: 'conflict', local: summary(local, st.localAt || Date.now()), cloud: summary(cloud.data, Date.parse(cloud.updated_at), cloud.device_name) });
          if (!pick) paused = true;
          else if (pick === 'cloud') applyCloud(cloud.data, cloud.updated_at); else ok = await up(cloud);
        }
      } else {
        const localChanged = lh !== st.lastHash, cloudChanged = cloud.updated_at !== st.cloudAt;
        if (localChanged && cloudChanged) {
          // Already signed in and linked: the cloud copy wins, without asking. This device's copy is kept once as a safety copy.
          try { localStorage.setItem('pocketta-conflict-copy', JSON.stringify({ at: Date.now(), data: R.exportJson() })); } catch { /* too big: skip */ }
          applyCloud(cloud.data, cloud.updated_at);
        } else if (localChanged) ok = await up(cloud);
        else if (cloudChanged) applyCloud(cloud.data, cloud.updated_at);
      }
      // An old copy in the cloud that is not sealed yet (or a new password after using the recovery key): seal and upload it now.
      if (ok && !paused && !uploaded && cloud && (!cloud.wasEnc || st.forceUpload)) ok = await up(cloud);
      if (paused) { st.status = 'paused'; break; }
      if (ok) { st.lastSyncAt = Date.now(); st.status = 'ok'; break; }
    }
  } catch (e) {
    st.status = 'error'; st.error = e.message === 'offline' || e instanceof TypeError ? 'offline' : String(e.message);
  }
  busy = false; save(); emit(); hooks.done();
}
const stripDevice = (d) => { const c = { ...d, settings: { ...d.settings } }; for (const k of DEVICE_KEYS) delete c.settings[k]; return c; };

export async function deleteCloud() {
  await whoAmI();
  await api(`/rest/v1/backups?user_id=eq.${st.uid}`, { method: 'DELETE' });
  st.lastHash = ''; st.cloudAt = ''; st.lastSyncAt = 0; st.dek = ''; st.keys = null; st.forceUpload = false; save(); emit();
}

/** Settings > Encryption: a new encryption key made by the app. It asks for the current key (or old password) first. The data itself is not re-encrypted, so other devices keep working. */
async function checkSecret(cur) { try { await openWith(st.keys, cur); return true; } catch { return false; } }
export const hasKeys = () => !!(st.dek && st.keys);
export async function newRecoveryKey(cur) {
  if (!hasKeys() || !(await checkSecret(cur))) return null;
  const rec = K.newRecoveryKey();
  st.keys = await K.rewrap(st.dek, await K.withRecovery(st.dek, st.keys, rec), rec); st.forceUpload = true; save();
  syncNow();
  return rec;
}

/** Start syncing after changes (debounced) and when the app opens. Does nothing while signed out. */
export function start(h) {
  hooks = { ...hooks, ...h };
  R.onChange(() => {
    if (applying || !signedIn()) return;
    st.localAt = Date.now();
    clearTimeout(timer);
    timer = setTimeout(syncNow, 4000);
  });
  document.addEventListener('visibilitychange', () => { if (!document.hidden && signedIn()) syncNow(); });
  window.addEventListener('online', () => { if (signedIn()) syncNow(); });
  if (signedIn()) syncNow();
}
