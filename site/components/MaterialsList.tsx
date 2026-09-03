'use client';

import { useMemo, useState } from 'react';
import Link from 'next/link';
import type { SiteEvent } from '@/lib/types';
import { FileIcon, FolderIcon, SearchIcon } from './Icons';

export type PublishedMaterial = {
  id: string;
  created_at: string;
  full_name: string;
  organization: string | null;
  region: string;
  event_route: string | null;
  title: string;
  description: string | null;
  file_name: string | null;
};

export default function MaterialsList({
  materials,
  events,
  regions,
  initialRegion = '',
  initialEvent = '',
}: {
  materials: PublishedMaterial[];
  events: SiteEvent[];
  regions: string[];
  /**
   * Preselected filters. The district buttons on an event page link straight
   * here with both set, which is what replaced the old external folders.
   */
  initialRegion?: string;
  initialEvent?: string;
}) {
  const [query, setQuery] = useState('');
  const [region, setRegion] = useState(initialRegion);
  const [eventRoute, setEventRoute] = useState(initialEvent);

  const eventTitles = useMemo(
    () => new Map(events.map((event) => [event.route, event.title])),
    [events],
  );

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    return materials.filter((item) => {
      if (region && item.region !== region) return false;
      if (eventRoute && item.event_route !== eventRoute) return false;
      if (!q) return true;
      return [item.title, item.full_name, item.organization ?? '']
        .join(' ')
        .toLowerCase()
        .includes(q);
    });
  }, [materials, query, region, eventRoute]);

  return (
    <>
      <div className="portal-card materials-toolbar">
        <label>
          <SearchIcon />
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Название, автор или организация"
            aria-label="Іздеу"
          />
        </label>
        <div>
          <FolderIcon />
          <select value={region} onChange={(e) => setRegion(e.target.value)} aria-label="Қала немесе аудан">
            <option value="">Все города и районы</option>
            {regions.map((item) => (
              <option key={item} value={item}>
                {item}
              </option>
            ))}
          </select>
        </div>
        <div>
          <FileIcon />
          <select value={eventRoute} onChange={(e) => setEventRoute(e.target.value)} aria-label="Іс-шара">
            <option value="">Все мероприятия</option>
            {events.map((event) => (
              <option key={event.route} value={event.route}>
                {event.title}
              </option>
            ))}
          </select>
        </div>
      </div>

      <div className="materials-summary">
        <div>
          <strong>{filtered.length}</strong>
          <span>материалов найдено</span>
        </div>
        <Link href="/submit" className="portal-button portal-button-quiet">
          Отправить материал
        </Link>
      </div>

      {filtered.length === 0 ? (
        <div className="portal-empty">
          <FolderIcon />
          <h3>Опубликованных материалов пока нет</h3>
          <p>Можно отправить первый документ через открытую форму.</p>
          <Link href="/submit" className="portal-button portal-button-primary">
            Отправить материал
          </Link>
        </div>
      ) : (
        <div className="material-list">
          {filtered.map((item) => (
            <article className="material-card" key={item.id}>
              <div className="material-card-icon">
                <FileIcon />
              </div>
              <div>
                <div className="material-card-meta">
                  <span>{item.region}</span>
                  <span>{new Date(item.created_at).toLocaleDateString('kk-KZ')}</span>
                </div>
                <h3>{item.title}</h3>
                {item.description && <p>{item.description}</p>}
                <dl>
                  <div>
                    <dt>Автор</dt>
                    <dd>{item.full_name}</dd>
                  </div>
                  {item.organization && (
                    <div>
                      <dt>Организация</dt>
                      <dd>{item.organization}</dd>
                    </div>
                  )}
                  {item.event_route && eventTitles.has(item.event_route) && (
                    <div>
                      <dt>Іс-шара</dt>
                      <dd>{eventTitles.get(item.event_route)}</dd>
                    </div>
                  )}
                </dl>
              </div>
              <a
                className="portal-button portal-button-secondary"
                href={`/api/file/${item.id}`}
                target="_blank"
                rel="noopener noreferrer"
              >
                Ашу / Открыть
              </a>
            </article>
          ))}
        </div>
      )}
    </>
  );
}
