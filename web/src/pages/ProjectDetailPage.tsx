import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api, errorMessage, formatSize, formatTime, queryString } from '../api';
import { canWrite, useAuth } from '../auth-context';
import Pagination from '../components/Pagination';
import ProjectBranchesPanel from '../components/ProjectBranchesPanel';
import ProjectEnvironmentsPanel from '../components/ProjectEnvironmentsPanel';
import ProjectSecretsPanel from '../components/ProjectSecretsPanel';
import StatusBadge from '../components/StatusBadge';
import type {
  Artifact,
  Deployment,
  DeploymentExecution,
  PageView,
  Pipeline,
  Project,
  ProjectEnvironment,
  Run,
} from '../types';

type Tab = 'build' | 'runs' | 'deploy' | 'branches' | 'envs' | 'secrets';

interface DeployForm {
  environment: string;
  image: string;
  replicas: string;
  runId: string;
  note: string;
}

interface LogPanel {
  title: string;
  url?: string;
  text?: string | null;
  hint?: string;
}

const EMPTY_FORM: DeployForm = {
  environment: '',
  image: '',
  replicas: '1',
  runId: '',
  note: '',
};

const EMPTY_DEPLOY_FILTERS = { environment: '', status: '', keyword: '' };

/** 项目管理页：固定项目上下文，覆盖该项目所有分支/环境的构建、日志、制品下载与发布。 */
export default function ProjectDetailPage() {
  const { id } = useParams();
  const projectId = Number(id);
  const { user } = useAuth();

  const [project, setProject] = useState<Project | null>(null);
  const [branch, setBranch] = useState('');
  const [tab, setTab] = useState<Tab>('build');
  const [pipelines, setPipelines] = useState<Pipeline[]>([]);
  const [runs, setRuns] = useState<PageView<Run> | null>(null);
  const [runPage, setRunPage] = useState(1);
  const [runSize, setRunSize] = useState(10);
  const [runStatus, setRunStatus] = useState('');
  const [deployments, setDeployments] = useState<PageView<Deployment> | null>(null);
  const [environments, setEnvironments] = useState<ProjectEnvironment[]>([]);
  const [deployDraft, setDeployDraft] = useState({ ...EMPTY_DEPLOY_FILTERS });
  const [deployApplied, setDeployApplied] = useState({ ...EMPTY_DEPLOY_FILTERS });
  const [deployPage, setDeployPage] = useState(1);
  const [deploySize, setDeploySize] = useState(10);
  const [runKey, setRunKey] = useState(0);
  const [deployKey, setDeployKey] = useState(0);
  const [pipelineKey, setPipelineKey] = useState(0);
  const [envKey, setEnvKey] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [busyPipelineId, setBusyPipelineId] = useState<number | null>(null);
  const [logPanel, setLogPanel] = useState<LogPanel | null>(null);
  const [artifactPanel, setArtifactPanel] = useState<{
    run: Run;
    items: Artifact[];
    busy: boolean;
  } | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState<DeployForm>({ ...EMPTY_FORM });
  const [executingId, setExecutingId] = useState<number | null>(null);
  const [result, setResult] = useState<DeploymentExecution | null>(null);

  const valid = Number.isFinite(projectId) && projectId > 0;

  const loadProject = useCallback(async () => {
    if (!valid) {
      return;
    }
    try {
      setProject(await api.get<Project>(`/projects/${projectId}`));
    } catch (err) {
      setProject(null);
      setError(errorMessage(err));
    }
  }, [projectId, valid]);

  const loadPipelines = useCallback(async () => {
    if (!valid) {
      return;
    }
    try {
      const page = await api.get<PageView<Pipeline>>(
        `/pipelines${queryString({ page: 1, size: 100, projectId })}`,
      );
      setPipelines(page.items ?? []);
    } catch (err) {
      setPipelines([]);
      setError(errorMessage(err));
    }
  }, [projectId, valid, pipelineKey]);

  const loadRuns = useCallback(async () => {
    if (!valid) {
      return;
    }
    try {
      setRuns(
        await api.get<PageView<Run>>(
          `/runs${queryString({
            page: runPage,
            size: runSize,
            projectId,
            branch: branch.trim(),
            status: runStatus,
          })}`,
        ),
      );
    } catch (err) {
      setRuns(null);
      setError(errorMessage(err));
    }
  }, [projectId, valid, branch, runPage, runSize, runStatus, runKey]);

  const loadDeployments = useCallback(async () => {
    if (!valid) {
      return;
    }
    try {
      setDeployments(
        await api.get<PageView<Deployment>>(
          `/deployments${queryString({
            page: deployPage,
            size: deploySize,
            projectId,
            environment: deployApplied.environment,
            status: deployApplied.status,
            keyword: deployApplied.keyword,
          })}`,
        ),
      );
    } catch (err) {
      setDeployments(null);
      setError(errorMessage(err));
    }
  }, [projectId, valid, deployPage, deploySize, deployApplied, deployKey]);

  const loadEnvironments = useCallback(async () => {
    if (!valid) {
      return;
    }
    try {
      setEnvironments(await api.get<ProjectEnvironment[]>(`/environments${queryString({ projectId })}`));
    } catch (err) {
      setEnvironments([]);
      setError(errorMessage(err));
    }
  }, [projectId, valid, envKey]);

  useEffect(() => {
    void loadProject();
  }, [loadProject]);

  useEffect(() => {
    void loadPipelines();
  }, [loadPipelines]);

  useEffect(() => {
    void loadRuns();
  }, [loadRuns]);

  useEffect(() => {
    void loadDeployments();
  }, [loadDeployments]);

  useEffect(() => {
    void loadEnvironments();
  }, [loadEnvironments]);

  const branchOptions = useMemo(() => {
    const values = new Set<string>();
    pipelines.forEach((item) => {
      if (item.branch && item.branch !== '*') {
        values.add(item.branch);
      }
    });
    (runs?.items ?? []).forEach((item) => {
      if (item.branch) {
        values.add(item.branch);
      }
    });
    return Array.from(values).sort();
  }, [pipelines, runs]);

  const visiblePipelines = useMemo(() => {
    const current = branch.trim();
    if (!current) {
      return pipelines;
    }
    return pipelines.filter((item) => item.branch === '*' || item.branch === current);
  }, [pipelines, branch]);

  const enabledEnvironments = useMemo(
    () => environments.filter((item) => item.enabled),
    [environments],
  );

  const selectedEnvironment = useMemo(
    () => environments.find((item) => item.name === form.environment) ?? null,
    [environments, form.environment],
  );

  function defaultDeployValues() {
    const target = enabledEnvironments[0];
    return {
      environment: target?.name ?? '',
      replicas: target ? String(target.defaultReplicas) : '1',
    };
  }

  function toggleDeployForm() {
    setShowForm((prev) => {
      if (!prev) {
        setForm({ ...EMPTY_FORM, ...defaultDeployValues() });
      }
      return !prev;
    });
  }

  function refreshAll() {
    setError(null);
    void loadProject();
    setPipelineKey((value) => value + 1);
    setRunKey((value) => value + 1);
    setDeployKey((value) => value + 1);
    setEnvKey((value) => value + 1);
  }

  function applyDeployFilters(event: FormEvent) {
    event.preventDefault();
    setDeployPage(1);
    setDeployApplied({ ...deployDraft });
    setDeployKey((value) => value + 1);
  }

  function resetDeployFilters() {
    setDeployDraft({ ...EMPTY_DEPLOY_FILTERS });
    setDeployApplied({ ...EMPTY_DEPLOY_FILTERS });
    setDeployPage(1);
    setDeployKey((value) => value + 1);
  }

  async function triggerBuild(pipeline: Pipeline) {
    const target = pipeline.branch === '*' ? branch.trim() : pipeline.branch;
    if (!target) {
      setError('该流水线使用通配分支，请先在上方选择或填写分支');
      return;
    }
    setError(null);
    setNotice(null);
    setBusyPipelineId(pipeline.id);
    try {
      await api.post<Run>(`/pipelines/${pipeline.id}/trigger`, {
        branch: target,
        commitSha: null,
      });
      setNotice(`已触发构建：${pipeline.name}（${target}）`);
      setTab('runs');
      setRunPage(1);
      setRunKey((value) => value + 1);
      window.setTimeout(() => setRunKey((value) => value + 1), 2000);
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusyPipelineId(null);
    }
  }

  async function openLog(run: Run) {
    setLogPanel({ title: `运行 #${run.id} · ${run.pipelineName}`, text: null, hint: '正在加载日志…' });
    try {
      const { url } = await api.get<{ url: string }>(`/runs/${run.id}/log`);
      setLogPanel({ title: `运行 #${run.id} · ${run.pipelineName}`, url, text: null });
      try {
        const response = await fetch(url);
        if (!response.ok) {
          throw new Error(`HTTP ${response.status}`);
        }
        const text = await response.text();
        setLogPanel((prev) => (prev ? { ...prev, text } : prev));
      } catch {
        setLogPanel((prev) =>
          prev
            ? { ...prev, hint: '日志直读失败（对象存储未开放跨域），请点击「新窗口打开日志」。' }
            : prev,
        );
      }
    } catch (err) {
      setLogPanel((prev) => (prev ? { ...prev, hint: errorMessage(err) } : prev));
    }
  }

  async function openArtifacts(run: Run) {
    setArtifactPanel({ run, items: [], busy: true });
    try {
      const items = await api.get<Artifact[]>(`/runs/${run.id}/artifacts`);
      setArtifactPanel({ run, items: items ?? [], busy: false });
    } catch (err) {
      setArtifactPanel(null);
      setError(errorMessage(err));
    }
  }

  async function downloadArtifact(artifactId: number) {
    try {
      const { url } = await api.get<{ url: string }>(`/artifacts/${artifactId}/download`);
      window.open(url, '_blank', 'noopener');
    } catch (err) {
      setError(errorMessage(err));
    }
  }

  function deployFromRun(run: Run) {
    setForm({
      ...EMPTY_FORM,
      ...defaultDeployValues(),
      runId: String(run.id),
      note: `来自运行 #${run.id}`,
    });
    setTab('deploy');
    setShowForm(true);
    setNotice(null);
  }

  async function handleCreate(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setNotice(null);
    if (!form.environment) {
      setError('请先在「环境配置」中启用至少一个环境');
      return;
    }
    try {
      await api.post<Deployment>('/deployments', {
        projectId,
        runId: form.runId ? Number(form.runId) : null,
        environment: form.environment,
        image: form.image.trim(),
        replicas: form.replicas ? Number(form.replicas) : null,
        note: form.note || null,
      });
      setShowForm(false);
      setForm({ ...EMPTY_FORM });
      setNotice('发布单已创建，可在下方发布记录中执行。');
      setDeployPage(1);
      setDeployKey((value) => value + 1);
    } catch (err) {
      setError(errorMessage(err));
    }
  }

  async function handleExecute(deployment: Deployment) {
    setError(null);
    setResult(null);
    setExecutingId(deployment.id);
    try {
      const execution = await api.post<DeploymentExecution>(`/deployments/${deployment.id}/execute`);
      setResult(execution);
      setDeployKey((value) => value + 1);
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setExecutingId(null);
    }
  }

  if (!valid) {
    return (
      <>
        <div className="alert">无效的项目 ID</div>
        <Link className="btn ghost" to="/projects">
          返回项目列表
        </Link>
      </>
    );
  }

  const projectLabel = project ? `${project.projectKey} · ${project.name}` : `项目 #${projectId}`;

  return (
    <>
      <div className="page-head">
        <div>
          <div className="muted">
            <Link className="link-btn" to="/projects">
              项目
            </Link>{' '}
            / {project?.projectKey ?? projectId}
          </div>
          <h1>{project?.name ?? '项目管理'}</h1>
          <p>覆盖该项目所有分支的构建、日志、镜像下载与发布</p>
        </div>
        <div className="toolbar">
          <Link className="btn ghost" to="/deployments">
            发布管理
          </Link>
          <button type="button" className="btn ghost" onClick={refreshAll}>
            刷新
          </button>
        </div>
      </div>

      {error && <div className="alert">{error}</div>}
      {notice && <div className="alert info">{notice}</div>}

      <div className="card">
        <div className="card-head">
          <h2>项目上下文</h2>
          <span className="muted">{projectLabel}</span>
        </div>
        <div className="filters">
          <label className="field">
            <span>分支（构建目标 / 运行筛选）</span>
            <input
              list="project-branch-options"
              value={branch}
              placeholder="全部，可输入任意分支"
              onChange={(e) => {
                setBranch(e.target.value);
                setRunPage(1);
              }}
            />
            <datalist id="project-branch-options">
              {branchOptions.map((item) => (
                <option key={item} value={item} />
              ))}
            </datalist>
          </label>
          <button
            type="button"
            className="btn ghost small"
            onClick={() => {
              setBranch('');
              setRunPage(1);
            }}
          >
            全部分支
          </button>
          {project && (
            <>
              <span className="muted">状态：{project.status}</span>
              <span className="muted">Owner：#{project.ownerId}</span>
            </>
          )}
        </div>
      </div>

      <div className="card">
        <div className="tabs">
          <button
            type="button"
            className={tab === 'build' ? 'tab active' : 'tab'}
            onClick={() => setTab('build')}
          >
            流水线构建 <span className="tab-count">{visiblePipelines.length}</span>
          </button>
          <button
            type="button"
            className={tab === 'runs' ? 'tab active' : 'tab'}
            onClick={() => setTab('runs')}
          >
            运行与制品 <span className="tab-count">{runs?.total ?? 0}</span>
          </button>
          <button
            type="button"
            className={tab === 'deploy' ? 'tab active' : 'tab'}
            onClick={() => setTab('deploy')}
          >
            发布记录 <span className="tab-count">{deployments?.total ?? 0}</span>
          </button>
          <button
            type="button"
            className={tab === 'branches' ? 'tab active' : 'tab'}
            onClick={() => setTab('branches')}
          >
            分支团队
          </button>
          <button
            type="button"
            className={tab === 'envs' ? 'tab active' : 'tab'}
            onClick={() => setTab('envs')}
          >
            环境配置 <span className="tab-count">{environments.length}</span>
          </button>
          <button
            type="button"
            className={tab === 'secrets' ? 'tab active' : 'tab'}
            onClick={() => setTab('secrets')}
          >
            构建变量
          </button>
        </div>

        {tab === 'branches' && (
          <ProjectBranchesPanel
            projectId={projectId}
            onChanged={() => {
              setPipelineKey((value) => value + 1);
              setRunKey((value) => value + 1);
            }}
          />
        )}

        {tab === 'envs' && (
          <ProjectEnvironmentsPanel projectId={projectId} onChanged={() => setEnvKey((value) => value + 1)} />
        )}

        {tab === 'secrets' && <ProjectSecretsPanel projectId={projectId} />}

        {tab === 'build' && (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>流水线</th>
                  <th>端</th>
                  <th>触发方式</th>
                  <th>分支</th>
                  <th>团队</th>
                  <th>状态</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {visiblePipelines.map((item) => (
                  <tr key={item.id}>
                    <td>
                      {item.name}
                      <span className="muted"> #{item.id}</span>
                    </td>
                    <td>
                      <span className="side-badge">
                        {item.side === 'FRONTEND' ? '前端' : '后端'}
                      </span>
                    </td>
                    <td className="muted">{item.triggerType}</td>
                    <td className="mono">{item.branch}</td>
                    <td>{item.team ?? '-'}</td>
                    <td>
                      <StatusBadge status={item.enabled ? 'ACTIVE' : 'DISABLED'} />
                    </td>
                    <td>
                      {canWrite(user) && (
                        <button
                          type="button"
                          className="btn small"
                          disabled={!item.enabled || busyPipelineId === item.id}
                          onClick={() => void triggerBuild(item)}
                        >
                          {busyPipelineId === item.id ? '触发中…' : '构建'}
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
                {visiblePipelines.length === 0 && (
                  <tr>
                    <td colSpan={7} className="empty">
                      {pipelines.length === 0
                        ? '该项目暂无流水线，请先在「流水线」页创建'
                        : '当前分支没有匹配的流水线'}
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        )}

        {tab === 'runs' && (
          <>
            <div className="filters">
              <select
                value={runStatus}
                onChange={(e) => {
                  setRunStatus(e.target.value);
                  setRunPage(1);
                }}
              >
                <option value="">全部状态</option>
                <option value="QUEUED">QUEUED</option>
                <option value="RUNNING">RUNNING</option>
                <option value="SUCCESS">SUCCESS</option>
                <option value="FAILED">FAILED</option>
                <option value="CANCELED">CANCELED</option>
              </select>
              <span className="muted">共 {runs?.total ?? 0} 条运行记录</span>
            </div>
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>ID</th>
                    <th>流水线</th>
                    <th>分支</th>
                    <th>状态</th>
                    <th>Commit</th>
                    <th>触发</th>
                    <th>创建时间</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  {(runs?.items ?? []).map((run) => (
                    <tr key={run.id}>
                      <td>{run.id}</td>
                      <td>{run.pipelineName}</td>
                      <td className="mono">{run.branch}</td>
                      <td>
                        <StatusBadge status={run.status} />
                      </td>
                      <td className="mono">{run.commitSha ? run.commitSha.slice(0, 8) : '-'}</td>
                      <td className="muted">
                        {run.triggerType} / {run.triggeredBy ?? '-'}
                      </td>
                      <td className="muted">{formatTime(run.createdAt)}</td>
                      <td className="toolbar">
                        <button
                          type="button"
                          className="btn ghost small"
                          onClick={() => void openLog(run)}
                        >
                          日志
                        </button>
                        <button
                          type="button"
                          className="btn ghost small"
                          onClick={() => void openArtifacts(run)}
                        >
                          制品/镜像
                        </button>
                        {canWrite(user) && (
                          <button
                            type="button"
                            className="btn small"
                            onClick={() => deployFromRun(run)}
                          >
                            发布
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                  {(runs?.items.length ?? 0) === 0 && (
                    <tr>
                      <td colSpan={8} className="empty">
                        暂无运行记录，可在「流水线构建」中触发构建
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
            <Pagination
              page={runs}
              onPageChange={setRunPage}
              onSizeChange={(next) => {
                setRunSize(next);
                setRunPage(1);
              }}
            />
          </>
        )}

        {tab === 'deploy' && (
          <>
            <div className="toolbar" style={{ marginBottom: 12 }}>
              {canWrite(user) && (
                <button type="button" className="btn" onClick={toggleDeployForm}>
                  {showForm ? '收起发布单' : '新建发布单'}
                </button>
              )}
              <span className="muted">K8s 未配置时执行只返回 PREVIEW 命令预览，不会变更集群</span>
            </div>

            {showForm && canWrite(user) && (
              <form className="card" onSubmit={handleCreate} style={{ marginBottom: 16 }}>
                <div className="card-head">
                  <h2>新建发布单</h2>
                  <span className="muted">{projectLabel}</span>
                </div>
                <div className="form-grid">
                  <label className="field">
                    <span>环境</span>
                    <select
                      value={form.environment}
                      onChange={(e) => {
                        const next = environments.find((item) => item.name === e.target.value);
                        setForm({
                          ...form,
                          environment: e.target.value,
                          replicas: next ? String(next.defaultReplicas) : form.replicas,
                        });
                      }}
                    >
                      <option value="">请选择环境</option>
                      {enabledEnvironments.map((item) => (
                        <option key={item.id} value={item.name}>
                          {item.name} · {item.displayName}
                        </option>
                      ))}
                    </select>
                    <span className="field-hint">
                      {selectedEnvironment
                        ? `namespace：${selectedEnvironment.namespace}｜默认副本：${selectedEnvironment.defaultReplicas}`
                        : '环境在「环境配置」中维护'}
                    </span>
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
                    <input
                      value={form.note}
                      onChange={(e) => setForm({ ...form, note: e.target.value })}
                    />
                  </label>
                </div>
                <div className="form-actions">
                  <button type="submit" className="btn">
                    创建发布单
                  </button>
                </div>
              </form>
            )}

            {result && (
              <div className="card" style={{ marginBottom: 16 }}>
                <div className="card-head">
                  <h2>
                    执行结果 <StatusBadge status={result.mode} />
                  </h2>
                  <button type="button" className="btn ghost small" onClick={() => setResult(null)}>
                    关闭
                  </button>
                </div>
                <p className="muted">{result.message}</p>
                {result.commandPreview && <pre className="log">{result.commandPreview}</pre>}
              </div>
            )}

            <form className="filters" onSubmit={applyDeployFilters}>
              <input
                value={deployDraft.keyword}
                placeholder="搜索镜像 / 备注"
                onChange={(e) => setDeployDraft({ ...deployDraft, keyword: e.target.value })}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') {
                    applyDeployFilters(e);
                  }
                }}
              />
              <select
                value={deployDraft.environment}
                onChange={(e) => setDeployDraft({ ...deployDraft, environment: e.target.value })}
              >
                <option value="">全部环境</option>
                {environments.map((item) => (
                  <option key={item.id} value={item.name}>
                    {item.name} · {item.displayName}
                  </option>
                ))}
              </select>
              <select
                value={deployDraft.status}
                onChange={(e) => setDeployDraft({ ...deployDraft, status: e.target.value })}
              >
                <option value="">全部状态</option>
                <option value="PENDING">PENDING</option>
                <option value="RUNNING">RUNNING</option>
                <option value="SUCCESS">SUCCESS</option>
                <option value="FAILED">FAILED</option>
              </select>
              <button type="submit" className="btn small">
                查询
              </button>
              <button type="button" className="btn ghost small" onClick={resetDeployFilters}>
                重置
              </button>
            </form>

            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>ID</th>
                    <th>环境</th>
                    <th>namespace</th>
                    <th>镜像</th>
                    <th>副本</th>
                    <th>状态</th>
                    <th>Run</th>
                    <th>备注</th>
                    <th>更新时间</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {(deployments?.items ?? []).map((deployment) => (
                    <tr key={deployment.id}>
                      <td>{deployment.id}</td>
                      <td>{deployment.environment}</td>
                      <td className="mono">{deployment.namespace ?? '-'}</td>
                      <td className="mono">{deployment.image}</td>
                      <td>{deployment.replicas}</td>
                      <td>
                        <StatusBadge status={deployment.status} />
                      </td>
                      <td>{deployment.runId ?? '-'}</td>
                      <td className="muted">{deployment.note ?? '-'}</td>
                      <td className="muted">{formatTime(deployment.updatedAt)}</td>
                      <td>
                        {canWrite(user) && (
                          <button
                            type="button"
                            className="btn small"
                            disabled={executingId === deployment.id}
                            onClick={() => void handleExecute(deployment)}
                          >
                            {executingId === deployment.id ? '执行中…' : '执行'}
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                  {(deployments?.items.length ?? 0) === 0 && (
                    <tr>
                      <td colSpan={10} className="empty">
                        暂无发布记录
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
            <Pagination
              page={deployments}
              onPageChange={setDeployPage}
              onSizeChange={(next) => {
                setDeploySize(next);
                setDeployPage(1);
              }}
            />
          </>
        )}
      </div>

      {logPanel && (
        <div className="modal-mask" onClick={() => setLogPanel(null)}>
          <div className="modal wide" onClick={(e) => e.stopPropagation()}>
            <div className="modal-head">
              <h2>{logPanel.title} · 构建日志</h2>
              <div className="toolbar">
                {logPanel.url && (
                  <a className="btn ghost small" href={logPanel.url} target="_blank" rel="noreferrer">
                    新窗口打开日志
                  </a>
                )}
                <button type="button" className="btn ghost small" onClick={() => setLogPanel(null)}>
                  关闭
                </button>
              </div>
            </div>
            {logPanel.hint && <div className="alert info">{logPanel.hint}</div>}
            <pre className="log">
              {logPanel.text ?? '尚未加载到日志内容。运行结束后日志会写入对象存储。'}
            </pre>
          </div>
        </div>
      )}

      {artifactPanel && (
        <div className="modal-mask" onClick={() => setArtifactPanel(null)}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <div className="modal-head">
              <h2>运行 #{artifactPanel.run.id} · 制品 / 镜像</h2>
              <button type="button" className="btn ghost small" onClick={() => setArtifactPanel(null)}>
                关闭
              </button>
            </div>
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>文件名</th>
                    <th>大小</th>
                    <th>SHA256</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {artifactPanel.items.map((artifact) => (
                    <tr key={artifact.id}>
                      <td>{artifact.fileName}</td>
                      <td>{formatSize(artifact.size)}</td>
                      <td className="mono">
                        {artifact.sha256 ? artifact.sha256.slice(0, 12) : '-'}
                      </td>
                      <td>
                        <button
                          type="button"
                          className="btn ghost small"
                          onClick={() => void downloadArtifact(artifact.id)}
                        >
                          下载
                        </button>
                      </td>
                    </tr>
                  ))}
                  {!artifactPanel.busy && artifactPanel.items.length === 0 && (
                    <tr>
                      <td colSpan={4} className="empty">
                        该运行暂无制品
                      </td>
                    </tr>
                  )}
                  {artifactPanel.busy && (
                    <tr>
                      <td colSpan={4} className="empty">
                        加载中…
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
