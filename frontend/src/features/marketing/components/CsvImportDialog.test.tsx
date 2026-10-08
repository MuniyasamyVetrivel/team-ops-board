import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it } from 'vitest';

import { api } from '@/lib/api/client';
import { seoExecutive } from '@/test/fixtures';
import { mockApi, renderPage } from '@/test/render';

import type { ImportDefinition, ImportPreview } from '../api';
import { CsvImportDialog } from './CsvImportDialog';

const originalAdapter = api.defaults.adapter;

const definition: ImportDefinition = {
  type: 'keywords',
  label: 'Keywords',
  description: 'SEO keywords per page.',
  maxRows: 5000,
  columns: [
    { name: 'keyword', required: true, description: 'The search phrase', example: 'sap testing' },
    { name: 'volume', required: false, description: 'Monthly searches', example: '880' },
  ],
};

const preview: ImportPreview = {
  type: 'keywords',
  fileName: 'keywords.csv',
  checksum: 'abc123',
  columns: ['keyword', 'volume'],
  unknownColumns: ['Notes'],
  totalRows: 3,
  validCount: 2,
  invalidCount: 1,
  validRows: [
    { line: 2, values: { keyword: 'sap testing', volume: '880' }, errors: [] },
    { line: 4, values: { keyword: 'selenium', volume: '' }, errors: [] },
  ],
  invalidRows: [{ line: 3, values: { keyword: 'erp testing', volume: 'abc' }, errors: [{ column: 'volume', message: 'Must be a whole number' }] }],
};

function csvFile(name = 'keywords.csv') {
  return new File(['keyword,volume\nsap testing,880\n'], name, { type: 'text/csv' });
}

describe('CsvImportDialog', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  it('previews valid and invalid rows and imports only after skipping the invalid ones', async () => {
    const adapter = mockApi({
      'POST /marketing/imports/keywords/preview': () => preview,
      'POST /marketing/imports/keywords/commit': () => ({ type: 'keywords', imported: 2, skipped: 1 }),
    });
    renderPage(<CsvImportDialog definition={definition} open onOpenChange={() => {}} />, seoExecutive);

    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText('keyword')).toBeInTheDocument();
    expect(within(dialog).getByText('Required')).toBeInTheDocument();
    await userEvent.upload(within(dialog).getByLabelText(/CSV file/), csvFile());
    await userEvent.click(within(dialog).getByRole('button', { name: 'Check file' }));

    expect(await within(dialog).findByRole('heading', { name: 'Review keywords.csv' })).toBeInTheDocument();
    expect(within(dialog).getByText('Ignored columns: Notes')).toBeInTheDocument();
    // Invalid rows are shown first, with each problem next to the row.
    expect(within(dialog).getByRole('tab', { name: 'Invalid rows (1)' })).toHaveAttribute('aria-selected', 'true');
    expect(within(dialog).getByText('Must be a whole number')).toBeInTheDocument();

    const importButton = within(dialog).getByRole('button', { name: 'Import 2 rows' });
    expect(importButton).toBeDisabled();
    await userEvent.click(within(dialog).getByLabelText(/Skip the 1 invalid row/));
    await userEvent.click(importButton);

    expect(await within(dialog).findByText('Imported 2 rows')).toBeInTheDocument();
    expect(within(dialog).getByText('1 invalid row was skipped.')).toBeInTheDocument();
    const commit = adapter.mock.calls.find(([config]) => config.url === '/marketing/imports/keywords/commit')?.[0];
    expect(commit?.params).toEqual({ checksum: 'abc123', skipInvalid: true });
    expect(commit?.data).toBeInstanceOf(FormData);
  });

  it('imports a clean file without asking to skip anything', async () => {
    mockApi({
      'POST /marketing/imports/keywords/preview': () => ({ ...preview, unknownColumns: [], totalRows: 2, invalidCount: 0, invalidRows: [] }),
    });
    renderPage(<CsvImportDialog definition={definition} open onOpenChange={() => {}} />, seoExecutive);

    const dialog = await screen.findByRole('dialog');
    await userEvent.upload(within(dialog).getByLabelText(/CSV file/), csvFile());
    await userEvent.click(within(dialog).getByRole('button', { name: 'Check file' }));

    expect(await within(dialog).findByRole('tab', { name: 'Valid rows (2)' })).toHaveAttribute('aria-selected', 'true');
    expect(within(dialog).queryByLabelText(/Skip the/)).not.toBeInTheDocument();
    expect(within(dialog).getByRole('button', { name: 'Import 2 rows' })).toBeEnabled();
  });

  it('rejects files that are not CSV before uploading', async () => {
    const adapter = mockApi({});
    renderPage(<CsvImportDialog definition={definition} open onOpenChange={() => {}} />, seoExecutive);

    const dialog = await screen.findByRole('dialog');
    await userEvent.upload(within(dialog).getByLabelText(/CSV file/), csvFile('keywords.xlsx'), { applyAccept: false });
    await userEvent.click(within(dialog).getByRole('button', { name: 'Check file' }));

    expect(await within(dialog).findByText(/Upload a .csv file/)).toBeInTheDocument();
    expect(adapter).not.toHaveBeenCalled();
  });
});
