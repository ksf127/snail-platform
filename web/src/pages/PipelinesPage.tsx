import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { api, errorMessage, formatTime, queryString } from '../api';
import { canWrite, isAdmin, useAuth } from '../auth-context';
import Pagination from '../components/Pagination';
import type { PageView, Pipeline, PipelineSide, PipelineStep, PipelineTemplateView, PipelineYamlView, Project, ProjectBranch, Repo, Run } from '../types';

const DEFAULT_DEFINITION = {
  steps: [
    {
      name: 'build',
      image: 'alpine:3.20',
      commands: ['echo "hello from pipeline"'],
      workDir: '/workspace',
      artifacts: [],
    },
  ],
};

const EMPTY_FORM = {
  projectId: '',
  repoId: '',
  name: '',
  branch: 'main',
  triggerType: 'MANUAL',
  executorType: 'DOCKER',
  side: 'BACKEND' as PipelineSide,
  definitionJson: JSON.stringify(DEFAULT_DEFINITION, null, 2),
  configPath: '',
  enabled: true,
};

interface Filters {
  keyword: string;
  projectId: string;
  repoId: string;
  side: string;
  triggerType: string;
  executorType: string;
  enabled: string;
  team: string;
}

const EMPTY_FILTERS: Filters = {
  keyword: '',
  projectId: '',
  repoId: '',
  side: '',
  triggerType: '',
  executorType: '',
  enabled: '',
  team: '',
};

const TRIGGER_LABELS: Record<string, string> = { MANUAL: '手动', WEBHOOK: 'Webhook' };
const EXECUTOR_LABELS: Record<string, string> = { DOCKER: 'Docker', K8S: 'K8s', JENKINS: 'Jenkins' };
const SIDE_LABELS: Record<string, string> = { FRONTEND: '前端', BACKEND: '后端' };

type GenMode = 'BACKEND' | 'FRONTEND' | 'BOTH';

const GEN_MODES: { value: GenMode; label: string }[] = [
  { value: 'BACKEND', label: '后端' },
  { value: 'FRONTEND', label: '前端' },
  { value: 'BOTH', label: '前后端' },
];

export default function PipelinesPage() {
  const { user } = useAuth();
  const [projects, setProjects] = useState<Project[]>([]);
  const [repos, setRepos] = useState<Repo[]>([]);
  const [branches, setBranches] = useState<ProjectBranch[]>([]);
  const [pipelines, setPipelines] = useState<PageView<Pipeline> | null>(null);
  const [draft, setDraft] = useState<Filters>(EMPTY_FILTERS);
  const [applied, setApplied] = useState<Filters>(EMPTY_FILTERS);
  const [queryKey, setQueryKey] = useState(0);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(10);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [form, setForm] = useState({ ...EMPTY_FORM });
  const [yamlText, setYamlText] = useState('');
  const [yamlBusy, setYamlBusy] = useState(false);
  const [yamlSource, setYamlSource] = useState<string | null>(null);
  const [triggering, setTriggering] = useState<Pipeline | null>(null);
  const [triggerBranch, setTriggerBranch] = useState('');
  const [triggerSha, setTriggerSha] = useState('');
  const [templateStack, setTemplateStack] = useState('AUTO');
  const [templateSource, setTemplateSource] = useState<string | null>(null);
  const [templateBusy, setTemplateBusy] = useState(false);
  const [frontendDir, setFrontendDir] = useState('web');
  const [genMode, setGenMode] = useState<GenMode>('BOTH');
  // 记录上一次自动带出的名称，用户改过名称后不再覆盖
  const suggestedName = useRef<string | null>(null);

  useEffect(() => {
    void (async () => {
      try {
        const [projectPage, repoPage, branchRows] = await Promise.all([
          api.get<PageView<Project>>(`/projects${queryString({ page: 1, size: 100 })}`),
          api.get<PageView<Repo>>(`/repos${queryString({ page: 1, size: 100 })}`),
          api.get<ProjectBranch[]>('/branches'),
        ]);
        setProjects(projectPage.items);
        setRepos(repoPage.items);
        setBranches(branchRows ?? []);
      } catch (err) {
        setError(errorMessage(err));
      }
    })();
  }, []);

  const load = useCallback(async () => {
    try {
      const pipelinePage = await api.get<PageView<Pipeline>>(
        `/pipelines${queryString({
          page,
          size,
          projectId: applied.projectId,
          repoId: applied.repoId,
          keyword: applied.keyword,
          side: applied.side,
          triggerType: applied.triggerType,
          executorType: applied.executorType,
          enabled: applied.enabled === '' ? undefined : applied.enabled === 'true',
          team: applied.team,
        })}`,
      );
      setPipelines(pipelinePage);
      setError(null);
    } catch (err) {
      setError(errorMessage(err));
    }
  }, [applied, page, size, queryKey]);

  useEffect(() => {
    void load();
  }, [load]);

  // 新建时选择仓库后自动生成步骤模板；「前后端」模式由按钮一次生成两条，这里不生成
  useEffect(() => {
    if (!showForm || editingId !== null || !form.repoId || genMode === 'BOTH') {
      return;
    }
    void applyTemplate(genMode, templateStack, form.repoId);
  }, [showForm, editingId, form.repoId, genMode, templateStack]);

  async function applyTemplate(mode: Exclude<GenMode, 'BOTH'>, stack: string, repoIdOverride?: string) {
    setTemplateBusy(true);
    setError(null);
    try {
      // 用户手动改过名称才传，避免切换生成方式时沿用上一条流水线的名字
      const currentName = form.name.trim();
      const typedName = currentName && currentName !== suggestedName.current ? currentName : undefined;
      const previousSuggested = suggestedName.current;
      const view = await api.get<PipelineTemplateView>(
        `/pipelines/templates/suggest${queryString({
          repoId: repoIdOverride ?? form.repoId,
          stack: mode === 'FRONTEND' ? undefined : stack,
          name: typedName,
          side: mode,
          frontendDir: mode === 'FRONTEND' ? frontendDir.trim() || 'web' : undefined,
        })}`,
      );
      setForm((current) => {
        const untouched = !current.name.trim() || current.name === previousSuggested;
        return {
          ...current,
          name: untouched ? view.name : current.name,
          branch: repoIdOverride ? view.branch : current.branch.trim() || view.branch,
          side: view.side,
          triggerType: view.triggerType,
          executorType: view.executorType,
          definitionJson: JSON.stringify(JSON.parse(view.definitionJson), null, 2),
        };
      });
      suggestedName.current = view.name;
      setTemplateSource(view.source);
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setTemplateBusy(false);
    }
  }

  // 单仓（前后端同仓）：一次生成前端、后端两条流水线
  async function handleCreateMonorepo() {
    setError(null);
    setNotice(null);
    if (!form.projectId) {
      setError('请先选择所属项目');
      return;
    }
    if (!form.repoId) {
      setError('请先选择关联仓库');
      return;
    }
    setTemplateBusy(true);
    try {
      const items = await api.post<Pipeline[]>('/pipelines/templates/monorepo', {
        projectId: Number(form.projectId),
        repoId: Number(form.repoId),
        branch: form.branch.trim() || undefined,
        triggerType: form.triggerType,
        frontendDir: frontendDir.trim() || 'web',
        backendStack: templateStack === 'AUTO' ? undefined : templateStack,
      });
      setShowForm(false);
      setNotice(`已生成 ${items.length} 条流水线：${items.map((item) => item.name).join('、')}`);
      await load();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setTemplateBusy(false);
    }
  }

  const formRepos = useMemo(
    () => repos.filter((repo) => !form.projectId || repo.projectId === Number(form.projectId)),
    [repos, form.projectId],
  );

  const draftRepos = useMemo(
    () => repos.filter((repo) => !draft.projectId || repo.projectId === Number(draft.projectId)),
    [repos, draft.projectId],
  );

  const teamOptions = useMemo(() => {
    const values = new Set<string>();
    branches.forEach((item) => {
      if (item.team) {
        values.add(item.team);
      }
    });
    return Array.from(values).sort();
  }, [branches]);

  const formBranchOptions = useMemo(() => {
    const values = new Set<string>();
    branches
      .filter((item) => !form.projectId || item.projectId === Number(form.projectId))
      .forEach((item) => values.add(item.branch));
    return Array.from(values).sort();
  }, [branches, form.projectId]);

  const activeFilters = useMemo(() => {
    const chips: { key: keyof Filters; label: string }[] = [];
    if (applied.keyword) chips.push({ key: 'keyword', label: `关键字：${applied.keyword}` });
    if (applied.projectId) {
      const project = projects.find((item) => item.id === Number(applied.projectId));
      chips.push({ key: 'projectId', label: `项目：${project?.projectKey ?? `#${applied.projectId}`}` });
    }
    if (applied.repoId) {
      const repo = repos.find((item) => item.id === Number(applied.repoId));
      chips.push({ key: 'repoId', label: `仓库：${repo?.name ?? `#${applied.repoId}`}` });
    }
    if (applied.side) chips.push({ key: 'side', label: `端：${SIDE_LABELS[applied.side] ?? applied.side}` });
    if (applied.triggerType) {
      chips.push({ key: 'triggerType', label: `触发：${TRIGGER_LABELS[applied.triggerType] ?? applied.triggerType}` });
    }
    if (applied.executorType) {
      chips.push({ key: 'executorType', label: `执行器：${EXECUTOR_LABELS[applied.executorType] ?? applied.executorType}` });
    }
    if (applied.enabled) chips.push({ key: 'enabled', label: applied.enabled === 'true' ? '状态：启用' : '状态：禁用' });
    if (applied.team) chips.push({ key: 'team', label: `团队：${applied.team}` });
    return chips;
  }, [applied, repos, projects]);

  const parsedSteps = useMemo<PipelineStep[] | null>(() => {
    if (!form.definitionJson.trim()) {
      return null;
    }
    try {
      const parsed = JSON.parse(form.definitionJson) as { steps?: PipelineStep[] };
      return Array.isArray(parsed.steps) ? parsed.steps : null;
    } catch {
      return null;
    }
  }, [form.definitionJson]);

  function applyFilters(event?: { preventDefault: () => void }) {
    event?.preventDefault();
    setApplied({ ...draft, keyword: draft.keyword.trim() });
    setPage(1);
    setQueryKey((value) => value + 1);
  }

  function resetFilters() {
    setDraft(EMPTY_FILTERS);
    setApplied(EMPTY_FILTERS);
    setPage(1);
    setQueryKey((value) => value + 1);
  }

  function clearFilter(key: keyof Filters) {
    setDraft((value) => ({ ...value, [key]: '' }));
    setApplied((value) => ({ ...value, [key]: '', ...(key === 'projectId' ? { repoId: '' } : {}) }));
    setPage(1);
  }

  function updateDraft(patch: Partial<Filters>) {
    setDraft((value) => ({ ...value, ...patch }));
  }

  function startCreate() {
    setEditingId(null);
    setForm({ ...EMPTY_FORM });
    setYamlText('');
    setYamlSource(null);
    setTemplateStack('AUTO');
    setTemplateSource(null);
    suggestedName.current = null;
    setShowForm(true);
  }

  function startEdit(pipeline: Pipeline) {
    setEditingId(pipeline.id);
    setTemplateSource(null);
    setYamlText('');
    setYamlSource(null);
    setForm({
      projectId: String(pipeline.projectId),
      repoId: pipeline.repoId ? String(pipeline.repoId) : '',
      name: pipeline.name,
      branch: pipeline.branch,
      triggerType: pipeline.triggerType,
      executorType: pipeline.executorType,
      side: pipeline.side,
      definitionJson: pipeline.definitionJson,
      configPath: pipeline.configPath ?? '',
      enabled: pipeline.enabled,
    });
    setShowForm(true);
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (editingId === null && genMode === 'BOTH') {
      await handleCreateMonorepo();
      return;
    }
    setError(null);
    setNotice(null);
    const useRepoConfig = Boolean(form.configPath.trim());
    let definitionJson = '';
    if (!useRepoConfig || form.definitionJson.trim()) {
      let parsed: { steps?: PipelineStep[] };
      try {
        parsed = JSON.parse(form.definitionJson || '{}') as { steps?: PipelineStep[] };
      } catch {
        setError('流水线定义不是合法 JSON');
        return;
      }
      if (!parsed.steps || parsed.steps.length === 0) {
        setError('流水线至少需要一个 step，或填写仓库内配置路径');
        return;
      }
      definitionJson = JSON.stringify(parsed);
    }
    try {
      const body = {
        projectId: Number(form.projectId),
        repoId: form.repoId ? Number(form.repoId) : null,
        name: form.name.trim(),
        branch: form.branch.trim(),
        triggerType: form.triggerType,
        executorType: form.executorType,
        side: form.side,
        definitionJson,
        configPath: form.configPath.trim() || null,
        enabled: form.enabled,
      };
      if (editingId) {
        await api.put<Pipeline>(`/pipelines/${editingId}`, body);
        setNotice('流水线已更新');
      } else {
        await api.post<Pipeline>('/pipelines', body);
        setNotice('流水线已创建');
      }
      setShowForm(false);
      await load();
    } catch (err) {
      setError(errorMessage(err));
    }
  }

  async function handleDelete(pipeline: Pipeline) {
    if (!window.confirm(`确认删除流水线 ${pipeline.name}？`)) {
      return;
    }
    try {
      await api.delete<void>(`/pipelines/${pipeline.id}`);
      await load();
    } catch (err) {
      setError(errorMessage(err));
    }
  }

  function openTrigger(pipeline: Pipeline) {
    setTriggering(pipeline);
    setTriggerBranch(pipeline.branch);
    setTriggerSha('');
    setError(null);
    setNotice(null);
  }

  async function handleTrigger(event: FormEvent) {
    event.preventDefault();
    if (!triggering) {
      return;
    }
    try {
      const run = await api.post<Run>(`/pipelines/${triggering.id}/trigger`, {
        branch: triggerBranch.trim(),
        commitSha: triggerSha.trim() || null,
      });
      setTriggering(null);
      setNotice(`已触发构建 #${run.buildNo}（runId=${run.id}），可前往运行记录查看`);
    } catch (err) {
      setError(errorMessage(err));
    }
  }

  async function loadYaml() {
    if (!editingId) {
      return;
    }
    setYamlBusy(true);
    try {
      const view = await api.get<PipelineYamlView>(`/pipelines/${editingId}/yaml`);
      setYamlText(view.yaml);
      setYamlSource(view.source);
      setError(null);
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setYamlBusy(false);
    }
  }

  async function importYaml() {
    if (!editingId) {
      return;
    }
    if (!yamlText.trim()) {
      setError('请先粘贴或读取 YAML 内容');
      return;
    }
    setYamlBusy(true);
    try {
      const view = await api.put<PipelineYamlView>(`/pipelines/${editingId}/yaml`, { yaml: yamlText });
      setForm((value) => ({ ...value, definitionJson: view.definitionJson }));
      setYamlSource(view.source);
      setNotice('YAML 已导入，步骤定义已更新');
      setError(null);
      await load();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setYamlBusy(false);
    }
  }

  async function syncYaml() {
    if (!editingId) {
      return;
    }
    if (!form.configPath.trim()) {
      setError('请先填写配置路径（例如 .snail/pipeline.yml）');
      return;
    }
    setYamlBusy(true);
    try {
      // 同步使用已保存的配置路径，先落库再读取远端 YAML。
      await api.put<Pipeline>(`/pipelines/${editingId}`, {
        projectId: Number(form.projectId),
        repoId: form.repoId ? Number(form.repoId) : null,
        name: form.name.trim(),
        branch: form.branch.trim(),
        triggerType: form.triggerType,
        executorType: form.executorType,
        side: form.side,
        definitionJson: form.definitionJson.trim() || '',
        configPath: form.configPath.trim(),
        enabled: form.enabled,
      });
      const view = await api.post<PipelineYamlView>(`/pipelines/${editingId}/yaml/sync`);
      setYamlText(view.yaml);
      setForm((value) => ({ ...value, definitionJson: view.definitionJson }));
      setYamlSource(view.source);
      setNotice(`已从 ${view.source} 同步配置`);
      setError(null);
      await load();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setYamlBusy(false);
    }
  }

  function formatJson() {
    try {
      setForm({ ...form, definitionJson: JSON.stringify(JSON.parse(form.definitionJson), null, 2) });
      setError(null);
    } catch {
      setError('流水线定义不是合法 JSON，无法格式化');
    }
  }

  function projectLabel(projectId: number): string {
    return projects.find((project) => project.id === projectId)?.projectKey ?? `#${projectId}`;
  }

  return (
    <>
      <div className="page-head">
        <div>
          <h1>流水线</h1>
          <p>定义构建步骤，手工触发或由 Gitea push 事件触发</p>
        </div>
        {canWrite(user) && (
          <button type="button" className="btn" onClick={startCreate}>
            新建流水线
          </button>
        )}
      </div>

      {error && <div className="alert">{error}</div>}
      {notice && <div className="alert info">{notice}</div>}

      {showForm && (
        <form className="card" onSubmit={handleSubmit}>
          <div className="card-head">
            <h2>{editingId ? '编辑流水线' : '新建流水线'}</h2>
            <button type="button" className="btn ghost small" onClick={() => setShowForm(false)}>
              取消
            </button>
          </div>
          <div className="form-grid">
            <label className="field">
              <span>所属项目</span>
              <select
                value={form.projectId}
                onChange={(e) => setForm({ ...form, projectId: e.target.value, repoId: '' })}
                required
                disabled={editingId !== null}
              >
                <option value="">请选择</option>
                {projects.map((project) => (
                  <option key={project.id} value={project.id}>
                    {project.projectKey} · {project.name}
                  </option>
                ))}
              </select>
            </label>
            <label className="field">
              <span>关联仓库</span>
              <select value={form.repoId} onChange={(e) => setForm({ ...form, repoId: e.target.value })}>
                <option value="">不绑定</option>
                {formRepos.map((repo) => (
                  <option key={repo.id} value={repo.id}>
                    {repo.name}（{repo.provider}）
                  </option>
                ))}
              </select>
            </label>
            <label className="field">
              <span>流水线名称</span>
              <input
                value={form.name}
                onChange={(e) => setForm({ ...form, name: e.target.value })}
                required
              />
            </label>
            <label className="field">
              <span>分支（支持 *）</span>
              <input
                list="pipeline-branch-options"
                value={form.branch}
                onChange={(e) => setForm({ ...form, branch: e.target.value })}
                required
              />
              <datalist id="pipeline-branch-options">
                {formBranchOptions.map((item) => (
                  <option key={item} value={item} />
                ))}
              </datalist>
            </label>
            {(editingId !== null || genMode !== 'BOTH') && (
              <label className="field">
                <span>所属端</span>
                <select
                  value={form.side}
                  onChange={(e) => setForm({ ...form, side: e.target.value as PipelineSide })}
                >
                  <option value="BACKEND">后端</option>
                  <option value="FRONTEND">前端</option>
                </select>
              </label>
            )}
            <label className="field">
              <span>触发方式</span>
              <select
                value={form.triggerType}
                onChange={(e) => setForm({ ...form, triggerType: e.target.value })}
              >
                <option value="MANUAL">MANUAL</option>
                <option value="WEBHOOK">WEBHOOK</option>
              </select>
            </label>
            <label className="field">
              <span>执行器</span>
              <select
                value={form.executorType}
                onChange={(e) => setForm({ ...form, executorType: e.target.value })}
              >
                <option value="DOCKER">DOCKER</option>
                <option value="K8S">K8S</option>
                <option value="JENKINS">JENKINS</option>
              </select>
            </label>
            <label className="field full">
              <span>配置路径（可选，配置即代码）</span>
              <input
                value={form.configPath}
                placeholder=".snail/pipeline.yml"
                onChange={(e) => setForm({ ...form, configPath: e.target.value })}
              />
              <span className="muted field-hint">
                填写后，构建时以仓库该分支/提交中的 YAML 为准；留空则使用下方平台内步骤定义。
              </span>
            </label>
            <label className="field checkbox">
              <input
                type="checkbox"
                checked={form.enabled}
                onChange={(e) => setForm({ ...form, enabled: e.target.checked })}
              />
              <span>启用</span>
            </label>
            {editingId === null && (
              <div className="field full">
                <span>生成方式</span>
                <div className="inline-form">
                  <select
                    value={genMode}
                    onChange={(e) => setGenMode(e.target.value as GenMode)}
                    title="后端/前端各生成一条流水线；前后端一次生成两条"
                  >
                    {GEN_MODES.map((mode) => (
                      <option key={mode.value} value={mode.value}>
                        {mode.label}
                      </option>
                    ))}
                  </select>
                  {genMode !== 'FRONTEND' && (
                    <select
                      value={templateStack}
                      onChange={(e) => setTemplateStack(e.target.value)}
                      title="后端技术栈"
                    >
                      <option value="AUTO">自动识别技术栈</option>
                      <option value="MAVEN">Maven</option>
                      <option value="GRADLE">Gradle</option>
                    </select>
                  )}
                  {genMode !== 'BACKEND' && (
                    <input
                      value={frontendDir}
                      onChange={(e) => setFrontendDir(e.target.value)}
                      placeholder="前端子目录"
                      title="前端代码相对仓库根目录的路径"
                      style={{ maxWidth: 140 }}
                    />
                  )}
                  {genMode === 'BOTH' ? (
                    <button
                      type="button"
                      className="btn small"
                      onClick={() => void handleCreateMonorepo()}
                      disabled={templateBusy || !form.repoId}
                      title="前端按子目录构建，后端按仓库根目录构建，各生成一条流水线"
                    >
                      {templateBusy ? '生成中…' : '生成前后端两条'}
                    </button>
                  ) : (
                    <button
                      type="button"
                      className="btn ghost small"
                      onClick={() => void applyTemplate(genMode, templateStack)}
                      disabled={templateBusy || !form.repoId}
                    >
                      {templateBusy ? '生成中…' : '生成步骤'}
                    </button>
                  )}
                  <span className="muted">
                    {templateSource ?? '选择关联仓库后自动生成步骤，也可切换后重新生成'}
                  </span>
                </div>
                <span className="muted field-hint">
                  {genMode === 'BOTH'
                    ? `一次创建两条流水线：前端 workDir=/workspace/${frontendDir.trim() || 'web'}，后端 workDir=/workspace，点击「生成前后端两条」执行。`
                    : genMode === 'FRONTEND'
                      ? `只生成前端流水线：Node 构建，workDir=/workspace/${frontendDir.trim() || 'web'}。`
                      : '只生成后端流水线：按仓库根目录 /workspace 构建，可用下方 JSON 微调。'}
                </span>
              </div>
            )}
            {(editingId !== null || genMode !== 'BOTH') && (
              <>
                <div className="field full">
                  <span className="toolbar" style={{ justifyContent: 'space-between' }}>
                    <span>步骤定义（JSON）</span>
                    <button type="button" className="btn ghost small" onClick={formatJson}>
                      格式化
                    </button>
                  </span>
                  <textarea
                    className="mono"
                    value={form.definitionJson}
                    spellCheck={false}
                    onChange={(e) => setForm({ ...form, definitionJson: e.target.value })}
                  />
                </div>
                {editingId !== null && (
                  <div className="field full">
                    <span className="toolbar" style={{ justifyContent: 'space-between' }}>
                      <span>配置即代码（YAML）</span>
                      <span className="inline-form">
                        <button type="button" className="btn ghost small"
                          onClick={() => void loadYaml()} disabled={yamlBusy}>
                          读取当前
                        </button>
                        <button type="button" className="btn ghost small"
                          onClick={() => void syncYaml()} disabled={yamlBusy || !form.configPath.trim()}>
                          从仓库同步
                        </button>
                        <button type="button" className="btn small"
                          onClick={() => void importYaml()} disabled={yamlBusy || !yamlText.trim()}>
                          导入 YAML
                        </button>
                      </span>
                    </span>
                    <textarea
                      className="mono"
                      value={yamlText}
                      spellCheck={false}
                      placeholder={'steps:\n  - name: build\n    image: maven:3.9-eclipse-temurin-17\n    commands:\n      - mvn -B package\n    artifacts:\n      - target/*.jar'}
                      onChange={(e) => setYamlText(e.target.value)}
                    />
                    <span className="muted field-hint">
                      {yamlSource ? `来源：${yamlSource}` : '导入会覆盖上方 JSON；配置路径非空时，构建始终以仓库 YAML 为准。'}
                    </span>
                  </div>
                )}
              </>
            )}
          </div>

          {(editingId !== null || genMode !== 'BOTH') && (
            <>
              {parsedSteps && parsedSteps.length > 0 && (
                <div className="steps-list" style={{ marginTop: 14 }}>
                  {parsedSteps.map((step, index) => (
                    <div className="step-item" key={`${step.name}-${index}`}>
                      <div className="step-head">
                        <strong>
                          {index + 1}. {step.name || `step-${index + 1}`}
                        </strong>
                        <span className="mono">{step.image}</span>
                      </div>
                      <div className="muted mono">{(step.commands ?? []).join(' && ')}</div>
                    </div>
                  ))}
                </div>
              )}
              {!parsedSteps && (form.configPath.trim() && !form.definitionJson.trim()
                ? <div className="alert info">已启用仓库配置：保存后点击「从仓库同步」即可读取并校验步骤。</div>
                : <div className="alert">JSON 解析失败，请检查括号与逗号</div>)}
              {form.configPath.trim() && (
                <div className="alert info">
                  当前流水线以仓库文件 <code>{form.configPath.trim()}</code> 为准；上方 JSON 仅用于本地预览。
                </div>
              )}

              <div className="form-actions">
                <button type="submit" className="btn">
                  {editingId ? '保存' : '创建'}
                </button>
              </div>
            </>
          )}
        </form>
      )}

      {triggering && (
        <form className="card" onSubmit={handleTrigger}>
          <div className="card-head">
            <h2>触发 {triggering.name}</h2>
            <button type="button" className="btn ghost small" onClick={() => setTriggering(null)}>
              取消
            </button>
          </div>
          <div className="inline-form">
            <label className="field">
              <span>分支</span>
              <input value={triggerBranch} onChange={(e) => setTriggerBranch(e.target.value)} required />
            </label>
            <label className="field">
              <span>Commit SHA（可选）</span>
              <input value={triggerSha} onChange={(e) => setTriggerSha(e.target.value)} />
            </label>
            <button type="submit" className="btn">
              立即触发
            </button>
            <span className="muted">需要流水线已绑定仓库且处于启用状态。</span>
          </div>
        </form>
      )}

      <div className="card">
        <div className="card-head">
          <h2>流水线列表</h2>
          <span className="muted">{pipelines?.total ?? 0} 条</span>
        </div>
        <form className="filters" onSubmit={applyFilters}>
          <input
            value={draft.keyword}
            placeholder="搜索流水线名称或所属项目"
            onChange={(e) => updateDraft({ keyword: e.target.value })}
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                applyFilters(e);
              }
            }}
          />
          <select
            value={draft.projectId}
            onChange={(e) => updateDraft({ projectId: e.target.value, repoId: '' })}
          >
            <option value="">全部项目</option>
            {projects.map((project) => (
              <option key={project.id} value={project.id}>
                {project.projectKey} · {project.name}
              </option>
            ))}
          </select>
          <select value={draft.repoId} onChange={(e) => updateDraft({ repoId: e.target.value })}>
            <option value="">全部仓库</option>
            {draftRepos.map((repo) => (
              <option key={repo.id} value={repo.id}>
                {repo.name}
              </option>
            ))}
          </select>
          <select value={draft.side} onChange={(e) => updateDraft({ side: e.target.value })}>
            <option value="">全部端</option>
            <option value="FRONTEND">前端</option>
            <option value="BACKEND">后端</option>
          </select>
          <select value={draft.triggerType} onChange={(e) => updateDraft({ triggerType: e.target.value })}>
            <option value="">全部触发方式</option>
            <option value="MANUAL">手动</option>
            <option value="WEBHOOK">Webhook</option>
          </select>
          <select value={draft.executorType} onChange={(e) => updateDraft({ executorType: e.target.value })}>
            <option value="">全部执行器</option>
            <option value="DOCKER">Docker</option>
            <option value="K8S">K8s</option>
            <option value="JENKINS">Jenkins</option>
          </select>
          <select value={draft.enabled} onChange={(e) => updateDraft({ enabled: e.target.value })}>
            <option value="">全部状态</option>
            <option value="true">启用</option>
            <option value="false">禁用</option>
          </select>
          <select value={draft.team} onChange={(e) => updateDraft({ team: e.target.value })}>
            <option value="">全部团队</option>
            {teamOptions.map((team) => (
              <option key={team} value={team}>
                {team}
              </option>
            ))}
          </select>
          <button type="submit" className="btn small">
            查询
          </button>
          <button type="button" className="btn ghost small" onClick={resetFilters}>
            重置
          </button>
        </form>
        {activeFilters.length > 0 && (
          <div className="filter-tags">
            <span className="muted">当前条件</span>
            {activeFilters.map((chip) => (
              <button
                type="button"
                key={chip.key}
                className="filter-tag"
                title="移除该条件"
                onClick={() => clearFilter(chip.key)}
              >
                {chip.label}
                <span aria-hidden="true">×</span>
              </button>
            ))}
            <button type="button" className="btn ghost small" onClick={resetFilters}>
              清空全部
            </button>
          </div>
        )}
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>名称</th>
                <th>所属项目</th>
                <th>端</th>
                <th>分支</th>
                <th>团队</th>
                <th>触发</th>
                <th>执行器</th>
                <th>状态</th>
                <th>更新时间</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {(pipelines?.items ?? []).map((pipeline) => (
                <tr key={pipeline.id}>
                  <td>{pipeline.name}</td>
                  <td className="muted">{projectLabel(pipeline.projectId)}</td>
                  <td>
                    <span className="side-badge">
                      {pipeline.side === 'FRONTEND' ? '前端' : '后端'}
                    </span>
                  </td>
                  <td className="mono">{pipeline.branch}</td>
                  <td className="muted">{pipeline.team ?? '-'}</td>
                  <td>{pipeline.triggerType}</td>
                  <td>{pipeline.executorType}</td>
                  <td>
                    <span className={`badge ${pipeline.enabled ? 'green' : 'gray'}`}>
                      {pipeline.enabled ? '启用' : '禁用'}
                    </span>
                  </td>
                  <td className="muted">{formatTime(pipeline.updatedAt)}</td>
                  <td className="toolbar">
                    <Link to={`/runs?pipelineId=${pipeline.id}`}>运行</Link>
                    {canWrite(user) && (
                      <>
                        <button type="button" className="btn small" onClick={() => openTrigger(pipeline)}>
                          触发
                        </button>
                        <button
                          type="button"
                          className="btn ghost small"
                          onClick={() => startEdit(pipeline)}
                        >
                          编辑
                        </button>
                      </>
                    )}
                    {isAdmin(user) && (
                      <button
                        type="button"
                        className="btn danger small"
                        onClick={() => void handleDelete(pipeline)}
                      >
                        删除
                      </button>
                    )}
                  </td>
                </tr>
              ))}
              {(pipelines?.items.length ?? 0) === 0 && (
                <tr>
                  <td colSpan={10} className="empty">
                    {activeFilters.length > 0
                      ? '没有符合当前条件的流水线，试试调整或清空筛选条件'
                      : '暂无流水线'}
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
        <Pagination
          page={pipelines}
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
