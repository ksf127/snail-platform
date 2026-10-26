import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { api, errorMessage, formatTime, queryString } from '../api';
import Pagination from '../components/Pagination';
import StatusBadge from '../components/StatusBadge';
import type { PageView, Pipeline, Run } from '../types';

export default function RunsPage() {
  const [params, setParams] = useSearchParams();
  const pipelineId = params.get('pipelineId') ?? '';
  const [pipelines, setPipelines] = useState<Pipeline[]>([]);
  const [runs, setRuns] = useState<PageView<Run> | null>(null);
  const [status, setStatus] = useState('');
  const [triggerType, setTriggerType] = useState('');
  const [keyword, setKeyword] = useState('');
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(10);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const [pipelinePage, runPage] = await Promise.all([
        api.get<PageView<Pipeline>>(`/pipelines${queryString({ page: 1, size: 100 })}`),
        api.get<PageView<Run>>(
          `/runs${queryString({ page, size, pipelineId, status, triggerType, keyword })}`,
        ),
      ]);
      setPipelines(pipelinePage.items);
      setRuns(runPage);
      setError(null);
    } catch (err) {
      setError(errorMessage(err));
    }
  }, [page, size, pipelineId, status, triggerType, keyword]);

  useEffect(() => {
    void load();
  }, [load]);

  function changePipeline(value: string) {
    setPage(1);
    setParams(value ? { pipelineId: value } : {});
  }

  function applyFilters(event: FormEvent) {
    event.preventDefault();
    setPage(1);
    void load();
  }

  function resetFilters() {
    setStatus('');
    setTriggerType('');
    setKeyword('');
    setPage(1);
    setParams({});
  }

  return (
    <>
      <div className="page-head">
        <div>
          <h1>运行记录</h1>
          <p>查看构建状态、日志与制品</p>
        </div>
        <div className="toolbar">
          <button type="button" className="btn ghost" onClick={() => void load()}>
            刷新
          </button>
        </div>
      </div>

      {error && <div className="alert">{error}</div>}

      <div className="card">
        <form className="filters" onSubmit={applyFilters}>
          <input
            value={keyword}
            placeholder="搜索分支 / 触发人 / Commit"
            onChange={(e) => setKeyword(e.target.value)}
          />
          <select value={pipelineId} onChange={(e) => changePipeline(e.target.value)}>
            <option value="">全部流水线</option>
            {pipelines.map((pipeline) => (
              <option key={pipeline.id} value={pipeline.id}>
                {pipeline.name}
              </option>
            ))}
          </select>
          <select value={status} onChange={(e) => setStatus(e.target.value)}>
            <option value="">全部状态</option>
            <option value="QUEUED">QUEUED</option>
            <option value="RUNNING">RUNNING</option>
            <option value="SUCCESS">SUCCESS</option>
            <option value="FAILED">FAILED</option>
            <option value="CANCELED">CANCELED</option>
          </select>
          <select value={triggerType} onChange={(e) => setTriggerType(e.target.value)}>
            <option value="">全部触发方式</option>
            <option value="MANUAL">MANUAL</option>
            <option value="WEBHOOK">WEBHOOK</option>
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
              {(runs?.items ?? []).map((run) => (
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
              {(runs?.items.length ?? 0) === 0 && (
                <tr>
                  <td colSpan={9} className="empty">
                    暂无运行记录
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
        <Pagination
          page={runs}
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
