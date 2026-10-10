import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { InternalAxiosRequestConfig } from 'axios';
import { useLocation } from 'react-router';
import { afterEach, describe, expect, it } from 'vitest';

import { api } from '@/lib/api/client';
import { webEmployee } from '@/test/fixtures';
import { mockApi, renderPage } from '@/test/render';

import type { SearchResults } from './api';
import { GlobalSearch } from './GlobalSearch';

const originalAdapter = api.defaults.adapter;

const results: SearchResults = {
  query: 'invoice',
  groups: [
    {
      group: 'TASKS',
      label: 'Tasks',
      total: 7,
      hits: [
        { type: 'TASK', id: 12, code: 'TSK-000012', title: 'Invoice template', subtitle: 'Karthik Raj · Finance portal', status: 'IN_PROGRESS', ref: null },
        { type: 'TASK', id: 13, code: 'TSK-000013', title: 'Invoice export', subtitle: 'Karthik Raj', status: 'TODO', ref: null },
      ],
    },
    {
      group: 'KNOWLEDGE_BASE',
      label: 'Knowledge Base',
      total: 1,
      hits: [{ type: 'ARTICLE', id: 4, code: null, title: 'How invoices are approved', subtitle: 'Finance', status: 'PUBLISHED', ref: 'how-invoices-are-approved' }],
    },
  ],
};

function Where() {
  const location = useLocation();
  return <p data-testid="location">{location.pathname + location.search}</p>;
}

function renderSearch() {
  return renderPage(
    <>
      <GlobalSearch />
      <Where />
    </>,
    webEmployee,
  );
}

describe('GlobalSearch', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  it('groups the results the server allows and opens one with the keyboard', async () => {
    const user = userEvent.setup();
    const requests: InternalAxiosRequestConfig[] = [];
    mockApi({ 'GET /search': (config) => (requests.push(config), results) });
    renderSearch();

    const box = screen.getByRole('combobox', { name: 'Search everything' });
    await user.type(box, 'invoice');
    const list = await screen.findByRole('listbox', { name: 'Search results' });
    expect(requests.at(-1)?.params).toEqual({ q: 'invoice' });
    expect(within(list).getByRole('group', { name: 'Tasks' })).toHaveTextContent('TSK-000012Invoice template');
    expect(within(list).getByRole('button', { name: 'See all 7' })).toBeInTheDocument();
    expect(within(list).getAllByRole('option')).toHaveLength(3);
    expect(within(list).getByRole('option', { name: /Invoice template/ })).toHaveTextContent('In progress');

    await user.keyboard('{ArrowDown}{ArrowDown}');
    expect(within(list).getByRole('option', { name: /How invoices are approved/ })).toHaveAttribute('aria-selected', 'true');
    await user.keyboard('{Enter}');
    expect(screen.getByTestId('location')).toHaveTextContent('/knowledge-base/how-invoices-are-approved');
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
  });

  it('waits for two characters and says when nothing matches', async () => {
    const user = userEvent.setup();
    const requests: InternalAxiosRequestConfig[] = [];
    mockApi({ 'GET /search': (config) => (requests.push(config), { query: 'zz', groups: [] }) });
    renderSearch();

    const box = screen.getByRole('combobox', { name: 'Search everything' });
    await user.type(box, 'z');
    expect(screen.getByText('Type at least 2 characters to search.')).toBeInTheDocument();
    await user.type(box, 'z');
    expect(await screen.findByText(/No matches for/)).toHaveTextContent('No matches for “zz” in anything you can see.');
    expect(requests).toHaveLength(1);

    await user.keyboard('{Escape}');
    expect(screen.queryByText(/No matches for/)).not.toBeInTheDocument();
  });

  it('opens a group’s full list', async () => {
    const user = userEvent.setup();
    mockApi({ 'GET /search': () => results });
    renderSearch();

    await user.type(screen.getByRole('combobox', { name: 'Search everything' }), 'invoice');
    await user.click(await screen.findByRole('button', { name: 'See all 7' }));
    expect(screen.getByTestId('location')).toHaveTextContent('/tasks?search=invoice');
  });

  it('focuses with Ctrl+K', async () => {
    const user = userEvent.setup();
    mockApi({ 'GET /search': () => results });
    renderSearch();

    await user.keyboard('{Control>}k{/Control}');
    expect(screen.getByRole('combobox', { name: 'Search everything' })).toHaveFocus();
  });
});
