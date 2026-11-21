import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

import '../data/edgewind_store.dart';
import '../domain/categories.dart';
import '../domain/models.dart';
import '../ui/term.dart';
import '../ui/time_text.dart';
import '../ui/tokens.dart';
import '../widgets/avatar.dart';
import '../widgets/story_card.dart';
import 'compose_sheet.dart';
import 'story_screen.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key, required this.store, required this.onToggleTheme});

  final EdgewindStore store;
  final VoidCallback onToggleTheme;

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  int _tab = 0;

  EdgewindStore get _store => widget.store;

  Future<void> _compose() async {
    final created = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      backgroundColor: Colors.transparent,
      builder: (_) => ComposeSheet(store: _store),
    );
    if (created == true && mounted) {
      setState(() => _tab = 0);
      ScaffoldMessenger.of(context)
          .showSnackBar(const SnackBar(content: Text('ЗАПИСЬ ОПУБЛИКОВАНА')));
    }
  }

  Future<void> _openStory(Story story) async {
    await Navigator.of(context).push(
      MaterialPageRoute(builder: (_) => StoryScreen(store: _store, storyId: story.id)),
    );
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _store,
      builder: (context, _) {
        final heads = [
          ('ЛЕНТА', '${_store.stories.length} ЗАПИСЕЙ', 'ОБЩАЯ ДЛЯ ВСЕХ'),
          ('АРХИВ', '${_store.savedStories.length} СОХРАНЕНО', 'ВИДНО ТОЛЬКО ТЕБЕ'),
          ('ОТКЛИК', '${_store.inbox.length} ВСЕГО', 'НА ТВОИ ЗАПИСИ'),
          ('ПРОФИЛЬ', '${_store.earnedCount} / ${_store.achievements.length}', 'ДОСТИЖЕНИЙ'),
        ];
        final head = heads[_tab];

        return TermScreen(
          child: Column(
            children: [
              SafeArea(bottom: false, child: _TopBar(onToggleTheme: widget.onToggleTheme)),
              _Header(title: head.$1, count: head.$2, sub: head.$3),
              Expanded(
                child: RefreshIndicator(
                  color: context.c.amber,
                  backgroundColor: context.c.panel,
                  onRefresh: _store.refreshAll,
                  child: IndexedStack(
                    index: _tab,
                    children: [
                      _FeedTab(store: _store, onOpen: _openStory, onCompose: _compose),
                      _SavedTab(store: _store, onOpen: _openStory),
                      _InboxTab(store: _store, onOpen: _openStory),
                      _ProfileTab(store: _store),
                    ],
                  ),
                ),
              ),
              _BottomBar(
                tab: _tab,
                onTab: (i) => setState(() => _tab = i),
                onCompose: _compose,
              ),
            ],
          ),
        );
      },
    );
  }
}

class _TopBar extends StatelessWidget {
  const _TopBar({required this.onToggleTheme});
  final VoidCallback onToggleTheme;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    final dark = Theme.of(context).brightness == Brightness.dark;
    return Padding(
      padding: const EdgeInsets.fromLTRB(18, 10, 18, 0),
      child: Row(
        children: [
          Container(
            width: 7,
            height: 7,
            decoration: BoxDecoration(
              color: c.phos,
              boxShadow: [BoxShadow(color: c.phos.withValues(alpha: 0.8), blurRadius: 7)],
            ),
          ),
          const SizedBox(width: 8),
          const TermLabel('ON THE EDGE'),
          const SizedBox(width: 10),
          Expanded(child: Container(height: 1, color: c.line)),
          const SizedBox(width: 10),
          InkResponse(
            onTap: onToggleTheme,
            radius: 20,
            child: Icon(
              dark ? Icons.light_mode_outlined : Icons.dark_mode_outlined,
              size: 17,
              color: c.muted,
            ),
          ),
        ],
      ),
    );
  }
}

class _Header extends StatelessWidget {
  const _Header({required this.title, required this.count, required this.sub});
  final String title;
  final String count;
  final String sub;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    return Padding(
      padding: const EdgeInsets.fromLTRB(18, 18, 18, 0),
      child: Column(
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Text(
                title,
                style: GoogleFonts.jetBrainsMono(
                  fontSize: 29,
                  height: 1,
                  fontWeight: FontWeight.w700,
                  letterSpacing: 5,
                  color: c.amber,
                ),
              ),
              const Spacer(),
              Column(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  TermLabel(count, color: c.amber),
                  const SizedBox(height: 3),
                  TermLabel(sub),
                ],
              ),
            ],
          ),
          const SizedBox(height: 11),
          const TermRule(),
        ],
      ),
    );
  }
}

// ── ЛЕНТА ─────────────────────────────────────────────────────────────────

class _FeedTab extends StatelessWidget {
  const _FeedTab({required this.store, required this.onOpen, required this.onCompose});

  final EdgewindStore store;
  final ValueChanged<Story> onOpen;
  final VoidCallback onCompose;

  @override
  Widget build(BuildContext context) {
    final cats = [EdgewindStore.kAllCategories, ...storyCategories];
    return Column(
      children: [
        SizedBox(
          height: 48,
          child: ListView.separated(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.fromLTRB(18, 14, 18, 0),
            itemCount: cats.length,
            separatorBuilder: (_, __) => const SizedBox(width: 6),
            itemBuilder: (_, i) => _Chip(
              label: cats[i],
              on: store.category == cats[i],
              onTap: () => store.setCategory(cats[i]),
            ),
          ),
        ),
        Expanded(
          child: store.stories.isEmpty
              ? _Empty(
                  title: 'НЕТ ЗАПИСЕЙ',
                  text: store.category == EdgewindStore.kAllCategories
                      ? 'Пока пусто. Можно оставить первую — её увидят те, кому это близко.'
                      : 'В этой теме пока пусто. Можно оставить первую.',
                  action: 'НАПИСАТЬ',
                  onAction: onCompose,
                )
              : ListView.separated(
                  padding: const EdgeInsets.fromLTRB(18, 14, 18, 24),
                  itemCount: store.stories.length,
                  separatorBuilder: (_, __) => const SizedBox(height: 11),
                  itemBuilder: (_, i) {
                    final s = store.stories[i];
                    return StoryCard(
                      story: s,
                      index: i,
                      onTap: () => onOpen(s),
                      onLike: () => store.toggleLike(s.id),
                    );
                  },
                ),
        ),
      ],
    );
  }
}

class _Chip extends StatelessWidget {
  const _Chip({required this.label, required this.on, required this.onTap});
  final String label;
  final bool on;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    return GestureDetector(
      onTap: onTap,
      child: Container(
        height: 28,
        padding: const EdgeInsets.symmetric(horizontal: 11),
        alignment: Alignment.center,
        decoration: BoxDecoration(
          border: Border.all(color: on ? c.amber : c.line),
          color: on ? c.amber.withValues(alpha: 0.08) : null,
        ),
        child: TermLabel(label, color: on ? c.amber : c.muted, size: 10, tracking: 1.2),
      ),
    );
  }
}

// ── АРХИВ ─────────────────────────────────────────────────────────────────

class _SavedTab extends StatelessWidget {
  const _SavedTab({required this.store, required this.onOpen});
  final EdgewindStore store;
  final ValueChanged<Story> onOpen;

  @override
  Widget build(BuildContext context) {
    if (store.savedStories.isEmpty) {
      return const _Empty(
        title: 'АРХИВ ПУСТ',
        text: 'Сюда попадает то, к чему захочется вернуться. Видно только тебе.',
      );
    }
    return ListView.separated(
      padding: const EdgeInsets.fromLTRB(18, 16, 18, 24),
      itemCount: store.savedStories.length,
      separatorBuilder: (_, __) => const SizedBox(height: 11),
      itemBuilder: (_, i) {
        final s = store.savedStories[i];
        return StoryCard(
          story: s,
          index: i,
          onTap: () => onOpen(s),
          onLike: () => store.toggleLike(s.id),
          onSave: () => store.toggleSaved(s.id),
        );
      },
    );
  }
}

// ── ОТКЛИКИ ───────────────────────────────────────────────────────────────

class _InboxTab extends StatelessWidget {
  const _InboxTab({required this.store, required this.onOpen});
  final EdgewindStore store;
  final ValueChanged<Story> onOpen;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    if (store.inbox.isEmpty) {
      return const _Empty(
        title: 'ТИХО',
        text: 'Здесь появятся отклики на твои записи.',
      );
    }
    return ListView.separated(
      padding: const EdgeInsets.fromLTRB(18, 16, 18, 24),
      itemCount: store.inbox.length,
      separatorBuilder: (_, __) => const SizedBox(height: 9),
      itemBuilder: (_, i) {
        final item = store.inbox[i];
        return Pane(
          onTap: () {
            final s = store.storyById(item.storyId);
            if (s != null) onOpen(s);
          },
          padding: const EdgeInsets.all(14),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Avatar(
                    kind: item.authorAvatarKind,
                    url: item.authorAvatarUrl,
                    size: 26,
                  ),
                  const SizedBox(width: 9),
                  Expanded(
                    child: TermLabel(
                      item.authorNickname,
                      color: c.amber,
                      size: 10.5,
                      tracking: 1.3,
                      weight: FontWeight.w700,
                    ),
                  ),
                  TermLabel(timeText(item.createdAt)),
                ],
              ),
              const SizedBox(height: 9),
              Text(
                item.body,
                maxLines: 3,
                overflow: TextOverflow.ellipsis,
                style: Theme.of(context).textTheme.bodyMedium,
              ),
              const SizedBox(height: 9),
              TermLabel('К ЗАПИСИ «${item.storyTitle}»', size: 9, tracking: 0.8),
            ],
          ),
        );
      },
    );
  }
}

// ── ПРОФИЛЬ ───────────────────────────────────────────────────────────────

class _ProfileTab extends StatelessWidget {
  const _ProfileTab({required this.store});
  final EdgewindStore store;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    final p = store.profile;
    if (p == null) return const SizedBox.shrink();

    return ListView(
      padding: const EdgeInsets.fromLTRB(18, 16, 18, 24),
      children: [
        Pane(
          padding: const EdgeInsets.all(16),
          child: Row(
            children: [
              Avatar(kind: p.avatarKind, url: p.avatarUrl, size: 56, lit: true),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      p.nickname,
                      style: GoogleFonts.literata(
                        fontSize: 20,
                        fontWeight: FontWeight.w600,
                        color: c.bone,
                      ),
                    ),
                    const SizedBox(height: 5),
                    const TermLabel('ИМЯ НЕНАСТОЯЩЕЕ — И ХОРОШО'),
                    const SizedBox(height: 9),
                    Row(
                      children: [
                        TermLabel('${store.myStories.length} ИСТОРИЙ', color: c.amber),
                        const SizedBox(width: 12),
                        TermLabel('${p.streakDays} ДНЕЙ', color: c.amber),
                      ],
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 20),
        Row(
          children: [
            const TermLabel('ДОСТИЖЕНИЯ', tracking: 2.2),
            const SizedBox(width: 10),
            Expanded(child: Container(height: 1, color: c.line)),
            const SizedBox(width: 10),
            TermLabel(
              '${store.earnedCount.toString().padLeft(2, '0')} / '
              '${store.achievements.length.toString().padLeft(2, '0')}',
              color: c.amber,
            ),
          ],
        ),
        const SizedBox(height: 12),
        for (final a in store.achievements) ...[
          _AchievementRow(a: a),
          const SizedBox(height: 8),
        ],
        const SizedBox(height: 10),
        Container(
          padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(border: Border.all(color: c.line)),
          child: Row(
            children: [
              Container(width: 3, height: 32, color: c.rust),
              const SizedBox(width: 11),
              Expanded(
                child: TermLabel(
                  'ЗДЕСЬ ОТВЕЧАЮТ ТАКИЕ ЖЕ ЛЮДИ, НЕ ВРАЧИ.\n'
                  'ЕСТЬ УГРОЗА ЖИЗНИ — ЗВОНИ В ЭКСТРЕННУЮ СЛУЖБУ.',
                  size: 10,
                  tracking: 0.6,
                  color: c.muted,
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }
}

class _AchievementRow extends StatelessWidget {
  const _AchievementRow({required this.a});
  final Achievement a;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    final ink = a.earned ? c.amber : c.faint;

    return Pane(
      padding: const EdgeInsets.all(13),
      cornerColor: a.earned ? c.amberDim : c.line,
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: 36,
            height: 36,
            alignment: Alignment.center,
            decoration: BoxDecoration(
              border: Border.all(color: a.earned ? c.amberDim : c.line),
              color: a.earned ? c.amber.withValues(alpha: 0.07) : null,
            ),
            child: a.earned
                ? Icon(Icons.check, size: 17, color: c.amber)
                : Icon(Icons.lock_outline, size: 15, color: c.faint),
          ),
          const SizedBox(width: 13),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                TermLabel(a.title, color: ink, size: 11.5, tracking: 1.3, weight: FontWeight.w700),
                const SizedBox(height: 5),
                Text(a.description, style: Theme.of(context).textTheme.bodyMedium),
                if (!a.earned && a.goal > 1) ...[
                  const SizedBox(height: 9),
                  Row(
                    children: [
                      Expanded(
                        child: Container(
                          height: 3,
                          color: c.line,
                          child: FractionallySizedBox(
                            alignment: Alignment.centerLeft,
                            widthFactor: a.fraction,
                            child: Container(color: c.amber),
                          ),
                        ),
                      ),
                      const SizedBox(width: 9),
                      TermLabel('${a.progress}/${a.goal}', size: 9.5),
                    ],
                  ),
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }
}

// ── ОБЩЕЕ ─────────────────────────────────────────────────────────────────

class _Empty extends StatelessWidget {
  const _Empty({required this.title, required this.text, this.action, this.onAction});
  final String title;
  final String text;
  final String? action;
  final VoidCallback? onAction;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    return ListView(
      padding: const EdgeInsets.fromLTRB(18, 60, 18, 24),
      children: [
        Pane(
          padding: const EdgeInsets.symmetric(horizontal: 22, vertical: 30),
          child: Column(
            children: [
              TermLabel(title, tracking: 2.6, color: c.muted),
              const SizedBox(height: 12),
              Text(
                text,
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.bodyMedium,
              ),
              if (action != null) ...[
                const SizedBox(height: 20),
                GestureDetector(
                  onTap: onAction,
                  child: Container(
                    height: 44,
                    alignment: Alignment.center,
                    decoration: BoxDecoration(
                      border: Border.all(color: c.amber),
                      color: c.amber.withValues(alpha: 0.1),
                    ),
                    child: TermLabel(
                      action!,
                      color: c.amber,
                      size: 11,
                      tracking: 3,
                      weight: FontWeight.w700,
                    ),
                  ),
                ),
              ],
            ],
          ),
        ),
      ],
    );
  }
}

/// Нижняя панель. Кнопка записи стоит ВНУТРИ панели, а не висит над лентой:
/// плавающая перекрывала последнюю карточку.
class _BottomBar extends StatelessWidget {
  const _BottomBar({required this.tab, required this.onTab, required this.onCompose});

  final int tab;
  final ValueChanged<int> onTab;
  final VoidCallback onCompose;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    return Container(
      decoration: BoxDecoration(
        color: c.voidBg,
        border: Border(top: BorderSide(color: c.line)),
      ),
      child: SafeArea(
        top: false,
        child: SizedBox(
          height: 64,
          child: Row(
            children: [
              _NavItem(label: 'ЛЕНТА', on: tab == 0, onTap: () => onTab(0)),
              _NavItem(label: 'АРХИВ', on: tab == 1, onTap: () => onTab(1)),
              SizedBox(
                width: 68,
                child: Center(
                  child: GestureDetector(
                    onTap: onCompose,
                    child: Container(
                      width: 44,
                      height: 44,
                      decoration: BoxDecoration(
                        border: Border.all(color: c.amber),
                        color: c.amber.withValues(alpha: 0.1),
                        boxShadow: [
                          BoxShadow(color: c.amber.withValues(alpha: 0.22), blurRadius: 14),
                        ],
                      ),
                      child: Icon(Icons.add, size: 20, color: c.amber),
                    ),
                  ),
                ),
              ),
              _NavItem(label: 'ОТКЛИК', on: tab == 2, onTap: () => onTab(2)),
              _NavItem(label: 'ПРОФИЛЬ', on: tab == 3, onTap: () => onTab(3)),
            ],
          ),
        ),
      ),
    );
  }
}

class _NavItem extends StatelessWidget {
  const _NavItem({required this.label, required this.on, required this.onTap});
  final String label;
  final bool on;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    return Expanded(
      child: InkResponse(
        onTap: onTap,
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            AnimatedContainer(
              duration: const Duration(milliseconds: 180),
              width: 16,
              height: 2,
              decoration: BoxDecoration(
                color: on ? c.amber : Colors.transparent,
                boxShadow: on
                    ? [BoxShadow(color: c.amber.withValues(alpha: 0.8), blurRadius: 8)]
                    : null,
              ),
            ),
            const SizedBox(height: 7),
            TermLabel(label, color: on ? c.amber : c.faint, size: 9.5, tracking: 1.3),
          ],
        ),
      ),
    );
  }
}
