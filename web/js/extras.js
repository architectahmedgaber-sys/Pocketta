// Pocketta v3 pure logic. No DOM, no storage. Kotlin ports live in core/src/main/kotlin/app/pocketta/core/ (see the brief).
import { epochOf, ymd, addMonths } from './core.js';

const r2 = (v) => Math.round(v * 100) / 100;

// ---- 1. Category budgets: alert levels (80 = near, 100 = over) ----
export function limitAlertLevel(spent, limit) {
  if (!(limit > 0)) return 0;
  const sc = Math.round(spent * 100), lc = Math.round(limit * 100); // whole cents: no float edge cases
  if (sc > lc) return 100;
  if (sc * 5 >= lc * 4) return 80;
  return 0;
}
// State text: "<cycleStartEpochDay>|<id>:<level>,<id>:<level>". Old state "<day>|1,2" (no colon) means level 100.
export function parseAlertState(s) {
  const [d, rest = ''] = String(s || '').split('|');
  const day = Number(d);
  if (!Number.isFinite(day) || d === '') return { day: 0, levels: {} };
  const levels = {};
  for (const part of rest.split(',')) {
    if (!part) continue;
    const [id, lv] = part.split(':');
    if (!Number.isFinite(Number(id))) continue;
    levels[id] = lv === undefined ? 100 : Number(lv);
  }
  return { day, levels };
}
export function formatAlertState(day, levels) {
  const ids = Object.keys(levels).sort((a, b) => Number(a) - Number(b));
  return `${day}|` + ids.map((id) => `${id}:${levels[id]}`).join(',');
}
// items: [{id, spent, limit}]. Returns {fresh: [{id, level}], state} . A category alerts again only when its level goes up.
export function freshLimitAlerts(cycleDay, state, items) {
  const cur = parseAlertState(state);
  const known = cur.day === cycleDay ? { ...cur.levels } : {};
  const fresh = [];
  for (const it of items) {
    const lv = limitAlertLevel(it.spent, it.limit);
    if (lv > (known[it.id] || 0)) { fresh.push({ id: it.id, level: lv }); known[it.id] = lv; }
  }
  return { fresh, state: formatAlertState(cycleDay, known) };
}
// Budgets card numbers: categories that have a limit.
export function budgetsSummary(items) {
  const withLimit = items.filter((i) => i.limit > 0);
  const over = withLimit.filter((i) => limitAlertLevel(i.spent, i.limit) === 100).length;
  const near = withLimit.filter((i) => limitAlertLevel(i.spent, i.limit) === 80).length;
  return { total: withLimit.length, over, near, ok: withLimit.length - over - near };
}

// ---- 2. Month-end forecast ----
// left = budget - spent - unpaid commitments. avg = everyday spending per elapsed day. daysLeft counts today.
// Returns days from today until the money runs out, or null when it lasts to the end of the cycle.
export function runOutIn(left, avg, daysLeft) {
  if (!(avg > 0)) return null;
  if (left <= 0) return 0;
  const d = Math.floor(left / avg);
  return d >= daysLeft ? null : d;
}
// forecastDiff = budget - forecastSpend (computeBudget). Tight = under by less than 10% of the budget.
export function forecastStatus(forecastDiff, budget) {
  if (forecastDiff < 0) return 'over';
  if (budget > 0 && forecastDiff < budget * 0.1) return 'tight';
  return 'ok';
}

// ---- 3. Recurring bills that record themselves ----
// bills: [{id, active, autoPost, variable, nextDue, frequency, everyN, anchorDay}], nextFn = nextDue(ed, freq, everyN, anchor).
// Returns [{billId, day}] in order: every occurrence due on or before today, dated on its due date, at most 12 per bill per run.
export function autoPostPlan(bills, today, nextFn) {
  const out = [];
  for (const b of bills) {
    if (!b.active || !b.autoPost || b.variable) continue;
    let due = b.nextDue, n = 0;
    while (due <= today && n < 12) { out.push({ billId: b.id, day: due }); due = nextFn(due, b.frequency, b.everyN, b.anchorDay); n++; }
  }
  return out;
}

// ---- 4. Savings goal with a deadline ----
// deadline and today are epoch days. Whole calendar months between them, at least 1.
export function monthsUntil(today, deadline) {
  const a = ymd(today), b = ymd(deadline);
  return Math.max(1, (b.y * 12 + b.m) - (a.y * 12 + a.m));
}
export function goalNeed(target, saved, monthly, deadline, today) {
  if (target > 0 && saved >= target) return { status: 'reached', needed: 0, months: 0 };
  if (!deadline) return { status: 'nodate', needed: null, months: null };
  const months = monthsUntil(today, deadline);
  const needed = Math.ceil((target - saved) / months);
  return { status: monthly >= needed ? 'ontrack' : 'behind', needed, months };
}
// Goal lines: id, name, target, saved, monthly, deadline (6th field, epoch day, empty = none). 5-field lines still read.
export function parseGoals(s) {
  return String(s || '').split('\n').map((line) => {
    const p = line.split('\t');
    if (p.length !== 5 && p.length !== 6) return null;
    const id = Number(p[0]), t = Number(p[2]), sv = Number(p[3]), m = Number(p[4]);
    if (![id, t, sv, m].every(Number.isFinite) || p[0] === '' || p[2] === '' || p[3] === '' || p[4] === '') return null;
    const dl = p.length === 6 && p[5] !== '' && Number.isFinite(Number(p[5])) ? Number(p[5]) : null;
    return { id, name: p[1], target: t, saved: sv, monthly: m, deadline: dl };
  }).filter(Boolean);
}
export function formatGoals(list) {
  return list.map((g) => [g.id, g.name.replace(/[\t\n]/g, ' ').trim(), g.target, g.saved, g.monthly, g.deadline ?? ''].join('\t')).join('\n');
}

// ---- 5. Monthly report ----
// txns: [{type, base, cat (top-level category name or ''), note, epochDay, source}]. Piggy and loan money and transfers are not spending.
export function monthReport(txns, prevTxns, cycleLengthDays) {
  const spendOf = (list) => list.filter((t) => t.type === 'EXPENSE' && t.source !== 'PIGGY' && t.source !== 'LOAN');
  const exp = spendOf(txns);
  const spent = r2(exp.reduce((a, t) => a + t.base, 0));
  const income = r2(txns.filter((t) => t.type === 'INCOME' && t.source !== 'PIGGYOUT' && t.source !== 'LOAN').reduce((a, t) => a + t.base, 0));
  const prevSpent = r2(spendOf(prevTxns).reduce((a, t) => a + t.base, 0));
  const byCat = new Map();
  for (const t of exp) byCat.set(t.cat || '', (byCat.get(t.cat || '') || 0) + t.base);
  const cats = [...byCat.entries()].sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0])).slice(0, 3)
    .map(([name, v]) => ({ name, amount: r2(v), share: spent > 0 ? Math.round(v / spent * 100) : 0 }));
  const top = exp.slice().sort((a, b) => b.base - a.base || a.epochDay - b.epochDay).slice(0, 3).map((t) => ({ note: t.note, amount: r2(t.base), day: t.epochDay }));
  const byDay = new Map();
  for (const t of exp) byDay.set(t.epochDay, (byDay.get(t.epochDay) || 0) + t.base);
  let bestDay = null;
  for (const [day, v] of byDay) if (!bestDay || v > bestDay.amount || (v === bestDay.amount && day < bestDay.day)) bestDay = { day, amount: r2(v) };
  return {
    spent, income, net: r2(income - spent), prevSpent,
    changePct: prevSpent > 0 ? Math.round((spent - prevSpent) / prevSpent * 100) : null,
    perDay: cycleLengthDays > 0 ? r2(spent / cycleLengthDays) : 0,
    topCategories: cats, topExpenses: top, biggestDay: bestDay,
  };
}

// ---- 7. CSV export ----
const CSV_HEAD = ['Date', 'Type', 'Amount', 'Currency', 'Rate', 'Amount (base)', 'Category', 'Wallet', 'Note', 'Tags'];
const iso = (ed) => { const { y, m, d } = ymd(ed); return `${y}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`; };
function cell(v) {
  let s = String(v ?? '');
  if (/^[=+\-@\t\r]/.test(s)) s = "'" + s; // a spreadsheet must not run it as a formula
  return /[",\n\r]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s;
}
// rows: [{epochDay, type, amount, currency, rate, categoryLabel, walletName, note, tags: []}] already sorted by the caller (date ascending, then id).
export function toCsv(rows, baseCurrency) {
  const head = CSV_HEAD.map((h, i) => (i === 5 ? `Amount (${baseCurrency})` : h));
  const lines = [head.map(cell).join(',')];
  for (const r of rows) {
    lines.push([iso(r.epochDay), r.type, r.amount.toFixed(2), r.currency, String(r.rate), (r.amount * r.rate).toFixed(2), r.categoryLabel, r.walletName, r.note, r.tags.join('|')].map(cell).join(','));
  }
  return '\uFEFF' + lines.join('\r\n') + '\r\n';
}

// ---- 8. Lock grace ----
export const LOCK_GRACE_OPTIONS = [0, 30, 60, 300];
export function shouldRelock(leftAtMs, nowMs, graceSec) {
  if (!leftAtMs) return true;
  return nowMs - leftAtMs >= graceSec * 1000;
}

// ---- 10. Search and filters ----
// f: {q, types:[], catIds:[], walletId, min, max, from, to, need, tag}. All given parts must match. Returns {list, total}.
// t: {id, type, base, note, categoryId, walletId, epochDay, need, tags: []}. catName(id) gives the category label (parent and child).
// 60 -> "60", 60.5 -> "60.5", 60.25 -> "60.25" (whole cents, no trailing zeros)
export const amountText = (v) => String(Math.round(v * 100) / 100);
export function filterTxns(txns, f, catName) {
  const q = String(f.q || '').trim().toLowerCase();
  const list = txns.filter((t) => {
    if (f.types && f.types.length && !f.types.includes(t.type)) return false;
    if (f.catIds && f.catIds.length && !f.catIds.includes(t.categoryId)) return false;
    if (f.walletId && t.walletId !== f.walletId) return false;
    if (f.min != null && t.base < f.min) return false;
    if (f.max != null && t.base > f.max) return false;
    if (f.from != null && t.epochDay < f.from) return false;
    if (f.to != null && t.epochDay > f.to) return false;
    if (f.need && t.need !== f.need) return false;
    if (f.tag && !t.tags.includes(f.tag)) return false;
    if (q) {
      const hay = [t.note, catName(t.categoryId), t.tags.join(' '), amountText(t.base)].join(' ').toLowerCase();
      if (!hay.includes(q)) return false;
    }
    return true;
  }).sort((a, b) => b.epochDay - a.epochDay || b.id - a.id);
  const total = r2(list.filter((t) => t.type === 'EXPENSE').reduce((a, t) => a + t.base, 0));
  return { list, total };
}
