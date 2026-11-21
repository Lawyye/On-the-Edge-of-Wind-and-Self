import 'dart:io';

import 'package:flutter/services.dart';

/// Настоящие шрифты приложения в тестах. Без них Flutter рисует всё
/// квадратиками одной ширины — и вёрстка проверялась бы не та.
Future<void> loadAppFonts() async {
  Future<ByteData> asset(String path) => rootBundle.load(path);
  Future<ByteData> file(String path) async => ByteData.sublistView(await File(path).readAsBytes());

  final mono = FontLoader('JetBrainsMono')
    ..addFont(asset('assets/fonts/JetBrainsMono-Light.ttf'))
    ..addFont(asset('assets/fonts/JetBrainsMono-Regular.ttf'))
    ..addFont(asset('assets/fonts/JetBrainsMono-Bold.ttf'));
  final serif = FontLoader('Literata')
    ..addFont(asset('assets/fonts/Literata-Regular.ttf'))
    ..addFont(asset('assets/fonts/Literata-Italic.ttf'))
    ..addFont(asset('assets/fonts/Literata-SemiBold.ttf'));
  final jp = FontLoader('NotoSansJP')..addFont(asset('assets/fonts/NotoSansJP-Light.ttf'));
  await Future.wait([mono.load(), serif.load(), jp.load()]);

  // Значки Material лежат в самом Flutter SDK.
  final sdk = Platform.environment['FLUTTER_ROOT'] ?? '/opt/flutter';
  final icons = File('$sdk/bin/cache/artifacts/material_fonts/MaterialIcons-Regular.otf');
  if (icons.existsSync()) {
    await (FontLoader('MaterialIcons')..addFont(file(icons.path))).load();
  }
}

