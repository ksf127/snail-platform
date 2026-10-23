import { useCallback, useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { api, errorMessage, formatTime } from '../api';
import StatusBadge from '../components/StatusBadge';
import type { Pipeline, Run } from '../types';

export default function RunsPage() {
  const [params, setParams] = useSearchParams();
  const pipelineId = params.get('pipelineId') ?? '';
  const [pipelines, setPipelines] = useState<Pipeline[]>([]);
  const [runs, setRuns] = useState<Run[]>([]);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const [pipelineList, runList] = await Promise.all([
        api.get<Pipeline[]>('/pipelines'),
        api.get<Run[]>(pipelineId ? `/runs?pipelineId=${pipelineId}` : '/runs'),
      ]);
      setPipelines(pipelineList);
      setRuns(runList);
      setError(null);
    } catch (err) {
      setError(errorMessage(err));
    }
  }, [pipelineId]);

  useEffect(() => {
    void load();
  }, [load]);

  function changeFilter(value: string) {
    if (value) {
      setParams({ pipelineId: value });
    } else {
      setParams({});
    }
  }

  return (
    <>
      <div className="page-head">
        <div>
          <h1>运行记录</h1>
          <p>查看构建状态、日志与制品</p>
        </div>
        <div className="toolbar">
          <select value={pipelineId} onChange={(e) => changeFilter(e.target.value)}>
            <option value="">全部流水线</option>
            {pipelines.map((pipeline) => (
              <option key={pipeline.id} value={pipeline.id}>
                {pipeline.name}
              </option>
            ))}
          </select>
          <button type="button" className="btn ghost" onClick={() => void load()}>
            刷新
          </button>
        </div>
      </div>

      {error && <div className="alert">{error}</div>}

      <div className="card">
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Run</th>
                <th>流水线</th>
                <th>分支</th>
                <th>Commit</th>
                <th>状态</th>
                <th>触发</th>
                <th>开始</th>
                <th>结束</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {runs.map((run) => (
                <tr key={run.id}>
                  <td>#{run.buildNo}</td>
                  <td>{run.pipelineName}</td>
                  <td className="mono">{run.branch}</td>
                  <td className="mono">{run.commitSha ? run.commitSha.slice(0, 8) : '-'}</td>
                  <td>
                    <StatusBadge status={run.status} />
                  </td>
                  <td>{run.triggeredBy ?? run.triggerType}</td>
                  <td className="muted">{formatTime(run.startedAt)}</td>
                  <td className="muted">{formatTime(run.finishedAt)}</td>
                  <td>
                    <Link to={`/runs/${run.id}`}>详情</Link>
                  </td>
                </tr>
              ))}
              {runs.length === 0 && (
                <tr>
                  <td colSpan={9} className="empty">
                    暂无运行记录
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
