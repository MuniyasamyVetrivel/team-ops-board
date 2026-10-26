import { hasPermission } from "@/features/auth/permissions";
import { useAuth } from "@/features/auth/use-auth";

/**
 * Celebrations, important days and events belong to Digital Marketing: Super Admins and MARKETING_VIEW holders see
 * them. The server enforces the same rule (403 from their API, nothing on the calendar).
 */
export function useCanSeeCelebrations(): boolean {
  const { user } = useAuth();
  return hasPermission(user, "MARKETING_VIEW");
}
