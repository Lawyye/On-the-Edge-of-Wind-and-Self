import 'dart:math';

import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

import 'tokens.dart';

/// Моноширинная служебная подпись: всё, что говорит машина.
class TermLabel extends StatelessWidget {
  const TermLabel(
    this.text, {
    super.key,
    this.color,
    this.size = 9.5,
    this.tracking = 1.6,
    this.weight = FontWeight.w500,
  });

  final String text;
  final Color? color;
  final double size;
  final double tracking;
  final FontWeight weight;

  @override
  Widget build(BuildContext context) => Text(
    text.toUpperCase(),
    style: GoogleFonts.jetBrainsMono(
      fontSize: size,
      letterSpacing: tracking,
      fontWeight: weight,
      color: color ?? context.c.faint,
    ),
  );
}

/// Крупный заголовок с хроматическим расслоением: два цветных канала уезжают
/// в стороны, как на плохо сведённой трубке. Смещение постоянное, а не
/// мигающее — приложение и так про тяжёлое, дёргаться экрану незачем.
class GlitchText extends StatelessWidget {
  const GlitchText(
    this.text, {
    super.key,
    this.size = 29,
    this.tracking = 5,
    this.shift = 1.6,
  });

  final String text;
  final double size;
  final double tracking;

  /// На сколько пикселей разъезжаются цветные каналы.
  final double shift;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    TextStyle style(Color color) => GoogleFonts.jetBrainsMono(
      fontSize: size,
      height: 1,
      letterSpacing: tracking,
      fontWeight: FontWeight.w700,
      color: color,
    );

    final up = text.toUpperCase();
    return Stack(
      children: [
        Transform.translate(
          offset: Offset(-shift, 0),
          child: Text(up, style: style(c.magenta.withValues(alpha: 0.85))),
        ),
        Transform.translate(
          offset: Offset(shift, 0),
          child: Text(up, style: style(c.cyan.withValues(alpha: 0.85))),
        ),
        Text(up, style: style(c.bone)),
      ],
    );
  }
}

/// Пластина с уголками. Уголки вместо скруглений: интерфейс собран из
/// пластин, а не из пилюль, и рамка должна это показывать.
class Pane extends StatelessWidget {
  const Pane({
    super.key,
    required this.child,
    this.padding = const EdgeInsets.all(15),
    this.onTap,
    this.borderColor,
    this.cornerColor,
    this.fill,
  });

  final Widget child;
  final EdgeInsets padding;
  final VoidCallback? onTap;
  final Color? borderColor;
  final Color? cornerColor;
  final Color? fill;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    final body = CustomPaint(
      foregroundPainter: _CornerPainter(cornerColor ?? c.steel),
      child: Container(
        decoration: BoxDecoration(
          color: fill ?? c.panel,
          border: Border.all(color: borderColor ?? c.line),
        ),
        padding: padding,
        child: child,
      ),
    );
    if (onTap == null) return body;
    return InkWell(onTap: onTap, child: body);
  }
}

class _CornerPainter extends CustomPainter {
  _CornerPainter(this.color);
  final Color color;

  @override
  void paint(Canvas canvas, Size size) {
    final p = Paint()
      ..color = color
      ..strokeWidth = 1
      ..style = PaintingStyle.stroke;
    const n = 9.0;
    // По диагонали, а не на всех четырёх углах: рамка не выглядит замкнутой,
    // пластина словно продолжается за край экрана.
    canvas.drawPath(
      Path()
        ..moveTo(0, n)
        ..lineTo(0, 0)
        ..lineTo(n, 0),
      p,
    );
    canvas.drawPath(
      Path()
        ..moveTo(size.width, size.height - n)
        ..lineTo(size.width, size.height)
        ..lineTo(size.width - n, size.height),
      p,
    );
  }

  @override
  bool shouldRepaint(_CornerPainter old) => old.color != color;
}

/// Линия, затухающая вправо: разделитель, который не делит экран пополам.
class TermRule extends StatelessWidget {
  const TermRule({super.key, this.lead});
  final Color? lead;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    return Container(
      height: 1,
      decoration: BoxDecoration(
        gradient: LinearGradient(
          colors: [lead ?? c.cyan, c.line, c.line.withValues(alpha: 0)],
          stops: const [0, 0.7, 1],
        ),
      ),
    );
  }
}

/// Поле глифов — редкая россыпь моноширинных знаков на самой границе
/// видимости. Даёт пустым местам фактуру данных вместо плоской черноты.
/// Раскладка берётся от постоянного зерна, поэтому не пляшет при перерисовке.
class GlyphField extends StatelessWidget {
  const GlyphField({super.key, this.seed = 7, this.count = 90});

  final int seed;
  final int count;

  @override
  Widget build(BuildContext context) => IgnorePointer(
    child: RepaintBoundary(
      child: CustomPaint(
        size: Size.infinite,
        painter: _GlyphPainter(seed: seed, count: count, color: context.c.steel),
      ),
    ),
  );
}

class _GlyphPainter extends CustomPainter {
  _GlyphPainter({required this.seed, required this.count, required this.color});

  final int seed;
  final int count;
  final Color color;

  static const _glyphs = '0123456789ABCDEF/\\|-_=+<>[]{}:;.*#†‡◇△≈○╳┼╎╏';

  @override
  void paint(Canvas canvas, Size size) {
    final rnd = Random(seed);
    for (var i = 0; i < count; i++) {
      final g = _glyphs[rnd.nextInt(_glyphs.length)];
      final tp = TextPainter(
        text: TextSpan(
          text: g,
          style: TextStyle(
            fontFamily: 'monospace',
            fontSize: 9 + rnd.nextInt(4).toDouble(),
            color: color.withValues(alpha: 0.05 + rnd.nextDouble() * 0.07),
          ),
        ),
        textDirection: TextDirection.ltr,
      )..layout();
      tp.paint(
        canvas,
        Offset(rnd.nextDouble() * size.width, rnd.nextDouble() * size.height),
      );
    }
  }

  @override
  bool shouldRepaint(_GlyphPainter old) => old.color != color || old.seed != seed;
}

/// Развёртка поверх всего экрана. Чисто декоративная и не перехватывает
/// касания — иначе под ней перестаёт нажиматься интерфейс.
class Scanlines extends StatelessWidget {
  const Scanlines({super.key});

  @override
  Widget build(BuildContext context) => IgnorePointer(
    child: CustomPaint(size: Size.infinite, painter: _ScanPainter(context.c.scanline)),
  );
}

class _ScanPainter extends CustomPainter {
  _ScanPainter(this.color);
  final Color color;

  @override
  void paint(Canvas canvas, Size size) {
    final p = Paint()..color = color;
    for (double y = 0; y < size.height; y += 3) {
      canvas.drawRect(Rect.fromLTWH(0, y, size.width, 1), p);
    }
  }

  @override
  bool shouldRepaint(_ScanPainter old) => old.color != color;
}

/// Затемнение по краям: экран светится в середине, как у трубки.
class Vignette extends StatelessWidget {
  const Vignette({super.key});

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    final paper = c.voidBg.computeLuminance() > 0.5;
    return IgnorePointer(
      child: DecoratedBox(
        decoration: BoxDecoration(
          gradient: RadialGradient(
            center: const Alignment(0, -0.2),
            radius: 1.15,
            colors: [
              Colors.transparent,
              Colors.black.withValues(alpha: paper ? 0.07 : 0.62),
            ],
            stops: const [0.45, 1],
          ),
        ),
        child: const SizedBox.expand(),
      ),
    );
  }
}

/// Экран целиком: фон, поле глифов, содержимое, развёртка и затемнение.
/// Порядок важен — глифы под интерфейсом, развёртка поверх всего.
class TermScreen extends StatelessWidget {
  const TermScreen({super.key, required this.child, this.seed = 7});

  final Widget child;
  final int seed;

  @override
  Widget build(BuildContext context) => Scaffold(
    body: Stack(
      children: [
        Positioned.fill(child: GlyphField(seed: seed)),
        Positioned.fill(child: child),
        const Positioned.fill(child: Vignette()),
        const Positioned.fill(child: Scanlines()),
      ],
    ),
  );
}
