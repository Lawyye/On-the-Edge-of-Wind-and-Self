import 'package:flutter/material.dart';

import '../data/edgewind_store.dart';
import '../domain/categories.dart';
import '../domain/crisis.dart';
import '../ui/art.dart';
import '../ui/tokens.dart';
import '../ui/type.dart';
import '../ui/widgets.dart';
import 'help_screen.dart';

/// Новая запись. Если в тексте звучит угроза жизни, под полем появляются
/// номера, по которым ответит живой человек. Публикации это не мешает.
class ComposeScreen extends StatefulWidget {
  const ComposeScreen({super.key, required this.store});

  final EdgewindStore store;

  @override
  State<ComposeScreen> createState() => _ComposeScreenState();
}

class _ComposeScreenState extends State<ComposeScreen> {
  final _title = TextEditingController();
  final _body = TextEditingController();
  String _category = storyCategories.first;
  bool _sending = false;
  bool _crisis = false;
  String? _problem;

  EdgewindStore get _store => widget.store;

  @override
  void initState() {
    super.initState();
    void check() {
      final now = soundsLikeCrisis('${_title.text} ${_body.text}');
      if (now != _crisis) setState(() => _crisis = now);
    }

    _title.addListener(check);
    _body.addListener(check);
  }

  @override
  void dispose() {
    _title.dispose();
    _body.dispose();
    super.dispose();
  }

  bool get _dirty => _title.text.trim().isNotEmpty || _body.text.trim().isNotEmpty;
  bool get _ready => _title.text.trim().length >= 3 && _body.text.trim().isNotEmpty;

  Future<void> _publish() async {
    if (!_ready || _sending) return;
    FocusScope.of(context).unfocus();
    setState(() {
      _sending = true;
      _problem = null;
    });
    try {
      await _store.createStory(category: _category, title: _title.text, body: _body.text);
      if (mounted) Navigator.of(context).pop(true);
    } catch (e) {
      if (mounted) setState(() => _problem = _store.describe(e));
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }

  Future<void> _close() async {
    if (_dirty) {
      final leave = await confirm(
        context,
        title: 'ВЫЙТИ БЕЗ ПУБЛИКАЦИИ?',
        text: 'Написанное не сохранится.',
        action: 'ВЫЙТИ',
        danger: true,
      );
      if (!leave) return;
    }
    if (mounted) Navigator.of(context).pop(false);
  }

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    final name = _store.profile?.nickname ?? '';
    return PopScope(
      canPop: !_dirty,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) _close();
      },
      child: Scaffold(
        backgroundColor: c.bg,
        body: Column(
          children: [
            SizedBox(
              height: 128 + MediaQuery.paddingOf(context).top,
              child: Stack(
                fit: StackFit.expand,
                children: [
                  const HeroArt(image: kGirl, alignment: Alignment(0.55, -0.15), topShade: 0.45, bottomFade: false),
                  SafeArea(
                    bottom: false,
                    child: Column(
                      children: [
                        SizedBox(
                          height: 52,
                          child: Row(
                            children: [
                              IconAction(icon: Icons.close, tooltip: 'Закрыть', onTap: _close, color: c.onArt),
                              const SizedBox(width: 4),
                              Text('НОВАЯ ЗАПИСЬ', style: T.label(c.onArt, size: 12, tracking: 3, weight: FontWeight.w700)),
                              const Spacer(),
                              Padding(
                                padding: const EdgeInsets.only(right: 18),
                                child: Text('記録', style: TextStyle(fontFamily: T.jp, fontSize: 12, color: c.onArt.withValues(alpha: 0.6))),
                              ),
                            ],
                          ),
                        ),
                        const Spacer(),
                        Padding(
                          padding: const EdgeInsets.fromLTRB(20, 0, 20, 14),
                          child: Row(
                            children: [
                              Text('ПОДПИСЬ  ', style: T.label(c.onArt.withValues(alpha: 0.55), size: 9.5, tracking: 1.6)),
                              Flexible(
                                child: Text(
                                  name.toUpperCase(),
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                  style: T.label(c.lamp, size: 10, tracking: 1.6),
                                ),
                              ),
                              Flexible(
                                child: Text(
                                  '  · НАСТОЯЩЕЕ ИМЯ НЕ НУЖНО',
                                  maxLines: 1,
                                  overflow: TextOverflow.fade,
                                  softWrap: false,
                                  style: T.label(c.onArt.withValues(alpha: 0.55), size: 9.5, tracking: 1),
                                ),
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
            Expanded(
              child: ListView(
                padding: const EdgeInsets.fromLTRB(20, 18, 20, 24),
                children: [
                  Text('ТЕМА', style: T.label(c.textFaint, tracking: 2.2)),
                  const SizedBox(height: 10),
                  Wrap(
                    spacing: 6,
                    runSpacing: 8,
                    children: [
                      for (final cat in storyCategories)
                        _TopicChip(label: cat, selected: _category == cat, onTap: () => setState(() => _category = cat)),
                    ],
                  ),
                  const SizedBox(height: 22),
                  TextField(
                    controller: _title,
                    maxLength: 120,
                    textCapitalization: TextCapitalization.sentences,
                    onChanged: (_) => setState(() => _problem = null),
                    style: T.title(c.text, size: 20),
                    cursorColor: c.violet,
                    decoration: fieldDecoration(context, hint: 'Коротко: что случилось?'),
                  ),
                  const SizedBox(height: 14),
                  TextField(
                    controller: _body,
                    minLines: 7,
                    maxLines: null,
                    maxLength: 4000,
                    keyboardType: TextInputType.multiline,
                    textCapitalization: TextCapitalization.sentences,
                    onChanged: (_) => setState(() => _problem = null),
                    style: T.body(c.text, size: 16, height: 1.6),
                    cursorColor: c.violet,
                    decoration: fieldDecoration(
                      context,
                      hint: 'Пиши как есть. Не надо красиво — надо честно.',
                      counter: _body.text.length > 3500 ? '${_body.text.length} / 4000' : '',
                    ),
                  ),
                  const SizedBox(height: 16),
                  if (_crisis) ...[
                    _CrisisCard(onMore: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => const HelpScreen()))),
                    const SizedBox(height: 16),
                  ],
                  Note(
                    text: 'НЕ ПИШИ АДРЕС, ТЕЛЕФОН, ШКОЛУ И ФАМИЛИИ — НИ СВОИ, НИ ЧУЖИЕ. ЗАПИСЬ УВИДЯТ ВСЕ.',
                    color: c.violet,
                  ),
                  if (_problem != null) ...[
                    const SizedBox(height: 14),
                    Note(text: _problem!.toUpperCase(), color: c.alarm),
                  ],
                  const SizedBox(height: 22),
                  PrimaryButton(label: 'ОПУБЛИКОВАТЬ', onPressed: _ready ? _publish : null, busy: _sending),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _TopicChip extends StatelessWidget {
  const _TopicChip({required this.label, required this.selected, required this.onTap});

  final String label;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    final art = artFor(label);
    final tint = c.isDark ? art.tint : art.tintLight;
    return Semantics(
      button: true,
      selected: selected,
      child: InkWell(
        onTap: onTap,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 160),
          padding: const EdgeInsets.symmetric(horizontal: 11, vertical: 8),
          decoration: BoxDecoration(
            color: selected ? tint.withValues(alpha: 0.12) : Colors.transparent,
            border: Border.all(color: selected ? tint : c.line),
          ),
          child: Text(label.toUpperCase(), style: T.label(selected ? tint : c.textDim, size: 10, tracking: 1.2)),
        ),
      ),
    );
  }
}

/// Тёплая карточка с номерами — появляется, когда текст звучит тревожно.
class _CrisisCard extends StatelessWidget {
  const _CrisisCard({required this.onMore});

  final VoidCallback onMore;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    final first = helplines.first;
    return Container(
      padding: const EdgeInsets.fromLTRB(16, 15, 16, 14),
      decoration: BoxDecoration(
        color: c.lamp.withValues(alpha: c.isDark ? 0.07 : 0.1),
        border: Border.all(color: c.lamp.withValues(alpha: 0.6)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('ТЫ НЕ ОДИН', style: T.label(c.lamp, tracking: 2.4, weight: FontWeight.w700)),
          const SizedBox(height: 8),
          Text(
            'Похоже, тебе сейчас очень тяжело. Запись можно опубликовать — тебе ответят. '
            'А ещё можно позвонить: там выслушают сразу, бесплатно и анонимно.',
            style: T.body(c.text, size: 14.5, height: 1.5),
          ),
          const SizedBox(height: 12),
          Row(
            children: [
              Expanded(
                child: GhostButton(
                  label: 'ПОЗВОНИТЬ ${first.number}',
                  icon: Icons.call_outlined,
                  color: c.lamp,
                  height: 44,
                  onPressed: () => dial(context, first),
                ),
              ),
              const SizedBox(width: 8),
              Expanded(child: GhostButton(label: 'ВСЕ НОМЕРА', height: 44, onPressed: onMore)),
            ],
          ),
        ],
      ),
    );
  }
}
