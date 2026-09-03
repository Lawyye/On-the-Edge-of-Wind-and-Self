'use client';

import { useRef, useState } from 'react';
import Link from 'next/link';
import type { SiteEvent } from '@/lib/types';
import { CheckIcon, InfoIcon, UploadIcon } from './Icons';

type Props = {
  regions: string[];
  events: SiteEvent[];
  open: boolean;
  autoPublish: boolean;
};

export default function SubmitForm({ regions, events, open, autoPublish }: Props) {
  const formRef = useRef<HTMLFormElement>(null);
  const [fileName, setFileName] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [done, setDone] = useState(false);
  // Used server-side to reject submissions completed faster than a human could.
  const [startedAt] = useState(() => Date.now());

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError('');
    setBusy(true);

    try {
      const body = new FormData(event.currentTarget);
      body.set('started_at', String(startedAt));

      const response = await fetch('/api/submissions', { method: 'POST', body });
      const result = await response.json().catch(() => ({}));

      if (!response.ok) {
        setError(result.error ?? 'Материал жіберілмеді / Не удалось отправить.');
        return;
      }

      setDone(true);
      formRef.current?.reset();
      setFileName('');
    } catch {
      setError('Байланыс үзілді / Нет связи с сервером.');
    } finally {
      setBusy(false);
    }
  }

  if (done) {
    return (
      <div className="portal-card portal-empty">
        <CheckIcon />
        <h2>Материал жіберілді</h2>
        <p>
          {autoPublish
            ? 'Материал жіберілді және сайтта жарияланды.'
            : 'Материал жіберілді. Әкімші оны тексергеннен кейін сайтта жарияланады.'}
        </p>
        <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', justifyContent: 'center' }}>
          <button type="button" className="portal-button portal-button-secondary" onClick={() => setDone(false)}>
            Тағы бір материал жіберу
          </button>
          <Link href="/materials" className="portal-button portal-button-primary">
            Жарияланған материалдар
          </Link>
        </div>
      </div>
    );
  }

  return (
    <form ref={formRef} className="portal-card submission-form" onSubmit={handleSubmit}>
      {!open && (
        <div className="portal-notice portal-notice-error">
          <InfoIcon />
          <span>Материалдарды қабылдау уақытша жабық / Приём материалов временно закрыт.</span>
        </div>
      )}

      {!autoPublish && open && (
        <div className="portal-notice">
          <InfoIcon />
          <span>
            Материал әкімші тексергеннен кейін сайтта жарияланады. Документ появится на сайте
            после проверки куратором.
          </span>
        </div>
      )}

      {error && (
        <div className="portal-notice portal-notice-error">
          <InfoIcon />
          <span>{error}</span>
        </div>
      )}

      <div className="portal-form-heading">
        <span className="portal-step">1</span>
        <div>
          <h2>Қатысушы туралы мәлімет</h2>
          <p>Укажите автора и организацию, чтобы куратор мог найти материал.</p>
        </div>
      </div>

      <div className="portal-form-grid">
        <label className="portal-field">
          <span>Аты-жөні / ФИО</span>
          <input name="full_name" required maxLength={160} autoComplete="name" />
        </label>

        <label className="portal-field">
          <span>Мектеп немесе ұйым / Организация</span>
          <input name="organization" maxLength={200} />
        </label>

        <label className="portal-field">
          <span>Қала немесе аудан / Город или район</span>
          <select name="region" required defaultValue="">
            <option value="" disabled>
              Таңдаңыз / Выберите
            </option>
            {regions.map((region) => (
              <option key={region} value={region}>
                {region}
              </option>
            ))}
          </select>
        </label>

        <label className="portal-field">
          <span>Іс-шара / Мероприятие</span>
          <select name="event_route" defaultValue="">
            <option value="">Таңдаңыз / Выберите</option>
            {events.map((event) => (
              <option key={event.route} value={event.route}>
                {event.monthLabel} — {event.title}
              </option>
            ))}
          </select>
        </label>
      </div>

      <div className="portal-form-divider">
        <div className="portal-form-heading">
          <span className="portal-step">2</span>
          <div>
            <h2>Материал</h2>
            <p>PDF, Word, PowerPoint, Excel, JPG немесе PNG. Максимальный размер — 20 МБ.</p>
          </div>
        </div>

        <div className="portal-form-grid">
          <label className="portal-field portal-field-wide">
            <span>Материалдың атауы / Название</span>
            <input name="title" required maxLength={250} />
          </label>

          <label className="portal-field portal-field-wide">
            <span>Қысқаша сипаттама / Описание</span>
            <textarea name="description" rows={3} maxLength={1200} />
          </label>
        </div>

        <label className="file-drop">
          <UploadIcon />
          <strong>{fileName || 'Құжатты таңдаңыз / Выберите документ'}</strong>
          <span>Нажмите, чтобы открыть файлы телефона</span>
          <input
            type="file"
            name="file"
            required
            accept=".pdf,.doc,.docx,.ppt,.pptx,.xls,.xlsx,.jpg,.jpeg,.png"
            onChange={(e) => setFileName(e.target.files?.[0]?.name ?? '')}
          />
        </label>
      </div>

      <label className="privacy-check">
        <input type="checkbox" name="consent" required />
        <span>
          Құжатта жариялауға тыйым салынған жеке деректер жоқ екенін растаймын. Подтверждаю, что в
          документе нет персональных данных, которые нельзя публиковать.
        </span>
      </label>

      {/* Hidden from people, irresistible to form-filling bots. */}
      <div className="honeypot" aria-hidden>
        <label>
          Website
          <input name="website" tabIndex={-1} autoComplete="off" />
        </label>
      </div>

      <div className="portal-form-actions">
        <Link href="/" className="portal-button portal-button-secondary">
          Сайтқа оралу
        </Link>
        <button type="submit" className="portal-button portal-button-primary" disabled={busy || !open}>
          <UploadIcon />
          {busy ? 'Материал жіберілуде…' : 'Материалды жіберу'}
        </button>
      </div>
    </form>
  );
}
