import 'dart:typed_data';

import 'package:flutter/foundation.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

import '../domain/models.dart';
import 'supabase_config.dart';

/// Ошибка, которую не стыдно показать человеку целиком.
class EdgewindException implements Exception {
  const EdgewindException(this.message);
  final String message;
  @override
  String toString() => message;
}

/// Единственный источник данных приложения.
///
/// Раньше всё лежало на телефоне и люди друг друга не видели. Теперь истории
/// и отклики общие, а вход анонимный: ни почты, ни телефона, ни пароля. Имя и
/// знак человек придумывает сам — настоящее имя не спрашивается нигде.
class EdgewindStore extends ChangeNotifier {
  SupabaseClient get _db => Supabase.instance.client;

  bool _ready = false;
  String? _error;
  Profile? _profile;
  List<Story> _stories = const [];
  List<Story> _mine = const [];
  List<Story> _saved = const [];
  List<Achievement> _achievements = const [];
  List<InboxItem> _inbox = const [];
  String _category = kAllCategories;
  bool _busy = false;

  static const String kAllCategories = 'Все';

  bool get ready => _ready;
  String? get error => _error;
  Profile? get profile => _profile;
  bool get hasProfile => _profile != null;
  bool get busy => _busy;
  String get category => _category;
  List<Story> get stories => List.unmodifiable(_stories);
  List<Story> get myStories => List.unmodifiable(_mine);
  List<Story> get savedStories => List.unmodifiable(_saved);
  List<Achievement> get achievements => List.unmodifiable(_achievements);
  List<InboxItem> get inbox => List.unmodifiable(_inbox);
  int get earnedCount => _achievements.where((a) => a.earned).length;
  String? get userId => _db.auth.currentUser?.id;

  void clearError() {
    if (_error == null) return;
    _error = null;
    notifyListeners();
  }

  // ── запуск ──────────────────────────────────────────────────────────────

  Future<void> load() async {
    _error = null;
    try {
      await _signIn();
      await _loadProfile();
      if (_profile != null) await refreshAll();
    } catch (e) {
      _error = _message(e);
    }
    _ready = true;
    notifyListeners();
  }

  Future<void> _signIn() async {
    if (_db.auth.currentUser != null) return;
    await _db.auth.signInAnonymously();
  }

  Future<void> _loadProfile() async {
    final id = userId;
    if (id == null) return;
    final row = await _db.from('ew_profiles').select().eq('id', id).maybeSingle();
    if (row == null) {
      _profile = null;
      return;
    }
    _profile = Profile.fromRow(row);
    // Отметка захода: серию дней и достижение «Неделя здесь» считает база.
    try {
      final touched = await _db.rpc('ew_touch');
      if (touched is Map<String, dynamic>) _profile = Profile.fromRow(touched);
    } catch (_) {
      // Серия — не повод не пустить человека внутрь.
    }
  }

  Future<void> refreshAll() async {
    await Future.wait([refreshFeed(), refreshAchievements(), refreshInbox()]);
  }

  // ── знакомство ──────────────────────────────────────────────────────────

  /// Занимает имя и создаёт профиль. Имя занимается целиком и без учёта
  /// регистра: «Полынь» и «полынь» — одно и то же имя.
  Future<void> createProfile({required String nickname, int avatarKind = 0}) async {
    final name = nickname.trim();
    if (name.length < 2) throw const EdgewindException('Имя короче двух букв.');
    if (name.length > 24) throw const EdgewindException('Имя длиннее 24 букв.');

    await _guard(() async {
      await _signIn();
      final id = userId;
      if (id == null) throw const EdgewindException('Не удалось начать сеанс.');
      final row = await _db
          .from('ew_profiles')
          .insert({'id': id, 'nickname': name, 'avatar_kind': avatarKind})
          .select()
          .single();
      _profile = Profile.fromRow(row);
      await refreshAll();
    });
  }

  Future<void> renameTo(String nickname) async {
    final name = nickname.trim();
    if (name.length < 2) throw const EdgewindException('Имя короче двух букв.');
    await _guard(() async {
      final row = await _db
          .from('ew_profiles')
          .update({'nickname': name})
          .eq('id', userId!)
          .select()
          .single();
      _profile = Profile.fromRow(row);
      await refreshFeed();
    });
  }

  Future<void> setAvatarKind(int kind) async {
    await _guard(() async {
      final row = await _db
          .from('ew_profiles')
          .update({'avatar_kind': kind, 'avatar_url': null})
          .eq('id', userId!)
          .select()
          .single();
      _profile = Profile.fromRow(row);
      await refreshFeed();
    });
  }

  /// Кладёт фото в папку со своим id: политика хранилища в чужую не пустит.
  Future<void> uploadAvatar(Uint8List bytes, {String extension = 'jpg'}) async {
    await _guard(() async {
      final id = userId!;
      final path = '$id/avatar.$extension';
      await _db.storage.from(avatarBucket).uploadBinary(
        path,
        bytes,
        fileOptions: FileOptions(upsert: true, contentType: 'image/$extension'),
      );
      // Метка времени в адресе: без неё телефон покажет прежнюю картинку из кеша.
      final url = '${_db.storage.from(avatarBucket).getPublicUrl(path)}'
          '?v=${DateTime.now().millisecondsSinceEpoch}';
      final row = await _db
          .from('ew_profiles')
          .update({'avatar_url': url})
          .eq('id', id)
          .select()
          .single();
      _profile = Profile.fromRow(row);
      await refreshFeed();
    });
  }

  // ── лента ───────────────────────────────────────────────────────────────

  void setCategory(String value) {
    if (_category == value) return;
    _category = value;
    notifyListeners();
    refreshFeed();
  }

  Future<void> refreshFeed() async {
    final id = userId;
    if (id == null) return;

    var q = _db.from('ew_feed').select();
    if (_category != kAllCategories) q = q.eq('category', _category);
    final rows = await q.order('created_at', ascending: false).limit(100);
    _stories = rows.map(Story.fromRow).toList();

    final mineRows = await _db
        .from('ew_feed')
        .select()
        .eq('author_id', id)
        .order('created_at', ascending: false);
    _mine = mineRows.map(Story.fromRow).toList();

    // Архив берётся в два запроса нарочно. Вложить представление ew_feed прямо
    // в выборку из ew_saves можно, только если PostgREST сам распознает связь
    // таблицы с представлением, а на это полагаться нельзя — по id надёжнее.
    final savedRows = await _db.from('ew_saves').select('story_id').eq('user_id', id);
    final savedIds = savedRows.map((r) => r['story_id'] as String).toList();
    if (savedIds.isEmpty) {
      _saved = const [];
    } else {
      final savedStories = await _db
          .from('ew_feed')
          .select()
          .inFilter('id', savedIds)
          .order('created_at', ascending: false);
      _saved = savedStories.map(Story.fromRow).toList();
    }

    notifyListeners();
  }

  Future<void> refreshAchievements() async {
    final rows = await _db.rpc('ew_my_achievements') as List<dynamic>;
    _achievements = rows.map((r) => Achievement.fromRow(r as Map<String, dynamic>)).toList();
    notifyListeners();
  }

  /// Отклики на мои истории. Свои отфильтрованы: сообщать себе о себе незачем.
  Future<void> refreshInbox() async {
    final id = userId;
    if (id == null) return;
    final rows = await _db
        .from('ew_responses')
        .select('id, body, created_at, author_id, '
            'author:ew_profiles!inner(nickname, avatar_kind, avatar_url), '
            'story:ew_stories!inner(id, title, author_id)')
        .eq('story.author_id', id)
        .neq('author_id', id)
        .order('created_at', ascending: false)
        .limit(50);
    _inbox = rows.map(InboxItem.fromRow).toList();
    notifyListeners();
  }

  Story? storyById(String id) {
    for (final s in [..._stories, ..._mine, ..._saved]) {
      if (s.id == id) return s;
    }
    return null;
  }

  Future<void> createStory({
    required String category,
    required String title,
    required String body,
  }) async {
    if (title.trim().length < 3) throw const EdgewindException('Заголовок слишком короткий.');
    if (body.trim().isEmpty) throw const EdgewindException('Пустая запись.');
    await _guard(() async {
      await _db.from('ew_stories').insert({
        'author_id': userId,
        'category': category,
        'title': title.trim(),
        'body': body.trim(),
      });
      await Future.wait([refreshFeed(), refreshAchievements()]);
    });
  }

  Future<void> deleteStory(String storyId) async {
    await _guard(() async {
      await _db.from('ew_stories').delete().eq('id', storyId);
      await refreshFeed();
    });
  }

  // ── «я рядом» и архив ───────────────────────────────────────────────────

  bool isLiked(String storyId) => storyById(storyId)?.likedByMe ?? false;
  bool isSaved(String storyId) => storyById(storyId)?.savedByMe ?? false;

  /// Отметка ставится сразу, запрос уходит следом: ждать сеть ради галочки
  /// незачем. Если запрос не прошёл — состояние возвращается обратно.
  Future<void> toggleLike(String storyId) async {
    final before = storyById(storyId);
    if (before == null) return;
    final on = !before.likedByMe;

    _patch(storyId, (s) => s.copyWith(likedByMe: on, likes: s.likes + (on ? 1 : -1)));
    try {
      if (on) {
        await _db.from('ew_likes').insert({'story_id': storyId, 'user_id': userId});
      } else {
        await _db.from('ew_likes').delete().eq('story_id', storyId).eq('user_id', userId!);
      }
      await refreshAchievements();
    } catch (e) {
      _patch(storyId, (s) => s.copyWith(likedByMe: !on, likes: s.likes + (on ? -1 : 1)));
      _error = _message(e);
      notifyListeners();
    }
  }

  Future<void> toggleSaved(String storyId) async {
    final before = storyById(storyId);
    if (before == null) return;
    final on = !before.savedByMe;

    _patch(storyId, (s) => s.copyWith(savedByMe: on));
    try {
      if (on) {
        await _db.from('ew_saves').insert({'story_id': storyId, 'user_id': userId});
      } else {
        await _db.from('ew_saves').delete().eq('story_id', storyId).eq('user_id', userId!);
      }
      await refreshFeed();
    } catch (e) {
      _patch(storyId, (s) => s.copyWith(savedByMe: !on));
      _error = _message(e);
      notifyListeners();
    }
  }

  // ── отклики ─────────────────────────────────────────────────────────────

  Future<List<StoryResponse>> responsesFor(String storyId) async {
    final rows = await _db
        .from('ew_responses')
        .select('*, author:ew_profiles!inner(nickname, avatar_kind, avatar_url)')
        .eq('story_id', storyId)
        .order('created_at');
    return rows.map((r) => StoryResponse.fromRow(r, myId: userId)).toList();
  }

  Future<void> addResponse(String storyId, String body) async {
    final text = body.trim();
    if (text.isEmpty) throw const EdgewindException('Отклик пустой.');
    await _guard(() async {
      await _db.from('ew_responses').insert({
        'story_id': storyId,
        'author_id': userId,
        'body': text,
      });
      _patch(storyId, (s) => s.copyWith(responseCount: s.responseCount + 1));
      await refreshAchievements();
    });
  }

  // ── жалобы ──────────────────────────────────────────────────────────────

  /// Три независимые жалобы убирают запись из ленты до разбора. Пожаловаться
  /// дважды на одно и то же нельзя — это ограничение базы, не проверка здесь.
  Future<void> report({
    required String targetType,
    required String targetId,
    required String reason,
  }) async {
    await _guard(() async {
      await _db.from('ew_reports').insert({
        'target_type': targetType,
        'target_id': targetId,
        'reporter_id': userId,
        'reason': reason,
      });
      await refreshFeed();
    });
  }

  // ── служебное ───────────────────────────────────────────────────────────

  void _patch(String storyId, Story Function(Story) change) {
    List<Story> apply(List<Story> list) =>
        list.map((s) => s.id == storyId ? change(s) : s).toList();
    _stories = apply(_stories);
    _mine = apply(_mine);
    _saved = apply(_saved);
    notifyListeners();
  }

  Future<void> _guard(Future<void> Function() body) async {
    _busy = true;
    _error = null;
    notifyListeners();
    try {
      await body();
    } catch (e) {
      _error = _message(e);
      rethrow;
    } finally {
      _busy = false;
      notifyListeners();
    }
  }

  String _message(Object e) {
    if (e is EdgewindException) return e.message;
    if (e is AuthException) {
      // Анонимный вход выключается в настройках проекта, и без него приложение
      // не работает вообще — честнее сказать это прямо.
      if (e.message.toLowerCase().contains('anonymous')) {
        return 'Анонимный вход выключен в настройках Supabase. '
            'Включи Authentication → Sign In / Providers → Anonymous sign-ins.';
      }
      // Сюда попадает и обрыв сети: gotrue заворачивает её в ту же ошибку.
      // Один раз это уже стоило круга поисков — сборка ушла без разрешения
      // INTERNET, а экран говорил «не удалось войти», как будто дело в пароле.
      if (e is AuthRetryableFetchException) {
        return 'Сервер не отвечает. Проверь интернет и попробуй ещё раз.';
      }
      // Остальное показываем как есть: догадываться по вежливой формулировке,
      // что именно сломалось, дороже, чем прочитать настоящий текст.
      return 'Не удалось войти.\n\n${e.message}';
    }
    if (e is PostgrestException) {
      if (e.code == '23505') {
        return e.message.contains('nickname')
            ? 'Такое имя уже занято — попробуй другое.'
            : 'Это уже отправлено.';
      }
      if (e.code == '23514') return 'Слишком коротко или слишком длинно.';
      if (e.code == '42501') return 'Нет доступа к этому действию.';
      return 'Не получилось. Попробуй ещё раз.';
    }
    if (e is StorageException) return 'Не удалось загрузить фото.';
    return 'Нет связи. Проверь интернет.';
  }
}
