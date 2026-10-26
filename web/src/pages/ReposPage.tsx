import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react';
import { api, errorMessage, queryString } from '../api';
import { canWrite, isAdmin, useAuth } from '../auth-context';
import Pagination from '../components/Pagination';
import type { PageView, Project, Repo, RepoCreated } from '../types';

const API_BASE = (import.meta.env.VITE_API_BASE ?? window.location.origin).replace(/\/$/, '');

export default function ReposPage() {
  const { user } = useAuth();
  const [projects, setProjects] = useState<Project[]>([]);
  const [repos, setRepos] = useState<PageView<Repo> | null>(null);
  const [projectFilter, setProjectFilter] = useState('');
  const [providerFilter, setProviderFilter] = useState('');
  const [keyword, setKeyword] = useState('');
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(10);
  const [mode, setMode] = useState<'gitea' | 'bind'>('gitea');
  const [giteaEnabled, setGiteaEnabled] = useState<boolean | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [created, setCreated] = useState<RepoCreated | null>(null);

  const [form, setForm] = useState({
    projectId: '',
    name: '',
    description: '',
    cloneUrl: '',
    defaultBranch: 'main',
    privateRepo: true,
    webhookSecret: '',
    username: '',
    token: '',
  });

  const webhookUrl = useMemo(() => `${API_BASE}/api/webhooks/gitea`, []);

  const load = useCallback(async () => {
    try {
      const [projectPage, repoPage, capabilities] = await Promise.all([
        api.get<PageView<Project>>(`/projects${queryString({ page: 1, size: 100 })}`),
        api.get<PageView<Repo>>(
          `/repos${queryString({
            page,
            size,
            projectId: projectFilter,
            provider: providerFilter,
            keyword,
          })}`,
        ),
        api.get<{ giteaConfigured: boolean }>('/repos/capabilities'),
      ]);
      setProjects(projectPage.items);
      setRepos(repoPage);
      setGiteaEnabled(capabilities.giteaConfigured);
      setError(null);
    } catch (err) {
      setError(errorMessage(err));
    }
  }, [page, size, projectFilter, providerFilter, keyword]);

  useEffect(() => {
    void load();
  }, [load]);

  // Gitea 未配置时无法自动建仓，强制切换到手工绑定。
  useEffect(() => {
    if (giteaEnabled === false && mode === 'gitea') {
      setMode('bind');
    }
  }, [giteaEnabled, mode]);

  function applyFilters(event: FormEvent) {
    event.preventDefault();
    setPage(1);
    void load();
  }

  function resetFilters() {
    setProjectFilter('');
    setProviderFilter('');
    setKeyword('');
    setPage(1);
  }

  async function handleCreate(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setCreated(null);
    try {
      const body =
        mode === 'gitea'
          ? {
              projectId: Number(form.projectId),
              name: form.name.trim(),
              description: form.description,
              privateRepo: form.privateRepo,
              defaultBranch: form.defaultBranch,
              webhookSecret: form.webhookSecret || null,
            }
          : {
              projectId: Number(form.projectId),
              name: form.name.trim(),
              cloneUrl: form.cloneUrl.trim(),
              defaultBranch: form.defaultBranch,
              webhookSecret: form.webhookSecret || null,
              username: form.username.trim() || null,
              token: form.token.trim() || null,
            };
      const result = await api.post<RepoCreated>(mode === 'gitea' ? '/repos' : '/repos/bind', body);
      setCreated(result);
      setShowForm(false);
      setForm((prev) => ({
        ...prev, name: '', description: '', cloneUrl: '', webhookSecret: '', username: '', token: '',
      }));
      setPage(1);
      await load();
    } catch (err) {
      setError(errorMessage(err));
    }
  }

  async function handleDelete(repo: Repo) {
    if (!window.confirm(`确认删除仓库 ${repo.name}？`)) {
      return;
    }
    try {
      await api.delete<void>(`/repos/${repo.id}`);
      await load();
    } catch (err) {
      setError(errorMessage(err));
    }
  }

  return (
    <>
      <div className="page-head">
        <div>
          <h1>代码仓库</h1>
          <p>仓库必须归属项目：先在「项目」页建项目，再在这里绑定或创建仓库</p>
        </div>
        <div className="toolbar">
          {canWrite(user) && (
            <button type="button" className="btn" onClick={() => setShowForm((v) => !v)}>
              {showForm ? '收起' : '添加仓库'}
            </button>
          )}
        </div>
      </div>

      {error && <div className="alert">{error}</div>}

      {created && (
        <div className="alert info">
          仓库 {created.repo.name} 已登记。Webhook Secret：<code>{created.webhookSecret}</code>
          ，Webhook 地址：<code>{webhookUrl}</code>（仅本次展示，请立即保存）
        </div>
      )}

      {showForm && (
        <form className="card" onSubmit={handleCreate}>
          <div className="card-head">
            <h2>添加仓库</h2>
            <div className="toolbar">
              {giteaEnabled !== false && (
                <button
                  type="button"
                  className={mode === 'gitea' ? 'btn small' : 'btn ghost small'}
                  onClick={() => setMode('gitea')}
                >
                  Gitea 创建
                </button>
              )}
              <button
                type="button"
                className={mode === 'bind' ? 'btn small' : 'btn ghost small'}
                onClick={() => setMode('bind')}
              >
                手工绑定
              </button>
            </div>
          </div>
          {giteaEnabled === false && (
            <div className="alert info" style={{ marginBottom: 12 }}>
              当前环境未配置 Gitea，无法自动创建仓库。GitHub 上已有的仓库请用「手工绑定」，
              粘贴 Clone URL 即可登记。
            </div>
          )}
          <div className="form-grid">
            <label className="field">
              <span>所属项目</span>
              <select
                value={form.projectId}
                onChange={(e) => setForm({ ...form, projectId: e.target.value })}
                required
              >
                <option value="">请选择</option>
                {projects.map((project) => (
                  <option key={project.id} value={project.id}>
                    {project.projectKey} · {project.name}
                  </option>
                ))}
              </select>
              <span className="muted field-hint">
                仓库必须归属某个项目；还没有项目请先到「项目」页创建。
              </span>
            </label>
            <label className="field">
              <span>仓库名</span>
              <input
                value={form.name}
                onChange={(e) => setForm({ ...form, name: e.target.value })}
                required
              />
            </label>
            <label className="field">
              <span>默认分支</span>
              <input
                value={form.defaultBranch}
                onChange={(e) => setForm({ ...form, defaultBranch: e.target.value })}
              />
            </label>
            {mode === 'gitea' ? (
              <label className="field">
                <span>描述</span>
                <input
                  value={form.description}
                  onChange={(e) => setForm({ ...form, description: e.target.value })}
                />
              </label>
            ) : (
              <label className="field full">
                <span>Clone URL</span>
                <input
                  value={form.cloneUrl}
                  placeholder="https://github.com/org/repo.git"
                  onChange={(e) => setForm({ ...form, cloneUrl: e.target.value })}
                  required
                />
              </label>
            )}
            {mode === 'bind' && (
              <>
                <label className="field">
                  <span>访问用户名（私有仓库）</span>
                  <input
                    value={form.username}
                    placeholder="github 用户或 git"
                    onChange={(e) => setForm({ ...form, username: e.target.value })}
                  />
                </label>
                <label className="field">
                  <span>访问令牌（私有仓库）</span>
                  <input
                    type="password"
                    value={form.token}
                    placeholder="PAT / Gitea Token，加密存储"
                    onChange={(e) => setForm({ ...form, token: e.target.value })}
                  />
                </label>
              </>
            )}
            <label className="field">
              <span>Webhook Secret（留空自动生成）</span>
              <input
                value={form.webhookSecret}
                onChange={(e) => setForm({ ...form, webhookSecret: e.target.value })}
              />
            </label>
            {mode === 'gitea' && (
              <label className="field checkbox">
                <input
                  type="checkbox"
                  checked={form.privateRepo}
                  onChange={(e) => setForm({ ...form, privateRepo: e.target.checked })}
                />
                <span>私有仓库</span>
              </label>
            )}
          </div>
          <div className="form-actions">
            <button type="submit" className="btn">
              提交
            </button>
            <span className="muted">
              {mode === 'bind'
                ? '粘贴 GitHub / GitLab 等已有仓库的 Clone URL，无需在 Gitea 中创建。'
                : '将在 Gitea 中新建仓库并返回 Webhook Secret。'}
            </span>
          </div>
        </form>
      )}

      <div className="card">
        <div className="card-head">
          <h2>仓库列表</h2>
          <span className="muted">{repos?.total ?? 0} 个</span>
        </div>
        <form className="filters" onSubmit={applyFilters}>
          <input
            value={keyword}
            placeholder="搜索仓库名 / Clone URL"
            onChange={(e) => setKeyword(e.target.value)}
          />
          <select value={projectFilter} onChange={(e) => setProjectFilter(e.target.value)}>
            <option value="">全部项目</option>
            {projects.map((project) => (
              <option key={project.id} value={project.id}>
                {project.projectKey} · {project.name}
              </option>
            ))}
          </select>
          <select value={providerFilter} onChange={(e) => setProviderFilter(e.target.value)}>
            <option value="">全部来源</option>
            <option value="GENERIC">GENERIC</option>
            <option value="GITEA">GITEA</option>
          </select>
          <button type="submit" className="btn small">
            查询
          </button>
          <button type="button" className="btn ghost small" onClick={resetFilters}>
            重置
          </button>
        </form>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>名称</th>
                <th>所属项目</th>
                <th>来源</th>
                <th>Clone URL</th>
                <th>默认分支</th>
                <th>Webhook</th>
                <th>访问凭据</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {(repos?.items ?? []).map((repo) => (
                <tr key={repo.id}>
                  <td>{repo.name}</td>
                  <td className="muted">
                    {projects.find((project) => project.id === repo.projectId)?.projectKey ?? '-'}
                  </td>
                  <td>{repo.provider}</td>
                  <td className="mono">{repo.cloneUrl}</td>
                  <td>{repo.defaultBranch}</td>
                  <td>
                    <span className={`badge ${repo.webhookConfigured ? 'green' : 'gray'}`}>
                      {repo.webhookConfigured ? '已配置' : '未配置'}
                    </span>
                  </td>
                  <td>
                    <span className={`badge ${repo.credentialConfigured ? 'green' : 'gray'}`}>
                      {repo.credentialConfigured ? '已配置' : '未配置'}
                    </span>
                  </td>
                  <td>
                    {isAdmin(user) && (
                      <button
                        type="button"
                        className="btn danger small"
                        onClick={() => void handleDelete(repo)}
                      >
                        删除
                      </button>
                    )}
                  </td>
                </tr>
              ))}
              {(repos?.items.length ?? 0) === 0 && (
                <tr>
                  <td colSpan={8} className="empty">
                    暂无仓库
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
        <Pagination
          page={repos}
          onPageChange={setPage}
          onSizeChange={(next) => {
            setSize(next);
            setPage(1);
          }}
        />
      </div>
    </>
  );
}
