import { ImagePlus, LoaderCircle, Move, Trash2, ZoomIn } from 'lucide-react';
import { useEffect, useRef, useState, type KeyboardEvent, type PointerEvent } from 'react';
import { toast } from 'sonner';

import { FormBanner } from '@/components/common/FormField';
import { UserAvatar, type AvatarUser } from '@/components/common/UserAvatar';
import { Button } from '@/components/ui/button';
import { Dialog, DialogBody, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { errorMessage } from '@/lib/api/errors';

import { useRemovePhoto, useSetPhoto } from './api';
import {
  clampCrop,
  cropRect,
  MAX_ZOOM,
  MIN_ZOOM,
  PHOTO_MIN_SIZE,
  PHOTO_OUTPUT_SIZE,
  PHOTO_TYPES,
  photoFileProblem,
  type CropState,
} from './photo-rules';

/** The crop square, in CSS pixels. */
const FRAME = 288;
const START: CropState = { zoom: 1, offsetX: 0, offsetY: 0 };

interface Loaded {
  url: string;
  image: HTMLImageElement;
  width: number;
  height: number;
}

/** Draws the chosen square onto a canvas and returns it as a JPEG. */
function exportSquare(loaded: Loaded, crop: CropState): Promise<Blob> {
  const rect = cropRect({ width: loaded.width, height: loaded.height }, FRAME, crop);
  const size = Math.min(PHOTO_OUTPUT_SIZE, Math.round(rect.size));
  const canvas = document.createElement('canvas');
  canvas.width = size;
  canvas.height = size;
  const context = canvas.getContext('2d');
  if (!context) return Promise.reject(new Error('Your browser cannot crop images'));
  context.fillStyle = '#ffffff';
  context.fillRect(0, 0, size, size);
  context.imageSmoothingQuality = 'high';
  context.drawImage(loaded.image, rect.sx, rect.sy, rect.size, rect.size, 0, 0, size, size);
  return new Promise((resolve, reject) =>
    canvas.toBlob((blob) => (blob ? resolve(blob) : reject(new Error('Could not prepare the photo'))), 'image/jpeg', 0.9),
  );
}

/**
 * Change or remove a profile photo: pick a JPG, PNG or WebP (up to 2 MB), zoom and drag it inside the square, and
 * save. The browser sends the cropped square; the server checks and resizes it again. {@code userId} null is the
 * signed-in user's own photo; someone else's is the Super Admin's alone (the server enforces it).
 */
export function PhotoDialog({
  open,
  onOpenChange,
  userId,
  name,
  current,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  userId: number | null;
  name: string;
  current: AvatarUser | null;
}) {
  const setPhoto = useSetPhoto();
  const removePhoto = useRemovePhoto();
  const input = useRef<HTMLInputElement>(null);
  const drag = useRef<{ x: number; y: number; crop: CropState } | null>(null);
  const [loaded, setLoaded] = useState<Loaded | null>(null);
  const [crop, setCrop] = useState<CropState>(START);
  const [problem, setProblem] = useState<string | null>(null);
  const busy = setPhoto.isPending || removePhoto.isPending;
  const hasPhoto = current?.photoVersion != null;

  useEffect(() => () => {
    if (loaded) URL.revokeObjectURL(loaded.url);
  }, [loaded]);

  const reset = () => {
    setLoaded(null);
    setCrop(START);
    setProblem(null);
    if (input.current) input.current.value = '';
  };

  const close = (next: boolean) => {
    if (busy) return;
    if (!next) reset();
    onOpenChange(next);
  };

  const choose = (file: File | undefined) => {
    if (!file) return;
    const wrong = photoFileProblem(file);
    if (wrong) {
      setProblem(wrong);
      return;
    }
    const url = URL.createObjectURL(file);
    const image = new Image();
    image.onload = () => {
      if (Math.min(image.naturalWidth, image.naturalHeight) < PHOTO_MIN_SIZE) {
        URL.revokeObjectURL(url);
        setProblem(`The photo must be at least ${PHOTO_MIN_SIZE} × ${PHOTO_MIN_SIZE} pixels.`);
        return;
      }
      setProblem(null);
      setCrop(START);
      setLoaded({ url, image, width: image.naturalWidth, height: image.naturalHeight });
    };
    image.onerror = () => {
      URL.revokeObjectURL(url);
      setProblem('This image could not be opened. Choose a JPG, PNG or WebP image.');
    };
    image.src = url;
  };

  const update = (next: CropState) => {
    if (loaded) setCrop(clampCrop({ width: loaded.width, height: loaded.height }, FRAME, next));
  };

  const onPointerDown = (event: PointerEvent<HTMLDivElement>) => {
    event.currentTarget.setPointerCapture(event.pointerId);
    drag.current = { x: event.clientX, y: event.clientY, crop };
  };
  const onPointerMove = (event: PointerEvent<HTMLDivElement>) => {
    const start = drag.current;
    if (!start) return;
    update({ ...start.crop, offsetX: start.crop.offsetX + event.clientX - start.x, offsetY: start.crop.offsetY + event.clientY - start.y });
  };
  const onKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    const step = event.shiftKey ? 20 : 5;
    const moves: Record<string, [number, number]> = { ArrowLeft: [step, 0], ArrowRight: [-step, 0], ArrowUp: [0, step], ArrowDown: [0, -step] };
    const move = moves[event.key];
    if (!move) return;
    event.preventDefault();
    update({ ...crop, offsetX: crop.offsetX + move[0], offsetY: crop.offsetY + move[1] });
  };

  const save = async () => {
    if (!loaded) return;
    setProblem(null);
    try {
      const blob = await exportSquare(loaded, crop);
      await setPhoto.mutateAsync({ userId, file: blob });
      toast.success('Photo updated');
      reset();
      onOpenChange(false);
    } catch (error) {
      setProblem(errorMessage(error, 'The photo could not be saved. Please try again.'));
    }
  };

  const remove = async () => {
    setProblem(null);
    try {
      await removePhoto.mutateAsync(userId);
      toast.success('Photo removed');
      reset();
      onOpenChange(false);
    } catch (error) {
      setProblem(errorMessage(error, 'The photo could not be removed. Please try again.'));
    }
  };

  const scale = loaded ? (FRAME / Math.min(loaded.width, loaded.height)) * crop.zoom : 1;

  return (
    <Dialog open={open} onOpenChange={close}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{userId === null ? 'Your profile photo' : `Profile photo of ${name}`}</DialogTitle>
          <DialogDescription>JPG, PNG or WebP, up to 2 MB. Zoom and drag to choose the square that is shown.</DialogDescription>
        </DialogHeader>
        <DialogBody className="space-y-4">
          <FormBanner message={problem} />
          <input
            ref={input}
            type="file"
            accept={PHOTO_TYPES.join(',')}
            className="sr-only"
            aria-label="Choose a photo"
            onChange={(event) => choose(event.target.files?.[0])}
          />
          {loaded ? (
            <div className="space-y-4">
              <div
                role="img"
                aria-label="Crop area: drag, or use the arrow keys, to move the photo"
                tabIndex={0}
                className="relative mx-auto cursor-grab touch-none overflow-hidden rounded-xl bg-muted outline-none select-none focus-visible:ring-[3px] focus-visible:ring-ring/40 active:cursor-grabbing"
                style={{ width: FRAME, height: FRAME }}
                onPointerDown={onPointerDown}
                onPointerMove={onPointerMove}
                onPointerUp={() => (drag.current = null)}
                onPointerCancel={() => (drag.current = null)}
                onKeyDown={onKeyDown}
              >
                <img
                  src={loaded.url}
                  alt=""
                  draggable={false}
                  className="pointer-events-none absolute max-w-none"
                  style={{
                    width: loaded.width * scale,
                    height: loaded.height * scale,
                    left: FRAME / 2 - (loaded.width * scale) / 2 + crop.offsetX,
                    top: FRAME / 2 - (loaded.height * scale) / 2 + crop.offsetY,
                  }}
                />
                {/* The circle avatars show, over the square that is saved. */}
                <div className="pointer-events-none absolute inset-0 rounded-full shadow-[0_0_0_9999px_rgba(15,23,42,0.45)]" aria-hidden />
              </div>
              <label className="flex items-center gap-3 text-sm">
                <ZoomIn className="size-4 shrink-0 text-muted-foreground" aria-hidden />
                <span className="sr-only">Zoom</span>
                <input
                  type="range"
                  min={MIN_ZOOM}
                  max={MAX_ZOOM}
                  step={0.01}
                  value={crop.zoom}
                  onChange={(event) => update({ ...crop, zoom: Number(event.target.value) })}
                  className="w-full accent-primary"
                  aria-label="Zoom"
                />
              </label>
              <p className="flex items-center gap-1.5 text-xs text-muted-foreground">
                <Move className="size-3.5" aria-hidden />
                Drag the photo, or use the arrow keys, to position it.
              </p>
            </div>
          ) : (
            <div className="flex flex-col items-center gap-4 py-4">
              <UserAvatar name={name} user={current} size="xl" />
              <Button type="button" variant="outline" onClick={() => input.current?.click()} disabled={busy}>
                <ImagePlus aria-hidden />
                {hasPhoto ? 'Choose a new photo' : 'Choose a photo'}
              </Button>
            </div>
          )}
        </DialogBody>
        <DialogFooter className="sm:justify-between">
          <div>
            {hasPhoto && !loaded && (
              <Button type="button" variant="ghost" className="text-destructive hover:text-destructive" onClick={() => void remove()} disabled={busy}>
                {removePhoto.isPending ? <LoaderCircle className="animate-spin" aria-hidden /> : <Trash2 aria-hidden />}
                Remove photo
              </Button>
            )}
            {loaded && (
              <Button type="button" variant="ghost" onClick={reset} disabled={busy}>
                Choose another
              </Button>
            )}
          </div>
          <div className="flex gap-2">
            <Button type="button" variant="outline" onClick={() => close(false)} disabled={busy}>
              Cancel
            </Button>
            <Button type="button" onClick={() => void save()} disabled={!loaded || busy}>
              {setPhoto.isPending && <LoaderCircle className="animate-spin" aria-hidden />}
              Save photo
            </Button>
          </div>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
