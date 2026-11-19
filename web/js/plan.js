// The month-by-month plan (ported from core/MonthPlan.kt): salary raises, living cost, commitments, circles, loans.
import * as C from './core.js';

/** A month as one number: year * 12 + (month - 1). */
export const ymOf = (ed) => { const { y, m } = C.ymd(ed); return y * 12 + m - 1; };
export const ymStart = (ym) => C.epochOf(Math.floor(ym / 12), (ym % 12) + 1, 1);
export const ymEnd = (ym) => ymStart(ym + 1);
export const ymLabel = (ym) => C.fmtMonthYear(ymStart(ym));

/** The value of the latest step at or before [ym], or null. Steps are { ym: amount }. */
export function stepAt(steps, ym) {
  let best = null;
  for (const k of Object.keys(steps || {})) { const n = +k; if (n <= ym && (best === null || n > best)) best = n; }
  return best === null ? null : steps[best];
}

/** What is expected in each month from [fromYm] for [count] months. Only unpaid things count. */
export function project(input, fromYm, count) {
  const out = [];
  for (let ym = fromYm; ym < fromYm + count; ym++) {
    const from = ymStart(ym), to = ymEnd(ym), lines = [];
    let bills = 0, inst = 0, cards = 0, shares = 0, payout = 0, loansIn = 0, loansOut = 0;
    for (const b of input.bills) {
      const n = C.occurrences(b.nextDue, b.frequency, b.everyN, from, to, b.anchorDay).length;
      if (n > 0) { bills += b.amount * n; lines.push({ kind: 'BILL', name: b.name, amount: b.amount * n }); }
    }
    for (const p of input.installments) {
      const n = C.plan(p.amount, p.total, p.paid, p.firstDue).unpaidDueIn(from, to).length;
      if (n > 0) { lines.push({ kind: p.card ? 'CARD' : 'INSTALLMENT', name: p.name, amount: p.amount * n }); if (p.card) cards += p.amount * n; else inst += p.amount * n; }
    }
    for (const c of input.circles) {
      const circ = C.circle(c);
      const n = circ.plan.unpaidDueIn(from, to).length;
      if (n > 0) { shares += c.share * n; lines.push({ kind: 'CIRCLE', name: c.name, amount: c.share * n }); }
      if (!c.payoutReceived && circ.payoutDate >= from && circ.payoutDate < to) { payout += circ.payout; lines.push({ kind: 'PAYOUT', name: c.name, amount: circ.payout }); }
    }
    for (const l of input.loans) {
      if (l.due == null || l.due < from || l.due >= to) continue;
      if (l.lent) { loansIn += l.remaining; lines.push({ kind: 'LOAN_IN', name: l.person, amount: l.remaining }); }
      else { loansOut += l.remaining; lines.push({ kind: 'LOAN_OUT', name: l.person, amount: l.remaining }); }
    }
    out.push({
      ym, actual: false, salary: stepAt(input.salarySteps, ym) ?? input.salaryBase, payout, loansIn, bills, installments: inst, cards,
      circleShares: shares, loansOut, living: input.livingOverrides?.[ym] ?? input.livingBase, lines,
    });
  }
  return withCumulative(out);
}

export const incomeOf = (m) => m.salary + m.payout + m.loansIn;
export const committedOf = (m) => m.bills + m.installments + m.cards + m.circleShares + m.loansOut;
export const remainingOf = (m) => incomeOf(m) - committedOf(m) - m.living;

export function withCumulative(months) {
  let run = 0;
  return months.map((m) => { run += remainingOf(m); return { ...m, cumulative: run }; });
}
