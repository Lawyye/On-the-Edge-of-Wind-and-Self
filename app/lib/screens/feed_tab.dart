import 'package:flutter/material.dart';

import '../data/edgewind_store.dart';
import '../domain/categories.dart';
import '../domain/models.dart';
import '../ui/format.dart';
import '../ui/hero_header.dart';
import '../ui/tokens.dart';
import '../ui/type.dart';
import '../ui/widgets.dart';
import '../widgets/story_tile.dart';

/// Лента: провода на закате, под ними — общие истории. Темы закреплены под
/// шапкой, чтобы переключаться, не пролистывая наверх.
class FeedTab extends StatefulWidget {
  const FeedTab({super.key, required this.store, required this.onOpen, required this.onCompose, required this.onHelp});

  final EdgewindStore store;
  final ValueChanged<Story> onOpen;
  final VoidCallback onCompose;
  final VoidCallback onHelp;

  @override
  State<FeedTab> createState() => _FeedTabState();
}

class _FeedTabState extends State<FeedTab> {
  final _scroll = ScrollController();

  EdgewindStore get _store => widget.store;

  @override
  void initState() {
    super.initState();
    _scroll.addListener(() {
      final p = _scroll.position;
      if (p.pixels > p.maxScrollExtent - 600) _store.loadMore();
    });
  }

  @override
  void dispose() {
    _scroll.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    final stories = _store.feed;
    final topics = [EdgewindStore.kAll, ...storyCategories];
    final count = stories.length;

    return RefreshIndicator(
      color: c.violet,
      backgroundColor: c.panel,
      edgeOffset: MediaQuery.paddingOf(context).top + 56,
      onRefresh: () => _store.refreshAll(),
      child: CustomScrollView(
        controller: _scroll,
        physics: const AlwaysScrollableScrollPhysics(parent: BouncingScrollPhysics()),
        slivers: [
          HeroHeader(
            image: kWires,
            alignment: const Alignment(-0.35, 0.25),
            title: 'ЛЕНТА',
            jp: '受信中',
            expandedHeight: 300,
            caption: _store.offline
                ? 'НЕТ СВЯЗИ · ПОКАЗАНО СОХРАНЁННОЕ'
                : '${count == 0 ? 'НИКОГО' : counted(count, 'ЗАПИСЬ', 'ЗАПИСИ', 'ЗАПИСЕЙ')} · ОБЩАЯ ДЛЯ ВСЕХ',
            actions: [
              IconAction(
                icon: Icons.support_outlined,
                tooltip: 'Если очень плохо',
                onTap: widget.onHelp,
                color: c.onArt,
              ),
            ],
          ),
          PinnedBar(
            height: 46,
            child: ListView(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.symmetric(horizontal: 10),
              children: [
                for (final t in topics)
                  TopicTab(label: t, selected: _store.category == t, onTap: () => _store.setCategory(t)),
              ],
            ),
          ),
          if (stories.isEmpty && !_store.feedLoaded)
            const SliverFillRemaining(hasScrollBody: false, child: Center(child: LoadingLine()))
          else if (stories.isEmpty)
            SliverFillRemaining(
              hasScrollBody: false,
              child: Center(
                child: EmptyState(
                  glyph: '静',
                  title: _store.category == EdgewindStore.kAll ? 'ПОКА ТИХО' : 'В ЭТОЙ ТЕМЕ ТИХО',
                  text: 'Можно написать первым. Это увидят те, кому сейчас так же.',
                  action: 'НАПИСАТЬ',
                  onAction: widget.onCompose,
                ),
              ),
            )
          else
            SliverList.separated(
              itemCount: stories.length + 1,
              separatorBuilder: (_, i) => i < stories.length - 1 ? const Hairline(indent: 74) : const SizedBox.shrink(),
              itemBuilder: (context, i) {
                if (i == stories.length) return _Tail(store: _store);
                final s = stories[i];
                return StoryTile(
                  story: s,
                  showCategory: _store.category == EdgewindStore.kAll,
                  onTap: () => widget.onOpen(s),
                  onLike: () => _store.toggleLike(s.id),
                );
              },
            ),
        ],
      ),
    );
  }
}

class _Tail extends StatelessWidget {
  const _Tail({required this.store});

  final EdgewindStore store;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 18, 20, 36),
      child: Center(
        child: store.feedLoading
            ? const LoadingLine()
            : store.feedEnd
            ? Text('— КОНЕЦ ЛЕНТЫ —', style: T.label(c.textFaint, tracking: 2.4))
            : const SizedBox(height: 12),
      ),
    );
  }
}
