// Optional sync: sign in with Google and the whole backup is kept in your own row in the cloud (Supabase),
// so the phone and the laptop share the same data. Nothing here runs until you tap "Sign in with Google".
// No library: plain fetch against Supabase Auth and its REST API. The URL and the publishable key are public by design;
// what keeps data private is row level security (each row can only be read by its owner).
import * as R from './store.js';

const URL_BASE = 'https://gnbtetbbxekofmjzbzga.supabase.co';
const KEY = 'sb_publishable_rjOgzmPudvrnCbc5nkBevg_xq_jPPFt';
const LS = 'pocketta-sync';
// Things that belong to one device (or change by themselves), so they are not synced and not compared.
const DEVICE_KEYS = ['pinHash', 'pinSalt', 'theme', 'lang', 'onlineRates', 'prevOnlineRates', 'gold', 'prevGold', 'ratesUpdated', 'ratesFetchedAt', 'overAlert', 'limitDismissed', 'monthEndDismissed'];

let st = load();
let hooks = { choose: async () => null, done: () => {} };
let busy = false, timer = null, applying = false;
const listeners = new Set();
export const onStatus = (f) => listeners.add(f);
const emit = () => listeners.forEach((f) => f());

function load() { try { return JSON.parse(localStorage.getItem(LS) || '{}'); } catch { return {}; } }
function save() { try { localStorage.setItem(LS, JSON.stringify(st)); } catch { /* private mode */ } }

export const signedIn = () => !!st.session;
export const info = () => ({ email: st.email || '', lastSyncAt: st.lastSyncAt || 0, status: st.status || '', error: st.error || '', busy });

// ---------- What is synced ----------

function snapshot() {
  const d = JSON.parse(R.exportJson());
  delete d.exportedAt;
  d.settings = { ...d.settings };
  for (const k of DEVICE_KEYS) delete d.settings[k];
  return d;
}
function hash(obj) {
  const s = JSON.stringify(obj);
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
async function upload(cloud) {
  const body = { data: snapshot(), device_name: deviceName(), version: 1 };
  let rows;
  if (!cloud) rows = await api('/rest/v1/backups', { method: 'POST', headers: { Prefer: 'return=representation' }, body: JSON.stringify({ ...body, user_id: st.uid }) });
  else {
    // Keep the version being replaced as a safety copy, and only replace it if nobody changed it meanwhile.
    rows = await api(`/rest/v1/backups?user_id=eq.${st.uid}&updated_at=eq.${encodeURIComponent(cloud.updated_at)}`, { method: 'PATCH', headers: { Prefer: 'return=representation' }, body: JSON.stringify({ ...body, prev_data: cloud.data, prev_updated_at: cloud.updated_at }) });
  }
  if (!rows.length) return false;
  st.lastHash = hash(snapshot());
  st.cloudAt = rows[0].updated_at;
  return true;
}

/** auto = true when it runs by itself (never asks anything except when two different copies exist). */
export async function syncNow() {
  if (!signedIn() || busy || applying) return;
  busy = true; st.status = 'syncing'; st.error = ''; emit();
  try {
    await whoAmI();
    for (let tries = 0; tries < 3; tries++) {
      const cloud = await fetchCloud();
      const local = snapshot();
      const lh = hash(local);
      const summary = (d, at, device) => ({ ...counts(d), at, device });
      let ok = true;
      if (!cloud) {
        if (!isEmpty(local)) {
          const pick = st.lastHash ? 'local' : await hooks.choose({ kind: 'first-upload', local: summary(local, st.localAt || Date.now()) });
          if (!pick) { st.status = 'paused'; break; }
          ok = await upload(null);
        }
      } else if (!st.cloudAt) {
        // This device never synced with this account.
        if (isEmpty(local)) applyCloud(cloud.data, cloud.updated_at);
        else if (hash(stripDevice(cloud.data)) === lh) { st.lastHash = lh; st.cloudAt = cloud.updated_at; }
        else {
          const pick = await hooks.choose({ kind: 'conflict', local: summary(local, st.localAt || Date.now()), cloud: summary(cloud.data, Date.parse(cloud.updated_at), cloud.device_name) });
          if (!pick) { st.status = 'paused'; break; }
          if (pick === 'cloud') applyCloud(cloud.data, cloud.updated_at); else ok = await upload(cloud);
        }
      } else {
        const localChanged = lh !== st.lastHash, cloudChanged = cloud.updated_at !== st.cloudAt;
        if (localChanged && cloudChanged) {
          const pick = await hooks.choose({ kind: 'conflict', local: summary(local, st.localAt || Date.now()), cloud: summary(cloud.data, Date.parse(cloud.updated_at), cloud.device_name) });
          if (!pick) { st.status = 'paused'; break; }
          if (pick === 'cloud') applyCloud(cloud.data, cloud.updated_at); else ok = await upload(cloud);
        } else if (localChanged) ok = await upload(cloud);
        else if (cloudChanged) applyCloud(cloud.data, cloud.updated_at);
      }
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
  st.lastHash = ''; st.cloudAt = ''; st.lastSyncAt = 0; save(); emit();
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
