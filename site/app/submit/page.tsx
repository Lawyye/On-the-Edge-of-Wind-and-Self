import Shell from '@/components/Shell';
import SubmitForm from '@/components/SubmitForm';
import { getContent, getSettings, regionOptions } from '@/lib/content';

export const dynamic = 'force-dynamic';

export default async function SubmitPage() {
  const [content, settings] = await Promise.all([getContent(), getSettings()]);

  return (
    <Shell content={content}>
      <div className="portal-page">
        <div className="portal-hero">
          <span>ҚАТЫСУШЫЛАРҒА / ДЛЯ УЧАСТНИКОВ</span>
          <h1>Материал жіберу</h1>
          <p>Форманы телефоннан толтырып, құжатты тіркеңіз. Тіркелу қажет емес.</p>
        </div>
        <div className="portal-content">
          <SubmitForm
            regions={regionOptions(content.events)}
            events={content.events}
            open={settings.submissions_open}
            autoPublish={settings.auto_publish}
          />
        </div>
      </div>
    </Shell>
  );
}
