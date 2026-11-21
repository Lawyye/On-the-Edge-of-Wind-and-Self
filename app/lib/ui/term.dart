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
      foregroundPainter: _CornerPainter(cornerColor ?? c.amberDim),
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
    // Левый верхний и правый нижний — по диагонали, чтобы рамка не выглядела
    // замкнутой: пластина продолжается за край экрана.
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
          colors: [lead ?? c.amber, c.line, c.line.withValues(alpha: 0)],
          stops: const [0, 0.7, 1],
        ),
      ),
    );
  }
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
  Widget build(BuildContext context) => IgnorePointer(
    child: DecoratedBox(
      decoration: BoxDecoration(
        gradient: RadialGradient(
          center: const Alignment(0, -0.2),
          radius: 1.1,
          colors: [
            Colors.transparent,
            Colors.black.withValues(alpha: context.c.voidBg.computeLuminance() > 0.5 ? 0.06 : 0.5),
          ],
          stops: const [0.55, 1],
        ),
      ),
      child: const SizedBox.expand(),
    ),
  );
}

/// Экран целиком: фон, содержимое, развёртка и затемнение поверх.
class TermScreen extends StatelessWidget {
  const TermScreen({super.key, required this.child});
  final Widget child;

  @override
  Widget build(BuildContext context) => Scaffold(
    body: Stack(
      children: [
        Positioned.fill(child: child),
        const Positioned.fill(child: Vignette()),
        const Positioned.fill(child: Scanlines()),
      ],
    ),
  );
}
