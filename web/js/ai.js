// Miqrish: the AI advisor. The user brings their own key (Gemini, Claude or ChatGPT). The key stays in this browser only
// (never in the saved data or backups) and goes only to the company picked. Same tools and rules as the Android app.
import * as C from './core.js';
import * as R from './store.js';

export const PROVIDERS = {
  GEMINI: { label: 'Gemini (Google)', model: 'gemini-flash-latest', help: 'aistudio.google.com/apikey' },
  CLAUDE: { label: 'Claude (Anthropic)', model: 'claude-haiku-5-5', help: 'console.anthropic.com' },
  OPENAI: { label: 'ChatGPT (OpenAI)', model: 'gpt-4o-mini', help: 'platform.openai.com/api-keys' },
};

const KEY = 'pocketta-ai';
function read() { try { return JSON.parse(localStorage.getItem(KEY) || '{}'); } catch { return {}; } }
function write(o) { try { localStorage.setItem(KEY, JSON.stringify(o)); } catch { /* private mode */ } }
export const cfg = {
  provider: () => (PROVIDERS[read().provider] ? read().provider : 'GEMINI'),
  setProvider: (p) => write({ ...read(), provider: p }),
  key: (p = cfg.provider()) => read().keys?.[p] || '',
  setKey: (p, k) => { const o = read(); write({ ...o, keys: { ...(o.keys || {}), [p]: k.trim() } }); },
  model: (p = cfg.provider()) => read().models?.[p] || PROVIDERS[p].model,
  setModel: (p, m) => { const o = read(); write({ ...o, models: { ...(o.models || {}), [p]: m.trim() } }); },
  notes: () => read().notes || [],
  addNote: (n) => { const t = String(n || '').replace(/\n/g, ' ').trim().slice(0, 200); if (!t) return; const o = read(); write({ ...o, notes: [...new Set([...(o.notes || []), t])].slice(-40) }); },
  clearNotes: () => write({ ...read(), notes: [] }),
};

const P = (name, type, desc, required = false, choices) => ({ name, type, desc, required, choices });
export const TOOLS = [
  { name: 'get_summary', desc: "This month's money picture: income, spent, what is left to spend today, budget, upcoming payments.", params: [], write: false },
  { name: 'spent_by_category', desc: 'Total spent per category in one month.', params: [P('months_ago', 'integer', '0 = this month, 1 = last month')], write: false },
  { name: 'list_recent', desc: 'The latest transactions with their ids.', params: [P('limit', 'integer', 'How many, default 10')], write: false },
  { name: 'list_commitments', desc: "Bills, installments, gam'eyas (savings circles) and open loans with amounts, progress and due dates.", params: [], write: false },
  { name: 'remember', desc: "Save a short fact about the user or their habits so it is remembered next time (for example 'koshary = Food category').", params: [P('note', 'string', 'The fact, one short sentence', true)], write: false },
  { name: 'add_expense', desc: 'Record money the user spent.', write: true, params: [P('amount', 'number', 'Amount in the base currency', true), P('note', 'string', 'What it was, for example koshary', true), P('category', 'string', 'Category name from the list, if clear'), P('days_ago', 'integer', '0 = today, 1 = yesterday')] },
  { name: 'add_income', desc: 'Record money the user received (salary, side work, gift, gam\'eya payout).', write: true, params: [P('amount', 'number', 'Amount', true), P('note', 'string', 'What it was', true), P('category', 'string', 'Income category name'), P('days_ago', 'integer', '0 = today')] },
  { name: 'add_loan', desc: "Record a loan. direction 'borrowed' = the user took money from someone (the user owes). direction 'lent' = the user gave money to someone (they owe the user).", write: true, params: [P('person', 'string', 'The other person', true), P('direction', 'string', 'borrowed or lent', true, ['borrowed', 'lent']), P('amount', 'number', 'Amount', true), P('due_date', 'string', 'Pay back by, YYYY-MM-DD, if said')] },
  { name: 'pay_installment', desc: 'Mark the next installment of a plan as paid.', write: true, params: [P('name', 'string', 'Installment name', true)] },
  { name: 'pay_bill', desc: 'Mark a bill or subscription as paid now.', write: true, params: [P('name', 'string', 'Bill name', true)] },
  { name: 'pay_circle_share', desc: "Mark this month's share of a gam'eya as paid.", write: true, params: [P('name', 'string', "Gam'eya name", true)] },
  { name: 'receive_circle_payout', desc: "Mark that the user collected the gam'eya payout.", write: true, params: [P('name', 'string', "Gam'eya name", true)] },
  { name: 'add_circle', desc: "Start a new gam'eya (savings circle).", write: true, params: [P('name', 'string', 'Name', true), P('share', 'number', 'Monthly share', true), P('months', 'integer', 'Number of months', true), P('my_turn', 'integer', 'Which month the user collects, 1 = first', true), P('first_due', 'string', 'Date of the first share, YYYY-MM-DD', true)] },
  { name: 'update_transaction', desc: 'Change an existing transaction (get the id from list_recent).', write: true, params: [P('id', 'integer', 'Transaction id', true), P('amount', 'number', 'New amount'), P('note', 'string', 'New note'), P('category', 'string', 'New category name')] },
  { name: 'delete_transaction', desc: 'Delete a transaction (get the id from list_recent).', write: true, params: [P('id', 'integer', 'Transaction id', true)] },
];
const schema = (t) => {
  const properties = {};
  for (const p of t.params) properties[p.name] = { type: p.type, description: p.desc, ...(p.choices ? { enum: p.choices } : {}) };
  const s = { type: 'object', properties };
  const req = t.params.filter((p) => p.required).map((p) => p.name);
  if (req.length) s.required = req;
  return s;
};

export class AiError extends Error {
  constructor(code, body) { super(`AI request failed (${code})`); this.code = code; this.body = body; }
  /** The company's own message, short, so a person can see the real reason. */
  detail() {
    try { const j = JSON.parse(this.body); const m = j.error?.message || j[0]?.error?.message || ''; return String(m).replace(/\s+/g, ' ').slice(0, 160); } catch { return ''; }
  }
  friendly() { const d = this.detail(); return this.base() + (d ? ` (${d})` : ''); }
  base() {
    const c = this.code;
    if (c === 400) return 'The company did not accept the request. Check the model name in the assistant settings.';
    if (c === 401 || c === 403) return 'The key was refused. Check that you pasted it fully and that it is still active.';
    if (c === 404) return 'That model name was not found. Check it in the assistant settings.';
    if (c === 429) return 'Too many requests or the free limit is used up. Try again in a minute.';
    if (c >= 500 && c < 600) return "The company's service is busy. Try again in a moment.";
    return `Could not reach the assistant (code ${c}).`;
  }
}

async function post(url, headers, body) {
  const res = await fetch(url, { method: 'POST', headers: { 'Content-Type': 'application/json', ...headers }, body: JSON.stringify(body) });
  const text = await res.text();
  if (!res.ok) throw new AiError(res.status, text.slice(0, 500));
  return JSON.parse(text);
}

// history items: {role:'user',text} | {role:'assistant',text,calls:[{id,name,args}]} | {role:'results',results:[{callId,name,content}]}
async function chat(system, history) {
  const p = cfg.provider(), key = cfg.key(p), model = cfg.model(p);
  const call = (m) => (p === 'GEMINI' ? gemini(key, m, system, history) : p === 'CLAUDE' ? claude(key, m, system, history) : openai(key, m, system, history));
  try { return await call(model); }
  catch (e) {
    // The saved model name may not exist for this key (names change). Find one that does and try once more.
    if (!(e instanceof AiError) || e.code !== 404) throw e;
    const found = await pickModel(p, key);
    if (!found || found === model) throw e;
    cfg.setModel(p, found);
    return call(found);
  }
}

const rankName = (n, fast) => (fast.test(n) ? 0 : 5) + (/preview|-\d{3,}$|\d{4}-\d{2}-\d{2}/.test(n) ? 1 : 0);
const verOf = (n) => parseFloat((/(\d+(?:\.\d+)?)/.exec(n) || [0, 0])[1]);

/** Asks the company which models this key can use and picks a fast one. Returns null when none is found. Throws AiError when the key is refused. */
export async function pickModel(p, key) {
  if (p === 'GEMINI') return geminiPick(key);
  const isClaude = p === 'CLAUDE';
  const res = await fetch(isClaude ? 'https://api.anthropic.com/v1/models?limit=100' : 'https://api.openai.com/v1/models',
    { headers: isClaude ? { 'x-api-key': key, 'anthropic-version': '2023-06-01', 'anthropic-dangerous-direct-browser-access': 'true' } : { Authorization: `Bearer ${key}` } });
  const text = await res.text();
  if (!res.ok) throw new AiError(res.status, text.slice(0, 500));
  const ids = (JSON.parse(text).data || []).map((m) => String(m.id));
  const names = isClaude ? ids.filter((n) => /^claude-/.test(n))
    : ids.filter((n) => /^gpt-/.test(n) && !/(audio|realtime|transcribe|tts|image|search|instruct|codex|vision|embedding|moderation|chat-latest)/.test(n));
  const fast = isClaude ? /haiku/ : /mini/;
  names.sort((a, b) => rankName(a, fast) - rankName(b, fast) || verOf(b) - verOf(a));
  return names[0] || null;
}

/** Asks Gemini which models this key can use and picks a fast one. Returns the model name or null. */
export async function geminiPick(key) {
  const res = await fetch('https://generativelanguage.googleapis.com/v1beta/models?pageSize=200', { headers: { 'x-goog-api-key': key } });
  const text = await res.text();
  if (!res.ok) throw new AiError(res.status, text.slice(0, 500));
  const names = (JSON.parse(text).models || []).filter((m) => (m.supportedGenerationMethods || []).includes('generateContent'))
    .map((m) => String(m.name).replace(/^models\//, '')).filter((n) => /^gemini-/.test(n) && !/(lite|image|tts|live|audio|embedding|thinking|exp|vision|robotics|computer)/.test(n));
  const rank = (n) => (/flash/.test(n) ? 0 : 5) + (/preview|-\d{3}$/.test(n) ? 1 : 0);
  const ver = (n) => parseFloat((/gemini-(\d+(?:\.\d+)?)/.exec(n) || [0, 0])[1]);
  names.sort((a, b) => rank(a) - rank(b) || ver(b) - ver(a));
  return names[0] || null;
}

async function gemini(key, model, system, history) {
  const contents = [];
  for (const m of history) {
    if (m.role === 'user') contents.push({ role: 'user', parts: [{ text: m.text }] });
    else if (m.role === 'assistant') {
      const parts = [];
      if (m.text) parts.push({ text: m.text });
      for (const c of m.calls) parts.push({ functionCall: { name: c.name, args: c.args } });
      if (parts.length) contents.push({ role: 'model', parts });
    } else contents.push({ role: 'user', parts: m.results.map((r) => ({ functionResponse: { name: r.name, response: { result: r.content } } })) });
  }
  const decl = TOOLS.map((t) => ({ name: t.name, description: t.desc, ...(t.params.length ? { parameters: schema(t) } : {}) }));
  const res = await post(`https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`, { 'x-goog-api-key': key },
    { systemInstruction: { parts: [{ text: system }] }, contents, tools: [{ functionDeclarations: decl }] });
  const parts = res.candidates?.[0]?.content?.parts || [];
  return {
    text: parts.map((x) => x.text || '').join('').trim(),
    calls: parts.filter((x) => x.functionCall).map((x, i) => ({ id: `g${Date.now()}${i}`, name: x.functionCall.name, args: x.functionCall.args || {} })),
  };
}

async function claude(key, model, system, history) {
  const messages = [];
  for (const m of history) {
    if (m.role === 'user') messages.push({ role: 'user', content: m.text });
    else if (m.role === 'assistant') {
      const content = [];
      if (m.text) content.push({ type: 'text', text: m.text });
      for (const c of m.calls) content.push({ type: 'tool_use', id: c.id, name: c.name, input: c.args });
      if (content.length) messages.push({ role: 'assistant', content });
    } else messages.push({ role: 'user', content: m.results.map((r) => ({ type: 'tool_result', tool_use_id: r.callId, content: r.content })) });
  }
  const res = await post('https://api.anthropic.com/v1/messages',
    { 'x-api-key': key, 'anthropic-version': '2023-06-01', 'anthropic-dangerous-direct-browser-access': 'true' },
    { model, max_tokens: 1500, system, messages, tools: TOOLS.map((t) => ({ name: t.name, description: t.desc, input_schema: schema(t) })) });
  const content = res.content || [];
  return {
    text: content.filter((b) => b.type === 'text').map((b) => b.text).join('').trim(),
    calls: content.filter((b) => b.type === 'tool_use').map((b) => ({ id: b.id, name: b.name, args: b.input || {} })),
  };
}

async function openai(key, model, system, history) {
  const messages = [{ role: 'system', content: system }];
  for (const m of history) {
    if (m.role === 'user') messages.push({ role: 'user', content: m.text });
    else if (m.role === 'assistant') {
      const o = { role: 'assistant', content: m.text || null };
      if (m.calls.length) o.tool_calls = m.calls.map((c) => ({ id: c.id, type: 'function', function: { name: c.name, arguments: JSON.stringify(c.args) } }));
      messages.push(o);
    } else for (const r of m.results) messages.push({ role: 'tool', tool_call_id: r.callId, content: r.content });
  }
  const res = await post('https://api.openai.com/v1/chat/completions', { Authorization: `Bearer ${key}` },
    { model, messages, tools: TOOLS.map((t) => ({ type: 'function', function: { name: t.name, description: t.desc, parameters: schema(t) } })) });
  const msg = res.choices?.[0]?.message || {};
  const calls = (msg.tool_calls || []).map((c) => { let args = {}; try { args = JSON.parse(c.function.arguments || '{}'); } catch { /* keep empty */ } return { id: c.id, name: c.function.name, args }; });
  return { text: (msg.content || '').trim(), calls };
}

// ---------- Tools ----------

const money = (v) => (v % 1 === 0 ? String(v) : v.toFixed(2));
const toNum = (v) => { const n = Number(v); return Number.isFinite(n) ? n : null; };
const str = (a, k) => (a[k] == null ? '' : String(a[k]).trim());

function describe(name, a) {
  const amt = money(toNum(a.amount) ?? 0);
  switch (name) {
    case 'add_expense': return `Record an expense: ${amt} · ${str(a, 'note')}${str(a, 'category') ? ` · ${str(a, 'category')}` : ''}${toNum(a.days_ago) > 0 ? ` · ${toNum(a.days_ago)} days ago` : ''}`;
    case 'add_income': return `Record income: ${amt} · ${str(a, 'note')}${str(a, 'category') ? ` · ${str(a, 'category')}` : ''}`;
    case 'add_loan': return str(a, 'direction') === 'lent'
      ? `Record a loan you gave: ${amt} to ${str(a, 'person')} (they owe you)`
      : `Record a loan you took: ${amt} from ${str(a, 'person')} (you owe)${str(a, 'due_date') ? ` · pay back by ${str(a, 'due_date')}` : ''}`;
    case 'pay_installment': return `Mark the next installment of ${str(a, 'name')} as paid`;
    case 'pay_bill': return `Mark ${str(a, 'name')} as paid`;
    case 'pay_circle_share': return `Mark your share of ${str(a, 'name')} as paid`;
    case 'receive_circle_payout': return `Mark that you collected the payout of ${str(a, 'name')}`;
    case 'add_circle': return `Start a gam'eya ${str(a, 'name')}: ${money(toNum(a.share) ?? 0)} a month for ${a.months} months, your turn is month ${a.my_turn}`;
    case 'update_transaction': return `Change transaction #${a.id}${toNum(a.amount) != null ? ` · amount ${money(toNum(a.amount))}` : ''}${str(a, 'note') ? ` · note ${str(a, 'note')}` : ''}${str(a, 'category') ? ` · category ${str(a, 'category')}` : ''}`;
    case 'delete_transaction': return `Delete transaction #${a.id}`;
    default: return name;
  }
}

function categoryId(name, kind) {
  if (!name) return null;
  const n = name.toLowerCase(), all = R.categories().filter((c) => c.kind === kind);
  return (all.find((c) => c.name.toLowerCase() === n) || all.find((c) => c.name.toLowerCase().includes(n) || n.includes(c.name.toLowerCase())))?.id ?? null;
}
const byName = (list, name) => { const n = name.toLowerCase(); return list.find((x) => x.name.toLowerCase().includes(n) || n.includes(x.name.toLowerCase())); };
const dayOf = (a) => C.todayEpoch() - Math.max(0, Math.trunc(toNum(a.days_ago) || 0));

function run(name, a) {
  const d = R.data;
  switch (name) {
    case 'get_summary': {
      const h = R.home(), b = h.budget;
      const up = h.upcoming.slice(0, 6).map((u) => `${u.title} ${money(u.amount)} on ${C.isoOf(u.due)}`).join('; ') || 'none';
      return `Income this month ${money(h.income)}; spent ${money(b.spent)}; budget ${money(b.budget)}; committed (still to pay) ${money(b.committed)}; left to spend today ${money(b.leftToday)}; days left ${b.daysLeft}; upcoming: ${up}`;
    }
    case 'spent_by_category': {
      const t = new Date(); t.setMonth(t.getMonth() - Math.max(0, Math.trunc(toNum(a.months_ago) || 0)), 1);
      const from = C.epochOf(t.getFullYear(), t.getMonth() + 1, 1), to = C.addMonths(from, 1, 1);
      const by = {};
      for (const x of d.txns.filter((x) => x.type === 'EXPENSE' && !x.spread && x.epochDay >= from && x.epochDay < to)) {
        const c = R.byId(d.categories, x.categoryId), top = c ? (c.parentId ? R.byId(d.categories, c.parentId) || c : c).name : 'Other';
        by[top] = (by[top] || 0) + R.base(x);
      }
      const rows = Object.entries(by).sort((p, q) => q[1] - p[1]);
      return `Month ${t.getFullYear()}-${t.getMonth() + 1}: ${rows.map(([k, v]) => `${k} ${money(v)}`).join('; ') || 'no spending'}; total ${money(rows.reduce((s, r) => s + r[1], 0))}`;
    }
    case 'list_recent': {
      const lim = Math.min(Math.max(Math.trunc(toNum(a.limit) || 10), 1), 30), today = C.todayEpoch();
      return JSON.stringify(d.txns.filter((x) => x.epochDay >= today - 45).sort((p, q) => q.epochDay - p.epochDay || q.createdAt - p.createdAt).slice(0, lim)
        .map((x) => ({ id: x.id, date: C.isoOf(x.epochDay), type: x.type, amount: R.base(x), note: x.note, category: R.byId(d.categories, x.categoryId)?.name || '' })));
    }
    case 'list_commitments': return JSON.stringify({
      bills: d.bills.map((b) => ({ name: b.name, amount: b.amount, next_due: C.isoOf(b.nextDue) })),
      installments: d.installments.map((i) => ({ name: i.name, amount: i.amount, paid: i.paid, total: i.total })),
      circles: d.circles.map((c) => ({ name: c.name, share: c.share, paid: c.paid, months: c.months, my_turn: c.myTurn, payout_received: c.payoutReceived })),
      loans: d.loans.filter((l) => !l.closed).map((l) => ({ person: l.person, direction: l.direction, remaining: R.remaining(l), due: l.dueDay != null ? C.isoOf(l.dueDay) : '' })),
    });
    case 'remember': cfg.addNote(str(a, 'note')); return 'Saved.';
    case 'add_expense': {
      const amount = toNum(a.amount); if (!(amount > 0)) return 'Missing amount';
      const note = str(a, 'note');
      const cat = categoryId(str(a, 'category'), 'EXPENSE') ?? R.guessCategory(note, 'EXPENSE') ?? (R.S().lastCategory || null);
      R.saveTxn({ type: 'EXPENSE', amount, walletId: R.wallets().find((w) => w.id === R.S().lastWallet)?.id ?? R.wallets()[0]?.id ?? 0, categoryId: cat, note, epochDay: dayOf(a) });
      return 'Saved expense';
    }
    case 'add_income': {
      const amount = toNum(a.amount); if (!(amount > 0)) return 'Missing amount';
      R.saveTxn({ type: 'INCOME', amount, walletId: R.wallets().find((w) => w.id === R.S().lastWallet)?.id ?? R.wallets()[0]?.id ?? 0, categoryId: categoryId(str(a, 'category'), 'INCOME') ?? R.categoryNamed('Other income', 'INCOME'), note: str(a, 'note'), epochDay: dayOf(a) });
      return 'Saved income';
    }
    case 'add_loan': {
      const amount = toNum(a.amount); if (!(amount > 0)) return 'Missing amount';
      const person = str(a, 'person'); if (!person) return 'Missing person';
      const lent = str(a, 'direction') === 'lent';
      R.addLoan({ person, lent, amount, walletId: null, day: C.todayEpoch(), due: C.fromIso(str(a, 'due_date')), note: '' });
      return lent ? `Saved: ${person} owes the user ${money(amount)}` : `Saved: the user owes ${person} ${money(amount)}`;
    }
    case 'pay_installment': { const i = byName(d.installments, str(a, 'name')); if (!i) return 'No installment with that name'; R.payInstallment(i); return `Marked ${i.name} paid`; }
    case 'pay_bill': { const b = byName(d.bills, str(a, 'name')); if (!b) return 'No bill with that name'; R.payBill(b); return `Marked ${b.name} paid`; }
    case 'pay_circle_share': { const c = byName(d.circles, str(a, 'name')); if (!c) return "No gam'eya with that name"; R.payCircle(c); return `Marked the share of ${c.name} paid`; }
    case 'receive_circle_payout': { const c = byName(d.circles, str(a, 'name')); if (!c) return "No gam'eya with that name"; R.receivePayout(c); return `Marked the payout of ${c.name} collected`; }
    case 'add_circle': {
      const share = toNum(a.share), months = Math.trunc(toNum(a.months)), first = C.fromIso(str(a, 'first_due'));
      if (!(share > 0) || !(months > 0) || first == null) return 'Missing share, months or first_due';
      R.saveCircle({ name: str(a, 'name'), share, months, myTurn: Math.min(Math.max(Math.trunc(toNum(a.my_turn) || 1), 1), months), paid: 0, firstDue: first, payoutReceived: false, walletId: null, remindDays: 1 });
      return "Gam'eya saved";
    }
    case 'update_transaction': {
      const t = R.byId(d.txns, toNum(a.id)); if (!t) return 'No transaction with that id';
      const u = { ...t };
      if (toNum(a.amount) > 0) u.amount = toNum(a.amount);
      if (str(a, 'note')) u.note = str(a, 'note');
      const c = categoryId(str(a, 'category'), t.type); if (c) u.categoryId = c;
      R.saveTxn(u); return 'Updated';
    }
    case 'delete_transaction': { const t = R.byId(d.txns, toNum(a.id)); if (!t) return 'No transaction with that id'; R.deleteTxn(t); return 'Deleted'; }
    default: return 'Unknown tool';
  }
}

function systemPrompt() {
  const s = R.S(), cats = R.categories();
  const exp = cats.filter((c) => c.kind === 'EXPENSE' && !c.parentId).map((c) => c.name).join(', ');
  const inc = cats.filter((c) => c.kind === 'INCOME').map((c) => c.name).join(', ');
  const notes = cfg.notes();
  return `You are Miqrish (مِقرِش), a friendly personal money advisor inside the Pocketta app. Today is ${C.isoOf(C.todayEpoch())}. Base currency: ${s.baseCurrency}.
Reply in the user's language. If they write Egyptian Arabic, answer in short, simple Egyptian Arabic. Keep answers short.

How Pocketta works:
- Spending: every expense has an amount, a note and a category. Income: salary, side work, gifts, gam'eya payout, borrowed money.
- Bills and subscriptions repeat (monthly, yearly...). Installments are fixed monthly payments (valU, credit card plans). A gam'eya is a savings circle: the user pays a monthly share and collects the full payout in their turn month.
- Loans: 'borrowed' means the user took money and owes it back (it counts as income this month and a payment due later). 'lent' means the user gave money and is owed it back (it leaves their wallet). The Arabic word سلفة / استلفت / واخد سلفة means borrowed; سلّفت / سلّفته means lent. If it is unclear which one the user means, ask before saving.
- Expense categories: ${exp}
- Income categories: ${inc}

Rules:
- To record or change anything, call the matching tool. The app shows the user a confirmation first, so call the tool directly instead of asking in words, but only when you have the amount and what it was. If something is missing or unclear, ask one short question.
- Never invent amounts, ids or names. Use list_recent or list_commitments to find them.
- For questions about their money, call get_summary, spent_by_category or list_commitments, then answer from the numbers.
- When the user corrects you or tells you a habit, call remember with one short sentence.${notes.length ? `\n\nWhat you already know about this user:\n${notes.map((n) => `- ${n}`).join('\n')}` : ''}`;
}

/** One chat. [confirm](text) resolves true/false, [show](role, text) draws a bubble. */
export function createSession({ confirm, show }) {
  const history = [];
  return {
    async send(text) {
      history.push({ role: 'user', text });
      const system = systemPrompt();
      for (let round = 0; round < 8; round++) {
        const reply = await chat(system, history);
        history.push({ role: 'assistant', text: reply.text, calls: reply.calls });
        if (reply.text) show('ai', reply.text);
        if (!reply.calls.length) return;
        const results = [];
        for (const c of reply.calls) {
          const tool = TOOLS.find((t) => t.name === c.name);
          let out;
          if (!tool) out = 'Unknown tool';
          else if (tool.write) {
            const what = describe(c.name, c.args);
            if (await confirm(what)) {
              try { out = run(c.name, c.args); } catch (e) { out = `Failed: ${e.message || 'error'}`; }
              show('done', what);
            } else out = 'The user said no. Nothing was saved.';
          } else { try { out = run(c.name, c.args); } catch (e) { out = `Failed: ${e.message || 'error'}`; } }
          results.push({ callId: c.id, name: c.name, content: out });
        }
        history.push({ role: 'results', results });
      }
    },
  };
}
