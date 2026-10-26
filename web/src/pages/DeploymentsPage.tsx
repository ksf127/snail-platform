import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { api, errorMessage, formatTime, queryString } from '../api';
import { canWrite, useAuth } from '../auth-context';
import Pagination from '../components/Pagination';
import StatusBadge from '../components/StatusBadge';
import type {
  Deployment,
  DeploymentExecution,
  PageView,
  Project,
  ProjectEnvironment,
} from '../types';

const EMPTY_FILTERS = { project: '', environment: '', status: '', keyword: '' };

/** 发布管理：全部项目的发布记录；点击项目进入项目管理页做构建、日志与镜像下载。 */
export default function DeploymentsPage() {
  const { user } = useAuth();
  const [projects, setProjects] = useState<Project[]>([]);
  const [environments, setEnvironments] = useState<ProjectEnvironment[]>([]);
  const [deployments, setDeployments] = useState<PageView<Deployment> | null>(null);
  const [draft, setDraft] = useState({ ...EMPTY_FILTERS });
  const [applied, setApplied] = useState({ ...EMPTY_FILTERS });
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(10);
  const [reloadKey, setReloadKey] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [executingId, setExecutingId] = useState<number | null>(null);
  const [result, setResult] = useState<DeploymentExecution | null>(null);

  const loadProjects = useCallback(async () => {
    try {
      const all: Project[] = [];
      let current = 1;
      for (;;) {
        const pageView = await api.get<PageView<Project>>(
          `/projects${queryString({ page: current, size: 100 })}`,
        );
        const items = pageView.items ?? [];
        all.push(...items);
        if (items.length === 0 || all.length >= pageView.total) {
          break;
        }
        current += 1;
      }
      setProjects(all);
    } catch (err) {
      setProjects([]);
      setError(errorMessage(err));
    }
  }, []);

  const loadEnvironments = useCallback(async () => {
    try {
      setEnvironments(await api.get<ProjectEnvironment[]>('/environments'));
    } catch {
      setEnvironments([]);
    }
  }, []);

  const loadDeployments = useCallback(async () => {
    try {
      setDeployments(
        await api.get<PageView<Deployment>>(
          `/deployments${queryString({
            page,
            size,
            projectKeyword: applied.project,
            environment: applied.environment,
            status: applied.status,
            keyword: applied.keyword,
          })}`,
        ),
      );
    } catch (err) {
      setDeployments(null);
      setError(errorMessage(err));
    }
  }, [page, size, applied, reloadKey]);

  useEffect(() => {
    void loadProjects();
  }, [loadProjects]);

  useEffect(() => {
    void loadEnvironments();
  }, [loadEnvironments]);

  useEffect(() => {
    void loadDeployments();
  }, [loadDeployments]);

  const environmentOptions = useMemo(
    () => Array.from(new Set(environments.map((item) => item.name))).sort(),
    [environments],
  );

  const projectMap = useMemo(() => {
    const map = new Map<number, Project>();
    projects.forEach((item) => map.set(item.id, item));
    return map;
  }, [projects]);

  function applyFilters(event: FormEvent) {
    event.preventDefault();
    setPage(1);
    setApplied({ ...draft });
    setReloadKey((value) => value + 1);
  }

  function resetFilters() {
    setDraft({ ...EMPTY_FILTERS });
    setApplied({ ...EMPTY_FILTERS });
    setPage(1);
    setReloadKey((value) => value + 1);
  }

  async function handleExecute(deployment: Deployment) {
    setError(null);
    setNotice(null);
    setResult(null);
    setExecutingId(deployment.id);
    try {
      const execution = await api.post<DeploymentExecution>(`/deployments/${deployment.id}/execute`);
      setResult(execution);
      setNotice(`发布单 #${deployment.id} 已执行`);
      setReloadKey((value) => value + 1);
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setExecutingId(null);
    }
  }

  return (
    <>
      <div className="page-head">
        <div>
          <h1>发布管理</h1>
          <p>全部项目的发布记录；点击项目进入项目管理，执行构建、查看日志与下载镜像</p>
        </div>
        <div className="toolbar">
          <button
            type="button"
            className="btn ghost"
            onClick={() => {
              setError(null);
              void loadProjects();
              setReloadKey((value) => value + 1);
            }}
          >
            刷新
          </button>
        </div>
      </div>

      {error && <div className="alert">{error}</div>}
      {notice && <div className="alert info">{notice}</div>}

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

      <div className="card">
        <div className="card-head">
          <h2>发布记录</h2>
          <span className="muted">共 {deployments?.total ?? 0} 条</span>
        </div>

        <form className="filters" onSubmit={applyFilters}>
          <input
            value={draft.project}
            placeholder="搜索项目 Key / 名称"
            onChange={(e) => setDraft({ ...draft, project: e.target.value })}
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                applyFilters(e);
              }
            }}
          />
          <select
            value={draft.environment}
            onChange={(e) => setDraft({ ...draft, environment: e.target.value })}
          >
            <option value="">全部环境</option>
            {environmentOptions.map((item) => (
              <option key={item} value={item}>
                {item}
              </option>
            ))}
          </select>
          <select
            value={draft.status}
            onChange={(e) => setDraft({ ...draft, status: e.target.value })}
          >
            <option value="">全部状态</option>
            <option value="PENDING">PENDING</option>
            <option value="RUNNING">RUNNING</option>
            <option value="SUCCESS">SUCCESS</option>
            <option value="FAILED">FAILED</option>
          </select>
          <input
            value={draft.keyword}
            placeholder="搜索镜像 / 备注"
            onChange={(e) => setDraft({ ...draft, keyword: e.target.value })}
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                applyFilters(e);
              }
            }}
          />
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
                <th>ID</th>
                <th>项目</th>
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
              {(deployments?.items ?? []).map((deployment) => {
                const project = projectMap.get(deployment.projectId);
                return (
                  <tr key={deployment.id}>
                    <td>{deployment.id}</td>
                    <td>
                      <Link className="link-btn" to={`/projects/${deployment.projectId}`}>
                        {project ? project.projectKey : `#${deployment.projectId}`}
                      </Link>
                    </td>
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
                );
              })}
              {(deployments?.items.length ?? 0) === 0 && (
                <tr>
                  <td colSpan={11} className="empty">
                    暂无发布记录
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
        <Pagination
          page={deployments}
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
