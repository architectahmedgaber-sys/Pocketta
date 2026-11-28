// The numeric vectors of the v2 spec (sections 9 and 10), run against the code that is really in the app (web/js/core.js).
import assert from 'node:assert/strict';
import * as C from '../../web/js/core.js';
const eq = assert.deepEqual;
const iso = (ed) => { const { y, m, d } = C.ymd(ed); return `${y}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`; };
const E = C.epochOf;
const gen = (total, n, first) => C.loanGenerate(total, n, first);

// split: remainder on the last instalment
eq(gen(3000, 3, E(2026, 12, 5)).map((x) => x.amount), [1000, 1000, 1000]);
eq(gen(1000, 3, E(2026, 12, 5)).map((x) => x.amount), [333.33, 333.33, 333.34]);
eq(gen(0.01, 3, E(2026, 12, 5)).map((x) => x.amount), [0, 0, 0.01]);
eq(gen(0, 2, E(2026, 12, 5)).map((x) => x.amount), [0, 0]);
// due dates, month-end clamping, leap year
eq(gen(300, 3, E(2026, 12, 5)).map((x) => iso(x.due)), ['2026-12-05', '2027-01-05', '2027-02-05']);
eq(gen(300, 3, E(2027, 1, 31)).map((x) => iso(x.due)), ['2027-01-31', '2027-02-28', '2027-03-31']);
eq(gen(200, 2, E(2028, 1, 31)).map((x) => iso(x.due)), ['2028-01-31', '2028-02-29']);
eq(gen(300, 3, E(2026, 11, 30)).map((x) => iso(x.due)), ['2026-11-30', '2026-12-30', '2027-01-30']);
eq(iso(gen(1400, 14, E(2026, 11, 20)).at(-1).due), '2027-12-20');
// default first due date = same day next month, clamped
const nextMonth = (ed) => C.addMonths(ed, 1, C.ymd(ed).d);
eq(iso(nextMonth(E(2026, 11, 20))), '2026-12-20');
eq(iso(nextMonth(E(2026, 1, 31))), '2026-02-28');
// sums are exact
for (const [t, n] of [[1000, 3], [99.99, 7], [0.01, 3], [12345.67, 12]]) assert.ok(Math.abs(gen(t, n, E(2026, 1, 31)).reduce((a, x) => a + x.amount, 0) - t) < 1e-9);

// parse intents
const P = (s) => { const q = C.parseIntent(s); return { kind: q.kind, amount: q.amount, person: q.person, note: q.note, daysAgo: q.daysAgo }; };
const loan = (s, kind, person, amount) => { const q = P(s); eq([q.kind, q.person, q.amount], [kind, person, amount], s); };
loan('استلفت ٣٠٠٠ من محمد', 'BORROWED', 'محمد', 3000);
loan('استلفت من محمد 3000', 'BORROWED', 'محمد', 3000);
loan('borrowed 3000 from Sam', 'BORROWED', 'Sam', 3000);
loan('سلفت محمد ٥٠٠', 'LENT', 'محمد', 500);
loan('lent Sam 500', 'LENT', 'Sam', 500);
loan('lent 500 to Sam', 'LENT', 'Sam', 500);
loan('استلفت ٣٠٠٠', 'BORROWED', '', 3000);
eq(P('مرتب ٩٢٠٠'), { kind: 'INCOME', amount: 9200, note: 'مرتب', person: '', daysAgo: 0 });
eq(P('كشري ٦٠'), { kind: 'EXPENSE', amount: 60, note: 'كشري', person: '', daysAgo: 0 });
eq(P('امبارح تاكسي 85'), { kind: 'EXPENSE', amount: 85, note: 'تاكسي', person: '', daysAgo: 1 });
eq(P('1.5k rent'), { kind: 'EXPENSE', amount: 1500, note: 'rent', person: '', daysAgo: 0 });
eq(P('قهوة'), { kind: 'EXPENSE', amount: null, note: 'قهوة', person: '', daysAgo: 0 });
eq(P('60 uber'), { kind: 'EXPENSE', amount: 60, note: 'uber', person: '', daysAgo: 0 });

// instalments covered by repaid money, oldest first (the PR keeps paidAmount per row; the sum must always equal what was repaid)
const rows = () => [{ id: 1, dueDay: 10, amount: 1000, paidAmount: 0 }, { id: 2, dueDay: 20, amount: 1000, paidAmount: 0 }, { id: 3, dueDay: 30, amount: 1000, paidAmount: 0 }];
const apply = (rs, delta) => {
  if (delta > 0.004) for (const [id, a] of C.loanAllocate(rs, delta).parts) rs.find((r) => r.id === id).paidAmount += a;
  else if (delta < -0.004) for (const [id, a] of C.loanUnallocate(rs, -delta)) rs.find((r) => r.id === id).paidAmount -= a;
};
const sum = (rs) => Math.round(rs.reduce((a, r) => a + r.paidAmount, 0) * 100) / 100;
{
  const rs = rows(); let repaid = 0;
  const step = (d) => { const clamped = d > 0 ? Math.min(d, 3000 - repaid) : Math.max(d, -repaid); repaid = Math.round((repaid + clamped) * 100) / 100; apply(rs, clamped); eq(sum(rs), repaid); };
  step(1500); step(700); step(-300); step(400); step(-1000); step(5000); step(-3000); step(-5);
  eq(rs.map((r) => r.paidAmount), [0, 0, 0]);
  // edit a repayment 500 -> 800 then delete it
  step(500); step(300); step(-800);
}
// sync decision (same nine branches as the spec, plus the answer that is never asked twice)
const S = (o) => C.syncDecision({ cloudExists: true, localEmpty: false, syncedBefore: true, sameContent: false, localChanged: false, cloudChanged: false, ...o }).action;
eq(S({ cloudExists: false }), 'UPLOAD'); eq(S({ cloudExists: false, localEmpty: true }), 'NOTHING');
eq(S({ syncedBefore: false, localEmpty: true }), 'APPLY_CLOUD');
eq(S({ syncedBefore: false, sameContent: true }), 'MARK_SYNCED');
eq(S({ syncedBefore: false }), 'ASK_CONFLICT');
eq(S({ localChanged: true, cloudChanged: true }), 'ASK_CONFLICT');
eq(S({ localChanged: true, cloudChanged: true, sameContent: true }), 'MARK_SYNCED');
eq(S({ localChanged: true }), 'UPLOAD'); eq(S({ cloudChanged: true }), 'APPLY_CLOUD'); eq(S({}), 'NOTHING');
eq(C.syncDecision({ cloudExists: true, localEmpty: false, syncedBefore: false, localAt: 5, cloudAt: 9 }).newest, 'cloud');
eq(C.syncDecision({ cloudExists: true, localEmpty: false, syncedBefore: false, localAt: 9, cloudAt: 5 }).newest, 'local');
eq(S({ syncedBefore: false, remembered: 'cloud' }), 'APPLY_CLOUD'); eq(S({ localChanged: true, cloudChanged: true, remembered: 'local' }), 'UPLOAD');
console.log('V2 VECTORS PASS (web core)');
