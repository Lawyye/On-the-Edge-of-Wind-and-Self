import { getSupabase, MATERIALS_BUCKET } from '@/lib/supabase';
import { requireAdmin } from '@/lib/auth';

export const runtime = 'nodejs';

const STATUSES = ['pending', 'published', 'rejected'] as const;

export async function GET() {
  const denied = await requireAdmin();
  if (denied) return denied;

  const db = getSupabase();
  if (!db) return Response.json({ submissions: [] });

  const { data, error } = await db
    .from('submissions')
    // ip_hash is deliberately not selected — the panel never needs it.
    .select('id, created_at, full_name, organization, region, event_route, title, description, file_name, file_size, mime, status')
    .order('created_at', { ascending: false })
    .limit(500);

  if (error) return Response.json({ error: error.message }, { status: 502 });
  return Response.json({ submissions: data ?? [] });
}

export async function PATCH(request: Request) {
  const denied = await requireAdmin();
  if (denied) return denied;

  const body = await request.json().catch(() => null);
  const id = typeof body?.id === 'string' ? body.id : '';
  const status = body?.status;

  if (!id || !STATUSES.includes(status)) {
    return Response.json({ error: 'Дұрыс емес сұраныс / Некорректный запрос.' }, { status: 400 });
  }

  const db = getSupabase();
  if (!db) return Response.json({ error: 'Not configured' }, { status: 503 });

  const { error } = await db.from('submissions').update({ status }).eq('id', id);
  if (error) return Response.json({ error: error.message }, { status: 502 });

  return Response.json({ ok: true });
}

export async function DELETE(request: Request) {
  const denied = await requireAdmin();
  if (denied) return denied;

  const id = new URL(request.url).searchParams.get('id');
  if (!id) return Response.json({ error: 'id required' }, { status: 400 });

  const db = getSupabase();
  if (!db) return Response.json({ error: 'Not configured' }, { status: 503 });

  // Remove the stored file too, so deleting really deletes.
  const { data } = await db.from('submissions').select('file_path').eq('id', id).maybeSingle();
  if (data?.file_path) {
    await db.storage.from(MATERIALS_BUCKET).remove([data.file_path]);
  }

  const { error } = await db.from('submissions').delete().eq('id', id);
  if (error) return Response.json({ error: error.message }, { status: 502 });

  return Response.json({ ok: true });
}
