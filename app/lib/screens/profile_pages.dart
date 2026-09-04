import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';

import '../data/edgewind_store.dart';
import '../domain/models.dart';
import '../ui/time_text.dart';
import '../widgets/story_card.dart';
import 'story_sheet.dart';

class StoryListPage extends StatelessWidget {
  const StoryListPage({
    super.key,
    required this.store,
    required this.title,
    required this.emptyText,
    required this.stories,
  });

  final EdgewindStore store;
  final String title;
  final String emptyText;
  final List<Story> Function() stories;

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: store,
      builder: (context, _) {
        final items = stories();
        return Scaffold(
          appBar: AppBar(title: Text(title)),
          body: items.isEmpty
              ? EmptyPage(text: emptyText)
              : ListView.separated(
                  physics: const BouncingScrollPhysics(),
                  padding: const EdgeInsets.fromLTRB(18, 14, 18, 32),
                  itemCount: items.length,
                  separatorBuilder: (_, _) => const SizedBox(height: 12),
                  itemBuilder: (context, index) {
                    final story = items[index];
                    return StoryCard(
                      story: story,
                      store: store,
                      onTap: () =>
                          showStorySheet(context, store: store, story: story),
                    );
                  },
                ),
        );
      },
    );
  }
}

class ResponsesPage extends StatelessWidget {
  const ResponsesPage({super.key, required this.store, this.withAppBar = true});

  final EdgewindStore store;
  final bool withAppBar;

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: store,
      builder: (context, _) {
        final responses = store.myResponses;
        final content = responses.isEmpty
            ? const EmptyPage(text: 'Ты пока никому не отвечал.')
            : ListView.separated(
                physics: const BouncingScrollPhysics(),
                padding: const EdgeInsets.fromLTRB(18, 14, 18, 110),
                itemCount: responses.length,
                separatorBuilder: (_, _) => const SizedBox(height: 12),
                itemBuilder: (context, index) {
                  final response = responses[index];
                  final story = store.storyById(response.storyId);
                  if (story == null) return const SizedBox.shrink();
                  return Card(
                    clipBehavior: Clip.antiAlias,
                    child: InkWell(
                      onTap: () =>
                          showStorySheet(context, store: store, story: story),
                      child: Padding(
                        padding: const EdgeInsets.all(18),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              story.title,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: const TextStyle(
                                color: storyMoss,
                                fontWeight: FontWeight.w700,
                              ),
                            ),
                            const SizedBox(height: 12),
                            Text(response.text),
                            const SizedBox(height: 10),
                            Text(
                              timeText(response.createdAt),
                              style: const TextStyle(
                                fontSize: 12,
                                color: Color(0xFF78867F),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  );
                },
              );
        if (!withAppBar) return SafeArea(bottom: false, child: content);
        return Scaffold(
          appBar: AppBar(title: const Text('Мои отклики')),
          body: content,
        );
      },
    );
  }
}

class ProfilePage extends StatelessWidget {
  const ProfilePage({super.key, required this.store});

  final EdgewindStore store;

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      bottom: false,
      child: ListView(
        physics: const BouncingScrollPhysics(),
        padding: const EdgeInsets.fromLTRB(20, 28, 20, 120),
        children: [
          Text('Профиль', style: Theme.of(context).textTheme.headlineLarge),
          const SizedBox(height: 8),
          const Text(
            'Здесь твои записи, отклики и настройки.',
            style: TextStyle(fontSize: 16, color: Color(0xFF637169)),
          ),
          const SizedBox(height: 24),
          _ProfileHeader(store: store, onAvatarTap: () => _pickAvatar(context)),
          const SizedBox(height: 16),
          _ProfileStats(store: store),
          const SizedBox(height: 28),
          Text('Достижения', style: Theme.of(context).textTheme.headlineSmall),
          const SizedBox(height: 12),
          _Achievements(store: store),
          const SizedBox(height: 28),
          ProfileTile(
            icon: Icons.article_outlined,
            title: 'Мои истории',
            subtitle: 'То, что ты написал',
            onTap: () => _open(
              context,
              StoryListPage(
                store: store,
                title: 'Мои истории',
                emptyText: 'Ты пока ничего не писал.',
                stories: () => store.myStories,
              ),
            ),
          ),
          ProfileTile(
            icon: Icons.forum_outlined,
            title: 'Мои отклики',
            subtitle: 'Твои разговоры',
            onTap: () => _open(context, ResponsesPage(store: store)),
          ),
          ProfileTile(
            icon: Icons.shield_outlined,
            title: 'Приватность',
            subtitle: 'Что можно показывать другим',
            onTap: () => _open(context, PrivacyPage(store: store)),
          ),
          ProfileTile(
            icon: Icons.info_outline_rounded,
            title: 'Об этом месте',
            subtitle: 'Коротко о проекте',
            onTap: () => _open(context, const AboutPage()),
          ),
          ProfileTile(
            icon: Icons.build_outlined,
            title: 'Что-то сломалось',
            subtitle: 'Написать о неполадке',
            onTap: () => _open(context, TechReportPage(store: store)),
          ),
        ],
      ),
    );
  }

  Future<void> _pickAvatar(BuildContext context) async {
    try {
      final image = await ImagePicker().pickImage(
        source: ImageSource.gallery,
        maxWidth: 720,
        maxHeight: 720,
        imageQuality: 78,
      );
      if (image == null) return;
      await store.setAvatar(await image.readAsBytes());
    } catch (_) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Не получилось открыть фото.')),
        );
      }
    }
  }

  void _open(BuildContext context, Widget page) {
    Navigator.push(context, MaterialPageRoute(builder: (_) => page));
  }
}

class _ProfileHeader extends StatelessWidget {
  const _ProfileHeader({required this.store, required this.onAvatarTap});

  final EdgewindStore store;
  final VoidCallback onAvatarTap;

  @override
  Widget build(BuildContext context) {
    final avatar = store.avatarBytes;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(18),
        child: Row(
          children: [
            Semantics(
              button: true,
              label: 'Выбрать аватар',
              child: InkWell(
                customBorder: const CircleBorder(),
                onTap: onAvatarTap,
                child: Stack(
                  clipBehavior: Clip.none,
                  children: [
                    CircleAvatar(
                      radius: 34,
                      backgroundColor: storyPaleMoss,
                      backgroundImage: avatar == null
                          ? null
                          : MemoryImage(avatar),
                      child: avatar == null
                          ? const Icon(
                              Icons.landscape_outlined,
                              size: 31,
                              color: storyMoss,
                            )
                          : null,
                    ),
                    Positioned(
                      right: -2,
                      bottom: -2,
                      child: Container(
                        width: 25,
                        height: 25,
                        decoration: BoxDecoration(
                          color: storyInk,
                          shape: BoxShape.circle,
                          border: Border.all(color: Colors.white, width: 2),
                        ),
                        child: const Icon(
                          Icons.edit_rounded,
                          size: 13,
                          color: Colors.white,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(width: 16),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Анонимный профиль',
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                  const SizedBox(height: 5),
                  Text(
                    'Первый вход · ${_dateText(store.joinedAt)}',
                    style: const TextStyle(color: Color(0xFF637169)),
                  ),
                  const SizedBox(height: 7),
                  const Text(
                    'Аватар виден только здесь. Записи остаются анонимными.',
                    style: TextStyle(fontSize: 12, color: Color(0xFF78867F)),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _ProfileStats extends StatelessWidget {
  const _ProfileStats({required this.store});

  final EdgewindStore store;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        _Stat(value: store.myStories.length, label: 'историй'),
        const SizedBox(width: 10),
        _Stat(value: store.myResponses.length, label: 'откликов'),
        const SizedBox(width: 10),
        _Stat(value: store.givenLikesCount, label: 'отметок'),
      ],
    );
  }
}

class _Stat extends StatelessWidget {
  const _Stat({required this.value, required this.label});

  final int value;
  final String label;

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 14, horizontal: 6),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(18),
          border: Border.all(color: const Color(0xFFE0E6E1)),
        ),
        child: Column(
          children: [
            Text(
              '$value',
              style: const TextStyle(
                color: storyInk,
                fontSize: 22,
                fontWeight: FontWeight.w700,
              ),
            ),
            const SizedBox(height: 2),
            FittedBox(
              fit: BoxFit.scaleDown,
              child: Text(
                label,
                style: const TextStyle(fontSize: 12, color: Color(0xFF6C7A72)),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _Achievements extends StatelessWidget {
  const _Achievements({required this.store});

  final EdgewindStore store;

  @override
  Widget build(BuildContext context) {
    final days = DateTime.now().difference(store.joinedAt).inDays;
    final items = [
      _Achievement(
        icon: Icons.edit_note_rounded,
        title: 'Первый шаг',
        subtitle: 'Оставить историю',
        unlocked: store.myStories.isNotEmpty,
      ),
      _Achievement(
        icon: Icons.waving_hand_outlined,
        title: 'Я рядом',
        subtitle: 'Написать отклик',
        unlocked: store.myResponses.isNotEmpty,
      ),
      _Achievement(
        icon: Icons.bookmark_added_outlined,
        title: 'Не потерять',
        subtitle: 'Сохранить 3 записи',
        unlocked: store.savedCount >= 3,
      ),
      _Achievement(
        icon: Icons.calendar_today_outlined,
        title: 'Неделя здесь',
        subtitle: 'Вернуться через 7 дней',
        unlocked: days >= 7,
      ),
    ];

    return LayoutBuilder(
      builder: (context, constraints) {
        final width = constraints.maxWidth < 350
            ? constraints.maxWidth
            : (constraints.maxWidth - 10) / 2;
        return Wrap(
          spacing: 10,
          runSpacing: 10,
          children: items
              .map((item) => SizedBox(width: width, child: item))
              .toList(),
        );
      },
    );
  }
}

class _Achievement extends StatelessWidget {
  const _Achievement({
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.unlocked,
  });

  final IconData icon;
  final String title;
  final String subtitle;
  final bool unlocked;

  @override
  Widget build(BuildContext context) {
    return AnimatedContainer(
      duration: const Duration(milliseconds: 250),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: unlocked ? storyPaleMoss : const Color(0xFFF0F2EF),
        borderRadius: BorderRadius.circular(20),
        border: Border.all(
          color: unlocked ? const Color(0xFFC7D8CB) : const Color(0xFFE0E4E0),
        ),
      ),
      child: Row(
        children: [
          Icon(
            unlocked ? icon : Icons.lock_outline_rounded,
            color: unlocked ? storyMoss : const Color(0xFF9AA49E),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(
                    fontWeight: FontWeight.w700,
                    color: unlocked ? storyInk : const Color(0xFF77817B),
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  subtitle,
                  maxLines: 2,
                  style: const TextStyle(
                    fontSize: 11,
                    height: 1.25,
                    color: Color(0xFF78827C),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

String _dateText(DateTime date) {
  const months = [
    'января',
    'февраля',
    'марта',
    'апреля',
    'мая',
    'июня',
    'июля',
    'августа',
    'сентября',
    'октября',
    'ноября',
    'декабря',
  ];
  return '${date.day} ${months[date.month - 1]} ${date.year}';
}

class PrivacyPage extends StatelessWidget {
  const PrivacyPage({super.key, required this.store});

  final EdgewindStore store;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Приватность')),
      body: ListView(
        physics: const BouncingScrollPhysics(),
        padding: const EdgeInsets.fromLTRB(18, 12, 18, 32),
        children: [
          const _PrivacyCard(
            icon: Icons.visibility_off_outlined,
            title: 'Истории без имени',
            text:
                'Аватар и данные профиля не показываются рядом с твоими записями.',
          ),
          const SizedBox(height: 12),
          const _PrivacyCard(
            icon: Icons.phonelink_lock_outlined,
            title: 'Пока всё на устройстве',
            text:
                'В этой тестовой версии записи, отклики и аватар хранятся только на твоём устройстве.',
          ),
          const SizedBox(height: 18),
          const Padding(
            padding: EdgeInsets.symmetric(horizontal: 4),
            child: Text(
              'Не пиши адрес, телефон и другие личные данные в самом тексте.',
              style: TextStyle(color: Color(0xFF637169)),
            ),
          ),
          const SizedBox(height: 32),
          OutlinedButton.icon(
            onPressed: () => _clear(context),
            icon: const Icon(Icons.delete_outline),
            label: const Text('Удалить мои данные'),
            style: OutlinedButton.styleFrom(
              foregroundColor: const Color(0xFFA64E48),
              padding: const EdgeInsets.symmetric(vertical: 16),
            ),
          ),
        ],
      ),
    );
  }

  Future<void> _clear(BuildContext context) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Удалить всё?'),
        content: const Text(
          'Записи, отклики и сохранённое исчезнут с этого устройства.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Отмена'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Удалить'),
          ),
        ],
      ),
    );
    if (confirmed == true) {
      await store.clearUserData();
      if (context.mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('Данные удалены.')));
      }
    }
  }
}

class _PrivacyCard extends StatelessWidget {
  const _PrivacyCard({
    required this.icon,
    required this.title,
    required this.text,
  });

  final IconData icon;
  final String title;
  final String text;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(18),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Container(
              width: 42,
              height: 42,
              decoration: BoxDecoration(
                color: storyPaleMoss,
                borderRadius: BorderRadius.circular(14),
              ),
              child: Icon(icon, color: storyMoss),
            ),
            const SizedBox(width: 14),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(title, style: Theme.of(context).textTheme.titleMedium),
                  const SizedBox(height: 7),
                  Text(text),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class AboutPage extends StatelessWidget {
  const AboutPage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Об этом месте')),
      body: ListView(
        physics: const BouncingScrollPhysics(),
        padding: const EdgeInsets.fromLTRB(24, 24, 24, 40),
        children: [
          ClipRRect(
            borderRadius: BorderRadius.circular(26),
            child: Image.asset(
              'assets/images/quiet-field.png',
              height: 210,
              fit: BoxFit.cover,
            ),
          ),
          const SizedBox(height: 26),
          Text('On the Edge', style: Theme.of(context).textTheme.headlineLarge),
          const SizedBox(height: 18),
          Text(
            'Иногда просто нужен разговор. Для этого всё и сделано.',
            style: Theme.of(context).textTheme.bodyLarge,
          ),
          const SizedBox(height: 14),
          Text(
            'Можно написать свою историю, ответить другому человеку или просто почитать.',
            style: Theme.of(context).textTheme.bodyLarge,
          ),
          const SizedBox(height: 28),
          const Text(
            'Если тебе прямо сейчас угрожает опасность, обратись в местную экстренную службу.',
            style: TextStyle(color: Color(0xFF637169), height: 1.45),
          ),
        ],
      ),
    );
  }
}

class TechReportPage extends StatefulWidget {
  const TechReportPage({super.key, required this.store});

  final EdgewindStore store;

  @override
  State<TechReportPage> createState() => _TechReportPageState();
}

class _TechReportPageState extends State<TechReportPage> {
  final _text = TextEditingController();
  bool _saving = false;

  @override
  void dispose() {
    _text.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Что сломалось?')),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          const Text('Напиши, что нажал и что произошло.'),
          const SizedBox(height: 18),
          TextField(
            controller: _text,
            enabled: !_saving,
            minLines: 6,
            maxLines: 10,
            maxLength: 1500,
            decoration: const InputDecoration(
              hintText: 'Например: нажимаю «Отклики», а экран не открывается…',
            ),
          ),
          const SizedBox(height: 10),
          FilledButton(
            onPressed: _saving ? null : _submit,
            child: const Text('Отправить'),
          ),
        ],
      ),
    );
  }

  Future<void> _submit() async {
    if (_text.text.trim().length < 5) return;
    setState(() => _saving = true);
    await widget.store.addTechReport(_text.text);
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Спасибо. Сообщение сохранено.')),
      );
      Navigator.pop(context);
    }
  }
}

class ProfileTile extends StatelessWidget {
  const ProfileTile({
    super.key,
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.onTap,
  });

  final IconData icon;
  final String title;
  final String subtitle;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      child: ListTile(
        contentPadding: const EdgeInsets.symmetric(horizontal: 18, vertical: 8),
        leading: Container(
          width: 42,
          height: 42,
          decoration: BoxDecoration(
            color: storyPaleMoss,
            borderRadius: BorderRadius.circular(14),
          ),
          child: Icon(icon, color: storyMoss),
        ),
        title: Text(title, style: const TextStyle(fontWeight: FontWeight.w700)),
        subtitle: Text(subtitle),
        trailing: const Icon(Icons.chevron_right_rounded),
        onTap: onTap,
      ),
    );
  }
}

class EmptyPage extends StatelessWidget {
  const EmptyPage({super.key, required this.text});

  final String text;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.air_rounded, size: 48, color: Color(0xFFB9C8BD)),
            const SizedBox(height: 16),
            Text(
              text,
              textAlign: TextAlign.center,
              style: const TextStyle(fontSize: 16, color: Color(0xFF637169)),
            ),
          ],
        ),
      ),
    );
  }
}
