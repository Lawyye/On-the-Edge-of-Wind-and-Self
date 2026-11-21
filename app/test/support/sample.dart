import 'package:edgewind/domain/models.dart';

/// Правдоподобные данные для проверки экранов. Тексты придуманы.
final now = DateTime.now();

final me = Profile(
  id: 'me',
  nickname: 'Звезда',
  avatarKind: 1,
  createdAt: DateTime(2026, 11, 20),
  streakDays: 4,
);

final stories = [
  Story(
    id: 'a1',
    category: 'Одиночество',
    title: 'Переехал в новый город и не с кем поговорить',
    body: 'Прошло два месяца. На парах все уже с кем-то, а я как будто опоздал на первую неделю '
        'и теперь не знаю, как в это войти. Вроде и не грустно, но вечером тихо так, что слышно холодильник.',
    createdAt: now.subtract(const Duration(hours: 2)),
    authorId: 'u1',
    authorNickname: 'Полынь',
    authorAvatarKind: 3,
    likes: 7,
    responseCount: 4,
  ),
  Story(
    id: 'b2',
    category: 'Учёба',
    title: 'Боюсь сказать родителям, что бросил универ',
    body: 'Забрал документы ещё в октябре. Каждое утро выхожу из дома как будто на пары и сижу в библиотеке. '
        'Чем дольше молчу, тем страшнее.',
    createdAt: now.subtract(const Duration(days: 1, hours: 3)),
    authorId: 'u2',
    authorNickname: 'Мятный Чай',
    authorAvatarKind: 0,
    likes: 23,
    responseCount: 12,
    likedByMe: true,
    savedByMe: true,
  ),
  Story(
    id: 'c3',
    category: 'Тревога',
    title: 'Каждое утро сердце колотится, как перед экзаменом',
    body: 'Ничего не случилось, а внутри будто сирена. Дышу по квадрату, помогает минут на десять.',
    createdAt: now.subtract(const Duration(hours: 5)),
    authorId: 'u3',
    authorNickname: 'Северный Ветер',
    authorAvatarKind: 2,
    likes: 4,
    responseCount: 2,
  ),
  Story(
    id: 'd4',
    category: 'Перемены',
    title: 'Сегодня первый раз за месяц вышла на улицу',
    body: 'Просто дошла до магазина и обратно. Звучит смешно, но для меня это много.',
    createdAt: now.subtract(const Duration(minutes: 40)),
    authorId: 'me',
    authorNickname: 'Звезда',
    authorAvatarKind: 1,
    likes: 3,
    responseCount: 1,
    isMine: true,
  ),
  Story(
    id: 'e5',
    category: 'Отношения',
    title: 'Подруга перестала отвечать, и я не понимаю почему',
    body: 'Мы дружили с пятого класса. Сообщения прочитаны, ответа нет уже две недели.',
    createdAt: now.subtract(const Duration(days: 3)),
    authorId: 'u5',
    authorNickname: 'Тихая Сова',
    authorAvatarKind: 4,
    likes: 11,
    responseCount: 6,
  ),
];

final achievements = [
  Achievement(code: 'first_step', title: 'Первый шаг', description: 'Оставить первую историю.', goal: 1, progress: 1, earnedAt: DateTime(2026, 11, 21)),
  const Achievement(code: 'nearby', title: 'Я рядом', description: 'Десять раз поддержать чужую историю.', goal: 10, progress: 4),
  const Achievement(code: 'week_here', title: 'Неделя здесь', description: 'Заходить семь дней подряд.', goal: 7, progress: 4),
  const Achievement(code: 'heard', title: 'Услышали', description: 'Твою историю поддержали пять человек.', goal: 5, progress: 3),
  const Achievement(code: 'own_person', title: 'Свой человек', description: 'Ответить пятидесяти людям.', goal: 50, progress: 12),
  Achievement(code: 'night_shift', title: 'Ночная смена', description: 'Ответить кому-то между полуночью и пятью утра.', goal: 1, progress: 1, earnedAt: DateTime(2026, 11, 22)),
];

final inbox = [
  InboxItem(
    id: 'r1',
    body: 'Я тоже так выходила в первый раз — до подъезда и обратно. Это не смешно, это огромный шаг.',
    createdAt: now.subtract(const Duration(minutes: 12)),
    authorNickname: 'Тихая Сова',
    authorAvatarKind: 4,
    storyId: 'd4',
    storyTitle: 'Сегодня первый раз за месяц вышла на улицу',
  ),
  InboxItem(
    id: 'r2',
    body: 'Горжусь тобой. Завтра можно до парка.',
    createdAt: now.subtract(const Duration(hours: 6)),
    authorNickname: 'Полынь',
    authorAvatarKind: 3,
    storyId: 'd4',
    storyTitle: 'Сегодня первый раз за месяц вышла на улицу',
  ),
];

final responses = [
  StoryResponse(
    id: 's1',
    storyId: 'a1',
    body: 'Я переехала год назад, первые месяцы было ровно так же. Помогло не искать сразу друзей, '
        'а найти место, куда ходишь регулярно: секция, клуб, хоть библиотека.',
    createdAt: now.subtract(const Duration(hours: 1, minutes: 20)),
    authorId: 'u5',
    authorNickname: 'Тихая Сова',
    authorAvatarKind: 4,
  ),
  StoryResponse(
    id: 's2',
    storyId: 'a1',
    body: 'Ты не опоздал. Люди на парах тоже часто просто держатся за первого попавшегося.',
    createdAt: now.subtract(const Duration(minutes: 50)),
    authorId: 'u3',
    authorNickname: 'Северный Ветер',
    authorAvatarKind: 2,
  ),
  StoryResponse(
    id: 's3',
    storyId: 'a1',
    body: 'Напиши, в каком ты городе, — может, найдутся свои.',
    createdAt: now.subtract(const Duration(minutes: 8)),
    authorId: 'me',
    authorNickname: 'Звезда',
    authorAvatarKind: 1,
    isMine: true,
  ),
];
