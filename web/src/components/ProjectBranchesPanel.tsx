import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { api, errorMessage, formatTime, queryString } from '../api';
import { canWrite, useAuth } from '../auth-context';
import type { PageView, ProjectBranch, User } from '../types';

interface Props {
  projectId: number;
  /** 分支归属变化时通知外层重新拉取流水线团队列。 */
  onChanged?: () => void;
}

interface BranchForm {
  id: number | null;
  branch: string;
  team: string;
  ownerId: string;
  description: string;
}

const EMPTY_FORM: BranchForm = { id: null, branch: '', team: '', ownerId: '', description: '' };

/** 分支团队：同一项目的不同分支可归属不同团队与负责人。 */
export default function ProjectBranchesPanel({ projectId, onChanged }: Props) {
  const { user } = useAuth();
  const [rows, setRows] = useState<ProjectBranch[]>([]);
  const [users, setUsers] = useState<User[]>([]);
  const [form, setForm] = useState<BranchForm>({ ...EMPTY_FORM });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      setRows(await api.get<ProjectBranch[]>(`/branches${queryString({ projectId })}`));
    } catch (err) {
      setRows([]);
      setError(errorMessage(err));
    }
  }, [projectId]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    async function loadUsers() {
      try {
        const page = await api.get<PageView<User>>(`/users${queryString({ page: 1, size: 100 })}`);
        setUsers(page.items ?? []);
      } catch {
        setUsers([]);
      }
    }
    void loadUsers();
  }, []);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setNotice(null);
    setBusy(true);
    const payload = {
      projectId,
      branch: form.branch.trim(),
      team: form.team.trim() || null,
      ownerId: form.ownerId ? Number(form.ownerId) : null,
      description: form.description.trim() || null,
    };
    try {
      if (form.id) {
        await api.put<ProjectBranch>(`/branches/${form.id}`, payload);
        setNotice(`分支 ${payload.branch} 已更新，相关流水线分支同步调整`);
      } else {
        await api.post<ProjectBranch>('/branches', payload);
        setNotice(`分支 ${payload.branch} 已登记`);
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

  function startEdit(row: ProjectBranch) {
    setForm({
      id: row.id,
      branch: row.branch,
      team: row.team ?? '',
      ownerId: row.ownerId ? String(row.ownerId) : '',
      description: row.description ?? '',
    });
    setError(null);
    setNotice(null);
  }

  async function handleDelete(row: ProjectBranch) {
    if (!window.confirm(`确认删除分支登记「${row.branch}」？流水线本身不会被删除。`)) {
      return;
    }
    setError(null);
    setNotice(null);
    try {
      await api.delete<void>(`/branches/${row.id}`);
      setNotice(`分支 ${row.branch} 已删除`);
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
            <h2>{form.id ? '编辑分支归属' : '登记分支归属'}</h2>
            <span className="muted">分支创建后，流水线使用该分支会自动登记</span>
          </div>
          <div className="form-grid">
            <label className="field">
              <span>分支</span>
              <input
                value={form.branch}
                placeholder="main / develop / release/*"
                onChange={(e) => setForm({ ...form, branch: e.target.value })}
                required
              />
            </label>
            <label className="field">
              <span>维护团队</span>
              <input
                value={form.team}
                placeholder="如：交易组"
                onChange={(e) => setForm({ ...form, team: e.target.value })}
              />
            </label>
            <label className="field">
              <span>负责人</span>
              <select
                value={form.ownerId}
                onChange={(e) => setForm({ ...form, ownerId: e.target.value })}
              >
                <option value="">未指定</option>
                {users.map((item) => (
                  <option key={item.id} value={item.id}>
                    {item.displayName}（{item.username}）
                  </option>
                ))}
              </select>
            </label>
            <label className="field full">
              <span>说明</span>
              <input
                value={form.description}
                placeholder="该分支负责的业务范围、发布节奏等"
                onChange={(e) => setForm({ ...form, description: e.target.value })}
              />
            </label>
          </div>
          <div className="form-actions">
            <button type="submit" className="btn" disabled={busy}>
              {busy ? '提交中…' : form.id ? '保存修改' : '登记分支'}
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
              <th>分支</th>
              <th>维护团队</th>
              <th>负责人</th>
              <th>说明</th>
              <th>更新时间</th>
              {canWrite(user) && <th />}
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={row.id}>
                <td className="mono">{row.branch}</td>
                <td>{row.team ?? '-'}</td>
                <td>{row.ownerName ?? '-'}</td>
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
                <td colSpan={canWrite(user) ? 6 : 5} className="empty">
                  暂无分支归属，创建流水线或在上方登记后展示
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </>
  );
}
