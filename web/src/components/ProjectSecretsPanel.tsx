import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { api, errorMessage, formatTime, queryString } from '../api';
import { canWrite, isAdmin, useAuth } from '../auth-context';
import type { ProjectSecret } from '../types';

interface Props {
  projectId: number;
}

interface SecretForm {
  id: number | null;
  name: string;
  value: string;
  description: string;
}

const EMPTY_FORM: SecretForm = { id: null, name: '', value: '', description: '' };

/** 项目构建变量：值加密存储，列表只展示掩码，构建时注入容器环境变量。 */
export default function ProjectSecretsPanel({ projectId }: Props) {
  const { user } = useAuth();
  const [rows, setRows] = useState<ProjectSecret[]>([]);
  const [form, setForm] = useState<SecretForm>({ ...EMPTY_FORM });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      setRows(await api.get<ProjectSecret[]>(`/secrets${queryString({ projectId })}`));
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
    if (!form.id && !form.value.trim()) {
      setError('新增变量时必须填写值');
      return;
    }
    setBusy(true);
    const payload = {
      projectId,
      name: form.name.trim(),
      value: form.value || null,
      description: form.description.trim() || null,
    };
    try {
      if (form.id) {
        await api.put<number>(`/secrets/${form.id}`, payload);
        setNotice(`变量 ${payload.name} 已更新`);
      } else {
        await api.post<number>('/secrets', payload);
        setNotice(`变量 ${payload.name} 已创建`);
      }
      setForm({ ...EMPTY_FORM });
      await load();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  function startEdit(row: ProjectSecret) {
    setForm({ id: row.id, name: row.name, value: '', description: row.description ?? '' });
    setError(null);
    setNotice(null);
  }

  async function handleDelete(row: ProjectSecret) {
    if (
      !window.confirm(
        `确认删除变量「${row.name}」？删除后使用该变量的流水线将不再注入，可能导致构建失败。`,
      )
    ) {
      return;
    }
    setError(null);
    setNotice(null);
    try {
      await api.delete<void>(`/secrets/${row.id}`);
      setNotice(`变量 ${row.name} 已删除`);
      if (form.id === row.id) {
        setForm({ ...EMPTY_FORM });
      }
      await load();
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
            <h2>{form.id ? '编辑变量' : '新增变量'}</h2>
            <span className="muted">值以 AES-256-GCM 加密存储，接口与日志只出现掩码</span>
          </div>
          <div className="form-grid">
            <label className="field">
              <span>变量名</span>
              <input
                value={form.name}
                placeholder="NPM_TOKEN / DOCKER_USERNAME"
                onChange={(e) => setForm({ ...form, name: e.target.value })}
                required
              />
              <span className="field-hint">字母或下划线开头，仅允许字母、数字、下划线</span>
            </label>
            <label className="field">
              <span>{form.id ? '变量值（留空表示不修改）' : '变量值'}</span>
              <input
                type="password"
                value={form.value}
                placeholder={form.id ? '不修改请留空' : '输入变量值'}
                autoComplete="new-password"
                onChange={(e) => setForm({ ...form, value: e.target.value })}
                required={!form.id}
              />
            </label>
            <label className="field full">
              <span>说明</span>
              <input
                value={form.description}
                placeholder="该变量的用途，例如：前端私服 npm 发布令牌"
                onChange={(e) => setForm({ ...form, description: e.target.value })}
              />
            </label>
          </div>
          <div className="form-actions">
            <button type="submit" className="btn" disabled={busy}>
              {busy ? '提交中…' : form.id ? '保存修改' : '新增变量'}
            </button>
            {form.id && (
              <button type="button" className="btn ghost" onClick={() => setForm({ ...EMPTY_FORM })}>
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
              <th>变量名</th>
              <th>值</th>
              <th>说明</th>
              <th>更新时间</th>
              {canWrite(user) && <th />}
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={row.id}>
                <td className="mono">{row.name}</td>
                <td className="mono muted">{row.maskedValue}</td>
                <td className="muted">{row.description ?? '-'}</td>
                <td className="muted">{formatTime(row.updatedAt)}</td>
                {canWrite(user) && (
                  <td className="toolbar">
                    <button type="button" className="btn ghost small" onClick={() => startEdit(row)}>
                      编辑
                    </button>
                    {isAdmin(user) && (
                      <button
                        type="button"
                        className="btn ghost small"
                        onClick={() => void handleDelete(row)}
                      >
                        删除
                      </button>
                    )}
                  </td>
                )}
              </tr>
            ))}
            {rows.length === 0 && (
              <tr>
                <td colSpan={canWrite(user) ? 5 : 4} className="empty">
                  暂无构建变量
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </>
  );
}
