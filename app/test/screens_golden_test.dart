// Снимки экранов на готовых данных. Запуск с обновлением картинок:
//   flutter test --update-goldens test/screens_golden_test.dart
// Картинки ложатся в test/goldens/ — по ним видно дизайн без телефона.
@Tags(['golden'])
library;

import 'package:edgewind/app.dart';
import 'package:edgewind/data/edgewind_store.dart';
import 'package:edgewind/data/local_cache.dart';
import 'package:edgewind/screens/boot_screen.dart';
import 'package:edgewind/screens/compose_screen.dart';
import 'package:edgewind/screens/help_screen.dart';
import 'package:edgewind/screens/home_screen.dart';
import 'package:edgewind/screens/story_screen.dart';
import 'package:edgewind/screens/welcome_screen.dart';
import 'package:edgewind/ui/tokens.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'support/fake_backend.dart';
import 'support/fonts.dart';
import 'support/sample.dart';

EdgewindStore _seeded() {
  final store = EdgewindStore(backend: FakeBackend(), cache: LocalCache());
  store.debugSeed(
    profile: me,
    feed: stories,
    mine: stories.where((s) => s.isMine).toList(),
    saved: stories.where((s) => s.savedByMe).toList(),
    inbox: inbox,
    achievements: achievements,
    inboxSeen: now.subtract(const Duration(hours: 1)),
  );
  return store;
}

Widget _frame(Widget child, {bool dark = true}) => MaterialApp(
  debugShowCheckedModeBanner: false,
  theme: buildTheme(EdgeColors.light),
  darkTheme: buildTheme(EdgeColors.dark),
  themeMode: dark ? ThemeMode.dark : ThemeMode.light,
  builder: (context, w) => MediaQuery(
    data: MediaQuery.of(context).copyWith(disableAnimations: true, padding: const EdgeInsets.only(top: 32, bottom: 18)),
    child: w!,
  ),
  home: child,
);

Future<void> _settleImages(WidgetTester tester) async {
  await tester.runAsync(() async {
    final ctx = tester.element(find.byType(MaterialApp));
    for (final path in [
      'assets/images/girl_crt.webp',
      'assets/images/wires_dusk.webp',
      'assets/images/daisies.webp',
      'assets/images/grain.png',
    ]) {
      await precacheImage(AssetImage(path), ctx);
    }
    await Future<void>.delayed(const Duration(milliseconds: 50));
  });
  await tester.pump(const Duration(milliseconds: 400));
  await tester.pump(const Duration(milliseconds: 400));
}

Future<void> _shot(WidgetTester tester, Widget screen, String name, {bool dark = true, Future<void> Function()? then}) async {
  await tester.pumpWidget(_frame(screen, dark: dark));
  await _settleImages(tester);
  if (then != null) await then();
  await expectLater(find.byType(MaterialApp), matchesGoldenFile('goldens/$name.png'));
}

void main() {
  setUpAll(() async {
    SharedPreferences.setMockInitialValues({});
    await loadAppFonts();
  });

  setUp(() {
    final binding = TestWidgetsFlutterBinding.ensureInitialized();
    binding.platformDispatcher.views.first
      ..physicalSize = const Size(1080, 2340)
      ..devicePixelRatio = 2.625;
  });

  testWidgets('01 welcome', (t) async {
    await _shot(t, WelcomeScreen(store: _seeded()), '01_welcome');
  });

  testWidgets('02 name', (t) async {
    await _shot(t, WelcomeScreen(store: _seeded(), startAtName: true), '02_name');
  });

  testWidgets('03 feed', (t) async {
    await _shot(t, HomeScreen(store: _seeded(), onToggleTheme: () {}), '03_feed');
  });

  testWidgets('04 feed scrolled', (t) async {
    await _shot(t, HomeScreen(store: _seeded(), onToggleTheme: () {}), '04_feed_scrolled', then: () async {
      await t.drag(find.byType(CustomScrollView).first, const Offset(0, -420));
      await t.pump(const Duration(milliseconds: 300));
    });
  });

  testWidgets('05 story', (t) async {
    await _shot(t, StoryScreen(store: _seeded(), storyId: 'a1', initialResponses: responses), '05_story');
  });

  testWidgets('06 story scrolled', (t) async {
    await _shot(t, StoryScreen(store: _seeded(), storyId: 'a1', initialResponses: responses), '06_story_scrolled',
        then: () async {
      await t.drag(find.byType(CustomScrollView).first, const Offset(0, -520));
      await t.pump(const Duration(milliseconds: 300));
    });
  });

  testWidgets('07 compose crisis', (t) async {
    await _shot(t, ComposeScreen(store: _seeded()), '07_compose', then: () async {
      await t.enterText(find.byType(TextField).at(0), 'Не знаю, зачем всё это');
      await t.enterText(find.byType(TextField).at(1), 'Иногда кажется, что не хочу жить. Устала притворяться, что всё нормально.');
      await t.pump(const Duration(milliseconds: 300));
      FocusManager.instance.primaryFocus?.unfocus();
      await t.pump(const Duration(milliseconds: 300));
    });
  });

  testWidgets('08 profile', (t) async {
    await _shot(t, HomeScreen(store: _seeded(), onToggleTheme: () {}, initialTab: 3), '08_profile');
  });

  testWidgets('09 profile scrolled', (t) async {
    await _shot(t, HomeScreen(store: _seeded(), onToggleTheme: () {}, initialTab: 3), '09_profile_scrolled',
        then: () async {
      await t.drag(find.byType(CustomScrollView).last, const Offset(0, -700));
      await t.pump(const Duration(milliseconds: 300));
    });
  });

  testWidgets('10 inbox', (t) async {
    await _shot(t, HomeScreen(store: _seeded(), onToggleTheme: () {}, initialTab: 2), '10_inbox');
  });

  testWidgets('11 saved', (t) async {
    await _shot(t, HomeScreen(store: _seeded(), onToggleTheme: () {}, initialTab: 1), '11_saved');
  });

  testWidgets('12 help', (t) async {
    await _shot(t, const HelpScreen(), '12_help');
  });

  testWidgets('13 feed light', (t) async {
    await _shot(t, HomeScreen(store: _seeded(), onToggleTheme: () {}), '13_feed_light', dark: false);
  });

  testWidgets('14 boot', (t) async {
    await _shot(t, const BootScreen(), '14_boot');
  });
}
