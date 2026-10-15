import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { IconComponent } from '../icon/icon.component';
import { MascotComponent } from '../mascot/mascot.component';

/**
 * A small bubble in the bottom-right corner that opens the chat page. It is a plain link, so it works from the keyboard
 * and with a screen reader, and it never covers the page's content: it is a fixed 60px square in the corner.
 */
@Component({
  selector: 'app-chat-launcher',
  standalone: true,
  imports: [RouterLink, IconComponent, MascotComponent],
  template: `
    <a class="launcher" routerLink="/assistant" aria-label="Ask your companion" data-tip="Ask me anything" data-tip-end data-testid="chat-launcher">
      <app-mascot [size]="44" [interactive]="false" />
      <span class="launcher__badge" aria-hidden="true"><app-icon name="chat" [size]="14" /></span>
    </a>
  `,
  styles: [
    `
      :host {
        position: fixed;
        right: 16px;
        bottom: 16px;
        z-index: 85;
      }

      .launcher {
        position: relative;
        display: grid;
        place-items: center;
        width: 60px;
        height: 60px;
        background: var(--surface);
        border: 2px solid var(--text);
        box-shadow: 4px 4px 0 var(--text);
        text-decoration: none;
        transition: transform 120ms steps(2);
      }

      .launcher:hover {
        transform: translate(-2px, -2px);
        box-shadow: 6px 6px 0 var(--text);
      }

      .launcher:active {
        transform: translate(2px, 2px);
        box-shadow: 2px 2px 0 var(--text);
      }

      .launcher__badge {
        position: absolute;
        top: -8px;
        left: -8px;
        display: grid;
        place-items: center;
        width: 24px;
        height: 24px;
        color: var(--on-accent);
        background: var(--accent);
        border: 2px solid var(--bg);
      }

      @media (prefers-reduced-motion: reduce) {
        .launcher {
          transition: none;
        }
      }
    `
  ],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ChatLauncherComponent {}
