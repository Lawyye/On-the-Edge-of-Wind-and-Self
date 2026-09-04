'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { LockIcon } from '../Icons';

export default function AdminLogin() {
  const router = useRouter();
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError('');

    try {
      const response = await fetch('/api/admin/login', {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ password }),
      });
      const result = await response.json().catch(() => ({}));

      if (!response.ok) {
        setError(result.error ?? 'Кіру мүмкін болмады / Не удалось войти.');
        return;
      }
      router.refresh();
    } catch {
      setError('Байланыс үзілді / Нет связи с сервером.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="admin-login-page">
      <div className="admin-login-card">
        <div className="admin-login-icon">
          <LockIcon />
        </div>
        <h1>Панель администратора</h1>
        <p>Здесь куратор проверяет документы и меняет информацию сайта с телефона.</p>

        <form onSubmit={submit}>
          {error && (
            <div className="portal-notice portal-notice-error">
              <span>{error}</span>
            </div>
          )}
          <label className="portal-field">
            <span>Пароль</span>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete="current-password"
              required
            />
          </label>
          <button
            type="submit"
            className="portal-button portal-button-primary admin-login-button"
            disabled={busy}
          >
            {busy ? 'Проверяю доступ…' : 'Войти'}
          </button>
        </form>

        <a className="admin-login-back" href="/">
          Вернуться на сайт
        </a>
      </div>
    </div>
  );
}
