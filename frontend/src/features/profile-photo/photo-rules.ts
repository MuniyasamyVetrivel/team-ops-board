/** Mirrors ProfilePhotoImages on the server: JPG, PNG or WebP, at most 2 MB, at least 64 × 64 pixels. */
export const PHOTO_TYPES = ['image/jpeg', 'image/png', 'image/webp'] as const;
export const PHOTO_MAX_BYTES = 2 * 1024 * 1024;
export const PHOTO_MIN_SIZE = 64;
/** The square the browser sends; the server resizes to at most 512 pixels anyway. */
export const PHOTO_OUTPUT_SIZE = 512;

/** Why a chosen file cannot be used, or null. The server checks the content again. */
export function photoFileProblem(file: { type: string; size: number }): string | null {
  if (!(PHOTO_TYPES as readonly string[]).includes(file.type)) return 'Choose a JPG, PNG or WebP image.';
  if (file.size > PHOTO_MAX_BYTES) return 'The photo can be at most 2 MB.';
  if (file.size === 0) return 'The file is empty.';
  return null;
}

export interface CropState {
  /** 1 = the image's shorter side just fills the square; up to 4. */
  zoom: number;
  /** Pan from the centre, in square-pixels (the square is {@code frame} pixels wide). */
  offsetX: number;
  offsetY: number;
}

export interface CropRect {
  sx: number;
  sy: number;
  size: number;
}

export const MIN_ZOOM = 1;
export const MAX_ZOOM = 4;

/** How far the image may be panned at this zoom, so it always covers the square. */
export function maxOffset(natural: { width: number; height: number }, frame: number, zoom: number) {
  const scale = (frame / Math.min(natural.width, natural.height)) * zoom;
  return {
    x: Math.max(0, (natural.width * scale - frame) / 2),
    y: Math.max(0, (natural.height * scale - frame) / 2),
  };
}

/** Keeps zoom and pan inside the limits, so the square never shows anything outside the image. */
export function clampCrop(natural: { width: number; height: number }, frame: number, crop: CropState): CropState {
  const zoom = Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, crop.zoom));
  const limit = maxOffset(natural, frame, zoom);
  // "|| 0" turns -0 (a clamp against -0) into 0.
  return {
    zoom,
    offsetX: Math.min(limit.x, Math.max(-limit.x, crop.offsetX)) || 0,
    offsetY: Math.min(limit.y, Math.max(-limit.y, crop.offsetY)) || 0,
  };
}

/** The part of the original image inside the square, in image pixels. */
export function cropRect(natural: { width: number; height: number }, frame: number, crop: CropState): CropRect {
  const { zoom, offsetX, offsetY } = clampCrop(natural, frame, crop);
  const scale = (frame / Math.min(natural.width, natural.height)) * zoom;
  const size = frame / scale;
  const sx = natural.width / 2 - offsetX / scale - size / 2;
  const sy = natural.height / 2 - offsetY / scale - size / 2;
  return {
    sx: Math.min(Math.max(0, sx), natural.width - size),
    sy: Math.min(Math.max(0, sy), natural.height - size),
    size,
  };
}
