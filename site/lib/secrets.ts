import 'server-only';
import { randomBytes } from 'node:crypto';
import { getSupabase } from './supabase';

/**
 * Server-side secrets that have no reason to be typed by a human.
 *
 * The curator sets this site up from a phone, so every environment variable we
 * can avoid asking for is one less step that can go wrong. These two are just
 * random strings the app needs to be stable across restarts, not credentials
 * anyone chooses — so the app mints them once and keeps them in the database.
 *
 * An environment variable of the same name still wins, which keeps the door open
 * for rotating a secret without touching the database.
 */

export type SecretKey = 'auth_secret' | 'ip_hash_salt';

const ENV_NAME: Record<SecretKey, string> = {
  auth_secret: 'AUTH_SECRET',
  ip_hash_salt: 'IP_HASH_SALT',
};

const cache = new Map<SecretKey, string>();

export async function getSecret(key: SecretKey): Promise<string> {
  const fromEnv = process.env[ENV_NAME[key]];
  if (fromEnv) return fromEnv;

  const cached = cache.get(key);
  if (cached) return cached;

  const db = getSupabase();
  if (!db) throw new Error(`${ENV_NAME[key]} is not set and the database is unavailable`);

  const existing = await db.from('settings').select('value').eq('key', key).maybeSingle();
  if (typeof existing.data?.value === 'string' && existing.data.value.length > 0) {
    cache.set(key, existing.data.value);
    return existing.data.value;
  }

  // Two requests can reach this at the same time on a cold start. Inserting
  // without overwriting means the first one wins and the loser re-reads it,
  // so both end up using the same value rather than one silently invalidating
  // the other's sessions.
  const minted = randomBytes(32).toString('hex');
  await db.from('settings').upsert({ key, value: minted }, { ignoreDuplicates: true });

  const settled = await db.from('settings').select('value').eq('key', key).maybeSingle();
  const value = typeof settled.data?.value === 'string' ? settled.data.value : minted;
  cache.set(key, value);
  return value;
}
