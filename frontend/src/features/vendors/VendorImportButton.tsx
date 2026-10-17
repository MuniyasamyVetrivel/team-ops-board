import { FileUp } from 'lucide-react';
import { useState } from 'react';

import { Button } from '@/components/ui/button';
import { CsvImportDialog } from '@/features/marketing/components/CsvImportDialog';

import { useVendorImportDefinitions, VENDOR_IMPORTS } from './api';

/** "Import CSV" for one of the Vendors imports, through the shared import flow. Only rendered for VENDOR_EDIT holders. */
export function VendorImportButton({ type }: { type: string }) {
  const [open, setOpen] = useState(false);
  const definition = useVendorImportDefinitions(true).data?.find((d) => d.type === type);
  if (!definition) return null;
  return (
    <>
      <Button variant="outline" onClick={() => setOpen(true)}>
        <FileUp aria-hidden />
        Import CSV
      </Button>
      <CsvImportDialog definition={definition} endpoint={VENDOR_IMPORTS} open={open} onOpenChange={setOpen} />
    </>
  );
}
