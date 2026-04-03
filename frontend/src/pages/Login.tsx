import React, { useState, memo, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuthStore } from '../store/authStore';
import { authService } from '../services/authService';
import { useTranslation } from 'react-i18next';
import { M3TextField } from '../components/m3/M3TextField';
import { M3Button } from '../components/m3/M3Button';

const ICON_STYLE = { fontSize: 20 };

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
      await authService.login({ username, password });
      const user = await authService.fetchMe();
      login(user);
      navigate('/');
    } catch (err: unknown) {
      const e = err as {response?: {status?: number}, message?: string};
      if (e.response?.status === 401) {
        setError(t('invalid_credentials'));
      } else {
        setError(t('login_error_generic'));
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
    <form data-testid="login-form" className="space-y-5" onSubmit={handleSubmit}>
      {error && (
        <div className="flex items-center gap-3 px-4 py-3 rounded-shape-sm bg-error-container text-on-error-container">
          <span className="material-symbols-outlined" style={ICON_STYLE} aria-hidden="true">error</span>
          <p className="text-sm font-body">{error}</p>
        </div>
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
  );
});
