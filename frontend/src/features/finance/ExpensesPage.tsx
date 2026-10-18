import { Paperclip, Plus, ReceiptText } from 'lucide-react';
import { useState } from 'react';
import { useSearchParams } from 'react-router';

import { EmptyState } from '@/components/common/EmptyState';
import { Pagination } from '@/components/common/Pagination';
import { SearchInput } from '@/components/common/SearchInput';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { ExportCsvButton } from '@/features/celebrations/CsvButtons';
import { useTool } from '@/features/modules/tool-context';
import { ToolSection } from '@/features/modules/ToolSection';
import { formatLocalDate } from '@/lib/format';
import { useDebouncedValue } from '@/lib/use-debounced-value';
import { useIdParam } from '@/lib/use-id-param';
import { useInitialSearch } from '@/lib/use-initial-search';
import { cn } from '@/lib/utils';

import { EXPENSE_CATEGORIES, exportExpenses, useExpenses, type Expense, type ExpenseCategory, type ExpenseQuery } from './api';
import { ExpenseFormDialog } from './ExpenseFormDialog';
import { ExpenseSheet } from './ExpenseSheet';
import { FinanceErrorState } from './FinanceBadges';
import { CATEGORY_LABELS, formatAud, formatMoney } from './finance-meta';
import { useDepartmentChoices } from './finance-options';

const SORTS = [
  { value: 'date,desc', label: 'Newest first' },
  { value: 'date,asc', label: 'Oldest first' },
  { value: 'amount,desc', label: 'Largest (AUD)' },
  { value: 'category,asc', label: 'Category' },
  { value: 'updated,desc', label: 'Recently updated' },
];

const DATE = /^\d{4}-\d{2}-\d{2}$/;

/**
 * Every expense with its AUD amount at the stored rate. Opens one from ?expense=40 (search, vendor page); the
 * dashboard links here with ?from=&to= (a month) and ?category= or ?departmentId=.
 */
export default function ExpensesPage() {
  const { canEdit } = useTool();
  const [params] = useSearchParams();
  const [openId, setOpenId] = useIdParam('expense');
  const [adding, setAdding] = useState(false);
  const [search, setSearch] = useState(useInitialSearch());
  const [category, setCategory] = useState<ExpenseCategory | ''>(() => ((EXPENSE_CATEGORIES as string[]).includes(params.get('category') ?? '') ? (params.get('category') as ExpenseCategory) : ''));
  const [departmentId, setDepartmentId] = useState(params.get('departmentId') ?? '');
  const [from, setFrom] = useState(DATE.test(params.get('from') ?? '') ? params.get('from')! : '');
  const [to, setTo] = useState(DATE.test(params.get('to') ?? '') ? params.get('to')! : '');
  const [sort, setSort] = useState(SORTS[0]!.value);
  const [page, setPage] = useState(0);
  const debounced = useDebouncedValue(search.trim());
  const { departments } = useDepartmentChoices(null);
  const badRange = Boolean(from && to && from > to);

  const filters: Omit<ExpenseQuery, 'page' | 'size' | 'sort'> = {
    search: debounced,
    category: category || undefined,
    departmentId: /^\d+$/.test(departmentId) ? Number(departmentId) : undefined,
    from: from || undefined,
    to: badRange ? undefined : to || undefined,
  };
  const expenses = useExpenses({ ...filters, sort, page, size: 25 });
  const filtered = Boolean(debounced || category || departmentId || from || to);

  function filter<T>(setter: (value: T) => void) {
    return (value: T) => {
      setter(value);
      setPage(0);
    };
  }

  return (
    <div className="space-y-4">
      <ToolSection
        title="Expenses"
        description="What was spent, with GST and the AUD amount at the exchange rate in force on the expense date."
        actions={
          <>
            <ExportCsvButton onExport={() => exportExpenses(filters)} disabled={!expenses.data?.totalElements} />
            {canEdit && (
              <Button onClick={() => setAdding(true)}>
                <Plus aria-hidden />
                New expense
              </Button>
            )}
          </>
        }
      />
      <Card>
        <div className="flex flex-col gap-3 border-b px-6 py-4 sm:flex-row sm:flex-wrap sm:items-center sm:*:w-auto! sm:*:min-w-40 sm:[&>*:first-child]:min-w-64 sm:[&>*:first-child]:flex-1">
          <SearchInput placeholder="Search description, vendor or notes" aria-label="Search expenses" value={search} onChange={(e) => filter(setSearch)(e.target.value)} />
          <Select aria-label="Category" value={category} onChange={(e) => filter(setCategory)(e.target.value as ExpenseCategory | '')}>
            <option value="">All categories</option>
            {EXPENSE_CATEGORIES.map((c) => (
              <option key={c} value={c}>
                {CATEGORY_LABELS[c]}
              </option>
            ))}
          </Select>
          <Select aria-label="Department" value={departmentId} onChange={(e) => filter(setDepartmentId)(e.target.value)}>
            <option value="">All departments</option>
            {departments.map((d) => (
              <option key={d.id} value={d.id}>
                {d.name}
              </option>
            ))}
          </Select>
          <Input type="date" aria-label="From" value={from} onChange={(e) => filter(setFrom)(e.target.value)} />
          <Input type="date" aria-label="To" value={to} min={from || undefined} aria-invalid={badRange || undefined} onChange={(e) => filter(setTo)(e.target.value)} />
          <Select aria-label="Sort expenses" value={sort} onChange={(e) => filter(setSort)(e.target.value)}>
            {SORTS.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </Select>
        </div>
        {badRange && <p className="border-b px-6 py-2 text-sm text-status-danger">The start date must be on or before the end date.</p>}

        {expenses.isPending ? (
          <div className="space-y-3 p-4" role="status" aria-label="Loading expenses">
            {Array.from({ length: 5 }, (_, i) => (
              <Skeleton key={i} className="h-12" />
            ))}
          </div>
        ) : expenses.isError ? (
          <FinanceErrorState error={expenses.error} title="Couldn't load expenses" onRetry={() => void expenses.refetch()} />
        ) : expenses.data.content.length === 0 ? (
          <EmptyState
            icon={ReceiptText}
            title={filtered ? 'No expenses match these filters' : 'No expenses yet'}
            description={!filtered && canEdit ? 'Record what was spent and attach the receipt. Add an exchange rate first for currencies other than AUD.' : undefined}
          />
        ) : (
          <>
            <ExpensesTable expenses={expenses.data.content} onOpen={setOpenId} dimmed={expenses.isPlaceholderData} />
            <Pagination {...expenses.data} onPageChange={setPage} />
          </>
        )}
      </Card>
      <ExpenseSheet expenseId={openId} onClose={() => setOpenId(null)} />
      {canEdit && <ExpenseFormDialog open={adding} onOpenChange={setAdding} onSaved={setOpenId} />}
    </div>
  );
}

function ExpensesTable({ expenses, onOpen, dimmed }: { expenses: Expense[]; onOpen: (id: number) => void; dimmed?: boolean }) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Date</TableHead>
          <TableHead>Expense</TableHead>
          <TableHead>Category</TableHead>
          <TableHead>Department</TableHead>
          <TableHead className="text-right">Amount</TableHead>
          <TableHead className="text-right">AUD</TableHead>
          <TableHead className="text-right">Receipt</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody className={cn(dimmed && 'opacity-60')}>
        {expenses.map((e) => (
          <TableRow key={e.id}>
            <TableCell className="text-sm whitespace-nowrap">{formatLocalDate(e.expenseDate)}</TableCell>
            <TableCell className="max-w-xs">
              <button type="button" className="block max-w-full text-left" onClick={() => onOpen(e.id)}>
                <span className="block truncate font-medium hover:underline">{e.description}</span>
              </button>
              <span className="block truncate text-xs text-muted-foreground">{e.vendor?.name ?? (e.vendorId !== null ? 'Vendor in the recycle bin' : 'No vendor')}</span>
            </TableCell>
            <TableCell className="text-sm">{CATEGORY_LABELS[e.category]}</TableCell>
            <TableCell className="text-sm">{e.department?.name ?? <span className="text-muted-foreground">None</span>}</TableCell>
            <TableCell className="text-right text-sm whitespace-nowrap tabular-nums">
              {formatMoney(e.amount, e.currency)}
              {e.gst !== null && <span className="block text-xs text-muted-foreground">GST {formatMoney(e.gst, e.currency)}</span>}
            </TableCell>
            <TableCell className="text-right text-sm font-medium whitespace-nowrap tabular-nums">{formatAud(e.amountAud)}</TableCell>
            <TableCell className="text-right">
              {e.receipt ? (
                <span className="inline-flex items-center gap-1 text-sm" title={e.receipt.fileName}>
                  <Paperclip className="size-3.5 text-muted-foreground" aria-hidden />
                  Yes
                </span>
              ) : (
                <span className="text-sm text-muted-foreground">No</span>
              )}
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
