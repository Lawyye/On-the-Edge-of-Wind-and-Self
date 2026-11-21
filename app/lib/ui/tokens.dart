import 'package:flutter/material.dart';

/// Палитра терминала.
///
/// Принцип, на котором держится всё оформление: ОБВЯЗКА ХОЛОДНАЯ, ЛЮДИ ТЁПЛЫЕ.
/// Машинная часть — сталь, электрический циан, развёртка и глитч; всё, что
/// человек написал сам, набрано книжной гарнитурой цветом [bone] и остаётся
/// единственным тёплым пятном на экране. Чем холоднее обвязка, тем сильнее
/// видно живой текст, поэтому синее здесь намеренно доведено до предела.
@immutable
class EdgeColors extends ThemeExtension<EdgeColors> {
  const EdgeColors({
    required this.voidBg,
    required this.panel,
    required this.line,
    required this.steel,
    required this.cyan,
    required this.magenta,
    required this.amber,
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

  /// Холодная сталь: уголки рамок, служебные подписи второго плана.
  final Color steel;

  /// Главный машинный акцент — электрический циан.
  final Color cyan;

  /// Второй канал расслоения при глитче. Как самостоятельный цвет не ставится.
  final Color magenta;

  /// Тёплый акцент. Достаётся только человеческому: имени, достижению.
  final Color amber;

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
    voidBg: Color(0xFF05070B),
    panel: Color(0xFF0C111A),
    line: Color(0xFF1A2432),
    steel: Color(0xFF4C6682),
    cyan: Color(0xFF4FD6E8),
    magenta: Color(0xFFE8486B),
    amber: Color(0xFFE8A33D),
    rust: Color(0xFFD4453A),
    bone: Color(0xFFDAD6C9),
    boneDim: Color(0xFF9FA7AE),
    muted: Color(0xFF6E8294),
    faint: Color(0xFF42566A),
    phos: Color(0xFF5FE3A1),
    scanline: Color(0x0DFFFFFF),
  );

  /// «Засвеченная плёнка»: бумажный вариант того же терминала. Циан уходит в
  /// глубокий индиго, иначе на светлом он слепнет и перестаёт быть акцентом.
  static const light = EdgeColors(
    voidBg: Color(0xFFE6E4DC),
    panel: Color(0xFFF1EFE8),
    line: Color(0xFFC7C9C4),
    steel: Color(0xFF5A6B7D),
    cyan: Color(0xFF186B86),
    magenta: Color(0xFFA32347),
    amber: Color(0xFF8A5C13),
    rust: Color(0xFF9E2B22),
    bone: Color(0xFF14181E),
    boneDim: Color(0xFF3C444C),
    muted: Color(0xFF5D6B77),
    faint: Color(0xFF8A96A0),
    phos: Color(0xFF1F6B47),
    scanline: Color(0x08000000),
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
      steel: m(steel, other.steel),
      cyan: m(cyan, other.cyan),
      magenta: m(magenta, other.magenta),
      amber: m(amber, other.amber),
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
