String timeText(DateTime value) {
  final difference = DateTime.now().difference(value);
  if (difference.inMinutes < 1) return 'только что';
  if (difference.inMinutes < 60) return '${difference.inMinutes} мин назад';
  if (difference.inHours < 24) return '${difference.inHours} ч назад';
  if (difference.inDays == 1) return 'вчера';
  if (difference.inDays < 7) return '${difference.inDays} дн назад';
  final day = value.day.toString().padLeft(2, '0');
  final month = value.month.toString().padLeft(2, '0');
  return '$day.$month.${value.year}';
}

