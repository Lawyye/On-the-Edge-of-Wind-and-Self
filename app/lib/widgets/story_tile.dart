import 'package:flutter/material.dart';

import '../domain/categories.dart';
import '../domain/models.dart';
import '../ui/art.dart';
import '../ui/format.dart';
import '../ui/tokens.dart';
import '../ui/type.dart';
import 'avatar.dart';

/// История в ленте. Не карточка, а запись в журнале: знак-сигнал слева,
/// текст человека справа, внизу подпись и отклик. Разделяет записи волосок.
class StoryTile extends StatelessWidget {
  const StoryTile({super.key, required this.story, required this.onTap, this.onLike, this.showCategory = true});

  final Story story;
  final VoidCallback onTap;
  final VoidCallback? onLike;
  final bool showCategory;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    final art = artFor(story.category);
    final tint = c.isDark ? art.tint : art.tintLight;

    return Semantics(
      button: true,
      label: '${story.title}. ${story.authorNickname}, ${shortAgo(story.createdAt).toLowerCase()}',
      child: InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(20, 18, 20, 16),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              SignalGlyph(seed: story.id, color: tint, size: 40),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        if (showCategory) ...[
                          Flexible(
                            child: Text(
                              story.category.toUpperCase(),
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: T.label(tint, size: 9.5, tracking: 1.3),
                            ),
                          ),
                          Text('  ·  ', style: T.label(c.textFaint, size: 9.5)),
                        ],
                        Text(shortAgo(story.createdAt), style: T.label(c.textFaint, size: 9.5, tracking: 1.1)),
                        if (story.hidden) ...[
                          const SizedBox(width: 8),
                          Container(
                            padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 2),
                            decoration: BoxDecoration(border: Border.all(color: c.alarm.withValues(alpha: 0.7))),
                            child: Text('СКРЫТА', style: T.label(c.alarm, size: 8.5, tracking: 1.2)),
                          ),
                        ],
                      ],
                    ),
                    const SizedBox(height: 7),
                    Text(story.title, style: T.title(c.text, size: 17.5), maxLines: 3, overflow: TextOverflow.ellipsis),
                    const SizedBox(height: 6),
                    Text(
                      story.body,
                      maxLines: 3,
                      overflow: TextOverflow.ellipsis,
                      style: T.body(c.textDim, size: 14.3, height: 1.55),
                    ),
                    const SizedBox(height: 12),
                    Row(
                      children: [
                        Avatar(kind: story.authorAvatarKind, url: story.authorAvatarUrl, size: 20, lit: story.isMine),
                        const SizedBox(width: 8),
                        Flexible(
                          child: Text(
                            story.isMine ? '${story.authorNickname} · ты' : story.authorNickname,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: T.label(story.isMine ? c.lamp : c.textDim, size: 10.5, tracking: 0.6),
                          ),
                        ),
                        const SizedBox(width: 10),
                        const Spacer(),
                        NearbyMark(story: story, onTap: onLike),
                        const SizedBox(width: 14),
                        Icon(Icons.subdirectory_arrow_left_rounded, size: 14, color: c.textFaint),
                        const SizedBox(width: 4),
                        Text('${story.responseCount}', style: T.label(c.textFaint, size: 10.5)),
                      ],
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// «Я рядом»: тёплая точка и число. На своей истории — только число.
class NearbyMark extends StatelessWidget {
  const NearbyMark({super.key, required this.story, this.onTap});

  final Story story;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    final on = story.likedByMe;
    final canTap = onTap != null && !story.isMine;
    final ink = on ? c.lamp : c.textFaint;
    final mark = Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        AnimatedContainer(
          duration: const Duration(milliseconds: 200),
          width: 7,
          height: 7,
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            color: on ? c.lamp : Colors.transparent,
            border: Border.all(color: ink, width: 1.2),
            boxShadow: on ? [BoxShadow(color: c.lamp.withValues(alpha: 0.8), blurRadius: 8)] : null,
          ),
        ),
        const SizedBox(width: 6),
        Text('${story.likes}', style: T.label(ink, size: 10.5)),
      ],
    );
    if (!canTap) {
      return Semantics(label: '${story.likes} рядом', child: ExcludeSemantics(child: mark));
    }
    return Semantics(
      button: true,
      toggled: on,
      label: on ? 'Ты рядом. Убрать отметку' : 'Я рядом',
      child: ExcludeSemantics(
        child: InkResponse(
          onTap: onTap,
          radius: 22,
          child: Padding(padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 8), child: mark),
        ),
      ),
    );
  }
}
