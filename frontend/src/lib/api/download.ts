import { api } from './client';

/** Downloads through the API client (plain links would not carry the bearer token) and saves the file. */
export async function downloadFile(url: string, fileName: string): Promise<void> {
  const response = await api.get<Blob>(url, { responseType: 'blob' });
  const objectUrl = URL.createObjectURL(response.data);
  const link = document.createElement('a');
  link.href = objectUrl;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(objectUrl);
}
