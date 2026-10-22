import type { ReactNode } from 'react';
import { formatCount } from '../api/format';

export interface BarItem {
  key: string;
  label: ReactNode;
  value: number;
  detail?: string;
  tone?: string; // CSS class modifier; defaults to the single series hue
}

// Horizontal bars for small categorical counts. Values are always printed as text,
// so the bar length is a secondary cue and the list doubles as its own table.
export function BarList({ items, caption }: { items: BarItem[]; caption: string }) {
  const max = Math.max(1, ...items.map((item) => item.value));
  return (
    <ul className="bar-list" aria-label={caption}>
      {items.map((item) => (
        <li key={item.key} className="bar-row" title={item.detail ?? `${formatCount(item.value)}`}>
          <span className="bar-label">{item.label}</span>
          <span className="bar-track">
            <span
              className={`bar-fill ${item.tone ? `tone-${item.tone}` : ''}`}
              style={{ width: item.value === 0 ? 0 : `max(4px, ${(item.value / max) * 100}%)` }}
            />
          </span>
          <span className="bar-value">{formatCount(item.value)}</span>
        </li>
      ))}
    </ul>
  );
}
