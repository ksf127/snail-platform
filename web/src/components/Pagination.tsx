import type { PageView } from '../types';

const SIZES = [10, 20, 50, 100];

interface PaginationProps<T> {
  page: PageView<T> | null;
  onPageChange: (page: number) => void;
  onSizeChange: (size: number) => void;
}

/** 列表页统一分页条：总数、页码窗口、每页条数。 */
export default function Pagination<T>({ page, onPageChange, onSizeChange }: PaginationProps<T>) {
  if (!page) {
    return null;
  }
  const totalPages = Math.max(1, Math.ceil(page.total / page.size));
  const current = Math.min(page.page, totalPages);
  const start = Math.max(1, Math.min(current - 2, totalPages - 4));
  const end = Math.min(totalPages, start + 4);
  const numbers: number[] = [];
  for (let i = start; i <= end; i += 1) {
    numbers.push(i);
  }

  return (
    <div className="pagination">
      <span className="muted">
        共 {page.total} 条 · 第 {current}/{totalPages} 页
      </span>
      <div className="pagination-actions">
        <button
          type="button"
          className="btn ghost small"
          disabled={current <= 1}
          onClick={() => onPageChange(current - 1)}
        >
          上一页
        </button>
        {numbers.map((number) => (
          <button
            key={number}
            type="button"
            className={number === current ? 'btn small' : 'btn ghost small'}
            onClick={() => onPageChange(number)}
          >
            {number}
          </button>
        ))}
        <button
          type="button"
          className="btn ghost small"
          disabled={current >= totalPages}
          onClick={() => onPageChange(current + 1)}
        >
          下一页
        </button>
        <select
          value={page.size}
          onChange={(event) => onSizeChange(Number(event.target.value))}
          aria-label="每页条数"
        >
          {SIZES.map((size) => (
            <option key={size} value={size}>
              {size} 条/页
            </option>
          ))}
        </select>
      </div>
    </div>
  );
}
