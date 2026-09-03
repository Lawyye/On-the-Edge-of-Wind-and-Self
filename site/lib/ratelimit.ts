import 'server-only';
import { createHash } from 'node:crypto';
import { getSupabase } from './supabase';

/**
 * Anti-flood for the anonymous upload form. Moderation is the real barrier —
 * nothing reaches the public site without the curator publishing it — so this
 * exists to keep her review queue usable, not to stop a determined attacker.
 */

export const MAX_PER_HOUR = 5;

/**
 * Raw IPs are never stored. The salt makes the hash useless as a lookup table,
 * which matters because we do not yet know whether submissions carry personal data.
 */
export function hashIp(ip: string): string {
  const salt = process.env.IP_HASH_SALT ?? '';
  return createHash('sha256').update(`${salt}:${ip}`).digest('hex');
}

export function clientIp(request: Request): string {
  const forwarded = request.headers.get('x-forwarded-for');
  if (forwarded) return forwarded.split(',')[0]!.trim();
  return request.headers.get('x-real-ip') ?? 'unknown';
}

export async function isRateLimited(ipHash: string): Promise<boolean> {
  const db = getSupabase();
  if (!db) return false;

  const since = new Date(Date.now() - 60 * 60 * 1000).toISOString();
  const { count, error } = await db
    .from('submissions')
    .select('id', { count: 'exact', head: true })
    .eq('ip_hash', ipHash)
    .gte('created_at', since);

  if (error) return false; // never block a genuine teacher because of a database hiccup
  return (count ?? 0) >= MAX_PER_HOUR;
}
