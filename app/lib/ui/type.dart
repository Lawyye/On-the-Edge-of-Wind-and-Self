import 'package:flutter/material.dart';

/// Две гарнитуры, две роли. Машина говорит моноширинным: метки, время,
/// счётчики, кнопки. Человек — книжной антиквой: заголовки и тексты историй,
/// отклики. Японский — только украшение по краю, как подписи на кадрах.
abstract final class T {
  static const mono = 'JetBrainsMono';
  static const serif = 'Literata';
  static const jp = 'NotoSansJP';

  /// Большие заголовки экранов. Тонкий моноширинный с разрядкой.
  static TextStyle display(Color color, {double size = 34}) => TextStyle(
    fontFamily: mono,
    fontWeight: FontWeight.w300,
    fontSize: size,
    letterSpacing: size * 0.14,
    height: 1.0,
    color: color,
  );

  /// Служебные подписи: всегда заглавными, с разрядкой.
  static TextStyle label(
    Color color, {
    double size = 10.5,
    double tracking = 1.4,
    FontWeight weight = FontWeight.w400,
    double height = 1.3,
  }) => TextStyle(
    fontFamily: mono,
    fontSize: size,
    letterSpacing: tracking,
    fontWeight: weight,
    height: height,
    color: color,
  );

  static TextStyle button(Color color, {double size = 12}) => TextStyle(
    fontFamily: mono,
    fontSize: size,
    letterSpacing: size * 0.22,
    fontWeight: FontWeight.w700,
    height: 1.0,
    color: color,
  );

  /// Заголовок истории.
  static TextStyle title(Color color, {double size = 19}) => TextStyle(
    fontFamily: serif,
    fontWeight: FontWeight.w600,
    fontSize: size,
    height: 1.3,
    color: color,
  );

  /// Текст, который написал человек.
  static TextStyle body(Color color, {double size = 15.5, double height = 1.62}) => TextStyle(
    fontFamily: serif,
    fontSize: size,
    height: height,
    color: color,
  );

  static TextStyle italic(Color color, {double size = 14.5}) => TextStyle(
    fontFamily: serif,
    fontStyle: FontStyle.italic,
    fontSize: size,
    height: 1.55,
    color: color,
  );
}
