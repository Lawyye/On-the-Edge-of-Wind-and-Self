import 'package:flutter/material.dart';

import 'screens/home_screen.dart';

class EdgewindApp extends StatelessWidget {
  const EdgewindApp({super.key});

  static const _ink = Color(0xFF20322D);
  static const _moss = Color(0xFF587568);
  static const _mist = Color(0xFFF4F6F1);

  @override
  Widget build(BuildContext context) {
    final scheme =
        ColorScheme.fromSeed(
          seedColor: _moss,
          brightness: Brightness.light,
          surface: _mist,
        ).copyWith(
          primary: _moss,
          onPrimary: Colors.white,
          secondary: const Color(0xFFB8845D),
          onSecondary: Colors.white,
          surface: _mist,
          onSurface: _ink,
          outline: const Color(0xFFC8D1CA),
        );

    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'On the Edge',
      scrollBehavior: const _EdgewindScrollBehavior(),
      theme: ThemeData(
        useMaterial3: true,
        colorScheme: scheme,
        scaffoldBackgroundColor: _mist,
        fontFamily: 'sans-serif',
        textTheme: const TextTheme(
          headlineLarge: TextStyle(
            color: _ink,
            fontSize: 34,
            height: 1.08,
            fontWeight: FontWeight.w700,
            letterSpacing: -0.8,
          ),
          headlineSmall: TextStyle(
            color: _ink,
            fontSize: 22,
            fontWeight: FontWeight.w700,
            letterSpacing: -0.25,
          ),
          titleMedium: TextStyle(
            color: _ink,
            fontSize: 16,
            fontWeight: FontWeight.w700,
          ),
          bodyLarge: TextStyle(color: _ink, fontSize: 16, height: 1.48),
          bodyMedium: TextStyle(color: _ink, fontSize: 14, height: 1.45),
        ),
        cardTheme: const CardThemeData(
          color: Colors.white,
          elevation: 0,
          margin: EdgeInsets.zero,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.all(Radius.circular(24)),
            side: BorderSide(color: Color(0xFFE0E6E1)),
          ),
        ),
        inputDecorationTheme: const InputDecorationTheme(
          filled: true,
          fillColor: Colors.white,
          border: OutlineInputBorder(
            borderRadius: BorderRadius.all(Radius.circular(18)),
            borderSide: BorderSide(color: Color(0xFFD9E1DB)),
          ),
          enabledBorder: OutlineInputBorder(
            borderRadius: BorderRadius.all(Radius.circular(18)),
            borderSide: BorderSide(color: Color(0xFFD9E1DB)),
          ),
          focusedBorder: OutlineInputBorder(
            borderRadius: BorderRadius.all(Radius.circular(18)),
            borderSide: BorderSide(color: _moss, width: 1.5),
          ),
        ),
      ),
      home: const HomeScreen(),
    );
  }
}

class _EdgewindScrollBehavior extends MaterialScrollBehavior {
  const _EdgewindScrollBehavior();

  @override
  ScrollPhysics getScrollPhysics(BuildContext context) =>
      const BouncingScrollPhysics(parent: AlwaysScrollableScrollPhysics());
}
