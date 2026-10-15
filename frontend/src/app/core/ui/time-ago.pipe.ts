import { Pipe, PipeTransform } from '@angular/core';

/** "just now", "5 minutes ago", "2 hours ago", "3 days ago"; older than a month falls back to the date. */
export function timeAgo(iso: string | null | undefined, now: number = Date.now()): string {
  if (!iso) {
    return '';
  }
  const then = Date.parse(iso);
  if (Number.isNaN(then)) {
    return '';
  }
  const seconds = Math.max(0, Math.round((now - then) / 1000));
  if (seconds < 45) {
    return 'just now';
  }
  const unit = (value: number, name: string) => `${value} ${name}${value === 1 ? '' : 's'} ago`;
  const minutes = Math.round(seconds / 60);
  if (minutes < 60) {
    return unit(minutes, 'minute');
  }
  const hours = Math.round(minutes / 60);
  if (hours < 24) {
    return unit(hours, 'hour');
  }
  const days = Math.round(hours / 24);
  if (days < 30) {
    return unit(days, 'day');
  }
  return new Date(then).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' });
}

@Pipe({ name: 'timeAgo', standalone: true })
export class TimeAgoPipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    return timeAgo(value);
  }
}
