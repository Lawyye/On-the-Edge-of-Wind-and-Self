import 'dart:math';

import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:image_picker/image_picker.dart';

import '../data/edgewind_store.dart';
import '../ui/term.dart';
import '../ui/tokens.dart';
import '../widgets/avatar.dart';

/// Знакомство: имя и знак, больше ничего. Настоящее имя не спрашивается, как
/// и почта с телефоном, — анонимность остаётся, узнаваемость появляется.
class SignupScreen extends StatefulWidget {
  const SignupScreen({super.key, required this.store, required this.onToggleTheme});

  final EdgewindStore store;
  final VoidCallback onToggleTheme;

  @override
  State<SignupScreen> createState() => _SignupScreenState();
}

class _SignupScreenState extends State<SignupScreen> {
  static const _suggestions = [
    'Соснарь', 'Полынь', 'Северный', 'Мятный Чай',
    'Кто-то с окна', 'Заря', 'Пятый Этаж', 'Тихий Спутник',
  ];

  final _controller = TextEditingController(text: 'Соснарь');
  int _mark = 0;
  bool _sending = false;
  String? _problem;

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  bool get _named => _controller.text.trim().length >= 2;

  void _suggest() {
    final pick = _suggestions[Random().nextInt(_suggestions.length)];
    setState(() {
      _controller.text = pick;
      _controller.selection = TextSelection.collapsed(offset: pick.length);
    });
  }

  Future<void> _enter({Future<void> Function()? andThen}) async {
    if (!_named || _sending) return;
    setState(() {
      _sending = true;
      _problem = null;
    });
    try {
      await widget.store.createProfile(nickname: _controller.text, avatarKind: _mark);
      if (andThen != null) await andThen();
    } catch (_) {
      if (mounted) setState(() => _problem = widget.store.error ?? 'Не получилось.');
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }

  Future<void> _pickPhoto() async {
    if (!_named) {
      setState(() => _problem = 'Сначала придумай имя — фото добавится к нему.');
      return;
    }
    final file = await ImagePicker().pickImage(
      source: ImageSource.gallery,
      maxWidth: 512,
      maxHeight: 512,
      imageQuality: 82,
    );
    if (file == null) return;
    final bytes = await file.readAsBytes();
    await _enter(andThen: () => widget.store.uploadAvatar(bytes));
  }

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    final dark = Theme.of(context).brightness == Brightness.dark;

    return TermScreen(
      child: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(20, 14, 20, 24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Container(
                    width: 7,
                    height: 7,
                    decoration: BoxDecoration(
                      color: c.phos,
                      boxShadow: [BoxShadow(color: c.phos.withValues(alpha: 0.8), blurRadius: 7)],
                    ),
                  ),
                  const SizedBox(width: 8),
                  const TermLabel('СЕАНС ОТКРЫТ'),
                  const SizedBox(width: 10),
                  Expanded(child: Container(height: 1, color: c.line)),
                  const SizedBox(width: 10),
                  InkResponse(
                    onTap: widget.onToggleTheme,
                    radius: 20,
                    child: Icon(
                      dark ? Icons.light_mode_outlined : Icons.dark_mode_outlined,
                      size: 17,
                      color: c.muted,
                    ),
                  ),
                ],
              ),

              const SizedBox(height: 20),
              const _BootLines(),

              const SizedBox(height: 24),
              const TermRule(),

              const SizedBox(height: 24),
              const GlitchText('ПРИВЕТ', size: 31),
              const SizedBox(height: 14),
              Text(
                'Иногда просто нужен разговор. Для этого всё и сделано.',
                style: Theme.of(context).textTheme.bodyLarge,
              ),

              const SizedBox(height: 26),
              const TermLabel('КАК ТЕБЯ НАЗЫВАТЬ'),
              const SizedBox(height: 9),
              TextField(
                controller: _controller,
                onChanged: (_) => setState(() {}),
                maxLength: 24,
                textCapitalization: TextCapitalization.sentences,
                style: GoogleFonts.literata(fontSize: 18, color: c.bone),
                decoration: InputDecoration(
                  counterText: '',
                  hintText: 'например, Соснарь',
                  suffixIcon: IconButton(
                    onPressed: _suggest,
                    tooltip: 'Придумать за меня',
                    icon: Icon(Icons.casino_outlined, size: 19, color: c.muted),
                  ),
                ),
              ),
              const SizedBox(height: 8),
              TermLabel(
                _named ? 'ИМЯ МОЖНО ПОМЕНЯТЬ КОГДА УГОДНО' : 'ХОТЯ БЫ ДВЕ БУКВЫ',
                tracking: 1.1,
              ),

              const SizedBox(height: 22),
              Row(
                children: [
                  const TermLabel('ЗНАК'),
                  const SizedBox(width: 10),
                  Expanded(child: Container(height: 1, color: c.line)),
                  const SizedBox(width: 10),
                  InkResponse(
                    onTap: _pickPhoto,
                    radius: 20,
                    child: Row(
                      children: [
                        Icon(Icons.file_upload_outlined, size: 15, color: c.muted),
                        const SizedBox(width: 6),
                        const TermLabel('СВОЁ ФОТО', tracking: 1.1),
                      ],
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 10),
              Row(
                children: [
                  for (var i = 0; i < avatarKindCount; i++) ...[
                    if (i > 0) const SizedBox(width: 7),
                    Expanded(
                      child: GestureDetector(
                        onTap: () => setState(() => _mark = i),
                        child: AspectRatio(
                          aspectRatio: 1,
                          child: Avatar(kind: i, size: double.infinity, lit: _mark == i),
                        ),
                      ),
                    ),
                  ],
                ],
              ),

              if (_problem != null) ...[
                const SizedBox(height: 18),
                Pane(
                  padding: const EdgeInsets.all(14),
                  borderColor: c.rust,
                  cornerColor: c.rust,
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Container(width: 3, height: 30, color: c.rust),
                      const SizedBox(width: 11),
                      Expanded(
                        child: Text(_problem!, style: Theme.of(context).textTheme.bodyMedium),
                      ),
                    ],
                  ),
                ),
              ],

              const SizedBox(height: 22),
              Container(
                padding: const EdgeInsets.all(13),
                decoration: BoxDecoration(border: Border.all(color: c.line)),
                child: Row(
                  children: [
                    Container(width: 3, height: 30, color: c.phos),
                    const SizedBox(width: 11),
                    Expanded(
                      child: TermLabel(
                        'НИ ПОЧТЫ, НИ ТЕЛЕФОНА, НИ ПАРОЛЯ.\nИМЯ И ЗНАК — ВСЁ, ЧТО О ТЕБЕ БУДЕТ ИЗВЕСТНО.',
                        size: 10,
                        tracking: 0.6,
                        color: c.muted,
                      ),
                    ),
                  ],
                ),
              ),

              const SizedBox(height: 14),
              GestureDetector(
                onTap: _named && !_sending ? _enter : null,
                child: Container(
                  height: 54,
                  alignment: Alignment.center,
                  decoration: BoxDecoration(
                    border: Border.all(color: _named ? c.cyan : c.line),
                    color: _named ? c.cyan.withValues(alpha: 0.12) : null,
                  ),
                  child: _sending
                      ? SizedBox(
                          width: 18,
                          height: 18,
                          child: CircularProgressIndicator(strokeWidth: 1.4, color: c.cyan),
                        )
                      : TermLabel(
                          _named ? 'ВОЙТИ' : 'ПРИДУМАЙ ИМЯ',
                          color: _named ? c.cyan : c.faint,
                          size: 11.5,
                          tracking: 3.4,
                          weight: FontWeight.w700,
                        ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// Заставка сообщает не версию ядра, а то, что человеку важно знать до входа.
class _BootLines extends StatelessWidget {
  const _BootLines();

  static const _lines = [
    ('OK', 'связь установлена', true),
    ('OK', 'имя придумываешь сам', true),
    ('OK', 'настоящее имя не передаётся', true),
    ('--', 'почта не требуется', false),
    ('--', 'пароль не требуется', false),
  ];

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        for (final (mark, text, ok) in _lines)
          Padding(
            padding: const EdgeInsets.only(bottom: 5),
            child: Row(
              children: [
                SizedBox(
                  width: 20,
                  child: TermLabel(mark, size: 10, color: ok ? c.phos : c.faint),
                ),
                TermLabel(text, size: 10.5, tracking: 0.7, color: ok ? c.muted : c.faint),
              ],
            ),
          ),
      ],
    );
  }
}
