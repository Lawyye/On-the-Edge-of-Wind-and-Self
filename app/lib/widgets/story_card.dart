import 'package:flutter/material.dart';

import '../data/edgewind_store.dart';
import '../domain/models.dart';
import '../ui/time_text.dart';

const storyInk = Color(0xFF20322D);
const storyMoss = Color(0xFF587568);
const storyPaleMoss = Color(0xFFE5ECE6);
const storyWarm = Color(0xFFF0E2D4);

class StoryCard extends StatelessWidget {
  const StoryCard({
    super.key,
    required this.story,
    required this.store,
    required this.onTap,
  });

  final Story story;
  final EdgewindStore store;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final liked = store.isLiked(story.id);
    final saved = store.isSaved(story.id);
    final replies = store.responseCount(story.id);

    return Card(
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(20, 18, 12, 12),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Container(
                    width: 38,
                    height: 38,
                    decoration: const BoxDecoration(
                      color: storyPaleMoss,
                      shape: BoxShape.circle,
                    ),
                    child: const Icon(
                      Icons.grass_rounded,
                      size: 19,
                      color: storyMoss,
                    ),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          story.isMine ? 'Твоя запись' : 'Без имени',
                          style: const TextStyle(
                            fontWeight: FontWeight.w700,
                            color: storyInk,
                          ),
                        ),
                        Text(
                          timeText(story.createdAt),
                          style: const TextStyle(
                            fontSize: 12,
                            color: Color(0xFF78867F),
                          ),
                        ),
                      ],
                    ),
                  ),
                  IconButton(
                    tooltip: saved ? 'Убрать из сохранённых' : 'Сохранить',
                    onPressed: () => store.toggleSaved(story.id),
                    icon: AnimatedSwitcher(
                      duration: const Duration(milliseconds: 180),
                      child: Icon(
                        saved ? Icons.bookmark_rounded : Icons.bookmark_border,
                        key: ValueKey(saved),
                        color: saved ? storyMoss : const Color(0xFF7B8982),
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 15),
              Container(
                padding: const EdgeInsets.symmetric(
                  horizontal: 10,
                  vertical: 5,
                ),
                decoration: BoxDecoration(
                  color: storyWarm,
                  borderRadius: BorderRadius.circular(20),
                ),
                child: Text(
                  story.category,
                  style: const TextStyle(
                    fontSize: 12,
                    fontWeight: FontWeight.w700,
                    color: Color(0xFF74543D),
                  ),
                ),
              ),
              const SizedBox(height: 12),
              Text(story.title, style: Theme.of(context).textTheme.titleMedium),
              const SizedBox(height: 7),
              Text(story.body, maxLines: 4, overflow: TextOverflow.ellipsis),
              const SizedBox(height: 10),
              Row(
                children: [
                  TextButton.icon(
                    onPressed: onTap,
                    icon: const Icon(
                      Icons.chat_bubble_outline_rounded,
                      size: 19,
                    ),
                    label: Text(replies == 0 ? 'Откликнуться' : '$replies'),
                  ),
                  const Spacer(),
                  IconButton(
                    tooltip: liked ? 'Убрать отметку' : 'Я рядом',
                    onPressed: () => store.toggleLike(story.id),
                    icon: AnimatedSwitcher(
                      duration: const Duration(milliseconds: 180),
                      transitionBuilder: (child, animation) =>
                          ScaleTransition(scale: animation, child: child),
                      child: Icon(
                        liked
                            ? Icons.favorite_rounded
                            : Icons.favorite_border_rounded,
                        key: ValueKey(liked),
                        color: liked
                            ? const Color(0xFFB46B64)
                            : const Color(0xFF7B8982),
                      ),
                    ),
                  ),
                  Text(
                    '${store.likeCount(story)}',
                    style: const TextStyle(color: Color(0xFF7B8982)),
                  ),
                  const SizedBox(width: 8),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}
