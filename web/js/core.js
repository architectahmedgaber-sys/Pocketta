// Pure money and date math, ported from the Android app's :core module.
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

export function fmt(v) {
  const r = Math.round(v * 100) / 100;
  return r.toLocaleString('en-US', { maximumFractionDigits: 2 });
}
export function short(v) { return Math.round(v).toLocaleString('en-US'); }

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
  return {
    budget, spent, committed: i.commitments, left: budget - spent - i.commitments, daysLeft,
    dailyAllowance: daily, leftToday: daily - i.spentToday, forecastSpend: forecast, forecastDiff: budget - forecast,
    spentFraction, committedFraction,
  };
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
    .replace(/(^|\s)(EGP|egp|LE|le|L\.E\.?|جنيه|ج\.?م?)$/u, '').trim();
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
