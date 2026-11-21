import 'package:flutter/material.dart';

import '../ui/tokens.dart';
import '../ui/type.dart';

/// Знаки вместо лиц. Фото — по желанию; без него у человека остаётся знак.
const avatarGlyphs = ['┼', '◇', '△', '≈', '○', '╳'];
const avatarNames = ['перекрёсток', 'ромб', 'треугольник', 'волна', 'круг', 'крест'];
int get avatarKindCount => avatarGlyphs.length;

class Avatar extends StatelessWidget {
  const Avatar({super.key, required this.kind, this.url, this.size = 32, this.lit = false, this.onArt = false});

  final int kind;
  final String? url;
  final double size;

  /// Подсветка тёплым: свой знак, выбранный знак.
  final bool lit;

  /// Лежит поверх картинки — рамка светлее.
  final bool onArt;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    final glyph = avatarGlyphs[kind.clamp(0, avatarGlyphs.length - 1)];
    final edge = lit ? c.lamp : (onArt ? c.onArt.withValues(alpha: 0.5) : c.line);
    final px = (size * MediaQuery.devicePixelRatioOf(context)).round();

    final glyphBox = Center(
      child: Text(
        glyph,
        style: TextStyle(
          fontFamily: T.mono,
          fontSize: size * 0.46,
          height: 1,
          color: lit ? c.lamp : (onArt ? c.onArt : c.textDim),
        ),
      ),
    );

    return Semantics(
      label: 'Знак: ${avatarNames[kind.clamp(0, avatarNames.length - 1)]}',
      image: true,
      child: Container(
        width: size,
        height: size,
        decoration: BoxDecoration(
          color: onArt ? Colors.black.withValues(alpha: 0.35) : (c.isDark ? const Color(0xFF12111F) : c.raised),
          border: Border.all(color: edge, width: lit ? 1.3 : 1),
          boxShadow: lit ? [BoxShadow(color: c.lamp.withValues(alpha: 0.35), blurRadius: size * 0.35)] : null,
        ),
        clipBehavior: Clip.antiAlias,
        child: url == null
            ? glyphBox
            : Image.network(
                url!,
                fit: BoxFit.cover,
                cacheWidth: px,
                gaplessPlayback: true,
                errorBuilder: (_, __, ___) => glyphBox,
                loadingBuilder: (context, child, progress) => progress == null ? child : glyphBox,
              ),
      ),
    );
  }
}
