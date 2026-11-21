// Проверка того, что кнопки делают то, что написано, и что вёрстка не
// разваливается на маленьком экране с крупным шрифтом.
import 'dart:typed_data';

import 'package:edgewind/app.dart';
import 'package:edgewind/data/edgewind_store.dart';
import 'package:edgewind/data/local_cache.dart';
import 'package:edgewind/domain/crisis.dart';
import 'package:edgewind/domain/names.dart';
import 'package:edgewind/screens/compose_screen.dart';
import 'package:edgewind/screens/help_screen.dart';
import 'package:edgewind/screens/home_screen.dart';
import 'package:edgewind/screens/story_screen.dart';
import 'package:edgewind/screens/welcome_screen.dart';
import 'package:edgewind/ui/format.dart';
import 'package:edgewind/ui/tokens.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'support/fake_backend.dart';
import 'support/fonts.dart';
import 'support/sample.dart';

Widget _frame(Widget child, {double textScale = 1.0}) => MaterialApp(
  debugShowCheckedModeBanner: false,
  theme: buildTheme(EdgeColors.light),
  darkTheme: buildTheme(EdgeColors.dark),
  themeMode: ThemeMode.dark,
  builder: (context, w) => MediaQuery(
    data: MediaQuery.of(context).copyWith(
      disableAnimations: true,
      textScaler: TextScaler.linear(textScale),
    ),
    child: w!,
  ),
  home: child,
);

(EdgewindStore, FakeBackend) _seeded() {
  final api = FakeBackend()..profile = me.toRow();
  final store = EdgewindStore(backend: api, cache: LocalCache());
  store.debugSeed(
    profile: me,
    feed: stories,
    mine: stories.where((s) => s.isMine).toList(),
    saved: stories.where((s) => s.savedByMe).toList(),
    inbox: inbox,
    achievements: achievements,
  );
  return (store, api);
}

void _size(WidgetTester t, Size logical, {double dpr = 2.0}) {
  t.view
    ..physicalSize = logical * dpr
    ..devicePixelRatio = dpr;
  addTearDown(t.view.reset);
}

void main() {
  setUpAll(() async {
    SharedPreferences.setMockInitialValues({});
    await loadAppFonts();
  });

  group('логика без экрана', () {
    test('распознаёт тревожные слова и не реагирует на обычные', () {
      expect(soundsLikeCrisis('Иногда не хочу жить'), isTrue);
      expect(soundsLikeCrisis('думаю покончить с собой'), isTrue);
      expect(soundsLikeCrisis('Өмір сүргім келмейді'), isTrue);
      expect(soundsLikeCrisis('Хочу жить у моря'), isFalse);
      expect(soundsLikeCrisis('Убил весь день на домашку'), isFalse);
    });

    test('русское множественное число', () {
      expect(counted(1, 'отклик', 'отклика', 'откликов'), '1 отклик');
      expect(counted(3, 'отклик', 'отклика', 'откликов'), '3 отклика');
      expect(counted(11, 'отклик', 'отклика', 'откликов'), '11 откликов');
      expect(counted(22, 'отклик', 'отклика', 'откликов'), '22 отклика');
      expect(counted(25, 'отклик', 'отклика', 'откликов'), '25 откликов');
    });

    test('проверка имени совпадает с правилами базы', () {
      expect(nicknameProblem('Я'), isNotNull);
      expect(nicknameProblem('Тихий  Кот'), isNotNull);
      expect(nicknameProblem('По${String.fromCharCode(0x200B)}лынь'), isNotNull);
      expect(nicknameProblem('Тихий Кот'), isNull);
      for (var i = 0; i < 200; i++) {
        expect(nicknameProblem(randomNickname()), isNull);
      }
    });

    test('формат картинки по первым байтам', () {
      expect(imageKind(Uint8List.fromList([0xFF, 0xD8, 0xFF, 0xE0])), ('jpg', 'image/jpeg'));
      expect(imageKind(Uint8List.fromList([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A])), ('png', 'image/png'));
      expect(imageKind(Uint8List.fromList('<html>'.codeUnits)), isNull);
    });
  });

  group('экраны', () {
    testWidgets('знакомство: имя занято — предлагается другое, потом вход', (t) async {
      _size(t, const Size(412, 915));
      final api = FakeBackend(session: false)..takenName = 'Полынь';
      final store = EdgewindStore(backend: api, cache: LocalCache());
      store.debugPhase(Phase.signup);
      await t.pumpWidget(_frame(WelcomeScreen(store: store)));
      await t.tap(find.text('ВОЙТИ'));
      await t.pump(const Duration(milliseconds: 500));
      await t.enterText(find.byType(TextField), 'Полынь');
      await t.pump();
      await t.ensureVisible(find.text('ГОТОВО'));
      await t.tap(find.text('ГОТОВО'));
      await t.pump(const Duration(milliseconds: 300));
      expect(find.textContaining('уже занято'), findsOneWidget);
      expect(store.phase, Phase.signup);
      await t.ensureVisible(find.text('ГОТОВО'));
      await t.tap(find.text('ГОТОВО'));
      await t.pump(const Duration(milliseconds: 300));
      expect(store.phase, Phase.ready);
      expect(store.profile?.nickname, isNot('Полынь'));
    });

    testWidgets('лента: «я рядом» ставится сразу и уходит на сервер', (t) async {
      final (store, api) = _seeded();
      await t.pumpWidget(_frame(HomeScreen(store: store, onToggleTheme: () {})));
      await t.pump();
      final before = store.storyById('a1')!.likes;
      await t.tap(find.text('$before').first);
      await t.pump();
      expect(store.storyById('a1')!.likedByMe, isTrue);
      expect(store.storyById('a1')!.likes, before + 1);
      expect(api.calls, contains('like a1 true'));
    });

    testWidgets('на свою историю «я рядом» не ставится', (t) async {
      final (store, api) = _seeded();
      await store.toggleLike('d4');
      expect(api.calls.where((c) => c.startsWith('like')), isEmpty);
    });

    testWidgets('история: отклик отправляется и появляется в списке', (t) async {
      _size(t, const Size(412, 915));
      final (store, _) = _seeded();
      await t.pumpWidget(_frame(StoryScreen(store: store, storyId: 'a1', initialResponses: responses)));
      await t.pump();
      await t.enterText(find.byType(TextField), 'Держись, ты не один.');
      await t.pump();
      await t.tap(find.byIcon(Icons.arrow_upward_rounded));
      await t.pump(const Duration(milliseconds: 500));
      await t.pump(const Duration(milliseconds: 500));
      expect(find.text('Держись, ты не один.', skipOffstage: false), findsOneWidget);
      expect(store.storyById('a1')!.responseCount, stories.first.responseCount + 1);
    });

    testWidgets('история: архив переключается и попадает во вкладку', (t) async {
      final (store, api) = _seeded();
      await t.pumpWidget(_frame(StoryScreen(store: store, storyId: 'a1', initialResponses: const [])));
      await t.pump();
      await t.tap(find.byTooltip('Сохранить в архив'));
      await t.pump();
      expect(store.savedStories.first.id, 'a1');
      expect(api.calls, contains('save a1 true'));
    });

    testWidgets('запись: публикуется и закрывает экран', (t) async {
      _size(t, const Size(412, 915));
      final (store, api) = _seeded();
      await t.pumpWidget(_frame(Builder(
        builder: (context) => Center(
          child: TextButton(
            onPressed: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => ComposeScreen(store: store))),
            child: const Text('open'),
          ),
        ),
      )));
      await t.tap(find.text('open'));
      await t.pump();
      await t.pump(const Duration(milliseconds: 600));
      await t.enterText(find.byType(TextField).at(0), 'Сегодня было трудно');
      await t.ensureVisible(find.byType(TextField).at(1));
      await t.enterText(find.byType(TextField).at(1), 'Но я справилась с половиной дел.');
      await t.pump();
      await t.ensureVisible(find.text('ОПУБЛИКОВАТЬ'));
      await t.tap(find.text('ОПУБЛИКОВАТЬ'));
      await t.pump(const Duration(milliseconds: 800));
      expect(api.calls, contains('createStory'));
      expect(find.text('open'), findsOneWidget);
    });

    testWidgets('профиль: удаление аккаунта спрашивает и возвращает к знакомству', (t) async {
      final (store, api) = _seeded();
      _size(t, const Size(412, 915));
      await t.pumpWidget(_frame(HomeScreen(store: store, onToggleTheme: () {}, initialTab: 3)));
      await t.pump();
      await t.scrollUntilVisible(find.text('Удалить аккаунт'), 300, scrollable: find.byType(Scrollable).last);
      await t.tap(find.text('Удалить аккаунт'));
      await t.pump(const Duration(milliseconds: 400));
      expect(find.text('УДАЛИТЬ АККАУНТ?'), findsOneWidget);
      await t.tap(find.text('УДАЛИТЬ'));
      await t.pump(const Duration(milliseconds: 400));
      expect(api.calls, contains('deleteAccount'));
      expect(store.phase, Phase.signup);
      expect(store.profile, isNull);
    });
  });

  group('маленький экран и крупный шрифт', () {
    for (final (name, build) in <(String, Widget Function(EdgewindStore))>[
      ('знакомство', (s) => WelcomeScreen(store: s)),
      ('имя', (s) => WelcomeScreen(store: s, startAtName: true)),
      ('лента', (s) => HomeScreen(store: s, onToggleTheme: () {})),
      ('архив', (s) => HomeScreen(store: s, onToggleTheme: () {}, initialTab: 1)),
      ('отклик', (s) => HomeScreen(store: s, onToggleTheme: () {}, initialTab: 2)),
      ('профиль', (s) => HomeScreen(store: s, onToggleTheme: () {}, initialTab: 3)),
      ('история', (s) => StoryScreen(store: s, storyId: 'b2', initialResponses: responses)),
      ('запись', (s) => ComposeScreen(store: s)),
      ('помощь', (s) => const HelpScreen()),
    ]) {
      testWidgets('$name: 320×568, шрифт ×1.3 — без переполнений', (t) async {
        _size(t, const Size(320, 568));
        final (store, _) = _seeded();
        await t.pumpWidget(_frame(build(store), textScale: 1.3));
        await t.pump(const Duration(milliseconds: 300));
        // Прокрутить до конца: переполнение могло прятаться ниже первого экрана.
        final scrollables = find.byType(Scrollable);
        if (scrollables.evaluate().isNotEmpty) {
          await t.drag(scrollables.first, const Offset(0, -3000));
          await t.pump(const Duration(milliseconds: 300));
        }
        expect(t.takeException(), isNull);
      });
    }
  });
}
