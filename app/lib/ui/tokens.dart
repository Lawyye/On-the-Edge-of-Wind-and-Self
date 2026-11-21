import 'package:flutter/material.dart';

/// Палитра снята с трёх картинок приложения: индиго ночной комнаты, сирень
/// закатного неба над проводами, голубое свечение монитора и тёплый свет
/// окна. Тёплый цвет один и редкий — им горит только человеческое: «я рядом»,
/// полученные достижения, свои отклики.
@immutable
class EdgeColors extends ThemeExtension<EdgeColors> {
  const EdgeColors({
    required this.isDark,
    required this.bg,
    required this.panel,
    required this.raised,
    required this.line,
    required this.text,
    required this.textDim,
    required this.textFaint,
    required this.violet,
    required this.lilac,
    required this.crt,
    required this.lamp,
    required this.alarm,
    required this.button,
    required this.onButton,
  });

  final bool isDark;

  /// Фон приложения.
  final Color bg;

  /// Листы, поля ввода, меню.
  final Color panel;

  /// То, что лежит поверх панели: выбранная плитка, нажатая строка.
  final Color raised;

  /// Волоски-разделители и рамки.
  final Color line;

  final Color text;
  final Color textDim;
  final Color textFaint;

  /// Сирень заката — выбор, ссылки, активная вкладка.
  final Color violet;
  final Color lilac;

  /// Голубое свечение монитора — служебное: время, счётчики, сигнал.
  final Color crt;

  /// Тёплый свет — только человеческое.
  final Color lamp;

  /// Ошибки и жалобы.
  final Color alarm;

  /// Главная кнопка: белая плашка на тёмном, чернильная на светлом.
  final Color button;
  final Color onButton;

  /// Текст поверх картинок всегда светлый — картинки тёмные в обеих темах.
  Color get onArt => const Color(0xFFF4F2FB);

  static const dark = EdgeColors(
    isDark: true,
    bg: Color(0xFF08070F),
    panel: Color(0xFF111120),
    raised: Color(0xFF1A1930),
    line: Color(0xFF25243B),
    text: Color(0xFFEEEDF6),
    textDim: Color(0xFFA9A7C4),
    textFaint: Color(0xFF6E6C8E),
    violet: Color(0xFF8F86EE),
    lilac: Color(0xFFC8BBF3),
    crt: Color(0xFFA6C6FF),
    lamp: Color(0xFFFFC56E),
    alarm: Color(0xFFFF6F86),
    button: Color(0xFFF2F0FA),
    onButton: Color(0xFF0A0912),
  );

  static const light = EdgeColors(
    isDark: false,
    bg: Color(0xFFF3F1F9),
    panel: Color(0xFFFFFFFF),
    raised: Color(0xFFE9E6F5),
    line: Color(0xFFDAD6EB),
    text: Color(0xFF17152B),
    textDim: Color(0xFF4D4A6B),
    textFaint: Color(0xFF8C89AA),
    violet: Color(0xFF5A4ED6),
    lilac: Color(0xFF6E5FD0),
    crt: Color(0xFF2D6BD8),
    lamp: Color(0xFFB8740F),
    alarm: Color(0xFFD62F4E),
    button: Color(0xFF17152B),
    onButton: Color(0xFFF7F6FC),
  );

  @override
  EdgeColors copyWith() => this;

  @override
  EdgeColors lerp(ThemeExtension<EdgeColors>? other, double t) {
    if (other is! EdgeColors) return this;
    Color m(Color a, Color b) => Color.lerp(a, b, t)!;
    return EdgeColors(
      isDark: t < 0.5 ? isDark : other.isDark,
      bg: m(bg, other.bg),
      panel: m(panel, other.panel),
      raised: m(raised, other.raised),
      line: m(line, other.line),
      text: m(text, other.text),
      textDim: m(textDim, other.textDim),
      textFaint: m(textFaint, other.textFaint),
      violet: m(violet, other.violet),
      lilac: m(lilac, other.lilac),
      crt: m(crt, other.crt),
      lamp: m(lamp, other.lamp),
      alarm: m(alarm, other.alarm),
      button: m(button, other.button),
      onButton: m(onButton, other.onButton),
    );
  }
}

extension EdgeColorsX on BuildContext {
  EdgeColors get c => Theme.of(this).extension<EdgeColors>()!;
}
