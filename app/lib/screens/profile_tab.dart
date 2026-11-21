import 'package:flutter/material.dart';

import '../data/edgewind_store.dart';
import '../domain/categories.dart';
import '../domain/models.dart';
import '../ui/format.dart';
import '../ui/hero_header.dart';
import '../ui/tokens.dart';
import '../ui/type.dart';
import '../ui/widgets.dart';
import '../widgets/achievement_badge.dart';
import '../widgets/avatar.dart';
import '../widgets/story_tile.dart';
import 'edit_profile_sheet.dart';

/// Профиль: ромашки на чёрном, знак и имя, достижения как значки, свои
/// записи и настройки. Удаление аккаунта — здесь же, внизу, с подтверждением.
class ProfileTab extends StatelessWidget {
  const ProfileTab({
    super.key,
    required this.store,
    required this.onOpen,
    required this.onToggleTheme,
    required this.onHelp,
  });

  final EdgewindStore store;
  final ValueChanged<Story> onOpen;
  final VoidCallback onToggleTheme;
  final VoidCallback onHelp;

  Future<void> _edit(BuildContext context) => showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    backgroundColor: context.c.panel,
    shape: RoundedRectangleBorder(borderRadius: BorderRadius.zero, side: BorderSide(color: context.c.line)),
    builder: (_) => EditProfileSheet(store: store),
  );

  Future<void> _storyMenu(BuildContext context, Story s) async {
    final choice = await showMenuSheet<String>(
      context,
      title: s.title.toUpperCase(),
      entries: const [
        MenuEntry('open', 'Открыть', Icons.open_in_new_rounded),
        MenuEntry('delete', 'Удалить запись', Icons.delete_outline_rounded, danger: true),
      ],
    );
    if (!context.mounted) return;
    if (choice == 'open') onOpen(s);
    if (choice == 'delete') await deleteStoryFlow(context, store, s);
  }

  Future<void> _deleteAccount(BuildContext context) async {
    final yes = await confirm(
      context,
      title: 'УДАЛИТЬ АККАУНТ?',
      text: 'Исчезнут имя, знак, фото, все твои истории и отклики, архив и достижения. '
          'Вернуть это будет нельзя.',
      action: 'УДАЛИТЬ',
      danger: true,
    );
    if (!yes || !context.mounted) return;
    try {
      await store.deleteAccount();
    } catch (e) {
      store.say(store.describe(e), error: true);
    }
  }

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    final p = store.profile;
    if (p == null) return const SizedBox.shrink();
    final mine = store.myStories;
    final ach = store.achievements;
    final width = MediaQuery.sizeOf(context).width;
    final columns = width >= 560 ? 3 : 2;

    return RefreshIndicator(
      color: c.violet,
      backgroundColor: c.panel,
      edgeOffset: MediaQuery.paddingOf(context).top + 56,
      onRefresh: () => store.refreshAll(),
      child: CustomScrollView(
        physics: const AlwaysScrollableScrollPhysics(parent: BouncingScrollPhysics()),
        slivers: [
          HeroHeader(
            image: kDaisies,
            alignment: const Alignment(-0.2, 0.55),
            title: p.nickname.toUpperCase(),
            titleSize: p.nickname.length > 11 ? 24 : 32,
            jp: '自分',
            expandedHeight: 330,
            caption: 'ТЫ ЗДЕСЬ С ${fullDate(p.createdAt)}',
            actions: [
              IconAction(icon: Icons.edit_outlined, tooltip: 'Изменить имя и знак', onTap: () => _edit(context), color: c.onArt),
            ],
            bottom: GestureDetector(
              onTap: () => _edit(context),
              child: Row(
                children: [
                  Avatar(kind: p.avatarKind, url: p.avatarUrl, size: 52, lit: true, onArt: true),
                  const SizedBox(width: 16),
                  Expanded(
                    child: Wrap(
                      spacing: 18,
                      runSpacing: 8,
                      children: [
                        _Stat(value: '${mine.length}', label: plural(mine.length, 'ИСТОРИЯ', 'ИСТОРИИ', 'ИСТОРИЙ')),
                        _Stat(value: '${p.streakDays}', label: plural(p.streakDays, 'ДЕНЬ ПОДРЯД', 'ДНЯ ПОДРЯД', 'ДНЕЙ ПОДРЯД')),
                        _Stat(value: '${store.earnedCount}/${ach.isEmpty ? 6 : ach.length}', label: 'ЗНАЧКИ'),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ),
          SliverPadding(
            padding: const EdgeInsets.fromLTRB(20, 22, 20, 14),
            sliver: SliverToBoxAdapter(
              child: SectionLabel('ДОСТИЖЕНИЯ', trailing: '${twoDigits(store.earnedCount)} / ${twoDigits(ach.length)}'),
            ),
          ),
          if (ach.isEmpty)
            const SliverToBoxAdapter(child: Padding(padding: EdgeInsets.all(20), child: Center(child: LoadingLine())))
          else
            SliverPadding(
              padding: const EdgeInsets.symmetric(horizontal: 20),
              sliver: SliverList.separated(
                itemCount: (ach.length / columns).ceil(),
                separatorBuilder: (_, __) => const SizedBox(height: 10),
                // Высота строки — по самой длинной плитке в ней: крупный
                // системный шрифт не обрезает описание достижения.
                itemBuilder: (context, row) => IntrinsicHeight(
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      for (var k = 0; k < columns; k++) ...[
                        if (k > 0) const SizedBox(width: 10),
                        Expanded(
                          child: row * columns + k < ach.length
                              ? AchievementBadge(a: ach[row * columns + k])
                              : const SizedBox.shrink(),
                        ),
                      ],
                    ],
                  ),
                ),
              ),
            ),
          SliverPadding(
            padding: const EdgeInsets.fromLTRB(20, 30, 20, 4),
            sliver: SliverToBoxAdapter(
              child: SectionLabel('МОИ ЗАПИСИ', trailing: twoDigits(mine.length)),
            ),
          ),
          if (mine.isEmpty)
            SliverToBoxAdapter(
              child: Padding(
                padding: const EdgeInsets.fromLTRB(20, 14, 20, 6),
                child: Text(
                  'Пока ни одной. Первая запись откроет значок «Первый шаг».',
                  style: T.body(c.textDim, size: 14.5),
                ),
              ),
            )
          else
            SliverList.separated(
              itemCount: mine.length,
              separatorBuilder: (_, __) => const Hairline(indent: 74),
              itemBuilder: (context, i) => GestureDetector(
                onLongPress: () => _storyMenu(context, mine[i]),
                child: StoryTile(story: mine[i], onTap: () => onOpen(mine[i])),
              ),
            ),
          const SliverPadding(
            padding: EdgeInsets.fromLTRB(20, 30, 20, 8),
            sliver: SliverToBoxAdapter(child: SectionLabel('НАСТРОЙКИ')),
          ),
          SliverToBoxAdapter(
            child: Column(
              children: [
                _SettingRow(
                  icon: c.isDark ? Icons.light_mode_outlined : Icons.dark_mode_outlined,
                  text: c.isDark ? 'Светлая тема' : 'Тёмная тема',
                  onTap: onToggleTheme,
                ),
                _SettingRow(icon: Icons.support_outlined, text: 'Если тебе очень плохо', onTap: onHelp, accent: c.lamp),
                _SettingRow(icon: Icons.edit_outlined, text: 'Имя, знак и фото', onTap: () => _edit(context)),
                _SettingRow(
                  icon: Icons.person_remove_outlined,
                  text: 'Удалить аккаунт',
                  onTap: () => _deleteAccount(context),
                  accent: c.alarm,
                ),
              ],
            ),
          ),
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(20, 22, 20, 40),
              child: Note(
                text: 'АККАУНТ ЖИВЁТ НА ЭТОМ ТЕЛЕФОНЕ. ЕСЛИ УДАЛИТЬ ПРИЛОЖЕНИЕ ИЛИ СМЕНИТЬ ТЕЛЕФОН, '
                    'ВОЙТИ В НЕГО СНОВА НЕ ПОЛУЧИТСЯ — ТАКАЯ ЦЕНА ПОЛНОЙ АНОНИМНОСТИ.',
                color: c.violet,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// Удаление своей истории с подтверждением. Общее для профиля и экрана истории.
Future<bool> deleteStoryFlow(BuildContext context, EdgewindStore store, Story s) async {
  final yes = await confirm(
    context,
    title: 'УДАЛИТЬ ЗАПИСЬ?',
    text: '«${s.title}» исчезнет вместе со всеми откликами на неё. Вернуть её будет нельзя.',
    action: 'УДАЛИТЬ',
    danger: true,
  );
  if (!yes) return false;
  try {
    await store.deleteStory(s.id);
    store.say('Запись удалена.');
    return true;
  } catch (e) {
    store.say(store.describe(e), error: true);
    return false;
  }
}

class _Stat extends StatelessWidget {
  const _Stat({required this.value, required this.label});

  final String value;
  final String label;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        Text(value, style: T.label(c.onArt, size: 17, tracking: 1, weight: FontWeight.w700)),
        const SizedBox(height: 3),
        Text(label, style: T.label(c.onArt.withValues(alpha: 0.6), size: 8.5, tracking: 1.3)),
      ],
    );
  }
}

class _SettingRow extends StatelessWidget {
  const _SettingRow({required this.icon, required this.text, required this.onTap, this.accent});

  final IconData icon;
  final String text;
  final VoidCallback onTap;
  final Color? accent;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    final ink = accent ?? c.text;
    return InkWell(
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 15),
        child: Row(
          children: [
            Icon(icon, size: 19, color: accent ?? c.textDim),
            const SizedBox(width: 16),
            Expanded(child: Text(text, style: T.body(ink, size: 15.5, height: 1.3))),
            Icon(Icons.chevron_right_rounded, size: 18, color: c.textFaint),
          ],
        ),
      ),
    );
  }
}
