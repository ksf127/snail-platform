import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { api, errorMessage, formatTime, queryString } from '../api';
import { canWrite, isAdmin, useAuth } from '../auth-context';
import Pagination from '../components/Pagination';
import type { Member, PageView, Project, Role, User } from '../types';

const PAGE_SIZE = 10;
const MEMBER_PAGE_SIZE = 10;
const PICKER_PAGE_SIZE = 10;

export default function ProjectsPage() {
  const { user } = useAuth();
  const [projects, setProjects] = useState<PageView<Project> | null>(null);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(PAGE_SIZE);
  const [error, setError] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [key, setKey] = useState('');
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [selected, setSelected] = useState<Project | null>(null);
  const [members, setMembers] = useState<PageView<Member> | null>(null);
  const [memberKeyword, setMemberKeyword] = useState('');
  const [memberQuery, setMemberQuery] = useState('');
  const [memberPage, setMemberPage] = useState(1);
  const [memberSize, setMemberSize] = useState(MEMBER_PAGE_SIZE);
  const [memberKey, setMemberKey] = useState(0);
  const [memberRole, setMemberRole] = useState<Role>('DEVELOPER');
  const [selectedUsers, setSelectedUsers] = useState<User[]>([]);
  const [memberIds, setMemberIds] = useState<number[]>([]);
  const [batchBusy, setBatchBusy] = useState(false);
  const [selectedMemberIds, setSelectedMemberIds] = useState<number[]>([]);
  const [removeBusy, setRemoveBusy] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [pickerOpen, setPickerOpen] = useState(false);
  const [pickerKeyword, setPickerKeyword] = useState('');
  const [pickerPage, setPickerPage] = useState(1);
  const [pickerSize, setPickerSize] = useState(PICKER_PAGE_SIZE);
  const [pickerUsers, setPickerUsers] = useState<PageView<User> | null>(null);
  const [pickerBusy, setPickerBusy] = useState(false);
  const [pickerError, setPickerError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      setProjects(
        await api.get<PageView<Project>>(
          `/projects${queryString({ page, size, keyword, status })}`,
        ),
      );
      setError(null);
    } catch (err) {
      setError(errorMessage(err));
    }
  }, [page, size, keyword, status]);

  useEffect(() => {
    void load();
  }, [load]);

  // 列表变化时自动选中首个项目（当前选中项不在本页时也回落到首个）。
  useEffect(() => {
    const items = projects?.items ?? [];
    if (items.length === 0) {
      setSelected((current) => (current ? null : current));
      return;
    }
    setSelected((current) =>
      current && items.some((item) => item.id === current.id) ? current : items[0],
    );
  }, [projects]);

  // 选中项目后自动带出成员（分页 + 关键字），无需再点按钮。
  useEffect(() => {
    if (!selected) {
      setMembers(null);
      return;
    }
    let cancelled = false;
    (async () => {
      try {
        const result = await api.get<PageView<Member>>(
          `/projects/${selected.id}/members${queryString({
            page: memberPage,
            size: memberSize,
            keyword: memberQuery,
          })}`,
        );
        if (!cancelled) {
          setMembers(result);
        }
      } catch (err) {
        if (!cancelled) {
          setMembers(null);
          setError(errorMessage(err));
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [selected, memberPage, memberSize, memberQuery, memberKey]);

  // 切换项目时成员查询条件与页码归零，并清空待添加的勾选。
  useEffect(() => {
    setMemberKeyword('');
    setMemberQuery('');
    setMemberPage(1);
    setSelectedUsers([]);
    setSelectedMemberIds([]);
    setNotice(null);
  }, [selected]);

  // 成员列表刷新或翻页后，丢弃已不在当前页的勾选。
  useEffect(() => {
    const pageUserIds = (members?.items ?? []).map((item) => item.userId);
    setSelectedMemberIds((current) => current.filter((id) => pageUserIds.includes(id)));
  }, [members]);

  // 已加入成员的用户 ID：弹框中标记并禁用，避免重复添加。
  useEffect(() => {
    if (!selected) {
      setMemberIds([]);
      return;
    }
    let cancelled = false;
    (async () => {
      try {
        const ids = await api.get<number[]>(`/projects/${selected.id}/members/ids`);
        if (!cancelled) {
          setMemberIds(ids);
        }
      } catch {
        if (!cancelled) {
          setMemberIds([]);
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [selected, memberKey]);

  // 用户搜索弹框：关键字防抖 + 分页，覆盖全部用户。
  useEffect(() => {
    if (!pickerOpen) {
      return;
    }
    const timer = window.setTimeout(async () => {
      setPickerBusy(true);
      try {
        const result = await api.get<PageView<User>>(
          `/users${queryString({ page: pickerPage, size: pickerSize, keyword: pickerKeyword.trim() })}`,
        );
        setPickerUsers(result);
        setPickerError(null);
      } catch (err) {
        setPickerUsers(null);
        setPickerError(errorMessage(err));
      } finally {
        setPickerBusy(false);
      }
    }, 250);
    return () => window.clearTimeout(timer);
  }, [pickerOpen, pickerKeyword, pickerPage, pickerSize]);

  function applyFilters(event: FormEvent) {
    event.preventDefault();
    setPage(1);
    void load();
  }

  function resetFilters() {
    setKeyword('');
    setStatus('');
    setPage(1);
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
      setPage(1);
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

  async function handleAddMembers(event: FormEvent) {
    event.preventDefault();
    if (!selected || selectedUsers.length === 0) {
      return;
    }
    setError(null);
    setNotice(null);
    setBatchBusy(true);
    try {
      const added = await api.post<number>(`/projects/${selected.id}/members/batch`, {
        userIds: selectedUsers.map((item) => item.id),
        role: memberRole,
      });
      setSelectedUsers([]);
      setMemberKey((value) => value + 1);
      setNotice(
        added > 0 ? `已添加 ${added} 名成员（${memberRole}）` : '所选用户均已是项目成员',
      );
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBatchBusy(false);
    }
  }

  async function handleRemoveMembers() {
    if (!selected || selectedMemberIds.length === 0) {
      return;
    }
    if (
      !window.confirm(
        `确认将选中的 ${selectedMemberIds.length} 名成员移出项目 ${selected.projectKey}？`,
      )
    ) {
      return;
    }
    setError(null);
    setNotice(null);
    setRemoveBusy(true);
    try {
      const removed = await api.delete<number>(
        `/projects/${selected.id}/members/batch${queryString({
          userIds: selectedMemberIds.join(','),
        })}`,
      );
      setSelectedMemberIds([]);
      setMemberKey((value) => value + 1);
      setNotice(removed > 0 ? `已移除 ${removed} 名成员` : '所选成员不在该项目中');
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setRemoveBusy(false);
    }
  }

  function toggleMember(userId: number) {
    setSelectedMemberIds((current) =>
      current.includes(userId)
        ? current.filter((id) => id !== userId)
        : [...current, userId],
    );
  }

  function toggleAllMembers() {
    const pageUserIds = (members?.items ?? []).map((item) => item.userId);
    setSelectedMemberIds((current) =>
      pageUserIds.every((id) => current.includes(id)) ? [] : pageUserIds,
    );
  }

  function toggleUser(target: User) {
    if (memberIds.includes(target.id)) {
      return;
    }
    setSelectedUsers((current) =>
      current.some((item) => item.id === target.id)
        ? current.filter((item) => item.id !== target.id)
        : [...current, target],
    );
  }

  function openPicker() {
    setPickerKeyword('');
    setPickerPage(1);
    setPickerUsers(null);
    setPickerError(null);
    setPickerOpen(true);
  }

  function applyMemberFilters(event: FormEvent) {
    event.preventDefault();
    setMemberPage(1);
    setMemberQuery(memberKeyword.trim());
  }

  function closePicker() {
    setPickerOpen(false);
    setPickerError(null);
  }

  // 弹框当前页中尚未加入该项目的用户，用于「全选本页」与空态提示。
  const pickerItems = pickerUsers?.items ?? [];
  const pickerAvailable = pickerItems.filter((item) => !memberIds.includes(item.id));

  function selectPageAvailable() {
    setSelectedUsers((current) => {
      const merged = [...current];
      pickerAvailable.forEach((item) => {
        if (!merged.some((picked) => picked.id === item.id)) {
          merged.push(item);
        }
      });
      return merged;
    });
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
      {notice && <div className="alert info">{notice}</div>}

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
            <span className="muted">{projects?.total ?? 0} 个</span>
          </div>
          <form className="filters" onSubmit={applyFilters}>
            <input
              value={keyword}
              placeholder="搜索 Key / 名称"
              onChange={(e) => setKeyword(e.target.value)}
            />
            <select value={status} onChange={(e) => setStatus(e.target.value)}>
              <option value="">全部状态</option>
              <option value="ACTIVE">ACTIVE</option>
              <option value="ARCHIVED">ARCHIVED</option>
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
                  <th>Key</th>
                  <th>名称</th>
                  <th>状态</th>
                  <th>创建时间</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {(projects?.items ?? []).map((project) => (
                  <tr
                    key={project.id}
                    className={selected?.id === project.id ? 'clickable active' : 'clickable'}
                    onClick={() => setSelected(project)}
                  >
                    <td className="mono">
                      <Link
                        to={`/projects/${project.id}`}
                        className="link-btn"
                        onClick={(e) => e.stopPropagation()}
                      >
                        {project.projectKey}
                      </Link>
                    </td>
                    <td>{project.name}</td>
                    <td>{project.status}</td>
                    <td className="muted">{formatTime(project.createdAt)}</td>
                    <td className="toolbar">
                      {isAdmin(user) && (
                        <button
                          type="button"
                          className="btn danger small"
                          onClick={(e) => {
                            e.stopPropagation();
                            void handleDelete(project);
                          }}
                        >
                          删除
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
                {(projects?.items.length ?? 0) === 0 && (
                  <tr>
                    <td colSpan={5} className="empty">
                      暂无项目
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
          <Pagination
            page={projects}
            onPageChange={setPage}
            onSizeChange={(next) => {
              setSize(next);
              setPage(1);
            }}
          />
        </div>

        <div className="card">
          <div className="card-head">
            <h2>项目成员</h2>
            <span className="muted">
              {selected ? `${selected.projectKey} · ${members?.total ?? 0} 人` : '未选择'}
            </span>
          </div>
          {!selected && <div className="empty">暂无可选项目</div>}
          {selected && (
            <>
              <form className="filters" onSubmit={applyMemberFilters}>
                <input
                  value={memberKeyword}
                  placeholder="搜索用户名 / 显示名"
                  onChange={(e) => setMemberKeyword(e.target.value)}
                />
                <button type="submit" className="btn small">
                  查询
                </button>
                {memberQuery && (
                  <button
                    type="button"
                    className="btn ghost small"
                    onClick={() => {
                      setMemberKeyword('');
                      setMemberQuery('');
                      setMemberPage(1);
                    }}
                  >
                    重置
                  </button>
                )}
              </form>
              {canWrite(user) && (
                <div className="toolbar" style={{ marginBottom: 8 }}>
                  <button
                    type="button"
                    className="btn danger small"
                    disabled={selectedMemberIds.length === 0 || removeBusy}
                    onClick={() => void handleRemoveMembers()}
                  >
                    {removeBusy
                      ? '移除中…'
                      : selectedMemberIds.length > 0
                        ? `移除选中成员（${selectedMemberIds.length}）`
                        : '移除选中成员'}
                  </button>
                  {selectedMemberIds.length > 0 && (
                    <button
                      type="button"
                      className="btn ghost small"
                      onClick={() => setSelectedMemberIds([])}
                    >
                      清空选择
                    </button>
                  )}
                </div>
              )}
              <div className="table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th style={{ width: 40 }}>
                        <input
                          type="checkbox"
                          aria-label="全选本页成员"
                          checked={
                            (members?.items.length ?? 0) > 0 &&
                            (members?.items ?? []).every((item) =>
                              selectedMemberIds.includes(item.userId),
                            )
                          }
                          disabled={(members?.items.length ?? 0) === 0}
                          onChange={toggleAllMembers}
                        />
                      </th>
                      <th>用户</th>
                      <th>显示名</th>
                      <th>角色</th>
                    </tr>
                  </thead>
                  <tbody>
                    {(members?.items ?? []).map((member) => (
                      <tr key={member.id}>
                        <td>
                          <input
                            type="checkbox"
                            aria-label={`选择成员 ${member.username}`}
                            checked={selectedMemberIds.includes(member.userId)}
                            onChange={() => toggleMember(member.userId)}
                          />
                        </td>
                        <td>{member.username}</td>
                        <td>{member.displayName}</td>
                        <td>{member.role}</td>
                      </tr>
                    ))}
                    {(members?.items.length ?? 0) === 0 && (
                      <tr>
                        <td colSpan={4} className="empty">
                          {memberQuery ? '没有匹配的成员' : '暂无成员'}
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
              <Pagination
                page={members}
                onPageChange={setMemberPage}
                onSizeChange={(next) => {
                  setMemberSize(next);
                  setMemberPage(1);
                }}
              />
              {canWrite(user) && (
                <form className="inline-form" style={{ marginTop: 14 }} onSubmit={handleAddMembers}>
                  <label className="field">
                    <span>用户（可多选）</span>
                    <button type="button" className="btn ghost picker-trigger" onClick={openPicker}>
                      {selectedUsers.length > 0
                        ? `已选 ${selectedUsers.length} 人，继续选择`
                        : '搜索并选择用户'}
                    </button>
                  </label>
                  <label className="field">
                    <span>角色</span>
                    <select value={memberRole} onChange={(e) => setMemberRole(e.target.value as Role)}>
                      <option value="ADMIN">ADMIN</option>
                      <option value="DEVELOPER">DEVELOPER</option>
                      <option value="VIEWER">VIEWER</option>
                    </select>
                  </label>
                  <button
                    type="submit"
                    className="btn"
                    disabled={selectedUsers.length === 0 || batchBusy}
                  >
                    {batchBusy
                      ? '添加中…'
                      : selectedUsers.length > 0
                        ? `添加 ${selectedUsers.length} 人`
                        : '添加成员'}
                  </button>
                </form>
              )}
              {canWrite(user) && selectedUsers.length > 0 && (
                <div className="picked-chips">
                  {selectedUsers.map((item) => (
                    <span className="picked-chip" key={item.id}>
                      {item.displayName}
                      <button type="button" className="link-btn" onClick={() => toggleUser(item)}>
                        移除
                      </button>
                    </span>
                  ))}
                </div>
              )}
            </>
          )}
        </div>
      </div>

      {pickerOpen && (
        <div className="modal-mask" onClick={closePicker}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <div className="modal-head">
              <h2>选择用户</h2>
              <button type="button" className="btn ghost small" onClick={closePicker}>
                关闭
              </button>
            </div>
            <input
              autoFocus
              value={pickerKeyword}
              placeholder="输入用户名 / 显示名搜索"
              onChange={(e) => {
                setPickerKeyword(e.target.value);
                setPickerPage(1);
              }}
            />
            {pickerError && <div className="alert">{pickerError}</div>}
            {!pickerBusy && pickerItems.length > 0 && pickerAvailable.length === 0 && (
              <div className="alert info" style={{ marginTop: 12 }}>
                当前查询结果中的 {pickerItems.length} 位用户均已加入该项目，无需重复添加。
                可换个关键字搜索其他用户。
              </div>
            )}
            <div className="user-options">
              {pickerItems.map((item) => {
                const picked = selectedUsers.some((pickedItem) => pickedItem.id === item.id);
                const joined = memberIds.includes(item.id);
                return (
                  <button
                    key={item.id}
                    type="button"
                    className={picked ? 'user-option picked' : 'user-option'}
                    disabled={joined}
                    onClick={() => toggleUser(item)}
                  >
                    <span>
                      {picked ? '✓ ' : ''}
                      {item.displayName}
                    </span>
                    <span className="muted">
                      {item.username} · #{item.id} · {item.role}
                      {joined ? ' · 已加入' : ''}
                    </span>
                  </button>
                );
              })}
              {!pickerBusy && (pickerUsers?.items.length ?? 0) === 0 && (
                <div className="empty">没有匹配的用户</div>
              )}
              {pickerBusy && <div className="empty">搜索中…</div>}
            </div>
            <Pagination
              page={pickerUsers}
              onPageChange={setPickerPage}
              onSizeChange={(next) => {
                setPickerSize(next);
                setPickerPage(1);
              }}
            />
            <div className="modal-actions">
              <span className="muted">
                已选 {selectedUsers.length} 人 · 本页可添加 {pickerAvailable.length} 人
              </span>
              <div className="toolbar">
                {pickerAvailable.length > 0 && (
                  <button type="button" className="btn ghost small" onClick={selectPageAvailable}>
                    全选本页
                  </button>
                )}
                <button
                  type="button"
                  className="btn ghost small"
                  disabled={selectedUsers.length === 0}
                  onClick={() => setSelectedUsers([])}
                >
                  清空
                </button>
                <button type="button" className="btn small" onClick={closePicker}>
                  完成
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
