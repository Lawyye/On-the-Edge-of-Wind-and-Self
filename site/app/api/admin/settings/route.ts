import { requireAdmin } from '@/lib/auth';
import { getSettings, saveSettings } from '@/lib/content';

export const runtime = 'nodejs';

export async function GET() {
  const denied = await requireAdmin();
  if (denied) return denied;
  return Response.json({ settings: await getSettings() });
}

export async function PUT(request: Request) {
  const denied = await requireAdmin();
  if (denied) return denied;

  const body = await request.json().catch(() => null);
  if (typeof body?.submissions_open !== 'boolean' || typeof body?.auto_publish !== 'boolean') {
    return Response.json({ error: 'Дұрыс емес сұраныс / Некорректный запрос.' }, { status: 400 });
  }

  try {
    await saveSettings({
      submissions_open: body.submissions_open,
      auto_publish: body.auto_publish,
    });
  } catch (error) {
    return Response.json({ error: (error as Error).message }, { status: 502 });
  }

  return Response.json({ ok: true });
}
