import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

import '../data/edgewind_store.dart';
import '../domain/models.dart';
import '../ui/term.dart';
import '../ui/time_text.dart';
import '../ui/tokens.dart';
import '../widgets/avatar.dart';

class StoryScreen extends StatefulWidget {
  const StoryScreen({super.key, required this.store, required this.storyId});

  final EdgewindStore store;
  final String storyId;

  @override
  State<StoryScreen> createState() => _StoryScreenState();
}

class _StoryScreenState extends State<StoryScreen> {
  final _reply = TextEditingController();
  late Future<List<StoryResponse>> _responses;
  bool _sending = false;

  @override
  void initState() {
    super.initState();
    _responses = widget.store.responsesFor(widget.storyId);
  }

  @override
  void dispose() {
    _reply.dispose();
    super.dispose();
  }

  void _reload() {
    setState(() => _responses = widget.store.responsesFor(widget.storyId));
  }

  Future<void> _send() async {
    final text = _reply.text.trim();
    if (text.isEmpty || _sending) return;
    setState(() => _sending = true);
    try {
      await widget.store.addResponse(widget.storyId, text);
      _reply.clear();
      FocusScope.of(context).unfocus();
      _reload();
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text((widget.store.error ?? 'НЕ ОТПРАВИЛОСЬ').toUpperCase())),
        );
      }
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }

  Future<void> _report() async {
    final reason = await showModalBottomSheet<String>(
      context: context,
      backgroundColor: Colors.transparent,
      builder: (_) => const _ReportSheet(),
    );
    if (reason == null) return;
    try {
      await widget.store.report(
        targetType: 'story',
        targetId: widget.storyId,
        reason: reason,
      );
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(const SnackBar(content: Text('ЖАЛОБА ПРИНЯТА')));
        Navigator.of(context).pop();
      }
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text((widget.store.error ?? 'НЕ ПОЛУЧИЛОСЬ').toUpperCase())),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final c = context.c;

    return AnimatedBuilder(
      animation: widget.store,
      builder: (context, _) {
        final story = widget.store.storyById(widget.storyId);
        if (story == null) {
          return TermScreen(
            child: Center(child: TermLabel('ЗАПИСЬ НЕ НАЙДЕНА', color: c.muted, tracking: 2.4)),
          );
        }

        return TermScreen(
          child: Column(
            children: [
              SafeArea(
                bottom: false,
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(14, 10, 14, 0),
                  child: Row(
                    children: [
                      InkResponse(
                        onTap: () => Navigator.of(context).pop(),
                        radius: 22,
                        child: Padding(
                          padding: const EdgeInsets.all(6),
                          child: Icon(Icons.arrow_back, size: 19, color: c.bone),
                        ),
                      ),
                      const SizedBox(width: 6),
                      TermLabel(story.category, color: c.cyan),
                      const SizedBox(width: 10),
                      Expanded(child: Container(height: 1, color: c.line)),
                      const SizedBox(width: 10),
                      InkResponse(
                        onTap: _report,
                        radius: 22,
                        child: Padding(
                          padding: const EdgeInsets.all(6),
                          child: Icon(Icons.flag_outlined, size: 17, color: c.muted),
                        ),
                      ),
                    ],
                  ),
                ),
              ),

              Expanded(
                child: ListView(
                  padding: const EdgeInsets.fromLTRB(18, 18, 18, 24),
                  children: [
                    Row(
                      children: [
                        Avatar(
                          kind: story.authorAvatarKind,
                          url: story.authorAvatarUrl,
                          size: 34,
                        ),
                        const SizedBox(width: 11),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              TermLabel(
                                story.isMine ? '${story.authorNickname} · ты' : story.authorNickname,
                                color: c.bone,
                                size: 11.5,
                                tracking: 1.4,
                                weight: FontWeight.w700,
                              ),
                              const SizedBox(height: 3),
                              TermLabel(timeText(story.createdAt)),
                            ],
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 18),
                    Text(story.title, style: Theme.of(context).textTheme.headlineSmall),
                    const SizedBox(height: 14),
                    Text(story.body, style: Theme.of(context).textTheme.bodyLarge),

                    const SizedBox(height: 22),
                    Row(
                      children: [
                        Expanded(
                          child: _Action(
                            on: story.likedByMe,
                            onColor: c.rust,
                            label: 'Я РЯДОМ · ${story.likes}',
                            onTap: () => widget.store.toggleLike(story.id),
                          ),
                        ),
                        const SizedBox(width: 9),
                        SizedBox(
                          width: 54,
                          child: _Action(
                            on: story.savedByMe,
                            onColor: c.phos,
                            onTap: () => widget.store.toggleSaved(story.id),
                          ),
                        ),
                      ],
                    ),

                    const SizedBox(height: 26),
                    Row(
                      children: [
                        const TermLabel('ОТКЛИКИ', tracking: 2.2),
                        const SizedBox(width: 10),
                        Expanded(child: Container(height: 1, color: c.line)),
                        const SizedBox(width: 10),
                        TermLabel(
                          story.responseCount.toString().padLeft(2, '0'),
                          color: c.cyan,
                        ),
                      ],
                    ),
                    const SizedBox(height: 14),

                    FutureBuilder<List<StoryResponse>>(
                      future: _responses,
                      builder: (context, snap) {
                        if (snap.connectionState != ConnectionState.done) {
                          return Padding(
                            padding: const EdgeInsets.symmetric(vertical: 22),
                            child: Center(
                              child: SizedBox(
                                width: 18,
                                height: 18,
                                child: CircularProgressIndicator(strokeWidth: 1.4, color: c.cyan),
                              ),
                            ),
                          );
                        }
                        final list = snap.data ?? const <StoryResponse>[];
                        if (list.isEmpty) {
                          return Padding(
                            padding: const EdgeInsets.symmetric(vertical: 18),
                            child: Text(
                              'Откликов пока нет. Можно написать первым.',
                              style: Theme.of(context).textTheme.bodyMedium,
                            ),
                          );
                        }
                        return Column(
                          children: [
                            for (final r in list) ...[
                              _ResponseRow(response: r),
                              const SizedBox(height: 10),
                            ],
                          ],
                        );
                      },
                    ),
                  ],
                ),
              ),

              _ReplyBar(
                controller: _reply,
                sending: _sending,
                name: widget.store.profile?.nickname ?? '',
                onSend: _send,
              ),
            ],
          ),
        );
      },
    );
  }
}

class _Action extends StatelessWidget {
  const _Action({
    required this.on,
    required this.onColor,
    required this.onTap,
    this.label,
  });

  final bool on;
  final Color onColor;
  final VoidCallback onTap;
  final String? label;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    return GestureDetector(
      onTap: onTap,
      child: Container(
        height: 46,
        alignment: Alignment.center,
        decoration: BoxDecoration(
          border: Border.all(color: on ? onColor : c.line),
          color: on ? onColor.withValues(alpha: 0.1) : null,
        ),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Container(
              width: 8,
              height: 8,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: on ? onColor : Colors.transparent,
                border: Border.all(color: on ? onColor : c.faint),
                boxShadow: on
                    ? [BoxShadow(color: onColor.withValues(alpha: 0.8), blurRadius: 8)]
                    : null,
              ),
            ),
            if (label != null) ...[
              const SizedBox(width: 9),
              TermLabel(
                label!,
                color: on ? onColor : c.faint,
                size: 10.5,
                tracking: 2,
                weight: FontWeight.w700,
              ),
            ],
          ],
        ),
      ),
    );
  }
}

class _ResponseRow extends StatelessWidget {
  const _ResponseRow({required this.response});
  final StoryResponse response;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    // Отклики не карточки, а отчёркнутые слева блоки: это реплики в разговоре,
    // а не записи сами по себе.
    return Container(
      padding: const EdgeInsets.fromLTRB(13, 2, 0, 2),
      decoration: BoxDecoration(
        border: Border(
          left: BorderSide(color: response.isMine ? c.amber : c.line, width: 2),
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              TermLabel(
                response.isMine ? '${response.authorNickname} · ты' : response.authorNickname,
                color: response.isMine ? c.amber : c.muted,
                size: 10.5,
                tracking: 1.3,
                weight: FontWeight.w700,
              ),
              const Spacer(),
              TermLabel(timeText(response.createdAt)),
            ],
          ),
          const SizedBox(height: 7),
          Text(response.body, style: Theme.of(context).textTheme.bodyMedium),
        ],
      ),
    );
  }
}

class _ReplyBar extends StatelessWidget {
  const _ReplyBar({
    required this.controller,
    required this.sending,
    required this.name,
    required this.onSend,
  });

  final TextEditingController controller;
  final bool sending;
  final String name;
  final VoidCallback onSend;

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
        child: Padding(
          padding: const EdgeInsets.fromLTRB(14, 10, 14, 10),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Expanded(
                child: TextField(
                  controller: controller,
                  minLines: 1,
                  maxLines: 4,
                  maxLength: 2000,
                  textCapitalization: TextCapitalization.sentences,
                  style: GoogleFonts.literata(fontSize: 15, height: 1.5, color: c.bone),
                  decoration: InputDecoration(
                    counterText: '',
                    isDense: true,
                    hintText: 'Ответить как $name…',
                  ),
                ),
              ),
              const SizedBox(width: 9),
              GestureDetector(
                onTap: sending ? null : onSend,
                child: Container(
                  width: 46,
                  height: 46,
                  alignment: Alignment.center,
                  decoration: BoxDecoration(
                    border: Border.all(color: c.cyan),
                    color: c.cyan.withValues(alpha: 0.1),
                  ),
                  child: sending
                      ? SizedBox(
                          width: 16,
                          height: 16,
                          child: CircularProgressIndicator(strokeWidth: 1.4, color: c.cyan),
                        )
                      : Icon(Icons.arrow_forward, size: 18, color: c.cyan),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _ReportSheet extends StatelessWidget {
  const _ReportSheet();

  static const _reasons = ['Оскорбления', 'Спам или обман', 'Опасный контент', 'Другое'];

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    return Container(
      decoration: BoxDecoration(
        color: c.voidBg,
        border: Border(top: BorderSide(color: c.rust)),
      ),
      child: SafeArea(
        top: false,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(18, 16, 18, 18),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              TermLabel('ПОЖАЛОВАТЬСЯ', color: c.rust, tracking: 2.4),
              const SizedBox(height: 8),
              TermLabel(
                'ТРИ НЕЗАВИСИМЫЕ ЖАЛОБЫ УБИРАЮТ ЗАПИСЬ ИЗ ЛЕНТЫ ДО РАЗБОРА',
                size: 9.5,
                tracking: 0.6,
              ),
              const SizedBox(height: 16),
              for (final r in _reasons) ...[
                GestureDetector(
                  onTap: () => Navigator.of(context).pop(r),
                  child: Container(
                    height: 46,
                    alignment: Alignment.centerLeft,
                    padding: const EdgeInsets.symmetric(horizontal: 14),
                    decoration: BoxDecoration(border: Border.all(color: c.line)),
                    child: Text(r, style: Theme.of(context).textTheme.bodyMedium),
                  ),
                ),
                const SizedBox(height: 8),
              ],
            ],
          ),
        ),
      ),
    );
  }
}
