import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../domain/models.dart';

class EdgewindStore extends ChangeNotifier {
  static const _storiesKey = 'edgewind.stories';
  static const _responsesKey = 'edgewind.responses';
  static const _likedKey = 'edgewind.liked';
  static const _savedKey = 'edgewind.saved';
  static const _reportsKey = 'edgewind.tech_reports';
  static const _shareUsernameKey = 'edgewind.share_username';
  static const _avatarKey = 'edgewind.avatar';
  static const _joinedAtKey = 'edgewind.joined_at';

  List<Story> _stories = [];
  List<StoryResponse> _responses = [];
  List<TechReport> _techReports = [];
  Set<String> _likedIds = {};
  Set<String> _savedIds = {};
  bool _shareUsername = false;
  Uint8List? _avatarBytes;
  DateTime _joinedAt = DateTime.now();
  bool _ready = false;

  bool get ready => _ready;
  bool get shareUsername => _shareUsername;
  Uint8List? get avatarBytes => _avatarBytes;
  DateTime get joinedAt => _joinedAt;
  int get givenLikesCount => _likedIds.length;
  int get savedCount => _savedIds.length;
  List<Story> get stories => List.unmodifiable(_stories);
  List<Story> get myStories =>
      List.unmodifiable(_stories.where((story) => story.isMine));
  List<Story> get savedStories =>
      List.unmodifiable(_stories.where((story) => isSaved(story.id)));
  List<StoryResponse> get myResponses => List.unmodifiable(
    _responses.where((response) => response.isMine).toList().reversed,
  );
  List<TechReport> get techReports => List.unmodifiable(_techReports);

  Future<void> load() async {
    final prefs = await SharedPreferences.getInstance();
    _stories = _decodeList(prefs.getString(_storiesKey), Story.fromJson);
    _responses = _decodeList(
      prefs.getString(_responsesKey),
      StoryResponse.fromJson,
    );
    _techReports = _decodeList(
      prefs.getString(_reportsKey),
      TechReport.fromJson,
    );
    _likedIds = prefs.getStringList(_likedKey)?.toSet() ?? {};
    _savedIds = prefs.getStringList(_savedKey)?.toSet() ?? {};
    _shareUsername = prefs.getBool(_shareUsernameKey) ?? false;
    final avatar = prefs.getString(_avatarKey);
    if (avatar != null) {
      try {
        _avatarBytes = base64Decode(avatar);
      } on FormatException {
        _avatarBytes = null;
      }
    }
    final joinedAt = prefs.getString(_joinedAtKey);
    _joinedAt = DateTime.tryParse(joinedAt ?? '') ?? DateTime.now();
    if (joinedAt == null) {
      await prefs.setString(_joinedAtKey, _joinedAt.toIso8601String());
    }
    _ready = true;
    notifyListeners();
  }

  Future<void> createStory({
    required String category,
    required String title,
    required String body,
  }) async {
    _stories.insert(
      0,
      Story(
        id: _newId(),
        category: category,
        title: title,
        body: body,
        createdAt: DateTime.now(),
      ),
    );
    await _persistStories();
    notifyListeners();
  }

  Future<void> deleteStory(String storyId) async {
    _stories.removeWhere((story) => story.id == storyId);
    _responses.removeWhere((response) => response.storyId == storyId);
    _likedIds.remove(storyId);
    _savedIds.remove(storyId);
    await Future.wait([
      _persistStories(),
      _persistResponses(),
      _persistStringSets(),
    ]);
    notifyListeners();
  }

  Story? storyById(String storyId) {
    for (final story in _stories) {
      if (story.id == storyId) return story;
    }
    return null;
  }

  List<StoryResponse> responsesFor(String storyId) => List.unmodifiable(
    _responses.where((response) => response.storyId == storyId),
  );

  int responseCount(String storyId) =>
      _responses.where((response) => response.storyId == storyId).length;

  bool isLiked(String storyId) => _likedIds.contains(storyId);
  bool isSaved(String storyId) => _savedIds.contains(storyId);

  int likeCount(Story story) => story.likes + (isLiked(story.id) ? 1 : 0);

  Future<void> toggleLike(String storyId) async {
    if (!_likedIds.add(storyId)) _likedIds.remove(storyId);
    await _persistStringSets();
    notifyListeners();
  }

  Future<void> toggleSaved(String storyId) async {
    if (!_savedIds.add(storyId)) _savedIds.remove(storyId);
    await _persistStringSets();
    notifyListeners();
  }

  Future<void> addResponse(String storyId, String text) async {
    _responses.add(
      StoryResponse(
        id: _newId(),
        storyId: storyId,
        text: text.trim(),
        createdAt: DateTime.now(),
      ),
    );
    await _persistResponses();
    notifyListeners();
  }

  Future<void> addTechReport(String text) async {
    _techReports.insert(
      0,
      TechReport(id: _newId(), text: text.trim(), createdAt: DateTime.now()),
    );
    await _persistTechReports();
    notifyListeners();
  }

  Future<void> setShareUsername(bool value) async {
    _shareUsername = value;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_shareUsernameKey, value);
    notifyListeners();
  }

  Future<void> setAvatar(Uint8List? bytes) async {
    _avatarBytes = bytes;
    final prefs = await SharedPreferences.getInstance();
    if (bytes == null) {
      await prefs.remove(_avatarKey);
    } else {
      await prefs.setString(_avatarKey, base64Encode(bytes));
    }
    notifyListeners();
  }

  Future<void> clearUserData() async {
    _stories = [];
    _responses = [];
    _techReports = [];
    _likedIds = {};
    _savedIds = {};
    _shareUsername = false;
    _avatarBytes = null;
    _joinedAt = DateTime.now();
    final prefs = await SharedPreferences.getInstance();
    await Future.wait([
      prefs.remove(_storiesKey),
      prefs.remove(_responsesKey),
      prefs.remove(_reportsKey),
      prefs.remove(_likedKey),
      prefs.remove(_savedKey),
      prefs.remove(_shareUsernameKey),
      prefs.remove(_avatarKey),
      prefs.remove(_joinedAtKey),
    ]);
    notifyListeners();
  }

  Future<void> _persistStories() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(
      _storiesKey,
      jsonEncode(_stories.map((story) => story.toJson()).toList()),
    );
  }

  Future<void> _persistResponses() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(
      _responsesKey,
      jsonEncode(_responses.map((item) => item.toJson()).toList()),
    );
  }

  Future<void> _persistTechReports() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(
      _reportsKey,
      jsonEncode(_techReports.map((item) => item.toJson()).toList()),
    );
  }

  Future<void> _persistStringSets() async {
    final prefs = await SharedPreferences.getInstance();
    await Future.wait([
      prefs.setStringList(_likedKey, _likedIds.toList()),
      prefs.setStringList(_savedKey, _savedIds.toList()),
    ]);
  }

  static List<T> _decodeList<T>(
    String? raw,
    T Function(Map<String, Object?> json) fromJson,
  ) {
    if (raw == null || raw.isEmpty) return [];
    try {
      final decoded = jsonDecode(raw) as List<dynamic>;
      return decoded
          .map((item) => fromJson((item as Map).cast<String, Object?>()))
          .toList();
    } on FormatException {
      return [];
    } on TypeError {
      return [];
    }
  }

  static String _newId() =>
      '${DateTime.now().microsecondsSinceEpoch}-${DateTime.now().hashCode}';
}
