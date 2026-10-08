import { FileText, Upload } from 'lucide-react';
import { useState } from 'react';

import { EmptyState } from '@/components/common/EmptyState';
import { ErrorState } from '@/components/common/ErrorState';
import { PageHeader } from '@/components/common/PageHeader';
import { Pagination } from '@/components/common/Pagination';
import { SearchInput } from '@/components/common/SearchInput';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Select } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { hasPermission } from '@/features/auth/permissions';
import { useAuth } from '@/features/auth/use-auth';
import { useDepartments } from '@/features/departments/api';
import { formatBytes } from '@/features/tasks/task-meta';
import { formatRelative } from '@/lib/format';
import { useIdParam } from '@/lib/use-id-param';
import { useDebouncedValue } from '@/lib/use-debounced-value';
import { cn } from '@/lib/utils';

import { useDocuments } from './api';
import { DocumentDrawer } from './DocumentDrawer';
import { UploadDocumentDialog } from './UploadDocumentDialog';

/** Versioned documents: company-wide, your departments' and your own uploads. */
export default function DocumentsPage() {
  const { user } = useAuth();
  const departments = useDepartments();
  const [search, setSearch] = useState('');
  const [departmentId, setDepartmentId] = useState('');
  const [page, setPage] = useState(0);
  const [uploading, setUploading] = useState(false);
  const [documentId, setDocumentId] = useIdParam('document');
  const debounced = useDebouncedValue(search.trim());
  const documents = useDocuments({ search: debounced, departmentId: departmentId ? Number(departmentId) : undefined, page, size: 25 });

  return (
    <div className="space-y-6">
      <PageHeader
        title="Documents"
        description="Policies, templates and project files, with every version kept."
        actions={
          hasPermission(user, 'DOCUMENT_EDIT') && (
            <Button onClick={() => setUploading(true)}>
              <Upload aria-hidden />
              Upload
            </Button>
          )
        }
      />
      <Card>
        <div className="grid gap-3 border-b p-4 sm:grid-cols-[1fr_minmax(0,14rem)]">
          <SearchInput placeholder="Search name or description" aria-label="Search documents" value={search} onChange={(e) => { setSearch(e.target.value); setPage(0); }} />
          <Select aria-label="Department" value={departmentId} onChange={(e) => { setDepartmentId(e.target.value); setPage(0); }}>
            <option value="">All departments</option>
            {departments.data?.map((d) => (
              <option key={d.id} value={d.id}>
                {d.name}
              </option>
            ))}
          </Select>
        </div>
        {documents.isPending ? (
          <div className="space-y-3 p-4" role="status" aria-label="Loading documents">
            {Array.from({ length: 5 }, (_, i) => (
              <Skeleton key={i} className="h-12" />
            ))}
          </div>
        ) : documents.isError ? (
          <ErrorState error={documents.error} title="Couldn't load documents" onRetry={() => void documents.refetch()} />
        ) : documents.data.content.length === 0 ? (
          <EmptyState icon={FileText} title={search || departmentId ? 'No documents match these filters' : 'No documents yet'} />
        ) : (
          <>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Document</TableHead>
                  <TableHead>Department</TableHead>
                  <TableHead>Current version</TableHead>
                  <TableHead>Updated</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody className={cn(documents.isPlaceholderData && 'opacity-60')}>
                {documents.data.content.map((d) => (
                  <TableRow
                    key={d.id}
                    data-clickable="true"
                    tabIndex={0}
                    aria-label={`Open ${d.name}`}
                    onClick={() => setDocumentId(d.id)}
                    onKeyDown={(event) => {
                      if (event.key === 'Enter') setDocumentId(d.id);
                    }}
                  >
                    <TableCell className="max-w-md">
                      <p className="flex items-center gap-2 font-medium">
                        <FileText className="size-4 shrink-0 text-muted-foreground" aria-hidden />
                        <span className="truncate">{d.name}</span>
                      </p>
                      {d.description && <p className="truncate text-xs text-muted-foreground">{d.description}</p>}
                    </TableCell>
                    <TableCell className="text-sm">{d.department?.name ?? 'Company-wide'}</TableCell>
                    <TableCell className="text-sm">
                      {d.current ? (
                        <>
                          v{d.current.versionNo} <span className="text-muted-foreground">· {formatBytes(d.current.sizeBytes)}</span>
                          <span className="block truncate text-xs text-muted-foreground">{d.current.fileName}</span>
                        </>
                      ) : (
                        '—'
                      )}
                    </TableCell>
                    <TableCell className="text-sm whitespace-nowrap text-muted-foreground">
                      {formatRelative(d.updatedAt)}
                      {d.current?.uploadedBy && <span className="block text-xs">by {d.current.uploadedBy.fullName}</span>}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
            <Pagination {...documents.data} onPageChange={setPage} />
          </>
        )}
      </Card>
      <UploadDocumentDialog open={uploading} onOpenChange={setUploading} onUploaded={(d) => setDocumentId(d.id)} />
      <DocumentDrawer documentId={documentId} onClose={() => setDocumentId(null)} />
    </div>
  );
}
