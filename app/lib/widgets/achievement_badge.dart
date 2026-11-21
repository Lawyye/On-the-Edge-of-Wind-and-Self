import 'package:flutter/material.dart';

import '../domain/models.dart';
import '../ui/art.dart';
import '../ui/format.dart';
import '../ui/tokens.dart';
import '../ui/type.dart';

/// Достижение, как значок в библиотеке игр: пиксельная иконка, название,
/// что нужно сделать и полоса прогресса. Полученное светится тёплым.
class AchievementBadge extends StatelessWidget {
  const AchievementBadge({super.key, required this.a});

  final Achievement a;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    final ink = a.earned ? c.lamp : c.textFaint;
    return Semantics(
      label: '${a.title}. ${a.description} ${a.earned ? 'Получено' : 'Прогресс ${a.progress} из ${a.goal}'}',
      child: ExcludeSemantics(
        child: Container(
          padding: const EdgeInsets.fromLTRB(14, 14, 14, 13),
          decoration: BoxDecoration(
            color: a.earned ? c.lamp.withValues(alpha: c.isDark ? 0.06 : 0.08) : c.panel,
            border: Border.all(color: a.earned ? c.lamp.withValues(alpha: 0.55) : c.line),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Opacity(
                    opacity: a.earned ? 1 : 0.55,
                    child: PixelIcon(a.code, color: ink, size: 30, glow: a.earned),
                  ),
                  const Spacer(),
                  Text(
                    a.earned ? dayMonth(a.earnedAt!) : '${a.progress}/${a.goal}',
                    style: T.label(a.earned ? c.lamp : c.textFaint, size: 9.5),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              Text(
                a.title.toUpperCase(),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: T.label(a.earned ? c.text : c.textDim, size: 10.5, tracking: 1.3, weight: FontWeight.w700),
              ),
              const SizedBox(height: 5),
              Text(
                a.description,
                maxLines: 3,
                overflow: TextOverflow.ellipsis,
                style: T.body(c.textDim, size: 12.8, height: 1.42),
              ),
              const SizedBox(height: 12),
              const Spacer(),
              Container(
                height: 3,
                color: c.line,
                alignment: Alignment.centerLeft,
                child: FractionallySizedBox(
                  widthFactor: a.earned ? 1 : a.fraction,
                  child: Container(color: a.earned ? c.lamp : c.violet),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
