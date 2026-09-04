class Story {
  const Story({
    required this.id,
    required this.category,
    required this.title,
    required this.body,
    required this.createdAt,
    this.isMine = true,
    this.likes = 0,
  });

  final String id;
  final String category;
  final String title;
  final String body;
  final DateTime createdAt;
  final bool isMine;
  final int likes;

  Map<String, Object?> toJson() => {
    'id': id,
    'category': category,
    'title': title,
    'body': body,
    'createdAt': createdAt.toIso8601String(),
    'isMine': isMine,
    'likes': likes,
  };

  factory Story.fromJson(Map<String, Object?> json) => Story(
    id: json['id'] as String,
    category: json['category'] as String,
    title: json['title'] as String,
    body: json['body'] as String,
    createdAt: DateTime.parse(json['createdAt'] as String),
    isMine: json['isMine'] as bool? ?? true,
    likes: json['likes'] as int? ?? 0,
  );
}

class StoryResponse {
  const StoryResponse({
    required this.id,
    required this.storyId,
    required this.text,
    required this.createdAt,
    this.isMine = true,
  });

  final String id;
  final String storyId;
  final String text;
  final DateTime createdAt;
  final bool isMine;

  Map<String, Object?> toJson() => {
    'id': id,
    'storyId': storyId,
    'text': text,
    'createdAt': createdAt.toIso8601String(),
    'isMine': isMine,
  };

  factory StoryResponse.fromJson(Map<String, Object?> json) => StoryResponse(
    id: json['id'] as String,
    storyId: json['storyId'] as String,
    text: json['text'] as String,
    createdAt: DateTime.parse(json['createdAt'] as String),
    isMine: json['isMine'] as bool? ?? true,
  );
}

class TechReport {
  const TechReport({
    required this.id,
    required this.text,
    required this.createdAt,
  });

  final String id;
  final String text;
  final DateTime createdAt;

  Map<String, Object?> toJson() => {
    'id': id,
    'text': text,
    'createdAt': createdAt.toIso8601String(),
  };

  factory TechReport.fromJson(Map<String, Object?> json) => TechReport(
    id: json['id'] as String,
    text: json['text'] as String,
    createdAt: DateTime.parse(json['createdAt'] as String),
  );
}
