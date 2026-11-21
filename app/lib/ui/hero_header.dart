import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'art.dart';
import 'tokens.dart';
import 'type.dart';

/// Шапка экрана-вкладки: картинка во всю ширину с большим заголовком. При
/// прокрутке картинка уезжает медленнее текста и сворачивается в узкую
/// тёмную полосу с маленьким заголовком — кадр остаётся, место освобождается.
class HeroHeader extends StatelessWidget {
  const HeroHeader({
    super.key,
    required this.image,
    required this.title,
    required this.jp,
    this.alignment = Alignment.center,
    this.caption,
    this.expandedHeight = 260,
    this.actions = const [],
    this.leading,
    this.bottom,
    this.titleWidget,
    this.titleSize = 34,
    this.barTitleAlways = false,
  });

  /// Маленький заголовок в полосе виден и в развёрнутой шапке — когда он
  /// говорит другое, чем большой (тема над заголовком истории).
  final bool barTitleAlways;

  final String image;
  final Alignment alignment;
  final String title;

  /// Вместо машинного глитч-заголовка — свой, например заголовок истории
  /// книжной гарнитурой: его написал человек.
  final Widget? titleWidget;
  final double titleSize;
  final String jp;
  final String? caption;
  final double expandedHeight;
  final List<Widget> actions;
  final Widget? leading;

  /// Что лежит под заголовком внутри картинки (аватар, счётчики).
  final Widget? bottom;

  @override
  Widget build(BuildContext context) {
    final top = MediaQuery.paddingOf(context).top;
    return SliverPersistentHeader(
      pinned: true,
      delegate: _HeroDelegate(
        image: image,
        alignment: alignment,
        title: title,
        jp: jp,
        caption: caption,
        topPadding: top,
        expanded: expandedHeight,
        actions: actions,
        leading: leading,
        bottom: bottom,
        titleWidget: titleWidget,
        titleSize: titleSize,
        barTitleAlways: barTitleAlways,
        colors: context.c,
      ),
    );
  }
}

class _HeroDelegate extends SliverPersistentHeaderDelegate {
  _HeroDelegate({
    required this.image,
    required this.alignment,
    required this.title,
    required this.jp,
    required this.caption,
    required this.topPadding,
    required this.expanded,
    required this.actions,
    required this.leading,
    required this.bottom,
    required this.titleWidget,
    required this.titleSize,
    required this.barTitleAlways,
    required this.colors,
  });

  final bool barTitleAlways;
  final Widget? titleWidget;
  final double titleSize;
  final String image;
  final Alignment alignment;
  final String title;
  final String jp;
  final String? caption;
  final double topPadding;
  final double expanded;
  final List<Widget> actions;
  final Widget? leading;
  final Widget? bottom;
  final EdgeColors colors;

  static const double _bar = 56;

  @override
  double get maxExtent => expanded + topPadding;

  @override
  double get minExtent => _bar + topPadding;

  @override
  Widget build(BuildContext context, double shrinkOffset, bool overlapsContent) {
    final range = maxExtent - minExtent;
    final t = range <= 0 ? 1.0 : (shrinkOffset / range).clamp(0.0, 1.0);
    final c = colors;
    final bigOpacity = (1 - t * 1.7).clamp(0.0, 1.0);
    final smallOpacity = ((t - 0.62) / 0.38).clamp(0.0, 1.0);

    return AnnotatedRegion<SystemUiOverlayStyle>(
      value: SystemUiOverlayStyle.light.copyWith(statusBarColor: Colors.transparent),
      child: ClipRect(
        child: Stack(
          fit: StackFit.expand,
          children: [
            Positioned(
              top: -shrinkOffset * 0.45,
              left: 0,
              right: 0,
              height: maxExtent,
              child: HeroArt(image: image, alignment: alignment),
            ),
            IgnorePointer(child: ColoredBox(color: const Color(0xFF07060E).withValues(alpha: 0.78 * t))),
            if (bigOpacity > 0)
              Positioned(
                left: 20,
                right: 56,
                bottom: 20,
                child: Opacity(
                  opacity: bigOpacity,
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      if (caption != null) ...[
                        Text(caption!, style: T.label(c.onArt.withValues(alpha: 0.75), size: 10, tracking: 1.8)),
                        const SizedBox(height: 10),
                      ],
                      titleWidget ?? GlitchTitle(title, size: titleSize),
                      if (bottom != null) ...[const SizedBox(height: 16), bottom!],
                    ],
                  ),
                ),
              ),
            if (bigOpacity > 0)
              Positioned(
                right: 18,
                top: topPadding + _bar + 4,
                child: Opacity(opacity: bigOpacity, child: VerticalJp(jp)),
              ),
            Positioned(
              top: topPadding,
              left: 0,
              right: 0,
              height: _bar,
              child: Row(
                children: [
                  if (leading != null) leading! else const SizedBox(width: 20),
                  Expanded(
                    child: Opacity(
                      opacity: barTitleAlways ? 1 : smallOpacity,
                      child: Text(
                        title,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: T.label(c.onArt, size: 12, tracking: 3, weight: FontWeight.w700),
                      ),
                    ),
                  ),
                  ...actions,
                  const SizedBox(width: 6),
                ],
              ),
            ),
            if (t > 0.98)
              Positioned(left: 0, right: 0, bottom: 0, child: Container(height: 1, color: Colors.white.withValues(alpha: 0.08))),
          ],
        ),
      ),
    );
  }

  @override
  bool shouldRebuild(_HeroDelegate old) =>
      old.image != image ||
      old.alignment != alignment ||
      old.title != title ||
      old.caption != caption ||
      old.topPadding != topPadding ||
      old.expanded != expanded ||
      old.bottom != bottom ||
      old.titleWidget != titleWidget ||
      old.titleSize != titleSize ||
      old.barTitleAlways != barTitleAlways ||
      old.actions != actions ||
      old.colors != colors;
}

/// Закреплённая полоса под шапкой — для вкладок-тем в ленте.
class PinnedBar extends StatelessWidget {
  const PinnedBar({super.key, required this.height, required this.child});

  final double height;
  final Widget child;

  @override
  Widget build(BuildContext context) =>
      SliverPersistentHeader(pinned: true, delegate: _BarDelegate(height, child, context.c.bg, context.c.line));
}

class _BarDelegate extends SliverPersistentHeaderDelegate {
  _BarDelegate(this.height, this.child, this.bg, this.line);

  final double height;
  final Widget child;
  final Color bg;
  final Color line;

  @override
  double get maxExtent => height;

  @override
  double get minExtent => height;

  @override
  Widget build(BuildContext context, double shrinkOffset, bool overlapsContent) => DecoratedBox(
    decoration: BoxDecoration(color: bg, border: Border(bottom: BorderSide(color: line))),
    child: child,
  );

  @override
  bool shouldRebuild(_BarDelegate old) => old.child != child || old.bg != bg || old.height != height;
}
