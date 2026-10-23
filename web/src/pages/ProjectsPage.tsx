import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { api, errorMessage, formatTime } from '../api';
import { canWrite, isAdmin, useAuth } from '../auth-context';
import type { Member, Project, Role } from '../types';

export default function ProjectsPage() {
  const { user } = useAuth();
  const [projects, setProjects] = useState<Project[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [key, setKey] = useState('');
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [selected, setSelected] = useState<Project | null>(null);
  const [members, setMembers] = useState<Member[]>([]);
  const [memberUserId, setMemberUserId] = useState('');
  const [memberRole, setMemberRole] = useState<Role>('DEVELOPER');

  const load = useCallback(async () => {
    try {
      setProjects(await api.get<Project[]>('/projects'));
      setError(null);
    } catch (err) {
      setError(errorMessage(err));
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  async function loadMembers(project: Project) {
    setSelected(project);
    try {
      setMembers(await api.get<Member[]>(`/projects/${project.id}/members`));
    } catch (err) {
      setError(errorMessage(err));
    }
  }

  async function handleCreate(event: FormEvent) {
    event.preventDefault();
    setError(null);
    try {
      await api.post<Project>('/projects', { key: key.trim(), name: name.trim(), description });
      setKey('');
      setName('');
      setDescription('');
      setShowForm(false);
      await load();
    } catch (err) {
      setError(errorMessage(err));
    }
  }

  async function handleDelete(project: Project) {
    if (!window.confirm(`确认删除项目 ${project.projectKey}？关联仓库与流水线会一并删除。`)) {
      return;
    }
    try {
      await api.delete<void>(`/projects/${project.id}`);
      if (selected?.id === project.id) {
        setSelected(null);
      }
      await load();
    } catch (err) {
      setError(errorMessage(err));
    }
  }

  async function handleAddMember(event: FormEvent) {
    event.preventDefault();
    if (!selected) {
      return;
    }
    setError(null);
    try {
      await api.post<Member>(`/projects/${selected.id}/members`, {
        userId: Number(memberUserId),
        role: memberRole,
      });
      setMemberUserId('');
      await loadMembers(selected);
    } catch (err) {
      setError(errorMessage(err));
    }
  }

  return (
    <>
      <div className="page-head">
        <div>
          <h1>项目</h1>
          <p>以项目为边界组织仓库、流水线与成员</p>
        </div>
        {canWrite(user) && (
          <button type="button" className="btn" onClick={() => setShowForm((v) => !v)}>
            {showForm ? '收起' : '新建项目'}
          </button>
        )}
      </div>

      {error && <div className="alert">{error}</div>}

      {showForm && (
        <form className="card" onSubmit={handleCreate}>
          <div className="card-head">
            <h2>新建项目</h2>
          </div>
          <div className="form-grid">
            <label className="field">
              <span>项目 Key</span>
              <input
                value={key}
                placeholder="例如 PAYMENT"
                onChange={(e) => setKey(e.target.value)}
                required
              />
            </label>
            <label className="field">
              <span>项目名称</span>
              <input value={name} onChange={(e) => setName(e.target.value)} required />
            </label>
            <label className="field full">
              <span>描述</span>
              <input value={description} onChange={(e) => setDescription(e.target.value)} />
            </label>
          </div>
          <div className="form-actions">
            <button type="submit" className="btn">
              创建
            </button>
          </div>
        </form>
      )}

      <div className="split">
        <div className="card">
          <div className="card-head">
            <h2>全部项目</h2>
            <span className="muted">{projects.length} 个</span>
          </div>
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Key</th>
                  <th>名称</th>
                  <th>状态</th>
                  <th>创建时间</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {projects.map((project) => (
                  <tr key={project.id}>
                    <td className="mono">{project.projectKey}</td>
                    <td>{project.name}</td>
                    <td>{project.status}</td>
                    <td className="muted">{formatTime(project.createdAt)}</td>
                    <td className="toolbar">
                      <button
                        type="button"
                        className="btn ghost small"
                        onClick={() => void loadMembers(project)}
                      >
                        成员
                      </button>
                      {isAdmin(user) && (
                        <button
                          type="button"
                          className="btn danger small"
                          onClick={() => void handleDelete(project)}
                        >
                          删除
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
                {projects.length === 0 && (
                  <tr>
                    <td colSpan={5} className="empty">
                      暂无项目
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>

        <div className="card">
          <div className="card-head">
            <h2>项目成员</h2>
            <span className="muted">{selected ? selected.projectKey : '未选择'}</span>
          </div>
          {!selected && <div className="empty">在左侧选择项目查看成员</div>}
          {selected && (
            <>
              <div className="table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>用户</th>
                      <th>显示名</th>
                      <th>角色</th>
                    </tr>
                  </thead>
                  <tbody>
                    {members.map((member) => (
                      <tr key={member.id}>
                        <td>{member.username}</td>
                        <td>{member.displayName}</td>
                        <td>{member.role}</td>
                      </tr>
                    ))}
                    {members.length === 0 && (
                      <tr>
                        <td colSpan={3} className="empty">
                          暂无成员
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
              {canWrite(user) && (
                <form className="inline-form" style={{ marginTop: 14 }} onSubmit={handleAddMember}>
                  <label className="field">
                    <span>用户 ID</span>
                    <input
                      type="number"
                      min="1"
                      value={memberUserId}
                      onChange={(e) => setMemberUserId(e.target.value)}
                      required
                    />
                  </label>
                  <label className="field">
                    <span>角色</span>
                    <select value={memberRole} onChange={(e) => setMemberRole(e.target.value as Role)}>
                      <option value="ADMIN">ADMIN</option>
                      <option value="DEVELOPER">DEVELOPER</option>
                      <option value="VIEWER">VIEWER</option>
                    </select>
                  </label>
                  <button type="submit" className="btn">
                    添加 / 更新
                  </button>
                </form>
              )}
            </>
          )}
        </div>
      </div>
    </>
  );
}
