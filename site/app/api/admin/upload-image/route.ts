import { getSupabase, IMAGES_BUCKET } from '@/lib/supabase';
import { requireAdmin } from '@/lib/auth';
import { checkUpload, safeFileName } from '@/lib/validate-file';

export const runtime = 'nodejs';

/**
 * Photographs the curator uploads for the site's own pages. The returned URL is
 * absolute, so once she replaces a picture the page stops pointing at the old
 * deployment for that slot.
 */
export async function POST(request: Request) {
  const denied = await requireAdmin();
  if (denied) return denied;

  const db = getSupabase();
  if (!db) return Response.json({ error: 'Not configured' }, { status: 503 });

  const form = await request.formData().catch(() => null);
  const file = form?.get('file');
  if (!(file instanceof File)) {
    return Response.json({ error: 'Файл таңдалмаған / Файл не выбран.' }, { status: 400 });
  }

  const bytes = new Uint8Array(await file.arrayBuffer());
  const check = checkUpload(file.name, file.size, bytes);
  if (!check.ok) return Response.json({ error: check.error }, { status: 400 });

  if (!['jpg', 'jpeg', 'png'].includes(check.extension)) {
    return Response.json(
      { error: 'Тек JPG немесе PNG / Только JPG или PNG.' },
      { status: 400 },
    );
  }

  const path = `${crypto.randomUUID()}-${safeFileName(file.name)}`;
  const upload = await db.storage.from(IMAGES_BUCKET).upload(path, bytes, {
    contentType: file.type || 'image/jpeg',
    upsert: false,
  });
  if (upload.error) {
    return Response.json({ error: 'Сурет жүктелмеді / Не удалось загрузить.' }, { status: 502 });
  }

  const { data } = db.storage.from(IMAGES_BUCKET).getPublicUrl(path);
  return Response.json({ ok: true, url: data.publicUrl });
}
