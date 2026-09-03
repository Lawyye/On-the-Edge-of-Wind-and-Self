import { getSupabase, MATERIALS_BUCKET } from '@/lib/supabase';
import { isAuthenticated } from '@/lib/auth';

export const runtime = 'nodejs';

/**
 * Materials live in a private bucket. This hands out a short-lived signed URL,
 * and only for a document that is actually published — so a pending or rejected
 * upload stays invisible even to someone who guesses its id.
 */
export async function GET(
  _request: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const { id } = await params;
  const db = getSupabase();
  if (!db) return new Response('Not configured', { status: 503 });

  const { data, error } = await db
    .from('submissions')
    .select('file_path, status')
    .eq('id', id)
    .maybeSingle();

  if (error || !data?.file_path) return new Response('Not found', { status: 404 });

  if (data.status !== 'published' && !(await isAuthenticated())) {
    return new Response('Not found', { status: 404 });
  }

  const signed = await db.storage
    .from(MATERIALS_BUCKET)
    .createSignedUrl(data.file_path, 60 * 60);

  if (signed.error || !signed.data) return new Response('Not found', { status: 404 });

  return Response.redirect(signed.data.signedUrl, 302);
}
