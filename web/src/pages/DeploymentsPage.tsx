import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { api, errorMessage, formatTime } from '../api';
import { canWrite, useAuth } from '../auth-context';
import StatusBadge from '../components/StatusBadge';
import type { Deployment, DeploymentExecution, Project } from '../types';

const EMPTY_FORM = {
  projectId: '',
  runId: '',
  environment: 'DEV',
  image: '',
  replicas: '1',
  note: '',
};

export default function DeploymentsPage() {
  const { user } = useAuth();
  const [projects, setProjects] = useState<Project[]>([]);
  const [deployments, setDeployments] = useState<Deployment[]>([]);
  const [projectFilter, setProjectFilter] = useState('');
  const [form, setForm] = useState({ ...EMPTY_FORM });
  const [showForm, setShowForm] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<DeploymentExecution | null>(null);

  const load = useCallback(async () => {
    try {
      const [projectList, deploymentList] = await Promise.all([
        api.get<Project[]>('/projects'),
        api.get<Deployment[]>(
          projectFilter ? `/deployments?projectId=${projectFilter}` : '/deployments',
        ),
      ]);
      setProjects(projectList);
      setDeployments(deploymentList);
      setError(null);
    } catch (err) {
      setError(errorMessage(err));
    }
  }, [projectFilter]);

  useEffect(() => {
    void load();
  }, [load]);

  async function handleCreate(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setResult(null);
    try {
      await api.post<Deployment>('/deployments', {
        projectId: Number(form.projectId),
        runId: form.runId ? Number(form.runId) : null,
        environment: form.environment,
        image: form.image.trim(),
        replicas: Number(form.replicas),
        note: form.note || null,
      });
      setShowForm(false);
      setForm({ ...EMPTY_FORM });
      await load();
    } catch (err) {
      setError(errorMessage(err));
    }
  }

  async function handleExecute(deployment: Deployment) {
    setError(null);
    setResult(null);
    try {
      const execution = await api.post<DeploymentExecution>(`/deployments/${deployment.id}/execute`);
      setResult(execution);
      await load();
    } catch (err) {
      setError(errorMessage(err));
    }
  }

  return (
    <>
      <div className="page-head">
        <div>
          <h1>发布管理</h1>
          <p>记录发布单；配置 K8s 后直接更新 Deployment 镜像</p>
        </div>
        <div className="toolbar">
          <select value={projectFilter} onChange={(e) => setProjectFilter(e.target.value)}>
            <option value="">全部项目</option>
            {projects.map((project) => (
              <option key={project.id} value={project.id}>
                {project.projectKey} · {project.name}
              </option>
            ))}
          </select>
          {canWrite(user) && (
            <button type="button" className="btn" onClick={() => setShowForm((v) => !v)}>
              {showForm ? '收起' : '新建发布单'}
            </button>
          )}
        </div>
      </div>

      {error && <div className="alert">{error}</div>}

      {result && (
        <div className="card">
          <div className="card-head">
            <h2>
              执行结果 <StatusBadge status={result.mode} />
            </h2>
            <button type="button" className="btn ghost small" onClick={() => setResult(null)}>
              关闭
            </button>
          </div>
          <p className="muted">{result.message}</p>
          {result.commandPreview && (
            <pre className="log">{result.commandPreview}</pre>
          )}
        </div>
      )}

      {showForm && (
        <form className="card" onSubmit={handleCreate}>
          <div className="card-head">
            <h2>新建发布单</h2>
            <span className="muted">K8s 未配置时执行会返回 PREVIEW 命令，不会真实变更集群</span>
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
              <span>环境</span>
              <select
                value={form.environment}
                onChange={(e) => setForm({ ...form, environment: e.target.value })}
              >
                <option value="DEV">DEV</option>
                <option value="TEST">TEST</option>
                <option value="PROD">PROD</option>
              </select>
            </label>
            <label className="field">
              <span>镜像</span>
              <input
                value={form.image}
                placeholder="registry.example.com/app:1.0.0"
                onChange={(e) => setForm({ ...form, image: e.target.value })}
                required
              />
            </label>
            <label className="field">
              <span>副本数</span>
              <input
                type="number"
                min="1"
                value={form.replicas}
                onChange={(e) => setForm({ ...form, replicas: e.target.value })}
              />
            </label>
            <label className="field">
              <span>关联 Run ID（可选）</span>
              <input
                type="number"
                min="1"
                value={form.runId}
                onChange={(e) => setForm({ ...form, runId: e.target.value })}
              />
            </label>
            <label className="field full">
              <span>备注</span>
              <input value={form.note} onChange={(e) => setForm({ ...form, note: e.target.value })} />
            </label>
          </div>
          <div className="form-actions">
            <button type="submit" className="btn">
              创建
            </button>
          </div>
        </form>
      )}

      <div className="card">
        <div className="card-head">
          <h2>发布记录</h2>
          <span className="muted">{deployments.length} 条</span>
        </div>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>ID</th>
                <th>环境</th>
                <th>镜像</th>
                <th>副本</th>
                <th>状态</th>
                <th>Run</th>
                <th>更新时间</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {deployments.map((deployment) => (
                <tr key={deployment.id}>
                  <td>{deployment.id}</td>
                  <td>{deployment.environment}</td>
                  <td className="mono">{deployment.image}</td>
                  <td>{deployment.replicas}</td>
                  <td>
                    <StatusBadge status={deployment.status} />
                  </td>
                  <td>{deployment.runId ?? '-'}</td>
                  <td className="muted">{formatTime(deployment.updatedAt)}</td>
                  <td>
                    {canWrite(user) && (
                      <button
                        type="button"
                        className="btn small"
                        onClick={() => void handleExecute(deployment)}
                      >
                        执行
                      </button>
                    )}
                  </td>
                </tr>
              ))}
              {deployments.length === 0 && (
                <tr>
                  <td colSpan={8} className="empty">
                    暂无发布记录
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
