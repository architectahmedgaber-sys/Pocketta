// A saved AI key must never be inside the backup / export JSON, which is also what cloud sync snapshots.
// Run: node tests/web/key-privacy.test.mjs
import assert from 'node:assert/strict';
const mem = new Map();
globalThis.localStorage = { getItem: (k) => mem.get(k) ?? null, setItem: (k, v) => mem.set(k, v), removeItem: (k) => mem.delete(k) };
const SECRET = 'sk-ant-api03-TEST-SECRET-KEY-1234567890';
// Where a key could sit in this browser: Miqrish's own entry (and, defensively, any Quick AI entry).
mem.set('pocketta-ai', JSON.stringify({ provider: 'CLAUDE', keys: { CLAUDE: SECRET } }));
mem.set('pocketta-quickai', JSON.stringify({ key: SECRET }));
const R = await import('../../web/js/store.js');
const backup = R.exportJson();
assert.ok(backup.length > 10 && JSON.parse(backup).settings, 'export works');
assert.ok(!backup.includes(SECRET), 'the key string must not be in the backup/export JSON');
assert.ok(!backup.includes('sk-ant'), 'no key-looking text in the backup/export JSON');
assert.ok(!/pocketta-ai|pocketta-quickai/.test(backup), 'no key storage name in the backup/export JSON');
// The cloud-sync payload (before encryption) is built from the same export.
const Sync = await import('../../web/js/sync.js');
const payload = JSON.stringify(Sync.snapshot());
assert.ok(payload.length > 10 && JSON.parse(payload).settings, 'sync snapshot works');
assert.ok(!payload.includes(SECRET) && !payload.includes('sk-ant') && !/pocketta-ai|pocketta-quickai/.test(payload), 'the key must not be in the sync payload');
console.log('KEY PRIVACY (web export + sync payload) PASS');
