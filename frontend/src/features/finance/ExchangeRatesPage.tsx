import { ArrowRightLeft, CircleCheck, Coins, LoaderCircle, Pencil, Plus, Settings2 } from 'lucide-react';
import { useState } from 'react';
import { Link } from 'react-router';
import { toast } from 'sonner';

import { EmptyState } from '@/components/common/EmptyState';
import { Pagination } from '@/components/common/Pagination';
import { Panel } from '@/components/common/Panel';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Select } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { useSettings, useUpdateSetting } from '@/features/admin/settings/api';
import { hasPermission, isSuperAdmin } from '@/features/auth/permissions';
import { useAuth } from '@/features/auth/use-auth';
import { ExportCsvButton } from '@/features/celebrations/CsvButtons';
import { useTool } from '@/features/modules/tool-context';
import { ToolSection } from '@/features/modules/ToolSection';
import { errorMessage } from '@/lib/api/errors';
import { formatLocalDate } from '@/lib/format';
import { cn } from '@/lib/utils';

import { exportRates, RATE_IMPORT, useCurrentRates, useExchangeRates, useFinanceContext, type ExchangeRate } from './api';
import { FinanceErrorState } from './FinanceBadges';
import { FinanceImportButton } from './FinanceImportButton';
import { BASE_CURRENCY, formatRate } from './finance-meta';
import { RateFormDialog } from './RateFormDialog';

/** Mirrors SettingDefinition FINANCE_DEFAULT_CURRENCY. */
export const DEFAULT_CURRENCY_SETTING = 'finance.defaultCurrency';

const SORTS = [
  { value: 'effective,desc', label: 'Newest first' },
  { value: 'currency,asc', label: 'Currency' },
  { value: 'updated,desc', label: 'Recently updated' },
];

/**
 * The manual rates table: one unit of a currency is the rate in AUD, in force from its date until that currency's next
 * rate. AUD itself is always 1 and never stored. Also shows (and, for settings managers, changes) the default currency.
 */
export default function ExchangeRatesPage() {
  const { canEdit } = useTool();
  const [currency, setCurrency] = useState('');
  const [sort, setSort] = useState(SORTS[0]!.value);
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState<ExchangeRate | 'new' | null>(null);
  const current = useCurrentRates();
  const rates = useExchangeRates({ currency: currency || undefined, sort, page, size: 50 });

  return (
    <div className="space-y-4">
      <ToolSection
        title="Exchange rates"
        description={`Rates to ${BASE_CURRENCY}. An expense keeps the rate in force on its date; licences show today's.`}
        actions={
          <>
            {canEdit && <FinanceImportButton type={RATE_IMPORT} />}
            <ExportCsvButton onExport={() => exportRates({ currency: currency || undefined })} disabled={!rates.data?.totalElements} />
            {canEdit && (
              <Button onClick={() => setEditing('new')}>
                <Plus aria-hidden />
                New rate
              </Button>
            )}
          </>
        }
      />

      <div className="grid gap-4 lg:grid-cols-[2fr_1fr]">
        <Panel title="In force today" icon={ArrowRightLeft} description={`1 unit of each currency in ${BASE_CURRENCY}`}>
          {current.isPending ? (
            <div className="flex gap-2 px-6 py-4" role="status" aria-label="Loading today's rates">
              <Skeleton className="h-8 w-28" />
              <Skeleton className="h-8 w-28" />
            </div>
          ) : current.isError ? (
            <FinanceErrorState error={current.error} title="Couldn't load today's rates" onRetry={() => void current.refetch()} />
          ) : current.data.length === 0 ? (
            <p className="px-6 py-4 text-sm text-muted-foreground">No rates yet. Expenses in other currencies need one on or before their date.</p>
          ) : (
            <ul className="flex flex-wrap gap-2 px-6 py-4" aria-label="Rates in force today">
              {current.data.map((r) => (
                <li key={r.id} className="rounded-lg border px-3 py-1.5 text-sm">
                  <span className="font-semibold">{r.currency}</span> <span className="tabular-nums">{formatRate(r.rateToAud)}</span>
                  <span className="block text-xs text-muted-foreground">since {formatLocalDate(r.effectiveFrom)}</span>
                </li>
              ))}
            </ul>
          )}
        </Panel>
        <DefaultCurrencyPanel />
      </div>

      <Card>
        <div className="flex flex-col gap-3 border-b px-6 py-4 sm:flex-row sm:items-center sm:justify-end sm:*:w-auto! sm:*:min-w-40">
          <Select aria-label="Currency" value={currency} onChange={(e) => (setCurrency(e.target.value), setPage(0))}>
            <option value="">All currencies</option>
            {(current.data ?? []).map((r) => (
              <option key={r.currency} value={r.currency}>
                {r.currency}
              </option>
            ))}
          </Select>
          <Select aria-label="Sort rates" value={sort} onChange={(e) => (setSort(e.target.value), setPage(0))}>
            {SORTS.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </Select>
        </div>
        {rates.isPending ? (
          <div className="space-y-3 p-4" role="status" aria-label="Loading exchange rates">
            {Array.from({ length: 4 }, (_, i) => (
              <Skeleton key={i} className="h-10" />
            ))}
          </div>
        ) : rates.isError ? (
          <FinanceErrorState error={rates.error} title="Couldn't load exchange rates" onRetry={() => void rates.refetch()} />
        ) : rates.data.content.length === 0 ? (
          <EmptyState
            icon={Coins}
            title={currency ? `No ${currency} rates` : 'No exchange rates yet'}
            description={canEdit && !currency ? `Add a rate for each currency you spend in other than ${BASE_CURRENCY}, or import them from a CSV.` : undefined}
          />
        ) : (
          <>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Currency</TableHead>
                  <TableHead>From</TableHead>
                  <TableHead className="text-right">1 unit in {BASE_CURRENCY}</TableHead>
                  <TableHead className="text-right">Per 1 {BASE_CURRENCY}</TableHead>
                  <TableHead>Source</TableHead>
                  <TableHead>
                    <span className="sr-only">Actions</span>
                  </TableHead>
                </TableRow>
              </TableHeader>
              <TableBody className={cn(rates.isPlaceholderData && 'opacity-60')}>
                {rates.data.content.map((r) => (
                  <TableRow key={r.id}>
                    <TableCell className="font-medium">
                      {r.currency}
                      {r.inForce && (
                        <Badge tone="success" className="ml-2">
                          <CircleCheck aria-hidden />
                          In force
                        </Badge>
                      )}
                    </TableCell>
                    <TableCell className="text-sm whitespace-nowrap">{formatLocalDate(r.effectiveFrom)}</TableCell>
                    <TableCell className="text-right text-sm tabular-nums">{formatRate(r.rateToAud)}</TableCell>
                    <TableCell className="text-right text-sm text-muted-foreground tabular-nums">{formatRate(r.unitsPerAud)}</TableCell>
                    <TableCell className="max-w-48 truncate text-sm text-muted-foreground">{r.source ?? '—'}</TableCell>
                    <TableCell className="text-right">
                      <Button variant="ghost" size="sm" onClick={() => setEditing(r)} aria-label={`${canEdit ? 'Edit' : 'View'} the ${r.currency} rate from ${formatLocalDate(r.effectiveFrom)}`}>
                        <Pencil aria-hidden />
                        {canEdit ? 'Edit' : 'View'}
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
            <Pagination {...rates.data} onPageChange={setPage} />
          </>
        )}
      </Card>
      <RateFormDialog rate={editing === 'new' ? undefined : (editing ?? undefined)} open={editing !== null} onOpenChange={(open) => !open && setEditing(null)} readOnly={!canEdit} />
    </div>
  );
}

/**
 * The currency new licences and expenses start in (the finance.defaultCurrency setting). Settings managers change it
 * here or in Admin settings → Finance; everyone else sees it.
 */
function DefaultCurrencyPanel() {
  const { user } = useAuth();
  const canManage = isSuperAdmin(user) || hasPermission(user, 'SETTINGS_MANAGE');
  const context = useFinanceContext();
  return (
    <Panel title="Default currency" icon={Settings2} description="New licences and expenses start in it">
      <div className="px-6 py-4">
        {context.isPending ? (
          <Skeleton className="h-9 w-32" role="status" aria-label="Loading the default currency" />
        ) : context.isError ? (
          <p className="text-sm text-status-danger">{errorMessage(context.error)}</p>
        ) : canManage ? (
          <DefaultCurrencyEditor current={context.data.defaultCurrency} />
        ) : (
          <>
            <p className="text-2xl font-semibold">{context.data.defaultCurrency}</p>
            <p className="mt-1 text-xs text-muted-foreground">Each record can still choose its own. Your Super Admin sets it in Admin settings → Finance.</p>
          </>
        )}
      </div>
    </Panel>
  );
}

function DefaultCurrencyEditor({ current }: { current: string }) {
  const settings = useSettings();
  const update = useUpdateSetting();
  const setting = settings.data?.find((s) => s.key === DEFAULT_CURRENCY_SETTING);
  const [value, setValue] = useState<string | null>(null);
  const chosen = value ?? setting?.value ?? current;

  async function save() {
    if (!setting) return;
    try {
      await update.mutateAsync({ key: setting.key, version: setting.version, value: chosen });
      toast.success(`New licences and expenses now start in ${chosen}`);
      setValue(null);
    } catch (error) {
      toast.error(errorMessage(error));
    }
  }

  if (settings.isPending) return <Skeleton className="h-9 w-48" role="status" aria-label="Loading the setting" />;
  if (!setting) return <p className="text-2xl font-semibold">{current}</p>;
  return (
    <div className="space-y-2">
      <div className="flex items-center gap-2">
        <Select aria-label="Default currency" className="w-32" value={chosen} onChange={(e) => setValue(e.target.value)}>
          {setting.options.map((o) => (
            <option key={o} value={o}>
              {o}
            </option>
          ))}
        </Select>
        <Button variant="outline" disabled={chosen === setting.value || update.isPending} onClick={() => void save()}>
          {update.isPending && <LoaderCircle className="animate-spin" aria-hidden />}
          Save
        </Button>
      </div>
      <p className="text-xs text-muted-foreground">
        Each record can still choose its own. Also in{' '}
        <Link to="/admin/settings" className="underline">
          Admin settings
        </Link>
        .
      </p>
    </div>
  );
}
