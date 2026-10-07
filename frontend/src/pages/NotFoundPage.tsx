import { TriangleAlert } from 'lucide-react';
import { Link } from 'react-router';

import { EmptyState } from '@/components/common/EmptyState';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';

export default function NotFoundPage() {
  return (
    <Card>
      <EmptyState
        icon={TriangleAlert}
        title="Page not found"
        description="The page you're looking for doesn't exist or has moved."
        action={
          <Button asChild variant="outline">
            <Link to="/dashboard">Back to dashboard</Link>
          </Button>
        }
      />
    </Card>
  );
}
