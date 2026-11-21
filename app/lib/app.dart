import 'package:flutter/material.dart';

import 'data/edgewind_store.dart';
import 'data/local_cache.dart';
import 'domain/categories.dart';
import 'screens/boot_screen.dart';
import 'screens/home_screen.dart';
import 'screens/welcome_screen.dart';
import 'ui/tokens.dart';
import 'ui/type.dart';

class EdgewindApp extends StatefulWidget {
  const EdgewindApp({super.key, required this.store, this.cache, this.dark = true});

  final EdgewindStore store;
  final LocalCache? cache;

  /// Тема на старте; выбор человека потом хранится на телефоне.
  final bool dark;

  @override
  State<EdgewindApp> createState() => _EdgewindAppState();
}

class _EdgewindAppState extends State<EdgewindApp> with WidgetsBindingObserver {
  late bool _dark = widget.dark;
  late final LocalCache _cache = widget.cache ?? LocalCache();

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    // Картинки шапок декодируются заранее: переключение вкладок без вспышки.
    for (final image in [kGirl, kWires, kDaisies]) {
      precacheImage(AssetImage(image), context);
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) widget.store.resumed();
  }

  void _toggleTheme() {
    setState(() => _dark = !_dark);
    _cache.setDarkTheme(_dark);
  }

  @override
  Widget build(BuildContext context) => MaterialApp(
    debugShowCheckedModeBanner: false,
    title: 'On the Edge',
    theme: buildTheme(EdgeColors.light),
    darkTheme: buildTheme(EdgeColors.dark),
    themeMode: _dark ? ThemeMode.dark : ThemeMode.light,
    builder: (context, child) {
      // Крупный системный шрифт уважаем, но не даём ему разорвать вёрстку.
      final mq = MediaQuery.of(context);
      return MediaQuery(
        data: mq.copyWith(textScaler: mq.textScaler.clamp(minScaleFactor: 0.9, maxScaleFactor: 1.3)),
        child: child!,
      );
    },
    home: ListenableBuilder(
      listenable: widget.store,
      builder: (context, _) => AnimatedSwitcher(
        duration: const Duration(milliseconds: 350),
        child: switch (widget.store.phase) {
          Phase.booting => const BootScreen(key: ValueKey('boot')),
          Phase.offline => TroubleScreen(
            key: const ValueKey('offline'),
            message: widget.store.bootProblem ?? 'Сервер не ответил.',
            onRetry: widget.store.boot,
          ),
          Phase.signup => WelcomeScreen(key: const ValueKey('signup'), store: widget.store),
          Phase.ready => HomeScreen(key: const ValueKey('home'), store: widget.store, onToggleTheme: _toggleTheme),
        },
      ),
    ),
  );
}

ThemeData buildTheme(EdgeColors c) {
  final brightness = c.isDark ? Brightness.dark : Brightness.light;
  final base = ThemeData(useMaterial3: true, brightness: brightness, fontFamily: T.mono);
  return base.copyWith(
    scaffoldBackgroundColor: c.bg,
    canvasColor: c.bg,
    extensions: [c],
    colorScheme: ColorScheme.fromSeed(seedColor: c.violet, brightness: brightness).copyWith(
      primary: c.violet,
      onPrimary: c.onButton,
      secondary: c.lamp,
      surface: c.bg,
      onSurface: c.text,
      outline: c.line,
      error: c.alarm,
    ),
    textTheme: base.textTheme.apply(fontFamily: T.mono, bodyColor: c.text, displayColor: c.text),
    textSelectionTheme: TextSelectionThemeData(
      cursorColor: c.violet,
      selectionColor: c.violet.withValues(alpha: 0.3),
      selectionHandleColor: c.violet,
    ),
    splashFactory: InkRipple.splashFactory,
    highlightColor: c.violet.withValues(alpha: 0.06),
    splashColor: c.violet.withValues(alpha: 0.12),
    dividerColor: c.line,
    iconTheme: IconThemeData(color: c.textDim),
    tooltipTheme: TooltipThemeData(
      decoration: BoxDecoration(color: c.panel, border: Border.all(color: c.line)),
      textStyle: T.label(c.text, size: 10.5, tracking: 0.8),
    ),
    snackBarTheme: SnackBarThemeData(
      backgroundColor: c.panel,
      contentTextStyle: T.body(c.text, size: 14, height: 1.35),
      behavior: SnackBarBehavior.floating,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(2), side: BorderSide(color: c.line)),
    ),
    progressIndicatorTheme: ProgressIndicatorThemeData(color: c.violet),
    bottomSheetTheme: BottomSheetThemeData(backgroundColor: c.panel, modalBackgroundColor: c.panel),
    pageTransitionsTheme: const PageTransitionsTheme(
      builders: {TargetPlatform.android: FadeForwardsPageTransitionsBuilder()},
    ),
  );
}
