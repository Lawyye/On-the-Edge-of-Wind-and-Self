import { getSupabase } from '@/lib/supabase';
import { requireAdmin } from '@/lib/auth';

export const runtime = 'nodejs';

/**
 * The whole register of submitted materials as a spreadsheet — the thing the
 * curator actually has to hand in at the end of the year, and the only copy
 * that survives if the Supabase project ever goes away.
 *
 * Semicolons and a UTF-8 BOM, because that is what Excel in a Russian locale
 * needs to open Cyrillic columns correctly; a comma-separated UTF-8 file opens
 * there as one column of mojibake.
 */

const STATUS_LABEL: Record<string, string> = {
  pending: 'На проверке',
  published: 'Опубликован',
  rejected: 'Отклонён',
};

function cell(value: unknown): string {
  const text = value === null || value === undefined ? '' : String(value);
  // Escape by doubling quotes, and always quote: titles contain semicolons.
  return `"${text.replace(/"/g, '""')}"`;
}

export async function GET(request: Request) {
  const denied = await requireAdmin();
  if (denied) return denied;

  const db = getSupabase();
  if (!db) return new Response('База данных не подключена', { status: 503 });

  const { data, error } = await db
    .from('submissions')
    .select('created_at, status, region, event_route, full_name, organization, title, description, file_name, file_size')
    .order('created_at', { ascending: false })
    .limit(5000);

  if (error) return new Response('Не удалось выгрузить материалы', { status: 502 });

  const origin = new URL(request.url).origin;
  const rows = data ?? [];

  const header = [
    'Дата', 'Статус', 'Город или район', 'Мероприятие', 'Автор',
    'Организация', 'Название', 'Описание', 'Файл', 'Размер, КБ',
  ];

  const lines = [header.map(cell).join(';')];

  for (const row of rows) {
    lines.push([
      new Date(row.created_at as string).toLocaleString('ru-RU'),
      STATUS_LABEL[row.status as string] ?? row.status,
      row.region,
      row.event_route ? decodeURIComponent(String(row.event_route)).replace(/^\//, '') : '',
      row.full_name,
      row.organization ?? '',
      row.title,
      row.description ?? '',
      row.file_name ?? '',
      row.file_size ? Math.round(Number(row.file_size) / 1024) : '',
    ].map(cell).join(';'));
  }

  const stamp = new Date().toISOString().slice(0, 10);
  const csv = '﻿' + lines.join('\r\n') + '\r\n';

  return new Response(csv, {
    headers: {
      'content-type': 'text/csv; charset=utf-8',
      'content-disposition': `attachment; filename="orleu-materialy-${stamp}.csv"`,
      'cache-control': 'no-store',
      // Kept so the caller can note the export was taken from this address.
      'x-export-origin': origin,
    },
  });
}
