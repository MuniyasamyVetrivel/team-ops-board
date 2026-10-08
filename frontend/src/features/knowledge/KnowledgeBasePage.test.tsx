import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router';
import { afterEach, describe, expect, it } from 'vitest';

import { api } from '@/lib/api/client';
import { superAdmin, webEmployee } from '@/test/fixtures';
import { mockApi, renderPage } from '@/test/render';

import type { ArticleDetail, ArticleListItem, KnowledgeCategory } from './api';
import ArticlePage from './ArticlePage';
import KnowledgeBasePage from './KnowledgeBasePage';

const originalAdapter = api.defaults.adapter;

const categories: KnowledgeCategory[] = [
  { id: 3, name: 'IT', slug: 'it', description: null, articleCount: 2 },
  { id: 2, name: 'HR', slug: 'hr', description: null, articleCount: 1 },
];

const vpn: ArticleListItem = {
  id: 1,
  title: 'Connect to the office VPN',
  slug: 'connect-to-the-office-vpn',
  excerpt: 'Install the VPN client and import the profile.',
  category: { id: 3, name: 'IT', slug: 'it' },
  status: 'PUBLISHED',
  author: null,
  tags: ['vpn'],
  publishedAt: '2026-10-05T05:00:00Z',
  updatedAt: '2026-10-05T05:00:00Z',
  viewCount: 12,
};

const page = (content: ArticleListItem[]) => ({ content, page: 0, size: 20, totalElements: content.length, totalPages: 1 });

describe('KnowledgeBasePage', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  it('lists categories with counts and searches as you type', async () => {
    const adapter = mockApi({ 'GET /knowledge-base/categories': () => categories, 'GET /knowledge-base/articles': () => page([vpn]) });

    renderPage(<KnowledgeBasePage />, webEmployee);

    expect(await screen.findByRole('link', { name: /Connect to the office VPN/ })).toHaveAttribute('href', '/knowledge-base/connect-to-the-office-vpn');
    expect(screen.getByRole('button', { name: /All articles\s*3/ })).toHaveAttribute('aria-current', 'true');
    expect(screen.queryByRole('button', { name: 'New article' })).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Status')).not.toBeInTheDocument();

    await userEvent.type(screen.getByLabelText('Search articles'), 'password');
    await waitFor(() => expect(adapter.mock.calls.filter(([c]) => c.url === '/knowledge-base/articles').at(-1)?.[0].params).toMatchObject({ search: 'password', status: ['PUBLISHED'] }));
  });

  it('shows an empty state for a search with no results', async () => {
    mockApi({ 'GET /knowledge-base/categories': () => categories, 'GET /knowledge-base/articles': () => page([]) });

    renderPage(<KnowledgeBasePage />, superAdmin);
    await userEvent.type(await screen.findByLabelText('Search articles'), 'zzz');

    expect(await screen.findByText('No articles match “zzz”')).toBeInTheDocument();
  });
});

describe('ArticlePage', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  it('renders Markdown but never raw HTML from the article', async () => {
    const article: ArticleDetail = {
      ...vpn,
      body: '## Steps\n\n1. Install the client\n\n<script>alert(1)</script>\n\n[Help](https://example.com)',
      department: null,
      createdAt: '2026-10-05T05:00:00Z',
      version: 0,
      attachments: [],
      canEdit: false,
    };
    mockApi({ 'GET /knowledge-base/articles/connect-to-the-office-vpn': () => article });

    const { container } = renderPage(
      <Routes>
        <Route path="/knowledge-base/:slug" element={<ArticlePage />} />
      </Routes>,
      webEmployee,
      '/knowledge-base/connect-to-the-office-vpn',
    );

    expect(await screen.findByRole('heading', { name: 'Steps' })).toBeInTheDocument();
    expect(screen.getByRole('listitem')).toHaveTextContent('Install the client');
    expect(container.querySelector('script')).toBeNull();
    expect(screen.getByRole('link', { name: 'Help' })).toHaveAttribute('rel', 'noopener noreferrer');
    expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument();
  });
});
