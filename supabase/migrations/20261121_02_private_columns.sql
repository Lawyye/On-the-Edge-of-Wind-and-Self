-- Вторая часть защиты: чужие служебные поля больше не читаются.
--
-- До неё любой вошедший видел у любого профиля, в какой день тот последний
-- раз заходил (last_seen_on), сколько дней подряд (streak_days) и когда
-- зарегистрировался (created_at), а у историй и откликов — сколько на них
-- пожаловались (report_count). Для анонимной площадки, куда приходят
-- подростки, расписание чужих заходов — уже слежка.
--
-- Новая сборка приложения просит колонки списком и свой профиль целиком
-- берёт через ew_me(). Старая сборка, которая делает select=*, после этой
-- миграции перестанет загружать профиль — её нужно обновить.

begin;

revoke select on public.ew_profiles from anon, authenticated;
grant select (id, nickname, avatar_kind, avatar_url, banned)
  on public.ew_profiles to authenticated;

revoke select on public.ew_stories from anon, authenticated;
grant select (id, author_id, category, title, body, created_at, like_count, response_count, hidden)
  on public.ew_stories to authenticated;

revoke select on public.ew_responses from anon, authenticated;
grant select (id, story_id, author_id, body, created_at, hidden)
  on public.ew_responses to authenticated;

-- Архив читается по человеку и в порядке сохранения (ew_saved).
create index if not exists ew_saves_user_idx on public.ew_saves (user_id, created_at desc);

commit;
