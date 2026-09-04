import fs from 'node:fs/promises';
import path from 'node:path';
import { address, operationHashes, projectId } from './mitum.js';

/**
 * Storage 프로젝트는 대구체인의 개념이고 Fabric 체인코드에는 없다.
 * 에뮬레이터가 파일에 보관하며, 데이터 키는 프로젝트와 무관하게 Fabric 에 그대로 저장된다.
 * (프로젝트가 달라도 같은 data_key 는 같은 원장 항목이다 — 백엔드는 프로젝트를 하나만 쓴다.)
 */
export interface StorageProject {
  readonly project_id: string;
  readonly project_name: string;
  readonly owner: string;
  readonly contract: string;
  readonly issued: string;
  readonly tx: { readonly hash: string; readonly fact_hash: string };
  readonly removed_at?: string;
}

export class ProjectStore {
  private projects = new Map<string, StorageProject>();
  private loaded = false;

  constructor(
    private readonly filePath: string,
    private readonly owner: string,
  ) {}

  async load(): Promise<void> {
    try {
      const parsed = JSON.parse(await fs.readFile(this.filePath, 'utf8')) as StorageProject[];
      this.projects = new Map(parsed.map((project) => [project.project_id, project]));
    } catch (error) {
      if ((error as NodeJS.ErrnoException).code !== 'ENOENT') throw error;
    }
    this.loaded = true;
  }

  list(): StorageProject[] {
    this.requireLoaded();
    return [...this.projects.values()].filter((project) => !project.removed_at);
  }

  get(id: string): StorageProject | undefined {
    this.requireLoaded();
    const project = this.projects.get(id);
    return project && !project.removed_at ? project : undefined;
  }

  /** 등록. project_id 는 자동 생성하고 등록 트랜잭션 해시는 프로젝트 ID 에서 파생한다. */
  async register(projectName: string, issuedAt: Date): Promise<StorageProject> {
    this.requireLoaded();
    let id = projectId();
    while (this.projects.has(id)) id = projectId();
    const project: StorageProject = {
      project_id: id,
      project_name: projectName,
      owner: this.owner,
      contract: address(`contract|${id}`),
      issued: issuedAt.toISOString(),
      tx: operationHashes(`register-project|${id}`),
    };
    this.projects.set(id, project);
    await this.persist();
    return project;
  }

  async remove(id: string, removedAt: Date): Promise<StorageProject | undefined> {
    const project = this.get(id);
    if (!project) return undefined;
    const removed: StorageProject = { ...project, removed_at: removedAt.toISOString() };
    this.projects.set(id, removed);
    await this.persist();
    return removed;
  }

  private async persist(): Promise<void> {
    await fs.mkdir(path.dirname(this.filePath), { recursive: true });
    const tmp = `${this.filePath}.tmp`;
    await fs.writeFile(tmp, JSON.stringify([...this.projects.values()], null, 2) + '\n', 'utf8');
    await fs.rename(tmp, this.filePath);
  }

  private requireLoaded(): void {
    if (!this.loaded) throw new Error('ProjectStore.load() 를 먼저 호출해야 한다');
  }
}
