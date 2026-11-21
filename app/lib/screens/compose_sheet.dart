import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

import '../data/edgewind_store.dart';
import '../domain/categories.dart';
import '../ui/term.dart';
import '../ui/tokens.dart';

class ComposeSheet extends StatefulWidget {
  const ComposeSheet({super.key, required this.store});
  final EdgewindStore store;

  @override
  State<ComposeSheet> createState() => _ComposeSheetState();
}

class _ComposeSheetState extends State<ComposeSheet> {
  final _title = TextEditingController();
  final _body = TextEditingController();
  String _category = storyCategories.first;
  bool _sending = false;
  String? _problem;

  @override
  void dispose() {
    _title.dispose();
    _body.dispose();
    super.dispose();
  }

  bool get _filled => _title.text.trim().length >= 3 && _body.text.trim().isNotEmpty;

  Future<void> _send() async {
    if (!_filled || _sending) return;
    setState(() {
      _sending = true;
      _problem = null;
    });
    try {
      await widget.store.createStory(
        category: _category,
        title: _title.text,
        body: _body.text,
      );
      if (mounted) Navigator.of(context).pop(true);
    } catch (_) {
      if (mounted) setState(() => _problem = widget.store.error ?? 'Не отправилось.');
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    final name = widget.store.profile?.nickname ?? '';
    final bottom = MediaQuery.of(context).viewInsets.bottom;

    return Padding(
      padding: EdgeInsets.only(bottom: bottom),
      child: Container(
        decoration: BoxDecoration(
          color: c.voidBg,
          border: Border(top: BorderSide(color: c.steel)),
        ),
        child: SafeArea(
          top: false,
          child: SingleChildScrollView(
            padding: const EdgeInsets.fromLTRB(18, 16, 18, 18),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    TermLabel('НОВАЯ ЗАПИСЬ', color: c.cyan, tracking: 2.4),
                    const SizedBox(width: 10),
                    Expanded(child: Container(height: 1, color: c.line)),
                    const SizedBox(width: 10),
                    InkResponse(
                      onTap: () => Navigator.of(context).pop(false),
                      radius: 20,
                      child: Icon(Icons.close, size: 18, color: c.muted),
                    ),
                  ],
                ),
                const SizedBox(height: 9),
                TermLabel(
                  'ПОДПИСЬ: ${name.toUpperCase()} · НАСТОЯЩЕЕ ИМЯ НЕ ПЕРЕДАЁТСЯ',
                  size: 9.5,
                  tracking: 0.8,
                ),

                const SizedBox(height: 18),
                const TermLabel('ТЕМА'),
                const SizedBox(height: 9),
                Wrap(
                  spacing: 6,
                  runSpacing: 6,
                  children: [
                    for (final cat in storyCategories)
                      GestureDetector(
                        onTap: () => setState(() => _category = cat),
                        child: Container(
                          height: 28,
                          padding: const EdgeInsets.symmetric(horizontal: 11),
                          alignment: Alignment.center,
                          decoration: BoxDecoration(
                            border: Border.all(color: _category == cat ? c.cyan : c.line),
                            color: _category == cat ? c.cyan.withValues(alpha: 0.08) : null,
                          ),
                          child: TermLabel(
                            cat,
                            color: _category == cat ? c.cyan : c.muted,
                            size: 10,
                            tracking: 1.2,
                          ),
                        ),
                      ),
                  ],
                ),

                const SizedBox(height: 18),
                const TermLabel('КОРОТКО'),
                const SizedBox(height: 9),
                TextField(
                  controller: _title,
                  onChanged: (_) => setState(() {}),
                  maxLength: 120,
                  textCapitalization: TextCapitalization.sentences,
                  style: GoogleFonts.literata(fontSize: 16, color: c.bone),
                  decoration: const InputDecoration(
                    counterText: '',
                    hintText: 'Что случилось?',
                  ),
                ),

                const SizedBox(height: 14),
                const TermLabel('ПОДРОБНЕЕ'),
                const SizedBox(height: 9),
                TextField(
                  controller: _body,
                  onChanged: (_) => setState(() {}),
                  maxLines: 7,
                  maxLength: 4000,
                  textCapitalization: TextCapitalization.sentences,
                  style: GoogleFonts.literata(fontSize: 15, height: 1.55, color: c.bone),
                  decoration: const InputDecoration(
                    counterText: '',
                    hintText: 'Пиши как есть. Не надо красиво — надо честно.',
                  ),
                ),

                const SizedBox(height: 14),
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(border: Border.all(color: c.line)),
                  child: Row(
                    children: [
                      Container(width: 3, height: 24, color: c.rust),
                      const SizedBox(width: 11),
                      Expanded(
                        child: TermLabel(
                          'НЕ ПИШИ АДРЕС, ТЕЛЕФОН И ФАМИЛИИ — НИ СВОИ, НИ ЧУЖИЕ',
                          size: 9.5,
                          tracking: 0.6,
                          color: c.muted,
                        ),
                      ),
                    ],
                  ),
                ),

                if (_problem != null) ...[
                  const SizedBox(height: 12),
                  Text(_problem!, style: Theme.of(context).textTheme.bodyMedium),
                ],

                const SizedBox(height: 14),
                GestureDetector(
                  onTap: _filled && !_sending ? _send : null,
                  child: Container(
                    height: 50,
                    alignment: Alignment.center,
                    decoration: BoxDecoration(
                      border: Border.all(color: _filled ? c.cyan : c.line),
                      color: _filled ? c.cyan.withValues(alpha: 0.12) : null,
                    ),
                    child: _sending
                        ? SizedBox(
                            width: 17,
                            height: 17,
                            child: CircularProgressIndicator(strokeWidth: 1.4, color: c.cyan),
                          )
                        : TermLabel(
                            'ОТПРАВИТЬ',
                            color: _filled ? c.cyan : c.faint,
                            size: 11,
                            tracking: 3,
                            weight: FontWeight.w700,
                          ),
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
