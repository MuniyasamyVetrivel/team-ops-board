import '@testing-library/jest-dom/vitest';

import { cleanup, configure } from '@testing-library/react';
import { afterEach } from 'vitest';

// findBy*/waitFor default to 1s. Page tests render real charts and forms, and with every file running in parallel
// a slower machine can pass that mark while still being correct, so allow more time before calling it a failure.
configure({ asyncUtilTimeout: 3000 });

afterEach(() => {
  cleanup();
});
