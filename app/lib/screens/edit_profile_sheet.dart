import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';

import '../data/edgewind_store.dart';
import '../domain/names.dart';
import '../ui/tokens.dart';
import '../ui/type.dart';
import '../ui/widgets.dart';
import '../widgets/avatar.dart';

/// Имя, знак и фото — поменять в любой момент.
class EditProfileSheet extends StatefulWidget {
  const EditProfileSheet({super.key, required this.store});

  final EdgewindStore store;

  @override
  State<EditProfileSheet> createState() => _EditProfileSheetState();
}

class _EditProfileSheetState extends State<EditProfileSheet> {
  late final _name = TextEditingController(text: widget.store.profile?.nickname ?? '');
  bool _busy = false;
  String? _problem;

  EdgewindStore get _store => widget.store;

  @override
  void dispose() {
    _name.dispose();
    super.dispose();
  }

  Future<void> _run(Future<void> Function() job, {String? done}) async {
    setState(() {
      _busy = true;
      _problem = null;
    });
    try {
      await job();
      if (done != null) _store.say(done);
    } catch (e) {
      if (mounted) setState(() => _problem = _store.describe(e));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _saveName() async {
    final problem = nicknameProblem(_name.text);
    if (problem != null) {
      setState(() => _problem = problem);
      return;
    }
    if (_name.text.trim() == _store.profile?.nickname) return;
    FocusScope.of(context).unfocus();
    await _run(() => _store.rename(_name.text), done: 'Имя изменено.');
  }

  Future<void> _photo() async {
    try {
      final file = await ImagePicker().pickImage(
        source: ImageSource.gallery,
        maxWidth: 512,
        maxHeight: 512,
        imageQuality: 85,
      );
      if (file == null) return;
      final bytes = await file.readAsBytes();
      await _run(() => _store.setPhoto(bytes), done: 'Фото обновлено.');
    } catch (_) {
      if (mounted) setState(() => _problem = 'Не удалось открыть галерею.');
    }
  }

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    final bottom = MediaQuery.viewInsetsOf(context).bottom;
    return ListenableBuilder(
      listenable: _store,
      builder: (context, _) {
        final p = _store.profile;
        if (p == null) return const SizedBox.shrink();
        return Padding(
          padding: EdgeInsets.only(bottom: bottom),
          child: SingleChildScrollView(
            padding: const EdgeInsets.fromLTRB(22, 18, 22, 26),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Text('ИМЯ И ЗНАК', style: T.label(c.text, size: 12, tracking: 2.6, weight: FontWeight.w700)),
                    const Spacer(),
                    if (_busy) SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 1.5, color: c.violet)),
                    IconAction(icon: Icons.close, tooltip: 'Закрыть', onTap: () => Navigator.of(context).pop()),
                  ],
                ),
                const SizedBox(height: 14),
                TextField(
                  controller: _name,
                  maxLength: 24,
                  textCapitalization: TextCapitalization.sentences,
                  onSubmitted: (_) => _saveName(),
                  style: T.title(c.text, size: 21),
                  decoration: fieldDecoration(
                    context,
                    hint: 'Имя',
                    suffix: IconAction(icon: Icons.check_rounded, tooltip: 'Сохранить имя', onTap: _saveName, color: c.violet),
                  ),
                ),
                const SizedBox(height: 22),
                Text('ЗНАК', style: T.label(c.textFaint, tracking: 2.2)),
                const SizedBox(height: 12),
                Wrap(
                  spacing: 10,
                  runSpacing: 10,
                  children: [
                    for (var i = 0; i < avatarKindCount; i++)
                      Semantics(
                        button: true,
                        selected: p.avatarUrl == null && p.avatarKind == i,
                        label: 'Знак ${avatarNames[i]}',
                        child: GestureDetector(
                          onTap: _busy ? null : () => _run(() => _store.setGlyph(i)),
                          child: Avatar(kind: i, size: 48, lit: p.avatarUrl == null && p.avatarKind == i),
                        ),
                      ),
                  ],
                ),
                const SizedBox(height: 18),
                Row(
                  children: [
                    if (p.avatarUrl != null) ...[
                      Avatar(kind: p.avatarKind, url: p.avatarUrl, size: 48, lit: true),
                      const SizedBox(width: 14),
                    ],
                    Expanded(
                      child: GhostButton(
                        label: p.avatarUrl == null ? 'ПОСТАВИТЬ ФОТО' : 'ДРУГОЕ ФОТО',
                        icon: Icons.add_photo_alternate_outlined,
                        onPressed: _busy ? null : _photo,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 10),
                Text(
                  'Лучше не своё лицо: фото видят все, кто читает твои записи.',
                  style: T.label(c.textFaint, size: 9.5, tracking: 0.5, height: 1.5),
                ),
                if (_problem != null) ...[
                  const SizedBox(height: 16),
                  Note(text: _problem!.toUpperCase(), color: c.alarm),
                ],
              ],
            ),
          ),
        );
      },
    );
  }
}
