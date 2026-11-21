import 'package:flutter/material.dart';

import '../data/edgewind_store.dart';
import '../domain/categories.dart';
import '../domain/models.dart';
import '../ui/format.dart';
import '../ui/hero_header.dart';
import '../ui/tokens.dart';
import '../ui/type.dart';
import '../ui/widgets.dart';
import '../widgets/avatar.dart';

/// Отклики на мои истории — то, ради чего человек сюда возвращается.
/// Новые отмечены тёплой чертой слева, пока вкладку не открыли.
class InboxTab extends StatelessWidget {
  const InboxTab({super.key, required this.store, required this.onOpenStory});

  final EdgewindStore store;
  final ValueChanged<String> onOpenStory;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    final items = store.inbox;
    final unread = store.unreadCount;
    return RefreshIndicator(
      color: c.violet,
      backgroundColor: c.panel,
      edgeOffset: MediaQuery.paddingOf(context).top + 56,
      onRefresh: () async {
        await store.refreshAll();
        await store.markInboxSeen();
      },
      child: CustomScrollView(
        physics: const AlwaysScrollableScrollPhysics(parent: BouncingScrollPhysics()),
        slivers: [
          HeroHeader(
            image: kGirl,
            alignment: const Alignment(0.75, -0.05),
            title: 'ОТКЛИК',
            jp: '応答',
            expandedHeight: 230,
            caption: items.isEmpty
                ? 'НА ТВОИ ЗАПИСИ'
                : unread > 0
                ? '${counted(unread, 'НОВЫЙ', 'НОВЫХ', 'НОВЫХ')} · ВСЕГО ${items.length}'
                : '${counted(items.length, 'ОТКЛИК', 'ОТКЛИКА', 'ОТКЛИКОВ')} НА ТВОИ ЗАПИСИ',
          ),
          if (items.isEmpty)
            const SliverFillRemaining(
              hasScrollBody: false,
              child: Center(
                child: EmptyState(
                  glyph: '応',
                  title: 'ТИХО',
                  text: 'Когда кто-то ответит на твою запись, ответ появится здесь.',
                ),
              ),
            )
          else
            SliverList.separated(
              itemCount: items.length,
              separatorBuilder: (_, __) => const Hairline(indent: 20),
              itemBuilder: (context, i) => _InboxRow(
                item: items[i],
                unread: store.isUnread(items[i]),
                onTap: () => onOpenStory(items[i].storyId),
              ),
            ),
          const SliverToBoxAdapter(child: SizedBox(height: 28)),
        ],
      ),
    );
  }
}

class _InboxRow extends StatelessWidget {
  const _InboxRow({required this.item, required this.unread, required this.onTap});

  final InboxItem item;
  final bool unread;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    return Semantics(
      button: true,
      label: '${unread ? 'Новый отклик. ' : ''}${item.authorNickname}: ${item.body}',
      child: ExcludeSemantics(
        child: InkWell(
          onTap: onTap,
          child: Container(
            decoration: BoxDecoration(
              border: Border(left: BorderSide(color: unread ? c.lamp : Colors.transparent, width: 2)),
            ),
            padding: const EdgeInsets.fromLTRB(18, 16, 20, 16),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Avatar(kind: item.authorAvatarKind, url: item.authorAvatarUrl, size: 34),
                const SizedBox(width: 13),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Expanded(
                            child: Text(
                              item.authorNickname,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: T.label(unread ? c.text : c.textDim, size: 11, tracking: 0.8, weight: FontWeight.w700),
                            ),
                          ),
                          Text(shortAgo(item.createdAt), style: T.label(c.textFaint, size: 9.5)),
                        ],
                      ),
                      const SizedBox(height: 6),
                      Text(item.body, maxLines: 4, overflow: TextOverflow.ellipsis, style: T.body(c.text, size: 14.5, height: 1.5)),
                      const SizedBox(height: 8),
                      Text(
                        'НА «${item.storyTitle}»',
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: T.label(c.textFaint, size: 9.5, tracking: 0.8),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
