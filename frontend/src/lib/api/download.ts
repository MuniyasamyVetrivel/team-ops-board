import { api } from './client';

/** Downloads through the API client (plain links would not carry the bearer token) and saves the file. */
export async function downloadFile(url: string, fileName: string): Promise<void> {
  const response = await api.get<Blob>(url, { responseType: 'blob' });
  saveBlob(response.data, fileName);
}

/** Saves content the app already holds (e.g. a file returned once in a response body). */
export function saveBlob(blob: Blob, fileName: string): void {
  const objectUrl = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = objectUrl;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(objectUrl);
}
