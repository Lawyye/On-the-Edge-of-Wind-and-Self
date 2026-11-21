/// Время и числа по-русски. Короткая форма — для лент, где место на счету.
library;

/// «2 Ч», «ВЧЕРА», «21.11» — для меток в ленте.
String shortAgo(DateTime value, {DateTime? now}) {
  final n = now ?? DateTime.now();
  final d = n.difference(value);
  if (d.inMinutes < 1) return 'СЕЙЧАС';
  if (d.inMinutes < 60) return '${d.inMinutes} МИН';
  if (d.inHours < 24) return '${d.inHours} Ч';
  if (d.inDays == 1) return 'ВЧЕРА';
  if (d.inDays < 7) return '${d.inDays} ДН';
  return dayMonth(value, withYear: value.year != n.year);
}

String dayMonth(DateTime v, {bool withYear = false}) {
  final day = v.day.toString().padLeft(2, '0');
  final month = v.month.toString().padLeft(2, '0');
  return withYear ? '$day.$month.${v.year}' : '$day.$month';
}

String fullDate(DateTime v) => dayMonth(v, withYear: true);

String clock(DateTime v) =>
    '${v.hour.toString().padLeft(2, '0')}:${v.minute.toString().padLeft(2, '0')}';

/// Русское множественное число: plural(5, 'отклик', 'отклика', 'откликов').
String plural(int n, String one, String few, String many) {
  final mod10 = n % 10;
  final mod100 = n % 100;
  if (mod10 == 1 && mod100 != 11) return one;
  if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) return few;
  return many;
}

String counted(int n, String one, String few, String many) => '$n ${plural(n, one, few, many)}';

String twoDigits(int n) => n.toString().padLeft(2, '0');
