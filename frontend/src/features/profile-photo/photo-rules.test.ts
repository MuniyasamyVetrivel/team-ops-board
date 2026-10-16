import { describe, expect, it } from 'vitest';

import { clampCrop, cropRect, maxOffset, photoFileProblem, PHOTO_MAX_BYTES } from './photo-rules';

const landscape = { width: 800, height: 600 };
const FRAME = 288;

describe('photoFileProblem', () => {
  it('accepts JPG, PNG and WebP up to 2 MB, like the server', () => {
    expect(photoFileProblem({ type: 'image/jpeg', size: 1000 })).toBeNull();
    expect(photoFileProblem({ type: 'image/png', size: PHOTO_MAX_BYTES })).toBeNull();
    expect(photoFileProblem({ type: 'image/webp', size: 1000 })).toBeNull();
    expect(photoFileProblem({ type: 'image/gif', size: 1000 })).toBe('Choose a JPG, PNG or WebP image.');
    expect(photoFileProblem({ type: 'image/svg+xml', size: 1000 })).toBe('Choose a JPG, PNG or WebP image.');
    expect(photoFileProblem({ type: 'image/png', size: PHOTO_MAX_BYTES + 1 })).toBe('The photo can be at most 2 MB.');
  });
});

describe('cropRect', () => {
  it('starts with the centred square of the shorter side', () => {
    expect(cropRect(landscape, FRAME, { zoom: 1, offsetX: 0, offsetY: 0 })).toEqual({ sx: 100, sy: 0, size: 600 });
  });

  it('zooms into the middle', () => {
    expect(cropRect(landscape, FRAME, { zoom: 2, offsetX: 0, offsetY: 0 })).toEqual({ sx: 250, sy: 150, size: 300 });
  });

  it('pans, but never past the edge of the image', () => {
    // Dragging the image to the right shows its left part.
    expect(cropRect(landscape, FRAME, { zoom: 1, offsetX: 10_000, offsetY: 0 })).toEqual({ sx: 0, sy: 0, size: 600 });
    expect(cropRect(landscape, FRAME, { zoom: 1, offsetX: -10_000, offsetY: 10_000 })).toEqual({ sx: 200, sy: 0, size: 600 });
  });
});

describe('clampCrop', () => {
  it('keeps zoom between 1 and 4 and the image covering the square', () => {
    expect(clampCrop(landscape, FRAME, { zoom: 9, offsetX: 0, offsetY: 0 }).zoom).toBe(4);
    expect(clampCrop(landscape, FRAME, { zoom: 0.2, offsetX: 0, offsetY: 0 }).zoom).toBe(1);
    const limit = maxOffset(landscape, FRAME, 1);
    expect(limit).toEqual({ x: 48, y: 0 });
    expect(clampCrop(landscape, FRAME, { zoom: 1, offsetX: 500, offsetY: -500 })).toEqual({ zoom: 1, offsetX: 48, offsetY: 0 });
  });
});
