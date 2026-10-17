import { useMemo } from 'react';
import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuthStore } from '../store';
import type { Role } from '../types';

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

  if (allowedRoles && (!user || !allowedRoles.includes(user.role))) {
    return <Navigate to="/" replace />;
  }

  return <Outlet />;
};
