/// Модели ложатся на строки PostgREST, поэтому ключи — как в базе. Каждая
/// модель умеет вернуться обратно в строку: так лента и профиль кэшируются на
/// телефоне и показываются сразу при запуске, ещё до ответа сервера.
library;

DateTime _date(Object? v) =>
    v is String ? (DateTime.tryParse(v)?.toLocal() ?? DateTime.now()) : DateTime.now();

int _int(Object? v, [int fallback = 0]) => (v as num?)?.toInt() ?? fallback;

class Profile {
  const Profile({
    required this.id,
    required this.nickname,
    required this.avatarKind,
    this.avatarUrl,
    required this.createdAt,
    this.streakDays = 1,
  });

  final String id;
  final String nickname;
  final int avatarKind;
  final String? avatarUrl;
  final DateTime createdAt;
  final int streakDays;

  factory Profile.fromRow(Map<String, dynamic> row) => Profile(
    id: row['id'] as String,
    nickname: row['nickname'] as String? ?? '',
    avatarKind: _int(row['avatar_kind']),
    avatarUrl: row['avatar_url'] as String?,
    createdAt: _date(row['created_at']),
    streakDays: _int(row['streak_days'], 1),
  );

  Map<String, dynamic> toRow() => {
    'id': id,
    'nickname': nickname,
    'avatar_kind': avatarKind,
    'avatar_url': avatarUrl,
    'created_at': createdAt.toUtc().toIso8601String(),
    'streak_days': streakDays,
  };
}

class Story {
  const Story({
    required this.id,
    required this.category,
    required this.title,
    required this.body,
    required this.createdAt,
    required this.authorId,
    required this.authorNickname,
    this.authorAvatarKind = 0,
    this.authorAvatarUrl,
    this.likes = 0,
    this.responseCount = 0,
    this.likedByMe = false,
    this.savedByMe = false,
    this.isMine = false,
    this.hidden = false,
  });

  final String id;
  final String category;
  final String title;
  final String body;
  final DateTime createdAt;
  final String authorId;
  final String authorNickname;
  final int authorAvatarKind;
  final String? authorAvatarUrl;
  final int likes;
  final int responseCount;
  final bool likedByMe;
  final bool savedByMe;
  final bool isMine;

  /// Скрыта по жалобам. Такую историю видит только её автор — с пометкой.
  final bool hidden;

  factory Story.fromRow(Map<String, dynamic> row) => Story(
    id: row['id'] as String,
    category: row['category'] as String? ?? 'Другое',
    title: row['title'] as String? ?? '',
    body: row['body'] as String? ?? '',
    createdAt: _date(row['created_at']),
    authorId: row['author_id'] as String? ?? '',
    authorNickname: row['author_nickname'] as String? ?? 'без имени',
    authorAvatarKind: _int(row['author_avatar_kind']),
    authorAvatarUrl: row['author_avatar_url'] as String?,
    likes: _int(row['like_count']),
    responseCount: _int(row['response_count']),
    likedByMe: row['liked_by_me'] as bool? ?? false,
    savedByMe: row['saved_by_me'] as bool? ?? false,
    isMine: row['is_mine'] as bool? ?? false,
    hidden: row['hidden'] as bool? ?? false,
  );

  Map<String, dynamic> toRow() => {
    'id': id,
    'category': category,
    'title': title,
    'body': body,
    'created_at': createdAt.toUtc().toIso8601String(),
    'author_id': authorId,
    'author_nickname': authorNickname,
    'author_avatar_kind': authorAvatarKind,
    'author_avatar_url': authorAvatarUrl,
    'like_count': likes,
    'response_count': responseCount,
    'liked_by_me': likedByMe,
    'saved_by_me': savedByMe,
    'is_mine': isMine,
    'hidden': hidden,
  };

  Story copyWith({
    int? likes,
    int? responseCount,
    bool? likedByMe,
    bool? savedByMe,
    String? authorNickname,
    int? authorAvatarKind,
    String? authorAvatarUrl,
    bool clearAvatarUrl = false,
  }) => Story(
    id: id,
    category: category,
    title: title,
    body: body,
    createdAt: createdAt,
    authorId: authorId,
    authorNickname: authorNickname ?? this.authorNickname,
    authorAvatarKind: authorAvatarKind ?? this.authorAvatarKind,
    authorAvatarUrl: clearAvatarUrl ? null : (authorAvatarUrl ?? this.authorAvatarUrl),
    likes: likes ?? this.likes,
    responseCount: responseCount ?? this.responseCount,
    likedByMe: likedByMe ?? this.likedByMe,
    savedByMe: savedByMe ?? this.savedByMe,
    isMine: isMine,
    hidden: hidden,
  );
}

class StoryResponse {
  const StoryResponse({
    required this.id,
    required this.storyId,
    required this.body,
    required this.createdAt,
    required this.authorId,
    required this.authorNickname,
    this.authorAvatarKind = 0,
    this.authorAvatarUrl,
    this.isMine = false,
  });

  final String id;
  final String storyId;
  final String body;
  final DateTime createdAt;
  final String authorId;
  final String authorNickname;
  final int authorAvatarKind;
  final String? authorAvatarUrl;
  final bool isMine;

  factory StoryResponse.fromRow(Map<String, dynamic> row, {String? myId}) {
    final author = row['author'] as Map<String, dynamic>?;
    final authorId = row['author_id'] as String? ?? '';
    return StoryResponse(
      id: row['id'] as String,
      storyId: row['story_id'] as String? ?? '',
      body: row['body'] as String? ?? '',
      createdAt: _date(row['created_at']),
      authorId: authorId,
      authorNickname: author?['nickname'] as String? ?? 'без имени',
      authorAvatarKind: _int(author?['avatar_kind']),
      authorAvatarUrl: author?['avatar_url'] as String?,
      isMine: myId != null && myId == authorId,
    );
  }
}

/// Отклик на мою историю — то, ради чего человек сюда возвращается.
class InboxItem {
  const InboxItem({
    required this.id,
    required this.body,
    required this.createdAt,
    required this.authorNickname,
    required this.authorAvatarKind,
    this.authorAvatarUrl,
    required this.storyId,
    required this.storyTitle,
  });

  final String id;
  final String body;
  final DateTime createdAt;
  final String authorNickname;
  final int authorAvatarKind;
  final String? authorAvatarUrl;
  final String storyId;
  final String storyTitle;

  factory InboxItem.fromRow(Map<String, dynamic> row) {
    final author = row['author'] as Map<String, dynamic>?;
    final story = row['story'] as Map<String, dynamic>?;
    return InboxItem(
      id: row['id'] as String,
      body: row['body'] as String? ?? '',
      createdAt: _date(row['created_at']),
      authorNickname: author?['nickname'] as String? ?? 'без имени',
      authorAvatarKind: _int(author?['avatar_kind']),
      authorAvatarUrl: author?['avatar_url'] as String?,
      storyId: story?['id'] as String? ?? row['story_id'] as String? ?? '',
      storyTitle: story?['title'] as String? ?? '',
    );
  }
}

class Achievement {
  const Achievement({
    required this.code,
    required this.title,
    required this.description,
    required this.goal,
    required this.progress,
    this.earnedAt,
  });

  final String code;
  final String title;
  final String description;
  final int goal;
  final int progress;
  final DateTime? earnedAt;

  bool get earned => earnedAt != null;

  /// Доля выполнения 0..1 — для полосы под закрытым достижением.
  double get fraction => goal <= 0 ? 0 : (progress / goal).clamp(0, 1).toDouble();

  factory Achievement.fromRow(Map<String, dynamic> row) => Achievement(
    code: row['code'] as String,
    title: row['title'] as String? ?? '',
    description: row['description'] as String? ?? '',
    goal: _int(row['goal'], 1),
    progress: _int(row['progress']),
    earnedAt: row['earned_at'] == null ? null : _date(row['earned_at']),
  );
}
