import 'package:flutter/material.dart';

import '../data/edgewind_store.dart';
import '../domain/categories.dart';
import '../widgets/story_card.dart';

class ComposeSheet extends StatefulWidget {
  const ComposeSheet({super.key, required this.store});

  final EdgewindStore store;

  @override
  State<ComposeSheet> createState() => _ComposeSheetState();
}

class _ComposeSheetState extends State<ComposeSheet> {
  final _title = TextEditingController();
  final _body = TextEditingController();
  String _category = 'Просто поговорить';
  bool _saving = false;

  @override
  void dispose() {
    _title.dispose();
    _body.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final keyboard = MediaQuery.viewInsetsOf(context).bottom;
    final safeBottom = MediaQuery.paddingOf(context).bottom;
    return Container(
      padding: EdgeInsets.fromLTRB(
        22,
        12,
        22,
        22 + keyboard + (keyboard == 0 ? safeBottom : 0),
      ),
      decoration: const BoxDecoration(
        color: Color(0xFFF7F8F5),
        borderRadius: BorderRadius.vertical(top: Radius.circular(30)),
      ),
      child: SingleChildScrollView(
        physics: const BouncingScrollPhysics(),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const _Handle(),
            const SizedBox(height: 22),
            const Text(
              'Новая запись',
              style: TextStyle(
                fontSize: 25,
                fontWeight: FontWeight.w700,
                color: storyInk,
              ),
            ),
            const SizedBox(height: 6),
            const Text(
              'Имя никто не увидит.',
              style: TextStyle(color: Color(0xFF69776F)),
            ),
            const SizedBox(height: 20),
            DropdownButtonFormField<String>(
              initialValue: _category,
              decoration: const InputDecoration(labelText: 'Тема'),
              items: storyCategories
                  .map(
                    (value) =>
                        DropdownMenuItem(value: value, child: Text(value)),
                  )
                  .toList(),
              onChanged: _saving
                  ? null
                  : (value) => setState(() => _category = value ?? _category),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _title,
              enabled: !_saving,
              maxLength: 120,
              textCapitalization: TextCapitalization.sentences,
              decoration: const InputDecoration(
                labelText: 'Коротко',
                hintText: 'Что случилось?',
              ),
            ),
            const SizedBox(height: 4),
            TextField(
              controller: _body,
              enabled: !_saving,
              minLines: 4,
              maxLines: 7,
              maxLength: 2000,
              textCapitalization: TextCapitalization.sentences,
              decoration: const InputDecoration(
                labelText: 'Подробнее',
                alignLabelWithHint: true,
                hintText: 'Пиши как есть…',
              ),
            ),
            const SizedBox(height: 8),
            SizedBox(
              width: double.infinity,
              child: FilledButton(
                style: FilledButton.styleFrom(
                  padding: const EdgeInsets.symmetric(vertical: 16),
                  backgroundColor: storyInk,
                ),
                onPressed: _saving ? null : _publish,
                child: _saving
                    ? const SizedBox.square(
                        dimension: 20,
                        child: CircularProgressIndicator(
                          strokeWidth: 2,
                          color: Colors.white,
                        ),
                      )
                    : const Text('Опубликовать'),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _publish() async {
    final title = _title.text.trim();
    final body = _body.text.trim();
    if (title.length < 3 || body.length < 10) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Добавь заголовок и пару предложений.')),
      );
      return;
    }
    setState(() => _saving = true);
    await widget.store.createStory(
      category: _category,
      title: title,
      body: body,
    );
    if (mounted) Navigator.pop(context, true);
  }
}

class _Handle extends StatelessWidget {
  const _Handle();

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Container(
        width: 44,
        height: 4,
        decoration: BoxDecoration(
          color: const Color(0xFFCDD5CF),
          borderRadius: BorderRadius.circular(4),
        ),
      ),
    );
  }
}
