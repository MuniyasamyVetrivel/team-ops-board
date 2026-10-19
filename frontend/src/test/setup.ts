import '@testing-library/jest-dom/vitest';

import { cleanup, configure } from '@testing-library/react';
import { afterEach } from 'vitest';

// findBy*/waitFor default to 1s. Page tests render real charts and forms, and with every file running in parallel
// a slower machine can pass that mark while still being correct, so allow more time before calling it a failure.
configure({ asyncUtilTimeout: 3000 });

// Saving a file clicks a download link (saveBlob). jsdom can't follow it and logs "Not implemented: navigation to
// another Document", so the click still fires but its default navigation is prevented.
const anchorClick = HTMLAnchorElement.prototype.click;
HTMLAnchorElement.prototype.click = function click(this: HTMLAnchorElement) {
  if (this.hasAttribute('download')) {
    this.addEventListener('click', (event) => event.preventDefault(), { once: true });
  }
  anchorClick.call(this);
};

afterEach(() => {
  cleanup();
});
