// Quick AI entry (web): one typed Egyptian-Arabic sentence -> proposed transactions -> the user confirms. Same rules as core/QuickAi.kt:
// one pinned model checked in every answer, strict JSON shape, usage + cost counting, and nothing saved before confirm().
// The key lives only in this browser (localStorage 'pocketta-quickai'), never in the saved data, backups or cloud sync.

/** The ONLY model this feature may use. No setting, fallback or alias changes it. Never a Sonnet or Opus model. */
export const MODEL = 'claude-haiku-5-5';
export const API_URL = 'https://api.anthropic.com/v1/messages';
export const API_VERSION = '2023-06-01';
export const MAX_TOKENS = 1024;
/** Price per million tokens in US dollars. Source: https://www.anthropic.com/claude-haiku-5-5 . Edit here when the price changes. */
export const INPUT_USD_PER_MILLION = 0.10;
export const OUTPUT_USD_PER_MILLION = 0.50;
export const PRICE_SOURCE = 'https://www.anthropic.com/claude-haiku-5-5';
export const MAX_TRANSACTIONS = 20;
export const TYPES = ['expense', 'income', 'loan_received', 'loan_given', 'loan_repaid_given', 'loan_repaid_received'];

/** The parser prompt, exactly as written by the owner (prompt.txt). Kept identical to the Android copy (a test compares them). */
export const SYSTEM_PROMPT = String.raw`You are the transaction parser inside Pocketta, a personal money app for Egyptian users.

The user writes short messages in Egyptian colloquial Arabic describing money events: expenses, income, loans borrowed, loans lent, and loan repayments.

Convert each message into JSON with this exact shape:
{
  "transactions": [
    {
      "type": "expense" | "income" | "loan_received" | "loan_given" | "loan_repaid_given" | "loan_repaid_received",
      "amount_egp": <number>,
      "category": "<one short Arabic word>",
      "person": "<name as written, or null>",
      "due_date": "<YYYY-MM-DD or null>",
      "date": "<YYYY-MM-DD>",
      "note": "<very short Arabic note>"
    }
  ]
}

Rules:
- Output JSON only. No explanation, no markdown fences, no text before or after the JSON.
- Types:
  - expense = money the user spent. income = money the user received as earnings.
  - "استلفت من X" / "اتسلفت من X" = loan_received (user borrowed, money in).
  - "سلفت X" / "اديت X" / "اديت لـ X" = loan_given (user lent, money out).
  - "رجعت لـ X" / "سددت لـ X" = loan_repaid_given (user paid back a debt).
  - "X رجعلي" / "X سددلي" = loan_repaid_received (someone paid the user back).
- amount_egp is a plain number in Egyptian pounds: "1000ج" = 1000, "جنيه ونص" = 1.5, Arabic-Indic digits (٧٥) = 75. Strip any "ج", "جنيه", "جنية" suffix.
- date: the user message starts with "today=YYYY-MM-DD". "النهاردة" = today, "امبارح" = today minus 1 day, "بكرة" = today plus 1 day. Default is today.
- due_date only for loans (loan_received, loan_given). Resolve relative dates against today: "أول الشهر الجاي" = the 1st of next month, "١٥ الشهر الجاي" = the 15th of next month, "بكرة" = tomorrow. If no repayment date is mentioned, null.
- category: one short Arabic word such as أكل, بقالة, مواصلات, بنزين, فواتير, صحة, ترفيه, تسوق, هدايا, مرتب, شغل, أخرى.
- If the user corrects themselves ("لأ مش ٥٠ دي ١٥٠"), use only the corrected amount.
- If a future plan is described without money already spent, or the amount is missing, or the message is not about money at all, return:
  {"transactions": [], "needs_review": true, "reason": "<short Arabic explanation>"}
- One message can contain several transactions. Keep them in the order mentioned.`;

export const userMessage = (today, text) => `today=${today}\n${String(text).trim()}`;
export const requestBody = (today, text) => ({
  model: MODEL, max_tokens: MAX_TOKENS, system: SYSTEM_PROMPT,
  messages: [{ role: 'user', content: userMessage(today, text) }],
});

// ---------- cost ----------
export const costUsd = (input, output, inPrice = INPUT_USD_PER_MILLION, outPrice = OUTPUT_USD_PER_MILLION) =>
  (input * inPrice) / 1e6 + (output * outPrice) / 1e6;
export const monthCostUsd = (entries, month) => entries.filter((e) => e.month === month).reduce((a, e) => a + costUsd(e.input, e.output), 0);

// ---------- answer ----------
const isObj = (v) => v !== null && typeof v === 'object' && !Array.isArray(v);
const num = (v) => typeof v === 'number' && Number.isFinite(v);
function isoDate(v) {
  if (typeof v !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(v)) return null;
  const [y, m, d] = v.split('-').map(Number);
  const t = new Date(Date.UTC(y, m - 1, d));
  return t.getUTCFullYear() === y && t.getUTCMonth() === m - 1 && t.getUTCDate() === d ? v : null;
}
const malformed = (why) => ({ kind: 'needs_review', reason: `مقدرتش أفهم الرد (${why})، راجع الرسالة`, malformed: true });

/** The model's text must be exactly the JSON of the prompt's shape. Anything else (fences, extra text, bad fields) is rejected. */
export function parseTransactions(text) {
  let root;
  try { root = JSON.parse(text); } catch { return malformed('not JSON'); }
  if (!isObj(root)) return malformed('not an object');
  const list = root.transactions;
  if (!Array.isArray(list)) return malformed('no transactions');
  if (!list.length) return root.needs_review === true && typeof root.reason === 'string' ? { kind: 'needs_review', reason: root.reason, malformed: false } : malformed('empty');
  if (root.needs_review === true) return malformed('both');
  if (list.length > MAX_TRANSACTIONS) return malformed('too many');
  const items = [];
  for (const o of list) {
    if (!isObj(o)) return malformed('item');
    if (!TYPES.includes(o.type)) return malformed('type');
    if (!num(o.amount_egp) || o.amount_egp <= 0 || o.amount_egp > 1e9) return malformed('amount');
    if (typeof o.category !== 'string') return malformed('category');
    if (typeof o.note !== 'string') return malformed('note');
    if (o.person != null && typeof o.person !== 'string') return malformed('person');
    const date = isoDate(o.date); if (!date) return malformed('date');
    let due = null;
    if (o.due_date != null) { due = isoDate(o.due_date); if (!due) return malformed('due_date'); }
    const loan = o.type === 'loan_received' || o.type === 'loan_given';
    items.push({ type: o.type, amountEgp: o.amount_egp, category: o.category.trim(), person: (o.person || '').trim() || null, dueDate: loan ? due : null, date, note: o.note.trim() });
  }
  return { kind: 'proposals', items };
}

/** Reads one raw answer of the Messages API. Never throws. */
export function readReply(body) {
  let root;
  try { root = typeof body === 'string' ? JSON.parse(body) : body; } catch { root = null; }
  if (!isObj(root)) return { usage: null, result: { kind: 'needs_review', reason: 'الرد مش مفهوم', malformed: true } };
  const u = root.usage;
  const usage = isObj(u) && num(u.input_tokens) && num(u.output_tokens) && u.input_tokens >= 0 && u.output_tokens >= 0 ? { input: u.input_tokens, output: u.output_tokens } : null;
  // The pinned model is checked first. Any other model: the whole answer is discarded.
  if (root.model !== MODEL) return { usage, result: { kind: 'wrong_model', got: typeof root.model === 'string' ? root.model : null } };
  const text = (Array.isArray(root.content) ? root.content : []).filter((b) => isObj(b) && b.type === 'text').map((b) => (typeof b.text === 'string' ? b.text : '')).join('');
  return { usage, result: parseTransactions(text) };
}

// ---------- session: nothing is saved before confirm ----------
export class Pending {
  #sink; #done = false;
  constructor(proposals, sink) { this.proposals = proposals; this.#sink = sink; }
  get confirmed() { return this.#done; }
  /** The only place a transaction is handed to the data. Saves the chosen proposals (all by default), once. */
  async confirm(selected = this.proposals.map((_, i) => i)) {
    if (this.#done) throw new Error('already confirmed');
    this.#done = true;
    let n = 0;
    for (const i of [...selected].sort((a, b) => a - b)) { if (this.proposals[i]) { await this.#sink(this.proposals[i]); n++; } }
    return n;
  }
}

export class Session {
  /** transport(bodyObject) -> raw answer text; sink(proposal) saves one; onUsage(usage, ok); onLog(line). */
  constructor({ transport, sink, onUsage = () => {}, onLog = () => {} }) { this.transport = transport; this.sink = sink; this.onUsage = onUsage; this.onLog = onLog; }
  /** Asks the model. Proposes only: the sink is not touched here. */
  async propose(text, today) {
    if (!String(text || '').trim()) return { kind: 'needs_review', reason: 'الرسالة فاضية', malformed: false };
    const reply = readReply(await this.transport(requestBody(today, text)));
    const r = reply.result;
    if (reply.usage) this.onUsage(reply.usage, r.kind !== 'wrong_model');
    if (r.kind === 'wrong_model') {
      this.onLog(`Discarded an answer from model '${r.got}' (expected '${MODEL}').`);
      return { kind: 'wrong_model', got: r.got, message: 'The answer came from a different model than expected, so it was thrown away. Nothing was saved.' };
    }
    if (r.kind === 'needs_review') { if (r.malformed) this.onLog('Discarded an answer that was not valid JSON of the expected shape.'); return r; }
    return { kind: 'ready', pending: new Pending(r.items, this.sink) };
  }
}

// ---------- browser storage: key + usage log (own localStorage entry, never in the app data) ----------
const KEY = 'pocketta-quickai';
export function makeStore(ls = globalThis.localStorage) {
  const read = () => { try { return JSON.parse(ls.getItem(KEY) || '{}'); } catch { return {}; } };
  const write = (o) => { try { ls.setItem(KEY, JSON.stringify(o)); } catch { /* private mode */ } };
  return {
    key: () => (typeof read().key === 'string' ? read().key : ''),
    setKey: (k) => { const o = read(); const t = String(k || '').trim(); if (t) write({ ...o, key: t }); else { delete o.key; write(o); } },
    removeKey: () => { const o = read(); delete o.key; write(o); },
    entries: () => (Array.isArray(read().usage) ? read().usage : []),
    addUsage: (u, ok = true, month = new Date().toISOString().slice(0, 7)) => { const o = read(); write({ ...o, usage: [...(o.usage || []), { month, input: u.input, output: u.output, ok }].slice(-600) }); },
    monthCost(month = new Date().toISOString().slice(0, 7)) { return monthCostUsd(this.entries(), month); },
    monthRequests(month = new Date().toISOString().slice(0, 7)) { return this.entries().filter((e) => e.month === month).length; },
    addLog: (line) => { const o = read(); write({ ...o, log: [...(o.log || []), `${new Date().toISOString()} ${line}`].slice(-30) }); console.warn('QuickAi', line); },
    log: () => read().log || [],
  };
}

export class HttpError extends Error {
  constructor(code) { super(`Quick AI request failed (${code})`); this.code = code; }
  friendly() {
    const c = this.code;
    if (c === 401 || c === 403) return 'The key was refused. Check that you pasted it fully and that it is still active.';
    if (c === 429) return 'Too many requests, or the balance on your Anthropic account is used up. Try again in a minute.';
    if (c >= 500 && c < 600) return 'The service is busy. Try again in a moment.';
    return `Could not reach the assistant (code ${c}).`;
  }
}
/** Sends the request to Anthropic. The model is fixed inside requestBody(); nothing here can change it and nothing retries with another model. */
export const httpTransport = (key) => async (body) => {
  const res = await fetch(API_URL, {
    method: 'POST',
    headers: { 'content-type': 'application/json', 'x-api-key': key, 'anthropic-version': API_VERSION, 'anthropic-dangerous-direct-browser-access': 'true' },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new HttpError(res.status);
  return res.text();
};
