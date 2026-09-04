import 'package:flutter/material.dart';

import '../widgets/story_card.dart';

class NotificationsPage extends StatelessWidget {
  const NotificationsPage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Уведомления')),
      body: const Center(
        child: Padding(
          padding: EdgeInsets.all(32),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              _BellMark(),
              SizedBox(height: 20),
              Text(
                'У тебя пока нет уведомлений.',
                textAlign: TextAlign.center,
                style: TextStyle(
                  color: storyInk,
                  fontSize: 19,
                  fontWeight: FontWeight.w700,
                ),
              ),
              SizedBox(height: 8),
              Text(
                'Здесь появятся отклики и другие важные события.',
                textAlign: TextAlign.center,
                style: TextStyle(color: Color(0xFF69776F)),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _BellMark extends StatelessWidget {
  const _BellMark();

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 68,
      height: 68,
      decoration: const BoxDecoration(
        color: storyPaleMoss,
        shape: BoxShape.circle,
      ),
      child: const Icon(
        Icons.notifications_none_rounded,
        size: 32,
        color: storyMoss,
      ),
    );
  }
}
