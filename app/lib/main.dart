import 'package:flutter/material.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

import 'app.dart';
import 'data/edgewind_store.dart';
import 'data/local_cache.dart';
import 'data/supabase_config.dart';

/// Первый кадр рисуется сразу: подключение к серверу и чтение кэша идут уже
/// под экраном загрузки, а не вместо него. Раньше, пока шла инициализация,
/// человек смотрел на пустой системный экран.
Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final cache = LocalCache();
  final dark = await cache.darkTheme() ?? true;
  final store = EdgewindStore(cache: cache);
  runApp(EdgewindApp(store: store, cache: cache, dark: dark));
  try {
    await Supabase.initialize(url: supabaseUrl, publishableKey: supabaseAnonKey);
  } catch (_) {
    // Сеть здесь не нужна: initialize только восстанавливает сессию с телефона.
  }
  await store.boot();
}
