'use client';

import { useState } from 'react';
import { assetUrl } from '@/lib/assets';
import { UploadIcon } from '../Icons';

/**
 * Replaces one picture on the site. Uploading stores an absolute Supabase URL,
 * which is how a page stops depending on the previous deployment's images.
 */
export default function ImageControl({
  label,
  value,
  onChange,
}: {
  label: string;
  value: string;
  onChange: (url: string) => void;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  async function upload(file: File) {
    setBusy(true);
    setError('');
    try {
      const body = new FormData();
      body.set('file', file);
      const response = await fetch('/api/admin/upload-image', { method: 'POST', body });
      const result = await response.json().catch(() => ({}));
      if (!response.ok) {
        setError(result.error ?? 'Не удалось загрузить изображение.');
        return;
      }
      onChange(result.url);
    } catch {
      setError('Не удалось загрузить изображение.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="image-control">
      <span>{label}</span>
      <div>
        {value ? <img src={assetUrl(value)} alt="" /> : <div className="document-preview"><UploadIcon /></div>}
        <label>
          <UploadIcon />
          {busy ? 'Загрузка…' : 'Заменить изображение'}
          <input
            type="file"
            accept=".jpg,.jpeg,.png"
            disabled={busy}
            onChange={(e) => {
              const file = e.target.files?.[0];
              if (file) void upload(file);
              e.target.value = '';
            }}
          />
        </label>
      </div>
      {error && <small>{error}</small>}
    </div>
  );
}
