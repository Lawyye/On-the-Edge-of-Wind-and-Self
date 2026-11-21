import 'dart:math';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:image_picker/image_picker.dart';

import '../data/edgewind_store.dart';
import '../domain/categories.dart';
import '../domain/names.dart';
import '../ui/art.dart';
import '../ui/format.dart';
import '../ui/tokens.dart';
import '../ui/type.dart';
import '../ui/widgets.dart';
import '../widgets/avatar.dart';

/// Знакомство в два шага. Сначала — кадр и что это за место. Потом — имя и
/// знак. Настоящее имя не спрашивается нигде, почта и телефон — тоже.
class WelcomeScreen extends StatefulWidget {
  const WelcomeScreen({super.key, required this.store, this.startAtName = false});

  final EdgewindStore store;

  /// Для проверки экрана в тестах — сразу второй шаг.
  final bool startAtName;

  @override
  State<WelcomeScreen> createState() => _WelcomeScreenState();
}

class _WelcomeScreenState extends State<WelcomeScreen> {
  late bool _naming = widget.startAtName;

  @override
  Widget build(BuildContext context) {
    return AnnotatedRegion<SystemUiOverlayStyle>(
      value: SystemUiOverlayStyle.light.copyWith(statusBarColor: Colors.transparent),
      child: PopScope(
        canPop: !_naming,
        onPopInvokedWithResult: (didPop, _) {
          if (!didPop && _naming) setState(() => _naming = false);
        },
        child: Scaffold(
          backgroundColor: const Color(0xFF07060E),
          resizeToAvoidBottomInset: true,
          body: Stack(
            fit: StackFit.expand,
            children: [
              AnimatedOpacity(
                duration: const Duration(milliseconds: 380),
                opacity: 1,
                child: HeroArt(
                  image: kGirl,
                  alignment: const Alignment(0.18, -0.1),
                  dim: _naming ? 0.5 : 0.0,
                  topShade: 0.45,
                ),
              ),
              AnimatedSwitcher(
                duration: const Duration(milliseconds: 320),
                switchInCurve: Curves.easeOut,
                switchOutCurve: Curves.easeIn,
                child: _naming
                    ? _NameStep(key: const ValueKey('name'), store: widget.store, onBack: () => setState(() => _naming = false))
                    : _IntroStep(key: const ValueKey('intro'), onEnter: () => setState(() => _naming = true)),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _IntroStep extends StatelessWidget {
  const _IntroStep({super.key, required this.onEnter});

  final VoidCallback onEnter;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    final dim = c.onArt.withValues(alpha: 0.58);
    return SafeArea(
      child: Stack(
        children: [
          Positioned(
            left: 24,
            top: 18,
            child: Row(
              children: [
                Container(
                  width: 6,
                  height: 6,
                  decoration: BoxDecoration(
                    color: const Color(0xFF7CF0B4),
                    boxShadow: [BoxShadow(color: const Color(0xFF7CF0B4).withValues(alpha: 0.8), blurRadius: 8)],
                  ),
                ),
                const SizedBox(width: 9),
                Text('СЕЙЧАС · ${clock(DateTime.now())}', style: T.label(dim, size: 10, tracking: 2)),
              ],
            ),
          ),
          const Positioned(right: 20, top: 56, child: VerticalJp('風と私のはざまで', size: 12)),
          Positioned(
            left: 24,
            right: 24,
            bottom: 26,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const GlitchTitle('ON THE EDGE', size: 36),
                const SizedBox(height: 10),
                Text('OF WIND AND SELF', style: T.label(c.lilac, size: 11, tracking: 5.5)),
                const SizedBox(height: 20),
                Text(
                  'Место, где можно сказать как есть. Тебя выслушают такие же люди — без имён и без оценок.',
                  style: T.body(c.onArt, size: 17, height: 1.5),
                ),
                const SizedBox(height: 22),
                const _Credits(rows: [
                  ('ИМЯ', 'придумаешь сам'),
                  ('ПОЧТА', 'не нужна'),
                  ('ПАРОЛЬ', 'не нужен'),
                  ('ГОЛОС', 'твой'),
                ]),
                const SizedBox(height: 26),
                PrimaryButton(label: 'ВОЙТИ', onPressed: onEnter),
                const SizedBox(height: 12),
                Center(
                  child: Text(
                    'Здесь отвечают люди, не врачи. Если опасно прямо сейчас — 112.',
                    textAlign: TextAlign.center,
                    style: T.label(dim, size: 9.5, tracking: 0.6, height: 1.5),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

/// Сетка «как в титрах»: слева что, справа как.
class _Credits extends StatelessWidget {
  const _Credits({required this.rows});

  final List<(String, String)> rows;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    return Column(
      children: [
        for (final (k, v) in rows)
          Padding(
            padding: const EdgeInsets.only(bottom: 6),
            child: Row(
              children: [
                SizedBox(width: 92, child: Text(k, style: T.label(c.onArt.withValues(alpha: 0.5), size: 10, tracking: 2.2))),
                Text(v, style: T.label(c.onArt.withValues(alpha: 0.92), size: 11, tracking: 0.6)),
              ],
            ),
          ),
      ],
    );
  }
}

class _NameStep extends StatefulWidget {
  const _NameStep({super.key, required this.store, required this.onBack});

  final EdgewindStore store;
  final VoidCallback onBack;

  @override
  State<_NameStep> createState() => _NameStepState();
}

class _NameStepState extends State<_NameStep> {
  final _random = Random();
  late final _name = TextEditingController(text: randomNickname(_random));
  late int _kind = _random.nextInt(avatarKindCount);
  Uint8List? _photo;
  bool _sending = false;
  String? _problem;

  @override
  void dispose() {
    _name.dispose();
    super.dispose();
  }

  String? get _nameProblem => nicknameProblem(_name.text);

  void _dice() {
    final pick = randomNickname(_random);
    setState(() {
      _name.text = pick;
      _name.selection = TextSelection.collapsed(offset: pick.length);
      _problem = null;
    });
  }

  Future<void> _pickPhoto() async {
    try {
      final file = await ImagePicker().pickImage(
        source: ImageSource.gallery,
        maxWidth: 512,
        maxHeight: 512,
        imageQuality: 85,
      );
      if (file == null) return;
      final bytes = await file.readAsBytes();
      if (imageKind(bytes) == null) {
        setState(() => _problem = 'Это фото не подходит. Нужен JPG, PNG или WEBP.');
        return;
      }
      setState(() {
        _photo = bytes;
        _problem = null;
      });
    } catch (_) {
      setState(() => _problem = 'Не удалось открыть галерею.');
    }
  }

  Future<void> _done() async {
    final problem = _nameProblem;
    if (problem != null) {
      setState(() => _problem = problem);
      return;
    }
    FocusScope.of(context).unfocus();
    setState(() {
      _sending = true;
      _problem = null;
    });
    try {
      await widget.store.createProfile(nickname: _name.text, avatarKind: _kind, photo: _photo);
    } catch (e) {
      if (!mounted) return;
      final text = widget.store.describe(e);
      setState(() {
        _problem = text;
        // Имя занято — сразу подставляем другое, чтобы не гадать вслепую.
        if (text.contains('занято')) {
          final pick = randomNickname(_random);
          _name.text = pick;
          _problem = '$text Например, «$pick».';
        }
      });
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    final art = c.onArt;
    final ok = _nameProblem == null;

    return SafeArea(
      child: Column(
        children: [
          SizedBox(
            height: 56,
            child: Row(
              children: [
                IconAction(icon: Icons.arrow_back, tooltip: 'Назад', onTap: widget.onBack, color: art),
                const SizedBox(width: 4),
                Text('ЗНАКОМСТВО', style: T.label(art, size: 12, tracking: 3, weight: FontWeight.w700)),
              ],
            ),
          ),
          Expanded(
            child: SingleChildScrollView(
              padding: const EdgeInsets.fromLTRB(24, 24, 24, 28),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('КАК ТЕБЯ НАЗЫВАТЬ', style: T.label(art.withValues(alpha: 0.6), tracking: 2.2)),
                  const SizedBox(height: 6),
                  TextField(
                    controller: _name,
                    maxLength: 24,
                    textCapitalization: TextCapitalization.sentences,
                    onChanged: (_) => setState(() => _problem = null),
                    onSubmitted: (_) => _done(),
                    cursorColor: c.lilac,
                    style: T.title(art, size: 24),
                    decoration: InputDecoration(
                      counterText: '',
                      isDense: true,
                      contentPadding: const EdgeInsets.symmetric(vertical: 12),
                      enabledBorder: UnderlineInputBorder(borderSide: BorderSide(color: art.withValues(alpha: 0.3))),
                      focusedBorder: UnderlineInputBorder(borderSide: BorderSide(color: c.lilac, width: 1.5)),
                      suffixIcon: IconAction(
                        icon: Icons.casino_outlined,
                        tooltip: 'Придумать другое',
                        onTap: _dice,
                        color: art.withValues(alpha: 0.8),
                      ),
                    ),
                  ),
                  const SizedBox(height: 10),
                  Text(
                    'Можно оставить или придумать своё. Настоящее имя здесь не нужно — и лучше его не писать.',
                    style: T.body(art.withValues(alpha: 0.72), size: 13.5, height: 1.5),
                  ),
                  const SizedBox(height: 30),
                  Text('ЗНАК', style: T.label(art.withValues(alpha: 0.6), tracking: 2.2)),
                  const SizedBox(height: 12),
                  Wrap(
                    spacing: 10,
                    runSpacing: 10,
                    children: [
                      for (var i = 0; i < avatarKindCount; i++)
                        Semantics(
                          button: true,
                          selected: _photo == null && _kind == i,
                          label: 'Знак ${avatarNames[i]}',
                          child: GestureDetector(
                            onTap: () => setState(() {
                              _kind = i;
                              _photo = null;
                            }),
                            child: Avatar(kind: i, size: 48, onArt: true, lit: _photo == null && _kind == i),
                          ),
                        ),
                      _PhotoTile(photo: _photo, onPick: _pickPhoto, onClear: () => setState(() => _photo = null)),
                    ],
                  ),
                  if (_problem != null) ...[
                    const SizedBox(height: 22),
                    Container(
                      padding: const EdgeInsets.fromLTRB(14, 12, 14, 12),
                      decoration: BoxDecoration(
                        color: Colors.black.withValues(alpha: 0.45),
                        border: Border(left: BorderSide(color: c.alarm, width: 2)),
                      ),
                      child: Text(_problem!, style: T.body(art, size: 14, height: 1.45)),
                    ),
                  ],
                  const SizedBox(height: 30),
                  PrimaryButton(label: 'ГОТОВО', onPressed: ok ? _done : null, busy: _sending),
                  const SizedBox(height: 14),
                  Text(
                    'Имя и знак можно поменять потом в профиле.',
                    style: T.label(art.withValues(alpha: 0.55), size: 9.5, tracking: 0.6),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _PhotoTile extends StatelessWidget {
  const _PhotoTile({required this.photo, required this.onPick, required this.onClear});

  final Uint8List? photo;
  final VoidCallback onPick;
  final VoidCallback onClear;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    final has = photo != null;
    return Semantics(
      button: true,
      label: has ? 'Убрать фото' : 'Выбрать фото',
      child: GestureDetector(
        onTap: has ? onClear : onPick,
        child: Container(
          width: 48,
          height: 48,
          decoration: BoxDecoration(
            color: Colors.black.withValues(alpha: 0.35),
            border: Border.all(color: has ? c.lamp : c.onArt.withValues(alpha: 0.5), width: has ? 1.3 : 1),
            boxShadow: has ? [BoxShadow(color: c.lamp.withValues(alpha: 0.35), blurRadius: 16)] : null,
          ),
          clipBehavior: Clip.antiAlias,
          child: has
              ? Image.memory(photo!, fit: BoxFit.cover, cacheWidth: 144)
              : Icon(Icons.add_photo_alternate_outlined, size: 20, color: c.onArt.withValues(alpha: 0.85)),
        ),
      ),
    );
  }
}
