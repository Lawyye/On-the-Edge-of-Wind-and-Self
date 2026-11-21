import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

/// Что приложение помнит между запусками: профиль и первую страницу ленты
/// (чтобы открываться сразу, а не ждать сеть), что из откликов уже прочитано
/// и какую тему выбрал человек. Хранится только на телефоне.
class LocalCache {
  static const _snapshotKey = 'ew.snapshot.v1';
  static const _themeKey = 'ew.theme';
  static String _seenKey(String uid) => 'ew.inbox_seen.$uid';

  Future<SharedPreferences> get _prefs => SharedPreferences.getInstance();

  Future<CacheSnapshot?> readSnapshot() async {
    try {
      final raw = (await _prefs).getString(_snapshotKey);
      if (raw == null) return null;
      final json = jsonDecode(raw) as Map<String, dynamic>;
      return CacheSnapshot(
        userId: json['uid'] as String,
        profile: (json['profile'] as Map?)?.cast<String, dynamic>(),
        feed: ((json['feed'] as List?) ?? const []).cast<Map<String, dynamic>>(),
      );
    } catch (_) {
      return null; // испорченный кэш просто не используется
    }
  }

  Future<void> writeSnapshot(CacheSnapshot s) async {
    try {
      await (await _prefs).setString(
        _snapshotKey,
        jsonEncode({'uid': s.userId, 'profile': s.profile, 'feed': s.feed}),
      );
    } catch (_) {}
  }

  Future<void> clearSnapshot() async {
    try {
      await (await _prefs).remove(_snapshotKey);
    } catch (_) {}
  }

  Future<DateTime?> inboxSeen(String uid) async {
    try {
      final raw = (await _prefs).getString(_seenKey(uid));
      return raw == null ? null : DateTime.tryParse(raw);
    } catch (_) {
      return null;
    }
  }

  Future<void> setInboxSeen(String uid, DateTime at) async {
    try {
      await (await _prefs).setString(_seenKey(uid), at.toUtc().toIso8601String());
    } catch (_) {}
  }

  Future<bool?> darkTheme() async {
    try {
      final v = (await _prefs).getString(_themeKey);
      return v == null ? null : v == 'dark';
    } catch (_) {
      return null;
    }
  }

  Future<void> setDarkTheme(bool dark) async {
    try {
      await (await _prefs).setString(_themeKey, dark ? 'dark' : 'light');
    } catch (_) {}
  }
}

class CacheSnapshot {
  const CacheSnapshot({required this.userId, this.profile, this.feed = const []});
  final String userId;
  final Map<String, dynamic>? profile;
  final List<Map<String, dynamic>> feed;
}
