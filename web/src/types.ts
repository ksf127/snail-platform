export interface ApiResult<T> {
  code: number;
  message: string;
  data: T;
}

export type Role = 'ADMIN' | 'DEVELOPER' | 'VIEWER';
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
  definitionJson: string;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
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
  environment: 'DEV' | 'TEST' | 'PROD';
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
