/**
 * File checks for anonymous uploads. Extension and MIME are both attacker-supplied,
 * so the signature check is the one that actually matters — the other two only
 * produce nicer error messages.
 */

export const MAX_FILE_BYTES = 20 * 1024 * 1024; // 20 MB, matching the form's copy

type Kind = { ext: string[]; mime: string[]; signatures: number[][] };

const PK_ZIP = [0x50, 0x4b, 0x03, 0x04];
const PK_EMPTY = [0x50, 0x4b, 0x05, 0x06];
const PK_SPANNED = [0x50, 0x4b, 0x07, 0x08];
const OLE2 = [0xd0, 0xcf, 0x11, 0xe0, 0xa1, 0xb1, 0x1a, 0xe1];

const KINDS: Kind[] = [
  {
    ext: ['pdf'],
    mime: ['application/pdf'],
    signatures: [[0x25, 0x50, 0x44, 0x46]], // %PDF
  },
  {
    // Modern Office formats are ZIP containers; the legacy ones are OLE2.
    ext: ['docx', 'pptx', 'xlsx'],
    mime: [
      'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
      'application/vnd.openxmlformats-officedocument.presentationml.presentation',
      'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    ],
    signatures: [PK_ZIP, PK_EMPTY, PK_SPANNED],
  },
  {
    ext: ['doc', 'ppt', 'xls'],
    mime: ['application/msword', 'application/vnd.ms-powerpoint', 'application/vnd.ms-excel'],
    signatures: [OLE2],
  },
  {
    ext: ['jpg', 'jpeg'],
    mime: ['image/jpeg'],
    signatures: [[0xff, 0xd8, 0xff]],
  },
  {
    ext: ['png'],
    mime: ['image/png'],
    signatures: [[0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]],
  },
];

export const ALLOWED_EXTENSIONS = KINDS.flatMap((k) => k.ext);

export type FileCheck = { ok: true; extension: string } | { ok: false; error: string };

function startsWith(bytes: Uint8Array, signature: number[]): boolean {
  if (bytes.length < signature.length) return false;
  return signature.every((byte, i) => bytes[i] === byte);
}

export function checkUpload(name: string, size: number, bytes: Uint8Array): FileCheck {
  if (size <= 0) {
    return { ok: false, error: 'Файл бос / Файл пустой.' };
  }
  if (size > MAX_FILE_BYTES) {
    return { ok: false, error: 'Файл 20 МБ-тан үлкен болмауы керек / Максимум 20 МБ.' };
  }

  const extension = (name.split('.').pop() ?? '').toLowerCase();
  const kind = KINDS.find((k) => k.ext.includes(extension));
  if (!kind) {
    return { ok: false, error: 'Бұл файл форматына рұқсат етілмеген / Формат файла не поддерживается.' };
  }

  // The bytes must match the format the name claims, so renaming an .exe to .pdf
  // does not get it into storage.
  if (!kind.signatures.some((signature) => startsWith(bytes, signature))) {
    return { ok: false, error: 'Файл мазмұны атауына сәйкес келмейді / Содержимое файла не соответствует расширению.' };
  }

  return { ok: true, extension };
}

/** Strips directory parts and anything that could confuse a storage path. */
export function safeFileName(name: string): string {
  const base = name.split(/[\\/]/).pop() ?? 'file';
  return base.replace(/[^\w.\-Ѐ-ӿ ]+/g, '_').slice(0, 120) || 'file';
}
