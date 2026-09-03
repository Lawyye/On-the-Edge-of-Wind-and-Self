import { getSupabase, MATERIALS_BUCKET } from '@/lib/supabase';
import { isAuthenticated } from '@/lib/auth';

export const runtime = 'nodejs';

/**
 * Materials live in a private bucket, so nothing here is served directly.
 *
 * Small files are streamed back through this route rather than redirecting to a
 * signed Supabase URL: a redirect to another origin behaves inconsistently in
 * mobile browsers — which is how most people will open these — and it loses the
 * original filename. Large files still redirect, because a serverless response
 * body cannot carry them.
 */

/** Vercel caps a serverless response body; stay well under it. */
const PROXY_LIMIT_BYTES = 4 * 1024 * 1024;

/** A readable page instead of a bare "Not found" the reader cannot act on. */
function problem(status: number, message: string) {
  const body = `<!doctype html>
<html lang="ru"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Файл не открылся</title>
<style>
  body{margin:0;min-height:100vh;display:grid;place-items:center;padding:24px;
       font-family:system-ui,-apple-system,Segoe UI,Roboto,sans-serif;
       color:#172a30;background:#f3f7f8}
  main{max-width:420px;text-align:center}
  h1{margin:0 0 12px;font-size:20px;color:#0a5664}
  p{margin:0 0 22px;font-size:15px;line-height:1.55;color:#5b7076}
  a{display:inline-block;padding:12px 18px;border-radius:9px;
    color:#fff;background:#0a5664;text-decoration:none;font-size:14px}
</style></head>
<body><main>
  <h1>Файл не открылся</h1>
  <p>${message}</p>
  <a href="/materials">Вернуться к материалам</a>
</main></body></html>`;

  return new Response(body, {
    status,
    headers: { 'content-type': 'text/html; charset=utf-8' },
  });
}

export async function GET(
  _request: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const { id } = await params;

  const db = getSupabase();
  if (!db) return problem(503, 'База данных сейчас недоступна. Попробуйте позже.');

  const { data, error } = await db
    .from('submissions')
    .select('file_path, file_name, mime, file_size, status')
    .eq('id', id)
    .maybeSingle();

  if (error) return problem(502, 'Не удалось обратиться к базе данных.');
  if (!data) return problem(404, 'Такого материала нет — возможно, он был удалён.');
  if (!data.file_path) return problem(404, 'К этому материалу не приложен файл.');

  // An unpublished document stays invisible to everyone but the curator, even
  // to someone who guesses its address.
  if (data.status !== 'published' && !(await isAuthenticated())) {
    return problem(404, 'Материал ещё не опубликован.');
  }

  const size = data.file_size ?? 0;
  const fileName = data.file_name || 'document';

  if (size > 0 && size <= PROXY_LIMIT_BYTES) {
    const download = await db.storage.from(MATERIALS_BUCKET).download(data.file_path);
    if (download.data) {
      return new Response(download.data.stream(), {
        headers: {
          'content-type': data.mime || 'application/octet-stream',
          // inline so a PDF or photo opens in the browser instead of downloading
          'content-disposition': `inline; filename*=UTF-8''${encodeURIComponent(fileName)}`,
          // Deliberately no content-length: it would come from the size recorded
          // at upload, and a stale or mismatched value stalls the browser mid
          // download. Chunked transfer costs nothing here.
          'cache-control': 'private, max-age=300',
        },
      });
    }
    // Fall through to the signed URL rather than failing outright.
  }

  const signed = await db.storage
    .from(MATERIALS_BUCKET)
    .createSignedUrl(data.file_path, 60 * 60);

  if (signed.error || !signed.data) {
    return problem(
      404,
      'Файл не найден в хранилище. Скорее всего, он был удалён — попросите автора прислать материал заново.',
    );
  }

  return Response.redirect(signed.data.signedUrl, 302);
}
