import { requireAdmin } from '@/lib/auth';
import { saveContent } from '@/lib/content';
import type { SiteContent } from '@/lib/types';

export const runtime = 'nodejs';

export async function PUT(request: Request) {
  const denied = await requireAdmin();
  if (denied) return denied;

  const body = await request.json().catch(() => null);
  if (!body?.site || !body?.home || !body?.curatorsPage || !Array.isArray(body?.events)) {
    return Response.json({ error: 'Мазмұн құрылымы дұрыс емес / Некорректная структура.' }, { status: 400 });
  }

  try {
    await saveContent(body as SiteContent);
  } catch (error) {
    return Response.json({ error: (error as Error).message }, { status: 502 });
  }

  return Response.json({ ok: true });
}
