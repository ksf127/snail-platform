import { useState, type FormEvent } from 'react';
import { Navigate } from 'react-router-dom';
import { errorMessage } from '../api';
import { useAuth } from '../auth-context';

export default function LoginPage() {
  const { user, login } = useAuth();
  const [username, setUsername] = useState('admin');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  if (user) {
    return <Navigate to="/" replace />;
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setLoading(true);
    try {
      await login(username.trim(), password);
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="login-page">
      <form className="login-card" onSubmit={handleSubmit}>
        <h1>研发平台</h1>
        <p className="sub">账号登录，进入控制台</p>
        {error && <div className="alert">{error}</div>}
        <label className="field">
          <span>用户名</span>
          <input value={username} autoComplete="username" onChange={(e) => setUsername(e.target.value)} />
        </label>
        <label className="field">
          <span>密码</span>
          <input
            type="password"
            value={password}
            autoComplete="current-password"
            placeholder="请输入密码"
            onChange={(e) => setPassword(e.target.value)}
          />
        </label>
        <button type="submit" className="btn" disabled={loading || !username || !password}>
          {loading ? '登录中…' : '登录'}
        </button>
        <div className="login-hint">默认账号 admin / admin123</div>
      </form>
    </div>
  );
}
