import { Download, FileUp, Gauge, PenLine, PlugZap, Upload } from 'lucide-react';
import { useState, type ReactNode } from 'react';
import { toast } from 'sonner';

import { EmptyState } from '@/components/common/EmptyState';
import { ErrorState } from '@/components/common/ErrorState';
import { Panel } from '@/components/common/Panel';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { errorMessage } from '@/lib/api/errors';

import { downloadImportTemplate, useImportDefinitions, useMarketingIntegrations, type ImportDefinition } from '../api';
import { formatPercent } from '../marketing-format';
import { PROVIDER_CATEGORY_LABELS } from '../marketing-meta';
import { CsvImportDialog } from './CsvImportDialog';
import { RankingBadge, RankingChangeIndicator } from './RankingBadges';
import { TargetStatusBadge } from './TargetProgress';

/** How performance is rated, where data comes from, and CSV imports: the reference shown under the dashboard. */
export function MarketingReferencePanels({ threshold }: { threshold: number | null }) {
  return (
    <section aria-label="Reference" className="space-y-6">
      <div className="grid gap-6 xl:grid-cols-2">
        <RatingsPanel threshold={threshold} />
        <DataSourcesPanel />
      </div>
      <ImportsPanel />
    </section>
  );
}

/** The colour rules every marketing page uses, so the legend and the pages can never disagree. */
function RatingsPanel({ threshold }: { threshold: number | null }) {
  return (
    <Panel title="How performance is rated" icon={Gauge} description="Status is always shown with a label and an icon, never colour alone.">
      <dl className="divide-y text-sm">
        <Rule term={<RankingBadge position={7} status="TOP_10" />}>Positions 1–10</Rule>
        <Rule term={<RankingBadge position={15} status="RANKING" />}>Positions 11–100</Rule>
        <Rule term={<RankingBadge position={null} status="NOT_RANKED" />}>No position recorded</Rule>
        <Rule term={<RankingChangeIndicator change={{ value: 5, movement: 'IMPROVED' }} />}>Climbed since last month (change = previous − current)</Rule>
        <Rule term={<RankingChangeIndicator change={{ value: -3, movement: 'DECLINED' }} />}>Dropped since last month</Rule>
        <Rule term={<TargetStatusBadge status="ACHIEVED" />}>Actual has reached the monthly target</Rule>
        <Rule term={<TargetStatusBadge status="IN_PROGRESS" />}>On the way, at or above {formatPercent(threshold)} of target</Rule>
        <Rule term={<TargetStatusBadge status="BEHIND" />}>Below {formatPercent(threshold)} of target (target types can override this)</Rule>
      </dl>
    </Panel>
  );
}

function Rule({ term, children }: { term: ReactNode; children: ReactNode }) {
  return (
    <div className="flex items-center gap-4 px-5 py-2.5">
      <dt className="w-40 shrink-0">{term}</dt>
      <dd className="text-muted-foreground">{children}</dd>
    </div>
  );
}

function DataSourcesPanel() {
  const providers = useMarketingIntegrations();
  return (
    <Panel title="Data sources" icon={PlugZap} description="Where each kind of marketing data comes from.">
      {providers.isPending ? (
        <div className="space-y-2 p-5">
          {[0, 1, 2].map((i) => (
            <Skeleton key={i} className="h-10" />
          ))}
        </div>
      ) : providers.isError ? (
        <ErrorState error={providers.error} onRetry={() => void providers.refetch()} />
      ) : (
        <ul className="divide-y text-sm">
          {providers.data.map((provider) => (
            <li key={provider.category} className="flex flex-wrap items-start justify-between gap-2 px-5 py-3">
              <div className="min-w-0">
                <p className="font-medium">{PROVIDER_CATEGORY_LABELS[provider.category]}</p>
                <p className="text-xs text-muted-foreground">{provider.description}</p>
                {provider.planned.length > 0 && <p className="mt-0.5 text-xs text-muted-foreground">Ready for: {provider.planned.join(', ')}</p>}
              </div>
              <Badge tone={provider.automated && provider.connected ? 'success' : 'neutral'}>
                {provider.automated ? <PlugZap aria-hidden /> : <PenLine aria-hidden />}
                {provider.name}
              </Badge>
            </li>
          ))}
        </ul>
      )}
    </Panel>
  );
}

function ImportsPanel() {
  const definitions = useImportDefinitions();
  const [importing, setImporting] = useState<ImportDefinition | null>(null);

  return (
    <Panel title="CSV imports" icon={FileUp} description="Rows are validated and previewed first. Invalid rows are never imported.">
      {definitions.isPending ? (
        <div className="p-5">
          <Skeleton className="h-12" />
        </div>
      ) : definitions.isError ? (
        <ErrorState error={definitions.error} onRetry={() => void definitions.refetch()} />
      ) : definitions.data.length === 0 ? (
        <EmptyState
          icon={FileUp}
          title="No imports available to you yet"
          description="Keyword, ranking, campaign, lead, backlink and target imports arrive with their pages. Each needs that page's edit permission."
        />
      ) : (
        <ul className="divide-y">
          {definitions.data.map((definition) => (
            <li key={definition.type} className="flex flex-wrap items-center justify-between gap-3 px-5 py-3">
              <div className="min-w-0">
                <p className="text-sm font-medium">{definition.label}</p>
                <p className="text-xs text-muted-foreground">{definition.description}</p>
              </div>
              <div className="flex gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  aria-label={`Download ${definition.label} template`}
                  onClick={() => void downloadImportTemplate(definition.type).catch((error: unknown) => toast.error(errorMessage(error)))}
                >
                  <Download aria-hidden />
                  Template
                </Button>
                <Button size="sm" aria-label={`Import ${definition.label}`} onClick={() => setImporting(definition)}>
                  <Upload aria-hidden />
                  Import
                </Button>
              </div>
            </li>
          ))}
        </ul>
      )}
      {importing && <CsvImportDialog definition={importing} open onOpenChange={(open) => !open && setImporting(null)} />}
    </Panel>
  );
}
