// Numeric vectors of the v3 spec, run against web/js/extras.js. Run: node tests/web/extras.test.mjs
import assert from 'node:assert/strict';
import * as C from '../../web/js/core.js';
import * as X from '../../web/js/extras.js';
const eq = assert.deepEqual;
const E = C.epochOf;

// 1 alert levels
eq([0, 79.99, 80, 100, 100.01].map((s) => X.limitAlertLevel(s, 100)), [0, 0, 80, 80, 100]);
eq(X.limitAlertLevel(500, 0), 0);
eq([X.limitAlertLevel(2400, 3000), X.limitAlertLevel(2399.99, 3000), X.limitAlertLevel(0.8 * 3, 3)], [80, 0, 80]);
eq([X.amountText(60), X.amountText(60.5), X.amountText(60.25), X.amountText(1200)], ['60', '60.5', '60.25', '1200']);
eq(X.parseAlertState('19700|1,2'), { day: 19700, levels: { 1: 100, 2: 100 } });
eq(X.parseAlertState(''), { day: 0, levels: {} });
eq(X.parseAlertState('19700|3:80,4:100'), { day: 19700, levels: { 3: 80, 4: 100 } });
let r = X.freshLimitAlerts(19700, '', [{ id: 3, spent: 85, limit: 100 }, { id: 4, spent: 10, limit: 100 }]);
eq(r.fresh, [{ id: 3, level: 80 }]); eq(r.state, '19700|3:80');
r = X.freshLimitAlerts(19700, r.state, [{ id: 3, spent: 90, limit: 100 }]);
eq(r.fresh, []);                                    // same level again: silent
r = X.freshLimitAlerts(19700, r.state, [{ id: 3, spent: 101, limit: 100 }]);
eq(r.fresh, [{ id: 3, level: 100 }]); eq(r.state, '19700|3:100');
r = X.freshLimitAlerts(19731, r.state, [{ id: 3, spent: 85, limit: 100 }]);
eq(r.fresh, [{ id: 3, level: 80 }]);                // new cycle: starts again
r = X.freshLimitAlerts(19700, '19700|7', [{ id: 7, spent: 120, limit: 100 }]);
eq(r.fresh, []);                                    // old-format state counts as already over
eq(X.budgetsSummary([{ spent: 50, limit: 100 }, { spent: 85, limit: 100 }, { spent: 130, limit: 100 }, { spent: 999, limit: 0 }]), { total: 3, over: 1, near: 1, ok: 1 });

// 2 forecast
eq(X.runOutIn(900, 100, 20), 9);
eq(X.runOutIn(900, 100, 9), null);
eq(X.runOutIn(899, 100, 20), 8);
eq(X.runOutIn(-5, 100, 20), 0);
eq(X.runOutIn(500, 0, 20), null);
eq([X.forecastStatus(-1, 1000), X.forecastStatus(0, 1000), X.forecastStatus(99.99, 1000), X.forecastStatus(100, 1000)], ['over', 'tight', 'tight', 'ok']);

// 3 auto post
const bills = [
  { id: 1, active: true, autoPost: true, variable: false, nextDue: E(2026, 11, 28), frequency: 'MONTHLY', everyN: 30, anchorDay: 28 },
  { id: 2, active: true, autoPost: true, variable: true, nextDue: E(2026, 11, 1), frequency: 'MONTHLY', everyN: 30, anchorDay: 1 },
  { id: 3, active: true, autoPost: false, variable: false, nextDue: E(2026, 11, 1), frequency: 'MONTHLY', everyN: 30, anchorDay: 1 },
  { id: 4, active: true, autoPost: true, variable: false, nextDue: E(2026, 12, 1), frequency: 'MONTHLY', everyN: 30, anchorDay: 1 },
  { id: 5, active: false, autoPost: true, variable: false, nextDue: E(2026, 11, 1), frequency: 'MONTHLY', everyN: 30, anchorDay: 1 },
  { id: 6, active: true, autoPost: true, variable: false, nextDue: E(2026, 9, 30), frequency: 'MONTHLY', everyN: 30, anchorDay: 30 },
];
const plan = X.autoPostPlan(bills, E(2026, 11, 28), C.nextDue);
const iso = (ed) => C.isoOf(ed);
eq(plan.map((p) => `${p.billId}@${iso(p.day)}`), ['1@2026-11-28', '6@2026-09-30', '6@2026-10-30']);
eq(X.autoPostPlan(bills, E(2026, 11, 27), C.nextDue).filter((p) => p.billId === 1), []);
eq(X.autoPostPlan([{ id: 9, active: true, autoPost: true, variable: false, nextDue: E(2020, 1, 1), frequency: 'MONTHLY', everyN: 30, anchorDay: 1 }], E(2026, 11, 28), C.nextDue).length, 12);

// 4 goals
eq(X.monthsUntil(E(2026, 11, 28), E(2027, 3, 1)), 4);
eq(X.monthsUntil(E(2026, 11, 28), E(2026, 11, 30)), 1);
eq(X.monthsUntil(E(2026, 11, 28), E(2026, 1, 1)), 1);
eq(X.goalNeed(12000, 3000, 2000, E(2027, 5, 15), E(2026, 11, 28)), { status: 'ontrack', needed: 1500, months: 6 });
eq(X.goalNeed(12000, 3000, 1000, E(2027, 5, 15), E(2026, 11, 28)), { status: 'behind', needed: 1500, months: 6 });
eq(X.goalNeed(10000, 3333, 0, E(2027, 2, 1), E(2026, 11, 28)), { status: 'behind', needed: 2223, months: 3 });
eq(X.goalNeed(5000, 5000, 0, null, E(2026, 11, 28)), { status: 'reached', needed: 0, months: 0 });
eq(X.goalNeed(5000, 100, 0, null, E(2026, 11, 28)), { status: 'nodate', needed: null, months: null });
const gl = X.parseGoals('1\tPhone\t20000\t5000\t1000\t' + E(2027, 6, 1) + '\n2\tCar\t90000\t0\t0\nbad line\n3\tX\tnope\t0\t0');
eq(gl.map((g) => [g.id, g.name, g.deadline]), [[1, 'Phone', E(2027, 6, 1)], [2, 'Car', null]]);
eq(X.parseGoals(X.formatGoals(gl)), gl);
eq(X.formatGoals([{ id: 2, name: 'Car', target: 90000, saved: 0, monthly: 0, deadline: null }]), '2\tCar\t90000\t0\t0\t');

// 5 report
const D = (d) => E(2026, 11, d);
const tx = [
  { type: 'EXPENSE', base: 100, cat: 'Food', note: 'koshary', epochDay: D(2), source: '' },
  { type: 'EXPENSE', base: 300, cat: 'Food', note: 'market', epochDay: D(2), source: '' },
  { type: 'EXPENSE', base: 1200, cat: 'Bills', note: 'rent', epochDay: D(1), source: 'BILL' },
  { type: 'EXPENSE', base: 50, cat: 'Fun', note: 'cinema', epochDay: D(9), source: '' },
  { type: 'EXPENSE', base: 5000, cat: 'Loans', note: 'lend', epochDay: D(3), source: 'LOAN' },
  { type: 'EXPENSE', base: 700, cat: '', note: 'piggy', epochDay: D(4), source: 'PIGGY' },
  { type: 'INCOME', base: 9000, cat: 'Salary', note: 'salary', epochDay: D(1), source: 'SALARY' },
  { type: 'INCOME', base: 400, cat: '', note: 'back', epochDay: D(5), source: 'PIGGYOUT' },
  { type: 'TRANSFER', base: 77, cat: '', note: 't', epochDay: D(6), source: '' },
];
const rep = X.monthReport(tx, [{ type: 'EXPENSE', base: 1000, cat: 'Food', note: 'x', epochDay: 1, source: '' }], 30);
eq(rep.spent, 1650); eq(rep.income, 9000); eq(rep.net, 7350); eq(rep.prevSpent, 1000); eq(rep.changePct, 65); eq(rep.perDay, 55);
eq(rep.topCategories, [{ name: 'Bills', amount: 1200, share: 73 }, { name: 'Food', amount: 400, share: 24 }, { name: 'Fun', amount: 50, share: 3 }]);
eq(rep.topExpenses.map((t) => t.note), ['rent', 'market', 'koshary']);
eq(rep.biggestDay, { day: D(1), amount: 1200 });
eq(X.monthReport([], [], 30), { spent: 0, income: 0, net: 0, prevSpent: 0, changePct: null, perDay: 0, topCategories: [], topExpenses: [], biggestDay: null });

// 7 csv
const csv = X.toCsv([
  { epochDay: E(2026, 11, 2), type: 'EXPENSE', amount: 60, currency: 'EGP', rate: 1, categoryLabel: 'Food · Lunch', walletName: 'Cash', note: 'koshary, large', tags: ['work', 'trip'] },
  { epochDay: E(2026, 11, 3), type: 'EXPENSE', amount: 10, currency: 'USD', rate: 49.5, categoryLabel: '', walletName: 'Bank', note: '=1+1 "x"', tags: [] },
  { epochDay: E(2026, 11, 4), type: 'INCOME', amount: 100.5, currency: 'EGP', rate: 1, categoryLabel: 'Salary', walletName: 'Bank', note: 'قهوة', tags: [] },
], 'EGP');
eq(csv, '\uFEFFDate,Type,Amount,Currency,Rate,Amount (EGP),Category,Wallet,Note,Tags\r\n' +
  '2026-11-02,EXPENSE,60.00,EGP,1,60.00,Food · Lunch,Cash,"koshary, large",work|trip\r\n' +
  '2026-11-03,EXPENSE,10.00,USD,49.5,495.00,,Bank,"\'=1+1 ""x""",\r\n' +
  '2026-11-04,INCOME,100.50,EGP,1,100.50,Salary,Bank,قهوة,\r\n');

// 8 lock
eq([X.shouldRelock(0, 5000, 60), X.shouldRelock(1000, 1000, 0), X.shouldRelock(1000, 30999, 30), X.shouldRelock(1000, 31000, 30)], [true, true, false, true]);

// 10 filters
const names = { 1: 'Food', 2: 'Bills', 3: 'Fun' };
const cn = (id) => names[id] || 'No category';
const T = [
  { id: 1, type: 'EXPENSE', base: 60, note: 'koshary', categoryId: 1, walletId: 1, epochDay: D(2), need: 'NEED', tags: ['work'] },
  { id: 2, type: 'EXPENSE', base: 1200, note: 'rent', categoryId: 2, walletId: 2, epochDay: D(1), need: '', tags: [] },
  { id: 3, type: 'EXPENSE', base: 50, note: 'cinema', categoryId: 3, walletId: 1, epochDay: D(9), need: 'WANT', tags: ['trip'] },
  { id: 4, type: 'INCOME', base: 9000, note: 'salary', categoryId: null, walletId: 2, epochDay: D(1), need: '', tags: [] },
  { id: 5, type: 'EXPENSE', base: 60, note: 'lunch', categoryId: 1, walletId: 1, epochDay: D(2), need: 'WANT', tags: [] },
];
const ids = (f) => X.filterTxns(T, f, cn).list.map((t) => t.id);
eq(ids({}), [3, 5, 1, 4, 2]);                       // newest day first, then higher id first
eq(ids({ q: 'FOOD' }), [5, 1]);
eq(ids({ q: '60' }), [5, 1]);
eq(ids({ q: 'trip' }), [3]);
eq(ids({ types: ['INCOME'] }), [4]);
eq(ids({ catIds: [1, 3] }), [3, 5, 1]);
eq(ids({ min: 55, max: 1200 }), [5, 1, 2]);
eq(ids({ from: D(2), to: D(9) }), [3, 5, 1]);
eq(ids({ walletId: 1, need: 'WANT' }), [3, 5]);
eq(ids({ tag: 'work', q: 'kosh' }), [1]);
eq(ids({ q: 'nothing' }), []);
eq(X.filterTxns(T, { types: ['EXPENSE'], walletId: 1 }, cn).total, 170);
console.log('ALL V3 JS TESTS PASS');
