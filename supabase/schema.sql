-- Схема приложения «On the Edge». Повторяет то, что применено в проекте
-- Supabase, и может развернуть его с нуля. Все имена с префиксом ew_, чтобы
-- не пересекаться с таблицами сайта, живущими в том же проекте.
--
-- Главный принцип: счётчики, модерацию и выдачу достижений ведёт база.
-- Клиенту нельзя доверять подсчёт того, на что он сам влияет, поэтому прав
-- на запись в эти места у него просто нет.

-- ─── ТАБЛИЦЫ ──────────────────────────────────────────────────────────────

-- Ни почты, ни телефона, ни пароля: строка появляется на анонимную сессию
-- Supabase, а человек добавляет к ней придуманное имя и знак.
create table if not exists public.ew_profiles (
  id           uuid primary key references auth.users(id) on delete cascade,
  nickname     text not null check (char_length(btrim(nickname)) between 2 and 24),
  avatar_kind  smallint not null default 0,
  avatar_url   text,
  created_at   timestamptz not null default now(),
  last_seen_on date not null default current_date,
  streak_days  integer not null default 1,
  banned       boolean not null default false
);

-- Имя должно быть узнаваемым, поэтому занимается целиком и без учёта регистра.
create unique index if not exists ew_profiles_nickname_key
  on public.ew_profiles (lower(btrim(nickname)));

create table if not exists public.ew_stories (
  id             uuid primary key default gen_random_uuid(),
  author_id      uuid not null references public.ew_profiles(id) on delete cascade,
  category       text not null,
  title          text not null check (char_length(btrim(title)) between 3 and 120),
  body           text not null check (char_length(btrim(body)) between 1 and 4000),
  created_at     timestamptz not null default now(),
  like_count     integer not null default 0,
  response_count integer not null default 0,
  report_count   integer not null default 0,
  hidden         boolean not null default false
);
create index if not exists ew_stories_feed_idx   on public.ew_stories (created_at desc) where not hidden;
create index if not exists ew_stories_cat_idx    on public.ew_stories (category, created_at desc) where not hidden;
create index if not exists ew_stories_author_idx on public.ew_stories (author_id, created_at desc);

create table if not exists public.ew_responses (
  id           uuid primary key default gen_random_uuid(),
  story_id     uuid not null references public.ew_stories(id) on delete cascade,
  author_id    uuid not null references public.ew_profiles(id) on delete cascade,
  body         text not null check (char_length(btrim(body)) between 1 and 2000),
  created_at   timestamptz not null default now(),
  report_count integer not null default 0,
  hidden       boolean not null default false
);
create index if not exists ew_responses_story_idx  on public.ew_responses (story_id, created_at);
create index if not exists ew_responses_author_idx on public.ew_responses (author_id, created_at desc);

-- «Я рядом». Осознанно не «лайк»: одна отметка на человека, снимается повторно.
create table if not exists public.ew_likes (
  story_id   uuid not null references public.ew_stories(id) on delete cascade,
  user_id    uuid not null references public.ew_profiles(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (story_id, user_id)
);
create index if not exists ew_likes_user_idx on public.ew_likes (user_id);

create table if not exists public.ew_saves (
  story_id   uuid not null references public.ew_stories(id) on delete cascade,
  user_id    uuid not null references public.ew_profiles(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (story_id, user_id)
);

create table if not exists public.ew_reports (
  id          uuid primary key default gen_random_uuid(),
  target_type text not null check (target_type in ('story','response')),
  target_id   uuid not null,
  reporter_id uuid not null references public.ew_profiles(id) on delete cascade,
  reason      text not null,
  created_at  timestamptz not null default now(),
  unique (target_type, target_id, reporter_id)
);

create table if not exists public.ew_achievements (
  code text primary key, title text not null, description text not null,
  goal integer not null default 1, sort integer not null default 0
);

create table if not exists public.ew_user_achievements (
  user_id   uuid not null references public.ew_profiles(id) on delete cascade,
  code      text not null references public.ew_achievements(code) on delete cascade,
  earned_at timestamptz not null default now(),
  primary key (user_id, code)
);

-- Все до одного выдаются за отклик другим и за возвращение. Ни одного — за
-- количество собственных тяжёлых историй: иначе приложение начнёт поощрять
-- ровно то, от чего должно помогать.
insert into public.ew_achievements (code, title, description, goal, sort) values
  ('first_step',  'Первый шаг',   'Оставить первую историю.',                       1,  1),
  ('nearby',      'Я рядом',      'Десять раз поддержать чужую историю.',          10,  2),
  ('week_here',   'Неделя здесь', 'Заходить семь дней подряд.',                     7,  3),
  ('heard',       'Услышали',     'Твою историю поддержали пять человек.',          5,  4),
  ('own_person',  'Свой человек', 'Ответить пятидесяти людям.',                    50,  5),
  ('night_shift', 'Ночная смена', 'Ответить кому-то между полуночью и пятью утра.', 1,  6)
on conflict (code) do update
  set title = excluded.title, description = excluded.description,
      goal = excluded.goal, sort = excluded.sort;

-- ─── ЛОГИКА ───────────────────────────────────────────────────────────────

create or replace function public.ew_grant(p_user uuid, p_code text)
returns void language sql security definer set search_path = public as $$
  insert into public.ew_user_achievements (user_id, code)
  values (p_user, p_code) on conflict (user_id, code) do nothing;
$$;

create or replace function public.ew_after_like()
returns trigger language plpgsql security definer set search_path = public as $$
declare v_author uuid; v_count integer; v_given integer;
begin
  if tg_op = 'INSERT' then
    update public.ew_stories set like_count = like_count + 1
      where id = new.story_id returning author_id, like_count into v_author, v_count;
    if v_count >= 5 then perform public.ew_grant(v_author, 'heard'); end if;
    select count(*) into v_given from public.ew_likes where user_id = new.user_id;
    if v_given >= 10 then perform public.ew_grant(new.user_id, 'nearby'); end if;
    return new;
  else
    update public.ew_stories set like_count = greatest(like_count - 1, 0) where id = old.story_id;
    return old;
  end if;
end; $$;

drop trigger if exists ew_likes_count on public.ew_likes;
create trigger ew_likes_count after insert or delete on public.ew_likes
  for each row execute function public.ew_after_like();

create or replace function public.ew_after_response()
returns trigger language plpgsql security definer set search_path = public as $$
declare v_given integer; v_hour integer;
begin
  if tg_op = 'INSERT' then
    update public.ew_stories set response_count = response_count + 1 where id = new.story_id;
    select count(*) into v_given from public.ew_responses where author_id = new.author_id;
    if v_given >= 50 then perform public.ew_grant(new.author_id, 'own_person'); end if;
    -- Часовой пояс аудитории; при выходе за пределы Казахстана вынести в настройку.
    v_hour := extract(hour from (new.created_at at time zone 'Asia/Almaty'));
    if v_hour >= 0 and v_hour < 5 then perform public.ew_grant(new.author_id, 'night_shift'); end if;
    return new;
  else
    update public.ew_stories set response_count = greatest(response_count - 1, 0) where id = old.story_id;
    return old;
  end if;
end; $$;

drop trigger if exists ew_responses_count on public.ew_responses;
create trigger ew_responses_count after insert or delete on public.ew_responses
  for each row execute function public.ew_after_response();

create or replace function public.ew_after_story()
returns trigger language plpgsql security definer set search_path = public as $$
begin perform public.ew_grant(new.author_id, 'first_step'); return new; end; $$;

drop trigger if exists ew_stories_first on public.ew_stories;
create trigger ew_stories_first after insert on public.ew_stories
  for each row execute function public.ew_after_story();

-- Три независимые жалобы убирают запись из ленты до разбора: на площадке для
-- подростков лучше лишний раз спрятать, чем лишний раз показать.
create or replace function public.ew_after_report()
returns trigger language plpgsql security definer set search_path = public as $$
declare v_count integer;
begin
  if new.target_type = 'story' then
    update public.ew_stories set report_count = report_count + 1
      where id = new.target_id returning report_count into v_count;
    if v_count >= 3 then update public.ew_stories set hidden = true where id = new.target_id; end if;
  else
    update public.ew_responses set report_count = report_count + 1
      where id = new.target_id returning report_count into v_count;
    if v_count >= 3 then update public.ew_responses set hidden = true where id = new.target_id; end if;
  end if;
  return new;
end; $$;

drop trigger if exists ew_reports_apply on public.ew_reports;
create trigger ew_reports_apply after insert on public.ew_reports
  for each row execute function public.ew_after_report();

create or replace function public.ew_touch()
returns public.ew_profiles language plpgsql security definer set search_path = public as $$
declare v_row public.ew_profiles;
begin
  update public.ew_profiles
     set streak_days = case
           when last_seen_on = current_date then streak_days
           when last_seen_on = current_date - 1 then streak_days + 1
           else 1 end,
         last_seen_on = current_date
   where id = auth.uid() returning * into v_row;
  if v_row.streak_days >= 7 then perform public.ew_grant(v_row.id, 'week_here'); end if;
  return v_row;
end; $$;

-- Лента одним запросом: история, имя и знак автора, и отметился ли уже ты сам.
create or replace view public.ew_feed with (security_invoker = true) as
  select s.id, s.category, s.title, s.body, s.created_at,
         s.like_count, s.response_count, s.author_id,
         p.nickname as author_nickname,
         p.avatar_kind as author_avatar_kind,
         p.avatar_url as author_avatar_url,
         exists (select 1 from public.ew_likes l where l.story_id = s.id and l.user_id = auth.uid()) as liked_by_me,
         exists (select 1 from public.ew_saves v where v.story_id = s.id and v.user_id = auth.uid()) as saved_by_me,
         s.author_id = auth.uid() as is_mine
    from public.ew_stories s
    join public.ew_profiles p on p.id = s.author_id
   where not s.hidden and not p.banned;

-- Каталог достижений вместе с моим прогрессом. Прогресс считает база.
create or replace function public.ew_my_achievements()
returns table (code text, title text, description text,
               goal integer, progress integer, earned_at timestamptz, sort integer)
language sql stable security definer set search_path = public as $$
  with me as (select auth.uid() as uid),
  stats as (
    select
      (select count(*) from public.ew_likes     where user_id  = (select uid from me)) as likes_given,
      (select count(*) from public.ew_responses where author_id = (select uid from me)) as responses_given,
      (select coalesce(max(streak_days),0) from public.ew_profiles where id = (select uid from me)) as streak,
      (select count(*) from public.ew_stories   where author_id = (select uid from me)) as stories_written,
      (select coalesce(max(like_count),0) from public.ew_stories where author_id = (select uid from me)) as best_story_likes
  )
  select a.code, a.title, a.description, a.goal,
         least(case a.code
             when 'first_step'  then s.stories_written
             when 'nearby'      then s.likes_given
             when 'week_here'   then s.streak
             when 'heard'       then s.best_story_likes
             when 'own_person'  then s.responses_given
             when 'night_shift' then (case when ua.code is null then 0 else 1 end)
             else 0 end, a.goal)::integer as progress,
         ua.earned_at, a.sort
    from public.ew_achievements a
    cross join stats s
    left join public.ew_user_achievements ua on ua.code = a.code and ua.user_id = (select uid from me)
   order by a.sort;
$$;

-- ─── ДОСТУП ───────────────────────────────────────────────────────────────

alter table public.ew_profiles          enable row level security;
alter table public.ew_stories           enable row level security;
alter table public.ew_responses         enable row level security;
alter table public.ew_likes             enable row level security;
alter table public.ew_saves             enable row level security;
alter table public.ew_reports           enable row level security;
alter table public.ew_achievements      enable row level security;
alter table public.ew_user_achievements enable row level security;

create or replace function public.ew_active()
returns boolean language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.ew_profiles where id = auth.uid() and not banned);
$$;

-- Имя и знак видны всем: подпись под историей должна что-то значить.
drop policy if exists ew_profiles_read on public.ew_profiles;
create policy ew_profiles_read on public.ew_profiles for select to authenticated using (true);
drop policy if exists ew_profiles_insert on public.ew_profiles;
create policy ew_profiles_insert on public.ew_profiles for insert to authenticated with check (id = auth.uid());
drop policy if exists ew_profiles_update on public.ew_profiles;
create policy ew_profiles_update on public.ew_profiles for update to authenticated
  using (id = auth.uid()) with check (id = auth.uid());

-- UPDATE разрешает трогать любые колонки своей строки, поэтому бан и дату
-- регистрации защищаем отдельно: иначе забаненный разбанит себя сам.
create or replace function public.ew_profiles_guard()
returns trigger language plpgsql security invoker set search_path = public as $$
begin
  new.id := old.id; new.banned := old.banned; new.created_at := old.created_at;
  return new;
end; $$;

drop trigger if exists ew_profiles_guard_trg on public.ew_profiles;
create trigger ew_profiles_guard_trg before update on public.ew_profiles
  for each row execute function public.ew_profiles_guard();

-- Скрытое по жалобам не видно никому, кроме автора.
drop policy if exists ew_stories_read on public.ew_stories;
create policy ew_stories_read on public.ew_stories for select to authenticated
  using (not hidden or author_id = auth.uid());
drop policy if exists ew_stories_insert on public.ew_stories;
create policy ew_stories_insert on public.ew_stories for insert to authenticated
  with check (author_id = auth.uid() and public.ew_active());
drop policy if exists ew_stories_delete on public.ew_stories;
create policy ew_stories_delete on public.ew_stories for delete to authenticated
  using (author_id = auth.uid());

drop policy if exists ew_responses_read on public.ew_responses;
create policy ew_responses_read on public.ew_responses for select to authenticated
  using (not hidden or author_id = auth.uid());
drop policy if exists ew_responses_insert on public.ew_responses;
create policy ew_responses_insert on public.ew_responses for insert to authenticated
  with check (author_id = auth.uid() and public.ew_active());
drop policy if exists ew_responses_delete on public.ew_responses;
create policy ew_responses_delete on public.ew_responses for delete to authenticated
  using (author_id = auth.uid());

-- Кто именно поддержал — не дело ни других, ни автора: в ленте только число.
drop policy if exists ew_likes_read on public.ew_likes;
create policy ew_likes_read on public.ew_likes for select to authenticated using (user_id = auth.uid());
drop policy if exists ew_likes_insert on public.ew_likes;
create policy ew_likes_insert on public.ew_likes for insert to authenticated
  with check (user_id = auth.uid() and public.ew_active());
drop policy if exists ew_likes_delete on public.ew_likes;
create policy ew_likes_delete on public.ew_likes for delete to authenticated using (user_id = auth.uid());

drop policy if exists ew_saves_own on public.ew_saves;
create policy ew_saves_own on public.ew_saves for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());

-- Жалобу можно только подать: по читаемой таблице было бы видно, кто на кого.
drop policy if exists ew_reports_insert on public.ew_reports;
create policy ew_reports_insert on public.ew_reports for insert to authenticated
  with check (reporter_id = auth.uid());

drop policy if exists ew_achievements_read on public.ew_achievements;
create policy ew_achievements_read on public.ew_achievements for select to authenticated using (true);
drop policy if exists ew_user_achievements_read on public.ew_user_achievements;
create policy ew_user_achievements_read on public.ew_user_achievements for select to authenticated
  using (user_id = auth.uid());

-- ─── ПРАВА ────────────────────────────────────────────────────────────────
-- PostgREST публикует как RPC любую функцию из public. Для SECURITY DEFINER
-- это дыра: ew_grant выдала бы достижение кому угодно и какое угодно.
-- Триггерам права не нужны — они исполняются от владельца, а не от вызывающего.

revoke all on function public.ew_grant(uuid, text) from public, anon, authenticated;
revoke all on function public.ew_after_like()     from public, anon, authenticated;
revoke all on function public.ew_after_response() from public, anon, authenticated;
revoke all on function public.ew_after_story()    from public, anon, authenticated;
revoke all on function public.ew_after_report()   from public, anon, authenticated;
revoke all on function public.ew_profiles_guard() from public, anon, authenticated;

revoke all on function public.ew_touch() from public, anon;
grant execute on function public.ew_touch() to authenticated;
revoke all on function public.ew_my_achievements() from public, anon;
grant execute on function public.ew_my_achievements() to authenticated;
-- ew_active читается из политик, поэтому право нужно вошедшему; наружу она
-- сообщает только про самого спрашивающего, так что это безопасно.
revoke all on function public.ew_active() from public, anon;
grant execute on function public.ew_active() to authenticated;

-- Supabase по умолчанию раздаёт на таблицы public все права. Здесь запрет
-- двойной: нет политики И нет самого права. Иначе один случайно добавленный
-- разрешительный policy открывает запись туда, где её быть не должно.
revoke insert, update, delete on public.ew_user_achievements from anon, authenticated;
revoke insert, update, delete on public.ew_achievements      from anon, authenticated;
revoke select, update, delete on public.ew_reports           from anon, authenticated;
revoke update on public.ew_stories   from anon, authenticated;
revoke update on public.ew_responses from anon, authenticated;
revoke update on public.ew_likes     from anon, authenticated;
revoke update on public.ew_saves     from anon, authenticated;

-- Гостю в приложении делать нечего: всё происходит после анонимного входа.
revoke all on public.ew_profiles, public.ew_stories, public.ew_responses,
              public.ew_likes, public.ew_saves, public.ew_reports, public.ew_feed
  from anon;

-- ─── ХРАНИЛИЩЕ ────────────────────────────────────────────────────────────
-- Аватары читают все (знак стоит рядом с именем), пишет каждый в свою папку.

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('ew-avatars', 'ew-avatars', true, 2097152,
        array['image/jpeg','image/png','image/webp'])
on conflict (id) do update
  set public = true, file_size_limit = 2097152,
      allowed_mime_types = array['image/jpeg','image/png','image/webp'];

drop policy if exists ew_avatars_read on storage.objects;
create policy ew_avatars_read on storage.objects for select using (bucket_id = 'ew-avatars');
drop policy if exists ew_avatars_write on storage.objects;
create policy ew_avatars_write on storage.objects for insert to authenticated
  with check (bucket_id = 'ew-avatars' and (storage.foldername(name))[1] = auth.uid()::text);
drop policy if exists ew_avatars_update on storage.objects;
create policy ew_avatars_update on storage.objects for update to authenticated
  using (bucket_id = 'ew-avatars' and (storage.foldername(name))[1] = auth.uid()::text);
drop policy if exists ew_avatars_delete on storage.objects;
create policy ew_avatars_delete on storage.objects for delete to authenticated
  using (bucket_id = 'ew-avatars' and (storage.foldername(name))[1] = auth.uid()::text);
