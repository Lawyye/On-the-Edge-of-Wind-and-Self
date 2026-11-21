// Поведение хранилища при запуске: кэш, обрыв сети, протухшая сессия.
import 'dart:async';

import 'package:edgewind/data/edgewind_store.dart';
import 'package:edgewind/data/local_cache.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

import 'support/fake_backend.dart';
import 'support/sample.dart';

class _Flaky extends FakeBackend {
  _Flaky(this.error);
  final Object error;
  @override
  Future<Map<String, dynamic>?> touch() async => throw error;
}

void main() {
  setUp(() => SharedPreferences.setMockInitialValues({}));

  test('первый запуск без сессии — сразу знакомство, без сети', () async {
    final api = FakeBackend(session: false);
    final store = EdgewindStore(backend: api, cache: LocalCache());
    await store.boot();
    expect(store.phase, Phase.signup);
    expect(api.calls, isEmpty);
  });

  test('сессия есть, профиля нет — знакомство', () async {
    final store = EdgewindStore(backend: FakeBackend(), cache: LocalCache());
    await store.boot();
    expect(store.phase, Phase.signup);
  });

  test('сессия есть, профиль есть — внутрь', () async {
    final api = FakeBackend()..profile = me.toRow();
    final store = EdgewindStore(backend: api, cache: LocalCache());
    await store.boot();
    expect(store.phase, Phase.ready);
    expect(store.profile?.nickname, me.nickname);
  });

  test('нет сети и нет кэша — экран «связи нет»', () async {
    final store = EdgewindStore(backend: _Flaky(TimeoutException('x')), cache: LocalCache());
    await store.boot();
    expect(store.phase, Phase.offline);
    expect(store.bootProblem, isNotNull);
  });

  test('нет сети, но есть кэш — открывается сохранённое', () async {
    final cache = LocalCache();
    await cache.writeSnapshot(CacheSnapshot(
      userId: 'me',
      profile: me.toRow(),
      feed: stories.map((s) => s.toRow()).toList(),
    ));
    final store = EdgewindStore(backend: _Flaky(TimeoutException('x')), cache: cache);
    await store.boot();
    expect(store.phase, Phase.ready);
    expect(store.offline, isTrue);
    expect(store.feed.length, stories.length);
  });

  test('чужой кэш (другой аккаунт) не показывается', () async {
    final cache = LocalCache();
    await cache.writeSnapshot(CacheSnapshot(userId: 'someone-else', profile: me.toRow()));
    final store = EdgewindStore(backend: _Flaky(TimeoutException('x')), cache: cache);
    await store.boot();
    expect(store.phase, Phase.offline);
  });

  test('сервер отказал в доступе — сессия сбрасывается, знакомство заново', () async {
    final api = _Flaky(const PostgrestException(message: 'permission denied for function ew_touch', code: '42501'));
    final store = EdgewindStore(backend: api, cache: LocalCache());
    await store.boot();
    expect(store.phase, Phase.signup);
    expect(api.session, isFalse);
  });

  test('ошибки базы — человеческими словами', () {
    final store = EdgewindStore(backend: FakeBackend(), cache: LocalCache());
    String d(String code, String m) => store.describe(PostgrestException(message: m, code: code));
    expect(d('23505', 'duplicate key value violates unique constraint "ew_profiles_nickname_key"'), contains('занято'));
    expect(d('P0001', 'rate_limited'), contains('Слишком часто'));
    expect(d('P0001', 'duplicate'), contains('уже отправлено'));
    expect(d('P0001', 'self_report'), contains('своё'));
    expect(d('P0001', 'nickname_reserved'), contains('служебное'));
    expect(d('23514', 'new row violates check constraint "ew_profiles_nickname_clean"'), contains('невидимые'));
  });
}
