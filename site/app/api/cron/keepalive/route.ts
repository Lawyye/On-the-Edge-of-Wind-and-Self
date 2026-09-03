import { getSupabase } from '@/lib/supabase';

export const runtime = 'nodejs';

/**
 * A free Supabase project is paused after a week without queries, and waking it
 * needs a trip to their dashboard. This site is consulted in bursts — possibly
 * not for weeks — so a daily touch keeps it from going to sleep on the curator.
 */
export async function GET(request: Request) {
  const secret = process.env.CRON_SECRET;
  if (secret) {
    const auth = request.headers.get('authorization');
    if (auth !== `Bearer ${secret}`) {
      return new Response('Unauthorized', { status: 401 });
    }
  }

  const db = getSupabase();
  if (!db) return Response.json({ ok: false, reason: 'not configured' });

  const { error } = await db.from('settings').select('key').limit(1);
  return Response.json({ ok: !error, at: new Date().toISOString() });
}
