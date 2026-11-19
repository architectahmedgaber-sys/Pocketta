import * as C from './core.js';
import * as R from './store.js';
import { shareMonth } from './share.js';
import { startArabic } from './i18n.js';
import { COUNTRIES, OTHER_CURRENCIES, FLAGS, CURRENCY_AR, decimalsOf } from './countries.js';
import * as P from './plan.js';
import * as AI from './ai.js';
import * as Sync from './sync.js';

const $ = (s, el = document) => el.querySelector(s);
const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const VERSION = '1.17.1';

const ICON = {
  home: 'M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z',
  spend: 'M3 13h2v-2H3v2zm0 4h2v-2H3v2zm0-8h2V7H3v2zm4 4h14v-2H7v2zm0 4h14v-2H7v2zM7 7v2h14V7H7z',
  bills: 'M9 11H7v2h2v-2zm4 0h-2v2h2v-2zm4 0h-2v2h2v-2zm2-7h-1V2h-2v2H8V2H6v2H5c-1.11 0-2 .9-2 2v14a2 2 0 0 0 2 2h14c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 16H5V9h14v11z',
  owe: 'M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z',
  commit: 'M9 11H7v2h2v-2zm4 0h-2v2h2v-2zm4 0h-2v2h2v-2zm2-7h-1V2h-2v2H8V2H6v2H5c-1.11 0-2 .9-2 2v14a2 2 0 0 0 2 2h14c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 16H5V9h14v11z',
  settings: 'M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58a.49.49 0 0 0 .12-.61l-1.92-3.32a.488.488 0 0 0-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54a.484.484 0 0 0-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58a.49.49 0 0 0-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z',
  more: 'M3 18h18v-2H3v2zm0-5h18v-2H3v2zm0-7v2h18V6H3z',
  add: 'M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z',
  back: 'M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z',
  del: 'M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z',
  left: 'M15.41 7.41 14 6l-6 6 6 6 1.41-1.41L10.83 12z',
  right: 'M10 6 8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z',
  share: 'M16 5l-1.42 1.42-1.59-1.59V16h-1.98V4.83L9.42 6.42 8 5l4-4 4 4zm4 5v11c0 1.1-.9 2-2 2H6a2 2 0 0 1-2-2V10c0-1.11.89-2 2-2h3v2H6v11h12V10h-3V8h3a2 2 0 0 1 2 2z',
};
const icon = (n, size = 24) => `<svg viewBox="0 0 24 24" width="${size}" height="${size}" aria-hidden="true"><path d="${ICON[n]}" fill="currentColor"/></svg>`;

const TABS = [['home', 'Today'], ['spend', 'Spending'], ['assistant', 'Miqrish'], ['commit', 'Commitments'], ['more', 'More']];
// Pages that draw their own top bar (so they are not wrapped in the usual page container).
const SELF_WRAPPED = new Set(['more', 'assistant']);

// ---------- UI state ----------

const ui = {
  stack: [{ r: 'home' }],
  spendOffset: 0, spendFilter: null, spendSearch: '',
  commitTab: 0, catKind: 'EXPENSE',
  details: false, quickText: '', quickHint: false, quickSaved: null, heroShown: 0, hideGuide: false,
  conv: { amount: '1', from: 'USD', to: '' },
  plan: { price: '', cat: null, pct: 20 },
  locked: false, hiddenAt: 0,
  hideInstall: false, updateReady: false,
  mp: { open: null },
  chat: { bubbles: [], busy: false, settings: null, draft: '', session: null },
};
try { ui.hideInstall = localStorage.getItem('pocketta-hide-install') === '1'; ui.hideGuide = localStorage.getItem('pocketta-hide-guide') === '1'; } catch { /* private mode */ }
const cur = () => ui.stack[ui.stack.length - 1];
const isTab = (r) => TABS.some((t) => t[0] === r);
function go(r, params = {}) { ui.stack = isTab(r) ? [{ r }] : [...ui.stack, { r, ...params }]; render(); window.scrollTo(0, 0); }
function back() { ui.stack = ui.stack.length > 1 ? ui.stack.slice(0, -1) : [{ r: 'home' }]; render(); }

// ---------- Small pieces ----------

const money = (v) => C.short(v);
const base = () => R.S().baseCurrency;
const card = (inner, cls = '', attrs = '') => `<section class="card ${cls}" ${attrs}>${inner}</section>`;
const label = (t) => `<div class="label">${esc(t)}</div>`;
const pill = (t, tone = '') => `<span class="pill ${tone}">${esc(t)}</span>`;
const progress = (f, tone = '') => `<div class="bar"><i class="${tone}" style="width:${Math.max(0, Math.min(1, f)) * 100}%"></i></div>`;
const hue = (s) => { let h = 0; for (const c of String(s)) h = (h * 31 + c.charCodeAt(0)) >>> 0; return h % 360; };
const initial = (s) => `<span class="avatar" style="--h:${hue(s)}">${esc(String(s || '?').trim().charAt(0).toUpperCase() || '?')}</span>`;
function row(title, sub, { icon: ic, act, data = '', trailing = '' } = {}) {
  return `<div class="row ${act ? 'tap' : ''}" ${act ? `data-act="${act}" ${data}` : ''}>
    ${ic != null ? initial(ic) : ''}
    <div class="row-main"><div class="row-title">${esc(title)}</div>${sub ? `<div class="row-sub">${esc(sub)}</div>` : ''}</div>
    ${trailing}</div>`;
}
const fab = (text, act, data = '') => `<button class="fab" data-act="${act}" ${data}>${icon('add')}<span>${esc(text)}</span></button>`;
function chips(group, options, selected, cls = '') {
  return `<div class="chips ${cls}" data-group="${group}">${options.map(([v, l]) =>
    `<button type="button" class="chip ${String(v) === String(selected) ? 'on' : ''}" data-act="chip" data-val="${esc(v)}">${esc(l)}</button>`).join('')}</div>`;
}
const chipVal = (root, group) => $(`[data-group="${group}"] .chip.on`, root)?.dataset.val ?? null;
const field = (name, lbl, value = '', { type = 'text', mode, suffix, ph = '' } = {}) =>
  `<label class="field"><span>${esc(lbl)}</span><div class="input-wrap"><input name="${name}" type="${type}" ${mode ? `inputmode="${mode}"` : ''} value="${esc(value)}" placeholder="${esc(ph)}" autocomplete="off">${suffix ? `<em>${esc(suffix)}</em>` : ''}</div></label>`;
const numField = (name, lbl, value, opts = {}) => field(name, lbl, value === 0 || value == null ? (opts.keepZero ? '0' : '') : String(value), { mode: opts.int ? 'numeric' : 'decimal', ...opts });
const dateField = (name, lbl, ed) => `<label class="field"><span>${esc(lbl)}</span><div class="input-wrap"><input name="${name}" type="date" value="${ed != null ? C.isoOf(ed) : ''}"></div></label>`;
const check = (name, lbl, on) => `<label class="check"><input type="checkbox" name="${name}" ${on ? 'checked' : ''}><span>${esc(lbl)}</span></label>`;
const val = (root, name) => $(`[name="${name}"]`, root)?.value ?? '';
const num = (root, name) => C.parseNumber(val(root, name));
const checked = (root, name) => !!$(`[name="${name}"]`, root)?.checked;

function toast(msg, ms = 3200) {
  const t = $('#toast');
  t.textContent = msg;
  t.classList.add('show');
  clearTimeout(toast.t);
  toast.t = setTimeout(() => t.classList.remove('show'), ms);
}

// ---------- Sheets (dialogs) ----------

function sheet({ title, body, saveLabel = 'Save', onSave, onDelete, deleteLabel = 'Delete', extra = '', onClose }) {
  const el = document.createElement('div');
  el.className = 'scrim';
  el.innerHTML = `<div class="sheet" role="dialog" aria-label="${esc(title)}">
    <div class="grip"></div><h2>${esc(title)}</h2>
    <form class="sheet-body" onsubmit="return false">${body}</form>
    <div class="sheet-actions">
      ${onDelete ? `<button class="btn text danger" data-s="del">${esc(deleteLabel)}</button>` : ''}
      <span class="grow"></span>${extra}
      <button class="btn text" data-s="cancel">Cancel</button>
      ${onSave ? `<button class="btn filled" data-s="save">${esc(saveLabel)}</button>` : ''}
    </div></div>`;
  document.body.appendChild(el);
  document.body.classList.add('noscroll');
  requestAnimationFrame(() => el.classList.add('open'));
  const close = () => { el.classList.remove('open'); document.body.classList.remove('noscroll'); setTimeout(() => el.remove(), 200); if (onClose) onClose(); };
  el.addEventListener('click', (e) => {
    if (e.target === el) return close();
    const b = e.target.closest('[data-s]');
    if (!b) return;
    if (b.dataset.s === 'cancel') close();
    if (b.dataset.s === 'del') {
      if (b.dataset.armed) { onDelete(el); close(); } else { b.dataset.armed = '1'; b.textContent = 'Tap again to delete'; }
    }
    if (b.dataset.s === 'save') {
      const err = onSave(el);
      if (typeof err === 'string') toast(err); else close();
    }
  });
  return { el, close };
}
function confirmSheet(title, text, yes, onYes) {
  sheet({ title, body: `<p>${esc(text)}</p>`, saveLabel: yes, onSave: () => { onYes(); } });
}

// ---------- Render ----------

function render() {
  const app = $('#app');
  if (ui.locked) { app.innerHTML = lockScreen(); $('#pin')?.focus(); return; }
  if (!R.S().onboarded) { app.innerHTML = onboarding(); return; }
  const c = cur();
  const pages = { home, spend, commit, more, add, income, tagsPage, tagDetail, categories, currencyPage, settings, plan, wealthPage, piggyPage, goalsPage, monthPlanPage, assistant: assistantPage };
  const page = pages[c.r === 'currencies' ? 'currencyPage' : c.r] || home;
  if (isTab(c.r)) {
    const tabIcon = (r) => (r === 'assistant' ? '<span class="coin">🪙</span>' : icon(r));
    app.innerHTML = (SELF_WRAPPED.has(c.r) ? page(c) : `<main class="page wide">${page(c)}</main>`) +
      `<nav class="tabs">${TABS.map(([r, l]) => `<button class="tab ${r === 'assistant' ? 'center' : ''} ${c.r === r ? 'on' : ''}" data-act="tab" data-r="${r}"><span class="ti">${tabIcon(r)}</span><span>${esc(l)}</span></button>`).join('')}</nav>`;
  } else {
    app.innerHTML = page(c);
  }
  applyTheme();
  afterRender(c);
  updateBadge();
}

function topbar(title, actions = '') {
  return `<header class="topbar"><button class="icon-btn" data-act="back" aria-label="Back">${icon('back')}</button><h1>${esc(title)}</h1>${actions}</header>`;
}

// ---------- Today ----------

const CHART_COLORS = ['#1F6B4F', '#C2571A', '#2B5FB0', '#8E3B8C', '#E8A33D', '#00687A', '#B3261E', '#6D7B74'];
const isDark = () => document.documentElement.dataset.theme === 'dark' || (document.documentElement.dataset.theme !== 'light' && matchMedia('(prefers-color-scheme: dark)').matches);
function applyTheme() {
  const t = R.S().theme;
  const el = document.documentElement;
  if (t === 'DARK') el.dataset.theme = 'dark'; else if (t === 'LIGHT') el.dataset.theme = 'light'; else delete el.dataset.theme;
  const dark = isDark();
  document.querySelectorAll('meta[name="theme-color"]').forEach((m) => m.setAttribute('content', dark ? '#0f1512' : '#1F6B4F'));
}

function home() {
  R.ensureSalary();
  const s = R.home();
  const b = s.budget;
  const cur = base();
  const ios = /iphone|ipad|ipod/i.test(navigator.userAgent) && !navigator.standalone;
  const over = b.flexLeft < 0;
  const spentPct = Math.round(b.flexSpentFraction * 100), monthPct = Math.round(b.elapsedFraction * 100);
  // Green while you spend slower than the month passes, amber when you are ahead of it, red when the flexible money is gone.
  const tone = over ? 'red' : b.flexSpentFraction > b.elapsedFraction + 0.1 ? 'amber' : 'green';
  let html = `<div class="head"><div class="grow"><h1 class="title">${C.fmtMonth(s.cycle.end)}</h1><div class="small">${b.daysLeft} days left in this month</div></div>
    <button class="icon-btn emoji" data-act="toggle-theme" aria-label="Night mode">${isDark() ? '☀️' : '🌙'}</button>
    <button class="icon-btn" data-act="open" data-r="more" aria-label="Settings and more">${icon('settings')}</button></div>`;
  if (ui.updateReady) {
    html += card(`<div class="row-flex"><div class="grow"><b>A new version of Pocketta is ready</b><div class="small">Tap Update to get it. Your data stays as it is.</div></div>
      <button class="btn filled" data-act="apply-update">Update</button></div>`, 'tonal');
  }
  if (ios && !ui.hideInstall) {
    html += card(`<div class="row-flex"><div class="grow"><b>Put Pocketta on your home screen</b><div class="small">In Safari tap ${icon('share', 16)} Share, then <b>Add to Home Screen</b>. It then opens like an app and works offline.</div></div>
      <button class="btn text" data-act="hide-install">OK</button></div>`, 'tonal');
  }
  if (b.budget <= 0 && s.salaryExpected <= 0) {
    html += card(`<b>Add your salary to see how much you can spend</b><div class="small">Tap here to set it</div>`, 'tap', 'data-act="open" data-r="income"');
  }
  // The one question: how much is left for everyday spending after the commitments?
  html += card(`<div class="hero-t">${over ? 'Over the flexible amount' : 'Left for flexible spending'}</div>
    <div class="hero-n"><span id="hero-amt" data-v="${Math.abs(b.flexLeft)}">${money(Math.abs(b.flexLeft))}</span><span class="hero-c"> ${esc(cur)}</span></div>
    <div class="hero-s">of ${money(Math.max(b.flexTotal, 0))} left after commitments${b.household > 0 ? ' and household shopping' : ''}</div>
    <div class="hero-row"><span>Month passed</span><b>${monthPct}%</b></div><div class="bar hero-bar"><i style="width:${monthPct}%"></i></div>
    <div class="hero-row"><span>Flexible money spent</span><b>${spentPct}%</b></div><div class="bar hero-bar"><i style="width:${spentPct}%"></i></div>`, `hero ${tone}`);
  html += streakCard(s);
  html += quickCard();
  html += converterCard();
  html += planEntryCard();
  html += wealthEntry();
  html += gameyaCards(s);
  html += weekCard(s);
  html += limitCard(s);
  html += monthEndCard(s);
  html += goalCard();
  const sp = b.spentFraction, cm = b.committedFraction, rest = Math.max(1 - sp - cm, 0);
  html += card(`<div class="row-flex"><b class="grow">This month</b><span class="small">${money(b.left)} left of ${money(b.budget)} ${esc(cur)}</span></div>
    <div class="seg3"><i style="flex:${sp};background:${b.left < 0 ? 'var(--error)' : 'var(--primary)'}"></i><i style="flex:${cm};background:var(--warn)"></i><i style="flex:${rest};background:var(--variant)"></i></div>
    <div class="legend"><span><i style="background:var(--primary)"></i>Spent</span><span><i style="background:var(--warn)"></i>Still to pay</span><span><i style="background:var(--outline)"></i>Free</span></div>`);
  html += startGuide(s);
  if (s.upcoming.length) {
    const list = s.upcoming.slice(0, 3);
    ui.homeUpcoming = list;
    html += card(label('Coming up') + list.map((u, i) => {
      const late = u.due < s.today;
      const can = u.due <= s.today + 3;
      return `<div class="row-flex line"><div class="grow"><div class="ellipsis">${esc(u.title)}</div><div class="small ${late ? 'bad-t' : ''}">${late ? 'Late · ' : ''}${C.fmtDay(u.due)}</div></div>
        <b class="${u.incoming ? 'good-t' : ''}">${u.incoming ? '+' : ''}${money(u.amount)}</b>
        ${can ? `<button class="btn text" data-act="pay-up" data-i="${i}">${u.incoming ? 'Got it' : 'Paid'}</button>` : ''}</div>`;
    }).join('') + `<div class="small">Amounts in ${esc(cur)}</div>`);
  }
  html += `<button class="btn text" data-act="toggle-details">${ui.details ? 'Hide details' : 'Show details (forecast, days, how it is calculated)'}</button>`;
  if (ui.details) {
    html += card(`<div class="row-flex"><div class="grow">${label('Month-end forecast')}<div class="mid">${money(b.forecastSpend)} ${esc(cur)}</div></div>
      ${b.forecastDiff >= 0 ? pill(`${money(b.forecastDiff)} under`, 'good') : pill(`${money(-b.forecastDiff)} over`, 'bad')}</div>`);
    if (s.days.length) html += card(label('This month, day by day') + dayBars(s));
    const line = (l, v, bold) => `<div class="kv ${bold ? 'bold' : ''}"><span>${esc(l)}</span><span>${v}</span></div>`;
    html += card(label('How the budget is made') +
      line('Income received', money(s.income)) +
      (s.salaryExpected > 0 ? line('Salary still coming', money(s.salaryExpected)) : '') +
      (s.setAside > 0 ? line('Set aside (yearly bills, savings)', '−' + money(s.setAside)) : '') +
      line('Spent so far', '−' + money(b.spent)) +
      line('Still to pay this month', '−' + money(b.committed)) +
      line('Left', money(b.left), true));
  }
  return html + '<div class="spacer"></div>' + fab('Add expense', 'open', 'data-r="add"');
}

function dayBars(s) {
  const days = s.days.slice(-31);
  const max = Math.max(...days.map((d) => d[1]), s.dayReference, 1);
  return `<div class="days">${days.map(([d, v]) => {
    const lv = v <= 0 ? 'empty' : C.level(v, s.dayReference);
    const h = v <= 0 ? 4 : Math.max(4, (v / max) * 64);
    return `<i class="${lv}" style="height:${h}px" title="${C.fmtDay(d)}: ${money(v)}"></i>`;
  }).join('')}</div>`;
}

/** Type a sentence ("koshary 60", "yesterday taxi 85") or tap a repeated expense to log it in one step. */
function quickCard() {
  const sug = R.suggestions();
  const saved = ui.quickSaved && ui.quickSaved.until > Date.now() ? ui.quickSaved : null;
  return card(`${label('Quick add')}
    <div class="row-flex"><div class="input-wrap grow"><input id="qa" value="${esc(ui.quickText)}" placeholder="koshary 60  or  yesterday taxi 85" autocomplete="off" enterkeyhint="done"></div>
      <button class="btn filled" data-act="qa-add">Add</button></div>
    ${ui.quickHint ? `<div class="small bad-t">Type what you spent, with the amount</div>` : ''}
    ${saved ? `<div class="row-flex"><b class="grow good-t">✓ Saved ${esc(saved.text)}</b><button class="btn text" data-act="qa-undo">Undo</button></div>` : ''}
    ${sug.length && !saved ? `<div class="small">One tap to add again</div><div class="chips">${sug.map((g, i) => `<button class="chip" data-act="qa-chip" data-i="${i}">${esc(g.note)} ${C.fmt(g.amount)}</button>`).join('')}</div>` : ''}`);
}
function quickAdd(note, amount, daysAgo) {
  const t = R.quickExpense(note, amount, daysAgo);
  const cat = R.byId(R.data.categories, t.categoryId)?.name ?? 'No category';
  const saved = { id: t.id, text: `${C.fmt(amount)} · ${cat}`, until: Date.now() + 7000 };
  ui.quickSaved = saved; ui.quickHint = false;
  setTimeout(() => { if (ui.quickSaved === saved) { ui.quickSaved = null; if (cur().r === 'home') render(); } }, 7000);
  try { navigator.vibrate && navigator.vibrate(15); } catch { /* no haptics */ }
  render();
  checkOverBudget();
}
function quickSubmit() {
  const q = C.parseQuickDay(ui.quickText);
  if (!(q.amount > 0)) { ui.quickHint = true; render(); return; }
  ui.quickText = '';
  quickAdd(q.note, q.amount, q.daysAgo);
}

// ----- Converter: money and gold, with today's rates -----

function convUnits() {
  const s = R.S();
  const out = [[base(), base(), 1]];
  const pref = ['USD', 'EUR', 'SAR', 'AED', 'KWD', 'GBP', 'QAR'];
  const codes = [...new Set([...pref, ...s.autoCurrencies, ...Object.keys(s.rates).sort(), ...Object.keys(s.onlineRates).sort()])]
    .filter((c) => c !== base() && (s.rates[c] || (s.autoRates && s.onlineRates[c])));
  for (const c of codes) out.push([c, c, R.rateOf(c)]);
  for (const k of ['24', '21', '18']) if (s.gold[k]) out.push([`GOLD${k}`, `Gold ${k}k (gram)`, s.gold[k]]);
  return out;
}
function fmtResult(v) {
  if (!v) return '0';
  if (v < 0.01) return v.toFixed(6).replace(/0+$/, '').replace(/\.$/, '');
  if (v < 10) return v.toFixed(4).replace(/0+$/, '').replace(/\.$/, '');
  return C.fmt(v);
}
function convCompute() {
  const all = convUnits();
  const c = ui.conv;
  const from = all.find((u) => u[0] === c.from) || all.find((u) => u[0] === 'USD') || all[1] || all[0];
  const to = all.find((u) => u[0] === c.to) || all[0];
  const a = C.parseNumber(c.amount) || 0;
  return { all, from, to, result: to[2] > 0 ? a * from[2] / to[2] : 0, one: to[2] > 0 ? from[2] / to[2] : 0 };
}
function ratesStrip() {
  const s = R.S();
  const usd = s.rates.USD || s.onlineRates.USD, g21 = s.gold['21'];
  if (!usd && !g21) return '';
  const chip = (name, value, change) => {
    let ch = '';
    if (change != null) {
      const pct = Math.abs(change) * 100;
      ch = pct < 0.05 ? `<span class="muted">＝</span>` : change > 0 ? `<span class="good-t">▲ ${pct.toFixed(1)}%</span>` : `<span class="bad-t">▼ ${pct.toFixed(1)}%</span>`;
    }
    return `<div class="rate"><div class="small">${name}</div><div class="row-flex"><b class="mid">${C.fmt(value)}</b><span class="small">${ch}</span></div>${change != null ? `<div class="small tiny">vs yesterday</div>` : ''}</div>`;
  };
  return `<div class="rates">${usd ? chip('Dollar', usd, s.rates.USD ? null : R.rateChange('USD')) : ''}${g21 ? chip('Gold 21k', g21, R.goldChange('21')) : ''}</div>`;
}
function converterCard() {
  const { all, from, to, result, one } = convCompute();
  const sel = (id, cur) => `<select id="${id}" class="select">${all.map((u) => `<option value="${esc(u[0])}" ${u[0] === cur[0] ? 'selected' : ''}>${esc(u[1])}</option>`).join('')}</select>`;
  let html = `<div class="semi">Convert money and gold</div>` + ratesStrip();
  if (all.length < 2) return card(html + `<div class="small">Turn on today's rates in More → Currencies & gold, or add your own rate, to convert.</div>`);
  html += `<div class="two"><label class="field"><span>Amount</span><div class="input-wrap"><input id="conv-amt" inputmode="decimal" value="${esc(ui.conv.amount)}" autocomplete="off"></div></label>
      <label class="field"><span>From</span>${sel('conv-from', from)}</label></div>
    <div class="swap"><button class="btn tonal" data-act="conv-swap" aria-label="Swap">⇅</button></div>
    <div class="two"><div><div class="small">Result</div><div class="big" id="conv-out">${fmtResult(result)}</div></div><label class="field"><span>To</span>${sel('conv-to', to)}</label></div>
    <div class="small" id="conv-one">1 ${esc(from[1])} = ${fmtResult(one)} ${esc(to[1])}</div>
    ${R.S().ratesUpdated ? `<div class="small">Rates of ${esc(String(R.S().ratesUpdated).slice(0, 10))}</div>` : ''}`;
  return card(html);
}
function convRefresh() {
  const { from, to, result, one } = convCompute();
  const o = $('#conv-out'), n = $('#conv-one');
  if (o) o.textContent = fmtResult(result);
  if (n) n.textContent = `1 ${from[1]} = ${fmtResult(one)} ${to[1]}`;
}

/** On Friday and Saturday: how the last 7 days went, against the 7 before. */
function weekCard(s) {
  const wd = C.ymd(s.today).wd;
  if (wd !== 5 && wd !== 6) return '';
  const d = R.week(s.today, s.dayReference);
  if (d.spent <= 0) return '';
  let tag = '';
  if (d.previous > 0) {
    const pct = Math.trunc((d.spent - d.previous) / d.previous * 100);
    tag = pct > 0 ? pill(`▲ ${pct}% vs last week`, 'bad') : pct < 0 ? pill(`▼ ${-pct}% vs last week`, 'good') : pill('＝ vs last week');
  }
  const pct = d.previous > 0 ? Math.trunc((d.spent - d.previous) / d.previous * 100) : null;
  const [first, second] = C.weekSentences(d.spent, pct, d.topCategory, d.daysUnder, base());
  const wk = weekKey(s.today), saved = R.S().weeklyAi || {};
  const ai = saved.week === wk ? saved.text : '';
  if (!ai) maybeWeeklyAi(s, d, wk);
  const b = s.budget;
  const body = ai ? `<div class="pre">${esc(ai)}</div>`
    : ui.weeklyBusy ? `<div class="small">Miqrish is writing your summary...</div>`
    : `<div>${esc(first)}</div><div>${esc(second)}</div><div class="tipline">💡 ${esc(C.weeklyTip(d.spent, d.previous, d.topCategory, b.flexLeft, b.daysLeft))}</div>` +
      (AI.hasKey() ? '' : `<div class="small">Add your own key in Miqrish to get this written for you every Friday.</div>`);
  return card(`${label(ai ? 'Your week · from Miqrish' : 'Your week')}<div class="row-flex"><div class="big grow">${money(d.spent)} ${esc(base())}</div>${tag}</div>${body}`, 'tonal');
}
const weekKey = (today) => today - ((C.ymd(today).wd - 5 + 7) % 7);
/** Friday summary written by the user's own AI key, once per week. Without a key the card keeps the plain numbers. */
function maybeWeeklyAi(s, d, wk) {
  if (!AI.hasKey() || R.S().weeklyAiOn === false || ui.weeklyBusy || ui.weeklyFailed === wk) return;
  ui.weeklyBusy = true;
  AI.weeklySummary({ currency: base(), spent: d.spent, previous: d.previous, topCategory: d.topCategory, topAmount: d.topAmount, flexLeft: s.budget.flexLeft, flexTotal: s.budget.flexTotal }, R.S().lang === 'AR')
    .then((text) => { if (text) { R.S().weeklyAi = { week: wk, text }; R.changed(); } else ui.weeklyFailed = wk; })
    .catch(() => { ui.weeklyFailed = wk; })
    .finally(() => { ui.weeklyBusy = false; if (cur().r === 'home') render(); });
}

/** The saving challenge: days in a row where the last 7 days stayed under the weekly share of the flexible money. */
function streakCard(s) {
  const k = s.streak;
  if (!k) return '';
  const frac = Math.min(Math.max(k.weekSpent / k.target, 0), 1);
  const head = k.days > 0 ? `🔥 ${k.days} ${k.days === 1 ? 'day' : 'days'} in a row` : 'Saving challenge';
  const sub = k.days > 0 ? `<span>Every day the last 7 days stayed under</span> <b>${money(k.target)} ${esc(base())}</b>` : `<span>Keep the last 7 days under</span> <b>${money(k.target)} ${esc(base())}</b> <span>to start a streak.</span>`;
  return card(`<div class="row-flex"><b class="grow">${head}</b>${k.best > k.days ? `<span class="small">best ${k.best}</span>` : ''}</div>
    <div class="small">${sub}</div>${progress(frac, k.weekSpent > k.target ? 'over' : '')}
    <div class="small">${k.weekLeft >= 0 ? `<b>${money(k.weekLeft)} ${esc(base())}</b> <span>left for this week</span>` : `<b>${money(-k.weekLeft)} ${esc(base())}</b> <span>over this week</span>`}</div>`);
}

/** A ring that fills as you save towards one goal (Umra, a car, a wedding...). */
function goalCard() {
  const s = R.S();
  if (!(s.goalAmount > 0)) return '';
  const frac = Math.max(0, Math.min(1, s.goalSaved / s.goalAmount));
  const r = 36, c = 2 * Math.PI * r;
  return card(`<div class="row-flex gap16"><div class="goal"><svg viewBox="0 0 84 84" width="84" height="84"><circle cx="42" cy="42" r="${r}" class="gt"/>
      <circle cx="42" cy="42" r="${r}" class="gr" style="stroke:${frac >= 1 ? 'var(--good)' : 'var(--primary)'}" stroke-dasharray="${frac * c} ${c}" transform="rotate(-90 42 42)"/></svg><b>${Math.floor(frac * 100)}%</b></div>
    <div class="grow">${label('Savings goal')}<div class="mid">${esc(s.goalName || 'Savings goal')}</div>
      <div class="small">${frac >= 1 ? 'Goal reached!' : `Saved ${money(s.goalSaved)} of ${money(s.goalAmount)}`}</div>
      <button class="btn text" data-act="goal-add">Add to goal</button></div></div>`);
}
/** A category went over its monthly limit. Nothing is shown while every category is under its limit. */
function limitCard(s) {
  const st = R.S();
  if (st.limitAlert === false || st.limitDismissed === String(s.cycle.start)) return '';
  const over = R.categoriesOverLimit(s.today);
  if (!over.length) return '';
  return card(`${label('Over the limit')}${over.map((o) => `<div class="row-flex line"><div class="grow"><b>${esc(o.name)}</b></div><span class="bad-t">${money(o.spent)} of ${money(o.limit)} ${esc(base())}</span></div>`).join('')}
    <div class="btns"><button class="btn text" data-act="limit-skip">OK</button></div>`, 'tonal');
}
/** Last 2 days of the month: offers to put what is left into the piggy bank. */
function monthEndCard(s) {
  const st = R.S(), b = s.budget;
  if (st.monthEndReminder === false || b.daysLeft > 2 || st.monthEndDismissed === C.isoOf(s.cycle.end)) return '';
  if (!(b.left > 0)) return '';
  const goalAmt = C.suggestMove(b.left, st.goalAmount, st.goalSaved);
  ui.monthEnd = { left: b.left, goalAmt, key: C.isoOf(s.cycle.end) };
  return card(`<div class="grow"><b>${b.daysLeft > 1 ? 'The month ends tomorrow. Left:' : 'The month ends today. Left:'} ${money(b.left)} ${esc(base())}</b>
    <div class="small">Put it in your piggy bank?</div></div>
    <div class="btns"><button class="btn filled" data-act="month-piggy">Put in piggy bank</button>${goalAmt > 0 ? `<button class="btn tonal" data-act="month-move">Move to goal</button>` : ''}<button class="btn text" data-act="month-skip">Not now</button></div>`, 'tonal');
}
function goalSheet() {
  sheet({
    title: 'Add to goal', body: numField('amount', 'Amount', null, { suffix: base() }),
    onSave: (root) => { const v = num(root, 'amount'); if (v > 0) R.addToMainGoal(v); },
  });
}

// ---------- Piggy bank and goals ----------

function piggySheet(dir) {
  const total = C.piggyTotal(R.S().piggy || []);
  sheet({
    title: dir > 0 ? 'Put in' : 'Take out',
    body: numField('amount', 'Amount', null, { suffix: base() }) + (dir < 0 ? `<div class="small">In your piggy bank: ${money(total)} ${esc(base())}</div>` : ''),
    onSave: (root) => { let v = num(root, 'amount'); if (dir < 0) v = Math.min(v, total); if (v > 0) R.piggyAdd(dir * v); },
  });
}
function piggyTargetSheet() {
  sheet({
    title: 'Monthly target',
    body: numField('amount', 'Amount each month (0 to remove)', R.S().piggyTarget || null, { suffix: base() }),
    onSave: (root) => R.setSetting('piggyTarget', Math.max(0, num(root, 'amount'))),
  });
}
function piggyPage() {
  const s = R.S(), hist = s.piggy || [], total = C.piggyTotal(hist), months = C.piggyMonths(hist, C.todayEpoch());
  const avg = C.piggyAverage(months);
  const hero = card(`${label('In your piggy bank')}<div class="huge">${money(total)} ${esc(base())}</div>
    ${avg != null ? `<div class="small">Saved over ${months.length} months, about ${money(avg)} a month</div>` : ''}
    <div class="btns"><button class="btn filled" data-act="piggy-in">Put in</button><button class="btn tonal" data-act="piggy-out" ${total > 0 ? '' : 'disabled'}>Take out</button></div>`, 'primary');
  let body = hero;
  const cover = C.coverMonths(total, R.monthlySpend());
  if (cover != null) body += card(`${label('How long it lasts')}<div class="mid">Your piggy bank covers ${cover} months of your spending</div><div class="small">You spend about ${money(R.monthlySpend())} a month</div>`);
  const target = s.piggyTarget || 0;
  if (!(target > 0)) {
    body += card(`${label('Monthly target')}<div class="small">Pick an amount to put in every month and see how many months in a row you hit it.</div><div class="btns"><button class="btn filled" data-act="piggy-target">Set a monthly target</button></div>`);
  } else {
    const got = C.piggyThisMonth(months, C.todayEpoch()), hit = got >= target, run = C.piggyStreak(months, target, C.todayEpoch()) + (hit ? 1 : 0);
    body += card(`${label('Monthly target')}<div><b>This month: ${money(got)} of ${money(target)} ${esc(base())}</b></div>
      <div class="bar"><i style="width:${Math.min(100, got / target * 100)}%${hit ? ';background:var(--good)' : ''}"></i></div>
      <div class="${hit ? 'good-t' : 'small'}">${hit ? "You hit this month's target" : `Still ${money(target - got)} to go this month`}</div>
      <div><b>${run === 0 ? 'No streak yet. Hit the target this month to start one.' : run === 1 ? '1 month in a row' : `${run} months in a row`}</b></div>
      <div class="btns"><button class="btn text" data-act="piggy-target">Change target</button></div>`);
  }
  if (!months.length) body += card(`<div class="small">Nothing in your piggy bank yet. Tap Put in when you set money aside.</div>`);
  else {
    const shown = months.slice(-6), top = Math.max(1, ...shown.map((m) => m.saved));
    body += card(`${label('Saved each month')}<div class="bars">${shown.map((m, i) => `<div class="barcol"><span class="small">${money(m.saved)}</span>
      <i class="${i === shown.length - 1 ? 'last' : ''}" style="height:${Math.max(3, Math.round(96 * Math.max(0, m.saved) / top))}px"></i><span class="small">${esc(C.fmtMonthName(m.y, m.m).slice(0, 3))}</span></div>`).join('')}</div>`);
    body += card(label('Month by month') + [...months].reverse().map((m, i) =>
      `<div class="pline"><span><b>${esc(C.fmtMonthName(m.y, m.m))}</b>${i === 0 ? `<div class="small">so far</div>` : ''}</span>
       <span style="text-align:end"><b class="${m.saved < 0 ? 'bad-t' : 'good-t'}">${m.saved > 0 ? '+' : m.saved < 0 ? '−' : ''}${money(Math.abs(m.saved))}</b><div class="small">Total ${money(m.total)}</div></span></div>`).join(''));
  }
  return topbar('Piggy bank') + `<main class="page">${body}</main>`;
}
function allGoals() {
  const s = R.S();
  const main = s.goalAmount > 0 ? [{ id: 0, name: s.goalName || 'Savings goal', target: s.goalAmount, saved: s.goalSaved || 0, monthly: s.savingsPerMonth || 0 }] : [];
  return [...main, ...(s.goals || [])];
}
function goalsPage() {
  const s = R.S(), goals = allGoals(), sorted = C.goalsByFinish(goals), today = C.todayEpoch();
  const firstId = (sorted.find((g) => !C.goalReached(g) && C.goalMonthsLeft(g) != null) || {}).id;
  const perMonth = goals.reduce((a, g) => a + (g.monthly || 0), 0);
  const r = 26, c = 2 * Math.PI * r;
  let body = card(`<div class="row-flex"><div class="grow">${label('Piggy bank')}<div class="mid">${money(C.piggyTotal(s.piggy || []))} ${esc(base())}</div></div><span class="chev">›</span></div>`, 'tonal tap', 'data-act="open" data-r="piggyPage"');
  if (!goals.length) body += card(`<div class="small">No goals yet. Add one (Umra, a car, a laptop) and see when you will reach it.</div><div class="btns"><button class="btn filled" data-act="goal-new">Add a goal</button></div>`);
  else {
    body += `<div class="small">${goals.length === 1 ? `1 goal. You put ${money(perMonth)} a month towards it.` : `${goals.length} goals. You put ${money(perMonth)} a month towards them.`}</div>`;
    body += sorted.map((g) => {
      const f = C.goalFraction(g), done = C.goalReached(g), m = C.goalMonthsLeft(g), sooner = C.monthsSoonerWith(g, 1000);
      return card(`<div class="row-flex gap16" data-act="goal-edit" data-id="${g.id}"><div class="goal" style="width:72px;height:72px"><svg viewBox="0 0 72 72" width="72" height="72"><circle cx="36" cy="36" r="${r}" class="gt" style="stroke-width:9"/>
        <circle cx="36" cy="36" r="${r}" class="gr" style="stroke:${done ? 'var(--good)' : 'var(--primary)'};stroke-width:9" stroke-dasharray="${f * c} ${c}" transform="rotate(-90 36 36)"/></svg><b style="font-size:15px">${Math.floor(f * 100)}%</b></div>
        <div class="grow">${g.id === firstId && goals.length > 1 ? `<div class="label good-t">Finishes first</div>` : ''}<div class="mid">${esc(g.name)}</div>
        <div class="small">${money(g.saved)} of ${money(g.target)} ${esc(base())}</div>
        ${done ? `<div class="good-t b">Goal reached!</div>` : `${g.monthly > 0 ? `<div class="small">${money(g.monthly)} a month</div>` : ''}
          ${m != null ? `<div class="b">Done by ${esc(C.fmtMonthYear(C.goalDateAfter(today, m)))}</div>` : `<div class="small">No date yet. Put an amount towards it each month.</div>`}
          ${sooner != null && sooner >= 6 ? `<div class="small">Add 1,000 a month and it moves up ${sooner} months</div>` : ''}`}</div></div>
        ${done ? '' : `<div class="btns"><button class="btn text" data-act="goal-addto" data-id="${g.id}">Add to goal</button></div>`}`, g.id === firstId && goals.length > 1 ? 'primary' : '');
    }).join('');
  }
  return topbar('Goals', `<button class="btn text" data-act="goal-new">Add a goal</button>`) + `<main class="page">${body}</main>`;
}
function goalAddSheet(id) {
  sheet({
    title: 'Add to goal', body: numField('amount', 'Amount', null, { suffix: base() }) + `<div class="small">Also goes into your piggy bank</div>`,
    onSave: (root) => { const v = num(root, 'amount'); if (v > 0) { if (id === 0) R.addToMainGoal(v); else R.addToGoal(id, v); } },
  });
}
function goalEditSheet(id) {
  const s = R.S(), g = id == null ? null : allGoals().find((x) => x.id === id);
  sheet({
    title: g ? 'Edit goal' : 'New goal',
    body: field('gName', 'Goal name', g ? (id === 0 ? s.goalName : g.name) : '') + numField('gTarget', 'Goal amount', g?.target, { suffix: base() }) +
      numField('gSaved', 'Saved so far', g?.saved, { suffix: base() }) + numField('gMonthly', 'Put towards it each month', g?.monthly, { suffix: base() }),
    onDelete: g ? () => { if (id === 0) { s.goalName = ''; s.goalAmount = 0; s.goalSaved = 0; R.changed(); } else R.deleteGoal(id); } : null,
    deleteLabel: 'Delete this goal',
    onSave: (root) => {
      const target = num(root, 'gTarget'), saved = num(root, 'gSaved') || 0, monthly = num(root, 'gMonthly') || 0, name = val(root, 'gName').trim();
      if (!(target > 0)) return 'Type the goal amount';
      if (id === 0) { s.goalName = name; s.goalAmount = target; s.goalSaved = saved; s.savingsPerMonth = monthly; R.changed(); }
      else R.saveGoal({ id: g ? id : 0, name: name || 'Savings goal', target, saved, monthly });
    },
  });
}

/** First-run guide: three steps that tell a new user what to do. It hides itself when done. */
function startGuide(s) {
  const hasIncome = s.budget.budget > 0 || s.salaryExpected > 0;
  const hasExpense = s.days.some((d) => d[1] > 0);
  const hasCommit = s.upcoming.length > 0;
  if (ui.hideGuide || (hasIncome && hasExpense && hasCommit)) return '';
  const step = (n, title, hint, done, r) => `<div class="step tap" data-act="open" data-r="${r}"><span class="dot ${done ? 'done' : ''}">${done ? '✓' : n}</span><div class="grow"><b>${title}</b><div class="small">${hint}</div></div></div>`;
  return card(`<div class="row-flex"><b class="mid grow">Start here</b><button class="btn text" data-act="hide-guide">Hide</button></div>
    <div class="small">Three quick steps and Pocketta works for you.</div>
    ${step(1, 'Put in your salary', 'So it can tell you what you can spend each day', hasIncome, 'income')}
    ${step(2, 'Add what you spent today', 'Tap Add expense, type the amount, pick a category', hasExpense, 'add')}
    ${step(3, "Add a bill, installment or gam'eya", 'Pocketta reminds you and counts it in your budget', hasCommit, 'commit')}`, 'primary-c');
}

// ---------- Spend ----------

function spendData() {
  const s = R.S();
  let cycle = C.cycleFor(C.todayEpoch(), s.cycleStart);
  for (let i = 0; i < ui.spendOffset; i++) cycle = C.previousCycle(cycle, s.cycleStart);
  const txns = R.data.txns.filter((t) => cycle.contains(t.epochDay));
  return { cycle, txns };
}
const topOf = (id) => { const c = R.byId(R.data.categories, id); return c ? (c.parentId ?? c.id) : null; };

function spend() {
  const { cycle, txns } = spendData();
  const s = R.S();
  const prev = C.previousCycle(cycle, s.cycleStart);
  const prevExp = R.data.txns.filter((t) => prev.contains(t.epochDay) && t.type === 'EXPENSE');
  const exp = txns.filter((t) => t.type === 'EXPENSE');
  const spent = exp.reduce((a, t) => a + R.base(t), 0);
  const income = txns.filter((t) => t.type === 'INCOME').reduce((a, t) => a + R.base(t), 0);
  const per = {}, prevPer = {};
  for (const t of exp) { const k = topOf(t.categoryId) ?? 'none'; per[k] = (per[k] || 0) + R.base(t); }
  for (const t of prevExp) { const k = topOf(t.categoryId) ?? 'none'; prevPer[k] = (prevPer[k] || 0) + R.base(t); }
  const perList = Object.entries(per).sort((a, b) => b[1] - a[1]);
  const nameOf = (k) => (k === 'none' ? null : R.byId(R.data.categories, +k)?.name) ?? 'No category';
  const limitOf = (k) => (k === 'none' ? 0 : R.byId(R.data.categories, +k)?.monthlyLimit || 0);
  ui.shareData = { month: `${C.fmtMonth(cycle.end)} ${C.ymd(cycle.end).y}`, currency: base(), spent, income, previousSpent: prevExp.reduce((a, t) => a + R.base(t), 0), categories: perList.map(([k, v]) => [nameOf(k), v]) };
  let html = `<div class="month-nav"><button class="icon-btn" data-act="spend-prev" aria-label="Previous month">${icon('left')}</button>
    <div class="center"><div class="mid">${C.fmtMonth(cycle.end)} ${C.ymd(cycle.end).y}</div><div class="small">${C.fmtDay(cycle.start)} → ${C.fmtDay(cycle.end)}</div></div>
    <button class="icon-btn" data-act="spend-next" ${ui.spendOffset === 0 ? 'disabled' : ''} aria-label="Next month">${icon('right')}</button>
    ${spent > 0 ? `<button class="btn text" data-act="share-month">Share</button>` : ''}</div>
    <div class="two">${card(label('Spent') + `<div class="big">${money(spent)}</div>`)}${card(label('Income') + `<div class="big good-t">${money(income)}</div>`)}</div>`;
  if (perList.length) {
    html += donutCard(perList.map(([k, v]) => [nameOf(k), v, limitOf(k)]), spent);
    const sums = {};
    for (const t of exp) sums[t.epochDay] = (sums[t.epochDay] || 0) + R.base(t);
    const vals = []; for (let d = cycle.start; d < cycle.nextStart; d++) vals.push(sums[d] || 0);
    html += daysCard(vals);
    html += compareCard(perList.slice(0, 5).map(([k, v]) => [nameOf(k), v, prevPer[k] || 0]));
    html += trendCard(cycle, spent);
    html += card(label('By category · tap to filter') + perList.map(([k, total]) => {
      const limit = limitOf(k);
      const sel = String(ui.spendFilter) === String(k);
      const frac = limit > 0 ? total / limit : spent > 0 ? total / spent : 0;
      const lv = C.level(total, limit);
      return `<div class="catline tap" data-act="spend-filter" data-k="${k}"><div class="row-flex"><span class="grow ${sel ? 'sel' : ''}">${esc(nameOf(k))}</span>
        <b>${limit > 0 ? `${money(total)} / ${money(limit)}` : `${money(total)} · ${spent > 0 ? Math.floor(total / spent * 100) : 0}%`}</b></div>${progress(frac, lv === 'ok' ? '' : lv)}</div>`;
    }).join(''));
  }
  html += `<div class="pad"><input class="search" id="spend-search" type="search" placeholder="Search notes and categories" value="${esc(ui.spendSearch)}"></div>
    <div id="spend-list">${spendList(txns)}</div><div class="spacer"></div>` + fab('Add expense', 'open', 'data-r="add"');
  return html;
}

/** A coloured ring that shows at a glance which categories take the biggest share. */
function donutCard(items, total) {
  if (total <= 0) return '';
  const shown = items.slice(0, 7);
  if (items.length > 7) shown.push(['Other', items.slice(7).reduce((a, i) => a + i[1], 0), 0]);
  const r = 62, c = 2 * Math.PI * r;
  let off = 0;
  const arcs = shown.map(([, v], i) => {
    const full = v / total * c;
    const len = Math.max(full - 2, 0.8);
    const el = `<circle cx="75" cy="75" r="${r}" fill="none" stroke="${CHART_COLORS[i % CHART_COLORS.length]}" stroke-width="26" stroke-dasharray="${len} ${c}" stroke-dashoffset="${-off}" transform="rotate(-90 75 75)"/>`;
    off += full;
    return el;
  }).join('');
  const legend = shown.map(([name, v, limit], i) => `<div class="lg"><div class="row-flex"><i class="dot-c" style="background:${CHART_COLORS[i % CHART_COLORS.length]}"></i><span class="grow ellipsis">${esc(name)}</span><b>${Math.floor(v / total * 100)}%</b></div>
    ${limit > 0 ? `${progress(v / limit, C.level(v, limit) === 'ok' ? '' : C.level(v, limit))}<div class="small tiny">${money(v)} / ${money(limit)}</div>` : ''}</div>`).join('');
  return card(`${label('Where it went')}<div class="row-flex gap16"><div class="donut"><svg viewBox="0 0 150 150" width="150" height="150">${arcs}</svg><span>Spent</span></div><div class="grow legend-col">${legend}</div></div>`);
}

/** One bar per day of the month: tall bars are the expensive days. */
function daysCard(values) {
  if (!values.some((v) => v > 0)) return '';
  const max = Math.max(...values);
  return card(`${label('Which days cost the most')}<div class="days tall">${values.map((v) =>
    `<i class="${v <= 0 ? 'empty' : v >= max * 0.85 ? 'over' : ''}" style="height:${v <= 0 ? 4 : Math.max(4, v / max * 90)}px"></i>`).join('')}</div>`);
}

/** The last 6 months as bars, with how this month compares to the one before. */
function trendCard(cycle, spent) {
  const s = R.S();
  const cycles = [cycle];
  for (let i = 0; i < 5; i++) cycles.unshift(C.previousCycle(cycles[0], s.cycleStart));
  const totals = cycles.map((c) => R.data.txns.filter((t) => t.type === 'EXPENSE' && !t.spread && c.contains(t.epochDay)).reduce((a, t) => a + R.base(t), 0));
  const prev = totals[totals.length - 2];
  if (totals.filter((v) => v > 0).length < 2) return '';
  const max = Math.max(...totals, 1);
  let pillHtml = '';
  if (prev > 0) {
    const pct = Math.round((spent - prev) / prev * 100);
    pillHtml = pct > 0 ? pill(`▲ ${pct}% vs last month`, 'bad') : pct < 0 ? pill(`▼ ${-pct}% vs last month`, 'good') : pill('＝ same as last month');
  }
  const bars = totals.map((v, i) => `<div class="tcol"><small>${v > 0 ? money(v) : ''}</small><i class="${i === totals.length - 1 ? 'now' : ''}" style="height:${v > 0 ? Math.max(4, v / max * 84) : 3}px"></i><span>${esc(C.fmtMonth(cycles[i].end).slice(0, 3))}</span></div>`).join('');
  return card(`<div class="row-flex"><div class="label grow">Last 6 months</div>${pillHtml}</div><div class="trend">${bars}</div>`);
}

/** This month against last month for the biggest categories: two bars each, longer bar = more money. */
function compareCard(items) {
  if (!items.length || items.every((i) => i[2] <= 0)) return '';
  const max = Math.max(1, ...items.map((i) => Math.max(i[1], i[2])));
  return card(`${label('This month vs last month')}
    <div class="legend"><span><i style="background:var(--primary)"></i>This month</span><span><i style="background:var(--outline)"></i>Last month</span></div>` +
    items.map(([name, now, before]) => `<div class="cmp"><b>${esc(name)}</b>${progress(now / max, before > 0 && now > before * 1.2 ? 'over' : '')}<div class="bar grey"><i style="width:${before / max * 100}%"></i></div></div>`).join(''));
}

function spendList(txns) {
  const q = ui.spendSearch.trim().toLowerCase();
  const shown = txns.filter((t) => (ui.spendFilter == null || String(topOf(t.categoryId) ?? 'none') === String(ui.spendFilter)) &&
    (!q || t.note.toLowerCase().includes(q) || R.categoryLabel(t.categoryId).toLowerCase().includes(q)));
  if (!shown.length) return `<p class="empty">Nothing here yet.</p>`;
  const groups = {};
  for (const t of shown) (groups[t.epochDay] ||= []).push(t);
  const today = C.todayEpoch();
  return Object.keys(groups).map(Number).sort((a, b) => b - a).map((d) => {
    const list = groups[d].sort((a, b) => b.createdAt - a.createdAt);
    const dayTotal = list.filter((t) => t.type === 'EXPENSE').reduce((a, t) => a + R.base(t), 0);
    const title = d === today ? 'Today' : d === today - 1 ? 'Yesterday' : C.fmtWeekday(d);
    return `<div class="day-h">${title} · ${money(dayTotal)}</div>` + list.map(txnRow).join('');
  }).join('');
}

function txnRow(t) {
  const cat = R.byId(R.data.categories, t.categoryId);
  const title = t.note || cat?.name || (t.type === 'TRANSFER' ? 'Transfer' : '—');
  const tagText = R.tagsOf(t).map((g) => `#${g}`).join(' ');
  const sub = t.type === 'TRANSFER' ? 'Transfer' : R.categoryLabel(t.categoryId) + (tagText ? ` · ${tagText}` : '');
  const sign = t.type === 'INCOME' ? '+' : t.type === 'EXPENSE' ? '−' : '';
  const cls = t.type === 'INCOME' ? 'good-t' : t.type === 'TRANSFER' ? 'muted' : '';
  return row(title, sub, { icon: cat?.name ?? title, act: 'edit-txn', data: `data-id="${t.id}"`, trailing: `<b class="${cls}">${sign}${C.fmt(t.amount)}${t.currency !== base() ? ' ' + esc(t.currency) : ''}</b>` });
}

// ---------- Tags (projects) ----------

function tagsPage() {
  const rows = R.tagTotals();
  const max = Math.max(1, ...rows.map((r) => r.spent));
  return topbar('Tags') + `<main class="page">` +
    `<p class="small pad">Add a tag to any spending or income (like a wedding or a trip) and see its total here, across all categories.</p>` +
    (rows.length ? rows.map((r) => `<section class="card tap" data-act="open-tag" data-name="${esc(r.name)}"><div class="row-flex"><b class="grow">#${esc(r.name)}</b><b>${money(r.spent)} ${esc(base())}</b></div>
      ${progress(r.spent / max)}<div class="small">${r.count} ${r.count === 1 ? 'record' : 'records'}${r.earned > 0 ? ` · received ${money(r.earned)}` : ''}</div></section>`).join('')
      : `<p class="empty">No tags yet. When you add an expense, write a tag in the Tags box.</p>`) + `</main>`;
}

function tagDetail(c) {
  const name = c.name;
  const t = R.tagTotals().find((x) => x.name === name);
  const txns = R.data.txns.filter((x) => R.tagsOf(x).includes(name)).sort((a, b) => b.epochDay - a.epochDay);
  if (!t) return topbar(`#${name}`) + `<main class="page"><p class="empty">Nothing with this tag.</p></main>`;
  const cats = Object.entries(t.cats).sort((a, b) => b[1] - a[1]);
  return topbar(`#${name}`, `<button class="icon-btn" data-act="tag-menu" data-name="${esc(name)}" aria-label="Rename or remove">${icon('settings')}</button>`) + `<main class="page">` +
    card(`${label('Spent on this tag')}<div class="big">${money(t.spent)} ${esc(base())}</div>${t.earned > 0 ? `<div class="small">Received ${money(t.earned)} ${esc(base())}</div>` : ''}`, 'primary') +
    (cats.length ? card(label('Where it went') + `<div class="seg3">${cats.map(([, v], i) => `<i style="flex:${v};background:${CHART_COLORS[i % CHART_COLORS.length]}"></i>`).join('')}</div>` +
      cats.map(([k, v], i) => `<div class="row-flex lg"><i class="dot-c" style="background:${CHART_COLORS[i % CHART_COLORS.length]}"></i><span class="grow">${esc(k)}</span><b>${money(v)}</b><span class="small">${Math.round(v / t.spent * 100)}%</span></div>`).join('')) : '') +
    label('Records') + txns.map(txnRow).join('') + `</main>`;
}

function tagMenu(name) {
  const { el, close } = sheet({
    title: `#${name}`, body: field('newname', 'Rename tag', name),
    saveLabel: 'Rename', onSave: (root) => { const n = val(root, 'newname').trim().replace(/^#/, ''); if (!n) return 'Type a name'; R.renameTag(name, n); ui.stack = [{ r: 'more' }, { r: 'tagsPage' }]; render(); },
    onDelete: () => { R.removeTag(name); ui.stack = [{ r: 'more' }, { r: 'tagsPage' }]; render(); }, deleteLabel: 'Remove tag from everything',
  });
}

// ---------- Mark a whole group as paid ----------

function bulkSheet() {
  const today = C.todayEpoch(), cycle = C.cycleFor(today, R.S().cycleStart);
  let month = ui.bulkMonth || 0;
  const startOf = () => (month === 0 ? today : cycle.nextStart);
  const body = () => {
    const gs = R.groupPayments(startOf());
    return chips('bmonth', [[0, 'This month'], [1, 'Next month']], month, 'seg') +
      (gs.length ? gs.map((g, i) => `<div class="row tap" data-bulk="${i}"><div class="row-main"><div class="row-title"><b>${esc(g.title)}</b></div>
        <div class="row-sub">${g.items.length} ${g.items.length === 1 ? 'item' : 'items'}</div></div><b>${money(g.total)}</b><span class="chev">›</span></div>`).join('')
        : '<p class="empty">Nothing to pay in this month.</p>');
  };
  const { el, close } = sheet({ title: 'Mark a whole group as paid', body: body() });
  const redraw = () => { el.querySelector('.sheet-body').innerHTML = body(); };
  el.addEventListener('click', (e) => {
    const m = e.target.closest('[data-group="bmonth"] .chip');
    if (m) { month = +m.dataset.val; ui.bulkMonth = month; redraw(); return; }
    const r = e.target.closest('[data-bulk]');
    if (!r) return;
    const g = R.groupPayments(startOf())[+r.dataset.bulk];
    if (!g) return;
    sheet({
      title: `${g.title}: mark all as paid`,
      body: `<p>These payments will be recorded as paid now:</p>` + g.items.map((it) => `<div class="pline"><span>${esc(it.name)}${it.n > 1 ? ` × ${it.n}` : ''}</span><b>${money(it.amount)}</b></div>`).join('') +
        `<div class="pline"><span><b>Total</b></span><b>${money(g.total)} ${esc(base())}</b></div><p class="small">You can undo it right after.</p>`,
      saveLabel: 'Mark all as paid',
      onSave: () => {
        const undo = R.payGroup(g);
        ui.bulkUndo = { undo, text: `${g.title}: ${g.items.length} ${g.items.length === 1 ? 'item' : 'items'} paid · ${money(g.total)} ${base()}` };
        close(); render();
      },
    });
  });
}

// ---------- Add / edit a transaction ----------

function add(c) {
  const editing = c.id ? R.byId(R.data.txns, c.id) : null;
  const st = c.st || (c.st = initAdd(c, editing));
  const s = R.S();
  const title = editing ? 'Edit' : st.type === 'INCOME' ? 'Add income' : st.type === 'TRANSFER' ? 'Move money' : 'Add expense';
  return `${topbar(title, editing ? `<button class="icon-btn" data-act="del-txn" aria-label="Delete">${icon('del')}</button>` : '')}
  <main class="page form" id="add">
    ${editing?.source ? `<p class="small">This was recorded from a bill, installment, gam'eya or loan.</p>` : ''}
    ${chips('type', st.type === 'TRANSFER' ? [['TRANSFER', 'Transfer']] : [['EXPENSE', 'Expense'], ['INCOME', 'Income']], st.type, 'seg')}
    <div>${label('Amount')}<div class="amount" id="amt"></div><div class="small" id="qnote"></div></div>
    <label class="field"><span>${st.type === 'EXPENSE' ? 'Amount, or quick: koshary 60' : 'Amount'}</span>
      <div class="input-wrap"><input id="quick" name="quick" value="${esc(st.input)}" autocomplete="off" enterkeyhint="done" inputmode="${st.type === 'EXPENSE' ? 'text' : 'decimal'}"></div></label>
    <div id="cats"></div>
    <div id="borrow"></div>
    ${R.currencies().length > 1 ? label('Currency') + chips('currency', R.currencies().map((x) => [x, x]), st.currency) + `<div class="small" id="conv"></div>` : ''}
    ${label('Date')}${chips('day', [[C.todayEpoch(), 'Today'], [C.todayEpoch() - 1, 'Yesterday']], st.date)}
    ${dateField('date', 'Or pick a day', st.date)}
    ${field('note', 'Note (optional)', st.noteField)}
    ${tagField(st.tags)}
    <button class="btn filled wide" data-act="save-txn">Save</button>
    <div class="spacer"></div>
  </main>`;
}

/** A tag (project) field: type words separated by commas, or tap one you used before. */
function tagField(tags) {
  const known = R.allTags().slice(0, 10).map((g) => g.name);
  return field('tags', 'Tags (optional)', (tags || []).join(', '), { ph: 'wedding, trip' }) +
    (known.length ? `<div class="chips tagpick">${known.map((n) => `<button type="button" class="chip" data-act="tag-pick" data-name="${esc(n)}">#${esc(n)}</button>`).join('')}</div>` : '');
}
const parseTags = (text) => R.normTags(String(text).split(/[,،]/));

function initAdd(c, t) {
  const s = R.S();
  if (t) {
    const cat = R.byId(R.data.categories, t.categoryId);
    return { type: t.type, input: String(t.amount), noteField: t.note, categoryId: t.categoryId, parentId: cat ? (cat.parentId ?? cat.id) : null, byHand: true, walletId: t.walletId, toWalletId: t.toWalletId, currency: t.currency, date: t.epochDay, person: '', due: null, tags: t.tags || [] };
  }
  const st = { type: c.type === 'INCOME' ? 'INCOME' : 'EXPENSE', input: '', noteField: '', categoryId: null, parentId: null, byHand: false, walletId: 0, toWalletId: null, currency: s.baseCurrency, date: c.date ?? C.todayEpoch(), person: '', due: null, tags: [] };
  if (st.type === 'EXPENSE' && s.lastCategory) {
    const cat = R.categories().find((x) => x.id === s.lastCategory);
    if (cat) { st.categoryId = cat.id; st.parentId = cat.parentId ?? cat.id; }
  }
  return st;
}

function addRefresh() {
  const c = cur();
  const st = c.st;
  const root = $('#add');
  if (!root) return;
  const q = C.parseQuick(st.input);
  $('#amt').textContent = `${q.amount != null ? C.fmt(q.amount) : '0'} ${st.currency}`;
  $('#amt').classList.toggle('muted', q.amount == null);
  $('#qnote').textContent = q.note && !st.noteField ? `Note: ${q.note}` : '';
  if (!st.byHand && q.note && st.type !== 'TRANSFER') {
    const g = R.guessCategory(q.note, st.type);
    if (g) { st.categoryId = g; const cat = R.byId(R.data.categories, g); st.parentId = cat.parentId ?? cat.id; }
  }
  if (st.type !== 'TRANSFER') {
    const kind = st.type === 'INCOME' ? 'INCOME' : 'EXPENSE';
    const cats = R.categories().filter((x) => x.kind === kind).sort((a, b) => a.sort - b.sort);
    const parents = cats.filter((x) => x.parentId == null);
    const subs = cats.filter((x) => x.parentId != null && x.parentId === st.parentId);
    $('#cats').innerHTML = label('Category') + chips('parent', parents.map((x) => [x.id, x.name]), st.parentId) +
      (subs.length ? chips('sub', subs.map((x) => [x.id, x.name]), st.categoryId, 'subs') : '');
  } else $('#cats').innerHTML = '';
  const catName = R.byId(R.data.categories, st.categoryId)?.name ?? '';
  const isBorrowed = st.type === 'INCOME' && catName.toLowerCase() === 'borrowed' && !c.id;
  const bw = $('#borrow');
  if (isBorrowed && !bw.dataset.on) {
    bw.dataset.on = '1';
    bw.innerHTML = field('person', 'Borrowed from', st.person) + dateField('due', 'Pay back by (optional)', st.due) + `<p class="small">This also goes to Owe → Loans as money you owe.</p>`;
  } else if (!isBorrowed) { bw.dataset.on = ''; bw.innerHTML = ''; }
  const conv = $('#conv');
  if (conv) conv.textContent = st.currency !== base() && q.amount != null ? `= ${C.fmt(q.amount * R.rateOf(st.currency))} ${base()}` : '';
  st.isBorrowed = isBorrowed;
}

function saveTxn() {
  const c = cur();
  const st = c.st;
  const root = $('#add');
  st.noteField = val(root, 'note');
  if (st.isBorrowed) { st.person = val(root, 'person'); st.due = C.fromIso(val(root, 'due')); }
  const q = C.parseQuick(st.input);
  const amount = q.amount;
  const note = st.noteField.trim() || q.note;
  if (!(amount > 0)) return toast('Type an amount first');
  if (st.type !== 'TRANSFER' && !st.categoryId) return toast('Pick a category');
  if (st.isBorrowed && !st.person.trim()) return toast('Who did you borrow from?');
  if (st.isBorrowed) {
    R.addLoan({ person: st.person.trim(), lent: false, amount: amount * R.rateOf(st.currency), walletId: 0, day: st.date, due: st.due, note });
  } else {
    R.saveTxn({
      ...(c.id ? { id: c.id } : {}), type: st.type, amount, currency: st.currency, rate: R.rateOf(st.currency), walletId: st.walletId || 0,
      toWalletId: st.type === 'TRANSFER' ? st.toWalletId : null, tags: parseTags(val(root, 'tags')), categoryId: st.type === 'TRANSFER' ? null : st.categoryId, note, epochDay: st.date,
    });
    if (st.type === 'EXPENSE' && !c.id) checkOverBudget();
  }
  back();
}

function checkOverBudget() {
  const s = R.S();
  if (!s.budgetAlert) return;
  const h = R.home();
  const b = h.budget;
  if (b.flexLeft >= 0 || b.budget <= 0) return;
  const today = C.isoOf(h.today);
  if (s.overAlert === today) return;
  s.overAlert = today;
  R.changed();
  setTimeout(() => toast(`You're ${money(-b.flexLeft)} ${base()} over the flexible amount for this month.`, 6000), 300);
}

// ---------- Bills ----------

function billsTab() {
  const s = R.S();
  const today = C.todayEpoch();
  const cycle = C.cycleFor(today, s.cycleStart);
  const list = [...R.data.bills].sort((a, b) => a.nextDue - b.nextDue);
  const monthly = list.reduce((a, b) => a + C.monthlyShare(b.amount * R.rateOf(b.currency), b.frequency, b.everyN), 0);
  const leftThis = list.reduce((a, b) => a + C.occurrences(b.nextDue, b.frequency, b.everyN, b.nextDue, cycle.nextStart, b.anchorDay).length * b.amount * R.rateOf(b.currency), 0);
  let html = card(`<div class="row-flex"><div class="grow">${label('Per month on average')}<div class="big">${money(monthly)} ${base()}</div></div>
    <div class="right"><div class="small">left to pay this month</div><b>${money(leftThis)}</b></div></div>
    <div class="small">Yearly and quarterly bills are spread over the months, so they never surprise you.</div>`, 'primary');
  if (!list.length) html += `<p class="empty">No bills yet. Add internet, mobile, electricity, Netflix, the gym...</p>`;
  html += list.map((b) => {
    const days = b.nextDue - today;
    const p = days < 0 ? pill('late', 'bad') : days === 0 ? pill('today', 'warn') : days <= 3 ? pill(`in ${days} d`, 'warn') : '';
    return row(b.name, `${C.describeFreq(b.frequency, b.everyN)} · due ${C.fmtDay(b.nextDue)}${b.kind === 'SUB' ? ' · subscription' : ''}`, {
      icon: b.name, act: 'edit-bill', data: `data-id="${b.id}"`,
      trailing: `${p}<b>${b.variable ? '~' : ''}${C.fmt(b.amount)}</b><button class="btn text" data-act="pay-bill" data-id="${b.id}">Paid</button>`,
    });
  }).join('');
  return html + '<div class="spacer"></div>' + fab('Bill', 'edit-bill', 'data-id="0"');
}

function billEditor(b) {
  const isNew = !b || !b.id;
  const today = C.todayEpoch();
  b = b || { id: 0, name: '', amount: 0, currency: base(), frequency: 'MONTHLY', everyN: 30, anchorDay: C.ymd(today).d, nextDue: today, categoryId: null, walletId: null, remindDays: 1, variable: false, kind: 'BILL' };
  const body = chips('kind', [['BILL', 'Bill'], ['SUB', 'Subscription']], b.kind, 'seg') +
    field('name', 'Name', b.name) +
    numField('amount', 'Amount', b.amount, { suffix: b.currency }) +
    check('variable', 'Amount changes (use the average of the last 3)', b.variable) +
    (R.currencies().length > 1 ? chips('currency', R.currencies().map((x) => [x, x]), b.currency) : '') +
    label('Repeats') + chips('freq', C.FREQS, b.frequency) +
    `<div id="everyn" ${b.frequency === 'EVERY_N_DAYS' ? '' : 'hidden'}>${numField('everyN', 'Every how many days', b.everyN, { int: true })}</div>
    <div class="small" id="share"></div>` +
    dateField('due', 'Next due date', b.nextDue) +
    numField('remind', 'Remind me days before', b.remindDays, { int: true, keepZero: true }) +

    (isNew ? '' : `<button type="button" class="btn tonal" data-act="skip-bill" data-id="${b.id}">Skip this time</button>`);
  const { el } = sheet({
    title: isNew ? 'New bill' : b.name, body,
    onDelete: isNew ? null : () => R.deleteBill(b),
    onSave: (root) => {
      const amount = num(root, 'amount');
      const name = val(root, 'name').trim();
      if (!name) return 'Give it a name';
      if (!(amount > 0)) return 'Type the amount';
      const due = C.fromIso(val(root, 'due')) ?? today;
      const freq = chipVal(root, 'freq') || 'MONTHLY';
      R.saveBill({
        ...b, name, amount, kind: chipVal(root, 'kind') || 'BILL', frequency: freq, everyN: Math.max(parseInt(val(root, 'everyN')) || 30, 1),
        nextDue: due, anchorDay: C.ymd(due).d, walletId: null,
        remindDays: Math.min(Math.max(parseInt(val(root, 'remind')) || 0, 0), 30), variable: checked(root, 'variable'),
        currency: chipVal(root, 'currency') || b.currency, categoryId: b.categoryId ?? R.guessCategory(name, 'EXPENSE'),
      });
    },
  });
  const upd = () => {
    const f = chipVal(el, 'freq');
    $('#everyn', el).hidden = f !== 'EVERY_N_DAYS';
    const a = num(el, 'amount'), n = Math.max(parseInt(val(el, 'everyN')) || 30, 1);
    $('#share', el).textContent = a > 0 ? `≈ ${C.fmt(C.monthlyShare(a, f, n))} a month · ${C.fmt(C.monthlyShare(a, f, n) * 12)} a year` : '';
  };
  el.addEventListener('input', upd);
  el.addEventListener('click', () => setTimeout(upd));
  upd();
}

function payVariableBill(b) {
  sheet({
    title: `Pay ${b.name}`, saveLabel: 'Paid',
    body: numField('amount', 'Amount this time', Math.round(R.estimate(b) * 100) / 100, { suffix: b.currency }),
    onSave: (root) => { const a = num(root, 'amount'); if (!(a > 0)) return 'Type the amount'; R.payBill(b, a); },
  });
}

// ---------- Commitments: bills, installments, gam'eya, loans ----------

function commit() {
  const tabs = ['This month', 'Bills', 'Installments', "Gam'eya", 'Loans'];
  return `<h1 class="title pad-t">Commitments</h1><div class="subtabs">${tabs.map((t, i) => `<button class="${ui.commitTab === i ? 'on' : ''}" data-act="commit-tab" data-i="${i}">${t}</button>`).join('')}</div>` +
    [commitOverview, billsTab, installmentsTab, circlesTab, loansTab][ui.commitTab]();
}

// ---------- Commitments overview: left to pay, calendar, later / paid ----------

const WEEK = ['Sa', 'Su', 'Mo', 'Tu', 'We', 'Th', 'Fr'];
const iconStore = {
  get() { try { return JSON.parse(localStorage.getItem('pocketta.icons') || '{}'); } catch { return {}; } },
  set(k, v) {
    const m = iconStore.get();
    if (v) m[k] = v; else delete m[k];
    try { localStorage.setItem('pocketta.icons', JSON.stringify(m)); } catch { toast('Not enough room to keep the picture'); }
  },
};
const iconKey = (i) => ({ BILL: 'b', SUB: 'b', INSTALLMENT: 'i', CARD: 'i', CIRCLE: 'c', LOAN: 'l' }[i.kind] || 's') + (i.kind === 'INCOME' ? '' : i.refId);

function commitBadge(i, size = 44) {
  const img = iconStore.get()[iconKey(i)];
  const b = C.badgeFor(i.name, i.kind);
  const st = `width:${size}px;height:${size}px;border-radius:${Math.round(size * 0.3)}px`;
  if (img) return `<img class="cbadge" style="${st}" src="${img}" alt="">`;
  if (b.letter) return `<span class="cbadge" style="${st};background:${b.color};font-size:${Math.round(size * (b.letter.length > 1 ? 0.34 : 0.46))}px">${esc(b.letter)}</span>`;
  return `<span class="cbadge emo" style="${st};font-size:${Math.round(size * 0.5)}px">${b.emoji}</span>`;
}

function commitItems(off = 0) {
  const s = R.S(), today = C.todayEpoch();
  let cycle = C.cycleFor(today, s.cycleStart);
  for (let k = 0; k < off; k++) cycle = C.cycleFor(cycle.nextStart, s.cycleStart);
  for (let k = 0; k > off; k--) cycle = C.previousCycle(cycle, s.cycleStart);
  const lo = off === 0 ? -1e9 : cycle.start; // other months: only that month's own payments
  const out = [];
  const push = (o) => out.push({ incoming: false, payable: false, paid: false, ...o });
  for (const b of off < 0 ? [] : R.data.bills) {
    C.occurrences(b.nextDue, b.frequency, b.everyN, b.nextDue, cycle.nextStart, b.anchorDay).forEach((d, idx) => {
      if (d >= lo) push({ kind: b.kind === 'SUB' ? 'SUB' : 'BILL', refId: b.id, name: b.name, sub: C.describeFreq(b.frequency, b.everyN), amount: b.amount * R.rateOf(b.currency), date: d, payable: idx === 0 && off === 0 });
    });
  }
  for (const i of off < 0 ? [] : R.data.installments) {
    const kind = String(i.type).toLowerCase() === 'credit card' ? 'CARD' : 'INSTALLMENT';
    C.plan(i.amount, i.total, i.paid, i.firstDue).unpaidDueIn(lo, cycle.nextStart).forEach((d, idx) =>
      push({ kind, refId: i.id, name: i.name, sub: `${i.paid + idx + 1} of ${i.total}`, amount: i.amount, date: d, payable: idx === 0 && off === 0 }));
  }
  for (const c of off < 0 ? [] : R.data.circles) {
    C.circle(c).plan.unpaidDueIn(lo, cycle.nextStart).forEach((d, idx) =>
      push({ kind: 'CIRCLE', refId: c.id, name: c.name, sub: `Gam'eya · ${c.paid + idx + 1} of ${c.months}`, amount: c.share, date: d, payable: idx === 0 && off === 0 }));
  }
  for (const l of off < 0 ? [] : R.data.loans) {
    if (l.closed || l.dueDay == null || l.dueDay >= cycle.nextStart || l.dueDay < lo) continue;
    const lent = l.direction === 'LENT';
    push({ kind: 'LOAN', refId: l.id, name: l.person, sub: lent ? 'They owe you' : 'You owe', amount: R.remaining(l), date: l.dueDay, incoming: lent });
  }
  const inCycle = R.data.txns.filter((t) => cycle.contains(t.epochDay));
  for (const t of inCycle) {
    if (t.type !== 'EXPENSE') continue;
    let kind;
    if (t.source === 'BILL') kind = R.data.bills.find((b) => b.id === t.sourceId)?.kind === 'SUB' ? 'SUB' : 'BILL';
    else if (t.source === 'INSTALLMENT') kind = String(R.data.installments.find((i) => i.id === t.sourceId)?.type).toLowerCase() === 'credit card' ? 'CARD' : 'INSTALLMENT';
    else if (t.source === 'CIRCLE') kind = 'CIRCLE';
    else continue;
    push({ kind, refId: t.sourceId, name: t.note, sub: 'Paid', amount: t.amount * (t.rate || 1), date: t.epochDay, paid: true });
  }
  const got = inCycle.find((t) => t.source === 'SALARY');
  if (got) push({ kind: 'INCOME', refId: 0, name: 'Salary', sub: 'Received', amount: got.amount * (got.rate || 1), date: got.epochDay, paid: true, incoming: true });
  else if (s.salary > 0 && off >= 0) {
    const d = R.salaryDateIn(cycle, s.salaryDay);
    if (d != null) push({ kind: 'INCOME', refId: 0, name: 'Salary', sub: 'Expected', amount: s.salary, date: d, incoming: true });
  }
  return { out, cycle, today };
}

/** Everyday expenses and income (not bill/installment payments) on one day, so a tapped calendar day shows what was added. */
const plainTxnsOn = (d) => R.data.txns.filter((t) => t.epochDay === d && !t.source && t.type !== 'TRANSFER');

function commitOverview() {
  const { out: all, cycle, today } = commitItems(ui.cOff || 0);
  ui.cFilter = ui.cFilter || 'ALL';
  const filtered = all.filter((i) => C.commitMatches(ui.cFilter, i));
  const shown = ui.cDay != null ? filtered.filter((i) => i.date === ui.cDay) : filtered;
  const left = C.commitLeft(all), n = C.commitLeftCount(all);
  const sd = C.ymd(cycle.start);
  const monthName = sd.d === 1 ? `${C.fmtMonth(cycle.start)} ${sd.y}` : `${C.fmtDay(cycle.start)} – ${C.fmtDay(cycle.end)}`;
  const line = (i) => {
    const btn = i.payable && !i.paid && !i.incoming ? `<button class="btn text" data-act="commit-pay" data-k="${i.kind}" data-id="${i.refId}">Paid</button>` : '';
    return `<div class="row ${i.kind === 'INCOME' ? '' : 'tap'}" ${i.kind === 'INCOME' ? '' : `data-act="commit-open" data-k="${i.kind}" data-id="${i.refId}"`}>
      <button class="cbtn" data-act="commit-icon" data-k="${i.kind}" data-id="${i.refId}" data-name="${esc(i.name)}" aria-label="Change picture">${commitBadge(i)}</button>
      <div class="row-main"><div class="row-title"><b>${esc(i.name)}</b></div><div class="row-sub">${esc(C.fmtWeekday(i.date))} · ${esc(i.sub)}</div></div>
      <b class="${i.incoming ? 'good-t' : i.paid ? 'muted' : ''}">${i.incoming ? '+' : ''}${C.fmt(i.amount)}</b>${btn}</div>`;
  };
  const byDay = {};
  filtered.forEach((i) => (byDay[i.date] = byDay[i.date] || []).push(i));
  const offset = (C.ymd(cycle.start).wd + 1) % 7;
  let cells = WEEK.map((w) => `<div class="cal-w">${w}</div>`).join('');
  for (let k = 0; k < offset; k++) cells += '<div></div>';
  for (let k = 0; k < cycle.length; k++) {
    const d = cycle.start + k, its = byDay[d] || [];
    const spentDay = plainTxnsOn(d).length > 0;
    cells += `<button class="cal-d ${d === today ? 'today' : ''} ${ui.cDay === d ? 'sel' : ''}" data-act="commit-day" data-d="${d}"><span>${C.ymd(d).d}${spentDay ? '<i class="cal-dot"></i>' : ''}</span>
      <span class="cal-i">${its.slice(0, 2).map((i) => commitBadge(i, 17)).join('')}</span>${its.length > 2 ? `<small>+${its.length - 2}</small>` : ''}</button>`;
  }
  const late = C.commitLate(shown, today), later = C.commitLater(shown, today), done = C.commitDone(shown);
  let html = card(`${label('Left to pay this month')}<div class="big hero-n">${money(left)} ${base()}</div>
    <div>${n === 0 ? 'Nothing left to pay. Well done!' : `${n} payments to go`}</div><div class="small">${esc(monthName)}</div>`, 'primary');
  if (ui.bulkUndo) html += card(`<div class="row-flex"><div class="grow"><b class="good-t">✓ ${esc(ui.bulkUndo.text)}</b></div><button class="btn text" data-act="bulk-undo">Undo</button><button class="btn text" data-act="bulk-dismiss">OK</button></div>`, 'tonal');
  if (R.groupPayments(today).length) html += `<button class="btn outline wide" data-act="bulk-open">Mark a whole group as paid</button>`;
  html += `<div class="fchips">${C.COMMIT_FILTERS.map(([v, l]) => `<button class="chip ${ui.cFilter === v ? 'on' : ''}" data-act="commit-filter" data-v="${v}">${l}</button>`).join('')}</div>`;
  html += `<div class="month-nav"><button class="icon-btn" data-act="commit-prev" aria-label="Previous month">${icon('left')}</button>
    <div class="center"><div class="mid">${esc(monthName)}</div></div>
    <button class="icon-btn" data-act="commit-next" aria-label="Next month">${icon('right')}</button></div>`;
  html += card(`<div class="cal">${cells}</div>`);
  if (ui.cDay != null) {
    html += `<div class="row-flex pad-t"><div class="grow small">Showing ${esc(C.fmtDay(ui.cDay))}</div><button class="btn text" data-act="commit-day" data-d="${ui.cDay}">Show all</button></div>
      <button class="btn tonal wide" data-act="commit-add-day" data-d="${ui.cDay}">Add expense on ${esc(C.fmtDay(ui.cDay))}</button>`;
    const plain = plainTxnsOn(ui.cDay);
    if (plain.length) html += `<div class="day-h">Spent that day</div>` + plain.map(txnRow).join('');
  }
  if (late.length) html += `<div class="day-h bad-t">Late</div>` + late.map(line).join('');
  html += `<div class="day-h">Later this month</div>` + (later.length ? later.map(line).join('') : '<p class="empty">Nothing here.</p>');
  if (done.length) html += `<div class="day-h">Paid</div>` + done.map(line).join('');
  return html + '<div class="spacer"></div>' + fab('Add', 'commit-add');
}

function commitAddSheet() {
  const types = [['bill', 'Bill', '🧾'], ['sub', 'Subscription', '🔁'], ['inst', 'Installment', '📆'], ['card', 'Credit card', '💳'], ['circle', "Gam'eya", '🤝'], ['loan', 'Loan', '💸']];
  const { el, close } = sheet({
    title: 'Add', body: `<div class="addgrid">${types.map(([k, l, e]) => `<button type="button" class="addtype" data-new="${k}"><span>${e}</span>${l}</button>`).join('')}</div>`,
  });
  el.addEventListener('click', (e) => {
    const b = e.target.closest('[data-new]');
    if (!b) return;
    close();
    const k = b.dataset.new, today = C.todayEpoch();
    setTimeout(() => {
      if (k === 'bill') billEditor(null);
      else if (k === 'sub') billEditor({ id: 0, name: '', amount: 0, currency: base(), frequency: 'MONTHLY', everyN: 30, anchorDay: C.ymd(today).d, nextDue: today, categoryId: null, walletId: null, remindDays: 1, variable: false, kind: 'SUB' });
      else if (k === 'inst') instEditor(null);
      else if (k === 'card') instEditor({ id: 0, name: '', type: 'Credit card', provider: '', amount: 0, total: 12, paid: 0, firstDue: today, walletId: null, remindDays: 1 });
      else if (k === 'circle') circleEditor(null);
      else newLoan();
    }, 220);
  });
}

function commitIconSheet(i) {
  const key = iconKey(i), has = !!iconStore.get()[key];
  const { el, close } = sheet({
    title: `Picture for ${i.name}`,
    body: `<div class="row-flex gap16">${commitBadge(i, 64)}<div class="small">Pick a photo or logo from your gallery to show here.</div></div>
      <input type="file" accept="image/*" hidden id="cpick">
      <div class="btns"><button type="button" class="btn outline" data-pick="1">Pick from gallery</button>${has ? '<button type="button" class="btn text" data-pick="0">Use default</button>' : ''}</div>`,
  });
  el.addEventListener('click', (e) => {
    const b = e.target.closest('[data-pick]');
    if (!b) return;
    if (b.dataset.pick === '0') { iconStore.set(key, null); close(); render(); return; }
    $('#cpick', el).click();
  });
  $('#cpick', el).addEventListener('change', (e) => {
    const f = e.target.files?.[0];
    if (!f) return;
    const url = URL.createObjectURL(f), img = new Image();
    img.onload = () => {
      const side = Math.min(img.width, img.height), cv = document.createElement('canvas');
      cv.width = cv.height = 96;
      cv.getContext('2d').drawImage(img, (img.width - side) / 2, (img.height - side) / 2, side, side, 0, 0, 96, 96);
      iconStore.set(key, cv.toDataURL('image/png'));
      URL.revokeObjectURL(url); close(); render();
    };
    img.onerror = () => { URL.revokeObjectURL(url); toast('Could not read that picture'); };
    img.src = url;
  });
}

function installmentsTab() {
  const today = C.todayEpoch();
  const cycle = C.cycleFor(today, R.S().cycleStart);
  const items = R.data.installments;
  const circles = R.data.circles;
  const pl = (i) => C.plan(i.amount, i.total, i.paid, i.firstDue);
  const thisMonth = items.reduce((a, i) => a + pl(i).unpaidDueIn(-1e9, cycle.nextStart).length * i.amount, 0) +
    circles.reduce((a, c) => a + C.circle(c).plan.unpaidDueIn(-1e9, cycle.nextStart).length * c.share, 0);
  const owed = items.reduce((a, i) => a + pl(i).remainingAmount, 0) + circles.reduce((a, c) => a + C.circle(c).plan.remainingAmount, 0);
  const active = items.filter((i) => !pl(i).done), finished = items.filter((i) => pl(i).done);
  let html = card(`<div class="row-flex"><div class="grow">${label('Due this month')}<div class="big">${money(thisMonth)}</div></div>
    <div class="right"><div class="small">still owed in total</div><b>${money(owed)}</b></div></div>`, 'primary');
  if (!active.length && !circles.length) html += `<p class="empty">No installments yet. Add valU, Aman, Contact, a credit card plan, or your own type.</p>`;
  const instRow = (title, sub, p, amount, act, id, payAct) => {
    const late = p.nextDue != null && p.nextDue < today;
    return `<div>${row(title, sub, {
      icon: title, act, data: `data-id="${id}"`,
      trailing: `<div class="right"><b>${C.fmt(amount)}</b>${p.nextDue != null ? `<div class="small ${late ? 'bad-t' : ''}">${late ? 'late' : C.fmtDay(p.nextDue)}</div>` : ''}</div>
        <button class="btn text" data-act="${payAct}" data-id="${id}">Pay</button>`,
    })}<div class="row-bar">${progress(p.progress)}</div></div>`;
  };
  html += active.map((i) => instRow(i.name + (i.provider ? ` · ${i.provider}` : ''), `${i.type} · ${i.paid} of ${i.total} · ends ${C.fmtMonthYear(pl(i).endDate)}`, pl(i), i.amount, 'edit-inst', i.id, 'pay-inst')).join('');
  html += circles.filter((c) => c.paid < c.months).map((c) => instRow(c.name, `Gam'eya share · ${c.paid} of ${c.months}`, C.circle(c).plan, c.share, 'commit-tab" data-i="3', c.id, 'pay-circle')).join('');
  if (finished.length) html += `<div class="day-h">Finished</div>` + finished.map((i) => row(i.name, `${i.type} · paid off`, { icon: i.name, act: 'edit-inst', data: `data-id="${i.id}"`, trailing: pill('done', 'good') })).join('');
  return html + '<div class="spacer"></div>' + fab('Installment', 'edit-inst', 'data-id="0"');
}

const INST_TYPES = ['Consumer finance', 'Credit card', 'Phone / device', 'Car', 'Apartment'];
const PROVIDERS = ['valU', 'Aman', 'Contact', 'Souhoola', 'Halan', 'Sympl', 'Bank'];

function instEditor(it) {
  const isNew = !it || !it.id;
  it = it || { id: 0, name: '', type: 'Consumer finance', provider: '', amount: 0, total: 12, paid: 0, firstDue: C.todayEpoch(), walletId: null, remindDays: 1 };
  const types = [...new Set([...INST_TYPES, ...R.data.installments.map((x) => x.type)])];
  const body = field('name', 'What is it (fridge, laptop...)', it.name) +
    label('Type') + chips('type', types.map((t) => [t, t]), it.type) + field('newType', 'Or a new type', '') +
    field('provider', 'Company / bank (optional)', it.provider) + chips('prov', PROVIDERS.map((p) => [p, p]), it.provider) +
    numField('amount', 'Monthly payment', it.amount) +
    `<div class="two">${numField('total', 'Months in total', it.total, { int: true })}${numField('paid', 'Already paid', it.paid, { int: true, keepZero: true })}</div>` +
    dateField('first', 'First payment date', it.firstDue) + `<div class="small" id="sum"></div>` +
    numField('remind', 'Remind me days before', it.remindDays, { int: true, keepZero: true });
  const { el } = sheet({
    title: isNew ? 'New installment' : it.name, body,
    onDelete: isNew ? null : () => R.deleteInstallment(it),
    onSave: (root) => {
      const name = val(root, 'name').trim(), a = num(root, 'amount'), t = parseInt(val(root, 'total'));
      if (!name) return 'What is it?';
      if (!(a > 0)) return 'Type the monthly payment';
      if (!(t > 0)) return 'How many months?';
      R.saveInstallment({
        ...it, name, type: val(root, 'newType').trim() || chipVal(root, 'type') || it.type, provider: val(root, 'provider').trim(),
        amount: a, total: t, paid: Math.min(Math.max(parseInt(val(root, 'paid')) || 0, 0), t), firstDue: C.fromIso(val(root, 'first')) ?? it.firstDue,
        walletId: null, remindDays: Math.min(Math.max(parseInt(val(root, 'remind')) || 0, 0), 30),
      });
    },
  });
  const upd = () => {
    const a = num(el, 'amount'), t = parseInt(val(el, 'total')), first = C.fromIso(val(el, 'first'));
    const pv = chipVal(el, 'prov');
    if (pv && document.activeElement?.name !== 'provider') $('[name="provider"]', el).value = pv;
    if (a > 0 && t > 0 && first != null) {
      const p = C.plan(a, t, parseInt(val(el, 'paid')) || 0, first);
      $('#sum', el).textContent = `Total ${C.fmt(a * t)} · ends ${C.fmtMonthYear(p.endDate)} · ${C.fmt(p.remainingAmount)} left`;
    } else $('#sum', el).textContent = '';
  };
  el.addEventListener('input', upd);
  el.addEventListener('click', () => setTimeout(upd));
  upd();
}

function circlesTab() {
  const today = C.todayEpoch();
  const list = R.data.circles;
  let html = list.length ? '' : `<p class="empty">No gam'eya yet. Add your share, how many months, and your turn.</p>`;
  html += list.map((c) => {
    const cc = C.circle(c);
    const turns = Array.from({ length: Math.min(c.months, 24) }, (_, k) => {
      const m = k + 1;
      const cls = m === c.myTurn ? 'mine' : m <= c.paid ? 'done' : '';
      return `<i class="${cls} ${m === c.paid + 1 ? 'now' : ''}">${m}</i>`;
    }).join('');
    const payoutPast = cc.payoutDate <= today;
    return card(`<div class="row-flex"><b class="grow mid">${esc(c.name)}</b>${c.paid >= c.months ? pill('finished', 'good') : pill(`Month ${cc.currentMonth} of ${c.months}`)}</div>
      <div class="small">${C.fmt(c.share)} a month · pot ${C.fmt(cc.payout)} ${base()}</div>
      <div class="turns">${turns}</div>
      <div>${c.payoutReceived ? `You collected ${C.fmt(cc.payout)} (month ${c.myTurn}).` : `Your turn: month ${c.myTurn} · ${C.fmtMonthYear(cc.payoutDate)} · +${C.fmt(cc.payout)}`}</div>
      ${c.payoutReceived && c.paid < c.months ? `<div class="small">Still to pay after collecting: ${C.fmt((c.months - c.paid) * c.share)}</div>` : ''}
      <div class="btns">${cc.plan.nextDue != null ? `<button class="btn outline" data-act="pay-circle" data-id="${c.id}">Pay share · ${C.fmtDay(cc.plan.nextDue)}</button>` : ''}
      ${!c.payoutReceived && payoutPast ? `<button class="btn outline" data-act="payout" data-id="${c.id}">Got payout</button>` : ''}</div>`, 'tap', `data-act="edit-circle" data-id="${c.id}"`);
  }).join('');
  return html + '<div class="spacer"></div>' + fab("Gam'eya", 'edit-circle', 'data-id="0"');
}

function circleEditor(c) {
  const isNew = !c;
  c = c || { id: 0, name: '', share: 0, months: 10, myTurn: 1, paid: 0, firstDue: C.todayEpoch(), payoutReceived: false, walletId: null, remindDays: 1 };
  const body = field('name', 'Name (office, family...)', c.name) + numField('share', 'My share per month', c.share) +
    `<div class="two">${numField('months', 'Months / members', c.months, { int: true })}${numField('turn', 'My turn', c.myTurn, { int: true })}</div>` +
    dateField('first', 'First payment date', c.firstDue) + numField('paid', 'Shares already paid', c.paid, { int: true, keepZero: true }) +
    check('received', 'I already collected my payout', c.payoutReceived) + `<div class="small" id="sum"></div>` +
    numField('remind', 'Remind me days before', c.remindDays, { int: true, keepZero: true });
  const { el } = sheet({
    title: isNew ? "New gam'eya" : c.name, body,
    onDelete: isNew ? null : () => R.deleteCircle(c),
    onSave: (root) => {
      const name = val(root, 'name').trim(), s = num(root, 'share'), m = parseInt(val(root, 'months')), t = parseInt(val(root, 'turn'));
      if (!name) return 'Give it a name';
      if (!(s > 0)) return 'Type your monthly share';
      if (!(m > 0)) return 'How many months?';
      if (!(t >= 1 && t <= m)) return `Your turn must be 1 to ${m}`;
      R.saveCircle({
        ...c, name, share: s, months: m, myTurn: t, paid: Math.min(Math.max(parseInt(val(root, 'paid')) || 0, 0), m),
        firstDue: C.fromIso(val(root, 'first')) ?? c.firstDue, payoutReceived: checked(root, 'received'),
        walletId: null, remindDays: Math.min(Math.max(parseInt(val(root, 'remind')) || 0, 0), 30),
      });
    },
  });
  const upd = () => {
    const s = num(el, 'share'), m = parseInt(val(el, 'months')), t = parseInt(val(el, 'turn')), f = C.fromIso(val(el, 'first'));
    $('#sum', el).textContent = s > 0 && m > 0 && t >= 1 && t <= m && f != null
      ? `You collect ${C.fmt(s * m)} in ${C.fmtMonthYear(C.circle({ share: s, months: m, myTurn: t, paid: 0, firstDue: f }).payoutDate)}` : '';
  };
  el.addEventListener('input', upd);
  upd();
}

function loansTab() {
  const today = C.todayEpoch();
  const loans = [...R.data.loans].sort((a, b) => (a.closed - b.closed) || b.epochDay - a.epochDay);
  const owedToMe = loans.filter((l) => l.direction === 'LENT' && !l.closed).reduce((a, l) => a + R.remaining(l), 0);
  const iOwe = loans.filter((l) => l.direction !== 'LENT' && !l.closed).reduce((a, l) => a + R.remaining(l), 0);
  let html = `<div class="two">${card(label('They owe you') + `<div class="big good-t">${money(owedToMe)}</div>`)}${card(label('You owe') + `<div class="big bad-t">${money(iOwe)}</div>`)}</div>`;
  if (!loans.length) html += `<p class="empty">No loans. Money you lend or borrow shows up here.</p>`;
  html += loans.map((l) => {
    const lent = l.direction === 'LENT';
    let sub = `${lent ? 'Lent' : 'Borrowed'} ${C.fmtDay(l.epochDay)}`;
    if (l.repaid > 0) sub += ` · paid ${C.fmt(l.repaid)} of ${C.fmt(l.amount)}`;
    if (l.dueDay != null && !l.closed) sub += ` · by ${C.fmtDay(l.dueDay)}`;
    if (l.note) sub += ` · ${l.note}`;
    const p = l.closed ? pill('settled', 'good') : l.dueDay != null && l.dueDay < today ? pill('late', 'bad') : '';
    const cls = l.closed ? 'muted' : lent ? 'good-t' : 'bad-t';
    return row(l.person, sub, { icon: l.person, act: 'open-loan', data: `data-id="${l.id}"`, trailing: `${p}<b class="${cls}">${lent ? '+' : '−'}${C.fmt(l.closed ? l.amount : R.remaining(l))}</b>` });
  }).join('');
  return html + '<div class="spacer"></div>' + fab('Loan', 'new-loan');
}

function newLoan() {
  const body = label('Pick one first') + chips('dir', [['BORROWED', 'I borrowed money (I owe)'], ['LENT', 'I lent money (they owe me)']], '', 'seg') +
    field('person', 'To / from whom', '') + numField('amount', 'Amount', null, { suffix: base() }) +
    dateField('date', 'Date', C.todayEpoch()) + dateField('due', 'Pay back by (optional)', null) + field('note', 'Note (optional)', '') +
    check('record', 'Add it to my balance too', true);
  sheet({
    title: 'New loan', body,
    onSave: (root) => {
      const person = val(root, 'person').trim(), a = num(root, 'amount');
      if (!chipVal(root, 'dir')) return 'Pick: I borrowed or I lent';
      if (!person) return 'Who is it?';
      if (!(a > 0)) return 'Type the amount';
      R.addLoan({ person, lent: chipVal(root, 'dir') === 'LENT', amount: a, walletId: null, day: C.fromIso(val(root, 'date')) ?? C.todayEpoch(), due: C.fromIso(val(root, 'due')), note: val(root, 'note').trim(), record: checked(root, 'record') });
    },
  });
}

function loanDialog(l) {
  const lent = l.direction === 'LENT';
  const rem = R.remaining(l);
  const msg = `Hi ${l.person}, just a friendly reminder about the ${C.fmt(rem)} ${base()}${l.dueDay != null ? ` (due ${C.fmtDay(l.dueDay)})` : ''}. Thanks!`;
  const body = `<p>${esc(lent ? `${l.person} owes you ` : `You owe ${l.person} `)}<b>${C.fmt(rem)} ${base()}</b>${l.repaid > 0 ? esc(` (paid ${C.fmt(l.repaid)} of ${C.fmt(l.amount)})`) : ''}</p>` +
    (l.closed ? '' : numField('pay', lent ? 'Amount they paid back' : 'Amount you paid back', Math.round(rem * 100) / 100)) +
    dateField('due', 'Pay back by', l.dueDay) +
    (lent && !l.closed ? `<a class="btn outline" target="_blank" rel="noopener" href="https://wa.me/?text=${encodeURIComponent(msg)}">Send a WhatsApp reminder</a>` : '');
  sheet({
    title: l.person, body, saveLabel: l.closed ? 'Save' : lent ? 'Got it back' : 'I paid',
    onDelete: () => R.deleteLoan(l),
    onSave: (root) => {
      const due = C.fromIso(val(root, 'due'));
      const x = R.byId(R.data.loans, l.id);
      if (x && due !== x.dueDay) { x.dueDay = due; R.changed(); }
      if (!l.closed) { const a = num(root, 'pay'); if (a > 0) R.loanPayment(l, a); }
    },
  });
}


// ---------- Month plan ----------

function monthCard(m, nowYm) {
  const isNow = m.ym === nowYm, rem = P.remainingOf(m), open = ui.mp.open === m.ym;
  const kind = m.actual ? 'Actual' : isNow ? 'This month' : 'Expected';
  let detail = '';
  if (open) {
    const rows = [['Salary / income', m.salary, 1], ['Gam\'eya payout', m.payout, 1], ['Loans coming back', m.loansIn, 1], ['Bills and subscriptions', m.bills, 0],
      ['Installments', m.installments, 0], ['Credit cards', m.cards, 0], ["Gam'eya shares", m.circleShares, 0], ['Loans to pay back', m.loansOut, 0], ['Living', m.living, 0]];
    detail = `<div class="mpd">` + rows.filter((r) => r[1] !== 0).map(([l, v, plus]) =>
      `<div class="row-flex"><div class="grow">${esc(l)}</div><span class="${plus ? 'good-t' : ''}">${plus ? '+' : '−'} ${money(v)} ${esc(base())}</span></div>`).join('') +
      (m.lines?.length ? `<div class="small">${m.lines.map((x) => `${esc(x.name)} ${money(x.amount)}`).join(' · ')}</div>` : '') +
      (!m.actual ? `<div class="btns"><button class="btn tonal" data-act="mp-living" data-ym="${m.ym}">Change living cost</button><button class="btn tonal" data-act="mp-raise" data-ym="${m.ym}">Salary from here</button></div>` : '') + `</div>`;
  }
  return card(`<div class="row-flex"><div class="grow"><div class="mid">${esc(P.ymLabel(m.ym))}</div>
    <div class="small">${esc(kind)} · In ${money(P.incomeOf(m))} · Out ${money(P.committedOf(m) + m.living)}</div></div>
    <div style="text-align:end"><b class="${rem < 0 ? 'bad-t' : 'good-t'}" style="font-size:1.25rem">${money(rem)}</b><div class="small">Total ${money(m.cumulative)}</div></div></div>${detail}`,
  `tap ${isNow ? 'tonal' : ''}`, `data-act="mp-open" data-ym="${m.ym}" ${isNow ? 'id="mp-now"' : ''}`);
}
function monthPlanPage() {
  const s = R.S(), months = R.monthPlan(3, 24), nowYm = P.ymOf(C.todayEpoch());
  const steps = Object.entries(s.salarySteps || {}).map(([k, v]) => [+k, v]).sort((a, b) => a[0] - b[0]);
  const head = card(label('Living cost in a normal month') + `<div class="small">Food, transport, everyday things. Used for every coming month.</div>` +
    numField('living', 'Living cost', s.livingBase || null, { suffix: base() }) +
    `<div class="btns"><button class="btn filled" data-act="mp-save">Save</button><button class="btn tonal" data-act="mp-raise" data-ym="${nowYm}">Salary raise</button></div>` +
    steps.map(([k, v]) => `<div class="row-flex"><div class="grow small">From ${esc(P.ymLabel(k))}: ${money(v)} ${esc(base())}</div><button class="btn text" data-act="mp-raise" data-ym="${k}">Edit</button></div>`).join(''));
  return topbar('Month plan') + `<main class="page" id="monthplan">${head}${months.map((m) => monthCard(m, nowYm)).join('')}</main>`;
}
function livingSheet(ym) {
  const s = R.S(), has = s.livingOverrides && s.livingOverrides[ym] != null;
  sheet({
    title: `Living cost · ${P.ymLabel(ym)}`,
    body: `<div class="small">Only for this month. Other months keep the normal living cost.</div>` + numField('living', 'Living cost', has ? s.livingOverrides[ym] : (s.livingBase || null), { suffix: base() }),
    onDelete: has ? () => { const o = { ...s.livingOverrides }; delete o[ym]; R.setSetting('livingOverrides', o); } : null,
    onSave: (root) => { const v = num(root, 'living'); if (!(v >= 0)) return 'Type the amount'; R.setSetting('livingOverrides', { ...(s.livingOverrides || {}), [ym]: v }); },
  });
}
function raiseSheet(ym0) {
  const s = R.S(), steps = s.salarySteps || {}, has = steps[ym0] != null;
  sheet({
    title: 'Salary from a month',
    body: `<div class="small">From this month on, the salary is this amount, until the next raise.</div>` + dateField('month', 'Month', P.ymStart(ym0)) +
      numField('salary', 'Salary', has ? steps[ym0] : (P.stepAt(steps, ym0) ?? (s.salary || null)), { suffix: base() }),
    onDelete: has ? () => { const o = { ...steps }; delete o[ym0]; R.setSetting('salarySteps', o); } : null,
    onSave: (root) => {
      const d = C.fromIso(val(root, 'month')), v = num(root, 'salary');
      if (d == null) return 'Pick a month';
      if (!(v > 0)) return 'Type the salary';
      const o = { ...steps }; delete o[ym0]; o[P.ymOf(d)] = v;
      R.setSetting('salarySteps', o);
    },
  });
}

// ---------- Miqrish (the AI advisor) ----------

function chatBubble([role, text]) {
  return `<div class="bubble-row ${role === 'me' ? 'me' : ''}"><div class="bubble ${role}">${role === 'done' ? '✓ ' : ''}${esc(text)}</div></div>`;
}
function assistantPage() {
  const ch = ui.chat, prov = AI.cfg.provider();
  const settings = ch.settings ?? !AI.cfg.key();
  const bar = topbar('Miqrish', `<button class="btn text" data-act="ai-toggle">${settings ? 'Chat' : 'Settings'}</button>`);
  if (settings) {
    const notes = AI.cfg.notes();
    return bar + `<main class="page" id="aiset">` + card(label('Choose the company') +
      chips('aiprov', Object.entries(AI.PROVIDERS).map(([k, v]) => [k, v.label]), prov) +
      `<div class="small">Get a key at ${esc(AI.PROVIDERS[prov].help)}. Gemini has a free plan that is enough for personal use.</div>` +
      field('key', 'Your API key', AI.cfg.key(prov), { type: 'password' }) + field('model', 'Model', AI.cfg.model(prov)) +
      `<div class="btns"><button class="btn filled" data-act="ai-save">Save and chat</button>${AI.cfg.key(prov) ? `<button class="btn tonal" data-act="ai-remove">Remove key</button>` : ''}</div>` +
      `<div class="small">The key stays in this browser only. It is not in your backups and it is sent only to the company you picked. Your chat is sent to that company, and any cost is on your own account there.</div>`) +
      card(label('What Miqrish learned about you') + (notes.length ? notes.map((n) => `<div>• ${esc(n)}</div>`).join('') + `<div class="btns"><button class="btn text" data-act="ai-forget">Forget everything</button></div>`
        : `<div class="small">Nothing yet. When you correct it or tell it a habit, it remembers here.</div>`)) + `</main>`;
  }
  const intro = ch.bubbles.length ? '' : card(`<div class="mid">Hi, I'm Miqrish, your money advisor.</div><div class="small">Tell me things like: koshary 60 · I borrowed 1000 from Basem · I paid the installment · how much did I spend on food this month? I always ask before I save anything.</div>`);
  return bar + `<main class="page chatpage"><div id="chat">${intro}${ch.bubbles.map(chatBubble).join('')}${ch.busy ? '<div class="small">…</div>' : ''}</div>
    <div class="chatbar"><input id="ai-in" placeholder="Write to Miqrish" value="${esc(ch.draft)}" autocomplete="off"><button class="btn filled" data-act="ai-send" ${ch.busy ? 'disabled' : ''}>Send</button></div></main>`;
}
function aiSession() {
  const ch = ui.chat;
  if (!ch.session) {
    ch.session = AI.createSession({
      confirm: (what) => new Promise((resolve) => sheet({ title: 'Save this?', body: `<p>${esc(what)}</p>`, saveLabel: 'Yes, save', onSave: () => { resolve(true); }, onClose: () => resolve(false) })),
      show: (role, text) => { ch.bubbles.push([role, text]); if (cur().r === 'assistant') render(); },
    });
  }
  return ch.session;
}
async function aiSend() {
  const ch = ui.chat, inp = $('#ai-in'), text = (inp?.value || '').trim();
  if (!text || ch.busy) return;
  ch.draft = '';
  ch.bubbles.push(['me', text]);
  ch.busy = true;
  render();
  try { await aiSession().send(text); }
  catch (e) { ch.bubbles.push(['error', e instanceof AI.AiError ? e.friendly() : 'Could not reach the assistant. Check your internet and try again.']); }
  ch.busy = false;
  render();
}

// ---------- More ----------

/** One card per running gam'eya: when it's your turn, how much you collect, how many installments are left. */
function gameyaCards(s) {
  const cur = base();
  return R.data.circles.map((c) => {
    const k = C.circle(c);
    const left = k.plan.remainingCount, next = k.plan.nextDue;
    const days = k.payoutDate - s.today;
    const collectNow = !c.payoutReceived && days <= 0;
    if (k.plan.done && c.payoutReceived) return '';
    const ln = (l, v, tone = '') => `<div class="pline"><span>${esc(l)}</span><span class="${tone} b">${esc(v)}</span></div>`;
    let h = `${label("Gam'eya")}<div class="mid">${esc(c.name)}</div><div class="gap8"></div>`;
    if (c.payoutReceived) h += `<div class="good-t b">You collected your turn</div>`;
    else if (collectNow) h += `<div class="good-t b">It's your turn! Collect ${money(k.payout)} ${esc(cur)}</div>`;
    else h += ln('Your turn', `${C.fmtDay(k.payoutDate)}, ${C.ymd(k.payoutDate).y}`) + ln('Days to your turn', String(days)) + ln('You collect', `${money(k.payout)} ${cur}`, 'good-t');
    h += ln('Installments left', `${left} / ${c.months}`);
    if (next != null) h += ln('Next installment', `${money(c.share)} ${cur} · ${C.fmtDay(next)}`);
    h += progress(c.months > 0 ? c.paid / c.months : 0);
    const btns = (collectNow ? `<button class="btn filled" data-act="gameya-payout" data-id="${c.id}">Got my payout</button>` : '') +
      (next != null && next <= s.today + 7 ? `<button class="btn tonal" data-act="gameya-pay" data-id="${c.id}">Paid installment</button>` : '');
    return card(h + (btns ? `<div class="btns">${btns}</div>` : ''), collectNow ? 'tonal' : '');
  }).join('');
}

function risesCard(d) {
  const rises = d.rises.slice(0, 3);
  if (!rises.length) return '';
  return card(`${label('Prices are rising')}` + rises.map((r) => `<div class="pline"><b>${esc(r.name)}</b><span class="bad-t b">▲ ${r.percent}% vs your usual</span></div>`).join('') +
    `<div class="small">At this pace, compared with your average of the last 3 months</div>`, 'tonal');
}

// ---------- Everything I own ----------

function wealthEntry() {
  R.recordWealth();
  const w = R.wealth();
  return card(`${label('Everything I own')}<div class="row-flex"><div class="big grow">${money(w.total)} ${esc(base())}</div><span class="chev">›</span></div>${w.dollars != null ? `<div class="small">≈ $${money(w.dollars)}</div>` : ''}`, 'tap', 'data-act="open" data-r="wealthPage"');
}
function sparkline(values) {
  const lo = Math.min(...values), hi = Math.max(...values), span = hi - lo || 1, W = 320, H = 110;
  const pts = values.map((v, i) => [i * W / Math.max(values.length - 1, 1), H - 8 - (v - lo) / span * (H - 16)]);
  return `<svg viewBox="0 0 ${W} ${H}" width="100%" height="${H}" aria-hidden="true"><polyline fill="none" stroke="var(--primary)" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" points="${pts.map((p) => p.join(',')).join(' ')}"/><circle cx="${pts[pts.length - 1][0]}" cy="${pts[pts.length - 1][1]}" r="5" fill="var(--primary)"/></svg>`;
}
function wealthPage() {
  R.recordWealth();
  const w = R.wealth(), cur = base(), today = C.todayEpoch();
  const ln = (l, v, tone = '') => `<div class="pline"><span>${esc(l)}</span><span class="${tone} b">${esc(v)}</span></div>`;
  let total = `${label('Total')}<div class="huge">${money(w.total)} ${esc(cur)}</div><div class="gap8"></div>` + ln('Money I have', `${money(w.cash)} ${cur}`) + ln("Gold I bought, at today's price", `${money(w.gold)} ${cur}`);
  if (w.dollars != null) total += ln('In dollars', `$${money(w.dollars)}`);
  if (w.goldGrams != null) total += ln('In gold (21k)', `${C.fmt(Math.round(w.goldGrams * 10) / 10)} g`);
  if (w.dollars == null && w.goldGrams == null) total += `<div class="small">Turn on today's rates in Currencies & gold to see dollars and gold</div>`;
  let time = label('Over time');
  if (w.history.length < 2) time += `<div class="small">Come back in a few days. Pocketta saves your total once a day and draws the line here.</div>`;
  else {
    time += sparkline(w.history.map((p) => p[1]));
    for (const [l, d] of [['Last 7 days', 7], ['Last 30 days', 30], ['Last 90 days', 90]]) {
      const ch = C.wealthChange(w.history, today, d);
      if (ch != null) time += ln(l, `${ch >= 0 ? '+' : '−'}${money(Math.abs(ch))} ${cur}`, ch >= 0 ? 'good-t' : 'bad-t');
    }
  }
  if (w.salaryComing > 0) total += ln('Salary still coming (not counted above)', `${money(w.salaryComing)} ${cur}`);
  if (w.cash < 0) total += `<div class="small bad-t">Your money is below zero because Pocketta only counts what you typed. Tap Change my balance and type what you really have.</div><div class="gap8"></div>`;
  total += `<button class="btn tonal wide" data-act="edit-balance">Change my balance</button>`;
  return topbar('Everything I own') + `<main class="page">${card(total)}${card(time)}</main>`;
}

// ---------- Plan ahead: can I afford this, what if I save, emergency mode ----------

const monthsText = (m) => m <= 0 ? 'now' : m === 1 ? '1 month' : `${m} months`;
const pline = (l, v, tone = '', bold = false) => `<div class="pline"><span>${esc(l)}</span><span class="${tone} ${bold ? 'b' : ''}">${esc(v)}</span></div>`;
const goalDateText = (today, months) => C.fmtMonthYear(C.goalDateAfter(today, months));

function emergencyCard(d, full) {
  const e = d.emergency, cur = base();
  if (!e) return full ? card(`${label('Emergency mode')}<div class="good-t b">Your money should last until the end of the month</div>`) : '';
  let h = `${label('Emergency mode')}<div class="mid">Your money may run out</div>` +
    pline('Money runs out around', C.fmtDay(e.runOutDate), '', true) + pline('Days before the month ends', String(e.daysShort)) +
    pline('Missing by month end', `${money(e.shortfall)} ${cur}`, 'bad-t', true);
  if (full) {
    if (e.postpone.length) h += `<div class="gap8">${label('Postpone if you can')}</div>` + e.postpone.map((p) => pline(`${p.name} · ${C.fmtDay(p.due)}`, `${money(p.amount)} ${cur}`)).join('');
    if (e.cuts.length) h += `<div class="gap8">${label('Spend less on')}</div>` + e.cuts.map((c) => pline(c.name, `−${money(c.amount)} ${cur}`)).join('');
    h += e.uncovered > 0
      ? `<div class="gap8">${pline('Still missing after this', `${money(e.uncovered)} ${cur}`, 'bad-t', true)}<div class="small">Think about extra income or moving money from savings</div></div>`
      : `<div class="gap8 good-t b">Do both and you reach the end of the month.</div>`;
  } else h += `<div class="small">Tap to see what to postpone or cut</div>`;
  return card(h, 'tonal' + (full ? '' : ' tap'), full ? '' : 'data-act="open" data-r="plan"');
}

function affordOut() {
  const d = R.planData(), s = R.S(), b = d.home.budget, cur = base();
  const price = C.parseNumber(ui.plan.price) || 0;
  if (!(price > 0)) return `<div class="small">Type a price to see what it does to this month and your goal</div>`;
  const r = C.canAfford(price, b.dailyAllowance, b.left, b.daysLeft, s.goalAmount, s.goalSaved, s.savingsPerMonth || 0);
  const tone = r.verdict === 'YES' ? 'good-t' : r.verdict === 'NO' ? 'bad-t' : 'warn-t';
  let h = `<div class="mid ${tone}">${{ YES: 'Yes, you can afford it', TIGHT: 'You can, but it will be tight', NO: 'Not this month' }[r.verdict]}</div>` +
    pline('Daily number now', `${money(r.dailyBefore)} ${cur}`) + pline('Daily number after', `${money(r.dailyAfter)} ${cur}`, tone, true) +
    pline('Left this month now', `${money(r.leftBefore)} ${cur}`) + pline('Left this month after', `${money(r.leftAfter)} ${cur}`, tone, true);
  if (r.shortfall > 0) {
    h += `<div class="small">It would have to come out of your savings: ${money(r.shortfall)}</div>`;
    const gm = C.monthsToGoal(s.goalAmount, s.goalSaved, s.savingsPerMonth || 0);
    if (r.goalDelayMonths > 0 && gm != null) {
      h += pline('Goal date', `${goalDateText(d.home.today, gm)} → ${goalDateText(d.home.today, gm + r.goalDelayMonths)}`, 'bad-t', true) +
        `<div class="small">Your goal moves back ${monthsText(r.goalDelayMonths)}</div>`;
    }
  }
  return h;
}

function simOut() {
  const d = R.planData(), s = R.S(), cur = base();
  const cats = d.categories.filter((c) => !C.FIXED_CATEGORIES.includes(c.name) && c.monthly > 0).slice(0, 8);
  if (!cats.length) return `<div class="small">Add some spending first, then try cutting a category</div>`;
  const cat = cats.find((c) => c.name === ui.plan.cat) || cats[0];
  const sim = C.simulateCut(cat.monthly, ui.plan.pct, s.goalAmount, s.goalSaved, s.savingsPerMonth || 0);
  const chipRow = (act, items, on) => `<div class="chips">${items.map(([v, l]) => `<button type="button" class="chip ${String(v) === String(on) ? 'on' : ''}" data-act="${act}" data-val="${esc(v)}">${esc(l)}</button>`).join('')}</div>`;
  let h = chipRow('plan-cat', cats.map((c) => [c.name, c.name]), cat.name) + `<div class="gap8"></div>` + chipRow('plan-pct', [10, 20, 30, 50].map((p) => [p, `${p}%`]), ui.plan.pct) + `<div class="gap8"></div>` +
    pline('You spend per month', `${money(cat.monthly)} ${cur}`) + pline('You would save per month', `${money(sim.extraPerMonth)} ${cur}`, 'good-t', true) +
    pline('You would save per year', `${money(sim.extraPerMonth * 12)} ${cur}`);
  if (!(s.goalAmount > 0)) h += `<div class="small">Set a savings goal in Settings to see the date</div>`;
  else if (s.goalSaved >= s.goalAmount) h += `<div class="good-t b">Goal reached!</div>`;
  else if (sim.monthsAfter != null) {
    if (sim.monthsBefore != null) h += pline('Goal date now', goalDateText(d.home.today, sim.monthsBefore));
    h += pline('Goal date with this cut', goalDateText(d.home.today, sim.monthsAfter), 'good-t', true);
    if (sim.monthsSooner > 0) h += `<div class="good-t b">Sooner by ${monthsText(sim.monthsSooner)}</div>`;
    else if (sim.monthsBefore == null) h += `<div class="small">Without a monthly saving the goal has no date. This cut gives it one.</div>`;
  }
  return h;
}

function plan() {
  const d = R.planData();
  return topbar('Plan ahead') + `<main class="page">` + emergencyCard(d, true) +
    card(`${label('Can I afford this?')}${numField('price', 'Price', ui.plan.price, { suffix: base() })}<div id="afford-out">${affordOut()}</div>`) +
    card(`${label('What if I save?')}<div id="sim-out">${simOut()}</div>`) + `</main>`;
}
function planEntryCard() {
  const d = R.planData();
  return emergencyCard(d, false) + risesCard(d) + card(`<div class="row-flex gap16"><span class="bigicon">🧮</span><div class="grow"><b>Plan ahead</b><div class="small">Can I afford this? What if I save?</div></div><span class="chev">›</span></div>`, 'tap', 'data-act="open" data-r="plan"');
}

function more() {
  const big = (emoji, title, sub, r) => `<div class="card bigcard tap" data-act="open" data-r="${r}"><span class="bigicon">${emoji}</span>
    <div class="grow"><div class="mid">${esc(title)}</div><div class="small">${esc(sub)}</div></div><span class="chev">›</span></div>`;
  return topbar('Settings & more') + `<main class="page more">` +
    big('📅', 'Month plan', 'Every month like your spreadsheet: income, installments, living, what is left', 'monthPlanPage') +
    big('🐷', 'Piggy bank', 'Everything you saved, month by month', 'piggyPage') +
    big('🎯', 'Goals', 'Umra, a car, a laptop, each with its own date', 'goalsPage') +
    big('🏦', 'Everything I own', 'Your money and gold in one number, in dollars and gold, and how it changed', 'wealthPage') +
    big('🧮', 'Plan ahead', 'Can I afford this? What if I save? When money runs short', 'plan') +
    big('💰', 'Income & salary', 'Your monthly salary, other income, money set aside', 'income') +
    big('#️⃣', 'Tags', 'Weddings, trips, projects: see the total of each across categories', 'tagsPage') +
    big('🏷️', 'Categories', 'Categories, sub-categories and monthly limits', 'categories') +
    big('💱', 'Currencies & gold', "Convert money and gold, today's prices, your own rates", 'currencies') +
    big('⚙️', 'Settings', 'Month start, look, savings goal, PIN lock, backup', 'settings') + `</main>`;
}

function income() {
  const s = R.S();
  const cycle = C.cycleFor(C.todayEpoch(), s.cycleStart);
  const incomes = R.data.txns.filter((t) => t.type === 'INCOME' && cycle.contains(t.epochDay)).sort((a, b) => b.epochDay - a.epochDay);
  return topbar('Income & salary') + `<main class="page" id="income">` +
    card(label('Monthly salary') + `<div class="small">Added by itself every month on its day. If one month is different, edit it in Spend.</div>` +
      numField('salary', 'Salary', s.salary, { suffix: base() }) + numField('salaryDay', 'Day of the month it arrives', s.salaryDay, { int: true }) +
      numField('savings', 'Set aside for savings each month (optional)', s.savingsPerMonth, { suffix: base() }) +
      numField('household', 'Household shopping each month (optional)', s.household, { suffix: base() }) +
      `<div class="small">Kept apart from your flexible money. Groceries you add use this amount first.</div>` +
      `<button class="btn filled" data-act="save-income">Save</button>`) +
    `<div class="row-flex pad"><div class="label grow">Income this month · ${money(incomes.reduce((a, t) => a + R.base(t), 0))}</div><button class="btn outline" data-act="open" data-r="add" data-type="INCOME">Add income</button></div>` +
    (incomes.length ? incomes.map(txnRow).join('') : `<p class="empty">Nothing yet this month.</p>`) +
    `<p class="small pad">Other income: gam'eya payout, borrowed money (also saved as a debt), side work, gifts. Pick the category when you add it.</p></main>`;
}

function categories() {
  const list = R.categories().filter((c) => c.kind === ui.catKind).sort((a, b) => a.sort - b.sort);
  const parents = list.filter((c) => c.parentId == null);
  return topbar('Categories', `<button class="icon-btn" data-act="edit-cat" data-id="0" aria-label="Add category">${icon('add')}</button>`) + `<main class="page">` +
    `<div class="pad">${chips('catkind', [['EXPENSE', 'Spending'], ['INCOME', 'Income']], ui.catKind, 'seg')}</div>` +
    parents.map((p) => row(p.name, p.monthlyLimit > 0 ? `Limit ${C.fmt(p.monthlyLimit)} a month` : '', {
      icon: p.name, act: 'edit-cat', data: `data-id="${p.id}"`,
      trailing: ui.catKind === 'EXPENSE' ? `<button class="btn text" data-act="add-sub" data-id="${p.id}">+ Sub</button>` : '',
    }) + list.filter((c) => c.parentId === p.id).map((c) => `<div class="indent">${row(c.name, '', { act: 'edit-cat', data: `data-id="${c.id}"` })}</div>`).join('')).join('') + `</main>`;
}

function catEditor(c, parentId = null) {
  const isNew = !c;
  c = c || { id: 0, name: '', parentId, kind: ui.catKind, monthlyLimit: 0, sort: R.data.categories.length, archived: false };
  const body = field('name', 'Name', c.name) +
    (c.parentId == null && c.kind === 'EXPENSE' ? numField('limit', 'Monthly limit (optional)', c.monthlyLimit) + `<div class="small">The bar turns amber near the limit and red when you go over.</div>` : '') +
    (isNew ? '' : `<div class="small">Deleting hides it. Past records keep it.</div>`);
  sheet({
    title: isNew ? (parentId ? 'New sub-category' : 'New category') : c.name, body,
    onDelete: isNew ? null : () => { R.byId(R.data.categories, c.id).archived = true; R.changed(); },
    onSave: (root) => {
      const name = val(root, 'name').trim();
      if (!name) return 'Give it a name';
      const nc = { ...c, name, monthlyLimit: num(root, 'limit') || 0 };
      if (isNew) R.data.categories.push({ ...nc, id: R.nextId() }); else Object.assign(R.byId(R.data.categories, c.id), nc);
      R.changed();
    },
  });
}

function currencyPage() {
  const s = R.S();
  const rates = Object.entries(s.rates).sort();
  const online = Object.keys(s.onlineRates).sort();
  const goldRows = ['24', '21', '18'].filter((k) => s.gold[k]).map((k) => `<div class="kv"><span>Karat ${k}</span><b>${money(s.gold[k])} ${esc(s.baseCurrency)}</b></div>`).join('');
  return topbar('Currencies & gold') + `<main class="page" id="cur">` +
    card(label("Today's rates and gold") + `<label class="switch-row"><span>Update rates from the internet</span><input type="checkbox" data-act="toggle-auto" ${s.autoRates ? 'checked' : ''}></label>
      ${s.autoRates ? `<div class="small">${s.ratesUpdated ? `Rates of ${esc(String(s.ratesUpdated).slice(0, 16))}` : 'No rates yet. Tap Update now.'}</div>
      ${online.length ? `<div class="small">Currencies to show:</div><div class="chips">${online.map((c) => `<button type="button" class="chip ${s.autoCurrencies.includes(c) ? 'on' : ''}" data-act="auto-cur" data-c="${c}">${c}</button>`).join('')}</div>` : ''}
      ${s.autoCurrencies.filter((c) => s.onlineRates[c]).map((c) => `<div class="kv"><span>1 ${c}</span><b>${C.fmt(s.onlineRates[c])} ${esc(s.baseCurrency)}${R.isManual(c) ? ` (you set ${C.fmt(s.rates[c])})` : ''}</b></div>`).join('')}
      ${goldRows ? `<div class="label pad-t">Gold, per gram</div>${goldRows}<div class="small">From the world gold price and the dollar rate. Shops may differ a little.</div>` : ''}
      <button class="btn text" data-act="update-rates">Update now</button>` : `<div class="small">Turned off. Only the rates you type below are used.</div>`}`) +
    goldCard() +
    card(label('Your own rates') + `<div class="small">A rate you type here always wins over the internet one.</div>` +
      rates.map(([c, r]) => `<div class="row-flex line"><span class="grow">1 ${esc(c)} = ${C.fmt(r)} ${esc(s.baseCurrency)}</span><button class="btn text" data-act="edit-rate" data-c="${esc(c)}">Edit</button><button class="btn text" data-act="del-rate" data-c="${esc(c)}">Remove</button></div>`).join('') +
      `<div class="two">${field('code', 'Code (USD)', '')}${numField('rate', 'Rate', null)}</div><button class="btn filled" data-act="save-rate">Save rate</button>`) +
    card(label('Main currency') + mainCurrencyBody(s)) + `</main>`;
}

function goldCard() {
  const s = R.S(), buys = s.goldBuys || [], sum = C.goldSummary(buys, s.gold), cur = esc(s.baseCurrency);
  const tone = (v) => (v >= 0 ? 'good-t' : 'bad-t');
  let html = label('My gold') + `<div class="small">Write down gold you bought to see your profit or loss against today's price.</div>`;
  if (sum) {
    html += `<div class="mid ${tone(sum.profit)}">${sum.profit >= 0 ? 'Profit' : 'Loss'} ${money(Math.abs(sum.profit))} ${cur}</div>
      <div class="small ${tone(sum.profit)}"><bdi>${sum.profit >= 0 ? '+' : '−'}${C.fmt(Math.abs(Math.round(sum.percent * 10) / 10))}%</bdi></div>
      <div class="small">${C.fmt(sum.grams)} g · Paid ${money(sum.cost)} · Worth ${money(sum.value)} ${cur}</div>`;
  } else if (buys.length) html += `<div class="small">Today's gold price isn't downloaded yet.</div>`;
  html += buys.map((b, i) => {
    const pr = C.goldProfit(b, s.gold);
    return `<div class="row-flex line"><div class="grow"><b>Karat ${esc(b.karat)} · ${C.fmt(b.grams)} g</b><div class="small">Bought at ${C.fmt(b.price)} per gram</div>
      ${pr == null ? '' : `<div class="small ${tone(pr)}">${pr >= 0 ? 'Profit' : 'Loss'} ${money(Math.abs(pr))} ${cur}</div>`}</div>
      <button class="btn text" data-act="gold-del" data-i="${i}">Remove</button></div>`;
  }).join('');
  html += chips('gkarat', [['24', 'Karat 24'], ['21', 'Karat 21'], ['18', 'Karat 18']], '21') +
    `<div class="two">${numField('gGrams', 'Grams', null)}${numField('gPrice', 'Price per gram', null, { suffix: s.baseCurrency })}</div><button class="btn filled" data-act="gold-add">Add gold</button>`;
  return card(html);
}

// ---------- Settings ----------

function settings() {
  const s = R.S();
  const standalone = navigator.standalone || matchMedia('(display-mode: standalone)').matches;
  return topbar('Settings') + `<main class="page" id="settings">` +
    card(label('Month') + `<div class="row-flex">${numField('start', 'My month starts on day', s.cycleStart, { int: true })}<button class="btn text" data-act="save-start">Save</button></div>`) +
    card(label('Look') + `<div class="small">Night mode</div>` + chips('theme', [['AUTO', 'Auto'], ['LIGHT', 'Light'], ['DARK', 'Dark']], s.theme, 'seg') + `<div class="small pad-t">Language</div>` + chips('lang', [['EN', 'English'], ['AR', 'العربية']], s.lang || 'EN', 'seg')) +
    card(label('Savings goal') + `<div class="small">Set a goal (Umra, car, wedding...) and watch the ring fill up on Today.</div>` +
      field('goalName', 'Name', s.goalName) + `<div class="two">${numField('goalAmount', 'Goal amount', s.goalAmount, { suffix: base() })}${numField('goalSaved', 'Saved so far', s.goalSaved, { suffix: base() })}</div>` +
      `<button class="btn filled" data-act="save-goal">Save</button>`) +
    card(label('Alerts') + `<label class="switch-row"><span>Tell me when I go over today's budget</span><input type="checkbox" data-act="toggle-alert" ${s.budgetAlert ? 'checked' : ''}></label>
      <label class="switch-row"><span>Tell me when a category goes over its monthly limit</span><input type="checkbox" data-act="toggle-limit" ${s.limitAlert !== false ? 'checked' : ''}></label>
      <label class="switch-row"><span>At month end, offer to put what's left in my piggy bank</span><input type="checkbox" data-act="toggle-monthend" ${s.monthEndReminder !== false ? 'checked' : ''}></label>
      <div class="small">Bills, installments and gam'eya shares that are due soon (by the days you choose on each one) show on Home and as a number on the app icon.</div>`) +
    card(label('Privacy') + (s.pinHash
      ? `<div class="row-flex"><span class="grow">PIN lock is on</span><button class="btn text" data-act="pin-set">Change</button><button class="btn text danger" data-act="pin-off">Turn off</button></div>`
      : `<div class="row-flex"><span class="grow">Lock the app with a PIN</span><button class="btn tonal" data-act="pin-set">Set PIN</button></div>`) +
      `<div class="small">Asked when you open the app or come back after a minute.</div>`) +
    card(label('Sync and backup') + syncCard()) +
    card(label('Backup') + `<div class="small">Everything is on this phone only, inside the browser. Save a backup file now and then (to Files, Drive, or WhatsApp to yourself). If you clear Safari's website data, the app's data goes too.</div>
      <div class="btns"><button class="btn outline" data-act="backup">Save backup</button><button class="btn outline" data-act="restore">Restore</button></div>
      <input type="file" id="restore-file" accept=".json,application/json" hidden>`) +
    card(label('About') + `<div>Pocketta web ${VERSION}${standalone ? ' · on home screen' : ''}</div>
      <div class="small">Updates come by themselves the next time you open it with internet.</div>
      <button class="btn text danger" data-act="wipe">Delete all data</button>`) + `</main>`;
}


function syncCard() {
  const i = Sync.info();
  const note = `<div class="lock-note">🔒 Your money data is locked on this device before it is sent. Only you can open it, with your encryption password. Nobody else can, not even us.</div>`;
  if (!Sync.signedIn()) {
    return `<div class="small">Optional. Pocketta works exactly the same without an account. Sign in to keep your phone and laptop in sync and to have an automatic backup.</div>
      <button class="btn outline google" data-act="sync-in"><span class="gdot"></span>Sign in with Google</button>${note}`;
  }
  const when = i.lastSyncAt ? ago(i.lastSyncAt) : 'not yet';
  const st = i.busy ? 'Syncing...' : i.error === 'offline' ? 'No internet. It will sync when you are back online.' : i.error ? esc(i.error) : i.status === 'paused' ? 'Paused. Tap Sync now to choose.' : 'Sync is on';
  return `<div class="row-flex"><b class="grow">${st}</b></div>
    <div class="small">${esc(i.email)}</div>
    ${i.encrypted ? '<div class="small">Encrypted on this device</div>' : ''}
    <div class="small">Last sync: ${esc(when)}</div>
    <div class="btns"><button class="btn filled" data-act="sync-now">Sync now</button><button class="btn outline" data-act="sync-out">Sign out</button></div>
    <div class="small">If you sign out, your data stays on this device and sync stops.</div>${note}
    <button class="btn text danger" data-act="sync-delete">Delete my data from the cloud</button>`;
}
function ago(t) {
  const m = Math.round((Date.now() - t) / 60000);
  return m < 1 ? 'just now' : m < 60 ? `${m} min ago` : m < 1440 ? `${Math.round(m / 60)} h ago` : new Date(t).toLocaleDateString();
}
function fmtAt(t) { return t ? new Date(t).toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' }) : ''; }


function syncSetup({ recovery }) {
  return new Promise((resolve) => {
    let done = false;
    const fin = (v) => { if (!done) { done = true; resolve(v); } };
    sheet({
      title: 'Lock your data',
      body: `<p>Your data is locked on this device before it goes to the cloud. Nobody can read it without your encryption password, not even us. This is not your Google password.</p>` +
        field('p1', 'Encryption password (8 or more)', '', { type: 'password' }) + field('p2', 'Type it again', '', { type: 'password' }) +
        `<div class="label">Recovery key</div><div class="recovery">${esc(recovery)}</div>
        <button type="button" class="btn text" data-act="copy-text" data-text="${esc(recovery)}">Copy</button>
        <button type="button" class="btn text" data-act="download-text" data-text="${esc(recovery)}">Download as a file</button>
        <p class="small">Save it somewhere safe (a screenshot, or WhatsApp to yourself). If you forget the password, this key is the only way back. With neither, your cloud copy is lost for good. The data on your devices stays.</p>` +
        check('saved', 'I saved the recovery key'),
      saveLabel: 'Lock and continue',
      onSave: (root) => {
        const p1 = val(root, 'p1');
        if (p1.length < 8) return 'Use at least 8 characters';
        if (p1 !== val(root, 'p2')) return "The two passwords don't match";
        if (!checked(root, 'saved')) return 'Tick the box after you save the recovery key';
        fin({ password: p1 });
      },
      onClose: () => fin(null),
    });
  });
}

function syncUnlock({ bad }) {
  return new Promise((resolve) => {
    let done = false, switching = false;
    const fin = (v) => { if (!done) { done = true; resolve(v); } };
    const forgot = () => sheet({
      title: 'Forgot your password?',
      body: `<p>Type your recovery key and choose a new password.</p>` + field('rk', 'Recovery key', '', { ph: 'XXXXX-XXXXX-XXXXX-XXXXX' }) +
        field('np1', 'New password (8 or more)', '', { type: 'password' }) + field('np2', 'Type it again', '', { type: 'password' }),
      saveLabel: 'Open',
      extra: `<button type="button" class="btn text danger" data-s="wipe">Delete cloud data</button>`,
      onSave: (root) => {
        const rk = val(root, 'rk').trim(), np = val(root, 'np1');
        if (!rk) return 'Type your recovery key';
        if (np.length < 8) return 'Use at least 8 characters';
        if (np !== val(root, 'np2')) return "The two passwords don't match";
        fin({ mode: 'rec', secret: rk, newPass: np });
      },
      onClose: () => { if (!switching) fin(null); },
    });
    const wipeHook = (el, close) => el.querySelector('[data-s=wipe]').addEventListener('click', () => {
      switching = true; close();
      sheet({ title: 'Delete the cloud copy?', body: `<p>${esc('The copy in the cloud is deleted for good. The data on this device stays and is uploaded again with a new password and recovery key.')}</p>`, saveLabel: 'Delete', onSave: () => { fin({ wipe: true }); }, onClose: () => fin(null) });
    });
    const main = sheet({
      title: 'Open your data',
      body: `<p>This account's data is locked. Type your encryption password so this device can open it. You will not be asked again on this device.</p>` +
        (bad ? `<p class="err">That did not work. Try again.</p>` : '') + field('pw', 'Encryption password', '', { type: 'password' }),
      saveLabel: 'Open',
      extra: `<button type="button" class="btn text" data-s="forgot">Forgot password</button>`,
      onSave: (root) => { const v = val(root, 'pw'); if (!v) return 'Type your password'; fin({ mode: 'pass', secret: v }); },
      onClose: () => { if (!switching) fin(null); },
    });
    main.el.querySelector('[data-s=forgot]').addEventListener('click', () => {
      switching = true; main.close();
      const f = forgot();
      switching = false;
      wipeHook(f.el, () => { switching = true; f.close(); });
    });
  });
}

/** Asked only when it is really needed: first upload, or two different copies of the same account. */
function syncChoose({ kind, local, cloud }) {
  return new Promise((resolve) => {
    let done = false;
    const fin = (v) => { if (!done) { done = true; resolve(v); } };
    const line = (x) => `${x.txns} records · ${x.commitments} commitments`;
    let body, saveLabel;
    if (kind === 'first-upload') {
      body = `<p>Your data is on this device and nothing is in the cloud yet.</p><div class="pick sel"><b>On this device</b><div class="small">${esc(line(local))}</div></div><p class="small">It is uploaded to your own private space.</p>`;
      saveLabel = 'Upload my data';
      const { el } = sheet({ title: 'First sync', body, saveLabel, onSave: () => { fin('local'); }, onClose: () => fin(null) });
      return el;
    }
    body = `<p>This account already has data in the cloud, and this device has different data. Pick the one you want to keep. Nothing is merged.</p>
      <label class="pick"><input type="radio" name="keep" value="local" checked><div><b>Keep this device</b><div class="small">${esc(line(local))}${local.at ? ' · changed ' + esc(fmtAt(local.at)) : ''}<br>Replaces what is in the cloud</div></div></label>
      <label class="pick"><input type="radio" name="keep" value="cloud"><div><b>Keep the cloud copy</b><div class="small">${esc(line(cloud))} · saved ${esc(fmtAt(cloud.at))}${cloud.device ? ' from ' + esc(cloud.device) : ''}<br>Replaces what is on this device</div></div></label>
      <p class="small">Before anything is replaced, a safety copy of that side is kept.</p>`;
    sheet({ title: 'Your data is different', body, saveLabel: 'Continue', onSave: (el) => { fin($('input[name=keep]:checked', el).value); }, onClose: () => fin(null) });
  });
}

// ---------- Onboarding & lock ----------

// ---------- Country and currency ----------

const isAr = () => R.S().lang === 'AR';
const countryName = (c) => (isAr() ? c[1] : c[2]);
const curName = (code, en) => (isAr() ? CURRENCY_AR[code] || code : en || code);
function countryRows(selected) {
  const row = (key, flag, name, cur, dec) => `<button type="button" class="crow ${key === selected ? 'on' : ''}" data-act="pick-country" data-k="${key}" data-q="${esc((name + ' ' + cur).toLowerCase())}"><span class="flag">${flag}</span><span class="grow">${esc(name)}</span><small>${esc(cur)}${dec === 3 ? ' · 3' : dec === 0 ? ' · 0' : ''}</small></button>`;
  return COUNTRIES.map((c) => row(c[0], FLAGS[c[0]], countryName(c), c[3], c[4])).join('') +
    OTHER_CURRENCIES.map((o) => row('CUR:' + o[0], FLAGS.XX, curName(o[0], o[2]), o[0], o[3])).join('');
}
const countrySearch = () => `<input class="csearch" type="search" placeholder="${isAr() ? 'ابحث عن بلدك' : 'Search your country'}" data-act-input="csearch" autocomplete="off">`;
/** key -> [currency code, country code] for a row key like 'JO' or 'CUR:USD'. */
function pickedCurrency(key) {
  if (key.startsWith('CUR:')) return [key.slice(4), ''];
  const c = COUNTRIES.find((x) => x[0] === key);
  return c ? [c[3], c[0]] : ['EGP', 'EG'];
}

function mainCurrencyBody(s) {
  const c = R.country(), cur = s.baseCurrency;
  const where = c ? `${FLAGS[c[0]]} ${esc(countryName(c))} · ` : '';
  return `<div class="row-flex"><b class="grow">${where}${esc(curName(cur))} (${esc(cur)})</b><button class="btn text" data-act="change-country">${isAr() ? 'تغيير' : 'Change'}</button></div>
    <div class="small">${isAr() ? 'شكل الأرقام' : 'Numbers look like'}: <b>${esc(C.fmt(1234.5678))} ${esc(cur)}</b></div>
    <div class="small">${isAr() ? 'كل حاجة بتتحسب بالعملة دي. أسعار الدولار والذهب من النت بتتحول ليها.' : 'Everything is totalled in this currency. Internet rates and gold are converted to it.'}</div>
    <details class="small pad-t"><summary>${isAr() ? 'عملة تانية' : 'Another currency'}</summary><div class="row-flex">${field('base', 'Code', cur)}<button class="btn text" data-act="save-base">Save</button></div></details>`;
}
function countrySheet() {
  const sel = R.country() ? R.country()[0] : 'CUR:' + R.S().baseCurrency;
  sheet({
    title: isAr() ? 'اختار بلدك' : 'Pick your country',
    body: `${countrySearch()}<div class="clist" id="clist" data-sel="${esc(sel)}">${countryRows(sel)}</div>
      <div class="small">${isAr() ? 'تغيير العملة مش بيحوّل مبالغك القديمة، بيغيّر الرمز وعدد الخانات بس.' : 'Changing the currency does not convert your old amounts, only the symbol and decimals.'}</div>`,
    onSave: (root) => { const [cur, cc] = pickedCurrency($('#clist', root)?.dataset.sel || sel); R.setCurrency(cur, cc); toast('Saved'); },
  });
}

function onboarding() {
  return `<main class="page onboard" id="onb"><h1 class="brand">Pocketta</h1>
    ${chips('lang', [['EN', 'English'], ['AR', 'العربية']], R.S().lang || 'EN', 'seg')}
    <p>Your money for the month: what you can spend today, your bills, installments, gam'eya and loans. Everything stays on your phone.</p>
    <div class="field"><span>${isAr() ? 'بلدك' : 'Your country'}</span><div class="small">${isAr() ? 'منها هنظبط العملة. تقدر تغيّرها بعدين من الإعدادات.' : 'We set your currency from it. You can change it later in Settings.'}</div></div>
    ${countrySearch()}<div class="clist" id="clist" data-sel="EG">${countryRows('EG')}</div>
    ${numField('salary', 'Monthly salary (optional)', null, { suffix: 'EGP' })}
    ${numField('salaryDay', 'Salary day of the month', 1, { int: true })}
    ${numField('start', 'My month starts on day', 1, { int: true })}
    <div class="small">Most people pick the day the salary arrives.</div>
    ${numField('savings', 'Save every month (optional)', null, { suffix: 'EGP' })}
    ${numField('balance', 'How much money do I have now? (optional)', null, { suffix: 'EGP' })}
    <div class="small">Cash, bank and mobile wallet together.</div>
    <button class="btn filled wide" data-act="start">Start</button>
    <div class="small">You can change all of this later in More.</div></main>`;
}

function lockScreen() {
  return `<main class="lock"><h1 class="brand">Pocketta</h1><p>Enter your PIN</p>
    <input id="pin" class="pin" type="password" inputmode="numeric" pattern="[0-9]*" maxlength="8" autocomplete="off">
    <div class="small" id="pin-msg"></div>
    <p class="small muted">Forgot it? Remove the app's website data in Safari settings to start over (this deletes your data).</p></main>`;
}

async function hashPin(pin, salt) {
  const buf = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(`${salt}:${pin}`));
  return [...new Uint8Array(buf)].map((b) => b.toString(16).padStart(2, '0')).join('');
}

function pinSheet() {
  sheet({
    title: 'Set a PIN', body: numField('p1', 'New PIN (4 to 8 digits)', null, { int: true, type: 'password' }) + numField('p2', 'Type it again', null, { int: true, type: 'password' }),
    onSave: (root) => {
      const p1 = val(root, 'p1'), p2 = val(root, 'p2');
      if (!/^\d{4,8}$/.test(p1)) return 'Use 4 to 8 digits';
      if (p1 !== p2) return "The two PINs don't match";
      const salt = crypto.getRandomValues(new Uint32Array(2)).join('-');
      hashPin(p1, salt).then((h) => { R.S().pinSalt = salt; R.setSetting('pinHash', h); toast('PIN lock is on'); });
    },
  });
}

// ---------- After render hooks ----------

function afterRender(c) {
  if (c.r === 'add') {
    addRefresh();
    const q = $('#quick');
    if (!c.id && !c.focused) { c.focused = true; setTimeout(() => q.focus(), 60); }
    q.addEventListener('input', () => { c.st.input = q.value; addRefresh(); });
    q.addEventListener('keydown', (e) => { if (e.key === 'Enter') { e.preventDefault(); saveTxn(); } });
    $('[name="date"]').addEventListener('change', (e) => { const d = C.fromIso(e.target.value); if (d != null) { c.st.date = d; syncChips('day', d); } });
    $('[name="note"]').addEventListener('input', (e) => { c.st.noteField = e.target.value; addRefresh(); });
  }
  if (c.r === 'home') {
    const el = $('#hero-amt');
    if (el) {
      const to = +el.dataset.v, from = ui.heroShown;
      ui.heroShown = to;
      if (from !== to && !matchMedia('(prefers-reduced-motion: reduce)').matches) {
        const t0 = performance.now();
        const tick = (now) => {
          const k = Math.min((now - t0) / 700, 1), e = 1 - Math.pow(1 - k, 3);
          if (!el.isConnected) return;
          el.textContent = money(from + (to - from) * e);
          if (k < 1) requestAnimationFrame(tick);
        };
        requestAnimationFrame(tick);
      }
    }
  }
  if (c.r === 'assistant') { const el = $('#chat'); if (el) window.scrollTo(0, document.body.scrollHeight); }
  if (c.r === 'monthPlanPage' && !ui.mp.scrolled) { ui.mp.scrolled = true; $('#mp-now')?.scrollIntoView({ block: 'center' }); }
  if (c.r === 'spend') {
    const s = $('#spend-search');
    s.addEventListener('input', () => { ui.spendSearch = s.value; $('#spend-list').innerHTML = spendList(spendData().txns); });
  }
}
function syncChips(group, v) { document.querySelectorAll(`[data-group="${group}"] .chip`).forEach((b) => b.classList.toggle('on', b.dataset.val === String(v))); }

async function updateBadge() {
  try {
    const n = R.S().onboarded ? R.dueSoon().length : 0;
    if (navigator.setAppBadge) n ? await navigator.setAppBadge(n) : await navigator.clearAppBadge();
  } catch { /* not supported */ }
}

// ---------- Actions ----------

const actions = {
  'mp-open': (el) => { const ym = +el.dataset.ym; ui.mp.open = ui.mp.open === ym ? null : ym; render(); },
  'mp-save': () => { const v = C.parseNumber($('#monthplan [name="living"]').value); R.setSetting('livingBase', v > 0 ? v : 0); toast('Saved'); },
  'mp-living': (el) => livingSheet(+el.dataset.ym),
  'mp-raise': (el) => raiseSheet(+el.dataset.ym),
  'ai-toggle': () => { ui.chat.settings = !(ui.chat.settings ?? !AI.cfg.key()); render(); },
  'ai-save': async () => {
    const root = $('#aiset'), p = AI.cfg.provider(), key = val(root, 'key').trim();
    if (!key) return toast('Paste your key first');
    AI.cfg.setKey(p, key); AI.cfg.setModel(p, val(root, 'model') || AI.PROVIDERS[p].model);
    // Check the key right away, so a wrong key is explained here and not in the middle of a chat.
    try { await AI.pickModel(p, key); }
    catch (e) { return toast(e instanceof AI.AiError ? e.friendly() : 'Could not reach the company. Check your internet.', 6000); }
    ui.chat.settings = false; ui.chat.session = null; render();
  },
  'ai-remove': () => { AI.cfg.setKey(AI.cfg.provider(), ''); render(); },
  'ai-forget': () => { AI.cfg.clearNotes(); render(); },
  'ai-send': () => aiSend(),
  tab: (el) => go(el.dataset.r),
  open: (el) => go(el.dataset.r, el.dataset.type ? { type: el.dataset.type } : {}),
  back: () => back(),
  'hide-install': () => { ui.hideInstall = true; try { localStorage.setItem('pocketta-hide-install', '1'); } catch { /* fine */ } render(); },
  'toggle-theme': () => R.setSetting('theme', isDark() ? 'LIGHT' : 'DARK'),
  'apply-update': () => location.reload(),
  'toggle-details': () => { ui.details = !ui.details; render(); },
  'hide-guide': () => { ui.hideGuide = true; try { localStorage.setItem('pocketta-hide-guide', '1'); } catch { /* fine */ } render(); },
  'qa-add': () => quickSubmit(),
  'qa-undo': () => { const t = R.byId(R.data.txns, ui.quickSaved?.id); ui.quickSaved = null; if (t) R.deleteTxn(t); else render(); },
  'qa-chip': (el) => { const g = R.suggestions()[+el.dataset.i]; if (g) quickAdd(g.note, g.amount, 0); },
  'gameya-pay': (el) => { const c = R.byId(R.data.circles, +el.dataset.id); if (c) R.payCircle(c); },
  'gameya-payout': (el) => { const c = R.byId(R.data.circles, +el.dataset.id); if (c) R.receivePayout(c); },
  'plan-cat': (el) => { ui.plan.cat = el.dataset.val; $('#sim-out').innerHTML = simOut(); },
  'plan-pct': (el) => { ui.plan.pct = +el.dataset.val; $('#sim-out').innerHTML = simOut(); },
  'conv-swap': () => { const { from, to } = convCompute(); ui.conv.from = to[0]; ui.conv.to = from[0]; render(); },
  'goal-add': () => goalSheet(),
  'piggy-in': () => piggySheet(1),
  'piggy-out': () => piggySheet(-1),
  'goal-new': () => goalEditSheet(null),
  'goal-edit': (el) => goalEditSheet(+el.dataset.id),
  'goal-addto': (el) => goalAddSheet(+el.dataset.id),
  'month-move': () => { const m = ui.monthEnd; if (!m) return; R.S().monthEndDismissed = m.key; R.addToMainGoal(m.goalAmt); toast('Moved to your goal'); },
  'month-piggy': () => { const m = ui.monthEnd; if (!m) return; R.S().monthEndDismissed = m.key; R.piggyAdd(m.left); toast('Put in your piggy bank'); },
  'limit-skip': () => { const c = R.home().cycle; R.setSetting('limitDismissed', String(c.start)); },
  'toggle-limit': (el) => R.setSetting('limitAlert', el.checked),
  'piggy-target': () => piggyTargetSheet(),
  'month-skip': () => { if (ui.monthEnd) R.setSetting('monthEndDismissed', ui.monthEnd.key); },
  'toggle-monthend': (el) => R.setSetting('monthEndReminder', el.checked),
  'gold-add': () => {
    const root = $('#cur');
    const g = num(root, 'gGrams'), pr = num(root, 'gPrice'), k = chipVal(root, 'gkarat') || '21';
    if (!(g > 0) || !(pr > 0)) return toast('Type the grams and the price per gram');
    R.setSetting('goldBuys', [...R.S().goldBuys, { karat: k, grams: g, price: pr, day: C.todayEpoch() }]);
  },
  'gold-del': (el) => R.setSetting('goldBuys', R.S().goldBuys.filter((_, i) => i !== +el.dataset.i)),
  'share-month': () => shareMonth(ui.shareData).then((r) => { if (r === 'saved') toast('Picture saved'); }).catch(() => toast("Couldn't make the picture")),
  'toggle-auto': (el) => { R.setSetting('autoRates', el.checked); if (el.checked) R.refreshRates(true); },
  'auto-cur': (el) => { const l = R.S().autoCurrencies; R.setSetting('autoCurrencies', l.includes(el.dataset.c) ? l.filter((x) => x !== el.dataset.c) : [...l, el.dataset.c]); },
  'update-rates': () => { toast('Updating...'); R.refreshRates(true).then((ok) => toast(ok ? 'Rates updated' : "Couldn't reach the internet")); },
  'save-goal': () => {
    const root = $('#settings');
    R.S().goalName = val(root, 'goalName').trim(); R.S().goalAmount = num(root, 'goalAmount') || 0; R.S().goalSaved = num(root, 'goalSaved') || 0;
    R.changed(); toast('Saved');
  },
  'pay-up': (el) => { const u = ui.homeUpcoming?.[+el.dataset.i]; if (u) { R.payUpcoming(u); toast(u.incoming ? 'Recorded' : 'Paid'); } },
  chip: (el) => {
    const g = el.closest('[data-group]');
    const group = g.dataset.group;
    const multiOff = ['wallet', 'prov'].includes(group) && el.classList.contains('on') && el.closest('.sheet');
    g.querySelectorAll('.chip').forEach((b) => b.classList.toggle('on', b === el && !multiOff));
    const c = cur();
    if (c.r === 'add' && !el.closest('.sheet')) {
      const st = c.st, v = el.dataset.val;
      if (group === 'type' && v !== st.type) { st.type = v; st.categoryId = null; st.parentId = null; st.byHand = false; render(); return; }
      if (group === 'parent') { st.parentId = +v; st.categoryId = +v; st.byHand = true; addRefresh(); }
      if (group === 'sub') { st.categoryId = st.categoryId === +v ? st.parentId : +v; st.byHand = true; addRefresh(); }
      if (group === 'currency') { st.currency = v; addRefresh(); }
      if (group === 'day') { st.date = +v; $('[name="date"]').value = C.isoOf(+v); }
    }
    if (group === 'aiprov') { AI.cfg.setProvider(el.dataset.val); render(); }
    if (group === 'catkind') { ui.catKind = el.dataset.val; render(); }
    if (group === 'theme') { R.setSetting('theme', el.dataset.val); }
    if (group === 'lang') { R.S().lang = el.dataset.val; R.changed(); setTimeout(() => location.reload(), 250); }
  },
  'save-txn': () => saveTxn(),
  'edit-txn': (el) => go('add', { id: +el.dataset.id }),
  'del-txn': () => {
    const t = R.byId(R.data.txns, cur().id);
    if (t) confirmSheet('Delete this?', `${t.note || 'This record'} · ${C.fmt(t.amount)} ${t.currency}`, 'Delete', () => { R.deleteTxn(t); back(); });
  },
  'spend-prev': () => { ui.spendOffset++; ui.spendFilter = null; render(); },
  'spend-next': () => { if (ui.spendOffset > 0) { ui.spendOffset--; ui.spendFilter = null; render(); } },
  'spend-filter': (el) => { ui.spendFilter = String(ui.spendFilter) === el.dataset.k ? null : el.dataset.k; render(); },
  'edit-bill': (el) => billEditor(R.byId(R.data.bills, +el.dataset.id)),
  'pay-bill': (el) => { const b = R.byId(R.data.bills, +el.dataset.id); if (!b) return; if (b.variable) payVariableBill(b); else { R.payBill(b); toast(`${b.name} paid`); } },
  'skip-bill': (el) => { const b = R.byId(R.data.bills, +el.dataset.id); if (b) { R.skipBill(b); document.querySelector('.scrim [data-s="cancel"]')?.click(); toast('Skipped to the next date'); } },
  'commit-tab': (el) => { ui.commitTab = +el.dataset.i; render(); },
  'commit-filter': (el) => { ui.cFilter = el.dataset.v; ui.cDay = null; render(); },
  'commit-day': (el) => { const d = +el.dataset.d; ui.cDay = ui.cDay === d ? null : d; render(); },
  'commit-prev': () => { ui.cOff = (ui.cOff || 0) - 1; ui.cDay = null; render(); },
  'commit-next': () => { ui.cOff = (ui.cOff || 0) + 1; ui.cDay = null; render(); },
  'commit-add-day': (el) => go('add', { date: +el.dataset.d }),
  'commit-add': () => commitAddSheet(),
  'bulk-open': () => bulkSheet(),
  'bulk-undo': () => { if (ui.bulkUndo) { R.undoPayGroup(ui.bulkUndo.undo); ui.bulkUndo = null; toast('Undone'); render(); } },
  'bulk-dismiss': () => { ui.bulkUndo = null; render(); },
  'open-tag': (el) => go('tagDetail', { name: el.dataset.name }),
  'tag-menu': (el) => tagMenu(el.dataset.name),
  'tag-pick': (el) => {
    const inp = $('[name="tags"]');
    const list = parseTags(inp.value);
    const n = el.dataset.name;
    inp.value = (list.includes(n) ? list.filter((x) => x !== n) : [...list, n]).join(', ');
  },
  'commit-icon': (el) => commitIconSheet({ kind: el.dataset.k, refId: +el.dataset.id, name: el.dataset.name }),
  'commit-pay': (el) => {
    const id = +el.dataset.id;
    if (el.dataset.k === 'BILL' || el.dataset.k === 'SUB') actions['pay-bill']({ dataset: { id } });
    else if (el.dataset.k === 'CIRCLE') actions['pay-circle']({ dataset: { id } });
    else actions['pay-inst']({ dataset: { id } });
  },
  'commit-open': (el) => {
    const id = +el.dataset.id, k = el.dataset.k;
    if (k === 'BILL' || k === 'SUB') actions['edit-bill']({ dataset: { id } });
    else if (k === 'CIRCLE') actions['edit-circle']({ dataset: { id } });
    else if (k === 'LOAN') actions['open-loan']({ dataset: { id } });
    else actions['edit-inst']({ dataset: { id } });
  },
  'edit-balance': () => sheet({
    title: 'How much money do I have now?',
    body: numField('bal', 'My money', Math.round(R.wealth().cash), { suffix: base(), keepZero: true }) + `<div class="small">Type the real total of your cash, bank and mobile wallet. Pocketta fixes the rest.</div>`,
    onSave: (root) => { const v = num(root, 'bal'); if (v == null || isNaN(v)) return 'Type a number'; R.setBalance(v); },
  }),
  'edit-inst': (el) => instEditor(R.byId(R.data.installments, +el.dataset.id)),
  'pay-inst': (el) => { const i = R.byId(R.data.installments, +el.dataset.id); if (i) { R.payInstallment(i); toast('Paid'); } },
  'edit-circle': (el) => circleEditor(R.byId(R.data.circles, +el.dataset.id)),
  'pay-circle': (el) => { const c = R.byId(R.data.circles, +el.dataset.id); if (c) { R.payCircle(c); toast('Share paid'); } },
  payout: (el) => { const c = R.byId(R.data.circles, +el.dataset.id); if (c) { R.receivePayout(c); toast('Payout recorded'); } },
  'new-loan': () => newLoan(),
  'open-loan': (el) => { const l = R.byId(R.data.loans, +el.dataset.id); if (l) loanDialog(l); },
  'save-income': () => {
    const root = $('#income');
    R.S().salary = num(root, 'salary') || 0;
    R.S().salaryDay = Math.min(Math.max(parseInt(val(root, 'salaryDay')) || 1, 1), 31);
    R.S().savingsPerMonth = num(root, 'savings') || 0;
    R.S().household = num(root, 'household') || 0;
    R.changed();
    R.ensureSalary();
    toast('Saved');
  },
  'edit-cat': (el) => catEditor(R.byId(R.data.categories, +el.dataset.id)),
  'add-sub': (el) => catEditor(null, +el.dataset.id),
  'pick-country': (el) => {
    const list = el.closest('.clist'), key = el.dataset.k;
    list.dataset.sel = key;
    list.querySelectorAll('.crow').forEach((b) => b.classList.toggle('on', b === el));
    const cur = pickedCurrency(key)[0];
    document.querySelectorAll('#onb .input-wrap em').forEach((e) => { e.textContent = cur; });
  },
  'change-country': () => countrySheet(),
  'save-base': () => { const v = val($('#cur'), 'base').trim().toUpperCase().slice(0, 4); if (v) { R.setCurrency(v, ''); toast('Saved'); } },
  'edit-rate': (el) => { const root = $('#cur'); $('[name="code"]', root).value = el.dataset.c; $('[name="rate"]', root).value = R.S().rates[el.dataset.c]; },
  'del-rate': (el) => { const r = { ...R.S().rates }; delete r[el.dataset.c]; R.setSetting('rates', r); },
  'save-rate': () => {
    const root = $('#cur');
    const code = val(root, 'code').trim().toUpperCase().slice(0, 4), r = num(root, 'rate');
    if (!code || !(r > 0) || code === base()) return toast('Type a code and a rate');
    R.setSetting('rates', { ...R.S().rates, [code]: r });
  },
  'save-start': () => { R.setSetting('cycleStart', Math.min(Math.max(parseInt(val($('#settings'), 'start')) || 1, 1), 31)); toast('Saved'); },
  'toggle-alert': (el) => R.setSetting('budgetAlert', el.checked),
  'pin-set': () => pinSheet(),
  'pin-off': () => confirmSheet('Turn off the PIN?', 'Anyone with your phone can open Pocketta.', 'Turn off', () => { R.S().pinSalt = ''; R.setSetting('pinHash', ''); }),
  'sync-in': () => Sync.signIn(),
  'download-text': (el) => {
    try {
      const txt = 'Pocketta recovery key\n\n' + el.dataset.text + '\n\nKeep this file somewhere safe (not only on this device). With it you can open your cloud backup if you forget your encryption password. Anyone who has it and your Google account can open your backup.\n';
      const a = document.createElement('a');
      a.href = URL.createObjectURL(new Blob([txt], { type: 'text/plain' }));
      a.download = 'pocketta-recovery-key.txt';
      document.body.appendChild(a); a.click(); a.remove();
      setTimeout(() => URL.revokeObjectURL(a.href), 4000);
      toast('Saved to your downloads');
    } catch (e) { toast('Could not save the file'); }
  },
  'copy-text': (el) => { navigator.clipboard?.writeText(el.dataset.text).then(() => toast('Copied')).catch(() => {}); },
  'sync-now': () => Sync.syncNow(),
  'sync-out': () => confirmSheet('Sign out?', 'Your data stays on this device. Sync stops until you sign in again.', 'Sign out', () => Sync.signOut()),
  'sync-delete': () => confirmSheet('Delete your data from the cloud?', 'This removes the copy in the cloud and its safety copy. Your data on this device is not touched.', 'Delete from cloud', () => { Sync.deleteCloud().then(() => toast('Deleted from the cloud')).catch(() => toast('Could not delete. Check your internet.')); }),
  backup: () => backup(),
  restore: () => $('#restore-file').click(),
  wipe: () => confirmSheet('Delete all data?', 'Everything in Pocketta on this phone will be deleted. Save a backup first if you may need it.', 'Delete all', () => { R.wipe(); ui.stack = [{ r: 'home' }]; render(); }),
  start: () => {
    const root = $('#onb');
    const [cur, cc] = pickedCurrency($('#clist')?.dataset.sel || 'EG');
    R.setCurrency(cur, cc);
    R.ensureSeeded();
    const s = R.S();
    s.cycleStart = Math.min(Math.max(parseInt(val(root, 'start')) || 1, 1), 31);
    s.salary = num(root, 'salary') || 0;
    s.salaryDay = Math.min(Math.max(parseInt(val(root, 'salaryDay')) || 1, 1), 31);
    s.savingsPerMonth = num(root, 'savings') || 0;
    const bal = num(root, 'balance');
    if (bal > 0) R.setBalance(bal);
    s.onboarded = true;
    R.changed();
    R.ensureSalary();
  },
};

async function backup() {
  const text = R.exportJson();
  const name = `pocketta-backup-${C.isoOf(C.todayEpoch())}.json`;
  const file = new File([text], name, { type: 'application/json' });
  try {
    if (navigator.canShare && navigator.canShare({ files: [file] })) { await navigator.share({ files: [file], title: 'Pocketta backup' }); return; }
  } catch (e) { if (e.name === 'AbortError') return; }
  const a = document.createElement('a');
  a.href = URL.createObjectURL(file);
  a.download = name;
  document.body.appendChild(a);
  a.click();
  setTimeout(() => { URL.revokeObjectURL(a.href); a.remove(); }, 1000);
}

document.addEventListener('change', (e) => {
  if (e.target.id !== 'restore-file') return;
  const f = e.target.files[0];
  if (!f) return;
  f.text().then((text) => text.startsWith('SQLite format 3') ? toast("That is the phone's own backup (.db). On the phone use Settings > Save a copy for the web app, then restore that .json file here.") : confirmSheet('Restore this backup?', 'Everything in the app now will be replaced by the backup.', 'Restore', () => {
    if (R.importJson(text)) { ui.stack = [{ r: 'home' }]; toast('Backup restored'); render(); } else toast("That file isn't a Pocketta backup");
  }));
  e.target.value = '';
});

document.addEventListener('click', (e) => {
  const el = e.target.closest('[data-act]');
  if (!el || el.disabled) return;
  if (el.tagName === 'INPUT') return; // checkboxes handled on change
  e.stopPropagation();
  actions[el.dataset.act]?.(el, e);
});
document.addEventListener('change', (e) => {
  const a = e.target.dataset?.act;
  if (a === 'toggle-alert' || a === 'toggle-auto' || a === 'toggle-monthend') actions[a](e.target);
  if (e.target.id === 'conv-from') { ui.conv.from = e.target.value; convRefresh(); }
  if (e.target.id === 'conv-to') { ui.conv.to = e.target.value; convRefresh(); }
});
document.addEventListener('input', (e) => {
  if (e.target.id === 'qa') { ui.quickText = e.target.value; ui.quickHint = false; }
  if (e.target.id === 'ai-in') ui.chat.draft = e.target.value;
  if (e.target.id === 'conv-amt') { ui.conv.amount = e.target.value; convRefresh(); }
  if (e.target.name === 'price' && cur().r === 'plan') { ui.plan.price = e.target.value; $('#afford-out').innerHTML = affordOut(); }
});
document.addEventListener('keydown', (e) => {
  if (e.key === 'Enter' && e.target.id === 'qa') { e.preventDefault(); quickSubmit(); }
  if (e.key === 'Enter' && e.target.id === 'ai-in') { e.preventDefault(); aiSend(); }
});

document.addEventListener('input', async (e) => {
  if (e.target.id !== 'pin') return;
  const v = e.target.value;
  const s = R.S();
  if (v.length < 4) return;
  if ((await hashPin(v, s.pinSalt)) === s.pinHash) { ui.locked = false; render(); }
  else if (v.length >= 8) { e.target.value = ''; $('#pin-msg').textContent = 'Wrong PIN'; }
});

document.addEventListener('input', (e) => {
  if (!e.target.classList || !e.target.classList.contains('csearch')) return;
  const q = e.target.value.trim().toLowerCase();
  document.querySelectorAll('.clist .crow').forEach((b) => { b.hidden = !!q && !b.dataset.q.includes(q); });
});
document.addEventListener('visibilitychange', () => {
  if (document.hidden) { ui.hiddenAt = Date.now(); return; }
  if (R.S().pinHash && ui.hiddenAt && Date.now() - ui.hiddenAt > 60000) { ui.locked = true; document.querySelectorAll('.scrim').forEach((x) => x.remove()); }
  R.ensureSalary();
  render();
  R.refreshRates();
});
window.addEventListener('popstate', () => { if (ui.stack.length > 1) back(); });

// Re-render on data changes, except while typing on the Add page.
R.onChange(() => { if (cur().r !== 'add') render(); });

(async function main() {
  await R.load();
  const returned = Sync.handleRedirect();
  if (R.S().lang === 'AR') startArabic();
  applyTheme();
  ui.locked = !!R.S().pinHash;
  // Long-press shortcuts on the home-screen icon: ?go=add or ?go=income
  const goto = new URLSearchParams(location.search).get('go');
  if (goto && R.S().onboarded && !ui.locked) { ui.stack = [{ r: 'home' }, goto === 'income' ? { r: 'add', type: 'INCOME' } : { r: 'add' }]; history.replaceState(null, '', location.pathname); }
  render();
  Sync.onStatus(() => { if (cur().r === 'settings' && !ui.locked && !document.querySelector('.scrim')) render(); });
  Sync.start({ choose: syncChoose, setup: syncSetup, unlock: syncUnlock, done: () => { if (!ui.locked && cur().r !== 'add' && !document.querySelector('.scrim')) render(); } });
  if (returned) { ui.stack = [{ r: 'home' }, { r: 'settings' }]; render(); }
  R.refreshRates();
  if ('serviceWorker' in navigator && location.protocol === 'https:') {
    // A new version installs in the background; when it takes over, say so and offer the update.
    const had = !!navigator.serviceWorker.controller;
    navigator.serviceWorker.addEventListener('controllerchange', () => { if (had) { ui.updateReady = true; if (!ui.locked) render(); toast('A new version is ready. Tap Update on Today.', 6000); } });
    navigator.serviceWorker.register('./sw.js').then((reg) => {
      const check = () => reg.update().catch(() => {});
      check();
      document.addEventListener('visibilitychange', () => { if (!document.hidden) check(); });
      setInterval(check, 30 * 60 * 1000);
    }).catch(() => {});
  }
})();
