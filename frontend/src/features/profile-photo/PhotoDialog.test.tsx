import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { InternalAxiosRequestConfig } from 'axios';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { UserAvatar } from '@/components/common/UserAvatar';
import { api } from '@/lib/api/client';
import { superAdmin, webEmployee } from '@/test/fixtures';
import { mockApi, renderPage } from '@/test/render';

import { PhotoDialog } from './PhotoDialog';

const originalAdapter = api.defaults.adapter;
const originalImage = globalThis.Image;
const originalGetContext = HTMLCanvasElement.prototype.getContext;
const originalToBlob = HTMLCanvasElement.prototype.toBlob;

/** jsdom does not decode images or draw on canvases: stand-ins that behave like an 800 × 600 photo. */
class FakeImage {
  onload: (() => void) | null = null;
  onerror: (() => void) | null = null;
  naturalWidth = 800;
  naturalHeight = 600;
  /** Radix Avatar listens on its own Image; it never loads here, so the initials stay. */
  addEventListener() {}
  removeEventListener() {}
  set src(_value: string) {
    setTimeout(() => this.onload?.());
  }
}

const drawImage = vi.fn();

describe('PhotoDialog', () => {
  beforeEach(() => {
    vi.stubGlobal('Image', FakeImage);
    URL.createObjectURL = vi.fn(() => 'blob:photo');
    URL.revokeObjectURL = vi.fn();
    HTMLCanvasElement.prototype.getContext = vi.fn(() => ({ fillRect: vi.fn(), drawImage, fillStyle: '', imageSmoothingQuality: 'low' })) as never;
    HTMLCanvasElement.prototype.toBlob = function toBlob(callback: BlobCallback) {
      callback(new Blob(['jpeg'], { type: 'image/jpeg' }));
    };
  });

  afterEach(() => {
    api.defaults.adapter = originalAdapter;
    globalThis.Image = originalImage;
    HTMLCanvasElement.prototype.getContext = originalGetContext;
    HTMLCanvasElement.prototype.toBlob = originalToBlob;
    vi.unstubAllGlobals();
    drawImage.mockReset();
  });

  it('crops the chosen photo to a centred square in the browser and uploads it as your own photo', async () => {
    const user = userEvent.setup();
    const uploads: InternalAxiosRequestConfig[] = [];
    mockApi({ 'PUT /users/me/photo': (config) => (uploads.push(config), { ...summary, photoVersion: 9 }) });
    const onOpenChange = vi.fn();
    renderPage(<PhotoDialog open onOpenChange={onOpenChange} userId={null} name="Karthik Raj" current={{ id: 4, photoVersion: null }} />, webEmployee);

    await user.upload(screen.getByLabelText('Choose a photo'), new File(['png'], 'me.png', { type: 'image/png' }));
    expect(await screen.findByRole('img', { name: /Crop area/ })).toBeInTheDocument();
    expect(screen.getByRole('slider', { name: 'Zoom' })).toHaveValue('1');

    await user.click(screen.getByRole('button', { name: 'Save photo' }));

    await waitFor(() => expect(uploads).toHaveLength(1));
    expect(uploads[0]!.data).toBeInstanceOf(FormData);
    expect((uploads[0]!.data as FormData).get('file')).toBeInstanceOf(Blob);
    // 800 × 600: the centred 600 × 600 square, drawn at 512 × 512.
    expect(drawImage).toHaveBeenCalledWith(expect.anything(), 100, 0, 600, 600, 0, 0, 512, 512);
    await waitFor(() => expect(onOpenChange).toHaveBeenCalledWith(false));
  });

  it('refuses other file types before uploading anything', async () => {
    const user = userEvent.setup({ applyAccept: false });
    const adapter = mockApi({});
    renderPage(<PhotoDialog open onOpenChange={vi.fn()} userId={null} name="Karthik Raj" current={null} />, webEmployee);

    await user.upload(screen.getByLabelText('Choose a photo'), new File(['gif'], 'me.gif', { type: 'image/gif' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Choose a JPG, PNG or WebP image.');
    expect(screen.queryByRole('img', { name: /Crop area/ })).not.toBeInTheDocument();
    expect(adapter).not.toHaveBeenCalled();
  });

  it("lets the Super Admin remove someone else's photo", async () => {
    const user = userEvent.setup();
    const removed: string[] = [];
    mockApi({
      'GET /users/4/photo': () => new Blob(['jpeg']),
      'DELETE /users/4/photo': (config) => (removed.push(config.url ?? ''), { ...summary, photoVersion: null }),
    });
    const onOpenChange = vi.fn();
    renderPage(<PhotoDialog open onOpenChange={onOpenChange} userId={4} name="Karthik Raj" current={{ id: 4, photoVersion: 7 }} />, superAdmin);

    expect(screen.getByRole('heading', { name: 'Profile photo of Karthik Raj' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Remove photo' }));

    await waitFor(() => expect(removed).toEqual(['/users/4/photo']));
    await waitFor(() => expect(onOpenChange).toHaveBeenCalledWith(false));
  });
});

describe('UserAvatar', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  it('shows initials and fetches nothing without a photo', () => {
    const adapter = mockApi({});
    renderPage(<UserAvatar name="Karthik Raj" user={{ id: 4, photoVersion: null }} />, webEmployee);
    expect(screen.getByText('KR')).toBeInTheDocument();
    expect(adapter).not.toHaveBeenCalled();
  });

  it('fetches the photo with the login token, as a blob, once per version', async () => {
    const photos: InternalAxiosRequestConfig[] = [];
    URL.createObjectURL = vi.fn(() => 'blob:karthik');
    mockApi({ 'GET /users/4/photo': (config) => (photos.push(config), new Blob(['jpeg'])) });
    renderPage(
      <>
        <UserAvatar name="Karthik Raj" user={{ id: 4, photoVersion: 7 }} />
        <UserAvatar name="Karthik Raj" user={{ id: 4, photoVersion: 7 }} size="sm" />
      </>,
      webEmployee,
    );

    await waitFor(() => expect(URL.createObjectURL).toHaveBeenCalledTimes(1));
    expect(photos).toHaveLength(1);
    expect(photos[0]!.responseType).toBe('blob');
    // Until the image loads, the initials stay visible.
    expect(screen.getAllByText('KR')).toHaveLength(2);
  });
});

const summary = { id: 4, fullName: 'Karthik Raj', email: 'karthik.raj@teamops.local', jobTitle: null, status: 'ACTIVE' as const, employeeId: 'EMP173_IN' };
