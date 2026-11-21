import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/material.dart';

import 'tokens.dart';
import 'type.dart';

/// Плёночное зерно. Одна плитка шума повторяется по площади и не двигается:
/// анимированное зерно перерисовывало бы экран каждый кадр и ело батарею.
class Grain extends StatelessWidget {
  const Grain({super.key, this.opacity = 0.08});

  final double opacity;

  @override
  Widget build(BuildContext context) => IgnorePointer(
    child: DecoratedBox(
      decoration: BoxDecoration(
        image: DecorationImage(
          image: const AssetImage('assets/images/grain.png'),
          repeat: ImageRepeat.repeat,
          opacity: opacity,
          scale: 2.4,
          filterQuality: FilterQuality.none,
        ),
      ),
      child: const SizedBox.expand(),
    ),
  );
}

/// Строки развёртки старого монитора — только поверх картинок.
class Scanlines extends StatelessWidget {
  const Scanlines({super.key, this.opacity = 0.16, this.gap = 3});

  final double opacity;
  final double gap;

  @override
  Widget build(BuildContext context) => IgnorePointer(
    child: RepaintBoundary(
      child: CustomPaint(painter: _ScanlinePainter(opacity, gap), size: Size.infinite),
    ),
  );
}

class _ScanlinePainter extends CustomPainter {
  _ScanlinePainter(this.opacity, this.gap);

  final double opacity;
  final double gap;

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()..color = Colors.black.withValues(alpha: opacity);
    for (double y = 0; y < size.height; y += gap) {
      canvas.drawRect(Rect.fromLTWH(0, y, size.width, 1), paint);
    }
  }

  @override
  bool shouldRepaint(_ScanlinePainter old) => old.opacity != opacity || old.gap != gap;
}

/// Картинка-шапка: кадр, развёртка, зерно и затемнение. Сверху темнее —
/// чтобы читались часы и значки системы, снизу картинка растворяется в фоне
/// экрана, и содержимое продолжает её, а не начинается под ней.
class HeroArt extends StatelessWidget {
  const HeroArt({
    super.key,
    required this.image,
    this.alignment = Alignment.center,
    this.topShade = 0.55,
    this.bottomFade = true,
    this.dim = 0.0,
  });

  final String image;
  final Alignment alignment;
  final double topShade;
  final bool bottomFade;

  /// Общее затемнение — когда поверх картинки много текста.
  final double dim;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    return Stack(
      fit: StackFit.expand,
      children: [
        const ColoredBox(color: Color(0xFF07060E)),
        Image.asset(
          image,
          fit: BoxFit.cover,
          alignment: alignment,
          filterQuality: FilterQuality.medium,
          gaplessPlayback: true,
        ),
        const Scanlines(),
        if (dim > 0) ColoredBox(color: Colors.black.withValues(alpha: dim)),
        DecoratedBox(
          decoration: BoxDecoration(
            gradient: LinearGradient(
              begin: Alignment.topCenter,
              end: Alignment.bottomCenter,
              colors: [
                Colors.black.withValues(alpha: topShade),
                Colors.black.withValues(alpha: 0.0),
                Colors.black.withValues(alpha: 0.0),
                if (bottomFade) c.bg else Colors.black.withValues(alpha: 0.35),
              ],
              stops: const [0.0, 0.32, 0.58, 1.0],
            ),
          ),
        ),
        const Grain(opacity: 0.12),
      ],
    );
  }
}

/// Заголовок с расхождением цветовых каналов, как на старой плёнке или
/// плохом кабеле. Раз в несколько секунд каналы на миг разъезжаются сильнее.
/// Между вспышками ничего не перерисовывается.
class GlitchTitle extends StatefulWidget {
  const GlitchTitle(
    this.text, {
    super.key,
    this.size = 34,
    this.color,
    this.split = 1.4,
    this.animate = true,
  });

  final String text;
  final double size;
  final Color? color;
  final double split;
  final bool animate;

  @override
  State<GlitchTitle> createState() => _GlitchTitleState();
}

class _GlitchTitleState extends State<GlitchTitle> {
  Timer? _timer;
  bool _burst = false;
  final _random = math.Random();

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _timer?.cancel();
    final reduce = MediaQuery.maybeDisableAnimationsOf(context) ?? false;
    if (widget.animate && !reduce) _schedule();
  }

  void _schedule() {
    _timer = Timer(Duration(milliseconds: 4200 + _random.nextInt(5200)), () {
      if (!mounted) return;
      setState(() => _burst = true);
      _timer = Timer(const Duration(milliseconds: 140), () {
        if (!mounted) return;
        setState(() => _burst = false);
        _schedule();
      });
    });
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    final color = widget.color ?? c.onArt;
    final s = widget.split * (_burst ? 3.2 : 1);
    final style = T.display(color, size: widget.size);
    return Semantics(
      header: true,
      label: widget.text,
      child: ExcludeSemantics(
        child: Stack(
          children: [
            Transform.translate(
              offset: Offset(-s, _burst ? 1 : 0),
              child: Text(widget.text, style: style.copyWith(color: const Color(0xFFFF5C8A).withValues(alpha: 0.55))),
            ),
            Transform.translate(
              offset: Offset(s, _burst ? -1 : 0),
              child: Text(widget.text, style: style.copyWith(color: const Color(0xFF6FB8FF).withValues(alpha: 0.6))),
            ),
            Text(widget.text, style: style),
          ],
        ),
      ),
    );
  }
}

/// Вертикальная японская подпись по краю кадра.
class VerticalJp extends StatelessWidget {
  const VerticalJp(this.text, {super.key, this.color, this.size = 11});

  final String text;
  final Color? color;
  final double size;

  @override
  Widget build(BuildContext context) {
    final ink = color ?? context.c.onArt.withValues(alpha: 0.62);
    return ExcludeSemantics(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          for (final rune in text.runes)
            Text(
              String.fromCharCode(rune),
              style: TextStyle(fontFamily: T.jp, fontSize: size, height: 1.28, color: ink, fontWeight: FontWeight.w300),
            ),
        ],
      ),
    );
  }
}

/// Знак-сигнал истории: симметричный пиксельный узор из её id. У каждой
/// истории свой, цвет — от темы. В ленте он даёт глазу зацепку и
/// заменяет картинку, которой у текста нет.
class SignalGlyph extends StatelessWidget {
  const SignalGlyph({super.key, required this.seed, required this.color, this.size = 44, this.cells = 7});

  final String seed;
  final Color color;
  final double size;
  final int cells;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    return ExcludeSemantics(
      child: RepaintBoundary(
        child: CustomPaint(
          size: Size.square(size),
          painter: _GlyphPainter(seed: seed, color: color, ground: c.isDark ? const Color(0xFF0E0D1C) : c.raised, cells: cells),
        ),
      ),
    );
  }
}

int _fnv(String s) {
  var h = 0x811c9dc5;
  for (final unit in s.codeUnits) {
    h ^= unit;
    h = (h * 0x01000193) & 0xffffffff;
  }
  return h;
}

class _GlyphPainter extends CustomPainter {
  _GlyphPainter({required this.seed, required this.color, required this.ground, required this.cells});

  final String seed;
  final Color color;
  final Color ground;
  final int cells;

  @override
  void paint(Canvas canvas, Size size) {
    final rng = math.Random(_fnv(seed));
    final cell = size.width / cells;
    canvas.drawRect(Offset.zero & size, Paint()..color = ground);

    // Подложка: шахматный дизеринг, густеющий книзу, — как закат на картинках.
    final dither = Paint()..color = color.withValues(alpha: 0.16);
    final fine = cell / 2;
    for (var y = 0; y < cells * 2; y++) {
      final density = y / (cells * 2);
      for (var x = 0; x < cells * 2; x++) {
        final on = ((x + y) % 2 == 0) && rng.nextDouble() < density * 0.9;
        if (on) canvas.drawRect(Rect.fromLTWH(x * fine, y * fine, fine, fine), dither);
      }
    }

    // Сам узор: левая половина случайная, правая — её отражение.
    final solid = Paint()..color = color;
    final soft = Paint()..color = color.withValues(alpha: 0.45);
    final half = (cells + 1) ~/ 2;
    for (var y = 1; y < cells - 1; y++) {
      for (var x = 0; x < half; x++) {
        final r = rng.nextDouble();
        if (r > 0.58) {
          final p = r > 0.86 ? soft : solid;
          final inset = cell * 0.08;
          for (final xx in {x, cells - 1 - x}) {
            canvas.drawRect(
              Rect.fromLTWH(xx * cell + inset, y * cell + inset, cell - inset * 2, cell - inset * 2),
              p,
            );
          }
        }
      }
    }
  }

  @override
  bool shouldRepaint(_GlyphPainter old) =>
      old.seed != seed || old.color != color || old.ground != ground || old.cells != cells;
}

/// Пиксельные значки достижений: 9×9 точек, как иконки старой системы.
const pixelIcons = <String, List<String>>{
  'first_step': [
    '....X....',
    '...XXX...',
    '..X.X.X..',
    '....X....',
    '....X....',
    '....X....',
    '....X....',
    '.........',
    'XXXXXXXXX',
  ],
  'nearby': [
    '.XX...XX.',
    '.XX...XX.',
    '.........',
    'XXXX.XXXX',
    '.XX...XX.',
    '.XX...XX.',
    '.XX...XX.',
    '.X.X.X.X.',
    '.X.X.X.X.',
  ],
  'week_here': [
    'XXXXXXXXX',
    'X.......X',
    'X.X.X.X.X',
    'X.......X',
    'X.X.X.X.X',
    'X.......X',
    'X.X.X.X.X',
    'X.......X',
    'XXXXXXXXX',
  ],
  'heard': [
    '.........',
    '..XXXXX..',
    '.X.....X.',
    'X..XXX..X',
    '..X...X..',
    '....X....',
    '...XXX...',
    '....X....',
    '.........',
  ],
  'own_person': [
    '....X....',
    '...X.X...',
    '..X...X..',
    '.X.....X.',
    'XXXXXXXXX',
    '.X.....X.',
    '.X.XX..X.',
    '.X.XX..X.',
    '.XXXXXXX.',
  ],
  'night_shift': [
    '...XXX...',
    '..XX.....',
    '.XX....X.',
    '.XX......',
    '.XX......',
    '.XX......',
    '..XX.....',
    '...XXX..X',
    '.........',
  ],
};

class PixelIcon extends StatelessWidget {
  const PixelIcon(this.code, {super.key, required this.color, this.size = 36, this.glow = false});

  final String code;
  final Color color;
  final double size;
  final bool glow;

  @override
  Widget build(BuildContext context) => ExcludeSemantics(
    child: CustomPaint(
      size: Size.square(size),
      painter: _PixelPainter(pixelIcons[code] ?? pixelIcons['first_step']!, color, glow),
    ),
  );
}

class _PixelPainter extends CustomPainter {
  _PixelPainter(this.rows, this.color, this.glow);

  final List<String> rows;
  final Color color;
  final bool glow;

  @override
  void paint(Canvas canvas, Size size) {
    final n = rows.length;
    final cell = size.width / n;
    if (glow) {
      final halo = Paint()
        ..color = color.withValues(alpha: 0.35)
        ..maskFilter = const MaskFilter.blur(BlurStyle.normal, 6);
      for (var y = 0; y < n; y++) {
        for (var x = 0; x < rows[y].length; x++) {
          if (rows[y][x] == 'X') canvas.drawRect(Rect.fromLTWH(x * cell, y * cell, cell, cell), halo);
        }
      }
    }
    final p = Paint()..color = color;
    for (var y = 0; y < n; y++) {
      for (var x = 0; x < rows[y].length; x++) {
        if (rows[y][x] == 'X') {
          canvas.drawRect(Rect.fromLTWH(x * cell, y * cell, cell - 0.6, cell - 0.6), p);
        }
      }
    }
  }

  @override
  bool shouldRepaint(_PixelPainter old) => old.color != color || old.glow != glow || old.rows != rows;
}

/// Мигающий курсор терминала — признак того, что что-то идёт.
class BlinkCursor extends StatefulWidget {
  const BlinkCursor({super.key, this.color, this.width = 7, this.height = 12});

  final Color? color;
  final double width;
  final double height;

  @override
  State<BlinkCursor> createState() => _BlinkCursorState();
}

class _BlinkCursorState extends State<BlinkCursor> with SingleTickerProviderStateMixin {
  late final AnimationController _c = AnimationController(vsync: this, duration: const Duration(milliseconds: 1100));

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final reduce = MediaQuery.maybeDisableAnimationsOf(context) ?? false;
    if (reduce) {
      _c.value = 1;
    } else if (!_c.isAnimating) {
      _c.repeat();
    }
  }

  @override
  void dispose() {
    _c.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => FadeTransition(
    opacity: _c.drive(TweenSequence([
      TweenSequenceItem(tween: ConstantTween(1.0), weight: 55),
      TweenSequenceItem(tween: ConstantTween(0.0), weight: 45),
    ])),
    child: Container(width: widget.width, height: widget.height, color: widget.color ?? context.c.lilac),
  );
}
