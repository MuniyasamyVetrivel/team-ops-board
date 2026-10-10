import { render, screen } from '@testing-library/react';
import { Mail, Wallet } from 'lucide-react';
import { describe, expect, it } from 'vitest';

import { MarketingKpiCard } from './MarketingKpiCard';
import { RankingBadge, RankingChangeIndicator } from './RankingBadges';
import { TargetProgress } from './TargetProgress';

describe('RankingBadge', () => {
  it('pairs every ranking colour with its label', () => {
    const { container } = render(
      <>
        <RankingBadge position={7} status="TOP_10" />
        <RankingBadge position={15} status="RANKING" />
        <RankingBadge position={null} status="NOT_RANKED" />
      </>,
    );
    const badges = [...container.querySelectorAll('[data-slot="badge"]')].map((badge) => badge.textContent);
    expect(badges).toEqual(['#7 · TOP 10', '#15 · RANKING', 'NR · NOT RANKED']);
  });
});

describe('RankingChangeIndicator', () => {
  it('describes the movement in words for screen readers', () => {
    render(
      <>
        <RankingChangeIndicator change={{ value: 5, movement: 'IMPROVED' }} />
        <RankingChangeIndicator change={{ value: -1, movement: 'DECLINED' }} />
        <RankingChangeIndicator change={{ value: null, movement: 'DECLINED' }} />
        <RankingChangeIndicator change={{ value: 0, movement: 'UNCHANGED' }} />
      </>,
    );
    expect(screen.getByText('Improved by 5 places')).toBeInTheDocument();
    // One line: arrow + amount, with the direction in words for screen readers.
    expect(screen.getByText('Improved by 5 places').parentElement).toHaveTextContent('5');
    expect(screen.getByText('Declined by 1 place')).toBeInTheDocument();
    expect(screen.getByText('Declined: no longer ranked')).toBeInTheDocument();
    expect(screen.getByText('No change')).toBeInTheDocument();
  });
});

describe('TargetProgress', () => {
  it('shows the server-computed achievement, remaining and status', () => {
    render(<TargetProgress label="Leads" target={250} actual={200} achievementPct={80} remaining={50} status="IN_PROGRESS" />);
    expect(screen.getByText('In progress')).toBeInTheDocument();
    expect(screen.getByText('80% achieved')).toBeInTheDocument();
    expect(screen.getByText('50 remaining')).toBeInTheDocument();
    expect(screen.getByRole('progressbar', { name: 'Leads achievement' })).toHaveAttribute('aria-valuenow', '80');
  });

  it('caps the bar at 100% when the target is exceeded', () => {
    render(<TargetProgress label="Leads" target={250} actual={275} achievementPct={110} remaining={0} status="ACHIEVED" />);
    expect(screen.getByText('Achieved')).toBeInTheDocument();
    expect(screen.getByText('110% achieved')).toBeInTheDocument();
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '100');
  });
});

describe('MarketingKpiCard', () => {
  it('shows a dash for missing values', () => {
    render(<MarketingKpiCard label="Open rate" icon={Mail} value={null} format="percent" />);
    expect(screen.getByText('—')).toBeInTheDocument();
  });

  it('shows the change and whether it is good news', () => {
    render(
      <>
        <MarketingKpiCard label="Open rate" icon={Mail} value={35.42} format="percent" previous={30} previousLabel="September" />
        <MarketingKpiCard label="Cost per lead" icon={Wallet} value={500} format="currency" previous={400} previousLabel="September" better="lower" />
        <MarketingKpiCard label="Leads" icon={Mail} value={10} previous={0} previousLabel="September" />
      </>,
    );
    expect(screen.getByText('35.42%')).toBeInTheDocument();
    expect(screen.getByText('18.1% vs September')).toHaveClass('text-status-success');
    expect(screen.getByText('25% vs September')).toHaveClass('text-status-danger');
    expect(screen.getByText('No comparison with September')).toBeInTheDocument();
  });
});
