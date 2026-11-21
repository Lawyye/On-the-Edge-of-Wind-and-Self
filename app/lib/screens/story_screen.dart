import 'package:flutter/material.dart';

import '../data/edgewind_store.dart';
import '../domain/categories.dart';
import '../domain/crisis.dart';
import '../domain/models.dart';
import '../ui/format.dart';
import '../ui/hero_header.dart';
import '../ui/tokens.dart';
import '../ui/type.dart';
import '../ui/widgets.dart';
import '../widgets/avatar.dart';
import 'help_screen.dart';
import 'profile_tab.dart';

const _reportReasons = [
  'Оскорбления или травля',
  'Спам или реклама',
  'Опасный контент',
  'Чужие личные данные',
  'Другое',
];

/// История целиком и разговор под ней. Кадр в шапке — от темы истории,
/// заголовок — книжной гарнитурой: его написал человек.
class StoryScreen extends StatefulWidget {
  const StoryScreen({super.key, required this.store, required this.storyId, this.initialResponses});

  final EdgewindStore store;
  final String storyId;

  /// Для проверки экрана в тестах — готовые отклики без сети.
  final List<StoryResponse>? initialResponses;

  @override
  State<StoryScreen> createState() => _StoryScreenState();
}

class _StoryScreenState extends State<StoryScreen> {
  final _reply = TextEditingController();
  final _scroll = ScrollController();
  List<StoryResponse>? _responses;
  String? _loadProblem;
  bool _sending = false;

  EdgewindStore get _store => widget.store;

  @override
  void initState() {
    super.initState();
    _responses = widget.initialResponses;
    if (_responses == null) _load();
  }

  @override
  void dispose() {
    _reply.dispose();
    _scroll.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    setState(() => _loadProblem = null);
    try {
      final list = await _store.responsesFor(widget.storyId);
      if (mounted) setState(() => _responses = list);
    } catch (e) {
      if (mounted) setState(() => _loadProblem = _store.describe(e));
    }
  }

  Future<void> _send() async {
    final text = _reply.text.trim();
    if (text.isEmpty || _sending) return;
    setState(() => _sending = true);
    try {
      final r = await _store.addResponse(widget.storyId, text);
      if (!mounted) return;
      _reply.clear();
      FocusScope.of(context).unfocus();
      setState(() => _responses = [...?_responses, r]);
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (_scroll.hasClients) {
          _scroll.animateTo(_scroll.position.maxScrollExtent, duration: const Duration(milliseconds: 320), curve: Curves.easeOut);
        }
      });
    } catch (e) {
      _store.say(_store.describe(e), error: true);
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }

  Future<String?> _pickReason(String title) => showMenuSheet<String>(
    context,
    title: title,
    entries: [for (final r in _reportReasons) MenuEntry(r, r, Icons.flag_outlined)],
  );

  Future<void> _storyMenu(Story s) async {
    final choice = await showMenuSheet<String>(
      context,
      entries: s.isMine
          ? const [MenuEntry('delete', 'Удалить запись', Icons.delete_outline_rounded, danger: true)]
          : const [MenuEntry('report', 'Пожаловаться', Icons.flag_outlined)],
    );
    if (!mounted || choice == null) return;
    if (choice == 'delete') {
      final gone = await deleteStoryFlow(context, _store, s);
      if (gone && mounted) Navigator.of(context).pop();
      return;
    }
    final reason = await _pickReason('ЧТО НЕ ТАК С ЗАПИСЬЮ');
    if (reason == null) return;
    try {
      await _store.report(targetType: 'story', targetId: s.id, reason: reason);
      _store.say('Жалоба отправлена. Спасибо, что бережёшь других.');
    } catch (e) {
      _store.say(_store.describe(e), error: true);
    }
  }

  Future<void> _responseMenu(StoryResponse r) async {
    final choice = await showMenuSheet<String>(
      context,
      entries: r.isMine
          ? const [MenuEntry('delete', 'Удалить отклик', Icons.delete_outline_rounded, danger: true)]
          : const [MenuEntry('report', 'Пожаловаться на отклик', Icons.flag_outlined)],
    );
    if (!mounted || choice == null) return;
    if (choice == 'delete') {
      final yes = await confirm(context, title: 'УДАЛИТЬ ОТКЛИК?', text: 'Его больше никто не увидит.', action: 'УДАЛИТЬ', danger: true);
      if (!yes) return;
      try {
        await _store.deleteResponse(r);
        if (mounted) setState(() => _responses = _responses?.where((x) => x.id != r.id).toList());
      } catch (e) {
        _store.say(_store.describe(e), error: true);
      }
      return;
    }
    final reason = await _pickReason('ЧТО НЕ ТАК С ОТКЛИКОМ');
    if (reason == null) return;
    try {
      await _store.report(targetType: 'response', targetId: r.id, reason: reason);
      _store.say('Жалоба отправлена. Спасибо, что бережёшь других.');
    } catch (e) {
      _store.say(_store.describe(e), error: true);
    }
  }

  void _help() => Navigator.of(context).push(MaterialPageRoute(builder: (_) => const HelpScreen()));

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    return ListenableBuilder(
      listenable: _store,
      builder: (context, _) {
        final s = _store.storyById(widget.storyId);
        if (s == null) return _Gone(onBack: () => Navigator.of(context).pop());
        final art = artFor(s.category);
        final crisis = soundsLikeCrisis('${s.title} ${s.body}');
        final responses = _responses;

        return Scaffold(
          backgroundColor: c.bg,
          body: Column(
            children: [
              Expanded(
                child: CustomScrollView(
                  controller: _scroll,
                  physics: const BouncingScrollPhysics(),
                  slivers: [
                    HeroHeader(
                      image: art.image,
                      alignment: art.alignment,
                      title: s.category.toUpperCase(),
                      jp: '記録',
                      barTitleAlways: true,
                      expandedHeight: 310,
                      caption: '${s.category.toUpperCase()} · ${shortAgo(s.createdAt)}',
                      leading: IconAction(
                        icon: Icons.arrow_back,
                        tooltip: 'Назад',
                        onTap: () => Navigator.of(context).maybePop(),
                        color: c.onArt,
                      ),
                      actions: [
                        IconAction(
                          icon: s.savedByMe ? Icons.bookmark_rounded : Icons.bookmark_border_rounded,
                          tooltip: s.savedByMe ? 'Убрать из архива' : 'Сохранить в архив',
                          onTap: () => _store.toggleSaved(s.id),
                          color: s.savedByMe ? c.lamp : c.onArt,
                        ),
                        IconAction(icon: Icons.more_vert_rounded, tooltip: 'Ещё', onTap: () => _storyMenu(s), color: c.onArt),
                      ],
                      titleWidget: Text(
                        s.title,
                        maxLines: 4,
                        overflow: TextOverflow.ellipsis,
                        style: T.title(c.onArt, size: 25).copyWith(
                          shadows: [Shadow(color: Colors.black.withValues(alpha: 0.6), blurRadius: 18)],
                        ),
                      ),
                      bottom: Row(
                        children: [
                          Avatar(kind: s.authorAvatarKind, url: s.authorAvatarUrl, size: 26, onArt: true, lit: s.isMine),
                          const SizedBox(width: 10),
                          Flexible(
                            child: Text(
                              s.isMine ? '${s.authorNickname} · ты' : s.authorNickname,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: T.label(s.isMine ? c.lamp : c.onArt.withValues(alpha: 0.9), size: 11, tracking: 0.8),
                            ),
                          ),
                        ],
                      ),
                    ),
                    SliverPadding(
                      padding: const EdgeInsets.fromLTRB(20, 6, 20, 0),
                      sliver: SliverList.list(
                        children: [
                          if (s.hidden) ...[
                            Note(
                              text: 'ЗАПИСЬ СКРЫТА ПОСЛЕ ЖАЛОБ. ЕЁ ВИДИШЬ ТОЛЬКО ТЫ — ДРУГИМ ОНА НЕ ПОКАЗЫВАЕТСЯ.',
                              color: c.alarm,
                            ),
                            const SizedBox(height: 18),
                          ],
                          SelectionArea(child: Text(s.body, style: T.body(c.text, size: 16.5, height: 1.66))),
                          const SizedBox(height: 22),
                          if (crisis) ...[
                            Note(
                              text: s.isMine
                                  ? 'ЕСЛИ ТЕБЕ СЕЙЧАС ОЧЕНЬ ПЛОХО — ПОЗВОНИ. ЭТО БЕСПЛАТНО, АНОНИМНО И КРУГЛОСУТОЧНО.'
                                  : 'АВТОРУ СЕЙЧАС МОЖЕТ БЫТЬ ОЧЕНЬ ТЯЖЕЛО. ОТВЕТЬ БЕРЕЖНО: БЕЗ СОВЕТОВ «ВЗЯТЬ СЕБЯ В РУКИ», ПРОСТО БУДЬ РЯДОМ.',
                              color: c.lamp,
                              child: GhostButton(
                                label: 'КУДА ПОЗВОНИТЬ',
                                icon: Icons.call_outlined,
                                color: c.lamp,
                                height: 42,
                                onPressed: _help,
                              ),
                            ),
                            const SizedBox(height: 20),
                          ],
                          _NearbyButton(story: s, onTap: () => _store.toggleLike(s.id)),
                          const SizedBox(height: 30),
                          SectionLabel('ОТКЛИКИ', trailing: twoDigits(responses?.length ?? s.responseCount)),
                          const SizedBox(height: 6),
                        ],
                      ),
                    ),
                    if (responses == null && _loadProblem == null)
                      const SliverToBoxAdapter(child: Padding(padding: EdgeInsets.all(28), child: Center(child: LoadingLine())))
                    else if (responses == null)
                      SliverToBoxAdapter(
                        child: Padding(
                          padding: const EdgeInsets.all(20),
                          child: Column(
                            children: [
                              Text(_loadProblem!, textAlign: TextAlign.center, style: T.body(c.textDim, size: 14.5)),
                              const SizedBox(height: 14),
                              SizedBox(width: 200, child: GhostButton(label: 'ЕЩЁ РАЗ', onPressed: _load)),
                            ],
                          ),
                        ),
                      )
                    else if (responses.isEmpty)
                      SliverToBoxAdapter(
                        child: Padding(
                          padding: const EdgeInsets.fromLTRB(20, 14, 20, 20),
                          child: Text(
                            s.isMine
                                ? 'Пока никто не ответил. Отклики придут во вкладку «Отклик».'
                                : 'Никто ещё не ответил. Можно быть первым — иногда хватает пары слов.',
                            style: T.body(c.textDim, size: 14.5),
                          ),
                        ),
                      )
                    else
                      SliverList.separated(
                        itemCount: responses.length,
                        separatorBuilder: (_, __) => const Hairline(indent: 64),
                        itemBuilder: (context, i) => _ResponseRow(r: responses[i], onMenu: () => _responseMenu(responses[i])),
                      ),
                    const SliverToBoxAdapter(child: SizedBox(height: 24)),
                  ],
                ),
              ),
              _ReplyBar(
                controller: _reply,
                sending: _sending,
                name: _store.profile?.nickname ?? '',
                onSend: _send,
              ),
            ],
          ),
        );
      },
    );
  }
}

class _NearbyButton extends StatelessWidget {
  const _NearbyButton({required this.story, required this.onTap});

  final Story story;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    final on = story.likedByMe;
    final n = story.likes;
    if (story.isMine) {
      return Text(
        n == 0 ? 'ПОКА НИКТО НЕ ОТМЕТИЛСЯ' : '${counted(n, 'ЧЕЛОВЕК', 'ЧЕЛОВЕКА', 'ЧЕЛОВЕК')} РЯДОМ С ТОБОЙ',
        style: T.label(n == 0 ? c.textFaint : c.lamp, tracking: 1.6),
      );
    }
    return Semantics(
      button: true,
      toggled: on,
      label: on ? 'Ты рядом. Убрать отметку' : 'Я рядом',
      child: ExcludeSemantics(
        child: Material(
          color: on ? c.lamp.withValues(alpha: 0.12) : Colors.transparent,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(2),
            side: BorderSide(color: on ? c.lamp : c.line),
          ),
          child: InkWell(
            onTap: onTap,
            child: SizedBox(
              height: 52,
              child: Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  AnimatedContainer(
                    duration: const Duration(milliseconds: 220),
                    width: 9,
                    height: 9,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      color: on ? c.lamp : Colors.transparent,
                      border: Border.all(color: on ? c.lamp : c.textFaint, width: 1.3),
                      boxShadow: on ? [BoxShadow(color: c.lamp.withValues(alpha: 0.9), blurRadius: 12)] : null,
                    ),
                  ),
                  const SizedBox(width: 12),
                  Text(on ? 'ТЫ РЯДОМ' : 'Я РЯДОМ', style: T.button(on ? c.lamp : c.text, size: 12)),
                  const SizedBox(width: 10),
                  Text('· $n', style: T.label(on ? c.lamp : c.textFaint, size: 11)),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _ResponseRow extends StatelessWidget {
  const _ResponseRow({required this.r, required this.onMenu});

  final StoryResponse r;
  final VoidCallback onMenu;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 16, 8, 14),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Avatar(kind: r.authorAvatarKind, url: r.authorAvatarUrl, size: 30, lit: r.isMine),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Flexible(
                      child: Text(
                        r.isMine ? '${r.authorNickname} · ты' : r.authorNickname,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: T.label(r.isMine ? c.lamp : c.textDim, size: 10.5, tracking: 0.6, weight: FontWeight.w700),
                      ),
                    ),
                    const SizedBox(width: 10),
                    Text(shortAgo(r.createdAt), style: T.label(c.textFaint, size: 9.5)),
                  ],
                ),
                const SizedBox(height: 6),
                SelectionArea(child: Text(r.body, style: T.body(c.text, size: 15, height: 1.55))),
              ],
            ),
          ),
          IconAction(icon: Icons.more_horiz_rounded, tooltip: 'Действия с откликом', onTap: onMenu, color: c.textFaint, size: 18),
        ],
      ),
    );
  }
}

class _ReplyBar extends StatelessWidget {
  const _ReplyBar({required this.controller, required this.sending, required this.name, required this.onSend});

  final TextEditingController controller;
  final bool sending;
  final String name;
  final VoidCallback onSend;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    return DecoratedBox(
      decoration: BoxDecoration(color: c.panel, border: Border(top: BorderSide(color: c.line))),
      child: SafeArea(
        top: false,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(16, 8, 10, 8),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Expanded(
                child: TextField(
                  controller: controller,
                  minLines: 1,
                  maxLines: 5,
                  maxLength: 2000,
                  textCapitalization: TextCapitalization.sentences,
                  cursorColor: c.violet,
                  style: T.body(c.text, size: 15, height: 1.45),
                  decoration: InputDecoration(
                    counterText: '',
                    isDense: true,
                    border: InputBorder.none,
                    contentPadding: const EdgeInsets.symmetric(vertical: 12),
                    hintText: name.isEmpty ? 'Ответить…' : 'Ответить как $name…',
                    hintStyle: T.body(c.textFaint, size: 15, height: 1.45),
                  ),
                ),
              ),
              const SizedBox(width: 8),
              Padding(
                padding: const EdgeInsets.only(bottom: 4),
                child: ValueListenableBuilder<TextEditingValue>(
                  valueListenable: controller,
                  builder: (context, value, _) {
                    final ready = value.text.trim().isNotEmpty && !sending;
                    return Semantics(
                      button: true,
                      enabled: ready,
                      label: 'Отправить отклик',
                      child: Material(
                        color: ready ? c.button : c.line,
                        borderRadius: BorderRadius.circular(2),
                        child: InkWell(
                          onTap: ready ? onSend : null,
                          child: SizedBox(
                            width: 44,
                            height: 40,
                            child: sending
                                ? Center(child: SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 1.5, color: c.onButton)))
                                : Icon(Icons.arrow_upward_rounded, size: 20, color: ready ? c.onButton : c.textFaint),
                          ),
                        ),
                      ),
                    );
                  },
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _Gone extends StatelessWidget {
  const _Gone({required this.onBack});

  final VoidCallback onBack;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    return Scaffold(
      backgroundColor: c.bg,
      body: SafeArea(
        child: Column(
          children: [
            Align(alignment: Alignment.centerLeft, child: IconAction(icon: Icons.arrow_back, tooltip: 'Назад', onTap: onBack)),
            const Expanded(
              child: Center(
                child: EmptyState(glyph: '静', title: 'ЗАПИСИ НЕТ', text: 'Её удалили или скрыли.'),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
