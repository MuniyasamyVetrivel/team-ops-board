import { FileUp } from 'lucide-react';
import { useState } from 'react';

import { Button } from '@/components/ui/button';
import { CsvImportDialog } from '@/features/marketing/components/CsvImportDialog';

import { FINANCE_IMPORTS, useFinanceImportDefinitions } from './api';

/** "Import CSV" for one of the Finance imports, through the shared import flow. Only rendered for FINANCE_EDIT holders. */
export function FinanceImportButton({ type }: { type: string }) {
  const [open, setOpen] = useState(false);
  const definition = useFinanceImportDefinitions(true).data?.find((d) => d.type === type);
  if (!definition) return null;
  return (
    <>
      <Button variant="outline" onClick={() => setOpen(true)}>
        <FileUp aria-hidden />
        Import CSV
      </Button>
      <CsvImportDialog definition={definition} endpoint={FINANCE_IMPORTS} open={open} onOpenChange={setOpen} />
    </>
  );
}
