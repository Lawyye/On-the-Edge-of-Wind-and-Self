import { getSupabase, IMAGES_BUCKET } from '@/lib/supabase';
import { requireAdmin } from '@/lib/auth';
import { getContent, saveContent } from '@/lib/content';
import { LEGACY_ORIGIN, LEGACY_PREFIX } from '@/lib/assets';
import type { SiteContent } from '@/lib/types';

export const runtime = 'nodejs';
export const maxDuration = 60;

/**
 * One-time move of the original site's photographs into this project's own
 * storage.
 *
 * They have been served from the previous deployment all along, which is a
 * project nobody here maintains: while that was true, a single successful build
 * there would have replaced the deployment holding them and taken every picture
 * off this site at once. This copies them across and rewrites the content to
 * point at the new URLs, after which the old project can be deleted.
 *
 * It runs here rather than from a developer's machine because this function can
 * reach both the old deployment and Supabase, and it is safe to run twice: an
 * asset already moved is simply not found in the content the second time.
 */

const MAX_BYTES = 25 * 1024 * 1024;

/** Every place in the content document that can hold an asset path. */
function collectPaths(content: SiteContent): string[] {
  const found = new Set<string>();
  const add = (value: unknown) => {
    if (typeof value === 'string' && value.startsWith(LEGACY_PREFIX)) found.add(value);
  };

  add(content.site.logo);
  add(content.site.heroImage);
  add(content.site.eventHeroImage);
  add(content.home.goalImage);
  add(content.home.tasksImage);
  add(content.home.resultsImage);
  add(content.curatorsPage.footerImage);
  add(content.curatorsPage.documentUrl);
  for (const curator of content.curatorsPage.curators) {
    add(curator.image);
    add(curator.barImage);
  }
  for (const event of content.events) add(event.image);

  return [...found];
}

/** Rewrites every occurrence of a moved path, wherever it appears. */
function rewrite(content: SiteContent, moved: Map<string, string>): SiteContent {
  const swap = (value: unknown) =>
    typeof value === 'string' && moved.has(value) ? moved.get(value)! : value;

  const walk = (node: unknown): unknown => {
    if (typeof node === 'string') return swap(node);
    if (Array.isArray(node)) return node.map(walk);
    if (node && typeof node === 'object') {
      return Object.fromEntries(
        Object.entries(node as Record<string, unknown>).map(([k, v]) => [k, walk(v)]),
      );
    }
    return node;
  };

  return walk(content) as SiteContent;
}

const CONTENT_TYPES: Record<string, string> = {
  jpg: 'image/jpeg',
  jpeg: 'image/jpeg',
  png: 'image/png',
  gif: 'image/gif',
  webp: 'image/webp',
  svg: 'image/svg+xml',
  pdf: 'application/pdf',
};

export async function POST() {
  const denied = await requireAdmin();
  if (denied) return denied;

  const db = getSupabase();
  if (!db) {
    return Response.json({ error: 'База данных не подключена.' }, { status: 503 });
  }

  const content = await getContent();
  const paths = collectPaths(content);

  if (paths.length === 0) {
    return Response.json({
      ok: true,
      moved: 0,
      failed: [],
      message: 'Все изображения уже находятся в вашем хранилище — переносить нечего.',
    });
  }

  const moved = new Map<string, string>();
  const failed: { path: string; reason: string }[] = [];

  for (const path of paths) {
    try {
      const response = await fetch(`${LEGACY_ORIGIN}${path}`);
      if (!response.ok) {
        failed.push({ path, reason: `источник ответил ${response.status}` });
        continue;
      }

      const bytes = new Uint8Array(await response.arrayBuffer());
      if (bytes.byteLength === 0) {
        failed.push({ path, reason: 'пустой файл' });
        continue;
      }
      if (bytes.byteLength > MAX_BYTES) {
        failed.push({ path, reason: 'файл слишком большой' });
        continue;
      }

      const name = path.slice(LEGACY_PREFIX.length);
      const extension = (name.split('.').pop() ?? '').toLowerCase();

      const upload = await db.storage.from(IMAGES_BUCKET).upload(`original/${name}`, bytes, {
        contentType: CONTENT_TYPES[extension] ?? response.headers.get('content-type') ?? 'application/octet-stream',
        // Overwrite so a repeated run repairs a half-finished one.
        upsert: true,
      });

      if (upload.error) {
        failed.push({ path, reason: upload.error.message });
        continue;
      }

      const { data } = db.storage.from(IMAGES_BUCKET).getPublicUrl(`original/${name}`);
      moved.set(path, data.publicUrl);
    } catch (error) {
      failed.push({ path, reason: (error as Error).message });
    }
  }

  // Only rewrite what actually arrived; anything that failed keeps pointing at
  // the old deployment rather than at a URL with nothing behind it.
  if (moved.size > 0) {
    try {
      await saveContent(rewrite(content, moved));
    } catch (error) {
      return Response.json(
        { error: `Файлы перенесены, но content сохранить не удалось: ${(error as Error).message}` },
        { status: 502 },
      );
    }
  }

  return Response.json({
    ok: failed.length === 0,
    moved: moved.size,
    total: paths.length,
    failed,
    message:
      failed.length === 0
        ? `Перенесено файлов: ${moved.size}. Сайт больше не зависит от старого проекта.`
        : `Перенесено ${moved.size} из ${paths.length}. Не удалось: ${failed.length} — старый проект пока удалять нельзя.`,
  });
}
