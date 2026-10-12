import { Download, FileUp } from 'lucide-react';
import { useState } from 'react';
import { toast } from 'sonner';

import { Button } from '@/components/ui/button';
import { useImportDefinitions } from '@/features/marketing/api';
import { CsvImportDialog } from '@/features/marketing/components/CsvImportDialog';
import { errorMessage } from '@/lib/api/errors';

/**
 * "Import CSV" through the shared import flow (validate → review valid and invalid rows → import). Only rendered for
 * CELEBRATIONS_EDIT holders, who always have the marketing import endpoints.
 */
export function ImportCsvButton({ type }: { type: string }) {
  const [open, setOpen] = useState(false);
  const definition = useImportDefinitions().data?.find((d) => d.type === type);
  if (!definition) return null;
  return (
    <>
      <Button variant="outline" onClick={() => setOpen(true)}>
        <FileUp aria-hidden />
        Import CSV
      </Button>
      <CsvImportDialog definition={definition} open={open} onOpenChange={setOpen} />
    </>
  );
}

export function ExportCsvButton({ onExport, disabled }: { onExport: () => Promise<void>; disabled?: boolean }) {
  const [exporting, setExporting] = useState(false);

  async function run() {
    setExporting(true);
    try {
      await onExport();
    } catch (error) {
      toast.error(errorMessage(error));
    } finally {
      setExporting(false);
    }
  }

  return (
    <Button variant="outline" disabled={exporting || disabled} onClick={() => void run()}>
      <Download aria-hidden />
      Export CSV
    </Button>
  );
}
