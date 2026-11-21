import 'package:flutter/material.dart';

import '../domain/categories.dart';
import '../ui/art.dart';
import '../ui/tokens.dart';
import '../ui/type.dart';
import '../ui/widgets.dart';

/// Первый запуск, пока сервер не ответил: картинка уже на месте, подпись
/// мигает. Повторные запуски сюда не попадают — они открываются из кэша.
class BootScreen extends StatelessWidget {
  const BootScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    return Scaffold(
      backgroundColor: const Color(0xFF07060E),
      body: Stack(
        fit: StackFit.expand,
        children: [
          const HeroArt(image: kGirl, alignment: Alignment(0.2, 0), dim: 0.35),
          SafeArea(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(24, 0, 24, 36),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.end,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const GlitchTitle('ON THE EDGE', size: 32),
                  const SizedBox(height: 22),
                  Row(
                    children: [
                      Text('УСТАНОВКА СВЯЗИ', style: T.label(c.onArt.withValues(alpha: 0.7), tracking: 2.6)),
                      const SizedBox(width: 8),
                      BlinkCursor(color: c.onArt.withValues(alpha: 0.8), width: 6, height: 11),
                    ],
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// Сервер не ответил, а показать нечего. Ошибка — словами, кнопка — повторить.
class TroubleScreen extends StatelessWidget {
  const TroubleScreen({super.key, required this.message, required this.onRetry});

  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    return Scaffold(
      backgroundColor: const Color(0xFF07060E),
      body: Stack(
        fit: StackFit.expand,
        children: [
          const HeroArt(image: kWires, alignment: Alignment(-0.3, -0.5), dim: 0.45),
          SafeArea(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(24, 0, 24, 32),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.end,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('СВЯЗИ НЕТ', style: T.label(c.alarm, tracking: 3, weight: FontWeight.w700, size: 12)),
                  const SizedBox(height: 14),
                  Text(message, style: T.body(c.onArt, size: 16.5)),
                  const SizedBox(height: 26),
                  PrimaryButton(label: 'ПОПРОБОВАТЬ ЕЩЁ РАЗ', onPressed: onRetry),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}
