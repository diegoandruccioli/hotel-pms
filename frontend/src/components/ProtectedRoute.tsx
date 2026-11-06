import { useMemo } from 'react';
import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuthStore } from '../store';
import type { Role } from '../types';

/** The only page a user who must change the password may use (the gateway refuses everything else). */
export const CHANGE_PASSWORD_PATH = '/settings/password';

interface ProtectedRouteProps {
  allowedRoles?: readonly Role[];
}

export const ProtectedRoute = ({ allowedRoles }: ProtectedRouteProps = {}) => {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const user = useAuthStore((state) => state.user);
  const location = useLocation();
  // Where the visitor was heading, so the login can send them back there.
  const loginState = useMemo(() => ({ from: `${location.pathname}${location.search}` }), [location.pathname, location.search]);

  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={loginState} />;
  }

  // Forced rotation comes before any role check: the gateway answers every other call with a 403.
  if (user?.mustChangePassword === true && location.pathname !== CHANGE_PASSWORD_PATH) {
    return <Navigate to={CHANGE_PASSWORD_PATH} replace state={loginState} />;
  }

  if (allowedRoles && (!user || !allowedRoles.includes(user.role))) {
    return <Navigate to="/" replace />;
  }

  return <Outlet />;
};
