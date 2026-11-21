import 'package:flutter/material.dart';

/// Палитра терминала. Тёмная — основная: весь язык интерфейса построен на
/// свечении на чёрном. Светлая существует для тех, кому тёмное неудобно, и
/// сделана не инверсией, а «бумажным» вариантом того же терминала.
///
/// Принцип, на котором держится всё оформление: ОБВЯЗКА ХОЛОДНАЯ, ЛЮДИ ТЁПЛЫЕ.
/// Служебное набрано моноширинным в верхнем регистре, а то, что человек
/// написал сам, — книжной гарнитурой цветом [bone], и это единственное
/// по-настоящему тёплое пятно на экране.
@immutable
class EdgeColors extends ThemeExtension<EdgeColors> {
  const EdgeColors({
    required this.voidBg,
    required this.panel,
    required this.line,
    required this.amber,
    required this.amberDim,
    required this.rust,
    required this.bone,
    required this.boneDim,
    required this.muted,
    required this.faint,
    required this.phos,
    required this.scanline,
  });

  /// Фон приложения.
  final Color voidBg;

  /// Пластина карточки.
  final Color panel;

  /// Разделители и рамки.
  final Color line;

  /// Главный акцент — янтарь CRT.
  final Color amber;

  /// Приглушённый янтарь: уголки рамок, неяркие рамки.
  final Color amberDim;

  /// Тревожный. Им горит «я рядом» и предупреждения — больше ничем.
  final Color rust;

  /// Цвет человеческого текста.
  final Color bone;

  /// Человеческий текст второго плана.
  final Color boneDim;

  final Color muted;
  final Color faint;

  /// Фосфорная зелень: «всё хорошо», живой индикатор.
  final Color phos;

  /// Цвет полос развёртки поверх экрана.
  final Color scanline;

  static const dark = EdgeColors(
    voidBg: Color(0xFF0A0C0B),
    panel: Color(0xFF121716),
    line: Color(0xFF222A28),
    amber: Color(0xFFE8A33D),
    amberDim: Color(0xFF7A5620),
    rust: Color(0xFFC4563B),
    bone: Color(0xFFD9D5C8),
    boneDim: Color(0xFFA8A598),
    muted: Color(0xFF8A8878),
    faint: Color(0xFF55584F),
    phos: Color(0xFF7FB08A),
    scanline: Color(0x09FFFFFF),
  );

  /// «Бумажный терминал»: распечатка, а не экран. Янтарь уходит в охру,
  /// иначе на светлом он слепнет и перестаёт читаться как акцент.
  static const light = EdgeColors(
    voidBg: Color(0xFFE8E4D8),
    panel: Color(0xFFF2EFE5),
    line: Color(0xFFCFC9B8),
    amber: Color(0xFF9A6A18),
    amberDim: Color(0xFFC2A268),
    rust: Color(0xFF9E3B23),
    bone: Color(0xFF1E211C),
    boneDim: Color(0xFF454840),
    muted: Color(0xFF6B6E62),
    faint: Color(0xFF8E9184),
    phos: Color(0xFF3C6B47),
    scanline: Color(0x07000000),
  );

  @override
  EdgeColors copyWith() => this;

  @override
  EdgeColors lerp(ThemeExtension<EdgeColors>? other, double t) {
    if (other is! EdgeColors) return this;
    Color m(Color a, Color b) => Color.lerp(a, b, t)!;
    return EdgeColors(
      voidBg: m(voidBg, other.voidBg),
      panel: m(panel, other.panel),
      line: m(line, other.line),
      amber: m(amber, other.amber),
      amberDim: m(amberDim, other.amberDim),
      rust: m(rust, other.rust),
      bone: m(bone, other.bone),
      boneDim: m(boneDim, other.boneDim),
      muted: m(muted, other.muted),
      faint: m(faint, other.faint),
      phos: m(phos, other.phos),
      scanline: m(scanline, other.scanline),
    );
  }
}

extension EdgeColorsOf on BuildContext {
  EdgeColors get c => Theme.of(this).extension<EdgeColors>()!;
}
