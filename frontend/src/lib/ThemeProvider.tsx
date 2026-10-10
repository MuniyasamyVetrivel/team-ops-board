import { useEffect, useMemo, useState, useSyncExternalStore, type ReactNode } from 'react';

import {
  applyTheme,
  readThemePreference,
  resolveTheme,
  systemDarkQuery,
  ThemeContext,
  writeThemePreference,
  type ThemePreference,
} from './theme';

function subscribeToSystem(onChange: () => void) {
  const query = systemDarkQuery();
  query?.addEventListener('change', onChange);
  return () => query?.removeEventListener('change', onChange);
}

const systemIsDark = () => systemDarkQuery()?.matches ?? false;

/** Light, dark or follow the operating system. The choice is a per-browser display preference. */
export function ThemeProvider({ children }: { children: ReactNode }) {
  const [preference, setPreferenceState] = useState<ThemePreference>(readThemePreference);
  const systemDark = useSyncExternalStore(subscribeToSystem, systemIsDark, () => false);
  const resolved = resolveTheme(preference, systemDark);

  useEffect(() => applyTheme(resolved), [resolved]);

  const value = useMemo(
    () => ({
      preference,
      resolved,
      setPreference: (next: ThemePreference) => {
        writeThemePreference(next);
        setPreferenceState(next);
      },
    }),
    [preference, resolved],
  );

  return <ThemeContext value={value}>{children}</ThemeContext>;
}
