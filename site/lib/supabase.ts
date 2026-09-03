// Importing this from a client component is a build error rather than a silent
// mistake. Next.js would not inline a non-public env var into the browser bundle
// anyway, so the key could not actually leak — but the failure would be a
// confusing "undefined key" at runtime instead of a clear one at compile time.
import 'server-only';
import { createClient, type SupabaseClient } from '@supabase/supabase-js';

/**
 * Server-only Supabase client. It uses the service-role key, so it must never be
 * imported from a client component — nothing here is exposed to the browser, and
 * every write goes through an API route that checks the caller first.
 */

export const MATERIALS_BUCKET = 'materials';
export const IMAGES_BUCKET = 'images';

let cached: SupabaseClient | null = null;

export function isConfigured(): boolean {
  return Boolean(process.env.SUPABASE_URL && process.env.SUPABASE_SERVICE_ROLE_KEY);
}

/**
 * Returns null when Supabase is not wired up yet, rather than throwing. The site
 * then falls back to the bundled seed content and stays viewable — a half-built
 * site that renders is far more useful to the curator than a white screen.
 */
export function getSupabase(): SupabaseClient | null {
  if (!isConfigured()) return null;
  if (cached) return cached;
  cached = createClient(process.env.SUPABASE_URL!, process.env.SUPABASE_SERVICE_ROLE_KEY!, {
    auth: { persistSession: false, autoRefreshToken: false },
  });
  return cached;
}
