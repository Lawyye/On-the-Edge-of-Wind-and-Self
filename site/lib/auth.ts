import 'server-only';
import { randomBytes, scrypt as scryptCb, timingSafeEqual } from 'node:crypto';
import { promisify } from 'node:util';
import { cookies } from 'next/headers';
import { SignJWT, jwtVerify } from 'jose';
import { getSupabase } from './supabase';

const scrypt = promisify(scryptCb) as (
  password: string,
  salt: Buffer,
  keylen: number,
) => Promise<Buffer>;

export const SESSION_COOKIE = 'orleu_admin';
const SESSION_DAYS = 7;
const KEY_LENGTH = 64;
const PASSWORD_SETTING_KEY = 'admin_password_hash';

/** Minimum the curator's password must satisfy, matching the admin UI copy. */
export const MIN_PASSWORD_LENGTH = 10;

function secret(): Uint8Array {
  const value = process.env.AUTH_SECRET;
  if (!value) throw new Error('AUTH_SECRET is not set');
  return new TextEncoder().encode(value);
}

export async function hashPassword(password: string): Promise<string> {
  const salt = randomBytes(16);
  const derived = await scrypt(password, salt, KEY_LENGTH);
  return `scrypt:${salt.toString('hex')}:${derived.toString('hex')}`;
}

export async function verifyPassword(password: string, stored: string): Promise<boolean> {
  const [scheme, saltHex, hashHex] = stored.split(':');
  if (scheme !== 'scrypt' || !saltHex || !hashHex) return false;

  const derived = await scrypt(password, Buffer.from(saltHex, 'hex'), KEY_LENGTH);
  const expected = Buffer.from(hashHex, 'hex');
  if (expected.length !== derived.length) return false;
  return timingSafeEqual(derived, expected);
}

/** Constant-time compare for the bootstrap password, which is a plain env value. */
function safeEqual(a: string, b: string): boolean {
  const left = Buffer.from(a);
  const right = Buffer.from(b);
  if (left.length !== right.length) return false;
  return timingSafeEqual(left, right);
}

async function storedPasswordHash(): Promise<string | null> {
  const db = getSupabase();
  if (!db) return null;
  const { data } = await db
    .from('settings')
    .select('value')
    .eq('key', PASSWORD_SETTING_KEY)
    .maybeSingle();
  const value = data?.value;
  return typeof value === 'string' && value.length > 0 ? value : null;
}

/**
 * Accepts either the password the curator set herself, or the bootstrap password
 * from the environment. The bootstrap route stays open on purpose: it is the only
 * way back in if she forgets her password, and rotating it is a one-field change
 * in the Vercel dashboard rather than a database repair.
 */
export async function checkAdminPassword(password: string): Promise<boolean> {
  if (!password) return false;

  const stored = await storedPasswordHash();
  if (stored && (await verifyPassword(password, stored))) return true;

  const bootstrap = process.env.ADMIN_BOOTSTRAP_PASSWORD;
  if (bootstrap && safeEqual(password, bootstrap)) return true;

  return false;
}

export async function setAdminPassword(password: string): Promise<void> {
  const db = getSupabase();
  if (!db) throw new Error('Supabase is not configured');
  const hash = await hashPassword(password);
  const { error } = await db.from('settings').upsert({ key: PASSWORD_SETTING_KEY, value: hash });
  if (error) throw new Error(error.message);
}

export async function createSession(): Promise<void> {
  const token = await new SignJWT({ role: 'admin' })
    .setProtectedHeader({ alg: 'HS256' })
    .setIssuedAt()
    .setExpirationTime(`${SESSION_DAYS}d`)
    .sign(secret());

  const jar = await cookies();
  jar.set(SESSION_COOKIE, token, {
    httpOnly: true,
    secure: process.env.NODE_ENV === 'production',
    sameSite: 'lax',
    path: '/',
    maxAge: SESSION_DAYS * 24 * 60 * 60,
  });
}

export async function destroySession(): Promise<void> {
  const jar = await cookies();
  jar.delete(SESSION_COOKIE);
}

export async function isAuthenticated(): Promise<boolean> {
  const jar = await cookies();
  const token = jar.get(SESSION_COOKIE)?.value;
  if (!token) return false;
  try {
    await jwtVerify(token, secret());
    return true;
  } catch {
    return false;
  }
}

/** Guard for admin API routes. Returns a 401 Response when the caller is not signed in. */
export async function requireAdmin(): Promise<Response | null> {
  if (await isAuthenticated()) return null;
  return Response.json({ error: 'Рұқсат жоқ / Нет доступа' }, { status: 401 });
}
