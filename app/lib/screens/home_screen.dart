import 'package:flutter/material.dart';

import '../data/edgewind_store.dart';
import '../domain/models.dart';
import '../ui/tokens.dart';
import '../ui/type.dart';
import 'compose_screen.dart';
import 'feed_tab.dart';
import 'help_screen.dart';
import 'inbox_tab.dart';
import 'profile_tab.dart';
import 'saved_tab.dart';
import 'story_screen.dart';

/// Четыре вкладки и кнопка записи между ними. Сообщения хранилища
/// (ошибки сети, «запись удалена») всплывают здесь, внизу экрана.
class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key, required this.store, required this.onToggleTheme, this.initialTab = 0});

  final EdgewindStore store;
  final VoidCallback onToggleTheme;
  final int initialTab;

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  late int _tab = widget.initialTab;

  EdgewindStore get _store => widget.store;

  @override
  void initState() {
    super.initState();
    _store.notice.addListener(_showNotice);
  }

  @override
  void dispose() {
    _store.notice.removeListener(_showNotice);
    super.dispose();
  }

  void _showNotice() {
    final n = _store.notice.value;
    if (n == null || !mounted) return;
    final c = context.c;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(
        content: Row(
          children: [
            Container(width: 2, height: 18, color: n.error ? c.alarm : c.violet),
            const SizedBox(width: 12),
            Expanded(child: Text(n.text, style: T.body(c.text, size: 14, height: 1.35))),
          ],
        ),
        backgroundColor: c.panel,
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(2), side: BorderSide(color: c.line)),
        duration: Duration(milliseconds: n.error ? 4200 : 2600),
      ));
  }

  void _select(int i) {
    if (i == _tab) return;
    setState(() => _tab = i);
    if (i == 2) _store.markInboxSeen();
  }

  Future<void> _compose() async {
    final created = await Navigator.of(context).push<bool>(
      MaterialPageRoute(fullscreenDialog: true, builder: (_) => ComposeScreen(store: _store)),
    );
    if (created == true && mounted) {
      setState(() => _tab = 0);
      _store.say('Запись опубликована. Отклики появятся во вкладке «Отклик».');
    }
  }

  Future<void> _openStory(Story story) => openStory(context, _store, story.id);

  void _openHelp() => Navigator.of(context).push(MaterialPageRoute(builder: (_) => const HelpScreen()));

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    return PopScope(
      canPop: _tab == 0,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) setState(() => _tab = 0);
      },
      child: Scaffold(
        backgroundColor: c.bg,
        body: ListenableBuilder(
          listenable: _store,
          builder: (context, _) => IndexedStack(
            index: _tab,
            children: [
              FeedTab(store: _store, onOpen: _openStory, onCompose: _compose, onHelp: _openHelp),
              SavedTab(store: _store, onOpen: _openStory),
              InboxTab(store: _store, onOpenStory: (id) => openStory(context, _store, id)),
              ProfileTab(store: _store, onOpen: _openStory, onToggleTheme: widget.onToggleTheme, onHelp: _openHelp),
            ],
          ),
        ),
        bottomNavigationBar: ListenableBuilder(
          listenable: _store,
          builder: (context, _) => _NavBar(
            tab: _tab,
            unread: _store.unreadCount,
            onTab: _select,
            onCompose: _compose,
          ),
        ),
      ),
    );
  }
}

/// Открывает историю: из кэша сразу, иначе — догружает по id.
Future<void> openStory(BuildContext context, EdgewindStore store, String id) async {
  var story = store.storyById(id);
  if (story == null) {
    try {
      story = await store.fetchStory(id);
    } catch (e) {
      store.say(store.describe(e), error: true);
      return;
    }
    if (story == null) {
      store.say('Этой записи уже нет: её удалили или скрыли.');
      return;
    }
  }
  if (!context.mounted) return;
  await Navigator.of(context).push(MaterialPageRoute(builder: (_) => StoryScreen(store: store, storyId: id)));
}

class _NavBar extends StatelessWidget {
  const _NavBar({required this.tab, required this.unread, required this.onTab, required this.onCompose});

  final int tab;
  final int unread;
  final ValueChanged<int> onTab;
  final VoidCallback onCompose;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    return DecoratedBox(
      decoration: BoxDecoration(color: c.bg, border: Border(top: BorderSide(color: c.line))),
      child: SafeArea(
        top: false,
        child: SizedBox(
          height: 62,
          child: Row(
            children: [
              _NavItem(icon: Icons.blur_on_outlined, label: 'ЛЕНТА', on: tab == 0, onTap: () => onTab(0)),
              _NavItem(icon: Icons.bookmark_border_rounded, label: 'АРХИВ', on: tab == 1, onTap: () => onTab(1)),
              SizedBox(
                width: 72,
                child: Center(
                  child: Semantics(
                    button: true,
                    label: 'Написать',
                    child: Material(
                      color: c.button,
                      borderRadius: BorderRadius.circular(2),
                      child: InkWell(
                        onTap: onCompose,
                        borderRadius: BorderRadius.circular(2),
                        child: SizedBox(width: 48, height: 40, child: Icon(Icons.add, size: 22, color: c.onButton)),
                      ),
                    ),
                  ),
                ),
              ),
              _NavItem(icon: Icons.forum_outlined, label: 'ОТКЛИК', on: tab == 2, onTap: () => onTab(2), badge: unread),
              _NavItem(icon: Icons.person_outline_rounded, label: 'Я', on: tab == 3, onTap: () => onTab(3)),
            ],
          ),
        ),
      ),
    );
  }
}

class _NavItem extends StatelessWidget {
  const _NavItem({required this.icon, required this.label, required this.on, required this.onTap, this.badge = 0});

  final IconData icon;
  final String label;
  final bool on;
  final VoidCallback onTap;
  final int badge;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    final ink = on ? c.text : c.textFaint;
    return Expanded(
      child: Semantics(
        button: true,
        selected: on,
        label: badge > 0 ? '$label, новых: $badge' : label,
        child: ExcludeSemantics(
          child: InkResponse(
            onTap: onTap,
            radius: 36,
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Stack(
                  clipBehavior: Clip.none,
                  children: [
                    Icon(icon, size: 21, color: ink),
                    if (badge > 0)
                      Positioned(
                        right: -9,
                        top: -5,
                        child: Container(
                          padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 1),
                          constraints: const BoxConstraints(minWidth: 15),
                          decoration: BoxDecoration(
                            color: c.lamp,
                            boxShadow: [BoxShadow(color: c.lamp.withValues(alpha: 0.6), blurRadius: 8)],
                          ),
                          child: Text(
                            badge > 9 ? '9+' : '$badge',
                            textAlign: TextAlign.center,
                            style: T.label(const Color(0xFF1A1206), size: 9, tracking: 0, weight: FontWeight.w700),
                          ),
                        ),
                      ),
                  ],
                ),
                const SizedBox(height: 5),
                Text(label, style: T.label(ink, size: 9, tracking: 1.4)),
                const SizedBox(height: 4),
                AnimatedContainer(
                  duration: const Duration(milliseconds: 200),
                  width: on ? 16 : 0,
                  height: 2,
                  decoration: BoxDecoration(
                    color: c.violet,
                    boxShadow: on ? [BoxShadow(color: c.violet.withValues(alpha: 0.8), blurRadius: 8)] : null,
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
