import { NavLink, Outlet } from 'react-router-dom';
import { useAuth } from '../auth-context';

const NAV_ITEMS = [
  { to: '/', label: '仪表盘', end: true },
  { to: '/projects', label: '项目' },
  { to: '/repos', label: '代码仓库' },
  { to: '/pipelines', label: '流水线' },
  { to: '/deployments', label: '发布管理' },
  { to: '/runs', label: '运行记录' },
];

export default function Layout() {
  const { user, logout } = useAuth();

  return (
    <div className="layout">
      <aside className="sidebar">
        <div className="brand">
          <span className="brand-mark">CP</span>
          <div>
            <strong>研发平台</strong>
            <small>Control Plane</small>
          </div>
        </div>
        <nav>
          {NAV_ITEMS.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) => (isActive ? 'nav-link active' : 'nav-link')}
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-footer">
          <div className="user-chip">
            <span className="avatar">{user?.displayName?.slice(0, 1) ?? '?'}</span>
            <div>
              <strong>{user?.displayName}</strong>
              <small>{user?.role}</small>
            </div>
          </div>
          <button type="button" className="btn ghost block" onClick={logout}>
            退出登录
          </button>
        </div>
      </aside>
      <main className="content">
        <Outlet />
      </main>
    </div>
  );
}
