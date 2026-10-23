import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react';
import { api, errorMessage } from '../api';
import { canWrite, isAdmin, useAuth } from '../auth-context';
import type { Project, Repo, RepoCreated } from '../types';

const API_BASE = (import.meta.env.VITE_API_BASE ?? window.location.origin).replace(/\/$/, '');

export default function ReposPage() {
  const { user } = useAuth();
  const [projects, setProjects] = useState<Project[]>([]);
  const [repos, setRepos] = useState<Repo[]>([]);
  const [projectId, setProjectId] = useState('');
  const [mode, setMode] = useState<'gitea' | 'bind'>('gitea');
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
  });

  const webhookUrl = useMemo(() => `${API_BASE}/api/webhooks/gitea`, []);

  const load = useCallback(async () => {
    try {
      const [projectList, repoList] = await Promise.all([
        api.get<Project[]>('/projects'),
        api.get<Repo[]>(projectId ? `/repos?projectId=${projectId}` : '/repos'),
      ]);
      setProjects(projectList);
      setRepos(repoList);
      setError(null);
    } catch (err) {
      setError(errorMessage(err));
    }
  }, [projectId]);

  useEffect(() => {
    void load();
  }, [load]);

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
            };
      const result = await api.post<RepoCreated>(mode === 'gitea' ? '/repos' : '/repos/bind', body);
      setCreated(result);
      setShowForm(false);
      setForm((prev) => ({ ...prev, name: '', description: '', cloneUrl: '', webhookSecret: '' }));
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
          <p>通过 Gitea 创建仓库，或手工绑定已有 Git 地址</p>
        </div>
        <div className="toolbar">
          <select value={projectId} onChange={(e) => setProjectId(e.target.value)}>
            <option value="">全部项目</option>
            {projects.map((project) => (
              <option key={project.id} value={project.id}>
                {project.projectKey} · {project.name}
              </option>
            ))}
          </select>
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
              <button
                type="button"
                className={mode === 'gitea' ? 'btn small' : 'btn ghost small'}
                onClick={() => setMode('gitea')}
              >
                Gitea 创建
              </button>
              <button
                type="button"
                className={mode === 'bind' ? 'btn small' : 'btn ghost small'}
                onClick={() => setMode('bind')}
              >
                手工绑定
              </button>
            </div>
          </div>
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
              Gitea 未配置时，创建会失败并返回明确错误，请改用手工绑定。
            </span>
          </div>
        </form>
      )}

      <div className="card">
        <div className="card-head">
          <h2>仓库列表</h2>
          <span className="muted">{repos.length} 个</span>
        </div>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>名称</th>
                <th>来源</th>
                <th>Clone URL</th>
                <th>默认分支</th>
                <th>Webhook</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {repos.map((repo) => (
                <tr key={repo.id}>
                  <td>{repo.name}</td>
                  <td>{repo.provider}</td>
                  <td className="mono">{repo.cloneUrl}</td>
                  <td>{repo.defaultBranch}</td>
                  <td>
                    <span className={`badge ${repo.webhookConfigured ? 'green' : 'gray'}`}>
                      {repo.webhookConfigured ? '已配置' : '未配置'}
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
              {repos.length === 0 && (
                <tr>
                  <td colSpan={6} className="empty">
                    暂无仓库
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    </>
  );
}
