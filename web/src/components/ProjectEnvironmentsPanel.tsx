import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { api, errorMessage, formatTime, queryString } from '../api';
import { canWrite, useAuth } from '../auth-context';
import type { ProjectEnvironment } from '../types';

interface Props {
  projectId: number;
  /** 环境列表变化时通知外层（发布表单需要最新的环境下拉）。 */
  onChanged?: () => void;
}

interface EnvForm {
  id: number | null;
  name: string;
  displayName: string;
  namespace: string;
  defaultReplicas: string;
  sortOrder: string;
  enabled: boolean;
  description: string;
}

const EMPTY_FORM: EnvForm = {
  id: null,
  name: '',
  displayName: '',
  namespace: '',
  defaultReplicas: '1',
  sortOrder: '10',
  enabled: true,
  description: '',
};

/** 环境配置：每个环境独立 namespace 与默认副本，发布单按环境固化发布目标。 */
export default function ProjectEnvironmentsPanel({ projectId, onChanged }: Props) {
  const { user } = useAuth();
  const [rows, setRows] = useState<ProjectEnvironment[]>([]);
  const [form, setForm] = useState<EnvForm>({ ...EMPTY_FORM });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      setRows(await api.get<ProjectEnvironment[]>(`/environments${queryString({ projectId })}`));
    } catch (err) {
      setRows([]);
      setError(errorMessage(err));
    }
  }, [projectId]);

  useEffect(() => {
    void load();
  }, [load]);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setNotice(null);
    setBusy(true);
    const payload = {
      projectId,
      name: form.name.trim().toUpperCase(),
      displayName: form.displayName.trim(),
      namespace: form.namespace.trim(),
      defaultReplicas: form.defaultReplicas ? Number(form.defaultReplicas) : 1,
      sortOrder: form.sortOrder ? Number(form.sortOrder) : 10,
      enabled: form.enabled,
      description: form.description.trim() || null,
    };
    try {
      if (form.id) {
        await api.put<ProjectEnvironment>(`/environments/${form.id}`, payload);
        setNotice(`环境 ${payload.name} 已更新`);
      } else {
        await api.post<ProjectEnvironment>('/environments', payload);
        setNotice(`环境 ${payload.name} 已创建`);
      }
      setForm({ ...EMPTY_FORM });
      await load();
      onChanged?.();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  function startEdit(row: ProjectEnvironment) {
    setForm({
      id: row.id,
      name: row.name,
      displayName: row.displayName,
      namespace: row.namespace,
      defaultReplicas: String(row.defaultReplicas),
      sortOrder: String(row.sortOrder),
      enabled: row.enabled,
      description: row.description ?? '',
    });
    setError(null);
    setNotice(null);
  }

  async function handleDelete(row: ProjectEnvironment) {
    if (!window.confirm(`确认删除环境「${row.name}」？已有发布单不受影响，但不能再用该环境新建发布单。`)) {
      return;
    }
    setError(null);
    setNotice(null);
    try {
      await api.delete<void>(`/environments/${row.id}`);
      setNotice(`环境 ${row.name} 已删除`);
      if (form.id === row.id) {
        setForm({ ...EMPTY_FORM });
      }
      await load();
      onChanged?.();
    } catch (err) {
      setError(errorMessage(err));
    }
  }

  return (
    <>
      {error && <div className="alert">{error}</div>}
      {notice && <div className="alert info">{notice}</div>}

      {canWrite(user) && (
        <form className="card" onSubmit={handleSubmit} style={{ marginBottom: 16 }}>
          <div className="card-head">
            <h2>{form.id ? '编辑环境' : '新增环境'}</h2>
            <span className="muted">
              新项目默认生成 DEV / TEST / PROD，namespace 以项目 Key 为前缀
            </span>
          </div>
          <div className="form-grid">
            <label className="field">
              <span>环境编码</span>
              <input
                value={form.name}
                placeholder="DEV / TEST / PROD / STAGING"
                disabled={Boolean(form.id)}
                onChange={(e) => setForm({ ...form, name: e.target.value.toUpperCase() })}
                required
              />
            </label>
            <label className="field">
              <span>环境名称</span>
              <input
                value={form.displayName}
                placeholder="开发环境"
                onChange={(e) => setForm({ ...form, displayName: e.target.value })}
                required
              />
            </label>
            <label className="field">
              <span>K8s namespace</span>
              <input
                value={form.namespace}
                placeholder="demo-app-test"
                onChange={(e) => setForm({ ...form, namespace: e.target.value })}
                required
              />
            </label>
            <label className="field">
              <span>默认副本数</span>
              <input
                type="number"
                min="1"
                value={form.defaultReplicas}
                onChange={(e) => setForm({ ...form, defaultReplicas: e.target.value })}
              />
            </label>
            <label className="field">
              <span>排序</span>
              <input
                type="number"
                min="0"
                value={form.sortOrder}
                onChange={(e) => setForm({ ...form, sortOrder: e.target.value })}
              />
            </label>
            <label className="field">
              <span>状态</span>
              <select
                value={form.enabled ? '1' : '0'}
                onChange={(e) => setForm({ ...form, enabled: e.target.value === '1' })}
              >
                <option value="1">启用</option>
                <option value="0">停用</option>
              </select>
            </label>
            <label className="field full">
              <span>说明</span>
              <input
                value={form.description}
                placeholder="该环境的用途、访问域名等"
                onChange={(e) => setForm({ ...form, description: e.target.value })}
              />
            </label>
          </div>
          <div className="form-actions">
            <button type="submit" className="btn" disabled={busy}>
              {busy ? '提交中…' : form.id ? '保存修改' : '新增环境'}
            </button>
            {form.id && (
              <button
                type="button"
                className="btn ghost"
                onClick={() => setForm({ ...EMPTY_FORM })}
              >
                取消编辑
              </button>
            )}
          </div>
        </form>
      )}

      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>编码</th>
              <th>名称</th>
              <th>namespace</th>
              <th>默认副本</th>
              <th>排序</th>
              <th>状态</th>
              <th>说明</th>
              <th>更新时间</th>
              {canWrite(user) && <th />}
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={row.id}>
                <td className="mono">{row.name}</td>
                <td>{row.displayName}</td>
                <td className="mono">{row.namespace}</td>
                <td>{row.defaultReplicas}</td>
                <td className="muted">{row.sortOrder}</td>
                <td>{row.enabled ? '启用' : '停用'}</td>
                <td className="muted">{row.description ?? '-'}</td>
                <td className="muted">{formatTime(row.updatedAt)}</td>
                {canWrite(user) && (
                  <td className="toolbar">
                    <button type="button" className="btn ghost small" onClick={() => startEdit(row)}>
                      编辑
                    </button>
                    <button
                      type="button"
                      className="btn ghost small"
                      onClick={() => void handleDelete(row)}
                    >
                      删除
                    </button>
                  </td>
                )}
              </tr>
            ))}
            {rows.length === 0 && (
              <tr>
                <td colSpan={canWrite(user) ? 9 : 8} className="empty">
                  暂无环境配置
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </>
  );
}
