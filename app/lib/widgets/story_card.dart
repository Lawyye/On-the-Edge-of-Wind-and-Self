import 'package:flutter/material.dart';

import '../domain/models.dart';
import '../ui/term.dart';
import '../ui/time_text.dart';
import '../ui/tokens.dart';

/// Карточка истории. Служебная строка сверху моноширинная и холодная,
/// текст истории — книжный и тёплый: взгляд идёт к человеку, а не к обвязке.
class StoryCard extends StatelessWidget {
  const StoryCard({
    super.key,
    required this.story,
    required this.index,
    required this.onTap,
    required this.onLike,
    this.onSave,
  });

  final Story story;

  /// Номер записи в ленте — для кода REC-NNN в шапке карточки.
  final int index;

  final VoidCallback onTap;
  final VoidCallback onLike;
  final VoidCallback? onSave;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    final code = 'REC-${(index + 1).toString().padLeft(3, '0')}';

    return Pane(
      onTap: onTap,
      padding: const EdgeInsets.fromLTRB(16, 15, 16, 14),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              TermLabel(code, color: c.cyan),
              const SizedBox(width: 8),
              Container(width: 1, height: 9, color: c.line),
              const SizedBox(width: 8),
              Flexible(child: TermLabel(story.category)),
              const Spacer(),
              TermLabel(timeText(story.createdAt)),
            ],
          ),
          const SizedBox(height: 13),
          Text(story.title, style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 7),
          Text(
            story.body,
            maxLines: 4,
            overflow: TextOverflow.ellipsis,
            style: Theme.of(context).textTheme.bodyMedium,
          ),
          const SizedBox(height: 14),
          Divider(height: 1, color: c.line),
          const SizedBox(height: 11),
          Row(
            children: [
              TermLabel(
                story.responseCount == 0 ? 'ОТКЛИКНУТЬСЯ' : '${story.responseCount} ОТКЛ.',
                color: c.cyan,
              ),
              const SizedBox(width: 16),
              NearbyMark(count: story.likes, on: story.likedByMe, onTap: onLike),
              const Spacer(),
              if (onSave != null)
                InkResponse(
                  onTap: onSave,
                  radius: 20,
                  child: Padding(
                    padding: const EdgeInsets.all(4),
                    child: Icon(
                      story.savedByMe ? Icons.bookmark : Icons.bookmark_border,
                      size: 17,
                      color: story.savedByMe ? c.phos : c.faint,
                    ),
                  ),
                )
              else
                TermLabel(story.isMine ? 'ТЫ' : story.authorNickname),
            ],
          ),
        ],
      ),
    );
  }
}

/// Отметка «я рядом». Не лайк: ставится один раз, снимается повторным
/// нажатием и не выносится крупно — здесь не за что соревноваться.
class NearbyMark extends StatelessWidget {
  const NearbyMark({
    super.key,
    required this.count,
    required this.on,
    required this.onTap,
  });

  final int count;
  final bool on;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    return InkResponse(
      onTap: onTap,
      radius: 24,
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 4),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            AnimatedContainer(
              duration: const Duration(milliseconds: 160),
              width: 7,
              height: 7,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: on ? c.rust : Colors.transparent,
                border: Border.all(color: on ? c.rust : c.faint),
                boxShadow: on
                    ? [BoxShadow(color: c.rust.withValues(alpha: 0.75), blurRadius: 7)]
                    : null,
              ),
            ),
            const SizedBox(width: 7),
            TermLabel(
              '$count РЯДОМ',
              color: on ? c.rust : c.faint,
              tracking: 1.1,
              size: 10,
            ),
          ],
        ),
      ),
    );
  }
}
