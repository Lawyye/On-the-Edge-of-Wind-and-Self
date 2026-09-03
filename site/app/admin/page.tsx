import AdminLogin from '@/components/admin/AdminLogin';
import AdminPanel from '@/components/admin/AdminPanel';
import { isAuthenticated } from '@/lib/auth';
import { getContent, getSettings } from '@/lib/content';
import { isConfigured } from '@/lib/supabase';

export const dynamic = 'force-dynamic';

export default async function AdminPage() {
  if (!(await isAuthenticated())) return <AdminLogin />;

  const [content, settings] = await Promise.all([getContent(), getSettings()]);

  return (
    <AdminPanel
      initialContent={content}
      initialSettings={settings}
      configured={isConfigured()}
    />
  );
}
