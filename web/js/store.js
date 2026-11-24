// All data lives in one object, saved to IndexedDB on this phone. Ported from the Android Repo.
import * as C from './core.js';
import * as P from './plan.js';
import { countryFor } from './countries.js';

const DB_NAME = 'pocketta', STORE = 'kv', KEY = 'data';

export const EXPENSE_SEED = [
  ['Food', ['Groceries', 'Eating out', 'Delivery', 'Coffee']],
  ['Transport', ['Uber / Careem', 'Microbus / Metro', 'Fuel', 'Parking', 'Car service']],
  ['Home', ['Rent', 'Maintenance', 'Cleaning', 'Furniture']],
  ['Bills', ['Electricity', 'Water', 'Gas', 'Internet', 'Mobile']],
  ['Subscriptions', []],
  ['Health', ['Doctor', 'Pharmacy', 'Lab tests']],
  ['Shopping', ['Clothes', 'Electronics', 'Home stuff']],
  ['Family', []], ['Education', []],
  ['Fun', ['Outings', 'Trips', 'Games']],
  ['Personal care', []], ['Gifts & charity', []], ['Installments', []], ["Gam'eya", []], ['Loans', []], ['Other', []],
];
export const INCOME_SEED = ['Salary', "Gam'eya payout", 'Borrowed', 'Loan repaid', 'Side work', 'Gift', 'Other income'];

const DEFAULT_SETTINGS = {
  onboarded: false, cycleStart: 1, baseCurrency: 'EGP', country: '', salary: 0, salaryDay: 1, salaryWallet: 0,
  savingsPerMonth: 0, budgetAlert: true, rates: {}, lastCategory: 0, lastWallet: 0, pinHash: '', pinSalt: '', overAlert: '',
  // 1.3: look, savings goal, today's rates from the internet (your own rates still win)
  theme: 'AUTO', goalName: '', goalAmount: 0, goalSaved: 0, goals: [], piggy: [], piggyTarget: 0, piggyAuto: 0, piggyAutoPct: 0, piggyAutoDone: 0, limitAlert: true, limitDismissed: '', addFields: {},
  lang: 'EN', goldBuys: [], monthEndReminder: true, monthEndDismissed: '',
  // 1.14: flexible money, household shopping reserve, weekly summary from Miqrish
  household: 0, weeklyAi: { week: '', text: '' }, weeklyAiOn: true,
  salarySteps: {}, livingBase: 0, livingOverrides: {},
  wealthHistory: [], autoRates: true, autoCurrencies: ['USD', 'EUR', 'SAR'], onlineRates: {}, prevOnlineRates: {}, gold: {}, prevGold: {}, ratesUpdated: '', ratesFetchedAt: 0,
};

function empty() {
  return { app: 'pocketta', v: 1, seq: 1, settings: { ...DEFAULT_SETTINGS }, wallets: [], categories: [], txns: [], bills: [], installments: [], circles: [], loans: [] };
}

export let data = empty();
const listeners = new Set();
export const onChange = (f) => listeners.add(f);

// ---------- Persistence ----------

function openDb() {
  return new Promise((res, rej) => {
    const r = indexedDB.open(DB_NAME, 1);
    r.onupgradeneeded = () => r.result.createObjectStore(STORE);
    r.onsuccess = () => res(r.result);
    r.onerror = () => rej(r.error);
  });
}
let dbp = null;
async function idb(mode, fn) {
  dbp = dbp || openDb();
  const db = await dbp;
  return new Promise((res, rej) => {
    const tx = db.transaction(STORE, mode);
    const req = fn(tx.objectStore(STORE));
    tx.oncomplete = () => res(req && req.result);
    tx.onerror = () => rej(tx.error);
  });
}

export async function load() {
  let raw = null;
  try { raw = await idb('readonly', (s) => s.get(KEY)); } catch (e) { console.warn(e); }
  if (!raw) { try { raw = JSON.parse(localStorage.getItem('pocketta-data') || 'null'); } catch { raw = null; } }
  data = normalize(raw) || empty();
  C.setCurrencyFormat(data.settings.baseCurrency);
  try { navigator.storage && navigator.storage.persist && navigator.storage.persist(); } catch { /* fine */ }
}

let saveTimer = null;
function persist() {
  clearTimeout(saveTimer);
  saveTimer = setTimeout(async () => {
    const copy = JSON.parse(JSON.stringify(data));
    try { await idb('readwrite', (s) => s.put(copy, KEY)); }
    catch (e) { try { localStorage.setItem('pocketta-data', JSON.stringify(copy)); } catch { console.error(e); } }
  }, 50);
}

export function changed() { persist(); listeners.forEach((f) => f()); }

function normalize(raw) {
  if (!raw || raw.app !== 'pocketta' || !Array.isArray(raw.txns)) return null;
  const d = empty();
  for (const k of Object.keys(d)) if (raw[k] !== undefined) d[k] = raw[k];
  d.settings = { ...DEFAULT_SETTINGS, ...(raw.settings || {}) };
  return d;
}

// Ids are unique across devices (time + random), so a later Google-account sync can merge records safely.
let lastId = 0;
export const nextId = () => (lastId = Math.max(lastId + 1, Date.now() * 1000 + Math.floor(Math.random() * 1000)));
export const S = () => data.settings;
export function setSetting(k, v) { data.settings[k] = v; changed(); }
/** The country picked in the app (old data has none, so it is read from the currency: Egypt for EGP). */
export const country = () => countryFor(S().country, S().baseCurrency);
/** Change the main currency. Old amounts are not converted: only the symbol and decimals change. */
export function setCurrency(cur, countryCode = '') {
  const s = S();
  cur = String(cur || '').trim().toUpperCase().slice(0, 4);
  if (!cur) return;
  const was = s.baseCurrency;
  s.country = countryCode;
  if (cur !== was) {
    s.baseCurrency = cur;
    // Online rates and gold were converted into the old currency: drop them and fetch again.
    s.onlineRates = {}; s.prevOnlineRates = {}; s.gold = {}; s.prevGold = {}; s.ratesFetchedAt = 0;
    s.autoCurrencies = [...new Set([...s.autoCurrencies.filter((c) => c !== cur), ...(cur === 'USD' ? [] : ['USD'])])];
  }
  C.setCurrencyFormat(cur);
  changed();
  if (cur !== was) refreshRates(true);
}
export const rateOf = (cur) => cur === S().baseCurrency ? 1 : (S().rates[cur] || (S().autoRates ? S().onlineRates[cur] : 0) || 1);
export const currencies = () => [...new Set([S().baseCurrency, ...Object.keys(S().rates).sort(),
  ...(S().autoRates ? S().autoCurrencies.filter((c) => S().onlineRates[c]) : [])])];
export const isManual = (cur) => !!S().rates[cur];
/** Change since yesterday as a fraction (0.01 = up 1%), or null when yesterday is not known. */
export function rateChange(cur) { const n = S().onlineRates[cur], b = S().prevOnlineRates[cur]; return n && b ? (n - b) / b : null; }
export function goldChange(k) { const n = S().gold[k], b = S().prevGold[k]; return n && b ? (n - b) / b : null; }

// ---------- Today's rates and gold (a small file the project publishes every day) ----------

const RATES_URL = 'https://raw.githubusercontent.com/architectahmedgaber-sys/pocketta/rates/rates.json';
const readMap = (o) => { const out = {}; for (const [k, v] of Object.entries(o || {})) if (v > 0) out[k.toUpperCase()] = +v; return out; };
export async function refreshRates(force = false) {
  const s = S();
  if (!s.autoRates) return false;
  if (!force && Date.now() - s.ratesFetchedAt < 6 * 3600000) return true;
  try {
    const res = await fetch(RATES_URL, { cache: 'no-cache' });
    if (!res.ok) throw new Error(res.status);
    const j = await res.json();
    // The file is in EGP; turn it into the main currency (rate of X = EGP per X / EGP per main currency).
    const inBase = (r, g) => {
      const rates = readMap(r), gold = readMap(g), b = s.baseCurrency;
      if (b === 'EGP') return [rates, gold];
      const f = rates[b];
      if (!f) throw new Error('no rate for ' + b);
      const rr = { EGP: 1 / f }, gg = {};
      for (const [k, v] of Object.entries(rates)) if (k !== b) rr[k] = v / f;
      for (const [k, v] of Object.entries(gold)) gg[k] = v / f;
      return [rr, gg];
    };
    const [rates, gold] = inBase(j.rates, j.gold);
    if (!Object.keys(rates).length) throw new Error('empty');
    if (j.previous) { [s.prevOnlineRates, s.prevGold] = inBase(j.previous.rates, j.previous.gold); }
    else if (Object.keys(s.onlineRates).length && String(s.ratesUpdated).slice(0, 10) !== String(j.updated).slice(0, 10)) { s.prevOnlineRates = s.onlineRates; s.prevGold = s.gold; }
    s.onlineRates = rates; s.gold = gold; s.ratesUpdated = String(j.updated || ''); s.ratesFetchedAt = Date.now();
    changed();
    return true;
  } catch (e) { return false; }
}
export const base = (t) => t.amount * (t.rate || 1);

// ---------- Backup ----------

export function exportJson() { return JSON.stringify({ ...data, exportedAt: new Date().toISOString() }); }
export function importJson(text) {
  let raw;
  try { raw = JSON.parse(text); } catch { return false; }
  const d = normalize(raw);
  if (!d) return false;
  data = d;
  C.setCurrencyFormat(data.settings.baseCurrency);
  changed();
  return true;
}
export function wipe() { const { theme, lang } = data.settings; data = empty(); data.settings.theme = theme; data.settings.lang = lang; C.setCurrencyFormat(data.settings.baseCurrency); changed(); }

// ---------- Seed ----------

export function ensureSeeded() {
  if (data.categories.length) return;
  let sort = 0;
  for (const [name, subs] of EXPENSE_SEED) {
    const pid = nextId();
    data.categories.push({ id: pid, name, parentId: null, kind: 'EXPENSE', monthlyLimit: 0, sort: sort++, archived: false });
    subs.forEach((s, i) => data.categories.push({ id: nextId(), name: s, parentId: pid, kind: 'EXPENSE', monthlyLimit: 0, sort: i, archived: false }));
  }
  for (const name of INCOME_SEED) data.categories.push({ id: nextId(), name, parentId: null, kind: 'INCOME', monthlyLimit: 0, sort: sort++, archived: false });
  changed();
}

export const wallets = () => data.wallets.filter((w) => !w.archived).sort((a, b) => a.sort - b.sort);
export const categories = () => data.categories.filter((c) => !c.archived);
export const byId = (list, id) => list.find((x) => x.id === id);

export function categoryNamed(name, kind) {
  const n = name.toLowerCase();
  return (data.categories.find((c) => c.name.toLowerCase() === n && c.kind === kind && !c.archived) ||
    data.categories.find((c) => c.name.toLowerCase() === n && c.kind === kind))?.id ?? null;
}
/** All money lives in one account. Old data may still hold several wallets; their ids stay but nothing shows them. */
function defaultWallet() {
  if (!data.wallets.length) data.wallets.push({ id: nextId(), name: 'Money', type: 'CASH', currency: S().baseCurrency, opening: 0, sort: 0, archived: false });
  const ws = data.wallets.filter((w) => !w.archived).sort((a, b) => a.sort - b.sort);
  const all = ws.length ? ws : data.wallets;
  return (all.find((w) => w.id === S().lastWallet) || all[0]).id;
}

export function categoryLabel(id) {
  const c = byId(data.categories, id);
  if (!c) return 'No category';
  const p = c.parentId ? byId(data.categories, c.parentId) : null;
  return p ? `${p.name} · ${c.name}` : c.name;
}

// ---------- Transactions ----------

function txn(t) {
  return {
    id: nextId(), type: 'EXPENSE', amount: 0, currency: S().baseCurrency, rate: 1, walletId: 0, toWalletId: null,
    categoryId: null, note: '', epochDay: C.todayEpoch(), createdAt: Date.now(), source: '', sourceId: 0, spread: false, tags: [], ...t,
  };
}

export function saveTxn(t) {
  if (!t.walletId) t = { ...t, walletId: defaultWallet() };
  if (t.tags) t = { ...t, tags: normTags(t.tags) };
  if (!t.id) data.txns.push(txn(t));
  else {
    const i = data.txns.findIndex((x) => x.id === t.id);
    if (i >= 0) {
      const old = data.txns[i];
      data.txns[i] = { ...old, ...t };
      if (old.source === 'PIGGY') piggyApply(old.sourceId, base(data.txns[i]) - base(old));
      if (old.source === 'PIGGYOUT') piggyApply(old.sourceId, base(old) - base(data.txns[i]));
    }
  }
  if (t.type === 'EXPENSE' && t.categoryId && !t.source) S().lastCategory = t.categoryId;
  S().lastWallet = t.walletId;
  changed();
}

export function deleteTxn(t) {
  data.txns = data.txns.filter((x) => x.id !== t.id);
  if (t.source === 'PIGGY') piggyApply(t.sourceId, -base(t));
  if (t.source === 'PIGGYOUT') piggyApply(t.sourceId, base(t));
  if (t.source === 'INSTALLMENT') { const i = byId(data.installments, t.sourceId); if (i) i.paid = Math.max(i.paid - 1, 0); }
  if (t.source === 'CIRCLE') {
    const c = byId(data.circles, t.sourceId);
    if (c) { if (t.type === 'INCOME') c.payoutReceived = false; else c.paid = Math.max(c.paid - 1, 0); }
  }
  if (t.source === 'LOAN') {
    const l = byId(data.loans, t.sourceId);
    if (l) {
      const lent = l.direction === 'LENT';
      if ((lent && t.type === 'INCOME') || (!lent && t.type === 'EXPENSE')) {
        l.repaid = Math.max(l.repaid - t.amount, 0);
        l.closed = l.repaid >= l.amount - 0.001;
      }
    }
  }
  changed();
}

/** Things bought lately (same name, last price): the add screen offers them as one-tap chips. */
export function recentItems(limit = 6) {
  const today = C.todayEpoch(), seen = new Map();
  for (const t of [...data.txns].sort((a, b) => b.epochDay - a.epochDay || b.createdAt - a.createdAt)) {
    if (t.type !== 'EXPENSE' || t.source || !t.note.trim() || t.epochDay < today - 90) continue;
    const k = t.note.trim().toLowerCase();
    const o = seen.get(k) || { note: t.note.trim(), amount: t.currency === S().baseCurrency ? t.amount : base(t), categoryId: t.categoryId, n: 0 };
    o.n++; seen.set(k, o);
  }
  return [...seen.values()].sort((a, b) => b.n - a.n).slice(0, limit);
}

/** Guesses the category for a quick note like "koshary": same note as before, or a category with that name. */
export function guessCategory(note, type) {
  if (!note.trim()) return null;
  const n = note.trim().toLowerCase();
  const prev = [...data.txns].reverse().find((t) => t.type === type && t.note.toLowerCase() === n && t.categoryId);
  if (prev) return prev.categoryId;
  const words = n.split(' ').filter((w) => w.length >= 3);
  return categories().filter((c) => c.kind === type).find((c) => {
    const cn = c.name.toLowerCase();
    return words.some((w) => cn === w || cn.startsWith(w) || w.startsWith(cn));
  })?.id ?? null;
}

/** Saves an expense typed as a sentence ("koshary 60"): category from the words, else the last one used. */
export function quickExpense(note, amount, daysAgo = 0) {
  const cat = guessCategory(note, 'EXPENSE') || S().lastCategory || null;
  const t = txn({ type: 'EXPENSE', amount, walletId: defaultWallet(), categoryId: cat, note: note.trim(), epochDay: C.todayEpoch() - daysAgo });
  data.txns.push(t);
  changed();
  return t;
}

/** Expenses you type again and again, so they can be added with one tap. */
export function suggestions(today = C.todayEpoch()) {
  const groups = new Map();
  for (const t of data.txns) {
    if (t.type !== 'EXPENSE' || t.source || !t.note.trim() || t.currency !== S().baseCurrency || t.epochDay < today - 60 || t.epochDay > today) continue;
    const k = `${t.note.trim().toLowerCase()}|${t.amount}`;
    const g = groups.get(k) || { note: t.note.trim(), amount: t.amount, n: 0 };
    g.n++; groups.set(k, g);
  }
  return [...groups.values()].filter((g) => g.n >= 2).sort((a, b) => b.n - a.n).slice(0, 4);
}

/** The last 7 days against the 7 before, for the Friday/Saturday card. */
export function week(today, dayReference) {
  const from = today - 6;
  const ex = (a, b) => data.txns.filter((t) => t.type === 'EXPENSE' && !t.spread && t.source !== 'PIGGY' && t.epochDay >= a && t.epochDay < b);
  const now = ex(from, today + 1), before = ex(from - 7, from);
  const top = {};
  for (const t of now) { const c = byId(data.categories, t.categoryId); if (!c) continue; const p = c.parentId ? byId(data.categories, c.parentId) : c; top[p.name] = (top[p.name] || 0) + base(t); }
  const perDay = {};
  for (const t of now) perDay[t.epochDay] = (perDay[t.epochDay] || 0) + base(t);
  let under = 0;
  if (dayReference > 0) for (let d = from; d <= today; d++) if ((perDay[d] || 0) <= dayReference) under++;
  const best = Object.entries(top).sort((a, b) => b[1] - a[1])[0];
  return { spent: now.reduce((a, t) => a + base(t), 0), previous: before.reduce((a, t) => a + base(t), 0), topCategory: best ? best[0] : null, topAmount: best ? best[1] : 0, daysUnder: under };
}

// ---------- Tags (projects): one word per tag, any transaction can have several ----------

export const normTags = (list) => [...new Set((list || []).map((x) => String(x).trim().replace(/^#/, '')).filter(Boolean))].slice(0, 5);
export const tagsOf = (t) => t.tags || [];
export function allTags() {
  const m = {};
  for (const t of data.txns) for (const g of tagsOf(t)) m[g] = (m[g] || 0) + 1;
  return Object.entries(m).sort((a, b) => b[1] - a[1]).map(([name, n]) => ({ name, n }));
}
/** Per tag: money spent, money earned, number of transactions and the split by category. */
export function tagTotals() {
  const m = new Map();
  const nm = (id) => { const c = byId(data.categories, id); if (!c) return 'No category'; const p = c.parentId ? byId(data.categories, c.parentId) : c; return (p || c).name; };
  for (const t of data.txns) {
    if (t.type === 'TRANSFER') continue;
    for (const g of tagsOf(t)) {
      const o = m.get(g) || { name: g, spent: 0, earned: 0, count: 0, cats: {}, last: 0 };
      if (t.type === 'EXPENSE') { o.spent += base(t); const k = nm(t.categoryId); o.cats[k] = (o.cats[k] || 0) + base(t); } else o.earned += base(t);
      o.count++; o.last = Math.max(o.last, t.epochDay);
      m.set(g, o);
    }
  }
  return [...m.values()].sort((a, b) => b.last - a.last);
}
export function renameTag(from, to) {
  to = String(to).trim().replace(/^#/, '');
  if (!to) return;
  for (const t of data.txns) if (tagsOf(t).includes(from)) t.tags = normTags(tagsOf(t).map((x) => (x === from ? to : x)));
  changed();
}
export function removeTag(name) { for (const t of data.txns) if (tagsOf(t).includes(name)) t.tags = tagsOf(t).filter((x) => x !== name); changed(); }

// ---------- Pay a whole group at once ----------

/** Payments of one group (a provider, credit cards, gam'eya, bills...) that fall due up to the end of the month starting at [monthStart]. */
export function groupPayments(monthStart) {
  const s = S();
  const cycle = C.cycleFor(monthStart, s.cycleStart);
  const end = cycle.nextStart;
  const groups = new Map();
  const add = (key, title, o) => { const g = groups.get(key) || { key, title, items: [], total: 0 }; g.items.push(o); g.total += o.amount; groups.set(key, g); };
  for (const b of data.bills) {
    if (b.active === false) continue;
    const n = C.occurrences(b.nextDue, b.frequency, b.everyN, b.nextDue, end, b.anchorDay).length;
    if (n > 0) add(b.kind === 'SUB' ? 'subs' : 'bills', b.kind === 'SUB' ? 'Subscriptions' : 'Bills', { kind: 'BILL', id: b.id, name: b.name, n, amount: estimate(b) * rateOf(b.currency) * n });
  }
  for (const i of data.installments) {
    const n = C.plan(i.amount, i.total, i.paid, i.firstDue).unpaidDueIn(-1e9, end).length;
    if (!n) continue;
    const card = String(i.type).toLowerCase() === 'credit card';
    const label = i.provider && i.provider.trim() ? i.provider.trim() : i.type;
    add(card ? 'cards' : 'p:' + label.toLowerCase(), card ? 'Credit cards' : label, { kind: 'INSTALLMENT', id: i.id, name: i.name, n, amount: i.amount * n });
  }
  for (const c of data.circles) {
    const n = C.circle(c).plan.unpaidDueIn(-1e9, end).length;
    if (n > 0) add('circles', "Gam'eya", { kind: 'CIRCLE', id: c.id, name: c.name, n, amount: c.share * n });
  }
  return [...groups.values()].sort((a, b) => b.total - a.total);
}
/** Marks every item of a group as paid. Returns what is needed to undo it. */
export function payGroup(group) {
  const undo = { txnIds: [], bills: [] };
  const before = new Set(data.txns.map((t) => t.id));
  for (const it of group.items) {
    for (let k = 0; k < it.n; k++) {
      if (it.kind === 'BILL') { const b = byId(data.bills, it.id); if (b) { undo.bills.push([b.id, b.nextDue]); payBill(b, it.each ?? null); } }
      if (it.kind === 'INSTALLMENT') { const x = byId(data.installments, it.id); if (x) payInstallment(x); }
      if (it.kind === 'CIRCLE') { const x = byId(data.circles, it.id); if (x) payCircle(x); }
    }
  }
  undo.txnIds = data.txns.filter((t) => !before.has(t.id)).map((t) => t.id);
  return undo;
}
export function undoPayGroup(undo) {
  for (const id of [...undo.txnIds].reverse()) { const t = byId(data.txns, id); if (t) deleteTxn(t); }
  for (const [id, due] of [...undo.bills].reverse()) { const b = byId(data.bills, id); if (b) b.nextDue = due; }
  changed();
}

// ---------- Bills ----------

export function saveBill(b) {
  if (!b.id) data.bills.push({ ...b, id: nextId() });
  else { const i = data.bills.findIndex((x) => x.id === b.id); data.bills[i] = b; }
  changed();
}
export function deleteBill(b) { data.bills = data.bills.filter((x) => x.id !== b.id); changed(); }
export function estimate(b) {
  if (!b.variable) return b.amount;
  const last = data.txns.filter((t) => t.source === 'BILL' && t.sourceId === b.id).sort((a, c) => c.epochDay - a.epochDay).slice(0, 3);
  return last.length ? last.reduce((s, t) => s + t.amount, 0) / last.length : b.amount;
}
export function payBill(b, amount = null, day = C.todayEpoch()) {
  data.txns.push(txn({
    type: 'EXPENSE', amount: amount ?? estimate(b), currency: b.currency, rate: rateOf(b.currency),
    walletId: b.walletId || defaultWallet(),
    categoryId: b.categoryId || categoryNamed(b.kind === 'SUB' ? 'Subscriptions' : 'Bills', 'EXPENSE'),
    note: b.name, epochDay: day, source: 'BILL', sourceId: b.id, spread: C.isSpread(b.frequency, b.everyN),
  }));
  skipBill(b);
}
export function skipBill(b) {
  const x = byId(data.bills, b.id);
  if (x) x.nextDue = C.nextDue(x.nextDue, x.frequency, x.everyN, x.anchorDay);
  changed();
}

// ---------- Installments ----------

export function saveInstallment(it) {
  if (!it.id) data.installments.push({ ...it, id: nextId() });
  else { const i = data.installments.findIndex((x) => x.id === it.id); data.installments[i] = it; }
  changed();
}
export function deleteInstallment(it) { data.installments = data.installments.filter((x) => x.id !== it.id); changed(); }
export function payInstallment(it, dayArg = null) {
  const x = byId(data.installments, it.id);
  if (!x || x.paid >= x.total) return;
  // Recorded on the day it was due when that day has passed, so old installments land in their own months.
  const due = C.plan(x.amount, x.total, x.paid, x.firstDue).dueOf(x.paid), today = C.todayEpoch();
  const day = dayArg ?? (due < today ? due : today);
  data.txns.push(txn({
    type: 'EXPENSE', amount: x.amount, walletId: x.walletId || defaultWallet(), categoryId: categoryNamed('Installments', 'EXPENSE'),
    note: x.name, epochDay: day, source: 'INSTALLMENT', sourceId: x.id,
  }));
  x.paid++;
  changed();
}

// ---------- Circles ----------

export function saveCircle(c) {
  if (!c.id) data.circles.push({ ...c, id: nextId() });
  else { const i = data.circles.findIndex((x) => x.id === c.id); data.circles[i] = c; }
  changed();
}
export function deleteCircle(c) { data.circles = data.circles.filter((x) => x.id !== c.id); changed(); }
export function payCircle(c, dayArg = null) {
  const x = byId(data.circles, c.id);
  if (!x || x.paid >= x.months) return;
  const due = C.circle(x).plan.dueOf(x.paid), today = C.todayEpoch();
  const day = dayArg ?? (due < today ? due : today);
  data.txns.push(txn({
    type: 'EXPENSE', amount: x.share, walletId: x.walletId || defaultWallet(), categoryId: categoryNamed("Gam'eya", 'EXPENSE'),
    note: `${x.name} share`, epochDay: day, source: 'CIRCLE', sourceId: x.id,
  }));
  x.paid++;
  changed();
}
export function receivePayout(c, dayArg = null) {
  const x = byId(data.circles, c.id);
  if (!x || x.payoutReceived) return;
  const pd = C.circle(x).payoutDate, today = C.todayEpoch();
  const day = dayArg ?? (pd < today ? pd : today);
  data.txns.push(txn({
    type: 'INCOME', amount: x.share * x.months, walletId: x.walletId || defaultWallet(), categoryId: categoryNamed("Gam'eya payout", 'INCOME'),
    note: `${x.name} payout`, epochDay: day, source: 'CIRCLE', sourceId: x.id,
  }));
  x.payoutReceived = true;
  changed();
}

// ---------- Loans ----------

export function addLoan({ person, lent, amount, walletId, day, due, note, record = true }) {
  const id = nextId();
  data.loans.push({ id, person, direction: lent ? 'LENT' : 'BORROWED', amount, repaid: 0, epochDay: day, dueDay: due ?? null, note: note || '', closed: false });
  if (record) {
    data.txns.push(txn({
      type: lent ? 'EXPENSE' : 'INCOME', amount, walletId: walletId || defaultWallet(),
      categoryId: lent ? categoryNamed('Loans', 'EXPENSE') : categoryNamed('Borrowed', 'INCOME'),
      note: lent ? `Lent to ${person}` : `Borrowed from ${person}`, epochDay: day, source: 'LOAN', sourceId: id,
    }));
  }
  changed();
}
export const remaining = (l) => Math.max(l.amount - l.repaid, 0);
export function loanPayment(l, amount, walletId = null, day = C.todayEpoch()) {
  const x = byId(data.loans, l.id);
  if (!x) return;
  const lent = x.direction === 'LENT';
  data.txns.push(txn({
    type: lent ? 'INCOME' : 'EXPENSE', amount, walletId: walletId || defaultWallet(),
    categoryId: lent ? categoryNamed('Loan repaid', 'INCOME') : categoryNamed('Loans', 'EXPENSE'),
    note: lent ? `${x.person} paid back` : `Paid back ${x.person}`, epochDay: day, source: 'LOAN', sourceId: x.id,
  }));
  x.repaid += amount;
  x.closed = x.repaid >= x.amount - 0.001;
  changed();
}
/** Deleting a loan also removes the money records it made, so wallets and the budget stay right. */
export function deleteLoan(l) {
  data.txns = data.txns.filter((t) => !(t.source === 'LOAN' && t.sourceId === l.id));
  data.loans = data.loans.filter((x) => x.id !== l.id);
  changed();
}

// ---------- Salary ----------

export function salaryDateIn(cycle, day) {
  const { y, m } = C.ymd(cycle.start);
  const next = m === 12 ? [y + 1, 1] : [y, m + 1];
  return [C.startIn(y, m, day), C.startIn(next[0], next[1], day)].find((d) => cycle.contains(d)) ?? null;
}
/** Records this month's salary on its day, once per cycle. */
export function ensureSalary(today = C.todayEpoch()) {
  const s = S();
  if (!(s.salary > 0)) return;
  const cycle = C.cycleFor(today, s.cycleStart);
  const d = salaryDateIn(cycle, s.salaryDay);
  if (d == null || today < d) return;
  if (data.txns.some((t) => t.source === 'SALARY' && cycle.contains(t.epochDay))) return;
  const w = defaultWallet();
  data.txns.push(txn({ type: 'INCOME', amount: s.salary, walletId: w, categoryId: categoryNamed('Salary', 'INCOME'), note: 'Salary', epochDay: d, source: 'SALARY' }));
  changed();
}

// ---------- Totals ----------

/** Everything unpaid that falls due before [to], including anything overdue. */
export function upcoming(to) {
  const out = [];
  for (const b of data.bills) {
    const est = estimate(b) * rateOf(b.currency);
    for (const d of C.occurrences(b.nextDue, b.frequency, b.everyN, b.nextDue, to, b.anchorDay)) {
      out.push({ kind: 'BILL', id: b.id, title: b.name, sub: C.describeFreq(b.frequency, b.everyN), amount: est, due: d, spread: C.isSpread(b.frequency, b.everyN), remind: b.remindDays });
    }
  }
  for (const i of data.installments) {
    C.plan(i.amount, i.total, i.paid, i.firstDue).unpaidDueIn(-1e9, to).forEach((d, k) => {
      out.push({ kind: 'INSTALLMENT', id: i.id, title: i.name, sub: `${i.paid + k + 1} of ${i.total} · ${i.type}`, amount: i.amount, due: d, remind: i.remindDays });
    });
  }
  for (const c of data.circles) {
    C.circle(c).plan.unpaidDueIn(-1e9, to).forEach((d, k) => {
      out.push({ kind: 'CIRCLE', id: c.id, title: `${c.name} share`, sub: `Month ${c.paid + k + 1} of ${c.months}`, amount: c.share, due: d, remind: c.remindDays });
    });
  }
  for (const l of data.loans) {
    if (l.dueDay == null || l.closed || l.dueDay >= to) continue;
    const lent = l.direction === 'LENT';
    out.push({ kind: 'LOAN', id: l.id, title: lent ? `${l.person} pays you back` : `Pay back ${l.person}`, sub: lent ? 'They owe you' : 'You owe', amount: remaining(l), due: l.dueDay, incoming: lent, remind: 1 });
  }
  return out.sort((a, b) => a.due - b.due);
}

export function payUpcoming(u) {
  if (u.kind === 'BILL') { const b = byId(data.bills, u.id); if (b) payBill(b); }
  if (u.kind === 'INSTALLMENT') { const i = byId(data.installments, u.id); if (i) payInstallment(i); }
  if (u.kind === 'CIRCLE') { const c = byId(data.circles, u.id); if (c) payCircle(c); }
  if (u.kind === 'LOAN') { const l = byId(data.loans, u.id); if (l) loanPayment(l, remaining(l)); }
}

export function setAside() {
  return data.bills.filter((b) => C.isSpread(b.frequency, b.everyN))
    .reduce((s, b) => s + C.monthlyShare(b.amount * rateOf(b.currency), b.frequency, b.everyN), 0) + (S().savingsPerMonth || 0) + (S().goals || []).reduce((a, g) => a + (g.monthly || 0), 0);
}

export function home(today = C.todayEpoch()) {
  const s = S();
  const cycle = C.cycleFor(today, s.cycleStart);
  const txns = data.txns.filter((t) => cycle.contains(t.epochDay));
  const income = txns.filter((t) => t.type === 'INCOME').reduce((a, t) => a + base(t), 0);
  const spending = txns.filter((t) => t.type === 'EXPENSE' && !t.spread && t.epochDay <= today);
  const sum = (l) => l.reduce((a, t) => a + base(t), 0);
  const spentBefore = sum(spending.filter((t) => t.epochDay < today));
  const spentToday = sum(spending.filter((t) => t.epochDay === today));
  const everyday = sum(spending.filter((t) => !t.source));
  const salaryPosted = txns.some((t) => t.source === 'SALARY');
  const salaryExpected = s.salary > 0 && !salaryPosted && salaryDateIn(cycle, s.salaryDay) != null ? s.salary : 0;
  const all = upcoming(cycle.nextStart + 35);
  const commitments = all.filter((u) => !u.incoming && !u.spread && u.due < cycle.nextStart).reduce((a, u) => a + u.amount, 0);
  const aside = setAside();
  const groceries = categoryNamed('Groceries', 'EXPENSE');
  const householdSpent = s.household > 0 && groceries ? sum(spending.filter((t) => !t.source && t.categoryId === groceries)) : 0;
  const b = C.computeBudget({ cycle, today, incomeReceived: income, salaryExpected, setAside: aside, spentBeforeToday: spentBefore, spentToday, commitments, everydaySpent: everyday, household: s.household || 0, householdSpent });
  const perDay = {};
  for (const t of spending) perDay[t.epochDay] = (perDay[t.epochDay] || 0) + base(t);
  const days = [];
  for (let d = cycle.start; d <= today && d < cycle.nextStart; d++) days.push([d, perDay[d] || 0]);
  return {
    cycle, today, budget: b, days, dayReference: cycle.length > 0 ? b.budget / cycle.length : 0,
    upcoming: all.filter((u) => u.due < today + 35), setAside: aside, income, salaryExpected,
    streak: streakNow(today, b, cycle),
  };
}

/** Flexible spending per day: everyday expenses, without the household shopping that has its own reserve. */
function flexPerDay(from, to) {
  const groceries = S().household > 0 ? categoryNamed('Groceries', 'EXPENSE') : null;
  const m = {};
  for (const t of data.txns) {
    if (t.type !== 'EXPENSE' || t.spread || t.source || t.epochDay < from || t.epochDay > to) continue;
    if (groceries && t.categoryId === groceries) continue;
    m[t.epochDay] = (m[t.epochDay] || 0) + base(t);
  }
  return m;
}
/** The saving challenge: days in a row where the last 7 days stayed under the weekly share of the flexible money. */
function streakNow(today, b, cycle) {
  if (!(b.flexTotal > 0)) return null;
  const spendDays = data.txns.filter((t) => t.type === 'EXPENSE' && !t.source).map((t) => t.epochDay);
  if (!spendDays.length) return null;
  const first = Math.min(...spendDays);
  if (today - first < 2) return null;
  const perDay = flexPerDay(today - 130, today);
  return C.streak(perDay, today, b.flexTotal / cycle.length * 7, first);
}

/** Everything the planning tools need: the home numbers, spending per top-level category now and usually, and bills that could wait. */
export function planData(today = C.todayEpoch()) {
  const h = home(today), s = S();
  const topName = (id) => { const c = byId(data.categories, id); if (!c) return 'Other'; const p = c.parentId ? byId(data.categories, c.parentId) : c; return (p || c).name; };
  const totals = (from, toExcl, upTo = null) => {
    const m = {};
    for (const t of data.txns) {
      if (t.type !== 'EXPENSE' || t.spread || t.source === 'PIGGY' || t.epochDay < from || t.epochDay >= toExcl || (upTo != null && t.epochDay > upTo)) continue;
      const n = topName(t.categoryId); m[n] = (m[n] || 0) + base(t);
    }
    return m;
  };
  const now = totals(h.cycle.start, h.cycle.nextStart, today);
  const past = [];
  let c = h.cycle;
  for (let i = 0; i < 3; i++) { c = C.previousCycle(c, s.cycleStart); past.push(totals(c.start, c.nextStart)); }
  const withData = past.filter((m) => Object.keys(m).length);
  const names = [...new Set([...Object.keys(now), ...withData.flatMap((m) => Object.keys(m))])];
  const dayIndex = h.cycle.dayIndex(today);
  const categories = names.map((name) => {
    const usual = withData.length ? withData.reduce((a, m) => a + (m[name] || 0), 0) / withData.length : 0;
    const spentSoFar = now[name] || 0;
    return { name, spentSoFar, usual, monthly: usual > 0 ? usual : (dayIndex > 0 ? spentSoFar / dayIndex * h.cycle.length : spentSoFar) };
  }).sort((a, b) => Math.max(b.usual, b.spentSoFar) - Math.max(a.usual, a.spentSoFar));
  const everyday = data.txns.filter((t) => t.type === 'EXPENSE' && !t.spread && !t.source && t.epochDay >= h.cycle.start && t.epochDay <= today).reduce((a, t) => a + base(t), 0);
  const postponable = h.upcoming.filter((u) => u.kind === 'BILL' && !u.incoming && !u.spread && u.due > today && u.due < h.cycle.nextStart).map((u) => ({ name: u.title, amount: u.amount, due: u.due }));
  const emergency = C.emergency(today, h.cycle, h.budget, everyday, dayIndex, postponable, categories.map((x) => ({ name: x.name, spent: x.spentSoFar })));
  const rises = C.priceRises(categories, dayIndex, h.cycle.length);
  return { home: h, categories, everydaySpent: everyday, dayIndex, postponable, emergency, rises };
}

/** Current balance of every wallet, in the base currency. */
export function balances() {
  const m = {};
  for (const w of data.wallets) m[w.id] = w.opening * rateOf(w.currency);
  for (const t of data.txns) {
    if (t.type === 'EXPENSE') m[t.walletId] = (m[t.walletId] || 0) - base(t);
    else if (t.type === 'INCOME') m[t.walletId] = (m[t.walletId] || 0) + base(t);
    else { m[t.walletId] = (m[t.walletId] || 0) - base(t); if (t.toWalletId) m[t.toWalletId] = (m[t.toWalletId] || 0) + base(t); }
  }
  return m;
}

/** "How much do I have now?": shifts the opening balance so the one total matches what the person says. */
export function setBalance(target) {
  const wid = defaultWallet();
  const cur = Object.values(balances()).reduce((a, v) => a + v, 0);
  const w = byId(data.wallets, wid);
  w.opening += (target - cur) / rateOf(w.currency);
  changed();
}

/** Items to remind about today: due within each item's reminder window, or late. */
export function dueSoon(today = C.todayEpoch()) {
  return upcoming(today + 31).filter((u) => u.due - today <= (u.remind ?? 1));
}

/** What you own: wallets, gold you bought at today's price, and how it looks in dollars and gold. */
export function wealth() {
  const s = S();
  const bal = balances();
  const cash = Object.values(bal).reduce((a, v) => a + v, 0);
  const gs = C.goldSummary(s.goldBuys || [], s.gold || {});
  const gold = gs ? gs.value : 0;
  const piggyHeld = data.txns.reduce((a, t) => a + (t.source === 'PIGGY' ? base(t) : t.source === 'PIGGYOUT' ? -base(t) : 0), 0);
  const total = cash + piggyHeld + gold;
  const usd = s.rates.USD || s.onlineRates.USD;
  const cycle = C.cycleFor(C.todayEpoch(), s.cycleStart);
  const salaryComing = s.salary > 0 && !data.txns.some((t) => t.source === 'SALARY' && cycle.contains(t.epochDay)) && salaryDateIn(cycle, s.salaryDay) != null ? s.salary : 0;
  return { cash, piggyHeld, gold, total, salaryComing, dollars: usd > 0 ? total / usd : null, goldGrams: s.gold && s.gold['21'] > 0 ? total / s.gold['21'] : null, history: s.wealthHistory || [] };
}
/** Saves today's total once a day (a later call the same day replaces it). */
export function recordWealth(today = C.todayEpoch()) {
  if (!S().onboarded) return;
  const w = wealth();
  const old = S().wealthHistory || [];
  const next = C.recordWealth(old, today, w.total);
  if (JSON.stringify(next) === JSON.stringify(old)) return;
  data.settings.wealthHistory = next;
  persist();
}

// ---------- Category limits and spending level ----------

/** Top-level expense categories whose spending this cycle is above their monthly limit: [{ name, spent, limit }]. */
export function categoriesOverLimit(today = C.todayEpoch()) {
  const cycle = C.cycleFor(today, S().cycleStart);
  const cats = data.categories.filter((c) => c.kind === 'EXPENSE');
  const byTop = {};
  for (const t of data.txns) {
    if (t.type !== 'EXPENSE' || t.spread || t.source === 'PIGGY' || !cycle.contains(t.epochDay)) continue;
    const c = byId(cats, t.categoryId);
    if (!c) continue;
    const top = c.parentId || c.id;
    byTop[top] = (byTop[top] || 0) + base(t);
  }
  return cats.filter((c) => !c.parentId && c.monthlyLimit > 0 && !c.archived && C.limitOver(byTop[c.id] || 0, c.monthlyLimit))
    .map((c) => ({ id: c.id, name: c.name, spent: byTop[c.id], limit: c.monthlyLimit }));
}

/** What you spend in a normal month: average of the last 3 finished cycles that had spending, else this cycle so far. */
export function monthlySpend(today = C.todayEpoch()) {
  const s = S();
  const total = (c) => data.txns.filter((t) => t.type === 'EXPENSE' && !t.spread && t.source !== 'PIGGY' && c.contains(t.epochDay)).reduce((a, t) => a + base(t), 0);
  let c = C.cycleFor(today, s.cycleStart);
  const cur = total(c), past = [];
  for (let i = 0; i < 3; i++) { c = C.previousCycle(c, s.cycleStart); const v = total(c); if (v > 0) past.push(v); }
  return past.length ? past.reduce((a, v) => a + v, 0) / past.length : cur;
}

// ---------- Piggy bank and goals ----------

/** Puts [amount] into the piggy bank (negative takes it out), dated today. */
export function piggyAdd(amount) {
  if (!amount) return;
  data.settings.piggy = C.piggyAdd(S().piggy || [], C.todayEpoch(), amount);
  changed();
}
/** Moves [amount] into (or out of) the piggy bank and the goal it belongs to. goalId: -1 = general piggy, 0 = main goal, n = extra goal. */
function piggyApply(goalId, amount) {
  if (!amount) return;
  if (goalId === 0) data.settings.goalSaved = Math.max((S().goalSaved || 0) + amount, 0);
  else if (goalId > 0) data.settings.goals = (S().goals || []).map((g) => (g.id === goalId ? { ...g, saved: Math.max(g.saved + amount, 0) } : g));
  data.settings.piggy = C.piggyAdd(S().piggy || [], C.todayEpoch(), amount);
}
/** Piggy bank category (created on first use, so old data works too). */
function piggyCategory(kind) {
  const name = kind === 'INCOME' ? 'From piggy bank' : 'Piggy bank';
  ensureSeeded();
  let id = categoryNamed(name, kind);
  if (id == null) {
    id = nextId();
    data.categories.push({ id, name, parentId: null, kind, monthlyLimit: 0, sort: 99, archived: false });
  }
  return id;
}
/** "Piggy" as a kind of spending: recorded with the expenses, counts against what is left, and goes into the piggy bank by itself. */
export function savePiggyTxn({ amount, goalId = -1, epochDay = C.todayEpoch(), note = '', tags = [] }) {
  if (!(amount > 0)) return false;
  data.txns.push(txn({ type: 'EXPENSE', amount, walletId: defaultWallet(), categoryId: piggyCategory('EXPENSE'), note: note || 'Piggy bank', epochDay, source: 'PIGGY', sourceId: goalId, tags: normTags(tags) }));
  piggyApply(goalId, amount);
  changed();
  return true;
}
/** Takes money back out of the piggy bank and records it as income "From piggy bank". */
export function takeFromPiggy({ amount, goalId = -1, epochDay = C.todayEpoch() }) {
  if (!(amount > 0)) return false;
  data.txns.push(txn({ type: 'INCOME', amount, walletId: defaultWallet(), categoryId: piggyCategory('INCOME'), note: 'From piggy bank', epochDay, source: 'PIGGYOUT', sourceId: goalId }));
  piggyApply(goalId, -amount);
  changed();
  return true;
}
/** After the salary arrives: the amount the user planned to move to the piggy bank this cycle (0 = nothing to ask). Never moves by itself. */
export function piggyAutoDue(today = C.todayEpoch()) {
  const s = S();
  if (!(s.piggyAuto > 0 || s.piggyAutoPct > 0)) return 0;
  const cycle = C.cycleFor(today, s.cycleStart);
  if (s.piggyAutoDone === cycle.start) return 0;
  const sal = data.txns.find((t) => t.source === 'SALARY' && cycle.contains(t.epochDay));
  if (!sal) return 0;
  const amt = s.piggyAuto > 0 ? s.piggyAuto : Math.round(base(sal) * s.piggyAutoPct / 100);
  return amt > 0 ? amt : 0;
}
export function piggyAutoAnswer(yes, today = C.todayEpoch()) {
  const amt = piggyAutoDue(today);
  if (amt <= 0) return;
  if (yes) savePiggyTxn({ amount: amt, note: 'Piggy bank (monthly)' });
  data.settings.piggyAutoDone = C.cycleFor(today, S().cycleStart).start;
  changed();
}
/** Adds to the main savings goal; the money also goes into the piggy bank. */
export function addToMainGoal(amount) {
  if (!(amount > 0)) return;
  data.settings.goalSaved = (S().goalSaved || 0) + amount;
  piggyAdd(amount);
}
/** Adds to one of the extra goals; the money also goes into the piggy bank. */
export function addToGoal(id, amount) {
  if (!(amount > 0)) return;
  data.settings.goals = (S().goals || []).map((g) => (g.id === id ? { ...g, saved: g.saved + amount } : g));
  piggyAdd(amount);
}
export function saveGoal(goal) {
  const list = S().goals || [];
  const id = goal.id > 0 ? goal.id : Math.max(0, ...list.map((g) => g.id)) + 1;
  const next = { ...goal, id };
  data.settings.goals = list.some((g) => g.id === id) ? list.map((g) => (g.id === id ? next : g)) : [...list, next];
  changed();
}
export function deleteGoal(id) { data.settings.goals = (S().goals || []).filter((g) => g.id !== id); changed(); }

// ---------- Month plan ----------

/** Past months from what really happened, this month as what happened plus what is still due, then expected months. */
export function monthPlan(past = 3, future = 12, today = C.todayEpoch()) {
  const s = S(), nowYm = P.ymOf(today);
  const input = {
    salaryBase: s.salary, salarySteps: s.salarySteps || {}, livingBase: s.livingBase || 0, livingOverrides: s.livingOverrides || {},
    bills: data.bills.filter((b) => b.active !== false).map((b) => ({ name: b.name, amount: b.amount * rateOf(b.currency), frequency: b.frequency, everyN: b.everyN, nextDue: b.nextDue, anchorDay: b.anchorDay })),
    installments: data.installments.map((i) => ({ name: i.name, amount: i.amount, total: i.total, paid: i.paid, firstDue: i.firstDue, card: String(i.type).toLowerCase() === 'credit card' })),
    circles: data.circles,
    loans: data.loans.filter((l) => !l.closed).map((l) => ({ person: l.person, remaining: remaining(l), due: l.dueDay, lent: l.direction === 'LENT' })),
  };
  const proj = Object.fromEntries(P.project(input, nowYm, future + 1).map((m) => [m.ym, m]));
  const rows = [];
  for (let ym = nowYm - past; ym <= nowYm + future; ym++) {
    const from = P.ymStart(ym), to = P.ymEnd(ym);
    const tx = data.txns.filter((t) => t.type !== 'TRANSFER' && t.epochDay >= from && t.epochDay < to);
    const a = actualMonth(ym, tx);
    const p = proj[ym];
    if (ym < nowYm || !p) rows.push(a);
    else rows.push({
      ...p, salary: a.salary + (a.salary > 0 ? 0 : p.salary), payout: a.payout + p.payout, loansIn: a.loansIn + p.loansIn,
      bills: a.bills + p.bills, installments: a.installments + p.installments, cards: a.cards + p.cards,
      circleShares: a.circleShares + p.circleShares, loansOut: a.loansOut + p.loansOut, living: Math.max(p.living, a.living),
    });
  }
  return P.withCumulative(rows);
}
function actualMonth(ym, tx) {
  const inc = tx.filter((t) => t.type === 'INCOME'), exp = tx.filter((t) => t.type === 'EXPENSE');
  const sum = (l, src) => l.filter((t) => t.source === src).reduce((a, t) => a + base(t), 0);
  const payout = sum(inc, 'CIRCLE'), loansIn = sum(inc, 'LOAN');
  const committed = ['BILL', 'INSTALLMENT', 'CIRCLE', 'LOAN', 'PIGGY'];
  return {
    ym, actual: true, salary: inc.reduce((a, t) => a + base(t), 0) - payout - loansIn - sum(inc, 'PIGGYOUT'), payout, loansIn,
    bills: sum(exp, 'BILL'), installments: sum(exp, 'INSTALLMENT'), cards: 0, circleShares: sum(exp, 'CIRCLE'), loansOut: sum(exp, 'LOAN'),
    living: exp.filter((t) => !committed.includes(t.source)).reduce((a, t) => a + base(t), 0), lines: [],
  };
}
