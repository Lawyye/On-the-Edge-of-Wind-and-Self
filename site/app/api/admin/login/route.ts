import { checkAdminPassword, createSession, destroySession } from '@/lib/auth';

export const runtime = 'nodejs';

export async function POST(request: Request) {
  const body = await request.json().catch(() => null);
  const password = typeof body?.password === 'string' ? body.password : '';

  if (!(await checkAdminPassword(password))) {
    // Slow failures down so the password cannot be tried at speed.
    await new Promise((resolve) => setTimeout(resolve, 600));
    return Response.json({ error: 'Құпия сөз қате / Неверный пароль.' }, { status: 401 });
  }

  await createSession();
  return Response.json({ ok: true });
}

export async function DELETE() {
  await destroySession();
  return Response.json({ ok: true });
}
