export interface ApiResult<T> {
  code: number;
  message: string;
  data: T;
}

export interface PageView<T> {
  items: T[];
  total: number;
  page: number;
  size: number;
}

export type Role = 'ADMIN' | 'DEVELOPER' | 'VIEWER';
export type PipelineSide = 'FRONTEND' | 'BACKEND';
export type RunStatus = 'QUEUED' | 'RUNNING' | 'SUCCESS' | 'FAILED' | 'CANCELED';

export interface User {
  id: number;
  username: string;
  displayName: string;
  role: Role;
}

export interface LoginResponse {
  token: string;
  user: User;
}

export interface Project {
  id: number;
  projectKey: string;
  name: string;
  description: string | null;
  ownerId: number;
  status: string;
  createdAt: string;
}

export interface Repo {
  id: number;
  projectId: number;
  name: string;
  provider: string;
  externalId: string | null;
  cloneUrl: string;
  defaultBranch: string;
  webhookConfigured: boolean;
  credentialConfigured: boolean;
}

export interface RepoCreated {
  repo: Repo;
  webhookSecret: string;
}

export interface Pipeline {
  id: number;
  projectId: number;
  repoId: number | null;
  name: string;
  branch: string;
  triggerType: string;
  executorType: string;
  side: PipelineSide;
  definitionJson: string;
  configPath: string | null;
  enabled: boolean;
  team?: string | null;
  createdAt: string;
  updatedAt: string;
}

/** 分支归属：同一项目不同分支可由不同团队维护。 */
export interface ProjectBranch {
  id: number;
  projectId: number;
  branch: string;
  team: string | null;
  ownerId: number | null;
  ownerName: string | null;
  description: string | null;
  createdAt: string;
  updatedAt: string;
}

/** 项目环境：dev/test/prod 等，决定发布使用的 namespace 与默认副本。 */
export interface ProjectEnvironment {
  id: number;
  projectId: number;
  name: string;
  displayName: string;
  namespace: string;
  defaultReplicas: number;
  description: string | null;
  sortOrder: number;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface PipelineTemplateView {
  stack: string;
  side: PipelineSide;
  name: string;
  branch: string;
  triggerType: string;
  executorType: string;
  definitionJson: string;
  source: string;
}

export interface Run {
  id: number;
  pipelineId: number;
  pipelineName: string;
  buildNo: number;
  status: RunStatus;
  commitSha: string | null;
  branch: string;
  triggerType: string;
  triggeredBy: string | null;
  startedAt: string | null;
  finishedAt: string | null;
  logObject: string | null;
  errorMessage: string | null;
  createdAt: string;
}

export interface Artifact {
  id: number;
  runId: number;
  fileName: string;
  objectKey: string;
  size: number;
  sha256: string | null;
  contentType: string | null;
  createdAt: string;
}

export interface Deployment {
  id: number;
  projectId: number;
  runId: number | null;
  environment: string;
  namespace: string | null;
  status: string;
  image: string;
  replicas: number;
  note: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface DeploymentExecution {
  deployment: Deployment;
  mode: 'APPLIED' | 'PREVIEW' | 'FAILED';
  commandPreview: string | null;
  message: string;
}

export interface Dashboard {
  projectCount: number;
  repoCount: number;
  pipelineCount: number;
  runCount: number;
  successRate: number;
  recentRuns: Run[];
}

export interface Member {
  id: number;
  userId: number;
  username: string;
  displayName: string;
  role: Role;
}

export interface PipelineStep {
  name: string;
  image: string;
  commands: string[];
  workDir?: string;
  artifacts?: string[];
}

/** 项目构建变量：接口只返回掩码。 */
export interface ProjectSecret {
  id: number;
  projectId: number;
  name: string;
  maskedValue: string;
  description: string | null;
  updatedAt: string;
}

export interface PipelineYamlView {
  yaml: string;
  definitionJson: string;
  configPath: string | null;
  source: string;
}
