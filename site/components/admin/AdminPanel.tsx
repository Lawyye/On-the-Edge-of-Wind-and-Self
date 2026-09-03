'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useRouter } from 'next/navigation';
import ImageControl from './ImageControl';
import {
  CheckIcon, EditIcon, GridIcon, InboxIcon, InfoIcon, LogoutIcon,
  SettingsIcon, ToggleOff, ToggleOn, UploadIcon, UserIcon,
} from '../Icons';
import type { PortalSettings, SiteContent, Submission, SubmissionStatus } from '@/lib/types';

type Tab = 'overview' | 'submissions' | 'editor' | 'settings';
type EditorSection = 'home' | 'curators' | 'events' | 'covers';

const STATUS_LABEL: Record<SubmissionStatus, string> = {
  pending: 'На проверке',
  published: 'Опубликован',
  rejected: 'Отклонён',
};

export default function AdminPanel({
  initialContent,
  initialSettings,
  configured,
}: {
  initialContent: SiteContent;
  initialSettings: PortalSettings;
  configured: boolean;
}) {
  const router = useRouter();
  const [tab, setTab] = useState<Tab>('overview');
  const [content, setContent] = useState<SiteContent>(initialContent);
  const [settings, setSettings] = useState<PortalSettings>(initialSettings);
  const [submissions, setSubmissions] = useState<Submission[]>([]);
  const [notice, setNotice] = useState('');
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);

  const loadSubmissions = useCallback(async () => {
    if (!configured) return;
    try {
      const response = await fetch('/api/admin/submissions');
      if (!response.ok) return;
      const result = await response.json();
      setSubmissions(result.submissions ?? []);
    } catch {
      /* the panel stays usable even when the list cannot be refreshed */
    }
  }, [configured]);

  useEffect(() => {
    void loadSubmissions();
  }, [loadSubmissions]);

  const pending = submissions.filter((item) => item.status === 'pending').length;
  const published = submissions.filter((item) => item.status === 'published').length;
  // Every row already carries its file size, so this costs no extra request.
  const usedBytes = submissions.reduce((sum, item) => sum + (item.file_size ?? 0), 0);

  function flash(message: string) {
    setNotice(message);
    setError('');
    setTimeout(() => setNotice(''), 4000);
  }

  async function saveContent() {
    setSaving(true);
    setError('');
    try {
      const response = await fetch('/api/admin/content', {
        method: 'PUT',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify(content),
      });
      const result = await response.json().catch(() => ({}));
      if (!response.ok) {
        setError(result.error ?? 'Не удалось сохранить изменения сайта.');
        return;
      }
      flash('Изменения сохранены и уже используются сайтом.');
      router.refresh();
    } finally {
      setSaving(false);
    }
  }

  async function saveSettings(next: PortalSettings) {
    setSettings(next);
    const response = await fetch('/api/admin/settings', {
      method: 'PUT',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify(next),
    });
    if (response.ok) flash('Настройки сохранены.');
    else setError('Не удалось сохранить настройки.');
  }

  async function moderate(id: string, status: SubmissionStatus) {
    const response = await fetch('/api/admin/submissions', {
      method: 'PATCH',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ id, status }),
    });
    if (!response.ok) {
      setError('Не удалось изменить статус материала.');
      return;
    }
    setSubmissions((prev) => prev.map((item) => (item.id === id ? { ...item, status } : item)));
    router.refresh();
  }

  async function remove(id: string) {
    if (!window.confirm('Удалить материал вместе с файлом? Отменить будет нельзя.')) return;
    const response = await fetch(`/api/admin/submissions?id=${encodeURIComponent(id)}`, {
      method: 'DELETE',
    });
    if (!response.ok) {
      setError('Не удалось удалить материал.');
      return;
    }
    setSubmissions((prev) => prev.filter((item) => item.id !== id));
    router.refresh();
  }

  async function logout() {
    await fetch('/api/admin/login', { method: 'DELETE' });
    router.refresh();
  }

  return (
    <div className="admin-page">
      <header className="admin-header">
        <div>
          <span>ОРЛЕУ МАҢҒЫСТАУ</span>
          <h1>Управление сайтом</h1>
        </div>
        <div className="admin-account">
          <UserIcon />
          <span>Куратор</span>
          <button type="button" onClick={logout} title="Выйти" aria-label="Выйти">
            <LogoutIcon />
          </button>
        </div>
      </header>

      {!configured && (
        <div className="admin-demo-banner">
          База данных ещё не подключена — изменения сохранить нельзя. Добавьте SUPABASE_URL и
          SUPABASE_SERVICE_ROLE_KEY в настройках Vercel.
        </div>
      )}

      <nav className="admin-tabs">
        <button type="button" className={tab === 'overview' ? 'is-active' : ''} onClick={() => setTab('overview')}>
          <GridIcon /> Сводка
        </button>
        <button type="button" className={tab === 'submissions' ? 'is-active' : ''} onClick={() => setTab('submissions')}>
          <InboxIcon /> Материалы
          {pending > 0 && <strong>{pending}</strong>}
        </button>
        <button type="button" className={tab === 'editor' ? 'is-active' : ''} onClick={() => setTab('editor')}>
          <EditIcon /> Редактор
        </button>
        <button type="button" className={tab === 'settings' ? 'is-active' : ''} onClick={() => setTab('settings')}>
          <SettingsIcon /> Настройки
        </button>
      </nav>

      <div className="admin-content">
        {notice && (
          <div className="portal-notice portal-notice-success">
            <CheckIcon />
            <span>{notice}</span>
          </div>
        )}
        {error && (
          <div className="portal-notice portal-notice-error">
            <InfoIcon />
            <span>{error}</span>
          </div>
        )}

        {tab === 'overview' && (
          <Overview
            total={submissions.length}
            pending={pending}
            published={published}
            events={content.events.length}
            usedBytes={usedBytes}
            onGo={setTab}
          />
        )}

        {tab === 'submissions' && (
          <SubmissionsTab
            submissions={submissions}
            onModerate={moderate}
            onRemove={remove}
            onRefresh={loadSubmissions}
          />
        )}

        {tab === 'editor' && (
          <EditorTab
            content={content}
            setContent={setContent}
            onSave={saveContent}
            saving={saving}
          />
        )}

        {tab === 'settings' && (
          <SettingsTab settings={settings} onChange={saveSettings} onError={setError} onFlash={flash} />
        )}
      </div>
    </div>
  );
}

/* ------------------------------------------------------------------ overview */

/**
 * The free Supabase plan allows 1 GB of uploaded files. Nothing warns you as it
 * fills — uploads simply start failing — so the panel shows how full it is.
 */
const STORAGE_LIMIT_BYTES = 1024 * 1024 * 1024;

function formatSize(bytes: number): string {
  if (bytes >= 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} ГБ`;
  if (bytes >= 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} МБ`;
  return `${Math.round(bytes / 1024)} КБ`;
}

function Overview({
  total, pending, published, events, usedBytes, onGo,
}: {
  total: number; pending: number; published: number; events: number;
  usedBytes: number; onGo: (tab: Tab) => void;
}) {
  const percent = Math.min(100, Math.round((usedBytes / STORAGE_LIMIT_BYTES) * 100));
  const tight = percent >= 80;

  return (
    <>
      <div className="admin-section-heading">
        <div>
          <span>Сводка</span>
          <h2>Что происходит на сайте</h2>
        </div>
      </div>

      <div className="dashboard-stats">
        <button type="button" onClick={() => onGo('submissions')}>
          <InboxIcon />
          <strong>{total}</strong>
          <span>Всего материалов</span>
        </button>
        <button type="button" onClick={() => onGo('submissions')}>
          <InfoIcon />
          <strong>{pending}</strong>
          <span>Ожидают проверки</span>
        </button>
        <button type="button" onClick={() => onGo('editor')}>
          <EditIcon />
          <strong>{events}</strong>
          <span>Мероприятий на сайте</span>
        </button>
      </div>

      <div className="admin-overview-grid">
        <div className="admin-panel-card">
          <h3>Быстрые действия</h3>
          <button type="button" onClick={() => onGo('submissions')}>
            <InboxIcon /> <span>Проверить новые материалы</span> <span>{pending}</span>
          </button>
          <button type="button" onClick={() => onGo('editor')}>
            <EditIcon /> <span>Изменить даты и тексты</span> <span>›</span>
          </button>
          <button type="button" onClick={() => onGo('settings')}>
            <SettingsIcon /> <span>Настроить приём файлов</span> <span>›</span>
          </button>
        </div>
        <div className="admin-panel-card admin-mode-card">
          <CheckIcon />
          <strong>{published} опубликовано</strong>
          <p>
            Опубликованные документы видны всем на странице «Материалы». Остальные видите только вы.
          </p>

          <div className="storage-meter">
            <div className="storage-meter-top">
              <span>Занято места</span>
              <strong className={tight ? 'is-tight' : undefined}>
                {formatSize(usedBytes)} из 1 ГБ
              </strong>
            </div>
            <div className="storage-bar">
              <span
                className={tight ? 'is-tight' : undefined}
                style={{ width: `${Math.max(percent, 1)}%` }}
              />
            </div>
            <p>
              {tight
                ? 'Место почти закончилось. Новые файлы скоро перестанут загружаться — удалите ненужные или перейдите на платный тариф Supabase.'
                : 'Бесплатный тариф Supabase даёт 1 ГБ под файлы участников.'}
            </p>
          </div>
        </div>
      </div>
    </>
  );
}

/* --------------------------------------------------------------- submissions */

function SubmissionsTab({
  submissions, onModerate, onRemove, onRefresh,
}: {
  submissions: Submission[];
  onModerate: (id: string, status: SubmissionStatus) => void;
  onRemove: (id: string) => void;
  onRefresh: () => void;
}) {
  const [filter, setFilter] = useState<'all' | SubmissionStatus>('pending');

  const shown = useMemo(
    () => (filter === 'all' ? submissions : submissions.filter((item) => item.status === filter)),
    [submissions, filter],
  );

  const filters: { key: 'all' | SubmissionStatus; label: string }[] = [
    { key: 'pending', label: 'На проверке' },
    { key: 'published', label: 'Опубликованы' },
    { key: 'rejected', label: 'Отклонены' },
    { key: 'all', label: 'Все' },
  ];

  return (
    <>
      <div className="admin-section-heading">
        <div>
          <span>Документы</span>
          <h2>Материалы участников</h2>
        </div>
        <div className="admin-heading-actions">
          <a className="portal-button portal-button-secondary" href="/api/admin/export">
            Выгрузить в таблицу
          </a>
          <button type="button" className="portal-button portal-button-secondary" onClick={onRefresh}>
            Обновить
          </button>
        </div>
      </div>

      <div className="admin-filter-row">
        {filters.map((item) => (
          <button
            key={item.key}
            type="button"
            className={filter === item.key ? 'is-active' : ''}
            onClick={() => setFilter(item.key)}
          >
            {item.label}
          </button>
        ))}
      </div>

      {shown.length === 0 ? (
        <div className="portal-empty compact">
          <InboxIcon />
          <h3>В этом разделе материалов нет</h3>
        </div>
      ) : (
        <div className="admin-submission-list">
          {shown.map((item) => (
            <article className="admin-submission-card" key={item.id}>
              <div className="admin-submission-top">
                <span className={`status-badge status-${item.status}`}>{STATUS_LABEL[item.status]}</span>
                <time>{new Date(item.created_at).toLocaleString('ru-RU')}</time>
              </div>

              <h3>{item.title}</h3>
              <p>{item.region}</p>

              <dl>
                <div>
                  <dt>Автор</dt>
                  <dd>{item.full_name}</dd>
                </div>
                <div>
                  <dt>Организация</dt>
                  <dd>{item.organization || '—'}</dd>
                </div>
                <div>
                  <dt>Файл</dt>
                  <dd>{item.file_name || '—'}</dd>
                </div>
                <div>
                  <dt>Размер</dt>
                  <dd>{item.file_size ? `${Math.round(item.file_size / 1024)} КБ` : '—'}</dd>
                </div>
              </dl>

              {item.description && <p className="admin-submission-description">{item.description}</p>}

              <div className="admin-submission-actions">
                <a
                  className="portal-button portal-button-secondary"
                  href={`/api/file/${item.id}`}
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  Открыть
                </a>
                {item.status !== 'published' && (
                  <button
                    type="button"
                    className="portal-button portal-button-primary"
                    onClick={() => onModerate(item.id, 'published')}
                  >
                    Опубликовать
                  </button>
                )}
                {item.status === 'published' && (
                  <button
                    type="button"
                    className="portal-button portal-button-warning"
                    onClick={() => onModerate(item.id, 'pending')}
                  >
                    Скрыть
                  </button>
                )}
                {item.status !== 'rejected' && (
                  <button
                    type="button"
                    className="portal-button portal-button-warning"
                    onClick={() => onModerate(item.id, 'rejected')}
                  >
                    Отклонить
                  </button>
                )}
                <button
                  type="button"
                  className="portal-button portal-button-danger"
                  onClick={() => onRemove(item.id)}
                >
                  Удалить
                </button>
              </div>
            </article>
          ))}
        </div>
      )}
    </>
  );
}

/* -------------------------------------------------------------------- editor */

function Text({
  label, value, onChange, hint, rows, type,
}: {
  label: string;
  value: string;
  onChange: (v: string) => void;
  hint?: string;
  rows?: number;
  type?: 'text' | 'password';
}) {
  return (
    <label className="portal-field">
      <span>{label}</span>
      {rows ? (
        <textarea rows={rows} value={value} onChange={(e) => onChange(e.target.value)} />
      ) : (
        <input type={type ?? 'text'} value={value} onChange={(e) => onChange(e.target.value)} />
      )}
      {hint && <small>{hint}</small>}
    </label>
  );
}

function EditorTab({
  content, setContent, onSave, saving,
}: {
  content: SiteContent;
  setContent: (updater: (prev: SiteContent) => SiteContent) => void;
  onSave: () => void;
  saving: boolean;
}) {
  const [section, setSection] = useState<EditorSection>('home');
  const [eventIndex, setEventIndex] = useState(0);

  const sections: { key: EditorSection; label: string }[] = [
    { key: 'home', label: 'Главная' },
    { key: 'curators', label: 'Кураторы' },
    { key: 'events', label: 'Мероприятия' },
    { key: 'covers', label: 'Обложки' },
  ];

  const home = content.home;
  const event = content.events[eventIndex];

  const setHome = (patch: Partial<SiteContent['home']>) =>
    setContent((prev) => ({ ...prev, home: { ...prev.home, ...patch } }));

  const setEvent = (patch: Partial<SiteContent['events'][number]>) =>
    setContent((prev) => ({
      ...prev,
      events: prev.events.map((item, i) => (i === eventIndex ? { ...item, ...patch } : item)),
    }));

  const lines = (value: string) => value.split('\n').map((l) => l.trim()).filter(Boolean);

  return (
    <>
      <div className="admin-section-heading">
        <div>
          <span>Редактор</span>
          <h2>Информация сайта</h2>
        </div>
        <button
          type="button"
          className="portal-button portal-button-primary"
          onClick={onSave}
          disabled={saving}
        >
          {saving ? 'Сохраняю…' : 'Сохранить всё'}
        </button>
      </div>

      <div className="editor-sections">
        {sections.map((item) => (
          <button
            key={item.key}
            type="button"
            className={section === item.key ? 'is-active' : ''}
            onClick={() => setSection(item.key)}
          >
            {item.label}
          </button>
        ))}
      </div>

      <div className="editor-stack">
        {section === 'home' && (
          <div className="portal-card editor-card">
            <h3>Главная страница</h3>
            <Text label="Подпись в верхнем баннере" value={home.billboardTitle} onChange={(v) => setHome({ billboardTitle: v })} />
            <Text label="Подзаголовок баннера" value={home.billboardSubtitle} onChange={(v) => setHome({ billboardSubtitle: v })} />
            <Text label="Главный заголовок" value={home.intro} onChange={(v) => setHome({ intro: v })} rows={3} />
            <Text label="Миссия" value={home.mission} onChange={(v) => setHome({ mission: v })} rows={4} />
            <Text label="Заголовок цели" value={home.goalTitle} onChange={(v) => setHome({ goalTitle: v })} />
            <Text label="Цель проекта" value={home.goal} onChange={(v) => setHome({ goal: v })} rows={4} />
            <Text label="Заголовок задач" value={home.tasksTitle} onChange={(v) => setHome({ tasksTitle: v })} />
            <Text
              label="Задачи проекта — по одной на строке"
              value={home.tasks.join('\n')}
              onChange={(v) => setHome({ tasks: lines(v) })}
              rows={7}
            />
            <Text label="Заголовок результатов" value={home.resultsTitle} onChange={(v) => setHome({ resultsTitle: v })} />
            <Text
              label="Ожидаемые результаты — по одному на строке"
              value={home.results.join('\n')}
              onChange={(v) => setHome({ results: lines(v) })}
              rows={7}
            />
            <Text
              label="Строки внизу страницы — по одной на строке"
              value={home.footerLines.join('\n')}
              onChange={(v) => setHome({ footerLines: lines(v) })}
              rows={4}
            />
          </div>
        )}

        {section === 'curators' && (
          <div className="portal-card editor-card">
            <h3>Страница кураторов</h3>
            <Text
              label="Заголовок страницы"
              value={content.curatorsPage.title}
              onChange={(v) =>
                setContent((prev) => ({ ...prev, curatorsPage: { ...prev.curatorsPage, title: v } }))
              }
            />

            {content.curatorsPage.curators.map((curator, i) => (
              <div key={i}>
                <h4>Куратор {i + 1}</h4>
                <ImageControl
                  label="Фотография"
                  value={curator.image}
                  onChange={(url) =>
                    setContent((prev) => ({
                      ...prev,
                      curatorsPage: {
                        ...prev.curatorsPage,
                        curators: prev.curatorsPage.curators.map((c, j) =>
                          j === i ? { ...c, image: url } : c,
                        ),
                      },
                    }))
                  }
                />
                <Text
                  label="ФИО"
                  value={curator.name}
                  onChange={(v) =>
                    setContent((prev) => ({
                      ...prev,
                      curatorsPage: {
                        ...prev.curatorsPage,
                        curators: prev.curatorsPage.curators.map((c, j) =>
                          j === i ? { ...c, name: v } : c,
                        ),
                      },
                    }))
                  }
                />
                <Text
                  label="Должность и описание"
                  rows={3}
                  value={curator.description}
                  onChange={(v) =>
                    setContent((prev) => ({
                      ...prev,
                      curatorsPage: {
                        ...prev.curatorsPage,
                        curators: prev.curatorsPage.curators.map((c, j) =>
                          j === i ? { ...c, description: v } : c,
                        ),
                      },
                    }))
                  }
                />
              </div>
            ))}

            <h4>План проекта</h4>
            <Text
              label="Заголовок над планом проекта"
              value={content.curatorsPage.footerTitle}
              onChange={(v) =>
                setContent((prev) => ({ ...prev, curatorsPage: { ...prev.curatorsPage, footerTitle: v } }))
              }
              rows={2}
            />
            <Text
              label="Название встроенного документа"
              value={content.curatorsPage.documentTitle}
              onChange={(v) =>
                setContent((prev) => ({ ...prev, curatorsPage: { ...prev.curatorsPage, documentTitle: v } }))
              }
            />
            <Text
              label="Ссылка на документ"
              hint="Можно вставить ссылку на Google-документ в режиме просмотра."
              value={content.curatorsPage.documentUrl}
              onChange={(v) =>
                setContent((prev) => ({ ...prev, curatorsPage: { ...prev.curatorsPage, documentUrl: v } }))
              }
            />
          </div>
        )}

        {section === 'events' && event && (
          <div className="portal-card editor-card">
            <h3>Мероприятия</h3>
            <label className="portal-field">
              <span>Выберите мероприятие</span>
              <select value={eventIndex} onChange={(e) => setEventIndex(Number(e.target.value))}>
                {content.events.map((item, i) => (
                  <option key={item.route} value={i}>
                    {item.monthLabel} — {item.title}
                  </option>
                ))}
              </select>
            </label>

            <ImageControl
              label="Изображение мероприятия"
              value={event.image}
              onChange={(url) => setEvent({ image: url })}
            />

            <Text label="Название" value={event.title} onChange={(v) => setEvent({ title: v })} rows={2} />
            <Text
              label="Дата"
              hint="Так, как должно быть написано на сайте: «16-20 ақпан» или «Келісімді уақытта»."
              value={event.date}
              onChange={(v) => setEvent({ date: v })}
            />
            <Text label="Формат" value={event.format} onChange={(v) => setEvent({ format: v })} />
            <Text label="Ответственный" value={event.responsible} onChange={(v) => setEvent({ responsible: v })} />
            <Text label="Форма завершения" value={event.completion} onChange={(v) => setEvent({ completion: v })} />
            <Text
              label="Заголовок блока материалов"
              value={event.materialsHeading}
              onChange={(v) => setEvent({ materialsHeading: v })}
              rows={2}
            />

            <h4>Ссылки городов и районов</h4>
            <div className="editor-links">
              {event.links.map((link, i) => (
                <div key={i}>
                  <Text
                    label={`Название ${i + 1}`}
                    value={link.label}
                    onChange={(v) =>
                      setEvent({ links: event.links.map((l, j) => (j === i ? { ...l, label: v } : l)) })
                    }
                  />
                  <Text
                    label="Ссылка"
                    value={link.url}
                    onChange={(v) =>
                      setEvent({ links: event.links.map((l, j) => (j === i ? { ...l, url: v } : l)) })
                    }
                  />
                </div>
              ))}
            </div>
          </div>
        )}

        {section === 'covers' && (
          <div className="portal-card editor-card">
            <h3>Обложки и изображения</h3>
            <Text
              label="Название сайта"
              value={content.site.title}
              onChange={(v) => setContent((prev) => ({ ...prev, site: { ...prev.site, title: v } }))}
            />
            <ImageControl
              label="Обложка главной страницы"
              value={content.site.heroImage}
              onChange={(url) => setContent((prev) => ({ ...prev, site: { ...prev.site, heroImage: url } }))}
            />
            <ImageControl
              label="Обложка страниц мероприятий"
              value={content.site.eventHeroImage}
              onChange={(url) =>
                setContent((prev) => ({ ...prev, site: { ...prev.site, eventHeroImage: url } }))
              }
            />
            <ImageControl
              label="Изображение цели"
              value={content.home.goalImage}
              onChange={(url) => setHome({ goalImage: url })}
            />
            <ImageControl
              label="Изображение задач"
              value={content.home.tasksImage}
              onChange={(url) => setHome({ tasksImage: url })}
            />
            <ImageControl
              label="Изображение результатов"
              value={content.home.resultsImage}
              onChange={(url) => setHome({ resultsImage: url })}
            />
          </div>
        )}
      </div>
    </>
  );
}

/* ------------------------------------------------------------------ settings */

function SettingsTab({
  settings, onChange, onError, onFlash,
}: {
  settings: PortalSettings;
  onChange: (next: PortalSettings) => void;
  onError: (message: string) => void;
  onFlash: (message: string) => void;
}) {
  const [password, setPassword] = useState('');
  const [repeat, setRepeat] = useState('');
  const [busy, setBusy] = useState(false);
  const [migrating, setMigrating] = useState(false);

  async function migrateImages() {
    if (!window.confirm(
      'Скопировать все фотографии со старого сайта в ваше хранилище? Это можно делать повторно.',
    )) return;

    setMigrating(true);
    try {
      const response = await fetch('/api/admin/migrate-images', { method: 'POST' });
      const result = await response.json().catch(() => ({}));
      if (!response.ok) {
        onError(result.error ?? 'Не удалось перенести фотографии.');
        return;
      }
      if (result.ok) onFlash(result.message);
      else onError(result.message ?? 'Часть файлов перенести не удалось.');
    } catch {
      onError('Не удалось перенести фотографии — нет связи с сервером.');
    } finally {
      setMigrating(false);
    }
  }

  async function changePassword(e: React.FormEvent) {
    e.preventDefault();
    if (password.length < 10) {
      onError('Пароль должен содержать не менее 10 символов.');
      return;
    }
    if (password !== repeat) {
      onError('Пароли не совпадают.');
      return;
    }

    setBusy(true);
    try {
      const response = await fetch('/api/admin/password', {
        method: 'PUT',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ password }),
      });
      const result = await response.json().catch(() => ({}));
      if (!response.ok) {
        onError(result.error ?? 'Не удалось сменить пароль.');
        return;
      }
      setPassword('');
      setRepeat('');
      onFlash('Пароль изменён.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <div className="admin-section-heading">
        <div>
          <span>Управление</span>
          <h2>Настройки портала</h2>
        </div>
      </div>

      <div className="settings-stack">
        <div className="portal-card setting-card">
          <div>
            <InboxIcon />
            <div>
              <h3>Приём новых материалов</h3>
              <p>Если выключить, форма останется видимой, но отправить файл будет нельзя.</p>
            </div>
          </div>
          <button
            type="button"
            className={`setting-toggle${settings.submissions_open ? ' is-on' : ''}`}
            onClick={() => onChange({ ...settings, submissions_open: !settings.submissions_open })}
            aria-label="Приём новых материалов"
          >
            {settings.submissions_open ? <ToggleOn /> : <ToggleOff />}
          </button>
        </div>

        <div className="portal-card setting-card">
          <div>
            <InfoIcon />
            <div>
              <h3>Автоматическая публикация</h3>
              <p>
                При включении любой анонимно загруженный документ сразу станет доступен посетителям
                сайта. Риск: без проверки могут появиться спам, запрещённые материалы или
                персональные данные. Безопаснее оставить ручное подтверждение.
              </p>
            </div>
          </div>
          <button
            type="button"
            className={`setting-toggle${settings.auto_publish ? ' is-danger is-on' : ''}`}
            onClick={() => onChange({ ...settings, auto_publish: !settings.auto_publish })}
            aria-label="Автоматическая публикация"
          >
            {settings.auto_publish ? <ToggleOn /> : <ToggleOff />}
          </button>
        </div>

        <div className="portal-card setting-card">
          <div>
            <UploadIcon />
            <div>
              <h3>Перенести фотографии в своё хранилище</h3>
              <p>
                Фотографии оригинального сайта до сих пор лежат на старом проекте, который никто
                не поддерживает. Эта кнопка копирует их к вам и переключает сайт на новые адреса —
                после этого старый проект можно удалить. Нажимать можно повторно.
              </p>
            </div>
          </div>
          <button
            type="button"
            className="portal-button portal-button-secondary"
            onClick={migrateImages}
            disabled={migrating}
          >
            {migrating ? 'Переношу…' : 'Перенести'}
          </button>
        </div>

        <form className="portal-card editor-card" onSubmit={changePassword}>
          <h3>Смена пароля</h3>
          <Text label="Новый пароль" type="password" value={password} onChange={setPassword} hint="Не менее 10 символов" />
          <Text label="Повторите пароль" type="password" value={repeat} onChange={setRepeat} />
          <div className="portal-form-actions">
            <span />
            <button type="submit" className="portal-button portal-button-primary" disabled={busy}>
              {busy ? 'Сохраняю…' : 'Сохранить пароль'}
            </button>
          </div>
        </form>
      </div>
    </>
  );
}
