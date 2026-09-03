import Link from 'next/link';
import { notFound, redirect } from 'next/navigation';
import Shell from '@/components/Shell';
import { getContent, findEvent } from '@/lib/content';
import { assetUrl } from '@/lib/assets';
import { AwardIcon, CalendarIcon, LayersIcon, UserIcon } from '@/components/Icons';
import type { CuratorsPage, SiteContent, SiteEvent } from '@/lib/types';

export const dynamic = 'force-dynamic';

/**
 * One catch-all keeps the original Cyrillic addresses intact
 * ("/ақпан/функционалдық-сауаттылық…") without creating a directory per page,
 * so pages the curator adds later resolve with no new files.
 */
export default async function CatchAllPage({
  params,
}: {
  params: Promise<{ slug: string[] }>;
}) {
  const { slug } = await params;
  const route = slug.map((part) => decodeURIComponent(part)).join('/');
  const content = await getContent();

  if (route === content.site.homeRoute.replace(/^\//, '')) redirect('/');

  if (route === content.curatorsPage.route.replace(/^\//, '')) {
    return (
      <Shell content={content}>
        <CuratorsView page={content.curatorsPage} />
      </Shell>
    );
  }

  const event = findEvent(content.events, route);
  if (!event) notFound();

  return (
    <Shell content={content}>
      <EventView event={event} site={content.site} />
    </Shell>
  );
}

function CuratorsView({ page }: { page: CuratorsPage }) {
  return (
    <div className="curators-page">
      <div className="curators-heading teal-section">
        <h1>{page.title}</h1>
      </div>

      {page.curators.map((curator, i) => (
        <div className={`curator-row curator-row-${i + 1}`} key={curator.name}>
          <div className="curator-inner content-width">
            <img src={assetUrl(curator.image)} alt="" />
            <div className="curator-copy">
              <p>
                <strong>{curator.name}</strong>
                <br />
                {curator.description}
              </p>
              {curator.barImage && (
                <img className="curator-accent" src={assetUrl(curator.barImage)} alt="" />
              )}
            </div>
          </div>
        </div>
      ))}

      <div
        className="curators-footer"
        style={{ backgroundImage: `url(${assetUrl(page.footerImage)})` }}
      >
        <h2>{page.footerTitle}</h2>
      </div>

      {page.documentUrl && (
        <div className="project-document">
          <iframe src={assetUrl(page.documentUrl)} title={page.documentTitle} />
        </div>
      )}
    </div>
  );
}

function EventView({ event, site }: { event: SiteEvent; site: SiteContent['site'] }) {
  const rows = [
    { icon: <CalendarIcon />, value: event.date, emphasis: true },
    { icon: <LayersIcon />, value: event.format, emphasis: false },
    { icon: <UserIcon />, value: event.responsible, emphasis: false },
    { icon: <AwardIcon />, value: event.completion, emphasis: false },
  ].filter((row) => Boolean(row.value));

  return (
    <div className="event-page">
      <div
        className="hero hero-compact"
        style={{ backgroundImage: `url(${assetUrl(site.eventHeroImage)})` }}
      >
        <div className="hero-shade" />
      </div>

      <div className="event-title teal-section">
        <h1>{event.title}</h1>
      </div>

      <div className="event-details content-width">
        {event.image && <img className="event-image" src={assetUrl(event.image)} alt="" />}
        <div className="event-meta">
          {rows.map((row, i) => (
            <div className={`detail-row${row.emphasis ? ' detail-row-emphasis' : ''}`} key={i}>
              <span className="detail-icon">{row.icon}</span>
              <span>{row.value}</span>
            </div>
          ))}
        </div>
      </div>

      {event.links.length > 0 && (
        <>
          <div className="materials-heading">
            <h2 className="content-width">{event.materialsHeading}</h2>
          </div>
          <div className="materials-links content-width">
            {event.links.map((link) =>
              // A district button now opens this site's own archive, filtered to
              // that district and this event. External addresses still work, so
              // the curator can point a button anywhere from the editor.
              link.url.startsWith('/') ? (
                <Link key={link.label} href={link.url}>
                  {link.label}
                </Link>
              ) : (
                <a key={link.label} href={link.url} target="_blank" rel="noopener noreferrer">
                  {link.label}
                </a>
              ),
            )}
          </div>

          <div className="event-submit content-width">
            <p>Осы іс-шара бойынша материалыңызды жіберіңіз. Тіркелу қажет емес.</p>
            <Link href="/submit" className="portal-button portal-button-primary">
              Материал жіберу
            </Link>
          </div>
        </>
      )}

      <div className="event-footer teal-section" />
    </div>
  );
}
