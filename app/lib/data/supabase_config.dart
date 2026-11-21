/// Адрес проекта и публикуемый ключ.
///
/// Ключ ниже не секрет: он для того и предназначен, чтобы лежать в клиенте, и
/// сам по себе не даёт ничего — доступ решают политики RLS на стороне базы.
/// Секретный ключ (service_role) в приложении не должен появляться никогда.
///
/// Значения переопределяются при сборке, не трогая код:
///   flutter build apk --release --split-per-abi \
///     --dart-define=SUPABASE_URL=... --dart-define=SUPABASE_ANON_KEY=...
library;

const String supabaseUrl = String.fromEnvironment(
  'SUPABASE_URL',
  defaultValue: 'https://wwwxthxyfglcgpuscafz.supabase.co',
);

const String supabaseAnonKey = String.fromEnvironment(
  'SUPABASE_ANON_KEY',
  defaultValue: 'sb_publishable_ErGhh8K1OwB-F5jrcr9GWQ_BnUF_Md9',
);

/// Корзина с аватарами: читают все, пишет каждый только в свою папку.
const String avatarBucket = 'ew-avatars';
