import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { ThemeMenu } from '@/layouts/ThemeMenu';

import { readThemePreference, resolveTheme, THEME_STORAGE_KEY } from './theme';
import { ThemeProvider } from './ThemeProvider';

describe('theme', () => {
  afterEach(() => {
    localStorage.clear();
    document.documentElement.classList.remove('dark');
    vi.restoreAllMocks();
  });

  it('follows the system unless light or dark was chosen', () => {
    expect(resolveTheme('system', true)).toBe('dark');
    expect(resolveTheme('system', false)).toBe('light');
    expect(resolveTheme('light', true)).toBe('light');
    expect(resolveTheme('dark', false)).toBe('dark');
  });

  it('defaults to light, also when storage is blocked or holds junk', () => {
    expect(readThemePreference()).toBe('light');
    localStorage.setItem(THEME_STORAGE_KEY, 'system');
    expect(readThemePreference()).toBe('system');
    localStorage.setItem(THEME_STORAGE_KEY, 'purple');
    expect(readThemePreference()).toBe('light');
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    expect(readThemePreference()).toBe('light');
  });

  it('switches the document to dark mode and remembers the choice', async () => {
    const user = userEvent.setup();
    render(
      <ThemeProvider>
        <ThemeMenu />
      </ThemeProvider>,
    );
    expect(document.documentElement).not.toHaveClass('dark');

    await user.click(screen.getByRole('button', { name: 'Change theme' }));
    await user.click(await screen.findByRole('menuitem', { name: /Dark/ }));

    expect(document.documentElement).toHaveClass('dark');
    expect(document.documentElement.style.colorScheme).toBe('dark');
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe('dark');
  });
});
