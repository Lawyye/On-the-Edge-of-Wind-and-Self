import 'server-only';
import seed from '@/seed/content.json';
import { getSupabase } from './supabase';
import type { PortalSettings, SiteContent, SiteEvent } from './types';

/**
 * The whole editable site is stored as one JSON document rather than a pile of
 * normalised tables. There is exactly one editor (the curator), she saves the
 * page as a whole, and the admin UI is a single "save everything" form — so a
 * document matches how the data is actually written, and keeps the editor from
 * having to reason about rows and foreign keys.
 */

export const CONTENT_ID = 'site';

export const SEED_CONTENT = seed as unknown as SiteContent;

export const DEFAULT_SETTINGS: PortalSettings = {
  submissions_open: true,
  // Review before publish. The curator explicitly asked that a stranger not be
  // able to put anything on the site directly.
  auto_publish: false,
};

export async function getContent(): Promise<SiteContent> {
  const db = getSupabase();
  if (!db) return SEED_CONTENT;

  const { data, error } = await db
    .from('site_content')
    .select('data')
    .eq('id', CONTENT_ID)
    .maybeSingle();

  if (error || !data?.data) return SEED_CONTENT;
  return data.data as SiteContent;
}

export async function saveContent(content: SiteContent): Promise<void> {
  const db = getSupabase();
  if (!db) throw new Error('Supabase is not configured');

  const { error } = await db
    .from('site_content')
    .upsert({ id: CONTENT_ID, data: content, updated_at: new Date().toISOString() });

  if (error) throw new Error(error.message);
}

export async function getSettings(): Promise<PortalSettings> {
  const db = getSupabase();
  if (!db) return DEFAULT_SETTINGS;

  const { data, error } = await db.from('settings').select('key, value');
  if (error || !data) return DEFAULT_SETTINGS;

  const map = new Map(data.map((row) => [row.key as string, row.value]));
  return {
    submissions_open: readBool(map.get('submissions_open'), DEFAULT_SETTINGS.submissions_open),
    auto_publish: readBool(map.get('auto_publish'), DEFAULT_SETTINGS.auto_publish),
  };
}

export async function saveSettings(settings: PortalSettings): Promise<void> {
  const db = getSupabase();
  if (!db) throw new Error('Supabase is not configured');

  const rows = Object.entries(settings).map(([key, value]) => ({ key, value }));
  const { error } = await db.from('settings').upsert(rows);
  if (error) throw new Error(error.message);
}

function readBool(raw: unknown, fallback: boolean): boolean {
  if (typeof raw === 'boolean') return raw;
  if (typeof raw === 'string') return raw === 'true';
  return fallback;
}

/** Events grouped by month, preserving the order they appear in the plan. */
export function groupByMonth(events: SiteEvent[]): { month: string; label: string; events: SiteEvent[] }[] {
  const groups: { month: string; label: string; events: SiteEvent[] }[] = [];
  for (const event of events) {
    let group = groups.find((g) => g.month === event.month);
    if (!group) {
      group = { month: event.month, label: event.monthLabel, events: [] };
      groups.push(group);
    }
    group.events.push(event);
  }
  return groups;
}

/**
 * Routes in the source data look like "/ақпан/тақырып". Next.js gives us the
 * decoded segments back, so both directions go through here.
 */
export function eventSlug(event: SiteEvent): string {
  return event.route.replace(/^\//, '');
}

export function findEvent(events: SiteEvent[], slug: string): SiteEvent | undefined {
  const normalised = decodeURIComponent(slug).replace(/^\//, '');
  return events.find((event) => eventSlug(event) === normalised);
}

/** Every distinct city/district referenced by the plan, for the submission form. */
export function regionOptions(events: SiteEvent[]): string[] {
  const seen = new Set<string>();
  for (const event of events) {
    for (const link of event.links) seen.add(link.label);
  }
  return [...seen];
}
