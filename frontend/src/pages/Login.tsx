import React, { useState, memo, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuthStore } from '../store';
import { authService } from '../services';
import { useTranslation } from 'react-i18next';
import { M3TextField } from '../components/m3';
import { M3Button } from '../components/m3';
import { Alert } from '../components/Alert';

export const Login = memo(() => {
  const { t } = useTranslation('auth');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');

  const navigate = useNavigate();
  const login = useAuthStore((state) => state.login);

  const handleSubmit = useCallback(async (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);
    setError('');

    try {
      const loginResult = await authService.login({ username, password });
      const user = await authService.fetchMe();
      login(user);
      if (loginResult.mustChangePassword) {
        navigate('/settings/password', { state: { mustChangePassword: true } });
      } else {
        navigate('/');
      }
    } catch (err: unknown) {
      const e = err as {response?: {status?: number}, message?: string};
      if (e.response?.status === 401) {
        setError(t('invalid_credentials'));
      } else {
        setError(t('login_failed'));
      }
    } finally {
      setIsLoading(false);
    }
  }, [username, password, login, navigate, t]);

  const handleUsernameChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    setUsername(e.target.value);
  }, []);

  const handlePasswordChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    setPassword(e.target.value);
  }, []);

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-display font-bold text-on-surface">{t('login_title')}</h1>
        <p className="mt-1 text-sm font-body text-on-surface-variant">{t('login_subtitle')}</p>
      </div>

      <form data-testid="login-form" className="space-y-5" onSubmit={handleSubmit}>
        {error && (
          <Alert tone="error">{error}</Alert>
        )}

        <M3TextField
          label={t('username')}
          name="username"
          type="text"
          required
          value={username}
          onChange={handleUsernameChange}
          leadingIcon="person"
        />

        <M3TextField
          label={t('password')}
          name="password"
          type="password"
          required
          value={password}
          onChange={handlePasswordChange}
          leadingIcon="lock"
        />

        <M3Button
          data-testid="login-submit"
          type="submit"
          disabled={isLoading}
          loading={isLoading}
          className="w-full"
        >
          {isLoading ? t('signing_in') : t('sign_in')}
        </M3Button>
      </form>
    </div>
  );
});
