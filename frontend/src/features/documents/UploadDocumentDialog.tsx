import { zodResolver } from '@hookform/resolvers/zod';
import { LoaderCircle } from 'lucide-react';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { toast } from 'sonner';
import { z } from 'zod';

import { FormBanner, FormField } from '@/components/common/FormField';
import { Button } from '@/components/ui/button';
import { Dialog, DialogBody, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import { isSuperAdmin } from '@/features/auth/permissions';
import { useAuth } from '@/features/auth/use-auth';
import { useDepartments } from '@/features/departments/api';
import { useProjectOptions } from '@/features/tasks/api';
import { applyServerErrors } from '@/lib/api/form-errors';
import { ACCEPTED_UPLOADS, MAX_UPLOAD_BYTES } from '@/lib/uploads';

import { useUploadDocument, type DocumentDetail } from './api';


const uploadSchema = z.object({
  file: z
    .custom<FileList>((v) => typeof FileList !== 'undefined' && v instanceof FileList && v.length === 1, 'Choose a file')
    .refine((files) => (files[0]?.size ?? 0) <= MAX_UPLOAD_BYTES, 'Files can be at most 20 MB'),
  name: z.string().max(200),
  description: z.string().max(5000),
  departmentId: z.string(),
  projectId: z.string(),
});

type UploadValues = z.infer<typeof uploadSchema>;

export function UploadDocumentDialog({ open, onOpenChange, onUploaded }: { open: boolean; onOpenChange: (open: boolean) => void; onUploaded: (document: DocumentDetail) => void }) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-lg">
        {open && (
          <UploadForm
            onCancel={() => onOpenChange(false)}
            onUploaded={(document) => {
              onOpenChange(false);
              onUploaded(document);
            }}
          />
        )}
      </DialogContent>
    </Dialog>
  );
}

function UploadForm({ onCancel, onUploaded }: { onCancel: () => void; onUploaded: (document: DocumentDetail) => void }) {
  const { user } = useAuth();
  const departments = useDepartments();
  const projects = useProjectOptions();
  const upload = useUploadDocument();
  const [banner, setBanner] = useState<string | null>(null);
  const companyWide = isSuperAdmin(user);
  const { register, handleSubmit, setError, formState: { errors, isSubmitting } } = useForm<UploadValues>({
    resolver: zodResolver(uploadSchema),
    defaultValues: { name: '', description: '', departmentId: companyWide ? '' : String(user?.department.id ?? ''), projectId: '' },
  });

  const onSubmit = handleSubmit(async (values) => {
    setBanner(null);
    try {
      const document = await upload.mutateAsync({
        file: values.file[0]!,
        name: values.name,
        description: values.description,
        departmentId: values.departmentId ? Number(values.departmentId) : null,
        projectId: values.projectId ? Number(values.projectId) : null,
      });
      toast.success(`${document.name} uploaded`);
      onUploaded(document);
    } catch (error) {
      setBanner(applyServerErrors(error, setError, ['name', 'description'] as const));
    }
  });

  return (
    <form onSubmit={onSubmit} noValidate className="flex min-h-0 flex-1 flex-col">
      <DialogHeader>
        <DialogTitle>Upload document</DialogTitle>
        <DialogDescription>Later uploads become new versions; earlier versions stay available.</DialogDescription>
      </DialogHeader>
      <DialogBody className="space-y-4">
        <FormBanner message={banner} />
        <FormField id="file" label="File" required hint="PDF, Office, images, text, CSV or ZIP, up to 20 MB" error={errors.file?.message}>
          <Input id="file" type="file" accept={ACCEPTED_UPLOADS} aria-invalid={errors.file ? true : undefined} {...register('file')} />
        </FormField>
        <FormField id="name" label="Name" hint="Defaults to the file name" error={errors.name?.message}>
          <Input id="name" {...register('name')} />
        </FormField>
        <FormField id="description" label="Description">
          <Textarea id="description" rows={2} {...register('description')} />
        </FormField>
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField id="departmentId" label="Department">
            <Select id="departmentId" {...register('departmentId')}>
              {companyWide && <option value="">Company-wide</option>}
              {departments.data
                ?.filter((d) => d.status === 'ACTIVE')
                .map((d) => (
                  <option key={d.id} value={d.id}>
                    {d.name}
                  </option>
                ))}
            </Select>
          </FormField>
          <FormField id="projectId" label="Project">
            <Select id="projectId" {...register('projectId')}>
              <option value="">No project</option>
              {projects.data?.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.code} · {p.name}
                </option>
              ))}
            </Select>
          </FormField>
        </div>
      </DialogBody>
      <DialogFooter>
        <Button type="button" variant="outline" onClick={onCancel}>
          Cancel
        </Button>
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting && <LoaderCircle className="animate-spin" aria-hidden />}
          Upload
        </Button>
      </DialogFooter>
    </form>
  );
}
