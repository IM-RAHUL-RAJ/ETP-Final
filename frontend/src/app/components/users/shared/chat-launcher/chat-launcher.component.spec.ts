import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { PreferencesService } from '../../../../core/services/preferences.service';
import { ChatLauncherComponent } from './chat-launcher.component';

describe('ChatLauncherComponent', () => {
  it('is a labelled link to the chat page', () => {
    TestBed.configureTestingModule({
      imports: [ChatLauncherComponent],
      providers: [provideRouter([]), { provide: PreferencesService, useValue: { get: vi.fn() } }]
    });
    const fixture = TestBed.createComponent(ChatLauncherComponent);
    fixture.detectChanges();

    const link = fixture.nativeElement.querySelector('[data-testid="chat-launcher"]') as HTMLAnchorElement;
    expect(link.getAttribute('href')).toBe('/assistant');
    expect(link.getAttribute('aria-label')).toBe('Ask your companion');
    expect(link.querySelector('app-mascot')).not.toBeNull();
  });
});
