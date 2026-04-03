import { useEffect, Suspense, lazy } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthLayout } from './layouts/AuthLayout';
import { MainLayout } from './layouts/MainLayout';
import { ProtectedRoute } from './components/ProtectedRoute';
import { useAuthStore } from './store/authStore';
import { authService } from './services/authService';
import { useTranslation } from 'react-i18next';
import { MaterialIcon } from './components/MaterialIcon';

const Login = lazy(() => import('./pages/Login').then((m) => ({ default: m.Login })));
const Dashboard = lazy(() => import('./pages/Dashboard').then((m) => ({ default: m.Dashboard })));
const Guests = lazy(() => import('./pages/Guests').then((m) => ({ default: m.Guests })));
const Reservations = lazy(() => import('./pages/Reservations').then((m) => ({ default: m.Reservations })));
const ReservationForm = lazy(() => import('./pages/Reservations/ReservationForm').then((m) => ({ default: m.ReservationForm })));
const CheckInForm = lazy(() => import('./pages/Stays/CheckInForm').then((m) => ({ default: m.CheckInForm })));
const Stays = lazy(() => import('./pages/Stays').then((m) => ({ default: m.Stays })));
const Billing = lazy(() => import('./pages/Billing').then((m) => ({ default: m.Billing })));
const Restaurant = lazy(() => import('./pages/Restaurant').then((m) => ({ default: m.Restaurant })));
const CalendarPlanning = lazy(() => import('./pages/CalendarPlanning').then((m) => ({ default: m.CalendarPlanning })));
const Housekeeping = lazy(() => import('./pages/Housekeeping').then((m) => ({ default: m.Housekeeping })));
const OwnerDashboard = lazy(() => import('./pages/OwnerDashboard').then((m) => ({ default: m.OwnerDashboard })));
const Rooms = lazy(() => import('./pages/Rooms').then((m) => ({ default: m.Rooms })));

function App() {
  const { t } = useTranslation('common');
  const checkAuth = useAuthStore((state) => state.checkAuth);
  const isLoading = useAuthStore((state) => state.isLoading);

  useEffect(() => {
    const initAuth = async () => {
      try {
        const user = await authService.fetchMe();
        checkAuth(user);
      } catch {
        checkAuth(null);
      }
    };
    initAuth();
  }, [checkAuth]);

  if (isLoading) {
    return (
      <div className="flex h-full items-center justify-center bg-surface">
        <div className="flex flex-col items-center gap-3">
          <MaterialIcon name="progress_activity" size={40} className="text-primary animate-spin" />
          <p className="text-on-surface-variant font-body text-sm">{t('loading_session')}</p>
        </div>
      </div>
    );
  }

  return (
    <BrowserRouter>
      <Suspense fallback={
        <div className="flex h-full items-center justify-center bg-surface">
          <div className="flex flex-col items-center gap-3">
            <MaterialIcon name="progress_activity" size={40} className="text-primary animate-spin" />
            <p className="text-on-surface-variant font-body text-sm">{t('loading')}</p>
          </div>
        </div>
      }>
        <Routes>
          <Route element={<AuthLayout />}>
            <Route path="/login" element={<Login />} />
          </Route>

          <Route element={<ProtectedRoute />}>
            <Route element={<MainLayout />}>
              <Route path="/" element={<Dashboard />} />
              <Route path="/guests" element={<Guests />} />
              <Route path="/reservations" element={<Reservations />} />
              <Route path="/reservations/new" element={<ReservationForm />} />
              <Route path="/reservations/:id" element={<ReservationForm />} />
              <Route path="/reservations/edit/:id" element={<ReservationForm />} />
              <Route path="/stays" element={<Stays />} />
              <Route path="/stays/check-in/:reservationId" element={<CheckInForm />} />
              <Route path="/billing" element={<Billing />} />
              <Route path="/restaurant" element={<Restaurant />} />
              <Route path="/calendar" element={<CalendarPlanning />} />
              <Route path="/housekeeping" element={<Housekeeping />} />
              <Route path="/rooms" element={<Rooms />} />
              <Route path="/owner-dashboard" element={<OwnerDashboard />} />
            </Route>
          </Route>

          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </Suspense>
    </BrowserRouter>
  );
}

export default App;
