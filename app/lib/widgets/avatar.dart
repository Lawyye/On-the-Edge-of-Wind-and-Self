import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

import '../ui/tokens.dart';

/// Знаки вместо лиц. Приложение анонимное, поэтому аватар по умолчанию —
/// начертание, а не физиономия. Своё фото, если человек его загрузил, побеждает.
const List<String> avatarGlyphs = ['┼', '◇', '△', '≈', '○', '╳'];
const int avatarKindCount = 6;

class Avatar extends StatelessWidget {
  const Avatar({
    super.key,
    required this.kind,
    this.url,
    this.size = 34,
    this.lit = false,
  });

  final int kind;
  final String? url;
  final double size;

  /// Подсвеченный вариант — для своего профиля и выбранного знака.
  final bool lit;

  @override
  Widget build(BuildContext context) {
    // Размер может прийти бесконечным, когда плитка растягивается по месту
    // (так сделан выбор знака при знакомстве). Тогда берём его у родителя:
    // иначе кегль глифа считается от бесконечности и экран падает.
    if (!size.isFinite) {
      return LayoutBuilder(
        builder: (context, box) => Avatar(
          kind: kind,
          url: url,
          lit: lit,
          size: box.biggest.shortestSide,
        ),
      );
    }

    final c = context.c;
    final edge = lit ? c.amber : c.amberDim;

    final Widget inner = (url != null && url!.isNotEmpty)
        ? Image.network(
            url!,
            width: size,
            height: size,
            fit: BoxFit.cover,
            errorBuilder: (_, __, ___) => _glyph(c, edge),
          )
        : _glyph(c, edge);

    return Container(
      width: size,
      height: size,
      decoration: BoxDecoration(
        border: Border.all(color: edge),
        color: c.amber.withValues(alpha: lit ? 0.1 : 0.05),
      ),
      clipBehavior: Clip.hardEdge,
      child: Center(child: inner),
    );
  }

  Widget _glyph(EdgeColors c, Color edge) => Text(
    avatarGlyphs[kind.abs() % avatarKindCount],
    style: GoogleFonts.jetBrainsMono(
      fontSize: size * 0.42,
      fontWeight: FontWeight.w700,
      color: lit ? c.amber : c.muted,
      height: 1,
    ),
  );
}
