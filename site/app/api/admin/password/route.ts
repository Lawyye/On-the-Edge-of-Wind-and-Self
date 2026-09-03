import { MIN_PASSWORD_LENGTH, requireAdmin, setAdminPassword } from '@/lib/auth';

export const runtime = 'nodejs';

export async function PUT(request: Request) {
  const denied = await requireAdmin();
  if (denied) return denied;

  const body = await request.json().catch(() => null);
  const password = typeof body?.password === 'string' ? body.password : '';

  if (password.length < MIN_PASSWORD_LENGTH) {
    return Response.json(
      { error: `Құпия сөз кемінде ${MIN_PASSWORD_LENGTH} таңба / Пароль не короче ${MIN_PASSWORD_LENGTH} символов.` },
      { status: 400 },
    );
  }

  try {
    await setAdminPassword(password);
  } catch (error) {
    return Response.json({ error: (error as Error).message }, { status: 502 });
  }

  return Response.json({ ok: true });
}
