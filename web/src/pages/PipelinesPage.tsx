import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { api, errorMessage, formatTime } from '../api';
import { canWrite, isAdmin, useAuth } from '../auth-context';
import type { Pipeline, PipelineStep, Project, Repo, Run } from '../types';

const DEFAULT_DEFINITION = {
  steps: [
    {
      name: 'build',
      image: 'alpine:3.20',
      commands: ['echo "hello from pipeline"'],
      workDir: '/workspace',
      artifacts: [],
    },
  ],
};

const EMPTY_FORM = {
  projectId: '',
  repoId: '',
  name: '',
  branch: 'main',
  triggerType: 'MANUAL',
  executorType: 'DOCKER',
  definitionJson: JSON.stringify(DEFAULT_DEFINITION, null, 2),
  enabled: true,
};

export default function PipelinesPage() {
  const { user } = useAuth();
  const [projects, setProjects] = useState<Project[]>([]);
  const [repos, setRepos] = useState<Repo[]>([]);
  const [pipelines, setPipelines] = useState<Pipeline[]>([]);
  const [projectFilter, setProjectFilter] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [form, setForm] = useState({ ...EMPTY_FORM });
  const [triggering, setTriggering] = useState<Pipeline | null>(null);
  const [triggerBranch, setTriggerBranch] = useState('');
  const [triggerSha, setTriggerSha] = useState('');

  const load = useCallback(async () => {
    try {
      const [projectList, repoList, pipelineList] = await Promise.all([
        api.get<Project[]>('/projects'),
        api.get<Repo[]>('/repos'),
        api.get<Pipeline[]>(projectFilter ? `/pipelines?projectId=${projectFilter}` : '/pipelines'),
      ]);
      setProjects(projectList);
      setRepos(repoList);
      setPipelines(pipelineList);
      setError(null);
    } catch (err) {
      setError(errorMessage(err));
    }
  }, [projectFilter]);

  useEffect(() => {
    void load();
  }, [load]);

  const formRepos = useMemo(
    () => repos.filter((repo) => !form.projectId || repo.projectId === Number(form.projectId)),
    [repos, form.projectId],
  );

  const parsedSteps = useMemo<PipelineStep[] | null>(() => {
    try {
      const parsed = JSON.parse(form.definitionJson) as { steps?: PipelineStep[] };
      return Array.isArray(parsed.steps) ? parsed.steps : null;
    } catch {
      return null;
    }
  }, [form.definitionJson]);

  function startCreate() {
    setEditingId(null);
    setForm({ ...EMPTY_FORM });
    setShowForm(true);
  }

  function startEdit(pipeline: Pipeline) {
    setEditingId(pipeline.id);
    setForm({
      projectId: String(pipeline.projectId),
      repoId: pipeline.repoId ? String(pipeline.repoId) : '',
      name: pipeline.name,
      branch: pipeline.branch,
      triggerType: pipeline.triggerType,
      executorType: pipeline.executorType,
      definitionJson: pipeline.definitionJson,
      enabled: pipeline.enabled,
    });
    setShowForm(true);
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setNotice(null);
    let parsed: { steps?: PipelineStep[] };
    try {
      parsed = JSON.parse(form.definitionJson) as { steps?: PipelineStep[] };
    } catch {
      setError('流水线定义不是合法 JSON');
      return;
    }
    if (!parsed.steps || parsed.steps.length === 0) {
      setError('流水线至少需要一个 step');
      return;
    }
    try {
      const body = {
        projectId: Number(form.projectId),
        repoId: form.repoId ? Number(form.repoId) : null,
        name: form.name.trim(),
        branch: form.branch.trim(),
        triggerType: form.triggerType,
        executorType: form.executorType,
        definitionJson: JSON.stringify(parsed),
        enabled: form.enabled,
      };
      if (editingId) {
        await api.put<Pipeline>(`/pipelines/${editingId}`, body);
        setNotice('流水线已更新');
      } else {
        await api.post<Pipeline>('/pipelines', body);
        setNotice('流水线已创建');
      }
      setShowForm(false);
      await load();
    } catch (err) {
      setError(errorMessage(err));
    }
  }

  async function handleDelete(pipeline: Pipeline) {
    if (!window.confirm(`确认删除流水线 ${pipeline.name}？`)) {
      return;
    }
    try {
      await api.delete<void>(`/pipelines/${pipeline.id}`);
      await load();
    } catch (err) {
      setError(errorMessage(err));
    }
  }

  function openTrigger(pipeline: Pipeline) {
    setTriggering(pipeline);
    setTriggerBranch(pipeline.branch);
    setTriggerSha('');
    setError(null);
    setNotice(null);
  }

  async function handleTrigger(event: FormEvent) {
    event.preventDefault();
    if (!triggering) {
      return;
    }
    try {
      const run = await api.post<Run>(`/pipelines/${triggering.id}/trigger`, {
        branch: triggerBranch.trim(),
        commitSha: triggerSha.trim() || null,
      });
      setTriggering(null);
      setNotice(`已触发构建 #${run.buildNo}（runId=${run.id}），可前往运行记录查看`);
    } catch (err) {
      setError(errorMessage(err));
    }
  }

  function formatJson() {
    try {
      setForm({ ...form, definitionJson: JSON.stringify(JSON.parse(form.definitionJson), null, 2) });
      setError(null);
    } catch {
      setError('流水线定义不是合法 JSON，无法格式化');
    }
  }

  return (
    <>
      <div className="page-head">
        <div>
          <h1>流水线</h1>
          <p>定义构建步骤，手工触发或由 Gitea push 事件触发</p>
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
            <button type="button" className="btn" onClick={startCreate}>
              新建流水线
            </button>
          )}
        </div>
      </div>

      {error && <div className="alert">{error}</div>}
      {notice && <div className="alert info">{notice}</div>}

      {showForm && (
        <form className="card" onSubmit={handleSubmit}>
          <div className="card-head">
            <h2>{editingId ? '编辑流水线' : '新建流水线'}</h2>
            <button type="button" className="btn ghost small" onClick={() => setShowForm(false)}>
              取消
            </button>
          </div>
          <div className="form-grid">
            <label className="field">
              <span>所属项目</span>
              <select
                value={form.projectId}
                onChange={(e) => setForm({ ...form, projectId: e.target.value, repoId: '' })}
                required
                disabled={editingId !== null}
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
              <span>关联仓库</span>
              <select value={form.repoId} onChange={(e) => setForm({ ...form, repoId: e.target.value })}>
                <option value="">不绑定</option>
                {formRepos.map((repo) => (
                  <option key={repo.id} value={repo.id}>
                    {repo.name}（{repo.provider}）
                  </option>
                ))}
              </select>
            </label>
            <label className="field">
              <span>流水线名称</span>
              <input
                value={form.name}
                onChange={(e) => setForm({ ...form, name: e.target.value })}
                required
              />
            </label>
            <label className="field">
              <span>分支（支持 *）</span>
              <input
                value={form.branch}
                onChange={(e) => setForm({ ...form, branch: e.target.value })}
                required
              />
            </label>
            <label className="field">
              <span>触发方式</span>
              <select
                value={form.triggerType}
                onChange={(e) => setForm({ ...form, triggerType: e.target.value })}
              >
                <option value="MANUAL">MANUAL</option>
                <option value="WEBHOOK">WEBHOOK</option>
              </select>
            </label>
            <label className="field">
              <span>执行器</span>
              <select
                value={form.executorType}
                onChange={(e) => setForm({ ...form, executorType: e.target.value })}
              >
                <option value="DOCKER">DOCKER</option>
                <option value="K8S">K8S</option>
                <option value="JENKINS">JENKINS</option>
              </select>
            </label>
            <label className="field checkbox">
              <input
                type="checkbox"
                checked={form.enabled}
                onChange={(e) => setForm({ ...form, enabled: e.target.checked })}
              />
              <span>启用</span>
            </label>
            <div className="field full">
              <span className="toolbar" style={{ justifyContent: 'space-between' }}>
                <span>步骤定义（JSON）</span>
                <button type="button" className="btn ghost small" onClick={formatJson}>
                  格式化
                </button>
              </span>
              <textarea
                className="mono"
                value={form.definitionJson}
                spellCheck={false}
                onChange={(e) => setForm({ ...form, definitionJson: e.target.value })}
              />
            </div>
          </div>

          {parsedSteps && parsedSteps.length > 0 && (
            <div className="steps-list" style={{ marginTop: 14 }}>
              {parsedSteps.map((step, index) => (
                <div className="step-item" key={`${step.name}-${index}`}>
                  <div className="step-head">
                    <strong>
                      {index + 1}. {step.name || `step-${index + 1}`}
                    </strong>
                    <span className="mono">{step.image}</span>
                  </div>
                  <div className="muted mono">{(step.commands ?? []).join(' && ')}</div>
                </div>
              ))}
            </div>
          )}
          {!parsedSteps && <div className="alert">JSON 解析失败，请检查括号与逗号</div>}

          <div className="form-actions">
            <button type="submit" className="btn">
              {editingId ? '保存' : '创建'}
            </button>
          </div>
        </form>
      )}

      {triggering && (
        <form className="card" onSubmit={handleTrigger}>
          <div className="card-head">
            <h2>触发 {triggering.name}</h2>
            <button type="button" className="btn ghost small" onClick={() => setTriggering(null)}>
              取消
            </button>
          </div>
          <div className="inline-form">
            <label className="field">
              <span>分支</span>
              <input value={triggerBranch} onChange={(e) => setTriggerBranch(e.target.value)} required />
            </label>
            <label className="field">
              <span>Commit SHA（可选）</span>
              <input value={triggerSha} onChange={(e) => setTriggerSha(e.target.value)} />
            </label>
            <button type="submit" className="btn">
              立即触发
            </button>
            <span className="muted">需要流水线已绑定仓库且处于启用状态。</span>
          </div>
        </form>
      )}

      <div className="card">
        <div className="card-head">
          <h2>流水线列表</h2>
          <span className="muted">{pipelines.length} 条</span>
        </div>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>名称</th>
                <th>分支</th>
                <th>触发</th>
                <th>执行器</th>
                <th>状态</th>
                <th>更新时间</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {pipelines.map((pipeline) => (
                <tr key={pipeline.id}>
                  <td>{pipeline.name}</td>
                  <td className="mono">{pipeline.branch}</td>
                  <td>{pipeline.triggerType}</td>
                  <td>{pipeline.executorType}</td>
                  <td>
                    <span className={`badge ${pipeline.enabled ? 'green' : 'gray'}`}>
                      {pipeline.enabled ? '启用' : '禁用'}
                    </span>
                  </td>
                  <td className="muted">{formatTime(pipeline.updatedAt)}</td>
                  <td className="toolbar">
                    <Link to={`/runs?pipelineId=${pipeline.id}`}>运行</Link>
                    {canWrite(user) && (
                      <>
                        <button type="button" className="btn small" onClick={() => openTrigger(pipeline)}>
                          触发
                        </button>
                        <button
                          type="button"
                          className="btn ghost small"
                          onClick={() => startEdit(pipeline)}
                        >
                          编辑
                        </button>
                      </>
                    )}
                    {isAdmin(user) && (
                      <button
                        type="button"
                        className="btn danger small"
                        onClick={() => void handleDelete(pipeline)}
                      >
                        删除
                      </button>
                    )}
                  </td>
                </tr>
              ))}
              {pipelines.length === 0 && (
                <tr>
                  <td colSpan={7} className="empty">
                    暂无流水线
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
