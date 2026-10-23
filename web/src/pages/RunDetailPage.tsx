import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api, errorMessage, formatSize, formatTime } from '../api';
import { canWrite, useAuth } from '../auth-context';
import StatusBadge from '../components/StatusBadge';
import type { Artifact, Run } from '../types';

export default function RunDetailPage() {
  const { id } = useParams();
  const { user } = useAuth();
  const runId = Number(id);
  const [run, setRun] = useState<Run | null>(null);
  const [artifacts, setArtifacts] = useState<Artifact[]>([]);
  const [logText, setLogText] = useState<string | null>(null);
  const [logUrl, setLogUrl] = useState<string | null>(null);
  const [logHint, setLogHint] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      const [runView, artifactList] = await Promise.all([
        api.get<Run>(`/runs/${runId}`),
        api.get<Artifact[]>(`/runs/${runId}/artifacts`),
      ]);
      setRun(runView);
      setArtifacts(artifactList);
      setError(null);
    } catch (err) {
      setError(errorMessage(err));
    }
  }, [runId]);

  useEffect(() => {
    void load();
  }, [load]);

  async function loadLog() {
    setLogHint(null);
    try {
      const result = await api.get<{ url: string }>(`/runs/${runId}/log`);
      setLogUrl(result.url);
      try {
        const response = await fetch(result.url);
        if (!response.ok) {
          throw new Error(`HTTP ${response.status}`);
        }
        setLogText(await response.text());
      } catch {
        setLogHint('日志直读失败（可能是对象存储未开放跨域），请点击上方「新窗口打开日志」。');
      }
    } catch (err) {
      setLogHint(errorMessage(err));
    }
  }

  async function handleCancel() {
    if (!window.confirm('确认取消该运行？已启动的容器不会被强制停止。')) {
      return;
    }
    setBusy(true);
    try {
      await api.post<Run>(`/runs/${runId}/cancel`);
      await load();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  async function downloadArtifact(artifactId: number) {
    try {
      const result = await api.get<{ url: string }>(`/artifacts/${artifactId}/download`);
      window.open(result.url, '_blank', 'noopener');
    } catch (err) {
      setError(errorMessage(err));
    }
  }

  if (!run) {
    return (
      <>
        {error && <div className="alert">{error}</div>}
        {!error && <div className="card empty">加载中…</div>}
      </>
    );
  }

  const active = run.status === 'QUEUED' || run.status === 'RUNNING';

  return (
    <>
      <div className="page-head">
        <div>
          <h1>
            运行 #{run.buildNo} · {run.pipelineName}
          </h1>
          <p>
            <Link to="/runs">返回运行记录</Link>
          </p>
        </div>
        <div className="toolbar">
          <StatusBadge status={run.status} />
          <button type="button" className="btn ghost" onClick={() => void load()}>
            刷新
          </button>
          {active && canWrite(user) && (
            <button type="button" className="btn danger" onClick={() => void handleCancel()} disabled={busy}>
              取消运行
            </button>
          )}
        </div>
      </div>

      {error && <div className="alert">{error}</div>}
      {run.errorMessage && <div className="alert">{run.errorMessage}</div>}

      <div className="split">
        <div className="card">
          <div className="card-head">
            <h2>基本信息</h2>
          </div>
          <dl className="kv">
            <dt>Run ID</dt>
            <dd>{run.id}</dd>
            <dt>流水线</dt>
            <dd>
              {run.pipelineName}（#{run.pipelineId}）
            </dd>
            <dt>分支</dt>
            <dd className="mono">{run.branch}</dd>
            <dt>Commit</dt>
            <dd className="mono">{run.commitSha ?? '-'}</dd>
            <dt>触发方式</dt>
            <dd>
              {run.triggerType} / {run.triggeredBy ?? '-'}
            </dd>
            <dt>创建时间</dt>
            <dd>{formatTime(run.createdAt)}</dd>
            <dt>开始时间</dt>
            <dd>{formatTime(run.startedAt)}</dd>
            <dt>结束时间</dt>
            <dd>{formatTime(run.finishedAt)}</dd>
            <dt>日志对象</dt>
            <dd className="mono">{run.logObject ?? '-'}</dd>
          </dl>
        </div>

        <div className="card">
          <div className="card-head">
            <h2>制品</h2>
            <span className="muted">{artifacts.length} 个</span>
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
                {artifacts.map((artifact) => (
                  <tr key={artifact.id}>
                    <td>{artifact.fileName}</td>
                    <td>{formatSize(artifact.size)}</td>
                    <td className="mono">{artifact.sha256 ? artifact.sha256.slice(0, 12) : '-'}</td>
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
                {artifacts.length === 0 && (
                  <tr>
                    <td colSpan={4} className="empty">
                      暂无制品
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      </div>

      <div className="card">
        <div className="card-head">
          <h2>构建日志</h2>
          <div className="toolbar">
            <button type="button" className="btn ghost small" onClick={() => void loadLog()}>
              加载日志
            </button>
            {logUrl && (
              <a className="btn ghost small" href={logUrl} target="_blank" rel="noreferrer">
                新窗口打开日志
              </a>
            )}
          </div>
        </div>
        {logHint && <div className="alert info">{logHint}</div>}
        <pre className="log">{logText ?? '尚未加载日志。运行结束后日志会写入对象存储。'}</pre>
      </div>
    </>
  );
}
