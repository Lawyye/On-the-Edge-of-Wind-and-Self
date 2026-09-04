import 'package:flutter/material.dart';

import '../data/edgewind_store.dart';
import '../domain/models.dart';
import '../ui/time_text.dart';
import '../widgets/story_card.dart';

Future<void> showStorySheet(
  BuildContext context, {
  required EdgewindStore store,
  required Story story,
}) {
  return showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    backgroundColor: Colors.transparent,
    builder: (_) => StorySheet(store: store, story: story),
  );
}

class StorySheet extends StatefulWidget {
  const StorySheet({super.key, required this.store, required this.story});

  final EdgewindStore store;
  final Story story;

  @override
  State<StorySheet> createState() => _StorySheetState();
}

class _StorySheetState extends State<StorySheet> {
  final _response = TextEditingController();
  bool _sending = false;

  @override
  void dispose() {
    _response.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: widget.store,
      builder: (context, _) {
        final responses = widget.store.responsesFor(widget.story.id);
        final liked = widget.store.isLiked(widget.story.id);
        final saved = widget.store.isSaved(widget.story.id);
        return DraggableScrollableSheet(
          initialChildSize: 0.86,
          minChildSize: 0.55,
          maxChildSize: 0.96,
          expand: false,
          builder: (context, controller) => Container(
            decoration: const BoxDecoration(
              color: Color(0xFFF7F8F5),
              borderRadius: BorderRadius.vertical(top: Radius.circular(30)),
            ),
            child: Column(
              children: [
                const SizedBox(height: 12),
                const _Handle(),
                Expanded(
                  child: ListView(
                    controller: controller,
                    physics: const BouncingScrollPhysics(),
                    padding: const EdgeInsets.fromLTRB(22, 20, 22, 28),
                    children: [
                      Row(
                        children: [
                          Expanded(
                            child: Text(
                              widget.story.category,
                              style: const TextStyle(
                                color: storyMoss,
                                fontWeight: FontWeight.w700,
                              ),
                            ),
                          ),
                          IconButton(
                            tooltip: saved ? 'Убрать' : 'Сохранить',
                            onPressed: () =>
                                widget.store.toggleSaved(widget.story.id),
                            icon: Icon(
                              saved
                                  ? Icons.bookmark_rounded
                                  : Icons.bookmark_border_rounded,
                            ),
                          ),
                          PopupMenuButton<String>(
                            onSelected: _handleMenu,
                            itemBuilder: (_) => [
                              PopupMenuItem(
                                value: widget.story.isMine
                                    ? 'delete'
                                    : 'report',
                                child: Text(
                                  widget.story.isMine
                                      ? 'Удалить запись'
                                      : 'Пожаловаться',
                                ),
                              ),
                            ],
                          ),
                        ],
                      ),
                      const SizedBox(height: 8),
                      Text(
                        widget.story.title,
                        style: Theme.of(context).textTheme.headlineSmall,
                      ),
                      const SizedBox(height: 8),
                      Text(
                        timeText(widget.story.createdAt),
                        style: const TextStyle(color: Color(0xFF78867F)),
                      ),
                      const SizedBox(height: 18),
                      Text(
                        widget.story.body,
                        style: Theme.of(context).textTheme.bodyLarge,
                      ),
                      const SizedBox(height: 18),
                      Row(
                        children: [
                          OutlinedButton.icon(
                            onPressed: () =>
                                widget.store.toggleLike(widget.story.id),
                            icon: Icon(
                              liked
                                  ? Icons.favorite_rounded
                                  : Icons.favorite_border_rounded,
                              color: liked
                                  ? const Color(0xFFB46B64)
                                  : storyMoss,
                            ),
                            label: Text(
                              liked
                                  ? 'Ты рядом · ${widget.store.likeCount(widget.story)}'
                                  : 'Я рядом',
                            ),
                          ),
                        ],
                      ),
                      const Padding(
                        padding: EdgeInsets.symmetric(vertical: 24),
                        child: Divider(),
                      ),
                      Text(
                        responses.isEmpty ? 'Откликов пока нет' : 'Отклики',
                        style: Theme.of(context).textTheme.titleMedium,
                      ),
                      const SizedBox(height: 12),
                      if (responses.isEmpty)
                        const Text(
                          'Можно написать первым.',
                          style: TextStyle(color: Color(0xFF69776F)),
                        )
                      else
                        ...responses.map(
                          (response) => _ResponseCard(response: response),
                        ),
                      const SizedBox(height: 18),
                      TextField(
                        controller: _response,
                        enabled: !_sending,
                        minLines: 2,
                        maxLines: 5,
                        maxLength: 1000,
                        textCapitalization: TextCapitalization.sentences,
                        decoration: const InputDecoration(
                          labelText: 'Твой отклик',
                          alignLabelWithHint: true,
                          hintText: 'Напиши по-человечески…',
                        ),
                      ),
                      const SizedBox(height: 8),
                      SizedBox(
                        width: double.infinity,
                        child: FilledButton(
                          onPressed: _sending ? null : _sendResponse,
                          child: _sending
                              ? const SizedBox.square(
                                  dimension: 18,
                                  child: CircularProgressIndicator(
                                    strokeWidth: 2,
                                    color: Colors.white,
                                  ),
                                )
                              : const Text('Отправить'),
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        );
      },
    );
  }

  Future<void> _sendResponse() async {
    final text = _response.text.trim();
    if (text.length < 2) return;
    setState(() => _sending = true);
    await widget.store.addResponse(widget.story.id, text);
    _response.clear();
    if (mounted) setState(() => _sending = false);
  }

  Future<void> _handleMenu(String action) async {
    if (action == 'delete') {
      final confirmed = await showDialog<bool>(
        context: context,
        builder: (context) => AlertDialog(
          title: const Text('Удалить запись?'),
          content: const Text('Отклики к ней тоже удалятся.'),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: const Text('Нет'),
            ),
            FilledButton(
              onPressed: () => Navigator.pop(context, true),
              child: const Text('Удалить'),
            ),
          ],
        ),
      );
      if (confirmed == true) {
        await widget.store.deleteStory(widget.story.id);
        if (mounted) Navigator.pop(context);
      }
      return;
    }

    final reason = await showDialog<String>(
      context: context,
      builder: (_) => const _ReportDialog(),
    );
    if (reason != null && mounted) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('Жалоба принята.')));
    }
  }
}

class _ResponseCard extends StatelessWidget {
  const _ResponseCard({required this.response});

  final StoryResponse response;

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: 10),
      padding: const EdgeInsets.all(15),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: const Color(0xFFE0E6E1)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Text(
                response.isMine ? 'Ты' : 'Без имени',
                style: const TextStyle(fontWeight: FontWeight.w700),
              ),
              const Spacer(),
              Text(
                timeText(response.createdAt),
                style: const TextStyle(fontSize: 12, color: Color(0xFF78867F)),
              ),
            ],
          ),
          const SizedBox(height: 8),
          Text(response.text),
        ],
      ),
    );
  }
}

class _ReportDialog extends StatefulWidget {
  const _ReportDialog();

  @override
  State<_ReportDialog> createState() => _ReportDialogState();
}

class _ReportDialogState extends State<_ReportDialog> {
  String _reason = 'Оскорбления';

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Что не так?'),
      content: RadioGroup<String>(
        groupValue: _reason,
        onChanged: (value) => setState(() => _reason = value ?? _reason),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children:
              ['Оскорбления', 'Спам или обман', 'Опасный контент', 'Другое']
                  .map(
                    (reason) => RadioListTile<String>(
                      value: reason,
                      title: Text(reason),
                    ),
                  )
                  .toList(),
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: const Text('Отмена'),
        ),
        FilledButton(
          onPressed: () => Navigator.pop(context, _reason),
          child: const Text('Отправить'),
        ),
      ],
    );
  }
}

class _Handle extends StatelessWidget {
  const _Handle();

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Container(
        width: 44,
        height: 4,
        decoration: BoxDecoration(
          color: const Color(0xFFCDD5CF),
          borderRadius: BorderRadius.circular(4),
        ),
      ),
    );
  }
}
