import { zodResolver } from '@hookform/resolvers/zod';
import { ArrowLeft, CircleAlert, CircleCheck, Download, FileSpreadsheet, LoaderCircle, Rows3, TriangleAlert, type LucideIcon } from 'lucide-react';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { toast } from 'sonner';
import { z } from 'zod';

import { FormBanner, FormField } from '@/components/common/FormField';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Dialog, DialogBody, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { errorMessage } from '@/lib/api/errors';
import { cn } from '@/lib/utils';

import { downloadImportTemplate, useCommitImport, usePreviewImport, type ImportDefinition, type ImportPreview, type ImportResult, type PreviewRow } from '../api';
import { formatCount } from '../marketing-format';

/** Mirrors CsvImportService.MAX_BYTES. */
export const MAX_CSV_BYTES = 2 * 1024 * 1024;

const fileSchema = z.object({
  file: z
    .custom<FileList>((v) => typeof FileList !== 'undefined' && v instanceof FileList && v.length === 1, 'Choose a CSV file')
    .refine((files) => /\.csv$/i.test(files[0]?.name ?? ''), 'Upload a .csv file. In Excel use Save As → CSV UTF-8.')
    .refine((files) => (files[0]?.size ?? 0) <= MAX_CSV_BYTES, 'CSV files can be at most 2 MB'),
});

type FileValues = z.infer<typeof fileSchema>;

interface CsvImportDialogProps {
  definition: ImportDefinition;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

/**
 * The import flow from brief section 53: choose a file → the server validates every row → review valid rows,
 * invalid rows and errors → import. Nothing is saved before the last step, invalid rows are never imported, and a
 * file with errors needs an explicit "skip invalid rows" confirmation.
 */
export function CsvImportDialog({ definition, open, onOpenChange }: CsvImportDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-4xl">{open && <ImportFlow definition={definition} onClose={() => onOpenChange(false)} />}</DialogContent>
    </Dialog>
  );
}

function ImportFlow({ definition, onClose }: { definition: ImportDefinition; onClose: () => void }) {
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<ImportPreview | null>(null);
  const [result, setResult] = useState<ImportResult | null>(null);

  if (result) return <ResultStep definition={definition} result={result} onClose={onClose} />;
  if (preview && file) {
    return (
      <PreviewStep
        definition={definition}
        file={file}
        preview={preview}
        onBack={() => setPreview(null)}
        onImported={(imported) => {
          toast.success(`Imported ${formatCount(imported.imported)} ${definition.label.toLowerCase()}`);
          setResult(imported);
        }}
      />
    );
  }
  return (
    <ChooseStep
      definition={definition}
      onCancel={onClose}
      onPreviewed={(chosen, validated) => {
        setFile(chosen);
        setPreview(validated);
      }}
    />
  );
}

function ChooseStep({ definition, onCancel, onPreviewed }: { definition: ImportDefinition; onCancel: () => void; onPreviewed: (file: File, preview: ImportPreview) => void }) {
  const previewImport = usePreviewImport(definition.type);
  const [banner, setBanner] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FileValues>({ resolver: zodResolver(fileSchema) });

  const onSubmit = handleSubmit(async ({ file }) => {
    setBanner(null);
    const chosen = file[0]!;
    try {
      onPreviewed(chosen, await previewImport.mutateAsync(chosen));
    } catch (error) {
      setBanner(errorMessage(error));
    }
  });

  return (
    <form onSubmit={onSubmit} noValidate className="flex min-h-0 flex-1 flex-col">
      <DialogHeader>
        <DialogTitle>Import {definition.label.toLowerCase()}</DialogTitle>
        <DialogDescription>{definition.description} Rows are checked first; nothing is saved until you confirm.</DialogDescription>
      </DialogHeader>
      <DialogBody className="space-y-5">
        <FormBanner message={banner} />
        <div>
          <div className="mb-2 flex items-center justify-between gap-2">
            <h3 className="text-sm font-medium">Columns</h3>
            <Button type="button" variant="outline" size="sm" onClick={() => void downloadImportTemplate(definition.type).catch((error: unknown) => setBanner(errorMessage(error)))}>
              <Download aria-hidden />
              Download template
            </Button>
          </div>
          <div className="rounded-lg border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Column</TableHead>
                  <TableHead>Description</TableHead>
                  <TableHead>Example</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {definition.columns.map((column) => (
                  <TableRow key={column.name}>
                    <TableCell className="font-mono text-xs whitespace-nowrap">
                      {column.name}
                      {column.required && (
                        <Badge tone="primary" className="ml-2 font-sans">
                          Required
                        </Badge>
                      )}
                    </TableCell>
                    <TableCell className="text-muted-foreground">{column.description}</TableCell>
                    <TableCell className="font-mono text-xs">{column.example ?? '—'}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        </div>
        <FormField id="csv-file" label="CSV file" required hint={`UTF-8 CSV with a header row, up to ${formatCount(definition.maxRows)} rows and 2 MB`} error={errors.file?.message}>
          <Input id="csv-file" type="file" accept=".csv,text/csv" aria-invalid={errors.file ? true : undefined} aria-describedby="csv-file-message" {...register('file')} />
        </FormField>
      </DialogBody>
      <DialogFooter>
        <Button type="button" variant="outline" onClick={onCancel}>
          Cancel
        </Button>
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting && <LoaderCircle className="animate-spin" aria-hidden />}
          Check file
        </Button>
      </DialogFooter>
    </form>
  );
}

function PreviewStep({ definition, file, preview, onBack, onImported }: { definition: ImportDefinition; file: File; preview: ImportPreview; onBack: () => void; onImported: (result: ImportResult) => void }) {
  const commit = useCommitImport(definition.type);
  const [skipInvalid, setSkipInvalid] = useState(false);
  const [banner, setBanner] = useState<string | null>(null);
  const hasInvalid = preview.invalidCount > 0;
  const canImport = preview.validCount > 0 && (!hasInvalid || skipInvalid) && !commit.isPending;

  async function runImport() {
    setBanner(null);
    try {
      onImported(await commit.mutateAsync({ file, checksum: preview.checksum, skipInvalid }));
    } catch (error) {
      setBanner(errorMessage(error));
    }
  }

  return (
    <>
      <DialogHeader>
        <DialogTitle>Review {preview.fileName}</DialogTitle>
        <DialogDescription>Nothing has been imported yet. Invalid rows are never imported.</DialogDescription>
      </DialogHeader>
      <DialogBody className="space-y-4">
        <FormBanner message={banner} />
        <div className="grid gap-3 sm:grid-cols-3">
          <Summary icon={Rows3} label="Rows in file" value={preview.totalRows} />
          <Summary icon={CircleCheck} label="Valid rows" value={preview.validCount} tone="text-status-success" />
          <Summary icon={CircleAlert} label="Invalid rows" value={preview.invalidCount} tone={hasInvalid ? 'text-status-danger' : undefined} />
        </div>
        {preview.unknownColumns.length > 0 && (
          <p className="flex items-start gap-2 rounded-lg border bg-muted/40 px-3 py-2 text-sm text-muted-foreground">
            <TriangleAlert className="mt-0.5 size-4 shrink-0 text-status-warning" aria-hidden />
            Ignored columns: {preview.unknownColumns.join(', ')}
          </p>
        )}
        <Tabs defaultValue={hasInvalid ? 'invalid' : 'valid'}>
          <TabsList className="px-0">
            <TabsTrigger value="invalid">Invalid rows ({formatCount(preview.invalidCount)})</TabsTrigger>
            <TabsTrigger value="valid">Valid rows ({formatCount(preview.validCount)})</TabsTrigger>
          </TabsList>
          <TabsContent value="invalid" className="pt-3">
            <RowsTable columns={preview.columns} rows={preview.invalidRows} total={preview.invalidCount} showErrors empty="Every row is valid." />
          </TabsContent>
          <TabsContent value="valid" className="pt-3">
            <RowsTable columns={preview.columns} rows={preview.validRows} total={preview.validCount} empty="No row is valid yet. Fix the errors and check the file again." />
          </TabsContent>
        </Tabs>
        {hasInvalid && preview.validCount > 0 && (
          <label className="flex items-start gap-2 rounded-lg border border-status-warning/40 bg-status-warning/5 px-3 py-2.5 text-sm">
            <Checkbox className="mt-0.5" checked={skipInvalid} onChange={(event) => setSkipInvalid(event.target.checked)} />
            <span>
              Skip the {formatCount(preview.invalidCount)} invalid {preview.invalidCount === 1 ? 'row' : 'rows'} and import only the {formatCount(preview.validCount)} valid{' '}
              {preview.validCount === 1 ? 'row' : 'rows'}.
            </span>
          </label>
        )}
      </DialogBody>
      <DialogFooter>
        <Button type="button" variant="outline" onClick={onBack} disabled={commit.isPending}>
          <ArrowLeft aria-hidden />
          Choose another file
        </Button>
        <Button type="button" disabled={!canImport} onClick={() => void runImport()}>
          {commit.isPending && <LoaderCircle className="animate-spin" aria-hidden />}
          Import {formatCount(preview.validCount)} {preview.validCount === 1 ? 'row' : 'rows'}
        </Button>
      </DialogFooter>
    </>
  );
}

function Summary({ icon: Icon, label, value, tone }: { icon: LucideIcon; label: string; value: number; tone?: string }) {
  return (
    <div className="rounded-lg border px-3 py-2.5">
      <span className="flex items-center gap-1.5 text-xs text-muted-foreground">
        <Icon className={cn('size-3.5', tone)} aria-hidden />
        {label}
      </span>
      <span className="mt-0.5 block text-xl font-semibold tabular-nums">{formatCount(value)}</span>
    </div>
  );
}

function RowsTable({ columns, rows, total, showErrors, empty }: { columns: string[]; rows: PreviewRow[]; total: number; showErrors?: boolean; empty: string }) {
  if (rows.length === 0) return <p className="py-6 text-center text-sm text-muted-foreground">{empty}</p>;
  return (
    <div className="space-y-2">
      <div className="max-h-80 overflow-y-auto rounded-lg border">
        <Table>
          <TableHeader className="sticky top-0 bg-card">
            <TableRow>
              <TableHead className="w-16">Row</TableHead>
              {showErrors && <TableHead>Problems</TableHead>}
              {columns.map((column) => (
                <TableHead key={column} className="normal-case">
                  {column}
                </TableHead>
              ))}
            </TableRow>
          </TableHeader>
          <TableBody>
            {rows.map((row) => {
              const invalidColumns = new Set(row.errors.map((error) => error.column));
              return (
                <TableRow key={row.line}>
                  <TableCell className="text-muted-foreground tabular-nums">{row.line}</TableCell>
                  {showErrors && (
                    <TableCell className="min-w-56">
                      <ul className="space-y-0.5 text-xs text-status-danger">
                        {row.errors.map((error, index) => (
                          <li key={index} className="flex gap-1">
                            <CircleAlert className="mt-0.5 size-3 shrink-0" aria-hidden />
                            <span>
                              {error.column && <span className="font-mono">{error.column}: </span>}
                              {error.message}
                            </span>
                          </li>
                        ))}
                      </ul>
                    </TableCell>
                  )}
                  {columns.map((column) => (
                    <TableCell key={column} className={cn('max-w-48 truncate', invalidColumns.has(column) && 'bg-status-danger/5 font-medium text-status-danger')}>
                      {row.values[column] || <span className="text-muted-foreground">—</span>}
                    </TableCell>
                  ))}
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </div>
      {total > rows.length && (
        <p className="text-xs text-muted-foreground">
          Showing the first {formatCount(rows.length)} of {formatCount(total)} rows.
        </p>
      )}
    </div>
  );
}

function ResultStep({ definition, result, onClose }: { definition: ImportDefinition; result: ImportResult; onClose: () => void }) {
  return (
    <>
      <DialogHeader>
        <DialogTitle>Import complete</DialogTitle>
        <DialogDescription>{definition.label}</DialogDescription>
      </DialogHeader>
      <DialogBody>
        <div className="flex flex-col items-center gap-2 py-6 text-center" role="status">
          <div className="flex size-12 items-center justify-center rounded-full bg-status-success/10 text-status-success">
            <FileSpreadsheet className="size-6" aria-hidden />
          </div>
          <p className="text-base font-semibold">
            Imported {formatCount(result.imported)} {result.imported === 1 ? 'row' : 'rows'}
          </p>
          {result.skipped > 0 && (
            <p className="text-sm text-muted-foreground">
              {formatCount(result.skipped)} invalid {result.skipped === 1 ? 'row was' : 'rows were'} skipped.
            </p>
          )}
        </div>
      </DialogBody>
      <DialogFooter>
        <Button type="button" onClick={onClose}>
          Done
        </Button>
      </DialogFooter>
    </>
  );
}
