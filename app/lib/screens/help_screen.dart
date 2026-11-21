import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:url_launcher/url_launcher.dart';

import '../domain/categories.dart';
import '../domain/crisis.dart';
import '../ui/hero_header.dart';
import '../ui/tokens.dart';
import '../ui/type.dart';
import '../ui/widgets.dart';

/// Набирает номер в звонилке. Если звонилки нет (планшет), копирует номер.
Future<void> dial(BuildContext context, Helpline h) async {
  final messenger = ScaffoldMessenger.maybeOf(context);
  var opened = false;
  try {
    opened = await launchUrl(Uri(scheme: 'tel', path: h.dial));
  } catch (_) {}
  if (!opened) {
    await Clipboard.setData(ClipboardData(text: h.dial));
    messenger?.showSnackBar(SnackBar(content: Text('Номер ${h.number} скопирован.')));
  }
}

/// «Если очень плохо». Здесь отвечают такие же люди, а не врачи, — и это
/// надо говорить прямо, вместе с номерами, где ответит специалист.
class HelpScreen extends StatelessWidget {
  const HelpScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    return Scaffold(
      backgroundColor: c.bg,
      body: CustomScrollView(
        physics: const BouncingScrollPhysics(),
        slivers: [
          HeroHeader(
            image: kDaisies,
            alignment: const Alignment(0.3, 0.7),
            title: 'ЕСЛИ ОЧЕНЬ ПЛОХО',
            titleSize: 26,
            jp: '助け',
            expandedHeight: 250,
            caption: 'ТЫ НЕ ОДИН',
            leading: IconAction(icon: Icons.arrow_back, tooltip: 'Назад', onTap: () => Navigator.of(context).maybePop(), color: c.onArt),
          ),
          SliverPadding(
            padding: const EdgeInsets.fromLTRB(20, 8, 20, 0),
            sliver: SliverList.list(
              children: [
                Text(
                  'В приложении отвечают такие же люди, как ты, — не врачи. Если ты думаешь о том, '
                  'чтобы причинить себе вред, или дома небезопасно — позвони. Там выслушают сразу. '
                  'Можно ничего не объяснять с порога: достаточно сказать «мне плохо».',
                  style: T.body(c.text, size: 16, height: 1.6),
                ),
                const SizedBox(height: 24),
                for (final h in helplines) ...[
                  _HelplineCard(h: h),
                  const SizedBox(height: 10),
                ],
                const SizedBox(height: 14),
                Note(
                  text: 'ЕСЛИ ОПАСНОСТЬ ПРЯМО СЕЙЧАС — ЗВОНИ 112. ЭТОТ НОМЕР РАБОТАЕТ ДАЖЕ БЕЗ ДЕНЕГ НА СЧЕТУ.',
                  color: c.alarm,
                ),
                const SizedBox(height: 40),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _HelplineCard extends StatelessWidget {
  const _HelplineCard({required this.h});

  final Helpline h;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    return Semantics(
      button: true,
      label: '${h.title}, ${h.number}. ${h.note} Позвонить',
      child: ExcludeSemantics(
        child: Material(
          color: c.panel,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(2), side: BorderSide(color: c.line)),
          child: InkWell(
            onTap: () => dial(context, h),
            child: Padding(
              padding: const EdgeInsets.fromLTRB(16, 16, 12, 16),
              child: Row(
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(h.region.toUpperCase(), style: T.label(c.textFaint, size: 9.5, tracking: 1.6)),
                        const SizedBox(height: 6),
                        Text(h.number, style: T.label(c.lamp, size: 24, tracking: 1.5, weight: FontWeight.w700, height: 1.1)),
                        const SizedBox(height: 6),
                        Text(h.title, style: T.title(c.text, size: 15.5)),
                        const SizedBox(height: 4),
                        Text(h.note, style: T.body(c.textDim, size: 13.5, height: 1.45)),
                      ],
                    ),
                  ),
                  const SizedBox(width: 10),
                  Container(
                    width: 46,
                    height: 46,
                    decoration: BoxDecoration(border: Border.all(color: c.lamp.withValues(alpha: 0.6))),
                    child: Icon(Icons.call_outlined, color: c.lamp, size: 20),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
