import 'package:flutter/material.dart';

import 'art.dart';
import 'tokens.dart';
import 'type.dart';

/// Главная кнопка: светлая плашка на тёмном фоне. Одна на экран.
class PrimaryButton extends StatelessWidget {
  const PrimaryButton({
    super.key,
    required this.label,
    required this.onPressed,
    this.busy = false,
    this.height = 54,
    this.icon,
  });

  final String label;
  final VoidCallback? onPressed;
  final bool busy;
  final double height;
  final IconData? icon;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    final enabled = onPressed != null && !busy;
    final bg = enabled || busy ? c.button : c.line;
    final fg = enabled || busy ? c.onButton : c.textFaint;
    return Semantics(
      button: true,
      enabled: enabled,
      label: label,
      child: ExcludeSemantics(
        child: Material(
          color: bg,
          borderRadius: BorderRadius.circular(2),
          child: InkWell(
            onTap: enabled ? onPressed : null,
            borderRadius: BorderRadius.circular(2),
            splashColor: c.violet.withValues(alpha: 0.25),
            child: SizedBox(
              height: height,
              child: Center(
                child: busy
                    ? SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 1.6, color: fg))
                    : Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          if (icon != null) ...[Icon(icon, size: 17, color: fg), const SizedBox(width: 10)],
                          Flexible(
                            child: Text(label, maxLines: 1, overflow: TextOverflow.ellipsis, style: T.button(fg)),
                          ),
                        ],
                      ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// Второстепенная кнопка: рамка без заливки.
class GhostButton extends StatelessWidget {
  const GhostButton({
    super.key,
    required this.label,
    required this.onPressed,
    this.color,
    this.height = 48,
    this.icon,
    this.busy = false,
  });

  final String label;
  final VoidCallback? onPressed;
  final Color? color;
  final double height;
  final IconData? icon;
  final bool busy;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    final ink = onPressed == null ? c.textFaint : (color ?? c.text);
    return Semantics(
      button: true,
      enabled: onPressed != null,
      label: label,
      child: ExcludeSemantics(
        child: Material(
          color: Colors.transparent,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(2),
            side: BorderSide(color: onPressed == null ? c.line : ink.withValues(alpha: 0.55)),
          ),
          child: InkWell(
            onTap: busy ? null : onPressed,
            borderRadius: BorderRadius.circular(2),
            child: SizedBox(
              height: height,
              child: Center(
                child: busy
                    ? SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 1.5, color: ink))
                    : Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          if (icon != null) ...[Icon(icon, size: 16, color: ink), const SizedBox(width: 9)],
                          Flexible(
                            child: Text(label, maxLines: 1, overflow: TextOverflow.ellipsis, style: T.button(ink, size: 11)),
                          ),
                        ],
                      ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// Круглая область нажатия под значок: на картинке и на фоне.
class IconAction extends StatelessWidget {
  const IconAction({
    super.key,
    required this.icon,
    required this.onTap,
    required this.tooltip,
    this.color,
    this.size = 20,
  });

  final IconData icon;
  final VoidCallback onTap;
  final String tooltip;
  final Color? color;
  final double size;

  @override
  Widget build(BuildContext context) => IconButton(
    onPressed: onTap,
    tooltip: tooltip,
    icon: Icon(icon, size: size, color: color ?? context.c.text),
    splashRadius: 22,
    constraints: const BoxConstraints.tightFor(width: 44, height: 44),
    padding: EdgeInsets.zero,
  );
}

/// Подпись раздела: слово, волосок до края и число справа.
class SectionLabel extends StatelessWidget {
  const SectionLabel(this.text, {super.key, this.trailing, this.color});

  final String text;
  final String? trailing;
  final Color? color;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    return Semantics(
      header: true,
      child: Row(
        children: [
          Text(text, style: T.label(color ?? c.textFaint, tracking: 2.2)),
          const SizedBox(width: 12),
          Expanded(child: Container(height: 1, color: c.line)),
          if (trailing != null) ...[
            const SizedBox(width: 12),
            Text(trailing!, style: T.label(c.crt, tracking: 1.6)),
          ],
        ],
      ),
    );
  }
}

class Hairline extends StatelessWidget {
  const Hairline({super.key, this.indent = 0});
  final double indent;

  @override
  Widget build(BuildContext context) =>
      Padding(padding: EdgeInsets.only(left: indent), child: Container(height: 1, color: context.c.line));
}

/// Вкладка-тема: слово, под выбранным — светящаяся черта.
class TopicTab extends StatelessWidget {
  const TopicTab({super.key, required this.label, required this.selected, required this.onTap});

  final String label;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    return Semantics(
      button: true,
      selected: selected,
      child: InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 10),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Text(label.toUpperCase(), style: T.label(selected ? c.text : c.textFaint, size: 10.5, tracking: 1.3)),
              const SizedBox(height: 7),
              AnimatedContainer(
                duration: const Duration(milliseconds: 180),
                height: 2,
                width: selected ? 22 : 0,
                decoration: BoxDecoration(
                  color: c.violet,
                  boxShadow: selected ? [BoxShadow(color: c.violet.withValues(alpha: 0.7), blurRadius: 8)] : null,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// Пустой экран: японский знак, заголовок, строка текста и, может быть, действие.
class EmptyState extends StatelessWidget {
  const EmptyState({
    super.key,
    required this.glyph,
    required this.title,
    required this.text,
    this.action,
    this.onAction,
  });

  final String glyph;
  final String title;
  final String text;
  final String? action;
  final VoidCallback? onAction;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    return Padding(
      padding: const EdgeInsets.fromLTRB(28, 40, 28, 40),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          ExcludeSemantics(
            child: Text(glyph, style: TextStyle(fontFamily: T.jp, fontSize: 40, height: 1, color: c.textFaint.withValues(alpha: 0.5), fontWeight: FontWeight.w300)),
          ),
          const SizedBox(height: 18),
          Text(title, textAlign: TextAlign.center, style: T.label(c.textDim, tracking: 2.6)),
          const SizedBox(height: 10),
          Text(text, textAlign: TextAlign.center, style: T.body(c.textDim, size: 14.5)),
          if (action != null) ...[
            const SizedBox(height: 22),
            SizedBox(width: 220, child: GhostButton(label: action!, onPressed: onAction)),
          ],
        ],
      ),
    );
  }
}

/// Загрузка в духе терминала: подпись и мигающий курсор.
class LoadingLine extends StatelessWidget {
  const LoadingLine({super.key, this.text = 'ПРИЁМ'});
  final String text;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    return Semantics(
      label: 'Загрузка',
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(text, style: T.label(c.textFaint, tracking: 2.4)),
          const SizedBox(width: 8),
          BlinkCursor(color: c.violet, width: 6, height: 11),
        ],
      ),
    );
  }
}

/// Подтверждение важного действия. Кнопка действия справа и подписана тем,
/// что она сделает, — не «ОК».
Future<bool> confirm(
  BuildContext context, {
  required String title,
  required String text,
  required String action,
  bool danger = false,
}) async {
  final c = context.c;
  final result = await showDialog<bool>(
    context: context,
    barrierColor: Colors.black.withValues(alpha: 0.6),
    builder: (context) => Dialog(
      backgroundColor: c.panel,
      insetPadding: const EdgeInsets.symmetric(horizontal: 24),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(2), side: BorderSide(color: c.line)),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(22, 22, 22, 18),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(title, style: T.label(danger ? c.alarm : c.text, tracking: 2, weight: FontWeight.w700, size: 12)),
            const SizedBox(height: 12),
            Text(text, style: T.body(c.textDim, size: 14.5)),
            const SizedBox(height: 22),
            Row(
              children: [
                Expanded(child: GhostButton(label: 'ОТМЕНА', onPressed: () => Navigator.of(context).pop(false))),
                const SizedBox(width: 10),
                Expanded(
                  child: danger
                      ? GhostButton(label: action, color: c.alarm, onPressed: () => Navigator.of(context).pop(true))
                      : PrimaryButton(label: action, height: 48, onPressed: () => Navigator.of(context).pop(true)),
                ),
              ],
            ),
          ],
        ),
      ),
    ),
  );
  return result ?? false;
}

class MenuEntry<V> {
  const MenuEntry(this.value, this.label, this.icon, {this.danger = false});
  final V value;
  final String label;
  final IconData icon;
  final bool danger;
}

/// Нижнее меню действий.
Future<V?> showMenuSheet<V>(BuildContext context, {String? title, required List<MenuEntry<V>> entries}) {
  final c = context.c;
  return showModalBottomSheet<V>(
    context: context,
    backgroundColor: c.panel,
    showDragHandle: false,
    shape: RoundedRectangleBorder(borderRadius: BorderRadius.zero, side: BorderSide(color: c.line)),
    builder: (context) => SafeArea(
      top: false,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(8, 14, 8, 10),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            if (title != null)
              Padding(
                padding: const EdgeInsets.fromLTRB(14, 0, 14, 10),
                child: Text(title, style: T.label(c.textFaint, tracking: 2.2)),
              ),
            for (final e in entries)
              ListTile(
                leading: Icon(e.icon, size: 20, color: e.danger ? c.alarm : c.textDim),
                title: Text(e.label, style: T.body(e.danger ? c.alarm : c.text, size: 15.5, height: 1.3)),
                onTap: () => Navigator.of(context).pop(e.value),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(2)),
              ),
          ],
        ),
      ),
    ),
  );
}

/// Поле ввода: подчёркивание вместо рамки, книжная гарнитура — пишет человек.
InputDecoration fieldDecoration(BuildContext context, {String? hint, Widget? suffix, String? counter}) {
  final c = context.c;
  return InputDecoration(
    hintText: hint,
    hintStyle: T.body(c.textFaint, size: 16, height: 1.4),
    counterText: counter ?? '',
    counterStyle: T.label(c.textFaint, size: 9.5),
    suffixIcon: suffix,
    isDense: true,
    contentPadding: const EdgeInsets.symmetric(vertical: 12),
    filled: false,
    border: UnderlineInputBorder(borderSide: BorderSide(color: c.line)),
    enabledBorder: UnderlineInputBorder(borderSide: BorderSide(color: c.line)),
    focusedBorder: UnderlineInputBorder(borderSide: BorderSide(color: c.violet, width: 1.4)),
  );
}

/// Плашка-предупреждение с цветной чертой слева.
class Note extends StatelessWidget {
  const Note({super.key, required this.text, this.color, this.child});

  final String text;
  final Color? color;
  final Widget? child;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    final bar = color ?? c.textFaint;
    return Container(
      decoration: BoxDecoration(
        color: c.panel,
        border: Border(left: BorderSide(color: bar, width: 2)),
      ),
      padding: const EdgeInsets.fromLTRB(14, 12, 14, 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(text, style: T.label(c.textDim, size: 10.5, tracking: 0.8, height: 1.55)),
          if (child != null) ...[const SizedBox(height: 12), child!],
        ],
      ),
    );
  }
}
