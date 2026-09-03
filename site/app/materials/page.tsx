import Shell from '@/components/Shell';
import MaterialsList, { type PublishedMaterial } from '@/components/MaterialsList';
import { getContent, regionOptions } from '@/lib/content';
import { getSupabase } from '@/lib/supabase';

export const dynamic = 'force-dynamic';

async function publishedMaterials(): Promise<PublishedMaterial[]> {
  const db = getSupabase();
  if (!db) return [];

  const { data, error } = await db
    .from('submissions')
    .select('id, created_at, full_name, organization, region, event_route, title, description, file_name')
    .eq('status', 'published')
    .order('created_at', { ascending: false })
    .limit(500);

  if (error || !data) return [];
  return data as PublishedMaterial[];
}

function first(value: string | string[] | undefined): string {
  if (Array.isArray(value)) return value[0] ?? '';
  return value ?? '';
}

export default async function MaterialsPage({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const [content, materials, params] = await Promise.all([
    getContent(),
    publishedMaterials(),
    searchParams,
  ]);

  // Filters are read on the server and handed down as initial state, so the
  // district buttons on an event page open an already-filtered archive without
  // the page needing a Suspense boundary for useSearchParams.
  const region = first(params.region);
  const eventRoute = first(params.event);

  return (
    <Shell content={content}>
      <div className="portal-page">
        <div className="portal-hero">
          <span>АШЫҚ МАТЕРИАЛДАР / ОТКРЫТЫЙ АРХИВ</span>
          <h1>Қатысушылар материалдары</h1>
          <p>Город, район немесе іс-шара бойынша жарияланған құжаттарды табыңыз.</p>
        </div>
        <div className="portal-content">
          <MaterialsList
            materials={materials}
            events={content.events}
            regions={regionOptions(content.events)}
            initialRegion={region}
            initialEvent={eventRoute}
          />
        </div>
      </div>
    </Shell>
  );
}
