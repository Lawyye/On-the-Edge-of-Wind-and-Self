import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../data/edgewind_store.dart';
import '../domain/categories.dart';
import '../domain/models.dart';
import '../widgets/story_card.dart';
import 'compose_sheet.dart';
import 'notifications_page.dart';
import 'profile_pages.dart';
import 'story_sheet.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  final EdgewindStore _store = EdgewindStore();
  int _page = 0;

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

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _store,
      builder: (context, _) {
        if (!_store.ready) {
          return const Scaffold(
            body: Center(child: CircularProgressIndicator(strokeWidth: 2)),
          );
        }

        final pages = [
          FeedPage(store: _store, onCompose: _openComposer),
          _SavedTab(store: _store),
          ResponsesPage(store: _store, withAppBar: false),
          ProfilePage(store: _store),
        ];

        return Scaffold(
          extendBody: true,
          body: AnimatedSwitcher(
            duration: const Duration(milliseconds: 300),
            switchInCurve: Curves.easeOutCubic,
            switchOutCurve: Curves.easeInCubic,
            transitionBuilder: (child, animation) => FadeTransition(
              opacity: animation,
              child: SlideTransition(
                position: Tween<Offset>(
                  begin: const Offset(0.025, 0),
                  end: Offset.zero,
                ).animate(animation),
                child: child,
              ),
            ),
            child: KeyedSubtree(key: ValueKey(_page), child: pages[_page]),
          ),
          floatingActionButton: _page == 0
              ? FloatingActionButton(
                  onPressed: _openComposer,
                  backgroundColor: storyInk,
                  foregroundColor: Colors.white,
                  elevation: 2,
                  tooltip: 'Написать',
                  child: const Icon(Icons.edit_outlined),
                )
              : null,
          floatingActionButtonLocation:
              FloatingActionButtonLocation.centerDocked,
          bottomNavigationBar: _BottomBar(
            selectedIndex: _page,
            onSelected: (value) => setState(() => _page = value),
          ),
        );
      },
    );
  }

  Future<void> _openComposer() async {
    final created = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      backgroundColor: Colors.transparent,
      builder: (_) => ComposeSheet(store: _store),
    );
    if (created == true && mounted) {
      setState(() => _page = 0);
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('Запись опубликована.')));
    }
  }
}

class FeedPage extends StatefulWidget {
  const FeedPage({super.key, required this.store, required this.onCompose});

  final EdgewindStore store;
  final VoidCallback onCompose;

  @override
  State<FeedPage> createState() => _FeedPageState();
}

class _FeedPageState extends State<FeedPage> {
  String _category = 'Все';

  List<Story> get _visibleStories {
    if (_category == 'Все') return widget.store.stories;
    return widget.store.stories
        .where((story) => story.category == _category)
        .toList();
  }

  @override
  Widget build(BuildContext context) {
    final hasStories = widget.store.stories.isNotEmpty;
    return CustomScrollView(
      key: const PageStorageKey('feed'),
      physics: const BouncingScrollPhysics(
        parent: AlwaysScrollableScrollPhysics(),
      ),
      slivers: [
        _buildHero(context),
        if (hasStories)
          SliverToBoxAdapter(child: _Reveal(child: _buildCategories())),
        if (!hasStories)
          SliverFillRemaining(
            hasScrollBody: false,
            child: _EmptyFeed(onCompose: widget.onCompose),
          )
        else if (_visibleStories.isEmpty)
          const SliverFillRemaining(
            hasScrollBody: false,
            child: EmptyPage(text: 'В этой теме пока пусто.'),
          )
        else
          SliverPadding(
            padding: const EdgeInsets.fromLTRB(18, 6, 18, 128),
            sliver: SliverList.separated(
              itemCount: _visibleStories.length,
              separatorBuilder: (_, _) => const SizedBox(height: 14),
              itemBuilder: (context, index) {
                final story = _visibleStories[index];
                return _Reveal(
                  duration: Duration(milliseconds: 400 + (index * 70)),
                  child: StoryCard(
                    story: story,
                    store: widget.store,
                    onTap: () => showStorySheet(
                      context,
                      store: widget.store,
                      story: story,
                    ),
                  ),
                );
              },
            ),
          ),
      ],
    );
  }

  SliverAppBar _buildHero(BuildContext context) {
    final height = MediaQuery.sizeOf(context).height;
    return SliverAppBar(
      expandedHeight: (height * 0.38).clamp(250.0, 330.0),
      pinned: true,
      stretch: true,
      elevation: 0,
      backgroundColor: const Color(0xFFF4F6F1),
      surfaceTintColor: Colors.transparent,
      foregroundColor: storyInk,
      systemOverlayStyle: SystemUiOverlayStyle.dark,
      titleSpacing: 20,
      title: const Text(
        'on the edge',
        style: TextStyle(fontSize: 17, fontWeight: FontWeight.w700),
      ),
      actions: [
        IconButton(
          tooltip: 'Уведомления',
          onPressed: () => Navigator.push(
            context,
            MaterialPageRoute(builder: (_) => const NotificationsPage()),
          ),
          icon: const Icon(Icons.notifications_none_rounded),
        ),
        const SizedBox(width: 6),
      ],
      flexibleSpace: FlexibleSpaceBar(
        collapseMode: CollapseMode.parallax,
        stretchModes: const [StretchMode.zoomBackground, StretchMode.fadeTitle],
        background: Stack(
          fit: StackFit.expand,
          children: [
            Image.asset(
              'assets/images/quiet-field.png',
              fit: BoxFit.cover,
              alignment: const Alignment(0.35, 0),
              filterQuality: FilterQuality.high,
            ),
            const DecoratedBox(
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  begin: Alignment.topCenter,
                  end: Alignment.bottomCenter,
                  colors: [
                    Color(0x55F4F6F1),
                    Color(0x110F211C),
                    Color(0xC91A2B25),
                  ],
                  stops: [0, 0.48, 1],
                ),
              ),
            ),
            const Positioned(
              left: 22,
              right: 22,
              bottom: 28,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Привет.',
                    style: TextStyle(
                      color: Colors.white,
                      fontSize: 36,
                      height: 1,
                      fontWeight: FontWeight.w700,
                      letterSpacing: -0.7,
                    ),
                  ),
                  SizedBox(height: 9),
                  Text(
                    'Как ты?',
                    style: TextStyle(
                      color: Color(0xFFE8EFE9),
                      fontSize: 18,
                      fontWeight: FontWeight.w500,
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildCategories() {
    const categories = ['Все', ...storyCategories];
    return Padding(
      padding: const EdgeInsets.only(top: 12, bottom: 10),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Padding(
            padding: EdgeInsets.symmetric(horizontal: 20),
            child: Text(
              'Что пишут',
              style: TextStyle(
                fontSize: 22,
                fontWeight: FontWeight.w700,
                color: storyInk,
              ),
            ),
          ),
          const SizedBox(height: 12),
          SizedBox(
            height: 42,
            child: ListView.separated(
              padding: const EdgeInsets.symmetric(horizontal: 18),
              scrollDirection: Axis.horizontal,
              itemCount: categories.length,
              separatorBuilder: (_, _) => const SizedBox(width: 8),
              itemBuilder: (context, index) {
                final category = categories[index];
                final selected = category == _category;
                return ChoiceChip(
                  label: Text(category),
                  selected: selected,
                  showCheckmark: false,
                  onSelected: (_) => setState(() => _category = category),
                  selectedColor: storyInk,
                  backgroundColor: Colors.white,
                  side: const BorderSide(color: Color(0xFFDCE3DD)),
                  labelStyle: TextStyle(
                    color: selected ? Colors.white : storyInk,
                    fontWeight: FontWeight.w600,
                  ),
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}

class _SavedTab extends StatelessWidget {
  const _SavedTab({required this.store});

  final EdgewindStore store;

  @override
  Widget build(BuildContext context) {
    final stories = store.savedStories;
    return SafeArea(
      bottom: false,
      child: stories.isEmpty
          ? const Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Padding(
                  padding: EdgeInsets.fromLTRB(24, 32, 24, 0),
                  child: Text(
                    'Сохранено',
                    style: TextStyle(
                      fontSize: 34,
                      fontWeight: FontWeight.w700,
                      color: storyInk,
                    ),
                  ),
                ),
                Expanded(child: EmptyPage(text: 'Здесь пока пусто.')),
              ],
            )
          : ListView.separated(
              physics: const BouncingScrollPhysics(),
              padding: const EdgeInsets.fromLTRB(18, 28, 18, 110),
              itemCount: stories.length + 1,
              separatorBuilder: (_, _) => const SizedBox(height: 12),
              itemBuilder: (context, index) {
                if (index == 0) {
                  return const Padding(
                    padding: EdgeInsets.fromLTRB(6, 0, 6, 12),
                    child: Text(
                      'Сохранено',
                      style: TextStyle(
                        fontSize: 34,
                        fontWeight: FontWeight.w700,
                        color: storyInk,
                      ),
                    ),
                  );
                }
                final story = stories[index - 1];
                return StoryCard(
                  story: story,
                  store: store,
                  onTap: () =>
                      showStorySheet(context, store: store, story: story),
                );
              },
            ),
    );
  }
}

class _EmptyFeed extends StatelessWidget {
  const _EmptyFeed({required this.onCompose});

  final VoidCallback onCompose;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(28, 36, 28, 130),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          const Icon(Icons.air_rounded, size: 48, color: Color(0xFFB9C8BD)),
          const SizedBox(height: 16),
          const Text(
            'Пока здесь пусто.',
            style: TextStyle(
              fontSize: 22,
              fontWeight: FontWeight.w700,
              color: storyInk,
            ),
          ),
          const SizedBox(height: 8),
          const Text(
            'Можно оставить первую запись.',
            textAlign: TextAlign.center,
            style: TextStyle(fontSize: 15, color: Color(0xFF637169)),
          ),
          const SizedBox(height: 20),
          OutlinedButton(onPressed: onCompose, child: const Text('Написать')),
        ],
      ),
    );
  }
}

class _BottomBar extends StatelessWidget {
  const _BottomBar({required this.selectedIndex, required this.onSelected});

  final int selectedIndex;
  final ValueChanged<int> onSelected;

  @override
  Widget build(BuildContext context) {
    return BottomAppBar(
      height: 78,
      padding: const EdgeInsets.symmetric(horizontal: 8),
      color: Colors.white,
      surfaceTintColor: Colors.white,
      shape: selectedIndex == 0 ? const CircularNotchedRectangle() : null,
      notchMargin: 9,
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceAround,
        children: [
          _NavItem(
            icon: Icons.home_outlined,
            selectedIcon: Icons.home_rounded,
            label: 'Главная',
            selected: selectedIndex == 0,
            onTap: () => onSelected(0),
          ),
          _NavItem(
            icon: Icons.bookmarks_outlined,
            selectedIcon: Icons.bookmarks_rounded,
            label: 'Сохранено',
            selected: selectedIndex == 1,
            onTap: () => onSelected(1),
          ),
          SizedBox(width: selectedIndex == 0 ? 78 : 0),
          _NavItem(
            icon: Icons.forum_outlined,
            selectedIcon: Icons.forum_rounded,
            label: 'Отклики',
            selected: selectedIndex == 2,
            onTap: () => onSelected(2),
          ),
          _NavItem(
            icon: Icons.person_outline_rounded,
            selectedIcon: Icons.person_rounded,
            label: 'Профиль',
            selected: selectedIndex == 3,
            onTap: () => onSelected(3),
          ),
        ],
      ),
    );
  }
}

class _NavItem extends StatelessWidget {
  const _NavItem({
    required this.icon,
    required this.selectedIcon,
    required this.label,
    required this.selected,
    required this.onTap,
  });

  final IconData icon;
  final IconData selectedIcon;
  final String label;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: InkWell(
        borderRadius: BorderRadius.circular(18),
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 6),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(
                selected ? selectedIcon : icon,
                color: selected ? storyMoss : const Color(0xFF829088),
                size: 23,
              ),
              const SizedBox(height: 3),
              Text(
                label,
                maxLines: 1,
                style: TextStyle(
                  fontSize: 10,
                  fontWeight: selected ? FontWeight.w700 : FontWeight.w500,
                  color: selected ? storyMoss : const Color(0xFF829088),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _Reveal extends StatelessWidget {
  const _Reveal({
    required this.child,
    this.duration = const Duration(milliseconds: 420),
  });

  final Widget child;
  final Duration duration;

  @override
  Widget build(BuildContext context) {
    return TweenAnimationBuilder<double>(
      tween: Tween(begin: 0, end: 1),
      duration: duration,
      curve: Curves.easeOutCubic,
      builder: (context, value, child) => Opacity(
        opacity: value,
        child: Transform.translate(
          offset: Offset(0, 18 * (1 - value)),
          child: child,
        ),
      ),
      child: child,
    );
  }
}
