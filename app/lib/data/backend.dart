import 'dart:typed_data';

import 'package:supabase_flutter/supabase_flutter.dart';

import 'supabase_config.dart';

/// Всё, что приложению нужно от сервера. Отделено от состояния, чтобы экраны
/// можно было проверить без сети — тесты подставляют свою реализацию.
abstract class Backend {
  String? get userId;

  /// Есть ли на телефоне сессия. Без неё ещё не было знакомства — и сеть для
  /// первого экрана не нужна.
  bool get hasSession;

  /// Анонимная сессия: без почты, телефона и пароля.
  Future<void> ensureSession();

  /// Отметка захода; возвращает свой профиль или null, если имени ещё нет.
  Future<Map<String, dynamic>?> touch();

  /// Свой профиль целиком, без отметки захода.
  Future<Map<String, dynamic>?> me();

  Future<void> createProfile({required String nickname, required int avatarKind});
  Future<void> updateProfile(Map<String, Object?> patch);

  Future<List<Map<String, dynamic>>> feed({String? category, DateTime? before, int limit = 40});
  Future<List<Map<String, dynamic>>> myStories();
  Future<List<Map<String, dynamic>>> saved();
  Future<List<Map<String, dynamic>>> inbox();
  Future<Map<String, dynamic>?> story(String id);
  Future<List<Map<String, dynamic>>> responses(String storyId);
  Future<List<Map<String, dynamic>>> achievements();

  Future<void> createStory({required String category, required String title, required String body});
  Future<void> deleteStory(String id);
  Future<void> setLike(String storyId, bool on);
  Future<void> setSaved(String storyId, bool on);
  Future<Map<String, dynamic>> createResponse(String storyId, String body);
  Future<void> deleteResponse(String id);
  Future<void> report(String targetType, String targetId, String reason);

  /// Кладёт фото в свою папку и возвращает его адрес с меткой версии.
  Future<String> uploadAvatar(Uint8List bytes, {required String extension, required String contentType});
  Future<void> removeAvatarFiles();

  /// Удаляет аккаунт целиком: фото, профиль со всем написанным и сам вход.
  Future<void> deleteAccount();
  Future<void> signOut();
}

class SupabaseBackend implements Backend {
  SupabaseClient get _db => Supabase.instance.client;

  /// Колонки ленты перечислены явно: служебные поля чужих записей база
  /// наружу не отдаёт, и select=* упал бы.
  static const _feedColumns =
      'id,category,title,body,created_at,like_count,response_count,author_id,'
      'author_nickname,author_avatar_kind,author_avatar_url,liked_by_me,saved_by_me,is_mine,hidden';

  static const _responseColumns =
      'id,story_id,body,created_at,author_id,author:ew_profiles!inner(nickname,avatar_kind,avatar_url)';

  String get _uid {
    final id = userId;
    if (id == null) throw const AuthException('no session');
    return id;
  }

  Map<String, dynamic>? _row(Object? value) {
    if (value is Map<String, dynamic> && value['id'] != null) return value;
    if (value is List && value.isNotEmpty && value.first is Map<String, dynamic>) {
      final first = value.first as Map<String, dynamic>;
      return first['id'] == null ? null : first;
    }
    return null;
  }

  List<Map<String, dynamic>> _rows(Object? value) =>
      value is List ? value.whereType<Map<String, dynamic>>().toList() : const [];

  @override
  String? get userId => _db.auth.currentUser?.id;

  @override
  bool get hasSession => _db.auth.currentSession != null;

  @override
  Future<void> ensureSession() async {
    if (_db.auth.currentSession != null) return;
    await _db.auth.signInAnonymously();
  }

  @override
  Future<Map<String, dynamic>?> touch() async => _row(await _db.rpc('ew_touch'));

  @override
  Future<Map<String, dynamic>?> me() async => _row(await _db.rpc('ew_me'));

  @override
  Future<void> createProfile({required String nickname, required int avatarKind}) =>
      _db.from('ew_profiles').insert({'id': _uid, 'nickname': nickname, 'avatar_kind': avatarKind});

  @override
  Future<void> updateProfile(Map<String, Object?> patch) =>
      _db.from('ew_profiles').update(patch).eq('id', _uid);

  @override
  Future<List<Map<String, dynamic>>> feed({String? category, DateTime? before, int limit = 40}) async {
    var q = _db.from('ew_feed').select(_feedColumns);
    if (category != null) q = q.eq('category', category);
    if (before != null) q = q.lt('created_at', before.toUtc().toIso8601String());
    return await q.order('created_at', ascending: false).limit(limit);
  }

  @override
  Future<List<Map<String, dynamic>>> myStories() async => await _db
      .from('ew_feed')
      .select(_feedColumns)
      .eq('author_id', _uid)
      .order('created_at', ascending: false)
      .limit(200);

  @override
  Future<List<Map<String, dynamic>>> saved() async => _rows(await _db.rpc('ew_saved'));

  @override
  Future<List<Map<String, dynamic>>> inbox() async {
    final uid = _uid;
    return await _db
        .from('ew_responses')
        .select('id,body,created_at,author_id,story_id,'
            'author:ew_profiles!inner(nickname,avatar_kind,avatar_url),'
            'story:ew_stories!inner(id,title,author_id)')
        .eq('story.author_id', uid)
        .neq('author_id', uid)
        .order('created_at', ascending: false)
        .limit(80);
  }

  @override
  Future<Map<String, dynamic>?> story(String id) async =>
      _row(await _db.from('ew_feed').select(_feedColumns).eq('id', id).limit(1));

  @override
  Future<List<Map<String, dynamic>>> responses(String storyId) async => await _db
      .from('ew_responses')
      .select(_responseColumns)
      .eq('story_id', storyId)
      .order('created_at')
      .limit(400);

  @override
  Future<List<Map<String, dynamic>>> achievements() async => _rows(await _db.rpc('ew_my_achievements'));

  @override
  Future<void> createStory({required String category, required String title, required String body}) =>
      _db.from('ew_stories').insert({'author_id': _uid, 'category': category, 'title': title, 'body': body});

  @override
  Future<void> deleteStory(String id) => _db.from('ew_stories').delete().eq('id', id);

  @override
  Future<void> setLike(String storyId, bool on) async {
    final uid = _uid;
    if (!on) {
      await _db.from('ew_likes').delete().eq('story_id', storyId).eq('user_id', uid);
      return;
    }
    try {
      await _db.from('ew_likes').insert({'story_id': storyId, 'user_id': uid});
    } on PostgrestException catch (e) {
      if (e.code != '23505') rethrow; // уже стоит — значит, цель достигнута
    }
  }

  @override
  Future<void> setSaved(String storyId, bool on) async {
    final uid = _uid;
    if (!on) {
      await _db.from('ew_saves').delete().eq('story_id', storyId).eq('user_id', uid);
      return;
    }
    try {
      await _db.from('ew_saves').insert({'story_id': storyId, 'user_id': uid});
    } on PostgrestException catch (e) {
      if (e.code != '23505') rethrow;
    }
  }

  @override
  Future<Map<String, dynamic>> createResponse(String storyId, String body) async => await _db
      .from('ew_responses')
      .insert({'story_id': storyId, 'author_id': _uid, 'body': body})
      .select('id,story_id,body,created_at,author_id')
      .single();

  @override
  Future<void> deleteResponse(String id) => _db.from('ew_responses').delete().eq('id', id);

  @override
  Future<void> report(String targetType, String targetId, String reason) async {
    try {
      await _db.from('ew_reports').insert({
        'target_type': targetType,
        'target_id': targetId,
        'reporter_id': _uid,
        'reason': reason,
      });
    } on PostgrestException catch (e) {
      if (e.code != '23505') rethrow; // уже жаловался — повтор не нужен
    }
  }

  @override
  Future<String> uploadAvatar(Uint8List bytes, {required String extension, required String contentType}) async {
    final uid = _uid;
    final path = '$uid/avatar.$extension';
    final bucket = _db.storage.from(avatarBucket);
    await bucket.uploadBinary(path, bytes, fileOptions: FileOptions(upsert: true, contentType: contentType));
    // Старое фото другого формата больше не нужно — иначе в папке копится мусор.
    final stale = ['jpg', 'png', 'webp'].where((e) => e != extension).map((e) => '$uid/avatar.$e').toList();
    try {
      await bucket.remove(stale);
    } catch (_) {}
    // Метка версии в адресе: без неё телефон покажет прежнюю картинку из кэша.
    return '${bucket.getPublicUrl(path)}?v=${DateTime.now().millisecondsSinceEpoch}';
  }

  @override
  Future<void> removeAvatarFiles() async {
    final uid = _uid;
    await _db.storage.from(avatarBucket).remove(['jpg', 'png', 'webp'].map((e) => '$uid/avatar.$e').toList());
  }

  @override
  Future<void> deleteAccount() async {
    try {
      await removeAvatarFiles();
    } catch (_) {
      // Фото могло не быть вовсе — удаление аккаунта от этого не зависит.
    }
    await _db.rpc('ew_delete_me');
    await signOut();
  }

  @override
  Future<void> signOut() async {
    try {
      await _db.auth.signOut(scope: SignOutScope.local);
    } catch (_) {
      // Сессия уже недействительна на сервере — локально она всё равно стёрта.
    }
  }
}
