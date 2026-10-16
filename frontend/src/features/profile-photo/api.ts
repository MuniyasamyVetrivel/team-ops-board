import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { api } from '@/lib/api/client';
import type { UserSummary } from '@/lib/api/types';

import { useAuth } from '@/features/auth/use-auth';

export const photoKeys = {
  all: ['user-photo'] as const,
  photo: (userId: number, version: number) => [...photoKeys.all, userId, version] as const,
};

/**
 * A user's profile photo as a blob, fetched with the login token (an <img> URL could not carry it). Keyed by the
 * photo version, which changes with every new photo, so each version is downloaded once and kept for the session.
 */
export function useUserPhoto(userId: number | undefined, version: number | null | undefined) {
  return useQuery({
    queryKey: photoKeys.photo(userId ?? 0, version ?? 0),
    queryFn: async () => (await api.get<Blob>(`/users/${userId}/photo`, { responseType: 'blob' })).data,
    enabled: userId !== undefined && version !== null && version !== undefined,
    staleTime: Infinity,
    gcTime: 30 * 60_000,
    retry: false,
  });
}

/**
 * Setting or removing a photo changes every list that shows that person, so all queries but the (versioned) photos
 * are refreshed, and the signed-in user's own avatar is updated straight away.
 */
function usePhotoMutation<TInput>(mutationFn: (input: TInput) => Promise<UserSummary>) {
  const queryClient = useQueryClient();
  const { user, updateUser } = useAuth();
  return useMutation({
    mutationFn,
    onSuccess: (summary) => {
      if (user?.id === summary.id) updateUser({ photoVersion: summary.photoVersion ?? null });
      void queryClient.invalidateQueries({ predicate: (query) => query.queryKey[0] !== photoKeys.all[0] });
    },
  });
}

/** {@code userId} null: the signed-in user's own photo ({@code /users/me/photo}). */
export function useSetPhoto() {
  return usePhotoMutation(async ({ userId, file }: { userId: number | null; file: Blob }) => {
    const form = new FormData();
    form.append('file', file, 'photo.jpg');
    return (await api.put<UserSummary>(`/users/${userId ?? 'me'}/photo`, form)).data;
  });
}

export function useRemovePhoto() {
  return usePhotoMutation(async (userId: number | null) => (await api.delete<UserSummary>(`/users/${userId ?? 'me'}/photo`)).data);
}
