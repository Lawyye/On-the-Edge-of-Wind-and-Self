'use client';

import { useMemo, useState } from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { assetUrl } from '@/lib/assets';
import type { SiteContent } from '@/lib/types';
import {
  ChevronDown, ChevronRight, CloseIcon, FolderIcon, LockIcon,
  MenuIcon, SearchIcon, UploadIcon,
} from './Icons';

type SearchEntry = { href: string; title: string; body: string };

export default function Shell({
  content,
  children,
}: {
  content: SiteContent;
  children: React.ReactNode;
}) {
  const { site, home, curatorsPage, events } = content;
  const pathname = usePathname();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [searchOpen, setSearchOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [openMonths, setOpenMonths] = useState<string[]>([]);

  const homeHref = '/';
  const curatorsHref = `/${encodeURI(curatorsPage.route.replace(/^\//, ''))}`;

  const months = useMemo(() => {
    const groups: { month: string; label: string; items: { href: string; title: string }[] }[] = [];
    for (const event of events) {
      let group = groups.find((g) => g.month === event.month);
      if (!group) {
        group = { month: event.month, label: event.monthLabel, items: [] };
        groups.push(group);
      }
      group.items.push({
        href: `/${encodeURI(event.route.replace(/^\//, ''))}`,
        title: event.title,
      });
    }
    return groups;
  }, [events]);

  const index = useMemo<SearchEntry[]>(() => {
    const entries: SearchEntry[] = [
      {
        href: homeHref,
        title: home.billboardTitle,
        body: [home.intro, home.mission, home.goal, ...home.tasks, ...home.results].join(' '),
      },
      {
        href: curatorsHref,
        title: curatorsPage.title,
        body: curatorsPage.curators.map((c) => `${c.name} ${c.description}`).join(' '),
      },
    ];
    for (const event of events) {
      entries.push({
        href: `/${encodeURI(event.route.replace(/^\//, ''))}`,
        title: event.title,
        body: [event.monthLabel, event.date, event.format, event.responsible, event.completion].join(' '),
      });
    }
    return entries;
  }, [home, curatorsPage, events, curatorsHref]);

  const results = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return [];
    return index.filter(
      (entry) => entry.title.toLowerCase().includes(q) || entry.body.toLowerCase().includes(q),
    );
  }, [query, index]);

  const isActive = (href: string) => decodeURI(pathname) === decodeURI(href);

  const closeDrawer = () => setDrawerOpen(false);

  return (
    <div className="site-app">
      <header className="mobile-header">
        <button type="button" onClick={() => setDrawerOpen(true)} aria-label="Мәзірді ашу">
          <MenuIcon />
        </button>
        <img src={assetUrl(site.logo)} alt="" />
        <span>{site.title}</span>
        <button type="button" onClick={() => setSearchOpen(true)} aria-label="Іздеуді ашу">
          <SearchIcon />
        </button>
      </header>

      <div
        className={`drawer-scrim${drawerOpen ? ' is-visible' : ''}`}
        onClick={closeDrawer}
        aria-hidden
      />

      <nav className={`sidebar${drawerOpen ? ' is-open' : ''}`} aria-label="Сайт навигациясы">
        <Link href={homeHref} className="brand" onClick={closeDrawer}>
          <img className="brand-logo" src={assetUrl(site.logo)} alt="" />
          <span className="brand-title">
            <span>{site.title}</span>
          </span>
        </Link>

        <div className="site-nav">
          <div className="nav-group">
            <Link
              href={homeHref}
              className={`nav-group-button${isActive(homeHref) ? ' is-active' : ''}`}
              onClick={closeDrawer}
            >
              НЕГІЗГІ БЕТ
            </Link>
            <div className="nav-children">
              <Link
                href={curatorsHref}
                className={isActive(curatorsHref) ? 'is-active' : undefined}
                onClick={closeDrawer}
              >
                {curatorsPage.title}
              </Link>
            </div>
          </div>

          {months.map((group) => {
            const expanded = openMonths.includes(group.month)
              || group.items.some((item) => isActive(item.href));
            return (
              <div className="nav-group" key={group.month}>
                <button
                  type="button"
                  className={`nav-group-button${expanded ? ' is-active' : ''}`}
                  aria-expanded={expanded}
                  onClick={() =>
                    setOpenMonths((prev) =>
                      prev.includes(group.month)
                        ? prev.filter((m) => m !== group.month)
                        : [...prev, group.month],
                    )
                  }
                >
                  {expanded ? <ChevronDown /> : <ChevronRight />}
                  {group.label}
                </button>
                {expanded && (
                  <div className="nav-children nav-event-list">
                    {group.items.map((item) => (
                      <Link
                        key={item.href}
                        href={item.href}
                        className={isActive(item.href) ? 'is-active' : undefined}
                        onClick={closeDrawer}
                      >
                        {item.title}
                      </Link>
                    ))}
                  </div>
                )}
              </div>
            );
          })}

          <div className="portal-nav-links">
            <Link
              href="/materials"
              className={isActive('/materials') ? 'is-active' : undefined}
              onClick={closeDrawer}
            >
              <FolderIcon /> Материалдар
            </Link>
            <Link
              href="/submit"
              className={isActive('/submit') ? 'is-active' : undefined}
              onClick={closeDrawer}
            >
              <UploadIcon /> Материал жіберу
            </Link>
            <Link
              href="/admin"
              className={isActive('/admin') ? 'is-active' : undefined}
              onClick={closeDrawer}
            >
              <LockIcon /> Әкімші
            </Link>
          </div>
        </div>
      </nav>

      <button
        type="button"
        className="floating-search"
        onClick={() => setSearchOpen(true)}
        aria-label="Іздеу жолағын ашу"
      >
        <SearchIcon />
      </button>

      {searchOpen && (
        <div className={`search-layer${query.trim() ? ' show-results' : ''}`}>
          <div className="search-bar">
            <button
              type="button"
              onClick={() => {
                setSearchOpen(false);
                setQuery('');
              }}
              aria-label="Іздеуді жабу"
            >
              <CloseIcon />
            </button>
            <SearchIcon />
            <input
              autoFocus
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Осы сайттан іздеу"
              aria-label="Осы сайттан іздеу"
            />
            <button type="button" onClick={() => setQuery('')} aria-label="Іздеуді тазалау">
              <CloseIcon />
            </button>
          </div>

          {query.trim() && (
            <div className="search-results-panel">
              <div className="search-tabs">
                <span>Бұл сайт</span>
              </div>
              <p className="search-hint">Сайт беттері бойынша іздеу нәтижелері</p>
              {results.length === 0 ? (
                <p className="no-results">Сұрауыңыз бойынша ештеңе табылмады.</p>
              ) : (
                results.map((entry) => (
                  <Link
                    key={entry.href}
                    href={entry.href}
                    className="search-result"
                    onClick={() => {
                      setSearchOpen(false);
                      setQuery('');
                    }}
                  >
                    <strong>{entry.title}</strong>
                    <span>{entry.body.slice(0, 180)}…</span>
                  </Link>
                ))
              )}
            </div>
          )}
        </div>
      )}

      <div className="page-shell">{children}</div>
    </div>
  );
}
