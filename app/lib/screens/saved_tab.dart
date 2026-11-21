import 'package:flutter/material.dart';

import '../data/edgewind_store.dart';
import '../domain/categories.dart';
import '../domain/models.dart';
import '../ui/format.dart';
import '../ui/hero_header.dart';
import '../ui/tokens.dart';
import '../ui/widgets.dart';
import '../widgets/story_tile.dart';

/// Архив: то, к чему хочется вернуться. Виден только самому человеку.
class SavedTab extends StatelessWidget {
  const SavedTab({super.key, required this.store, required this.onOpen});

  final EdgewindStore store;
  final ValueChanged<Story> onOpen;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    final saved = store.savedStories;
    return RefreshIndicator(
      color: c.violet,
      backgroundColor: c.panel,
      edgeOffset: MediaQuery.paddingOf(context).top + 56,
      onRefresh: () => store.refreshAll(),
      child: CustomScrollView(
        physics: const AlwaysScrollableScrollPhysics(parent: BouncingScrollPhysics()),
        slivers: [
          HeroHeader(
            image: kWires,
            alignment: const Alignment(-0.1, 1.0),
            title: 'АРХИВ',
            jp: '保存',
            expandedHeight: 230,
            caption: saved.isEmpty ? 'ВИДНО ТОЛЬКО ТЕБЕ' : '${counted(saved.length, 'ЗАПИСЬ', 'ЗАПИСИ', 'ЗАПИСЕЙ')} · ВИДНО ТОЛЬКО ТЕБЕ',
          ),
          if (saved.isEmpty)
            const SliverFillRemaining(
              hasScrollBody: false,
              child: Center(
                child: EmptyState(
                  glyph: '保',
                  title: 'АРХИВ ПУСТ',
                  text: 'Сохраняй истории, к которым захочется вернуться. Знак сохранения — в открытой записи.',
                ),
              ),
            )
          else
            SliverList.separated(
              itemCount: saved.length,
              separatorBuilder: (_, __) => const Hairline(indent: 74),
              itemBuilder: (context, i) => StoryTile(
                story: saved[i],
                onTap: () => onOpen(saved[i]),
                onLike: () => store.toggleLike(saved[i].id),
              ),
            ),
          const SliverToBoxAdapter(child: SizedBox(height: 28)),
        ],
      ),
    );
  }
}
