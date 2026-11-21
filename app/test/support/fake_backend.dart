import 'dart:typed_data';

import 'package:edgewind/data/backend.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

/// Сервер без сети: экраны проверяются на готовых данных.
class FakeBackend implements Backend {
  FakeBackend({this.session = true});

  bool session;
  final List<String> calls = [];
  Map<String, dynamic>? profile;

  /// Имя, которое база считает занятым.
  String? takenName;

  @override
  String? get userId => session ? 'me' : null;

  @override
  bool get hasSession => session;

  @override
  Future<void> ensureSession() async => session = true;

  @override
  Future<Map<String, dynamic>?> touch() async => profile;

  @override
  Future<Map<String, dynamic>?> me() async => profile;

  @override
  Future<void> createProfile({required String nickname, required int avatarKind}) async {
    calls.add('createProfile $nickname');
    if (nickname == takenName) {
      throw const PostgrestException(
        message: 'duplicate key value violates unique constraint "ew_profiles_nickname_key"',
        code: '23505',
      );
    }
    profile = {
      'id': 'me',
      'nickname': nickname,
      'avatar_kind': avatarKind,
      'avatar_url': null,
      'created_at': DateTime.now().toUtc().toIso8601String(),
      'streak_days': 1,
    };
  }

  @override
  Future<void> updateProfile(Map<String, Object?> patch) async => calls.add('updateProfile');

  @override
  Future<List<Map<String, dynamic>>> feed({String? category, DateTime? before, int limit = 40}) async => const [];

  @override
  Future<List<Map<String, dynamic>>> myStories() async => const [];

  @override
  Future<List<Map<String, dynamic>>> saved() async => const [];

  @override
  Future<List<Map<String, dynamic>>> inbox() async => const [];

  @override
  Future<Map<String, dynamic>?> story(String id) async => null;

  @override
  Future<List<Map<String, dynamic>>> responses(String storyId) async => const [];

  @override
  Future<List<Map<String, dynamic>>> achievements() async => const [];

  @override
  Future<void> createStory({required String category, required String title, required String body}) async =>
      calls.add('createStory');

  @override
  Future<void> deleteStory(String id) async => calls.add('deleteStory $id');

  @override
  Future<void> setLike(String storyId, bool on) async => calls.add('like $storyId $on');

  @override
  Future<void> setSaved(String storyId, bool on) async => calls.add('save $storyId $on');

  @override
  Future<Map<String, dynamic>> createResponse(String storyId, String body) async => {
    'id': 'new',
    'story_id': storyId,
    'body': body,
    'created_at': DateTime.now().toUtc().toIso8601String(),
    'author_id': 'me',
  };

  @override
  Future<void> deleteResponse(String id) async => calls.add('deleteResponse $id');

  @override
  Future<void> report(String targetType, String targetId, String reason) async => calls.add('report $targetId');

  @override
  Future<String> uploadAvatar(Uint8List bytes, {required String extension, required String contentType}) async =>
      'https://example.invalid/me/avatar.$extension';

  @override
  Future<void> removeAvatarFiles() async {}

  @override
  Future<void> deleteAccount() async {
    calls.add('deleteAccount');
    profile = null;
    session = false;
  }

  @override
  Future<void> signOut() async => session = false;
}
