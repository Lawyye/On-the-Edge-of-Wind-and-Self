import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

import 'data/edgewind_store.dart';
import 'screens/home_screen.dart';
import 'screens/signup_screen.dart';
import 'ui/term.dart';
import 'ui/tokens.dart';

class EdgewindApp extends StatefulWidget {
  const EdgewindApp({super.key});

  @override
  State<EdgewindApp> createState() => _EdgewindAppState();
}

class _EdgewindAppState extends State<EdgewindApp> {
  final EdgewindStore _store = EdgewindStore();

  /// Тёмная по умолчанию: весь язык интерфейса построен на свечении на чёрном.
  ThemeMode _mode = ThemeMode.dark;

  @override
  void initState() {
    super.initState();
    _store.load();
  }

  @override
  void dispose() {
    _store.dispose();
    super.dispose();
  }

  void _toggleTheme() => setState(
    () => _mode = _mode == ThemeMode.dark ? ThemeMode.light : ThemeMode.dark,
  );

  @override
  Widget build(BuildContext context) => MaterialApp(
    debugShowCheckedModeBanner: false,
    title: 'On the Edge',
    theme: _theme(EdgeColors.light, Brightness.light),
    darkTheme: _theme(EdgeColors.dark, Brightness.dark),
    themeMode: _mode,
    scrollBehavior: const _Bouncy(),
    home: _Gate(store: _store, onToggleTheme: _toggleTheme),
  );

  ThemeData _theme(EdgeColors c, Brightness brightness) {
    final mono = GoogleFonts.jetBrainsMonoTextTheme();
    final human = GoogleFonts.literataTextTheme();

    return ThemeData(
      useMaterial3: true,
      brightness: brightness,
      scaffoldBackgroundColor: c.voidBg,
      extensions: [c],
      colorScheme: ColorScheme.fromSeed(
        seedColor: c.cyan,
        brightness: brightness,
      ).copyWith(
        primary: c.cyan,
        surface: c.voidBg,
        onSurface: c.bone,
        outline: c.line,
        error: c.rust,
      ),
      // Заголовки и тела историй — книжной гарнитурой: это написал человек.
      // Всё служебное живёт в моноширинном и набирается через TermLabel.
      textTheme: mono.copyWith(
        headlineSmall: human.headlineSmall!.copyWith(
          color: c.bone, fontSize: 25, height: 1.28, fontWeight: FontWeight.w600,
        ),
        titleMedium: human.titleMedium!.copyWith(
          color: c.bone, fontSize: 18, height: 1.34, fontWeight: FontWeight.w600,
        ),
        bodyLarge: human.bodyLarge!.copyWith(
          color: c.boneDim, fontSize: 15.5, height: 1.66,
        ),
        bodyMedium: human.bodyMedium!.copyWith(
          color: c.boneDim, fontSize: 14.5, height: 1.58,
        ),
      ),
      textSelectionTheme: TextSelectionThemeData(
        cursorColor: c.cyan,
        selectionColor: c.cyan.withValues(alpha: 0.28),
        selectionHandleColor: c.cyan,
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: c.panel,
        contentPadding: const EdgeInsets.symmetric(horizontal: 15, vertical: 14),
        border: OutlineInputBorder(borderRadius: BorderRadius.zero, borderSide: BorderSide(color: c.line)),
        enabledBorder: OutlineInputBorder(borderRadius: BorderRadius.zero, borderSide: BorderSide(color: c.line)),
        focusedBorder: OutlineInputBorder(borderRadius: BorderRadius.zero, borderSide: BorderSide(color: c.cyan)),
        hintStyle: GoogleFonts.literata(color: c.faint, fontSize: 15),
      ),
      snackBarTheme: SnackBarThemeData(
        backgroundColor: c.panel,
        contentTextStyle: GoogleFonts.jetBrainsMono(
          color: c.bone, fontSize: 12, letterSpacing: 0.8,
        ),
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.zero,
          side: BorderSide(color: c.steel),
        ),
      ),
      progressIndicatorTheme: ProgressIndicatorThemeData(color: c.cyan),
    );
  }
}

/// Пока не знаем, есть ли имя — ждём; без имени — знакомство.
class _Gate extends StatelessWidget {
  const _Gate({required this.store, required this.onToggleTheme});

  final EdgewindStore store;
  final VoidCallback onToggleTheme;

  @override
  Widget build(BuildContext context) => AnimatedBuilder(
    animation: store,
    builder: (context, _) {
      if (!store.ready) return const _Booting();
      if (store.error != null && !store.hasProfile) {
        return _Trouble(message: store.error!, onRetry: store.load);
      }
      if (!store.hasProfile) {
        return SignupScreen(store: store, onToggleTheme: onToggleTheme);
      }
      return HomeScreen(store: store, onToggleTheme: onToggleTheme);
    },
  );
}

class _Booting extends StatelessWidget {
  const _Booting();

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    return TermScreen(
      child: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            SizedBox(
              width: 22,
              height: 22,
              child: CircularProgressIndicator(strokeWidth: 1.4, color: c.cyan),
            ),
            const SizedBox(height: 18),
            const TermLabel('УСТАНОВКА СВЯЗИ', tracking: 2.6),
          ],
        ),
      ),
    );
  }
}

class _Trouble extends StatelessWidget {
  const _Trouble({required this.message, required this.onRetry});

  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    return TermScreen(
      child: Center(
        child: Padding(
          padding: const EdgeInsets.all(28),
          child: Pane(
            padding: const EdgeInsets.all(22),
            borderColor: c.rust,
            cornerColor: c.rust,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Container(width: 3, height: 16, color: c.rust),
                    const SizedBox(width: 10),
                    TermLabel('СВЯЗИ НЕТ', color: c.rust, tracking: 2.4),
                  ],
                ),
                const SizedBox(height: 16),
                Text(message, style: Theme.of(context).textTheme.bodyMedium),
                const SizedBox(height: 20),
                GestureDetector(
                  onTap: onRetry,
                  child: Container(
                    height: 46,
                    alignment: Alignment.center,
                    decoration: BoxDecoration(
                      border: Border.all(color: c.cyan),
                      color: c.cyan.withValues(alpha: 0.1),
                    ),
                    child: TermLabel(
                      'ПОВТОРИТЬ',
                      color: c.cyan,
                      size: 11,
                      tracking: 3,
                      weight: FontWeight.w700,
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _Bouncy extends MaterialScrollBehavior {
  const _Bouncy();
  @override
  ScrollPhysics getScrollPhysics(BuildContext context) =>
      const BouncingScrollPhysics(parent: AlwaysScrollableScrollPhysics());
}
