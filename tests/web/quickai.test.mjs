// Quick AI entry rules, run against web/js/quickai.js. Run: node tests/web/quickai.test.mjs
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import * as Q from '../../web/js/quickai.js';
const eq = assert.deepEqual;
const TODAY = '2026-11-28';
const coffee = JSON.stringify({ transactions: [{ type: 'expense', amount_egp: 60, category: 'أكل', person: null, due_date: null, date: TODAY, note: 'كشري' }] });
const api = (text, model = Q.MODEL, input = 500, output = 80) =>
  JSON.stringify({ id: 'msg_1', type: 'message', role: 'assistant', model, content: [{ type: 'text', text }], stop_reason: 'end_turn', usage: { input_tokens: input, output_tokens: output } });
const rig = (answer, sent = []) => {
  const saved = [], usage = [], logs = [];
  const s = new Q.Session({ transport: async (b) => { sent.push(b); return answer; }, sink: async (p) => { saved.push(p); }, onUsage: (u, ok) => usage.push([u, ok]), onLog: (l) => logs.push(l) });
  return { s, saved, usage, logs };
};
let n = 0;
const test = async (name, fn) => { await fn(); n++; console.log('ok', name); };

await test('model is pinned to haiku', () => {
  eq(Q.MODEL, 'claude-haiku-5-5');
  const body = JSON.stringify(Q.requestBody(TODAY, 'كشري ٦٠'));
  eq(JSON.parse(body).model, 'claude-haiku-5-5');
  assert.ok(!/sonnet|opus|latest/i.test(body));
});

await test('request starts with today and uses the owner prompt; web prompt equals the Android prompt', async () => {
  eq(Q.userMessage(TODAY, '  كشري ٦٠ '), 'today=2026-11-28\nكشري ٦٠');
  const sent = [];
  await rig(api(coffee), sent).s.propose('كشري ٦٠', TODAY);
  eq(sent[0].messages[0].content, 'today=2026-11-28\nكشري ٦٠');
  eq(sent[0].system, Q.SYSTEM_PROMPT);
  const kt = readFileSync(new URL('../../core/src/main/kotlin/app/pocketta/core/QuickAi.kt', import.meta.url), 'utf8');
  const m = kt.match(/val SYSTEM_PROMPT: String = """\n([\s\S]*?)\n""".trim\(\)/);
  assert.ok(m, 'prompt found in QuickAi.kt');
  eq(Q.SYSTEM_PROMPT, m[1]);
});

await test('a wrong model is discarded, logged and its tokens still counted', async () => {
  for (const other of ['claude-sonnet-5-5', 'claude-opus-5-5', 'claude-haiku-5-5-latest', 'Claude-Haiku-5-5', 'claude-haiku-5-5 ', 'claude-haiku-5-4']) {
    const r = rig(api(coffee, other));
    const out = await r.s.propose('كشري ٦٠', TODAY);
    eq(out.kind, 'wrong_model'); eq(out.got, other);
    eq(r.saved, []); eq(r.logs.length, 1); eq(r.usage, [[{ input: 500, output: 80 }, false]]);
  }
  eq(Q.readReply(JSON.stringify({ content: [{ type: 'text', text: coffee }], usage: { input_tokens: 1, output_tokens: 1 } })).result.kind, 'wrong_model');
});

await test('malformed JSON becomes needs_review and saves nothing', async () => {
  const good = JSON.parse(coffee).transactions[0];
  const withTx = (patch) => JSON.stringify({ transactions: [{ ...good, ...patch }] });
  const bad = [
    '```json\n' + coffee + '\n```', 'تمام: ' + coffee, coffee + '\nشكرا', '{"transactions":[{"type":"expense","amount_egp":60', 'not json', '', '[]', 'null',
    '{"transactions":"x"}', '{"transactions":[]}', '{"transactions":[],"needs_review":true}',
    withTx({ type: 'gift' }), withTx({ amount_egp: '60' }), withTx({ amount_egp: 0 }), withTx({ amount_egp: -5 }), withTx({ date: '28/11/2026' }), withTx({ date: '2026-02-30' }),
    withTx({ type: 'loan_given', due_date: 'بكرة' }), withTx({ date: undefined }), JSON.stringify({ transactions: [good], needs_review: true }),
  ];
  for (const b of bad) {
    const r = rig(api(b));
    const out = await r.s.propose('كشري ٦٠', TODAY);
    assert.equal(out.kind, 'needs_review', `not accepted: ${b}`);
    assert.equal(out.malformed, true, b);
    eq(r.saved, []); eq(r.logs.length, 1);
  }
  eq(Q.readReply('<html>502</html>').result.kind, 'needs_review');
});

await test('the model may say needs_review itself', async () => {
  const r = rig(api('{"transactions": [], "needs_review": true, "reason": "المبلغ ناقص"}'));
  eq(await r.s.propose('اشتريت حاجة', TODAY), { kind: 'needs_review', reason: 'المبلغ ناقص', malformed: false });
  eq(r.saved, []);
  let calls = 0;
  const s = new Q.Session({ transport: async () => { calls++; return api(coffee); }, sink: async () => {} });
  eq((await s.propose('   ', TODAY)).kind, 'needs_review'); eq(calls, 0);
});

await test('a good answer gives proposals in order; due_date only for loans', () => {
  const text = JSON.stringify({ transactions: [
    { type: 'loan_received', amount_egp: 1000, category: 'أخرى', person: 'باسم', due_date: '2026-12-01', date: TODAY, note: 'سلفة' },
    { type: 'expense', amount_egp: 1.5, category: 'مواصلات', person: null, due_date: '2026-12-01', date: '2026-11-27', note: 'ميكروباص' },
    { type: 'loan_repaid_received', amount_egp: 200, category: 'أخرى', person: 'سامي', due_date: null, date: TODAY, note: 'رجع' }] });
  const r = Q.readReply(api(text)).result;
  eq(r.kind, 'proposals');
  eq(r.items.map((i) => i.type), ['loan_received', 'expense', 'loan_repaid_received']);
  eq(r.items[0].dueDate, '2026-12-01'); eq(r.items[1].dueDate, null); eq(r.items[1].amountEgp, 1.5); eq(r.items[2].person, 'سامي');
});

await test('usage cost uses the two prices', () => {
  eq([Q.INPUT_USD_PER_MILLION, Q.OUTPUT_USD_PER_MILLION], [0.10, 0.50]);
  const near = (a, b) => assert.ok(Math.abs(a - b) < 1e-12, `${a} vs ${b}`);
  near(Q.costUsd(1e6, 0), 0.10); near(Q.costUsd(0, 1e6), 0.50); near(Q.costUsd(1e6, 1e6), 0.60); near(Q.costUsd(500, 80), 0.00009); near(Q.costUsd(0, 0), 0);
  near(Q.costUsd(1e6, 1e6, 3, 15), 18);
  const log = [{ month: '2026-11', input: 1e6, output: 0 }, { month: '2026-11', input: 0, output: 1e6 }, { month: '2026-10', input: 5e6, output: 5e6 }];
  near(Q.monthCostUsd(log, '2026-11'), 0.60); near(Q.monthCostUsd(log, '2026-10'), 3); near(Q.monthCostUsd(log, '2026-09'), 0);
});

await test('usage is read from the answer and logged per request', async () => {
  eq(Q.readReply(api(coffee, Q.MODEL, 1234, 56)).usage, { input: 1234, output: 56 });
  eq(Q.readReply(JSON.stringify({ model: Q.MODEL, content: [{ type: 'text', text: coffee }] })).usage, null);
  const r = rig(api(coffee, Q.MODEL, 700, 90));
  await r.s.propose('كشري ٦٠', TODAY);
  eq(r.usage, [[{ input: 700, output: 90 }, true]]);
});

await test('nothing is saved until confirm', async () => {
  const r = rig(api(coffee));
  const out = await r.s.propose('كشري ٦٠', TODAY);
  eq(out.kind, 'ready'); eq(out.pending.proposals.length, 1);
  eq(r.saved, []); eq(out.pending.confirmed, false);
  eq(await out.pending.confirm(), 1);
  eq(r.saved.length, 1); eq(r.saved[0].amountEgp, 60); eq(r.saved[0].type, 'expense');
});

await test('confirm saves only the chosen ones and only once; rejected answers never reach the sink', async () => {
  const two = JSON.stringify({ transactions: [10, 20].map((a) => ({ type: 'expense', amount_egp: a, category: 'أكل', person: null, due_date: null, date: TODAY, note: 'x' })) });
  const r = rig(api(two));
  const { pending } = await r.s.propose('x', TODAY);
  eq(await pending.confirm([1]), 1); eq(r.saved.map((p) => p.amountEgp), [20]);
  await assert.rejects(() => pending.confirm(), /already confirmed/);
  eq(r.saved.length, 1);
  const w = rig(api(coffee, 'claude-sonnet-5-5')); await w.s.propose('x', TODAY);
  const m = rig(api('oops')); await m.s.propose('x', TODAY);
  const v = rig(api('{"transactions":[],"needs_review":true,"reason":"x"}')); await v.s.propose('x', TODAY);
  eq([w.saved, m.saved, v.saved], [[], [], []]);
});

await test('the key and usage live in their own storage entry, apart from app data', () => {
  const mem = new Map();
  const ls = { getItem: (k) => mem.get(k) ?? null, setItem: (k, v) => mem.set(k, v) };
  const st = Q.makeStore(ls);
  eq(st.key(), ''); st.setKey('  sk-ant-test '); eq(st.key(), 'sk-ant-test');
  st.addUsage({ input: 1e6, output: 1e6 }, true, '2026-11'); eq(st.monthCost('2026-11') > 0.59 && st.monthCost('2026-11') < 0.61, true); eq(st.monthRequests('2026-11'), 1);
  eq([...mem.keys()], ['pocketta-quickai']);
  st.removeKey(); eq(st.key(), '');
});

console.log(`${n} groups passed`);
