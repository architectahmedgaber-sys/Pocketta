// Pure money and date math, ported from the Android app's :core module.
import { decimalsOf } from './countries.js';
// Dates are "epoch days": whole days since 1970-01-01, in the phone's local calendar.

const DAY = 86400000;

export function epochOf(y, m, d) { return Math.round(Date.UTC(y, m - 1, d) / DAY); }
export function ymd(ed) {
  const t = new Date(ed * DAY);
  return { y: t.getUTCFullYear(), m: t.getUTCMonth() + 1, d: t.getUTCDate(), wd: t.getUTCDay() };
}
export function todayEpoch(now = new Date()) { return epochOf(now.getFullYear(), now.getMonth() + 1, now.getDate()); }
export function daysInMonth(y, m) { return new Date(Date.UTC(y, m, 0)).getUTCDate(); }
export function isoOf(ed) { const { y, m, d } = ymd(ed); return `${y}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`; }
export function fromIso(s) {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(s || '');
  return m ? epochOf(+m[1], +m[2], +m[3]) : null;
}

/** Same day-of-month [anchor] (clamped to short months), [months] after [ed]'s month. */
export function addMonths(ed, months, anchor) {
  const { y, m, d } = ymd(ed);
  const idx = y * 12 + (m - 1) + months;
  const ny = Math.floor(idx / 12), nm = (idx % 12) + 1;
  const day = Math.min(Math.max(anchor ?? d, 1), daysInMonth(ny, nm));
  return epochOf(ny, nm, day);
}

const MONTHS = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];
const WD = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
export const fmtMonth = (ed) => MONTHS[ymd(ed).m - 1];
export const fmtDay = (ed) => { const { m, d } = ymd(ed); return `${MONTHS[m - 1].slice(0, 3)} ${d}`; };
export const fmtMonthYear = (ed) => { const { y, m } = ymd(ed); return `${MONTHS[m - 1].slice(0, 3)} ${y}`; };
export const fmtWeekday = (ed) => { const { wd } = ymd(ed); return `${WD[wd]}, ${fmtDay(ed)}`; };

// ---------- Cycle ----------

export function startIn(y, m, startDay) { return epochOf(y, m, Math.min(Math.max(startDay, 1), 31, daysInMonth(y, m))); }

/** The budget "month" that contains [today], for a plan that renews on [startDay]. */
export function cycleFor(today, startDay) {
  const { y, m } = ymd(today);
  const s = startIn(y, m, startDay);
  const shift = (k) => { const i = y * 12 + (m - 1) + k; return [Math.floor(i / 12), (i % 12) + 1]; };
  if (today >= s) { const [ny, nm] = shift(1); return mkCycle(s, startIn(ny, nm, startDay)); }
  const [py, pm] = shift(-1);
  return mkCycle(startIn(py, pm, startDay), s);
}
function mkCycle(start, nextStart) {
  return {
    start, nextStart, end: nextStart - 1, length: nextStart - start,
    daysLeftIncluding: (t) => Math.max(nextStart - t, 1),
    dayIndex: (t) => t - start + 1,
    contains: (d) => d >= start && d < nextStart,
  };
}
export function previousCycle(c, startDay) { return cycleFor(c.start - 1, startDay); }

// ---------- Money ----------

let curDec = 2;
/** Called when the main currency is known or changes, so every amount shows that currency's decimals. */
export function setCurrencyFormat(cur) { curDec = decimalsOf(cur); }
export function fmt(v) {
  const f = 10 ** curDec;
  return (Math.round(v * f) / f).toLocaleString('en-US', { maximumFractionDigits: curDec });
}
// Whole numbers, except for currencies worth a lot per unit (dinar, 3 decimals) where fils matter.
export function short(v) {
  return curDec >= 3 ? fmt(v) : Math.round(v).toLocaleString('en-US');
}

// ---------- Recurrence ----------

export const FREQS = [
  ['WEEKLY', 'Weekly'], ['MONTHLY', 'Monthly'], ['QUARTERLY', 'Every 3 months'],
  ['SEMIANNUAL', 'Every 6 months'], ['ANNUAL', 'Yearly'], ['EVERY_N_DAYS', 'Every N days'],
];
const FREQ_MONTHS = { MONTHLY: 1, QUARTERLY: 3, SEMIANNUAL: 6, ANNUAL: 12 };

export function nextDue(ed, freq, everyN, anchor) {
  if (freq === 'WEEKLY') return ed + 7;
  if (freq === 'EVERY_N_DAYS') return ed + Math.max(everyN || 1, 1);
  return addMonths(ed, FREQ_MONTHS[freq] || 1, anchor ?? ymd(ed).d);
}
export function occurrences(first, freq, everyN, from, toExcl, anchor) {
  const out = [];
  let d = first, guard = 0;
  while (d < toExcl && guard < 2000) {
    if (d >= from) out.push(d);
    d = nextDue(d, freq, everyN, anchor ?? ymd(first).d);
    guard++;
  }
  return out;
}
export function monthlyShare(amount, freq, everyN) {
  switch (freq) {
    case 'WEEKLY': return amount * 52 / 12;
    case 'QUARTERLY': return amount / 3;
    case 'SEMIANNUAL': return amount / 6;
    case 'ANNUAL': return amount / 12;
    case 'EVERY_N_DAYS': return amount * (365.25 / 12) / Math.max(everyN || 1, 1);
    default: return amount;
  }
}
export const isSpread = (freq, everyN) => ['QUARTERLY', 'SEMIANNUAL', 'ANNUAL'].includes(freq) || (freq === 'EVERY_N_DAYS' && everyN > 31);
export const describeFreq = (freq, everyN) => freq === 'EVERY_N_DAYS' ? `Every ${everyN} days` : (FREQS.find((f) => f[0] === freq) || FREQS[1])[1];

// ---------- Plans ----------

export function plan(amount, total, paid, firstDue) {
  const anchor = ymd(firstDue).d;
  const dueOf = (i) => addMonths(firstDue, i, anchor);
  const done = paid >= total;
  return {
    amount, total, paid, done, dueOf,
    remainingCount: Math.max(total - paid, 0),
    remainingAmount: Math.max(total - paid, 0) * amount,
    nextDue: done ? null : dueOf(paid),
    endDate: dueOf(Math.max(total - 1, 0)),
    progress: total <= 0 ? 0 : paid / total,
    unpaidDueIn(from, toExcl) {
      const out = [];
      for (let i = paid; i < total; i++) { const d = dueOf(i); if (d >= toExcl) break; if (d >= from) out.push(d); }
      return out;
    },
  };
}
export function circle(c) {
  const p = plan(c.share, c.months, c.paid, c.firstDue);
  return {
    plan: p, payout: c.share * c.months,
    payoutDate: p.dueOf(Math.min(Math.max(c.myTurn - 1, 0), Math.max(c.months - 1, 0))),
    currentMonth: Math.min(c.paid + 1, c.months),
  };
}

// ---------- Budget ----------

export function computeBudget(i) {
  const budget = i.incomeReceived + i.salaryExpected - i.setAside;
  const spent = i.spentBeforeToday + i.spentToday;
  const daysLeft = i.cycle.daysLeftIncluding(i.today);
  const available = budget - i.spentBeforeToday - i.commitments;
  const daily = available / daysLeft;
  const dayIndex = Math.max(i.cycle.dayIndex(i.today), 1);
  const avg = i.everydaySpent / dayIndex;
  const forecast = spent + i.commitments + avg * (daysLeft - 1);
  const spentFraction = budget <= 0 ? 0 : Math.min(Math.max(spent / budget, 0), 1);
  const committedFraction = budget <= 0 ? 0 : Math.min(Math.max(i.commitments / budget, 0), 1 - spentFraction);
  // Flexible money: what is left after every commitment (paid or not) and the household shopping reserve.
  // It only goes down when you spend on everyday things, never by a fixed amount per day.
  const household = i.household || 0;
  const hs = i.householdSpent || 0;
  const flexSpent = Math.max(i.everydaySpent - hs + Math.max(hs - household, 0), 0);
  const flexTotal = budget - (spent - i.everydaySpent) - i.commitments - household;
  const flexLeft = flexTotal - flexSpent;
  const flexSpentFraction = flexTotal > 0 ? Math.min(Math.max(flexSpent / flexTotal, 0), 1) : (flexSpent > 0 ? 1 : 0);
  const elapsedFraction = Math.min(Math.max(dayIndex / Math.max(i.cycle.length, 1), 0), 1);
  return {
    flexTotal, flexSpent, flexLeft, flexSpentFraction, elapsedFraction, household,
    budget, spent, committed: i.commitments, left: budget - spent - i.commitments, daysLeft,
    dailyAllowance: daily, leftToday: daily - i.spentToday, forecastSpend: forecast, forecastDiff: budget - forecast,
    spentFraction, committedFraction,
  };
}
/**
 * Saving challenge: every 7 days you spend less than the weekly flexible amount. The streak is the number of days in a row
 * (ending today) for which the 7 days up to that day stayed under it. perDay = {epochDay: flexible spending}.
 */
export function streak(perDay, today, weeklyTarget, firstDay) {
  if (!(weeklyTarget > 0) || firstDay == null || firstDay > today) return null;
  const trailing = (d) => { let t = 0; for (let k = 0; k < 7; k++) t += perDay[d - k] || 0; return t; };
  let days = 0;
  for (let d = today; d >= firstDay; d--) { if (trailing(d) <= weeklyTarget) days++; else break; }
  let best = 0, run = 0;
  for (let d = Math.max(firstDay, today - 120); d <= today; d++) { if (trailing(d) <= weeklyTarget) { run++; best = Math.max(best, run); } else run = 0; }
  const weekSpent = trailing(today);
  return { days, best, weekSpent, target: weeklyTarget, weekLeft: weeklyTarget - weekSpent };
}

/** The most useful single line for the Friday summary when there is no AI key: based only on the numbers. */
export function weeklyTip(spent, previous, topCategory, flexLeft, daysLeft) {
  if (spent <= 0) return 'No spending recorded this week. Add what you spend so the summary has something to say.';
  if (flexLeft < 0) return 'You are over the flexible amount for this month: skip anything you can wait on until the next salary.';
  if (previous > 0 && spent > previous * 1.2 && topCategory) return `Spending is up on last week. ${topCategory} is the place to look first.`;
  if (previous > 0 && spent < previous * 0.9) return 'Good week: you spent less than the week before. Keep the same pace.';
  if (topCategory && daysLeft > 0) return `${topCategory} took the biggest share. A small limit on it would keep the rest of the month calm.`;
  return 'A steady week. Keep logging expenses and the month stays under control.';
}

export function level(value, limit) {
  if (limit <= 0) return 'ok';
  if (value > limit) return 'over';
  if (value >= limit * 0.8) return 'near';
  return 'ok';
}

// ---------- Quick text: "koshary 60", "كشري ٦٠", "1.5k rent" ----------

export function normalizeDigits(s) {
  let out = '';
  for (const c of s) {
    const code = c.charCodeAt(0);
    if (code >= 0x660 && code <= 0x669) out += String(code - 0x660);
    else if (code >= 0x6f0 && code <= 0x6f9) out += String(code - 0x6f0);
    else if (c === '٫') out += '.';
    else if (c === '٬' || c === ',') out += ' ';
    else out += c;
  }
  return out;
}
export function parseQuick(input) {
  const s = normalizeDigits(input).replace(/(\d) (?=\d{3}\b)/g, '$1');
  const m = /(\d+(?:\.\d+)?)([kK](?!\p{L}))?/u.exec(s);
  if (!m) return { amount: null, note: input.trim() };
  let amount = parseFloat(m[1]);
  if (m[2]) amount *= 1000;
  const note = (s.slice(0, m.index) + ' ' + s.slice(m.index + m[0].length))
    .replace(/\s+/g, ' ').trim()
    .replace(/(^|\s)(EGP|egp|LE|le|L\.E\.?|JOD|ILS|LYD|TND|DZD|MAD|MRU|SDG|SAR|AED|KWD|QAR|BHD|OMR|IQD|LBP|SYP|YER|USD|EUR|جنيه|دينار|شيكل|ريال|درهم|ليرة|ج\.?م?)$/iu, '').trim();
  return { amount, note };
}
const YESTERDAY = /(^|[^\p{L}])(yesterday|أمس|امس|امبارح|إمبارح)(?![\p{L}])/iu;
const TODAYW = /(^|[^\p{L}])(today|النهارده|النهاردة|اليوم)(?![\p{L}])/iu;
/** Like parseQuick, and also understands a day word: "yesterday taxi 85". daysAgo is 1 for yesterday. */
export function parseQuickDay(input) {
  if (YESTERDAY.test(input)) return { ...parseQuick(input.replace(YESTERDAY, ' ')), daysAgo: 1 };
  if (TODAYW.test(input)) return { ...parseQuick(input.replace(TODAYW, ' ')), daysAgo: 0 };
  return { ...parseQuick(input), daysAgo: 0 };
}
export function parseNumber(s) {
  if (s == null) return null;
  const t = normalizeDigits(String(s)).replace(/\s/g, '');
  if (!t) return null;
  const v = Number(t);
  return Number.isFinite(v) ? v : null;
}

// ---------- Gold you bought, and the end-of-month savings suggestion ----------

/** Profit or loss of one purchase {karat, grams, price} against today's price per gram (prices = {'21': 3200}), or null when unknown. */
export const goldProfit = (b, prices) => prices[b.karat] ? b.grams * prices[b.karat] - b.grams * b.price : null;
/** Totals over every purchase whose karat has a price today, or null when none has. */
export function goldSummary(buys, prices) {
  const p = buys.filter((b) => prices[b.karat]);
  if (!p.length) return null;
  const grams = p.reduce((a, b) => a + b.grams, 0), cost = p.reduce((a, b) => a + b.grams * b.price, 0), value = p.reduce((a, b) => a + b.grams * prices[b.karat], 0);
  return { grams, cost, value, profit: value - cost, percent: cost > 0 ? (value - cost) / cost * 100 : 0 };
}
/** Whole pounds worth moving into the goal at month end (never more than the goal still needs); 0 = do not suggest. */
export function suggestMove(left, goalAmount, goalSaved, minimum = 50) {
  if (!(goalAmount > 0) || goalSaved >= goalAmount) return 0;
  const amount = Math.floor(Math.min(left, goalAmount - goalSaved));
  return amount >= minimum || (amount > 0 && goalAmount - goalSaved <= amount) ? amount : 0;
}

// ---------- Planning: can I afford this, what if I save, emergency mode, price rises, weekly sentences ----------

export const FIXED_CATEGORIES = ['Bills', 'Installments', "Gam'eya", 'Loans', 'Home', 'Subscriptions', 'Education', 'Health'];

/** Whole months until the goal saving [monthly] a month. 0 = reached, null = can't tell. */
export function monthsToGoal(goalAmount, goalSaved, monthly) {
  if (!(goalAmount > 0)) return null;
  const need = goalAmount - goalSaved;
  if (need <= 0) return 0;
  if (!(monthly > 0)) return null;
  return Math.ceil(need / monthly - 1e-9);
}
export const goalDateAfter = (today, months) => addMonths(today, months, ymd(today).d);

export function canAfford(price, dailyAllowance, left, daysLeft, goalAmount = 0, goalSaved = 0, savingsPerMonth = 0) {
  const days = Math.max(daysLeft, 1);
  const dailyAfter = dailyAllowance - price / days;
  const leftAfter = left - price;
  const verdict = leftAfter < 0 ? 'NO' : (dailyAllowance > 0 && dailyAfter >= dailyAllowance * 0.5) ? 'YES' : 'TIGHT';
  const shortfall = Math.max(0, -leftAfter);
  const goalDelayMonths = shortfall > 0 ? (goalAmount > goalSaved && savingsPerMonth > 0 ? Math.ceil(shortfall / savingsPerMonth - 1e-9) : null) : 0;
  return { price, dailyBefore: dailyAllowance, dailyAfter, leftBefore: left, leftAfter, verdict, shortfall, goalDelayMonths };
}

export function simulateCut(categoryMonthly, percent, goalAmount, goalSaved, savingsPerMonth) {
  const extra = Math.max(0, categoryMonthly) * Math.min(Math.max(percent, 0), 100) / 100;
  const before = monthsToGoal(goalAmount, goalSaved, savingsPerMonth);
  const after = monthsToGoal(goalAmount, goalSaved, savingsPerMonth + extra);
  return { extraPerMonth: extra, monthsBefore: before, monthsAfter: after, monthsSooner: before != null && after != null ? Math.max(0, before - after) : 0 };
}

/** Null while the month looks fine; otherwise when the money runs out and what to postpone or cut. */
export function emergency(today, cycle, budget, everydaySpent, dayIndex, postponable, categories, minDays = 4) {
  if (!(budget.budget > 0) || dayIndex < minDays || !(everydaySpent > 0) || budget.forecastDiff >= 0) return null;
  const shortfall = -budget.forecastDiff;
  const perDay = everydaySpent / dayIndex;
  const daysAhead = budget.left <= 0 ? 0 : Math.floor(budget.left / perDay);
  const runOut = Math.min(today + daysAhead, cycle.end);
  const daysShort = Math.max(cycle.end - runOut, 0);
  let remaining = shortfall;
  const postpone = [];
  for (const p of postponable.filter((x) => x.amount > 0 && x.due > today).sort((a, b) => b.amount - a.amount)) {
    if (remaining <= 0) break;
    postpone.push(p); remaining -= p.amount;
  }
  const cuts = [];
  const daysToGo = Math.max(budget.daysLeft - 1, 1);
  for (const c of categories.filter((x) => !FIXED_CATEGORIES.includes(x.name) && x.spent > 0).sort((a, b) => b.spent - a.spent).slice(0, 4)) {
    if (remaining <= 0) break;
    const projected = c.spent / dayIndex * daysToGo;
    const cut = Math.min(remaining, Math.ceil(projected * 0.4 / 10) * 10);
    if (cut >= 10) { const v = Math.min(cut, projected); cuts.push({ name: c.name, amount: v }); remaining -= v; }
  }
  return { runOutDate: runOut, daysShort, shortfall, postpone, cuts, uncovered: Math.max(0, remaining) };
}

/** Categories heading higher than the usual (average of the last 3 months). [c] = {name, spentSoFar, usual}. */
export function priceRises(cats, dayIndex, cycleLength, minPercent = 20, minUsual = 100) {
  if (dayIndex < 5 || cycleLength <= 0) return [];
  return cats.filter((c) => c.usual >= minUsual).map((c) => {
    const projected = c.spentSoFar / dayIndex * cycleLength;
    return { name: c.name, percent: Math.trunc((projected - c.usual) / c.usual * 100), thisMonth: projected, usual: c.usual };
  }).filter((r) => r.percent >= minPercent).sort((a, b) => b.percent - a.percent);
}

/** Two short sentences for Friday. [pct] is the change against the week before (null when there was none). */
export function weekSentences(spent, pct, topCategory, daysUnder, currency) {
  const first = pct == null ? `You spent ${short(spent)} ${currency} this week.`
    : pct > 0 ? `You spent ${short(spent)} ${currency} this week, ${pct}% more than last week.`
    : pct < 0 ? `You spent ${short(spent)} ${currency} this week, ${-pct}% less than last week.`
    : `You spent ${short(spent)} ${currency} this week, the same as last week.`;
  const second = topCategory && daysUnder >= 5 ? `Most of it went to ${topCategory}, and you stayed under your daily budget on ${daysUnder} days.`
    : topCategory && daysUnder > 0 ? `Most of it went to ${topCategory}, and ${daysUnder} days were under your daily budget.`
    : topCategory ? `Most of it went to ${topCategory}.` : 'Keep going, next week is a fresh start.';
  return [first, second];
}

// ---------- Wealth over time ----------

/** history = [[epochDay, total], ...]. Adds today's total (replacing today's) and keeps the newest [max]. */
export function recordWealth(history, day, total, max = 400) {
  return [...history.filter((p) => p[0] !== day), [day, Math.round(total * 100) / 100]].sort((a, b) => a[0] - b[0]).slice(-max);
}
/** Change since the newest point at least [days] days old, or null when the history is too short. */
export function wealthChange(history, today, days) {
  const last = history[history.length - 1];
  const old = [...history].reverse().find((p) => p[0] <= today - days);
  return last && old ? last[1] - old[1] : null;
}

// ---------- Piggy bank and extra goals ----------

/** history = [[epochDay, amount], ...]; amount > 0 puts in, < 0 takes out. */
export function piggyAdd(history, day, amount) {
  if (!amount) return history;
  return [...history, [day, Math.round(amount * 100) / 100]].sort((a, b) => a[0] - b[0]);
}
export const piggyTotal = (history) => history.reduce((s, p) => s + p[1], 0);
/** Every month from the first entry to this month (empty months show 0), oldest first, with the running total. */
export function piggyMonths(history, today) {
  if (!history.length) return [];
  const key = (ed) => { const { y, m } = ymd(ed); return y * 12 + (m - 1); };
  const by = {};
  for (const [d, a] of history) by[key(d)] = (by[key(d)] || 0) + a;
  const first = key(history[0][0]), last = Math.max(key(today), key(history[history.length - 1][0]));
  const out = [];
  let running = 0;
  for (let k = first; k <= last; k++) {
    const saved = by[k] || 0;
    running += saved;
    out.push({ y: Math.floor(k / 12), m: (k % 12) + 1, saved, total: running });
  }
  return out;
}
export const piggyAverage = (months) => (months.length < 2 ? null : months.reduce((s, m) => s + m.saved, 0) / months.length);
/** Months in a row (counting back from the last finished month) in which at least [target] went into the piggy bank. */
export function piggyStreak(months, target, today) {
  if (!(target > 0)) return 0;
  const { y, m } = ymd(today);
  let k = y * 12 + (m - 1) - 1, n = 0;
  const by = {};
  for (const x of months) by[x.y * 12 + (x.m - 1)] = x.saved;
  while ((by[k] || 0) >= target) { n++; k--; }
  return n;
}
export function piggyThisMonth(months, today) {
  const { y, m } = ymd(today);
  const x = months.find((q) => q.y === y && q.m === m);
  return x ? x.saved : 0;
}
/** How many months of spending the piggy bank covers (one decimal), or null when spending is unknown. */
export const coverMonths = (total, monthlySpend) => (monthlySpend > 0 && total > 0 ? Math.round(total / monthlySpend * 10) / 10 : null);
export const limitOver = (spent, limit) => limit > 0 && spent > limit;
export const goalReached = (g) => g.target > 0 && g.saved >= g.target;
export const goalFraction = (g) => (g.target > 0 ? Math.max(0, Math.min(1, g.saved / g.target)) : 0);
export const goalMonthsLeft = (g) => monthsToGoal(g.target, g.saved, g.monthly);
/** Reached goals last, then soonest first, goals with no date at the end. */
export function goalsByFinish(list) {
  return [...list].sort((a, b) => (goalReached(a) - goalReached(b)) || ((goalMonthsLeft(a) ?? 1e9) - (goalMonthsLeft(b) ?? 1e9)));
}
/** Months sooner a goal finishes with [extra] more each month, or null when it has no date now. */
export function monthsSoonerWith(g, extra) {
  const before = monthsToGoal(g.target, g.saved, g.monthly), after = monthsToGoal(g.target, g.saved, g.monthly + extra);
  return before == null || after == null ? null : Math.max(0, before - after);
}
export const fmtMonthName = (y, m) => MONTHS[m - 1];

// ---------- Commitments overview (kept in step with core/Commitments.kt) ----------

// Item kinds: BILL, SUB, INSTALLMENT, CARD, CIRCLE, LOAN, INCOME.
export const COMMIT_FILTERS = [['ALL', 'All'], ['BILLS', 'Bills'], ['INSTALLMENTS', 'Installments'], ['CIRCLE', "Gam'eya"], ['CARDS', 'Cards'], ['INCOME', 'Income']];

export function commitMatches(filter, i) {
  switch (filter) {
    case 'BILLS': return i.kind === 'BILL' || i.kind === 'SUB';
    case 'INSTALLMENTS': return i.kind === 'INSTALLMENT';
    case 'CIRCLE': return i.kind === 'CIRCLE';
    case 'CARDS': return i.kind === 'CARD';
    case 'INCOME': return !!i.incoming;
    default: return true;
  }
}
export const commitLeft = (items) => items.filter((i) => !i.paid && !i.incoming).reduce((a, i) => a + i.amount, 0);
export const commitLeftCount = (items) => items.filter((i) => !i.paid && !i.incoming).length;
export const commitLate = (items, today) => items.filter((i) => !i.paid && i.date < today).sort((a, b) => a.date - b.date);
export const commitLater = (items, today) => items.filter((i) => !i.paid && i.date >= today).sort((a, b) => a.date - b.date);
export const commitDone = (items) => items.filter((i) => i.paid).sort((a, b) => b.date - a.date);

// Brand marks: our own coloured square + letter, never the official logos.
const BRANDS = [
  ['N', '#E50914', ['netflix', 'نتفليكس', 'نتفلكس']],
  ['S', '#00B386', ['shahid', 'شاهد']],
  ['Y', '#FF0000', ['youtube', 'يوتيوب']],
  ['S', '#1DB954', ['spotify', 'سبوتيفاي', 'سبوتفاي']],
  ['A', '#6C2BD9', ['anghami', 'انغامي', 'انجامي']],
  ['D+', '#113CCF', ['disney', 'ديزني']],
  ['O+', '#222B45', ['osn', 'اوسن']],
  ['C', '#D97757', ['claude', 'كلود']],
  ['G', '#10A37F', ['chatgpt', 'openai', 'شات جي بي تي', 'شات جيبيتي']],
  ['G', '#4285F4', ['gemini', 'جيميني', 'جمناي']],
  ['N', '#111111', ['notion', 'نوشن']],
  ['C', '#00B8C4', ['canva', 'كانفا']],
  ['A', '#FA0F00', ['adobe', 'ادوبي', 'creative cloud']],
  ['i', '#3693F3', ['icloud', 'ايكلاود', 'ايكلود']],
  ['G1', '#34A853', ['google one', 'جوجل ون', 'google drive', 'جوجل درايف']],
  ['M', '#D83B01', ['microsoft', 'office 365', 'm365', 'مايكروسوفت']],
  ['P+', '#003791', ['playstation', 'ps plus', 'psn', 'بلايستيشن', 'بلاي ستيشن']],
  ['X', '#107C10', ['xbox', 'اكس بوكس']],
  ['S', '#1B2838', ['steam', 'ستيم']],
  ['V', '#E60000', ['vodafone', 'فودافون']],
  ['O', '#FF7900', ['orange', 'اورنج']],
  ['e', '#6CBE45', ['etisalat', 'اتصالات']],
  ['WE', '#5B2D90', ['we', 'وي']],
];
const TOPICS = [
  [['electric', 'كهربا', 'كهرباء'], '⚡'], [['water', 'مياه', 'ميه', 'مية'], '💧'], [['gas', 'غاز'], '🔥'],
  [['internet', 'wifi', 'انترنت', 'نت'], '🌐'], [['rent', 'ايجار', 'إيجار'], '🔑'],
  [['school', 'tuition', 'مدرسه', 'مصاريف', 'حضانه'], '🎓'], [['club', 'gym', 'نادي', 'جيم'], '🏋️'],
  [['insurance', 'تامين'], '🛡️'], [['mobile', 'phone', 'موبايل', 'كارت شحن'], '📱'],
  [['doctor', 'clinic', 'دكتور', 'عياده'], '🩺'], [['garage', 'parking', 'جراج'], '🅿️'],
  [['zakat', 'charity', 'زكاه', 'صدقه'], '🤲'], [['salary', 'payroll', 'مرتب', 'مرتبي', 'راتب'], '💰'],
];
const KIND_EMOJI = { BILL: '🧾', SUB: '🔁', INSTALLMENT: '📆', CARD: '💳', CIRCLE: '🤝', LOAN: '💸', INCOME: '💰' };

export function normalizeName(s) {
  return String(s ?? '').toLowerCase().replace(/[أإآ]/g, 'ا').replace(/ى/g, 'ي').replace(/ة/g, 'ه').replace(/[ً-ٟـ]/g, '');
}
/** { letter, color, emoji }: letter is null unless the name is a known service. */
export function badgeFor(name, kind) {
  const text = normalizeName(name);
  const tokens = text.split(/[^\p{L}\p{N}+]+/u).filter(Boolean);
  const hit = (k) => { const key = normalizeName(k); return key.length <= 3 ? tokens.includes(key) : text.includes(key); };
  if (kind !== 'INCOME') {
    const b = BRANDS.find(([, , keys]) => keys.some(hit));
    if (b) return { letter: b[0], color: b[1], emoji: KIND_EMOJI[kind] };
  }
  const t = TOPICS.find(([keys]) => keys.some(hit));
  return { letter: null, color: null, emoji: t ? t[1] : KIND_EMOJI[kind] };
}


// ---------- Loan schedule (port of core/Loans.kt) ----------
const cents = (x) => Math.round(x * 100) / 100;
/** [count] monthly instalments adding up to exactly [total], on the day of [first] (clamped to month end); the rounding remainder goes on the last. */
export function loanGenerate(total, count, first) {
  const n = Math.max(1, Math.floor(count) || 1);
  const each = cents(total / n);
  const anchor = ymd(first).d;
  return Array.from({ length: n }, (_, k) => ({ due: addMonths(first, k, anchor), amount: k === n - 1 ? cents(total - each * (n - 1)) : each }));
}
/** What a loan does to each month: borrowed money is income in the month it came, each instalment a commitment in its month (lent is the mirror). */
export function loanPreview(lent, total, day, schedule, recorded = true) {
  const ymOf = (ed) => { const { y, m } = ymd(ed); return y * 12 + m - 1; };
  const rows = new Map();
  const at = (ym) => { if (!rows.has(ym)) rows.set(ym, { ym, income: 0, commitment: 0 }); return rows.get(ym); };
  if (!lent && recorded) at(ymOf(day)).income += total;
  for (const s of schedule) { if (lent) at(ymOf(s.due)).income += s.amount; else at(ymOf(s.due)).commitment += s.amount; }
  return [...rows.values()].sort((a, b) => a.ym - b.ym);
}
const instRem = (r) => Math.max(r.amount - (r.paidAmount || 0), 0);
export const instIsPaid = (r) => instRem(r) < 0.005;
/** Splits a payment over the oldest unpaid instalments first: { parts: [[id, amount]], left }. */
export function loanAllocate(rows, payment) {
  let left = payment;
  const parts = [];
  for (const r of rows.filter((x) => !instIsPaid(x)).sort((a, b) => a.dueDay - b.dueDay)) {
    if (left < 0.005) break;
    const use = Math.min(left, instRem(r));
    parts.push([r.id, use]);
    left -= use;
  }
  return { parts, left: left < 0.005 ? 0 : left };
}
/** Takes an amount back off the instalments, the most recently paid first (undo). */
export function loanUnallocate(rows, amount) {
  let left = amount;
  const out = [];
  for (const r of rows.filter((x) => (x.paidAmount || 0) > 0.004).sort((a, b) => b.dueDay - a.dueDay)) {
    if (left < 0.005) break;
    const take = Math.min(left, r.paidAmount);
    out.push([r.id, take]);
    left -= take;
  }
  return out;
}
export const loanNext = (rows) => rows.filter((x) => !instIsPaid(x)).sort((a, b) => a.dueDay - b.dueDay)[0] || null;
export const instRemaining = instRem;
