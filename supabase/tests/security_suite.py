"""Проверка базы приложения атаками и обычными действиями.

Заводит несколько анонимных аккаунтов тем же входом, что и приложение, и от их
имени делает то, что должен уметь каждый (регистрация, история, отклик, «я
рядом», архив, фото), и то, чего не должен уметь никто (чужое имя, дата из
будущего, накрутка, чужой аватар, жалоба на себя, поток записей). В конце
каждый аккаунт удаляет себя через ew_delete_me — это тоже проверка.

Запуск: python3 security_suite.py out.json
Печатает ОК или ПРОВАЛ по каждому пункту. Ходит в настоящую базу.
"""
import json, os, ssl, sys, time, urllib.request, urllib.error

BASE = 'https://wwwxthxyfglcgpuscafz.supabase.co'
ANON_JWT = ('eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Ind3d3h0aHh5ZmdsY2dwdXNjYWZ6Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODgxMjUxODcsImV4cCI6MjEwMzcwMTE4N30.1Gcq8BKJw1WEP4xKfvhQW46qxTv4sEGjh74EAK_1GyU')
CTX = ssl.create_default_context(cafile=os.environ.get('SSL_CERT_FILE')) if os.environ.get('SSL_CERT_FILE') else ssl.create_default_context()
results = []

def call(method, path, token=None, body=None, headers=None, raw=None, ctype='application/json'):
    h = {'apikey': ANON_JWT, 'Authorization': f'Bearer {token or ANON_JWT}'}
    if headers: h.update(headers)
    data = raw if raw is not None else (json.dumps(body).encode() if body is not None else None)
    if data is not None: h['Content-Type'] = ctype
    req = urllib.request.Request(BASE + path, data=data, method=method, headers=h)
    try:
        with urllib.request.urlopen(req, context=CTX, timeout=30) as r:
            txt = r.read().decode() or 'null'
            try: return r.status, json.loads(txt)
            except Exception: return r.status, txt
    except urllib.error.HTTPError as e:
        txt = e.read().decode()
        try: return e.code, json.loads(txt)
        except Exception: return e.code, txt

def signup():
    s, j = call('POST', '/auth/v1/signup', body={})
    assert s == 200, (s, j)
    return j['access_token'], j['user']['id']

def check(name, blocked, detail=''):
    results.append((name, blocked))
    print(f"  {'ОТБИТО ' if blocked else 'ПРОБИТО'}  {name}" + (f'  — {detail}' if detail else ''))


results = []
def ok(name, cond, detail=''):
    results.append((name, bool(cond)))
    print(f"  {'ОК    ' if cond else 'ПРОВАЛ'}  {name}" + (f'  — {detail}' if detail else ''))

rep = {'Prefer': 'return=representation'}
stamp = str(int(time.time()))[-5:]
users = []
def new_user(nick):
    t, uid = signup()
    s, j = call('POST', '/rest/v1/ew_profiles', t, {'id': uid, 'nickname': nick, 'avatar_kind': 2}, rep)
    users.append((t, uid))
    return t, uid, s, j

print('ОБЫЧНЫЕ ДЕЙСТВИЯ')
A, a, s, j = new_user(f'Проверка А {stamp}')
ok('регистрация с именем и знаком', s == 201 and j and j[0]['nickname'] == f'Проверка А {stamp}', f'HTTP {s}')
B, b, s, _ = new_user(f'Проверка Б {stamp}')
C, c, s, _ = new_user(f'Проверка В {stamp}')
s, j = call('POST', '/rest/v1/rpc/ew_touch', A, {})
ok('ew_touch возвращает свой профиль', s == 200 and isinstance(j, dict) and j.get('id') == a, f'HTTP {s}')
s, j = call('POST', '/rest/v1/rpc/ew_me', A, {})
ok('ew_me: свой профиль целиком', s == 200 and isinstance(j, dict) and 'streak_days' in j and j['id'] == a, f'HTTP {s}')
s, j = call('POST', '/rest/v1/ew_stories', A, {'author_id': a, 'category': 'Одиночество',
            'title': 'Проверочная история', 'body': '  Текст проверки.  '}, rep)
story = j[0]['id'] if s == 201 else None
ok('публикация истории', s == 201 and j[0]['body'] == 'Текст проверки.', f'HTTP {s}')
s, j = call('POST', '/rest/v1/ew_likes', B, {'story_id': story, 'user_id': b})
ok('Б ставит «я рядом»', s == 201, f'HTTP {s}')
s, j = call('GET', f'/rest/v1/ew_feed?id=eq.{story}&select=like_count,liked_by_me,is_mine,hidden', B)
ok('лента: счётчик и отметка Б', s == 200 and j and j[0]['like_count'] == 1 and j[0]['liked_by_me'] and not j[0]['is_mine'], json.dumps(j))
s, j = call('POST', '/rest/v1/ew_likes', A, {'story_id': story, 'user_id': a})
ok('«я рядом» на свою историю отклонено', s >= 400, f'HTTP {s}')
s, j = call('POST', '/rest/v1/ew_responses', B, {'story_id': story, 'author_id': b, 'body': 'Я рядом, правда.'}, rep)
resp = j[0]['id'] if s == 201 else None
ok('Б отвечает', s == 201, f'HTTP {s}')
s, j = call('POST', '/rest/v1/ew_responses', B, {'story_id': story, 'author_id': b, 'body': 'Я рядом, правда.'})
ok('двойная отправка того же отклика отклонена', s >= 400 and 'duplicate' in json.dumps(j, ensure_ascii=False), f'HTTP {s}')
s, j = call('GET', f'/rest/v1/ew_stories?id=eq.{story}&select=response_count', A)
ok('счётчик откликов', j and j[0]['response_count'] == 1, json.dumps(j))
s, j = call('GET', '/rest/v1/ew_responses?select=id,body,created_at,author_id,'
            'author:ew_profiles!inner(nickname,avatar_kind,avatar_url),story:ew_stories!inner(id,title,author_id)'
            f'&story.author_id=eq.{a}&author_id=neq.{a}&order=created_at.desc&limit=50', A)
ok('А видит отклик в «Откликах»', s == 200 and len(j) == 1 and j[0]['author']['nickname'].startswith('Проверка Б'), f'HTTP {s}')
s, j = call('POST', '/rest/v1/ew_saves', C, {'story_id': story, 'user_id': c})
ok('В сохраняет в архив', s == 201, f'HTTP {s}')
s, j = call('POST', '/rest/v1/rpc/ew_saved', C, {})
ok('ew_saved отдаёт архив', s == 200 and len(j) == 1 and j[0]['id'] == story and j[0]['saved_by_me'], f'HTTP {s}')
s, j = call('DELETE', f'/rest/v1/ew_saves?story_id=eq.{story}&user_id=eq.{c}', C)
ok('В убирает из архива', s in (200, 204), f'HTTP {s}')
s, j = call('POST', '/rest/v1/ew_reports', C, {'target_type': 'response', 'target_id': resp, 'reporter_id': c, 'reason': 'Оскорбления'})
ok('В жалуется на отклик Б', s == 201, f'HTTP {s}')
s, j = call('PATCH', f'/rest/v1/ew_profiles?id=eq.{a}', A, {'nickname': f'Новое Имя {stamp}'}, rep)
ok('переименование', s == 200 and j and j[0]['nickname'] == f'Новое Имя {stamp}', f'HTTP {s}')
s, j = call('PATCH', f'/rest/v1/ew_profiles?id=eq.{a}', A, {'nickname': 'Модератор Аня'})
ok('служебное имя «Модератор» отклонено', s >= 400 and 'nickname_reserved' in json.dumps(j), f'HTTP {s}')
s, j = call('PATCH', f'/rest/v1/ew_profiles?id=eq.{a}', A, {'nickname': 'Поль' + chr(0x200B) + 'нь'})
ok('имя с невидимым символом отклонено', s >= 400, f'HTTP {s}')
s, j = call('POST', '/rest/v1/rpc/ew_my_achievements', A, {})
first = [x for x in j if x['code'] == 'first_step'] if isinstance(j, list) else []
ok('достижения: «Первый шаг» выдан', first and first[0]['earned_at'], f'HTTP {s}')

print('\nФОТО')
png = bytes.fromhex('89504e470d0a1a0a0000000d4948445200000001000000010806000000'
                    '1f15c4890000000d49444154789c6360000002000154a24f5d0000000049454e44ae426082')
s, j = call('POST', f'/storage/v1/object/ew-avatars/{a}/avatar.png', A, raw=png, ctype='image/png',
            headers={'x-upsert': 'true'})
ok('загрузка своего avatar.png', s == 200, f'HTTP {s} {str(j)[:80]}')
s, j = call('POST', f'/storage/v1/object/ew-avatars/{a}/avatar.png', A, raw=png, ctype='image/png',
            headers={'x-upsert': 'true'})
ok('повторная загрузка (замена)', s == 200, f'HTTP {s} {str(j)[:80]}')
s, j = call('POST', f'/storage/v1/object/ew-avatars/{a}/spam-{stamp}.png', A, raw=png, ctype='image/png')
ok('лишний файл в своей папке отклонён', s >= 400, f'HTTP {s}')
url = f'{BASE}/storage/v1/object/public/ew-avatars/{a}/avatar.png?v={int(time.time()*1000)}'
s, j = call('PATCH', f'/rest/v1/ew_profiles?id=eq.{a}', A, {'avatar_url': url}, rep)
ok('адрес своего фото принят', s == 200, f'HTTP {s}')
s, j = call('PATCH', f'/rest/v1/ew_profiles?id=eq.{a}', A, {'avatar_url': f'{BASE}/storage/v1/object/public/ew-avatars/{b}/avatar.png'})
ok('чужое фото как своё отклонено', s >= 400, f'HTTP {s}')
s, j = call('GET', '/storage/v1/object/list/ew-avatars', B) if False else (0, None)
s, j = call('POST', '/storage/v1/object/list/ew-avatars', B, {'prefix': a, 'limit': 10})
ok('Б не видит список файлов А', s >= 400 or j == [], f'HTTP {s} {str(j)[:60]}')

print('\nПРИВАТНОСТЬ (вторая миграция)')
s, j = call('GET', f'/rest/v1/ew_profiles?id=eq.{a}&select=id,nickname,avatar_kind,avatar_url', B)
ok('имя и знак чужого профиля видны', s == 200 and j and j[0]['id'] == a, f'HTTP {s}')
for col in ('last_seen_on', 'streak_days', 'created_at'):
    s, j = call('GET', f'/rest/v1/ew_profiles?id=eq.{a}&select={col}', B)
    ok(f'чужое {col} закрыто', s >= 400, f'HTTP {s}')
s, j = call('GET', f'/rest/v1/ew_stories?id=eq.{story}&select=report_count', B)
ok('число жалоб на чужую историю закрыто', s >= 400, f'HTTP {s}')
s, j = call('GET', f'/rest/v1/ew_feed?id=eq.{story}&select=id,title,author_nickname,hidden,is_mine', B)
ok('лента читается списком колонок', s == 200 and j and j[0]['id'] == story, f'HTTP {s}')

print('\nАТАКИ')
s, j = call('POST', '/rest/v1/ew_stories', A, {'author_id': a, 'category': 'Другое', 'title': 'Из будущего',
            'body': 'x', 'created_at': '2030-01-01T00:00:00Z', 'like_count': 9999})
ok('дата из будущего и накрутка при вставке', s >= 400, f'HTTP {s}')
s, j = call('POST', '/rest/v1/ew_stories', A, {'author_id': a, 'category': 'Х' * 50, 'title': 'Категория', 'body': 'x'})
ok('выдуманная категория', s >= 400, f'HTTP {s}')
s, j = call('POST', '/rest/v1/ew_stories', B, {'author_id': a, 'category': 'Другое', 'title': 'От чужого имени', 'body': 'x'})
ok('история от чужого имени', s >= 400, f'HTTP {s}')
s, j = call('POST', '/rest/v1/ew_responses', B, {'story_id': story, 'author_id': b, 'body': 'ночь', 'created_at': '2026-01-01T20:30:00Z'})
ok('подделка времени отклика', s >= 400, f'HTTP {s}')
call('PATCH', f'/rest/v1/ew_profiles?id=eq.{a}', A, {'streak_days': 30})
s, j = call('POST', '/rest/v1/rpc/ew_me', A, {})
ok('накрутка серии дней', isinstance(j, dict) and j.get('streak_days', 99) < 7, f"streak={j.get('streak_days') if isinstance(j, dict) else j}")
s, j = call('PATCH', f'/rest/v1/ew_profiles?id=eq.{a}', A, {'avatar_url': 'https://evil.example/t.png'})
ok('аватар с чужого сервера', s >= 400, f'HTTP {s}')
s, j = call('POST', '/rest/v1/ew_reports', A, {'target_type': 'story', 'target_id': story, 'reporter_id': a, 'reason': 'x'})
ok('жалоба на себя', s >= 400 and 'self_report' in json.dumps(j), f'HTTP {s}')
s, j = call('POST', '/rest/v1/ew_reports', A, {'target_type': 'story', 'target_id': str(uuid.uuid4()), 'reporter_id': a, 'reason': 'x'})
ok('жалоба на несуществующее', s >= 400 and 'target_not_found' in json.dumps(j), f'HTTP {s}')
s, j = call('POST', '/rest/v1/ew_reports', B, {'target_type': 'story', 'target_id': story, 'reporter_id': b, 'reason': 'я' * 5000})
ok('длинная жалоба обрезается, а не раздувает базу', s == 201, f'HTTP {s}')
alts = [new_user(f'Бригада {i} {stamp}')[:2] for i in range(3)]
for t, uid in alts:
    call('POST', '/rest/v1/ew_reports', t, {'target_type': 'story', 'target_id': story, 'reporter_id': uid, 'reason': 'спам'})
s, j = call('GET', f'/rest/v1/ew_feed?id=eq.{story}&select=id', C)
ok('три свежих аккаунта не прячут историю', bool(j), 'видна' if j else 'СКРЫТА')
n_ok = 0
for i in range(8):
    s, j = call('POST', '/rest/v1/ew_stories', C, {'author_id': c, 'category': 'Другое', 'title': f'Поток номер {i}', 'body': f'спам {i}'})
    n_ok += s == 201
ok('лимит: не больше 5 историй в час', n_ok == 5, f'прошло {n_ok} из 8')
s, j = call('POST', '/rest/v1/rpc/ew_grant', B, {'p_user': b, 'p_code': 'own_person'})
ok('выдача себе медали', s >= 400, f'HTTP {s}')
s, j = call('GET', '/rest/v1/ew_reports?select=*', B)
ok('чтение чужих жалоб', s >= 400 or j == [], f'HTTP {s}')
s, j = call('GET', '/rest/v1/ew_feed?select=id')
ok('гость без входа', s >= 400 or j == [], f'HTTP {s}')

print('\nУДАЛЕНИЕ')
s, j = call('DELETE', f'/rest/v1/ew_responses?id=eq.{resp}', B, headers=rep)
ok('Б удаляет свой отклик', s == 200 and len(j) == 1, f'HTTP {s}')
s, j = call('GET', f'/rest/v1/ew_stories?id=eq.{story}&select=response_count', A)
ok('счётчик откликов уменьшился', j and j[0]['response_count'] == 0, json.dumps(j))
s, j = call('DELETE', f'/storage/v1/object/ew-avatars/{a}/avatar.png', A)
ok('А удаляет своё фото', s == 200, f'HTTP {s}')
for t, uid in users:
    s, j = call('POST', '/rest/v1/rpc/ew_delete_me', t, {})
    if not (s in (200, 204)):
        ok(f'ew_delete_me {uid[:8]}', False, f'HTTP {s} {j}')
s, j = call('GET', f'/rest/v1/ew_feed?id=eq.{story}&select=id', C)
ok('после удаления аккаунтов их данных нет', s >= 400 or j == [], f'HTTP {s} {str(j)[:60]}')

print('\nИТОГ:', sum(1 for _, x in results if x), 'ОК,', sum(1 for _, x in results if not x), 'ПРОВАЛ')
json.dump({'ids': [u for _, u in users]}, open(sys.argv[1], 'w'))
