import { Lock } from 'lucide-react';
import { Link } from 'react-router';

import { EmptyState } from '@/components/common/EmptyState';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';

export default function UnauthorizedPage() {
  return (
    <Card>
      <EmptyState
        icon={Lock}
        title="You don't have access to this page"
        description="Your role doesn't include this module. If you need access, ask your manager or an administrator."
        action={
          <Button asChild variant="outline">
            <Link to="/dashboard">Back to dashboard</Link>
          </Button>
        }
      />
    </Card>
  );
}
