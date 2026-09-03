-- ============================================================================
--  Орлеу Маңғыстау — схема базы
--  Открой Supabase → SQL Editor → вставь всё это целиком → Run.
--  Запускать можно повторно, ничего не сломается.
-- ============================================================================

-- 1. Содержимое сайта: один документ, который редактирует куратор ------------
create table if not exists public.site_content (
  id          text primary key,
  data        jsonb not null,
  updated_at  timestamptz not null default now()
);

-- 2. Настройки портала и хеш пароля администратора ---------------------------
create table if not exists public.settings (
  key    text primary key,
  value  jsonb
);

insert into public.settings (key, value) values
  ('submissions_open', 'true'::jsonb),
  ('auto_publish',     'false'::jsonb)
on conflict (key) do nothing;

-- 3. Материалы, которые присылают преподаватели ------------------------------
create table if not exists public.submissions (
  id            uuid primary key default gen_random_uuid(),
  created_at    timestamptz not null default now(),
  full_name     text not null,
  organization  text,
  region        text not null,
  event_route   text,
  title         text not null,
  description   text,
  file_path     text,
  file_name     text,
  file_size     bigint,
  mime          text,
  status        text not null default 'pending'
                check (status in ('pending', 'published', 'rejected')),
  -- Настоящий IP не хранится: только солёный хеш, нужный для лимита частоты.
  ip_hash       text,
  consent_at    timestamptz
);

create index if not exists submissions_status_created_idx
  on public.submissions (status, created_at desc);

-- Индекс под проверку лимита «не больше 5 заявок в час с одного адреса».
create index if not exists submissions_ip_created_idx
  on public.submissions (ip_hash, created_at desc);

-- 4. Хранилище файлов --------------------------------------------------------
-- materials — приватный: документ становится доступен только после публикации,
-- и ссылка на него выдаётся сервером на время.
-- images — публичный: сюда куратор грузит фотографии для страниц сайта.
insert into storage.buckets (id, name, public)
values ('materials', 'materials', false)
on conflict (id) do nothing;

insert into storage.buckets (id, name, public)
values ('images', 'images', true)
on conflict (id) do nothing;

-- 5. Доступ ------------------------------------------------------------------
-- RLS включён, но политик намеренно нет: сайт ходит в базу только с сервера,
-- сервисным ключом, который RLS обходит. Публичный ключ при этом не даёт
-- ни прочитать, ни записать ничего — даже если он куда-то утечёт.
alter table public.site_content enable row level security;
alter table public.settings     enable row level security;
alter table public.submissions  enable row level security;
