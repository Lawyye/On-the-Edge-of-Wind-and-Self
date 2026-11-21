import 'package:flutter/painting.dart';

/// Темы историй. Тот же список зашит в базе ограничением ew_stories_category_check:
/// новая тема появляется в обоих местах сразу, иначе публикация упадёт.
const storyCategories = [
  'Просто поговорить',
  'Отношения',
  'Буллинг',
  'Одиночество',
  'Тревога',
  'Семья',
  'Учёба',
  'Работа',
  'Перемены',
  'Другое',
];

/// Картинка и кадр для шапки истории. У каждой темы свой кусок одной из трёх
/// картинок: так шапки разные, а приложение не тащит десяток файлов.
class CategoryArt {
  const CategoryArt(this.image, this.alignment, this.tint, this.tintLight);

  final String image;
  final Alignment alignment;

  /// Цвет знака-сигнала в ленте: на тёмной теме и на светлой.
  final Color tint;
  final Color tintLight;
}

const kGirl = 'assets/images/girl_crt.webp';
const kWires = 'assets/images/wires_dusk.webp';
const kDaisies = 'assets/images/daisies.webp';

const _art = <String, CategoryArt>{
  'Просто поговорить': CategoryArt(kDaisies, Alignment(0.1, 0.75), Color(0xFFC6B8F2), Color(0xFF6E5FD0)),
  'Отношения': CategoryArt(kDaisies, Alignment(-0.6, 0.35), Color(0xFFF3A3C0), Color(0xFFB4436E)),
  'Буллинг': CategoryArt(kGirl, Alignment(0.95, 0.25), Color(0xFFFF8A7A), Color(0xFFC0392B)),
  'Одиночество': CategoryArt(kWires, Alignment(-0.3, -0.55), Color(0xFFA4C4FF), Color(0xFF2D6BD8)),
  'Тревога': CategoryArt(kGirl, Alignment(0.0, -0.05), Color(0xFF9A8FF0), Color(0xFF5A4ED6)),
  'Семья': CategoryArt(kWires, Alignment(-0.8, 1.0), Color(0xFFFFC56E), Color(0xFFB8740F)),
  'Учёба': CategoryArt(kGirl, Alignment(-0.1, 0.45), Color(0xFF8FE3C8), Color(0xFF1E8A6A)),
  'Работа': CategoryArt(kWires, Alignment(0.9, 0.55), Color(0xFFD9C79B), Color(0xFF8A6D2C)),
  'Перемены': CategoryArt(kWires, Alignment(0.2, 0.85), Color(0xFFE9A2F0), Color(0xFF9B3BA8)),
  'Другое': CategoryArt(kWires, Alignment(0.3, 0.0), Color(0xFFB5B2CC), Color(0xFF5F5C7D)),
};

CategoryArt artFor(String category) => _art[category] ?? _art['Другое']!;
