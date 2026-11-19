// End-to-end encryption for the cloud backup. Everything here runs on the device: the cloud only ever sees the sealed envelope.
// A random data key (DEK) encrypts the backup with AES-256-GCM. The DEK is itself locked twice: with a key made from the
// person's encryption password and with a random recovery key (PBKDF2-SHA256 -> AES-256-GCM). The Android app does the same
// with javax.crypto, so both sides read each other's envelopes. Nothing outside the device can open it without the password or recovery key.
const enc = new TextEncoder(), dec = new TextDecoder();
const AAD = enc.encode('pocketta-e2ee-1');
export const PASS_ITER = 600000, REC_ITER = 20000;
const ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';

export const toB64 = (buf) => {
  const u = buf instanceof Uint8Array ? buf : new Uint8Array(buf);
  let s = '';
  for (let i = 0; i < u.length; i += 0x8000) s += String.fromCharCode.apply(null, u.subarray(i, i + 0x8000));
  return btoa(s);
};
export const fromB64 = (s) => Uint8Array.from(atob(s), (c) => c.charCodeAt(0));
const rand = (n) => crypto.getRandomValues(new Uint8Array(n));

const normPass = (s) => String(s).normalize('NFKC');
export const normRecovery = (s) => String(s).toUpperCase().replace(/[^A-Z0-9]/g, '');

async function kek(secret, salt, iter) {
  const base = await crypto.subtle.importKey('raw', enc.encode(secret), 'PBKDF2', false, ['deriveKey']);
  return crypto.subtle.deriveKey({ name: 'PBKDF2', hash: 'SHA-256', salt, iterations: iter }, base, { name: 'AES-GCM', length: 256 }, false, ['encrypt', 'decrypt']);
}
const seal = async (key, bytes) => {
  const iv = rand(12);
  const ct = await crypto.subtle.encrypt({ name: 'AES-GCM', iv, additionalData: AAD }, key, bytes);
  return { iv: toB64(iv), ct: toB64(ct) };
};
const open = (key, o) => crypto.subtle.decrypt({ name: 'AES-GCM', iv: fromB64(o.iv), additionalData: AAD }, key, fromB64(o.ct));
const dataKey = (dekB64) => crypto.subtle.importKey('raw', fromB64(dekB64), 'AES-GCM', false, ['encrypt', 'decrypt']);

export function newRecoveryKey() {
  const r = rand(20);
  let s = '';
  for (const b of r) s += ALPHABET[b % 32];
  return s.match(/.{5}/g).join('-');
}

/** First setup: a new data key, locked with the password and with a fresh recovery key. Returns what to keep and the key to show. */
export async function createKeys(password, recovery = newRecoveryKey()) {
  const dek = rand(32);
  const salt = rand(16), recSalt = rand(16);
  const keys = {
    iter: PASS_ITER, salt: toB64(salt), wrapPass: await seal(await kek(normPass(password), salt, PASS_ITER), dek),
    recIter: REC_ITER, recSalt: toB64(recSalt), wrapRec: await seal(await kek(normRecovery(recovery), recSalt, REC_ITER), dek),
  };
  return { dek: toB64(dek), keys, recovery };
}

/** Opens the data key from an envelope with the password or the recovery key. Throws if it is wrong. */
export async function unlock(env, mode, secret) {
  const bytes = mode === 'rec'
    ? await open(await kek(normRecovery(secret), fromB64(env.recSalt), env.recIter), env.wrapRec)
    : await open(await kek(normPass(secret), fromB64(env.salt), env.iter), env.wrapPass);
  return toB64(bytes);
}

/** A new recovery key for the same data key (the old one stops working once this is uploaded). */
export async function withRecovery(dekB64, keys, recovery) {
  const recSalt = rand(16);
  return { ...keys, recIter: REC_ITER, recSalt: toB64(recSalt), wrapRec: await seal(await kek(normRecovery(recovery), recSalt, REC_ITER), fromB64(dekB64)) };
}

/** After a recovery: lock the same data key with a new password (the recovery key stays the same). */
export async function rewrap(dekB64, keys, newPassword) {
  const salt = rand(16);
  return { ...keys, iter: PASS_ITER, salt: toB64(salt), wrapPass: await seal(await kek(normPass(newPassword), salt, PASS_ITER), fromB64(dekB64)) };
}

export async function encryptData(dekB64, keys, obj) {
  const sealed = await seal(await dataKey(dekB64), enc.encode(JSON.stringify(obj)));
  return { enc: 1, ...keys, iv: sealed.iv, ct: sealed.ct };
}
export async function decryptData(dekB64, env) {
  return JSON.parse(dec.decode(await open(await dataKey(dekB64), { iv: env.iv, ct: env.ct })));
}
export const keysOf = (env) => ({ iter: env.iter, salt: env.salt, wrapPass: env.wrapPass, recIter: env.recIter, recSalt: env.recSalt, wrapRec: env.wrapRec });
