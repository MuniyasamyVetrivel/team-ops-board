import { ChevronLeft, ChevronRight } from 'lucide-react';

import { Button } from '@/components/ui/button';

interface PaginationProps {
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  onPageChange: (page: number) => void;
}

export function Pagination({ page, size, totalElements, totalPages, onPageChange }: PaginationProps) {
  if (totalElements === 0) return null;
  const from = page * size + 1;
  const to = Math.min((page + 1) * size, totalElements);
  return (
    <div className="flex items-center justify-between gap-4 border-t px-6 py-3 text-sm text-muted-foreground">
      <span className="tabular-nums">
        {from}–{to} of {totalElements}
      </span>
      <div className="flex items-center gap-1">
        <Button variant="outline" size="icon" className="size-9" disabled={page === 0} onClick={() => onPageChange(page - 1)} aria-label="Previous page">
          <ChevronLeft />
        </Button>
        <Button
          variant="outline"
          size="icon"
          className="size-9"
          disabled={page + 1 >= totalPages}
          onClick={() => onPageChange(page + 1)}
          aria-label="Next page"
        >
          <ChevronRight />
        </Button>
      </div>
    </div>
  );
}
