-- Закрывает то, что нашёл пентест 21.11.2026 (scratchpad: attack.py, attack2.py).
-- Совместимо со старой сборкой приложения: чтение не трогается, меняется
-- только то, что клиент вправе записать. Видимость чужих колонок закрывает
-- следующая миграция — вместе с новой сборкой, которая не просит select=*.
--
-- Что было дырой:
--   * при вставке клиент сам задавал like_count, created_at, hidden — запись
--     с датой 2030 года висела бы первой в ленте у всех четыре года;
--   * отклик с подделанным временем выдавал «Ночную смену», правка
--     streak_days — «Неделю здесь»;
--   * avatar_url принимал любой адрес: чужой сервер видел бы IP каждого, кто
--     открыл ленту;
--   * категория и причина жалобы без ограничений по длине и составу;
--   * жалоба на свою же запись и на несуществующую;
--   * три аккаунта, созданные за секунду, прятали любую чужую историю;
--   * не было ни одного лимита — 12 записей за несколько секунд проходили.

-- ─── ПРАВА НА ЗАПИСЬ ПО КОЛОНКАМ ──────────────────────────────────────────
-- Клиент пишет только то, что человек действительно вводит. Остальное ставит
-- база: даты, счётчики, флаги модерации. Это первая линия; триггеры ниже —
-- вторая, на случай если кто-то вернёт право на всю таблицу.

revoke insert, update on public.ew_profiles from anon, authenticated;
grant insert (id, nickname, avatar_kind, avatar_url) on public.ew_profiles to authenticated;
grant update (nickname, avatar_kind, avatar_url)     on public.ew_profiles to authenticated;

revoke insert, update on public.ew_stories from anon, authenticated;
grant insert (author_id, category, title, body) on public.ew_stories to authenticated;

revoke insert, update on public.ew_responses from anon, authenticated;
grant insert (story_id, author_id, body) on public.ew_responses to authenticated;

revoke insert, update on public.ew_likes from anon, authenticated;
grant insert (story_id, user_id) on public.ew_likes to authenticated;

revoke insert, update on public.ew_saves from anon, authenticated;
grant insert (story_id, user_id) on public.ew_saves to authenticated;

revoke insert, update on public.ew_reports from anon, authenticated;
grant insert (target_type, target_id, reporter_id, reason) on public.ew_reports to authenticated;

-- TRUNCATE обходит RLS целиком, TRIGGER и REFERENCES клиенту не нужны вовсе.
-- Через API их не вызвать, но права, которые не нужны, не должны лежать.
revoke truncate, trigger, references on
  public.ew_profiles, public.ew_stories, public.ew_responses, public.ew_likes,
  public.ew_saves, public.ew_reports, public.ew_achievements, public.ew_user_achievements
  from anon, authenticated;
-- Представление из двух таблиц обновлять нельзя, но права на это были выданы.
revoke insert, update, delete, truncate, trigger, references on public.ew_feed from anon, authenticated;

-- ─── ОГРАНИЧЕНИЯ ДАННЫХ ───────────────────────────────────────────────────

update public.ew_stories set category = 'Другое'
 where category not in ('Просто поговорить','Отношения','Буллинг','Одиночество','Тревога',
                        'Семья','Учёба','Работа','Перемены','Другое');
alter table public.ew_stories drop constraint if exists ew_stories_category_check;
alter table public.ew_stories add constraint ew_stories_category_check check (
  category in ('Просто поговорить','Отношения','Буллинг','Одиночество','Тревога',
               'Семья','Учёба','Работа','Перемены','Другое'));

update public.ew_reports set reason = left(btrim(reason), 200) where char_length(reason) > 200;
alter table public.ew_reports drop constraint if exists ew_reports_reason_check;
alter table public.ew_reports add constraint ew_reports_reason_check
  check (char_length(reason) between 1 and 200);

-- Имя: без невидимых символов, управляющих символов и двойных пробелов —
-- иначе «Полынь» и та же «Полынь» с невидимым пробелом внутри выглядят одинаково.
alter table public.ew_profiles drop constraint if exists ew_profiles_nickname_clean;
alter table public.ew_profiles add constraint ew_profiles_nickname_clean check (
  nickname = btrim(nickname)
  and nickname !~ '[[:cntrl:]]'
  -- мягкий перенос, невидимые и разворачивающие текст символы (U+00AD, U+200B–200F,
  -- U+202A–202E, U+2060–206F, U+FEFF и родня): chr(), а не escape-последовательности — так
  -- строка переживает любую пересылку без превращения в сами невидимки.
  and nickname !~ ('[' || chr(173) || chr(847) || chr(1564) || chr(4447) || chr(4448)
                    || chr(6068) || chr(6069) || chr(6158) || chr(8203) || '-' || chr(8207)
                    || chr(8234) || '-' || chr(8238) || chr(8288) || '-' || chr(8303)
                    || chr(12644) || chr(65024) || '-' || chr(65039) || chr(65279) || chr(65440)
                    || ']')
  and nickname !~ '\s\s');

alter table public.ew_profiles drop constraint if exists ew_profiles_avatar_kind_check;
alter table public.ew_profiles add constraint ew_profiles_avatar_kind_check
  check (avatar_kind between 0 and 15);

create index if not exists ew_reports_reporter_idx on public.ew_reports (reporter_id, created_at desc);

-- ─── ПРОФИЛЬ ──────────────────────────────────────────────────────────────

create or replace function public.ew_profiles_guard()
returns trigger language plpgsql security invoker set search_path = public as $$
declare
  -- Аватар — только из своей папки нашего хранилища. Адрес проекта зашит
  -- нарочно: при переезде проекта его нужно поменять здесь же.
  v_prefix constant text := 'https://wwwxthxyfglcgpuscafz.supabase.co/storage/v1/object/public/ew-avatars/';
  v_check_name boolean;
begin
  if tg_op = 'UPDATE' then
    new.id := old.id;
    new.banned := old.banned;
    new.created_at := old.created_at;
  else
    new.created_at := now();
    new.banned := false;
    new.streak_days := 1;
    new.last_seen_on := current_date;
  end if;

  new.nickname := btrim(new.nickname);
  -- Проверяется только новое имя: старое, придуманное до правила, остаётся.
  if tg_op = 'INSERT' then
    v_check_name := true;
  else
    v_check_name := new.nickname is distinct from old.nickname;
  end if;
  if v_check_name
     and lower(new.nickname) ~ '(модер|админ|поддержк|support|admin|moder|психолог|психиатр|психотерап|врач)' then
    raise exception 'nickname_reserved' using errcode = 'P0001',
      hint = 'Имя похоже на служебное или на специалиста — выбери другое.';
  end if;

  if new.avatar_url is not null and (
       left(new.avatar_url, length(v_prefix)) <> v_prefix
       or substr(new.avatar_url, length(v_prefix) + 1)
          !~ ('^' || new.id::text || '/avatar\.(jpg|jpeg|png|webp)(\?v=[0-9]{1,16})?$')) then
    raise exception 'avatar_url_rejected' using errcode = 'P0001';
  end if;
  return new;
end; $$;

drop trigger if exists ew_profiles_guard_trg on public.ew_profiles;
create trigger ew_profiles_guard_trg before insert or update on public.ew_profiles
  for each row execute function public.ew_profiles_guard();

-- ─── ИСТОРИИ И ОТКЛИКИ: ЛИМИТЫ И ПОВТОРЫ ─────────────────────────────────
-- Лимиты рассчитаны на живого человека, которому плохо и который пишет
-- много, а не на скрипт. Повтор одного и того же текста — почти всегда
-- двойное нажатие на слабой сети, а не желание запостить дважды.

create or replace function public.ew_stories_before_insert()
returns trigger language plpgsql security definer set search_path = public as $$
declare v_hour integer; v_day integer;
begin
  -- Чужое имя в author_id всё равно отсечёт политика, но уже после этого
  -- триггера — а он считает лимиты по author_id и не должен работать на чужом.
  if auth.uid() is not null and new.author_id is distinct from auth.uid() then
    raise exception 'not_yours' using errcode = '42501';
  end if;
  new.created_at := now();
  new.like_count := 0;
  new.response_count := 0;
  new.report_count := 0;
  new.hidden := false;
  new.title := btrim(new.title);
  new.body := btrim(new.body);

  select count(*) filter (where created_at > now() - interval '1 hour'), count(*)
    into v_hour, v_day
    from public.ew_stories
   where author_id = new.author_id and created_at > now() - interval '1 day';
  if v_hour >= 5 or v_day >= 15 then
    raise exception 'rate_limited' using errcode = 'P0001';
  end if;

  if exists (select 1 from public.ew_stories
              where author_id = new.author_id and title = new.title and body = new.body
                and created_at > now() - interval '1 day') then
    raise exception 'duplicate' using errcode = 'P0001';
  end if;
  return new;
end; $$;

drop trigger if exists ew_stories_before_insert_trg on public.ew_stories;
create trigger ew_stories_before_insert_trg before insert on public.ew_stories
  for each row execute function public.ew_stories_before_insert();

create or replace function public.ew_responses_before_insert()
returns trigger language plpgsql security definer set search_path = public as $$
declare v_minute integer; v_hour integer;
begin
  if auth.uid() is not null and new.author_id is distinct from auth.uid() then
    raise exception 'not_yours' using errcode = '42501';
  end if;
  new.created_at := now();
  new.report_count := 0;
  new.hidden := false;
  new.body := btrim(new.body);

  select count(*) filter (where created_at > now() - interval '1 minute'), count(*)
    into v_minute, v_hour
    from public.ew_responses
   where author_id = new.author_id and created_at > now() - interval '1 hour';
  if v_minute >= 6 or v_hour >= 60 then
    raise exception 'rate_limited' using errcode = 'P0001';
  end if;

  if exists (select 1 from public.ew_responses
              where author_id = new.author_id and story_id = new.story_id and body = new.body
                and created_at > now() - interval '10 minutes') then
    raise exception 'duplicate' using errcode = 'P0001';
  end if;
  return new;
end; $$;

drop trigger if exists ew_responses_before_insert_trg on public.ew_responses;
create trigger ew_responses_before_insert_trg before insert on public.ew_responses
  for each row execute function public.ew_responses_before_insert();

create or replace function public.ew_stamp_now()
returns trigger language plpgsql security invoker set search_path = public as $$
begin new.created_at := now(); return new; end; $$;

drop trigger if exists ew_likes_stamp on public.ew_likes;
create trigger ew_likes_stamp before insert on public.ew_likes
  for each row execute function public.ew_stamp_now();
drop trigger if exists ew_saves_stamp on public.ew_saves;
create trigger ew_saves_stamp before insert on public.ew_saves
  for each row execute function public.ew_stamp_now();

-- ─── ЖАЛОБЫ ───────────────────────────────────────────────────────────────

create or replace function public.ew_reports_before_insert()
returns trigger language plpgsql security definer set search_path = public as $$
declare v_author uuid; v_today integer;
begin
  if auth.uid() is not null and new.reporter_id is distinct from auth.uid() then
    raise exception 'not_yours' using errcode = '42501';
  end if;
  new.created_at := now();
  new.reason := left(btrim(new.reason), 200);

  if new.target_type = 'story' then
    select author_id into v_author from public.ew_stories
     where id = new.target_id and not hidden;
  else
    select r.author_id into v_author
      from public.ew_responses r join public.ew_stories s on s.id = r.story_id
     where r.id = new.target_id and not r.hidden and not s.hidden;
  end if;

  if v_author is null then
    raise exception 'target_not_found' using errcode = 'P0001';
  end if;
  if v_author = new.reporter_id then
    raise exception 'self_report' using errcode = 'P0001';
  end if;

  select count(*) into v_today from public.ew_reports
   where reporter_id = new.reporter_id and created_at > now() - interval '1 day';
  if v_today >= 20 then
    raise exception 'rate_limited' using errcode = 'P0001';
  end if;
  return new;
end; $$;

drop trigger if exists ew_reports_before_insert_trg on public.ew_reports;
create trigger ew_reports_before_insert_trg before insert on public.ew_reports
  for each row execute function public.ew_reports_before_insert();

-- Прячут запись только жалобы от аккаунтов старше суток. Аккаунт создаётся
-- за секунду и без всего, поэтому свежие жалобы записываются, но в счёт не
-- идут: иначе любой спрячет любую чужую историю, наделав трёх аккаунтов.
create or replace function public.ew_after_report()
returns trigger language plpgsql security definer set search_path = public as $$
declare v_trusted integer;
begin
  select count(*) into v_trusted
    from public.ew_reports r
    join public.ew_profiles p on p.id = r.reporter_id
   where r.target_type = new.target_type and r.target_id = new.target_id
     and p.created_at < now() - interval '24 hours'
     and not p.banned;

  if new.target_type = 'story' then
    update public.ew_stories
       set report_count = report_count + 1, hidden = hidden or v_trusted >= 3
     where id = new.target_id;
  else
    update public.ew_responses
       set report_count = report_count + 1, hidden = hidden or v_trusted >= 3
     where id = new.target_id;
  end if;
  return new;
end; $$;

-- ─── ПОЛИТИКИ ─────────────────────────────────────────────────────────────
-- auth.uid() обёрнут в select: так Postgres считает его один раз на запрос,
-- а не на каждую строку.

drop policy if exists ew_profiles_insert on public.ew_profiles;
create policy ew_profiles_insert on public.ew_profiles for insert to authenticated
  with check (id = (select auth.uid()));
drop policy if exists ew_profiles_update on public.ew_profiles;
create policy ew_profiles_update on public.ew_profiles for update to authenticated
  using (id = (select auth.uid())) with check (id = (select auth.uid()));

drop policy if exists ew_stories_read on public.ew_stories;
create policy ew_stories_read on public.ew_stories for select to authenticated
  using (not hidden or author_id = (select auth.uid()));
drop policy if exists ew_stories_insert on public.ew_stories;
create policy ew_stories_insert on public.ew_stories for insert to authenticated
  with check (author_id = (select auth.uid()) and public.ew_active());
drop policy if exists ew_stories_delete on public.ew_stories;
create policy ew_stories_delete on public.ew_stories for delete to authenticated
  using (author_id = (select auth.uid()));

-- Отклики забаненных не показываются, как и их истории.
drop policy if exists ew_responses_read on public.ew_responses;
create policy ew_responses_read on public.ew_responses for select to authenticated
  using ((not hidden
          and not exists (select 1 from public.ew_profiles p where p.id = author_id and p.banned))
         or author_id = (select auth.uid()));
-- Отвечать можно только туда, что видишь сам: скрытые чужие истории закрыты.
drop policy if exists ew_responses_insert on public.ew_responses;
create policy ew_responses_insert on public.ew_responses for insert to authenticated
  with check (author_id = (select auth.uid()) and public.ew_active()
              and exists (select 1 from public.ew_stories s where s.id = story_id));
drop policy if exists ew_responses_delete on public.ew_responses;
create policy ew_responses_delete on public.ew_responses for delete to authenticated
  using (author_id = (select auth.uid()));

-- «Я рядом» себе самому не ставится: смысла нет, а медаль «Услышали» иначе
-- набирается в одиночку.
drop policy if exists ew_likes_read on public.ew_likes;
create policy ew_likes_read on public.ew_likes for select to authenticated
  using (user_id = (select auth.uid()));
drop policy if exists ew_likes_insert on public.ew_likes;
create policy ew_likes_insert on public.ew_likes for insert to authenticated
  with check (user_id = (select auth.uid()) and public.ew_active()
              and exists (select 1 from public.ew_stories s
                           where s.id = story_id and s.author_id <> (select auth.uid())));
drop policy if exists ew_likes_delete on public.ew_likes;
create policy ew_likes_delete on public.ew_likes for delete to authenticated
  using (user_id = (select auth.uid()));

drop policy if exists ew_saves_own on public.ew_saves;
drop policy if exists ew_saves_read on public.ew_saves;
create policy ew_saves_read on public.ew_saves for select to authenticated
  using (user_id = (select auth.uid()));
drop policy if exists ew_saves_insert on public.ew_saves;
create policy ew_saves_insert on public.ew_saves for insert to authenticated
  with check (user_id = (select auth.uid())
              and exists (select 1 from public.ew_stories s where s.id = story_id));
drop policy if exists ew_saves_delete on public.ew_saves;
create policy ew_saves_delete on public.ew_saves for delete to authenticated
  using (user_id = (select auth.uid()));

drop policy if exists ew_reports_insert on public.ew_reports;
create policy ew_reports_insert on public.ew_reports for insert to authenticated
  with check (reporter_id = (select auth.uid()) and public.ew_active());

drop policy if exists ew_user_achievements_read on public.ew_user_achievements;
create policy ew_user_achievements_read on public.ew_user_achievements for select to authenticated
  using (user_id = (select auth.uid()));

-- ─── ЛЕНТА ────────────────────────────────────────────────────────────────
-- Автор видит и свою скрытую по жалобам историю — с пометкой. Пропавшая без
-- объяснения запись на площадке поддержки хуже, чем честное «её скрыли».
create or replace view public.ew_feed with (security_invoker = true) as
  select s.id, s.category, s.title, s.body, s.created_at,
         s.like_count, s.response_count, s.author_id,
         p.nickname as author_nickname,
         p.avatar_kind as author_avatar_kind,
         p.avatar_url as author_avatar_url,
         exists (select 1 from public.ew_likes l
                  where l.story_id = s.id and l.user_id = (select auth.uid())) as liked_by_me,
         exists (select 1 from public.ew_saves v
                  where v.story_id = s.id and v.user_id = (select auth.uid())) as saved_by_me,
         s.author_id = (select auth.uid()) as is_mine,
         s.hidden
    from public.ew_stories s
    join public.ew_profiles p on p.id = s.author_id
   where (not s.hidden or s.author_id = (select auth.uid())) and not p.banned;

revoke all on public.ew_feed from anon;
grant select on public.ew_feed to authenticated;

-- ─── НОВЫЕ ВЫЗОВЫ ─────────────────────────────────────────────────────────

-- Свой профиль целиком, включая серию дней, — только себе.
create or replace function public.ew_me()
returns json language sql stable security definer set search_path = public as $$
  select to_json(p) from (
    select id, nickname, avatar_kind, avatar_url, created_at, last_seen_on, streak_days, banned
      from public.ew_profiles where id = auth.uid()) p;
$$;

-- Архив одним запросом, в порядке сохранения.
create or replace function public.ew_saved()
returns setof public.ew_feed language sql stable security invoker set search_path = public as $$
  select f.* from public.ew_feed f
    join public.ew_saves v on v.story_id = f.id
   where v.user_id = (select auth.uid())
   order by v.created_at desc
   limit 200;
$$;

-- Удалить себя целиком: профиль со всем написанным и сам анонимный аккаунт.
-- Фото из хранилища приложение удаляет до вызова — SQL туда не пускают.
create or replace function public.ew_delete_me()
returns void language plpgsql security definer set search_path = public as $$
declare v_uid uuid := auth.uid();
begin
  if v_uid is null then
    raise exception 'not_signed_in' using errcode = '42501';
  end if;
  delete from public.ew_reports r
   where (r.target_type = 'story'
          and r.target_id in (select id from public.ew_stories where author_id = v_uid))
      or (r.target_type = 'response'
          and r.target_id in (select id from public.ew_responses where author_id = v_uid));
  delete from public.ew_profiles where id = v_uid;
  delete from auth.users where id = v_uid;
end; $$;

-- ─── ПРАВА НА ВЫЗОВ ──────────────────────────────────────────────────────

revoke all on function public.ew_profiles_guard()          from public, anon, authenticated;
revoke all on function public.ew_stories_before_insert()   from public, anon, authenticated;
revoke all on function public.ew_responses_before_insert() from public, anon, authenticated;
revoke all on function public.ew_reports_before_insert()   from public, anon, authenticated;
revoke all on function public.ew_after_report()            from public, anon, authenticated;
revoke all on function public.ew_stamp_now()               from public, anon, authenticated;

revoke all on function public.ew_me()        from public, anon;
grant execute on function public.ew_me()        to authenticated;
revoke all on function public.ew_saved()     from public, anon;
grant execute on function public.ew_saved()     to authenticated;
revoke all on function public.ew_delete_me() from public, anon;
grant execute on function public.ew_delete_me() to authenticated;

-- ─── ХРАНИЛИЩЕ ────────────────────────────────────────────────────────────
-- Публичная корзина отдаёт файлы по прямой ссылке и без политик. Политика
-- чтения нужна только API: перечислять чужие папки (а значит, id всех, кто
-- ставил фото) незачем. Писать — ровно один файл avatar.* в свою папку.

drop policy if exists ew_avatars_read on storage.objects;
create policy ew_avatars_read on storage.objects for select to authenticated
  using (bucket_id = 'ew-avatars' and (storage.foldername(name))[1] = (select auth.uid())::text);
drop policy if exists ew_avatars_write on storage.objects;
create policy ew_avatars_write on storage.objects for insert to authenticated
  with check (bucket_id = 'ew-avatars'
              and name ~ ('^' || (select auth.uid())::text || '/avatar\.(jpg|jpeg|png|webp)$'));
drop policy if exists ew_avatars_update on storage.objects;
create policy ew_avatars_update on storage.objects for update to authenticated
  using (bucket_id = 'ew-avatars' and (storage.foldername(name))[1] = (select auth.uid())::text)
  with check (bucket_id = 'ew-avatars'
              and name ~ ('^' || (select auth.uid())::text || '/avatar\.(jpg|jpeg|png|webp)$'));
drop policy if exists ew_avatars_delete on storage.objects;
create policy ew_avatars_delete on storage.objects for delete to authenticated
  using (bucket_id = 'ew-avatars' and (storage.foldername(name))[1] = (select auth.uid())::text);
