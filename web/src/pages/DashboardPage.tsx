import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api, errorMessage, formatTime } from '../api';
import StatusBadge from '../components/StatusBadge';
import type { Dashboard } from '../types';

export default function DashboardPage() {
  const [data, setData] = useState<Dashboard | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setData(await api.get<Dashboard>('/dashboard'));
      setError(null);
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <>
      <div className="page-head">
        <div>
          <h1>仪表盘</h1>
          <p>平台资源概览与最近构建情况</p>
        </div>
        <button type="button" className="btn ghost" onClick={() => void load()} disabled={loading}>
          刷新
        </button>
      </div>

      {error && <div className="alert">{error}</div>}

      <div className="stat-grid">
        <div className="stat">
          <span>项目</span>
          <strong>{data?.projectCount ?? '-'}</strong>
        </div>
        <div className="stat">
          <span>代码仓库</span>
          <strong>{data?.repoCount ?? '-'}</strong>
        </div>
        <div className="stat">
          <span>流水线</span>
          <strong>{data?.pipelineCount ?? '-'}</strong>
        </div>
        <div className="stat">
          <span>构建次数</span>
          <strong>{data?.runCount ?? '-'}</strong>
        </div>
        <div className="stat">
          <span>构建成功率</span>
          <strong>{data ? (data.successRate * 100).toFixed(1) : '-'}</strong>
          <em>%</em>
        </div>
      </div>

      <div className="card">
        <div className="card-head">
          <h2>最近 10 次运行</h2>
          <Link to="/runs">查看全部</Link>
        </div>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>#</th>
                <th>流水线</th>
                <th>分支</th>
                <th>状态</th>
                <th>触发方式</th>
                <th>开始时间</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {data?.recentRuns.map((run) => (
                <tr key={run.id}>
                  <td>#{run.buildNo}</td>
                  <td>{run.pipelineName}</td>
                  <td className="mono">{run.branch}</td>
                  <td>
                    <StatusBadge status={run.status} />
                  </td>
                  <td>{run.triggerType}</td>
                  <td className="muted">{formatTime(run.startedAt ?? run.createdAt)}</td>
                  <td>
                    <Link to={`/runs/${run.id}`}>详情</Link>
                  </td>
                </tr>
              ))}
              {data && data.recentRuns.length === 0 && (
                <tr>
                  <td colSpan={7} className="empty">
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
