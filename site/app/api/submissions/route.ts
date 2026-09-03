import { getSupabase, MATERIALS_BUCKET } from '@/lib/supabase';
import { getSettings } from '@/lib/content';
import { clientIp, hashIp, isRateLimited } from '@/lib/ratelimit';
import { checkUpload, safeFileName } from '@/lib/validate-file';

export const runtime = 'nodejs';

/** A real person needs longer than this to fill the form; bots do not. */
const MIN_FILL_MS = 3000;

function fail(error: string, status = 400) {
  return Response.json({ error }, { status });
}

export async function POST(request: Request) {
  const db = getSupabase();
  if (!db) {
    return fail('Дерекқор әлі қосылмаған / База данных ещё не подключена.', 503);
  }

  const settings = await getSettings();
  if (!settings.submissions_open) {
    return fail('Материалдарды қабылдау уақытша жабық / Приём материалов временно закрыт.', 403);
  }

  let form: FormData;
  try {
    form = await request.formData();
  } catch {
    return fail('Форманы оқу мүмкін болмады / Не удалось прочитать форму.');
  }

  const text = (key: string) => (form.get(key) ?? '').toString().trim();

  // 1. Honeypot — a field hidden off-screen that only an automated filler completes.
  if (text('website')) {
    // Answer as if it worked so a bot gets no signal about why it failed.
    return Response.json({ ok: true, status: 'pending' });
  }

  // 2. Submitted implausibly fast.
  const startedAt = Number(text('started_at'));
  if (Number.isFinite(startedAt) && startedAt > 0 && Date.now() - startedAt < MIN_FILL_MS) {
    return fail('Форма тым жылдам жіберілді / Форма отправлена слишком быстро.');
  }

  const fullName = text('full_name');
  const region = text('region');
  const title = text('title');
  if (!fullName || !region || !title) {
    return fail('Аты-жөні, аймақ және атауы міндетті / Заполните имя, район и название.');
  }

  if (text('consent') !== 'on') {
    return fail(
      'Жеке деректер туралы растауды белгілеңіз / Подтвердите отсутствие запрещённых персональных данных.',
    );
  }

  // 3. Flood protection, counted against a salted hash rather than the address.
  const ipHash = hashIp(clientIp(request));
  if (await isRateLimited(ipHash)) {
    return fail(
      'Сағатына 5 материалдан артық жіберуге болмайды / Не более 5 материалов в час.',
      429,
    );
  }

  // 4. The file itself.
  const file = form.get('file');
  if (!(file instanceof File)) {
    return fail('Құжат файлын таңдаңыз / Выберите файл.');
  }

  const bytes = new Uint8Array(await file.arrayBuffer());
  const check = checkUpload(file.name, file.size, bytes);
  if (!check.ok) return fail(check.error);

  const cleanName = safeFileName(file.name);
  const path = `${new Date().toISOString().slice(0, 7)}/${crypto.randomUUID()}-${cleanName}`;

  const upload = await db.storage.from(MATERIALS_BUCKET).upload(path, bytes, {
    contentType: file.type || 'application/octet-stream',
    upsert: false,
  });
  if (upload.error) {
    return fail('Файл жүктелмеді / Не удалось загрузить файл.', 502);
  }

  const { error } = await db.from('submissions').insert({
    full_name: fullName,
    organization: text('organization') || null,
    region,
    event_route: text('event_route') || null,
    title,
    description: text('description') || null,
    file_path: path,
    file_name: cleanName,
    file_size: file.size,
    mime: file.type || null,
    status: settings.auto_publish ? 'published' : 'pending',
    ip_hash: ipHash,
    consent_at: new Date().toISOString(),
  });

  if (error) {
    // Do not leave an orphaned file behind if the row could not be written.
    await db.storage.from(MATERIALS_BUCKET).remove([path]);
    return fail('Анкета сақталмады / Не удалось сохранить анкету.', 502);
  }

  return Response.json({ ok: true, status: settings.auto_publish ? 'published' : 'pending' });
}
