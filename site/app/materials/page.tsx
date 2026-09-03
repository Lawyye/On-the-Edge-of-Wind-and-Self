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

export default async function MaterialsPage() {
  const [content, materials] = await Promise.all([getContent(), publishedMaterials()]);

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
          />
        </div>
      </div>
    </Shell>
  );
}
