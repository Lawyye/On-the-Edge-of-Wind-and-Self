/// Модели ложатся на таблицы ew_* в Supabase. Разбор идёт из строк PostgREST,
/// поэтому ключи — как в базе, в нижнем регистре с подчёркиваниями.

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
    nickname: row['nickname'] as String,
    avatarKind: (row['avatar_kind'] as num?)?.toInt() ?? 0,
    avatarUrl: row['avatar_url'] as String?,
    createdAt: DateTime.parse(row['created_at'] as String),
    streakDays: (row['streak_days'] as num?)?.toInt() ?? 1,
  );
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

  /// Читается из представления ew_feed: имя автора и признаки «я отметил» и
  /// «я сохранил» оно уже приложило, поэтому на карточку не нужен отдельный запрос.
  factory Story.fromRow(Map<String, dynamic> row) => Story(
    id: row['id'] as String,
    category: row['category'] as String,
    title: row['title'] as String,
    body: row['body'] as String,
    createdAt: DateTime.parse(row['created_at'] as String),
    authorId: row['author_id'] as String,
    authorNickname: row['author_nickname'] as String? ?? 'без имени',
    authorAvatarKind: (row['author_avatar_kind'] as num?)?.toInt() ?? 0,
    authorAvatarUrl: row['author_avatar_url'] as String?,
    likes: (row['like_count'] as num?)?.toInt() ?? 0,
    responseCount: (row['response_count'] as num?)?.toInt() ?? 0,
    likedByMe: row['liked_by_me'] as bool? ?? false,
    savedByMe: row['saved_by_me'] as bool? ?? false,
    isMine: row['is_mine'] as bool? ?? false,
  );

  Story copyWith({int? likes, int? responseCount, bool? likedByMe, bool? savedByMe}) => Story(
    id: id,
    category: category,
    title: title,
    body: body,
    createdAt: createdAt,
    authorId: authorId,
    authorNickname: authorNickname,
    authorAvatarKind: authorAvatarKind,
    authorAvatarUrl: authorAvatarUrl,
    likes: likes ?? this.likes,
    responseCount: responseCount ?? this.responseCount,
    likedByMe: likedByMe ?? this.likedByMe,
    savedByMe: savedByMe ?? this.savedByMe,
    isMine: isMine,
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
    final authorId = row['author_id'] as String;
    return StoryResponse(
      id: row['id'] as String,
      storyId: row['story_id'] as String,
      body: row['body'] as String,
      createdAt: DateTime.parse(row['created_at'] as String),
      authorId: authorId,
      authorNickname: author?['nickname'] as String? ?? 'без имени',
      authorAvatarKind: (author?['avatar_kind'] as num?)?.toInt() ?? 0,
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
      body: row['body'] as String,
      createdAt: DateTime.parse(row['created_at'] as String),
      authorNickname: author?['nickname'] as String? ?? 'без имени',
      authorAvatarKind: (author?['avatar_kind'] as num?)?.toInt() ?? 0,
      authorAvatarUrl: author?['avatar_url'] as String?,
      storyId: story?['id'] as String? ?? '',
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
    title: row['title'] as String,
    description: row['description'] as String,
    goal: (row['goal'] as num?)?.toInt() ?? 1,
    progress: (row['progress'] as num?)?.toInt() ?? 0,
    earnedAt: row['earned_at'] == null ? null : DateTime.parse(row['earned_at'] as String),
  );
}
