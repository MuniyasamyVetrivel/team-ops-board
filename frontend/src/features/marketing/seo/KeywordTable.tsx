import { History, Monitor, Pencil, Smartphone } from 'lucide-react';
import { Link } from 'react-router';

import { UserCell } from '@/components/common/UserAvatar';
import { Button } from '@/components/ui/button';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { cn } from '@/lib/utils';

import { formatCount } from '../marketing-format';
import type { KeywordItem } from './api';
import { KeywordStatusBadge, PreviousPosition, StandingBadge, StandingChange } from './SeoBadges';
import { SEARCH_ENGINE_LABELS } from './seo-meta';

interface KeywordTableProps {
  keywords: KeywordItem[];
  month: number;
  year: number;
  /** Hide the page column on a page's own detail. */
  showPage?: boolean;
  /** Shown when the viewer may edit keywords. */
  onEdit?: (keyword: KeywordItem) => void;
  /** Opens the keyword's ranking history. */
  onHistory?: (keywordId: number) => void;
  dimmed?: boolean;
}

/** Keywords with their position, previous position and movement for the selected month. */
export function KeywordTable({ keywords, month, year, showPage = true, onEdit, onHistory, dimmed }: KeywordTableProps) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Keyword</TableHead>
          {showPage && <TableHead>Page</TableHead>}
          <TableHead>Position</TableHead>
          <TableHead>Previous</TableHead>
          <TableHead>Change</TableHead>
          <TableHead className="text-right">Target</TableHead>
          <TableHead className="text-right">Volume</TableHead>
          <TableHead className="text-right">Difficulty</TableHead>
          <TableHead>Owner</TableHead>
          <TableHead>Status</TableHead>
          {(onEdit || onHistory) && (
            <TableHead>
              <span className="sr-only">Actions</span>
            </TableHead>
          )}
        </TableRow>
      </TableHeader>
      <TableBody className={cn(dimmed && 'opacity-60')}>
        {keywords.map((keyword) => {
          const DeviceIcon = keyword.device === 'MOBILE' ? Smartphone : Monitor;
          return (
            <TableRow key={keyword.id}>
              <TableCell className="max-w-xs">
                <p className="truncate font-medium">{keyword.keyword}</p>
                <p className="flex items-center gap-1 text-xs text-muted-foreground">
                  <DeviceIcon className="size-3" aria-label={keyword.device === 'MOBILE' ? 'Mobile' : 'Desktop'} />
                  {SEARCH_ENGINE_LABELS[keyword.searchEngine]} · {keyword.location}
                </p>
              </TableCell>
              {showPage && (
                <TableCell className="max-w-56">
                  <Link to={`/digital-marketing/seo/pages/${keyword.page.id}`} className="block truncate text-sm hover:underline">
                    {keyword.page.title}
                  </Link>
                  <p className="truncate font-mono text-xs text-muted-foreground">{keyword.page.url}</p>
                </TableCell>
              )}
              <TableCell>
                <StandingBadge standing={keyword.ranking} month={month} year={year} />
              </TableCell>
              <TableCell className="text-sm">
                <PreviousPosition standing={keyword.ranking} />
              </TableCell>
              <TableCell>
                <StandingChange standing={keyword.ranking} />
              </TableCell>
              <TableCell className="text-right text-sm tabular-nums">{keyword.targetPosition === null ? '—' : `#${keyword.targetPosition}`}</TableCell>
              <TableCell className="text-right text-sm tabular-nums">{formatCount(keyword.searchVolume)}</TableCell>
              <TableCell className="text-right text-sm tabular-nums">{formatCount(keyword.keywordDifficulty)}</TableCell>
              <TableCell className="max-w-44">{keyword.owner ? <UserCell name={keyword.owner.fullName} /> : <span className="text-sm text-muted-foreground">No owner</span>}</TableCell>
              <TableCell>
                <KeywordStatusBadge status={keyword.status} />
              </TableCell>
              {(onEdit || onHistory) && (
                <TableCell>
                  <div className="flex">
                    {onHistory && (
                      <Button variant="ghost" size="icon" aria-label={`Ranking history for ${keyword.keyword}`} onClick={() => onHistory(keyword.id)}>
                        <History aria-hidden />
                      </Button>
                    )}
                    {onEdit && (
                      <Button variant="ghost" size="icon" aria-label={`Edit ${keyword.keyword}`} onClick={() => onEdit(keyword)}>
                        <Pencil aria-hidden />
                      </Button>
                    )}
                  </div>
                </TableCell>
              )}
            </TableRow>
          );
        })}
      </TableBody>
    </Table>
  );
}
