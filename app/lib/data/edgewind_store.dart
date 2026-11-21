import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

import '../domain/models.dart';
import 'backend.dart';
import 'local_cache.dart';

/// Ошибка, которую не стыдно показать человеку целиком.
class EdgewindException implements Exception {
  const EdgewindException(this.message);
  final String message;
  @override
  String toString() => message;
}

/// Сообщение внизу экрана. id растёт, чтобы одинаковый текст дважды подряд
/// всё равно показался дважды.
class Notice {
  const Notice(this.id, this.text, {this.error = false});
  final int id;
  final String text;
  final bool error;
}

enum Phase {
  /// Первый запуск без кэша: ждём сервер.
  booting,

  /// Сервер не ответил, а показать нечего.
  offline,

  /// Сессия есть, имени нет — знакомство.
  signup,

  /// Можно жить.
  ready,
}

/// Единственный источник данных приложения.
///
/// Вход анонимный: ни почты, ни телефона, ни пароля. Имя и знак человек
/// придумывает сам. Профиль и лента кэшируются на телефоне, поэтому при
/// повторном запуске экран появляется сразу, а свежие данные догружаются.
class EdgewindStore extends ChangeNotifier {
  EdgewindStore({Backend? backend, LocalCache? cache})
    : _api = backend ?? SupabaseBackend(),
      _cache = cache ?? LocalCache();

  final Backend _api;
  final LocalCache _cache;

  static const String kAll = 'Все';
  static const int _pageSize = 40;

  Phase _phase = Phase.booting;
  String? _bootProblem;
  Profile? _profile;
  List<Story> _feed = const [];
  bool _feedLoading = false;
  bool _feedLoaded = false;
  bool _feedEnd = false;
  List<Story> _mine = const [];
  List<Story> _saved = const [];
  List<InboxItem> _inbox = const [];
  List<Achievement> _achievements = const [];
  final Map<String, Story> _extra = {};
  List<Story> _lastAllFeed = const [];
  String _category = kAll;
  DateTime? _inboxSeen;
  DateTime? _lastRefresh;
  DateTime? _lastTouchDay;
  bool _offline = false;
  bool _disposed = false;
  final Set<String> _likeBusy = {};
  final Set<String> _saveBusy = {};
  int _noticeId = 0;

  /// Последнее сообщение для человека. Слушает его главный экран.
  final ValueNotifier<Notice?> notice = ValueNotifier(null);

  Phase get phase => _phase;
  String? get bootProblem => _bootProblem;
  Profile? get profile => _profile;
  String? get userId => _api.userId;
  bool get offline => _offline;
  String get category => _category;
  List<Story> get feed => _feed;
  bool get feedLoading => _feedLoading;
  bool get feedLoaded => _feedLoaded;
  bool get feedEnd => _feedEnd;
  List<Story> get myStories => _mine;
  List<Story> get savedStories => _saved;
  List<InboxItem> get inbox => _inbox;
  List<Achievement> get achievements => _achievements;
  int get earnedCount => _achievements.where((a) => a.earned).length;

  int get unreadCount {
    final seen = _inboxSeen;
    if (seen == null) return _inbox.length;
    return _inbox.where((i) => i.createdAt.isAfter(seen)).length;
  }

  bool isUnread(InboxItem item) => _inboxSeen == null || item.createdAt.isAfter(_inboxSeen!);

  @override
  void dispose() {
    _disposed = true;
    notice.dispose();
    super.dispose();
  }

  void _changed() {
    if (!_disposed) notifyListeners();
  }

  void say(String text, {bool error = false}) {
    if (_disposed) return;
    notice.value = Notice(++_noticeId, text, error: error);
  }

  // ── запуск ──────────────────────────────────────────────────────────────

  /// Запуск: показать кэш сразу, потом спросить сервер.
  Future<void> boot() async {
    _bootProblem = null;
    if (_phase == Phase.offline) {
      _phase = Phase.booting;
      _changed();
    }

    bool hasSession;
    try {
      hasSession = _api.hasSession;
    } catch (e) {
      _bootProblem = describe(e);
      _phase = Phase.offline;
      _changed();
      return;
    }
    if (!hasSession) {
      // Первый запуск: знакомство не требует сети, а анонимный аккаунт
      // заводится только когда человек выбрал имя — без брошенных пустышек.
      _phase = Phase.signup;
      _changed();
      return;
    }

    final snap = await _cache.readSnapshot();
    final cachedUid = _api.userId;
    if (_profile == null && snap != null && snap.userId == cachedUid && snap.profile != null) {
      _profile = Profile.fromRow(snap.profile!);
      _feed = snap.feed.map(Story.fromRow).toList();
      _lastAllFeed = _feed;
      _phase = Phase.ready;
      _changed();
    }

    try {
      await _api.ensureSession();
      final row = await _api.touch();
      _lastTouchDay = _today();
      if (row == null) {
        _profile = null;
        _phase = Phase.signup;
        await _cache.clearSnapshot();
        _changed();
        return;
      }
      _profile = Profile.fromRow(row);
      _offline = false;
      _phase = Phase.ready;
      final uid = _api.userId;
      if (uid != null) _inboxSeen = await _cache.inboxSeen(uid);
      _changed();
      unawaited(refreshAll());
    } catch (e) {
      if (_isDeadSession(e)) {
        await _restartSession();
        return;
      }
      if (_phase == Phase.ready) {
        _offline = true;
        say('Нет связи — показываю то, что было сохранено.');
      } else {
        _bootProblem = describe(e);
        _phase = Phase.offline;
      }
      _changed();
    }
  }

  /// Сессия на телефоне есть, а сервер её больше не знает (аккаунт удалён,
  /// вход отозван). Начинаем заново, с новой анонимной сессией.
  Future<void> _restartSession() async {
    await _api.signOut();
    await _cache.clearSnapshot();
    _clearData();
    _phase = Phase.signup;
    _changed();
  }

  void _clearData() {
    _profile = null;
    _feed = const [];
    _mine = const [];
    _saved = const [];
    _inbox = const [];
    _achievements = const [];
    _extra.clear();
    _lastAllFeed = const [];
    _feedLoaded = false;
    _feedEnd = false;
    _inboxSeen = null;
  }

  /// Возвращение в приложение. Серия дней считается по датам, поэтому
  /// после полуночи заход отмечается заново, даже если приложение не
  /// выгружалось из памяти.
  Future<void> resumed() async {
    if (_phase != Phase.ready) {
      if (_phase == Phase.offline) await boot();
      return;
    }
    final now = DateTime.now();
    if (_lastTouchDay != _today()) {
      try {
        final row = await _api.touch();
        _lastTouchDay = _today();
        if (row != null) {
          _profile = Profile.fromRow(row);
          _changed();
        }
      } catch (_) {}
    }
    if (_lastRefresh == null || now.difference(_lastRefresh!) > const Duration(seconds: 45)) {
      await refreshAll(quiet: true);
    }
  }

  DateTime _today() {
    final n = DateTime.now();
    return DateTime(n.year, n.month, n.day);
  }

  /// Всё сразу и параллельно. Одна ошибка не роняет остальное.
  Future<void> refreshAll({bool quiet = false}) async {
    _lastRefresh = DateTime.now();
    final results = await Future.wait([
      _try(refreshFeed),
      _try(_refreshMine),
      _try(refreshSaved),
      _try(refreshInbox),
      _try(refreshAchievements),
    ]);
    final failure = results.whereType<Object>().firstOrNull;
    if (failure != null) {
      if (_isDeadSession(failure)) {
        await _restartSession();
        return;
      }
      _offline = _isNetwork(failure);
      if (!quiet) say(describe(failure), error: true);
    } else {
      _offline = false;
    }
    _changed();
    unawaited(_saveSnapshot());
  }

  Future<Object?> _try(Future<void> Function() job) async {
    try {
      await job();
      return null;
    } catch (e) {
      return e;
    }
  }

  Future<void> _saveSnapshot() async {
    final uid = _api.userId;
    final p = _profile;
    if (uid == null || p == null) return;
    await _cache.writeSnapshot(CacheSnapshot(
      userId: uid,
      profile: p.toRow(),
      feed: _lastAllFeed.take(30).map((s) => s.toRow()).toList(),
    ));
  }

  // ── знакомство и профиль ────────────────────────────────────────────────

  /// Занимает имя и создаёт профиль. Фото, если выбрано, загружается уже
  /// после входа: человек не ждёт, пока оно долетит.
  Future<void> createProfile({required String nickname, required int avatarKind, Uint8List? photo}) async {
    final name = nickname.trim();
    await _api.ensureSession();
    await _api.createProfile(nickname: name, avatarKind: avatarKind);
    final row = await _api.me();
    if (row == null) throw const EdgewindException('Профиль не создался. Попробуй ещё раз.');
    _profile = Profile.fromRow(row);
    _phase = Phase.ready;
    _lastTouchDay = _today();
    final uid = _api.userId;
    if (uid != null) _inboxSeen = await _cache.inboxSeen(uid);
    _changed();
    unawaited(refreshAll());
    if (photo != null) {
      unawaited(setPhoto(photo).catchError((Object e) {
        say('Фото не загрузилось: ${describe(e)} Его можно поставить в профиле.', error: true);
      }));
    }
  }

  Future<void> rename(String nickname) async {
    await _api.updateProfile({'nickname': nickname.trim()});
    await _reloadMe();
    _patchMyAuthorInfo();
  }

  Future<void> setGlyph(int kind) async {
    await _api.updateProfile({'avatar_kind': kind, 'avatar_url': null});
    try {
      await _api.removeAvatarFiles();
    } catch (_) {}
    await _reloadMe();
    _patchMyAuthorInfo();
  }

  /// Фото проверяется по первым байтам, а не по имени файла: тип, который
  /// объявит телефон, и настоящий формат часто не совпадают.
  Future<void> setPhoto(Uint8List bytes) async {
    final kind = imageKind(bytes);
    if (kind == null) {
      throw const EdgewindException('Это фото не подходит. Нужен JPG, PNG или WEBP.');
    }
    if (bytes.lengthInBytes > 2 * 1024 * 1024) {
      throw const EdgewindException('Фото тяжелее 2 МБ. Выбери другое.');
    }
    final url = await _api.uploadAvatar(bytes, extension: kind.$1, contentType: kind.$2);
    await _api.updateProfile({'avatar_url': url});
    await _reloadMe();
    _patchMyAuthorInfo();
  }

  Future<void> _reloadMe() async {
    final row = await _api.me();
    if (row != null) _profile = Profile.fromRow(row);
    _changed();
    unawaited(_saveSnapshot());
  }

  void _patchMyAuthorInfo() {
    final p = _profile;
    if (p == null) return;
    Story fix(Story s) => s.isMine
        ? s.copyWith(
            authorNickname: p.nickname,
            authorAvatarKind: p.avatarKind,
            authorAvatarUrl: p.avatarUrl,
            clearAvatarUrl: p.avatarUrl == null,
          )
        : s;
    _feed = _feed.map(fix).toList();
    _mine = _mine.map(fix).toList();
    _saved = _saved.map(fix).toList();
    _extra.updateAll((_, s) => fix(s));
    _changed();
  }

  /// Удаляет всё: фото, профиль со всеми историями, откликами и отметками,
  /// и сам анонимный вход. Дальше — знакомство с чистого листа.
  Future<void> deleteAccount() async {
    await _api.deleteAccount();
    await _cache.clearSnapshot();
    _clearData();
    _phase = Phase.signup;
    _changed();
  }

  // ── лента ───────────────────────────────────────────────────────────────

  void setCategory(String value) {
    if (_category == value) return;
    _category = value;
    _feed = const [];
    _feedLoaded = false;
    _feedEnd = false;
    _changed();
    unawaited(_try(refreshFeed).then((e) {
      if (e != null) say(describe(e), error: true);
    }));
  }

  Future<void> refreshFeed() async {
    final requested = _category;
    _feedLoading = true;
    _changed();
    try {
      final rows = await _api.feed(category: requested == kAll ? null : requested, limit: _pageSize);
      if (requested != _category) return; // пока ждали, тему сменили
      _feed = rows.map(Story.fromRow).toList();
      if (requested == kAll) _lastAllFeed = _feed;
      _feedEnd = rows.length < _pageSize;
      _feedLoaded = true;
    } finally {
      _feedLoading = false;
      _changed();
    }
  }

  /// Следующая страница ленты — когда до конца списка осталось немного.
  Future<void> loadMore() async {
    if (_feedLoading || _feedEnd || _feed.isEmpty) return;
    final requested = _category;
    _feedLoading = true;
    _changed();
    try {
      final rows = await _api.feed(
        category: requested == kAll ? null : requested,
        before: _feed.last.createdAt,
        limit: _pageSize,
      );
      if (requested != _category) return;
      final known = _feed.map((s) => s.id).toSet();
      _feed = [..._feed, ...rows.map(Story.fromRow).where((s) => !known.contains(s.id))];
      _feedEnd = rows.length < _pageSize;
    } catch (e) {
      say(describe(e), error: true);
    } finally {
      _feedLoading = false;
      _changed();
    }
  }

  Future<void> _refreshMine() async {
    _mine = (await _api.myStories()).map(Story.fromRow).toList();
    _changed();
  }

  Future<void> refreshSaved() async {
    _saved = (await _api.saved()).map(Story.fromRow).toList();
    _changed();
  }

  Future<void> refreshAchievements() async {
    _achievements = (await _api.achievements()).map(Achievement.fromRow).toList();
    _changed();
  }

  /// Отклики на мои истории. Свои отфильтрованы: сообщать себе о себе незачем.
  Future<void> refreshInbox() async {
    _inbox = (await _api.inbox()).map(InboxItem.fromRow).toList();
    _changed();
  }

  /// Отклики прочитаны — отметка живёт на телефоне.
  Future<void> markInboxSeen() async {
    if (_inbox.isEmpty) return;
    final newest = _inbox.map((i) => i.createdAt).reduce((a, b) => a.isAfter(b) ? a : b);
    if (_inboxSeen != null && !newest.isAfter(_inboxSeen!)) return;
    _inboxSeen = newest;
    _changed();
    final uid = _api.userId;
    if (uid != null) await _cache.setInboxSeen(uid, newest);
  }

  Story? storyById(String id) {
    for (final list in [_feed, _mine, _saved]) {
      for (final s in list) {
        if (s.id == id) return s;
      }
    }
    return _extra[id];
  }

  /// История, которой нет в загруженных списках, — например, открытая из
  /// откликов. Null — если её удалили или скрыли.
  Future<Story?> fetchStory(String id) async {
    final known = storyById(id);
    if (known != null) return known;
    final row = await _api.story(id);
    if (row == null) return null;
    final s = Story.fromRow(row);
    _extra[id] = s;
    _changed();
    return s;
  }

  Future<void> createStory({required String category, required String title, required String body}) async {
    final t = title.trim();
    final b = body.trim();
    if (t.length < 3) throw const EdgewindException('Заголовок короче трёх букв.');
    if (b.isEmpty) throw const EdgewindException('Напиши хотя бы пару слов.');
    await _api.createStory(category: category, title: t, body: b);
    if (_category != kAll && _category != category) {
      _category = kAll;
    }
    await Future.wait([_try(refreshFeed), _try(_refreshMine), _try(refreshAchievements)]);
    unawaited(_saveSnapshot());
  }

  Future<void> deleteStory(String id) async {
    await _api.deleteStory(id);
    _feed = _feed.where((s) => s.id != id).toList();
    _mine = _mine.where((s) => s.id != id).toList();
    _saved = _saved.where((s) => s.id != id).toList();
    _extra.remove(id);
    _inbox = _inbox.where((i) => i.storyId != id).toList();
    _changed();
    unawaited(_saveSnapshot());
  }

  // ── «я рядом» и архив ───────────────────────────────────────────────────

  /// Отметка ставится сразу, запрос уходит следом. Пока запрос в пути,
  /// повторные нажатия игнорируются — иначе быстрый двойной тап рассинхронит
  /// счётчик с базой.
  Future<void> toggleLike(String storyId) async {
    final before = storyById(storyId);
    if (before == null || before.isMine || _likeBusy.contains(storyId)) return;
    final on = !before.likedByMe;
    _likeBusy.add(storyId);
    _patch(storyId, (s) => s.copyWith(likedByMe: on, likes: (s.likes + (on ? 1 : -1)).clamp(0, 1 << 30)));
    try {
      await _api.setLike(storyId, on);
      unawaited(_try(refreshAchievements));
    } catch (e) {
      _patch(storyId, (s) => s.copyWith(likedByMe: !on, likes: (s.likes + (on ? -1 : 1)).clamp(0, 1 << 30)));
      say(describe(e), error: true);
    } finally {
      _likeBusy.remove(storyId);
    }
  }

  Future<void> toggleSaved(String storyId) async {
    final before = storyById(storyId);
    if (before == null || _saveBusy.contains(storyId)) return;
    final on = !before.savedByMe;
    _saveBusy.add(storyId);
    _patch(storyId, (s) => s.copyWith(savedByMe: on));
    final after = storyById(storyId)!;
    _saved = on ? [after, ..._saved.where((s) => s.id != storyId)] : _saved.where((s) => s.id != storyId).toList();
    _changed();
    try {
      await _api.setSaved(storyId, on);
    } catch (e) {
      _patch(storyId, (s) => s.copyWith(savedByMe: !on));
      _saved = on ? _saved.where((s) => s.id != storyId).toList() : [before.copyWith(savedByMe: true), ..._saved];
      _changed();
      say(describe(e), error: true);
    } finally {
      _saveBusy.remove(storyId);
    }
  }

  // ── отклики ─────────────────────────────────────────────────────────────

  Future<List<StoryResponse>> responsesFor(String storyId) async {
    final rows = await _api.responses(storyId);
    final me = _api.userId;
    return rows.map((r) => StoryResponse.fromRow(r, myId: me)).toList();
  }

  Future<StoryResponse> addResponse(String storyId, String body) async {
    final text = body.trim();
    if (text.isEmpty) throw const EdgewindException('Отклик пустой.');
    final row = await _api.createResponse(storyId, text);
    final p = _profile;
    _patch(storyId, (s) => s.copyWith(responseCount: s.responseCount + 1));
    unawaited(_try(refreshAchievements));
    return StoryResponse(
      id: row['id'] as String,
      storyId: storyId,
      body: row['body'] as String? ?? text,
      createdAt: DateTime.tryParse(row['created_at'] as String? ?? '')?.toLocal() ?? DateTime.now(),
      authorId: row['author_id'] as String? ?? (_api.userId ?? ''),
      authorNickname: p?.nickname ?? '',
      authorAvatarKind: p?.avatarKind ?? 0,
      authorAvatarUrl: p?.avatarUrl,
      isMine: true,
    );
  }

  Future<void> deleteResponse(StoryResponse r) async {
    await _api.deleteResponse(r.id);
    _patch(r.storyId, (s) => s.copyWith(responseCount: (s.responseCount - 1).clamp(0, 1 << 30)));
  }

  // ── жалобы ──────────────────────────────────────────────────────────────

  /// Прячут запись только жалобы аккаунтов старше суток — это правило базы.
  Future<void> report({required String targetType, required String targetId, required String reason}) =>
      _api.report(targetType, targetId, reason);

  // ── служебное ───────────────────────────────────────────────────────────

  void _patch(String storyId, Story Function(Story) change) {
    List<Story> apply(List<Story> list) => list.map((s) => s.id == storyId ? change(s) : s).toList();
    _feed = apply(_feed);
    _mine = apply(_mine);
    _saved = apply(_saved);
    final extra = _extra[storyId];
    if (extra != null) _extra[storyId] = change(extra);
    _changed();
  }

  bool _isNetwork(Object e) {
    if (e is AuthRetryableFetchException) return true;
    if (e is TimeoutException) return true;
    final s = e.toString();
    return s.contains('SocketException') ||
        s.contains('ClientException') ||
        s.contains('Failed host lookup') ||
        s.contains('Connection closed') ||
        s.contains('Connection refused') ||
        s.contains('HandshakeException') ||
        s.contains('Network is unreachable');
  }

  bool _isDeadSession(Object e) {
    if (e is AuthApiException) {
      final code = e.code ?? '';
      return code == 'user_not_found' ||
          code == 'session_not_found' ||
          code == 'refresh_token_not_found' ||
          code == 'bad_jwt';
    }
    if (e is PostgrestException) {
      final m = e.message.toLowerCase();
      return e.code == 'PGRST301' || m.contains('jwt') && (m.contains('expired') || m.contains('invalid'));
    }
    return false;
  }

  /// Ошибка человеческими словами.
  String describe(Object e) {
    if (e is EdgewindException) return e.message;
    if (_isNetwork(e)) return 'Нет связи. Проверь интернет и попробуй ещё раз.';
    if (e is AuthException) {
      if (e.message.toLowerCase().contains('anonymous')) {
        return 'Вход без регистрации выключен на сервере. Это чинится в настройках Supabase.';
      }
      return 'Не удалось войти. ${e.message}';
    }
    if (e is StorageException) return 'Не удалось загрузить фото.';
    if (e is PostgrestException) {
      final m = e.message;
      // Уникальность проверяется первой: текст Postgres о ней сам начинается
      // со слова «duplicate» и иначе выдавался бы за повторную отправку.
      if (e.code == '23505') {
        return m.contains('nickname') ? 'Это имя уже занято — попробуй другое.' : 'Это уже отправлено.';
      }
      // Собственные ошибки базы (raise exception) приходят с кодом P0001
      // и коротким словом-причиной вместо текста.
      if (e.code == 'P0001') {
        switch (m.trim()) {
          case 'rate_limited':
            return 'Слишком часто. Передохни пару минут и попробуй снова.';
          case 'duplicate':
            return 'Это уже отправлено.';
          case 'self_report':
            return 'На своё жаловаться не нужно — его можно удалить.';
          case 'target_not_found':
            return 'Этой записи уже нет: её удалили или скрыли.';
          case 'nickname_reserved':
            return 'Имя похоже на служебное или на имя специалиста. Выбери другое — так никто не перепутает.';
          case 'avatar_url_rejected':
            return 'Не удалось сохранить фото.';
        }
      }
      if (e.code == '23514') {
        if (m.contains('nickname_clean')) return 'В имени есть лишние пробелы или невидимые символы.';
        if (m.contains('nickname')) return 'Имя — от 2 до 24 знаков.';
        if (m.contains('title')) return 'Заголовок — от 3 до 120 знаков.';
        return 'Слишком коротко или слишком длинно.';
      }
      if (e.code == '42501') return 'Это действие сейчас недоступно.';
      return 'Не получилось. Попробуй ещё раз.';
    }
    if (e is TimeoutException) return 'Сервер долго не отвечает. Попробуй ещё раз.';
    return 'Что-то пошло не так. Попробуй ещё раз.';
  }

  /// Для тестов экранов: состояние без сети.
  @visibleForTesting
  void debugSeed({
    required Profile profile,
    List<Story> feed = const [],
    List<Story> mine = const [],
    List<Story> saved = const [],
    List<InboxItem> inbox = const [],
    List<Achievement> achievements = const [],
    DateTime? inboxSeen,
  }) {
    _profile = profile;
    _feed = feed;
    _feedLoaded = true;
    _feedEnd = true;
    _mine = mine;
    _saved = saved;
    _inbox = inbox;
    _achievements = achievements;
    _inboxSeen = inboxSeen;
    _phase = Phase.ready;
    _changed();
  }

  @visibleForTesting
  void debugPhase(Phase phase) {
    _phase = phase;
    _changed();
  }
}

/// Формат картинки по первым байтам: расширение и MIME-тип или null.
(String, String)? imageKind(Uint8List b) {
  if (b.length >= 3 && b[0] == 0xFF && b[1] == 0xD8 && b[2] == 0xFF) return ('jpg', 'image/jpeg');
  if (b.length >= 8 && b[0] == 0x89 && b[1] == 0x50 && b[2] == 0x4E && b[3] == 0x47) return ('png', 'image/png');
  if (b.length >= 12 &&
      b[0] == 0x52 && b[1] == 0x49 && b[2] == 0x46 && b[3] == 0x46 &&
      b[8] == 0x57 && b[9] == 0x45 && b[10] == 0x42 && b[11] == 0x50) {
    return ('webp', 'image/webp');
  }
  return null;
}
