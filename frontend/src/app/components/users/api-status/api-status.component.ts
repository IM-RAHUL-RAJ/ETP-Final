import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { ApiErrorMessageService } from '../../../core/services/api-error-message.service';
import { AccountContextService } from '../../../core/services/account-context.service';
import { ApiStatusService } from '../../../core/services/api-status.service';
import { ServiceStatus } from '../../../core/models/account.model';

@Component({
  selector: 'app-api-status',
  standalone: true,
  templateUrl: './api-status.component.html',
  styleUrl: './api-status.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ApiStatusComponent {
  private readonly apiStatusService = inject(ApiStatusService);
  private readonly accountContext = inject(AccountContextService);
  private readonly errorMessageService = inject(ApiErrorMessageService);

  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly services = signal<ServiceStatus[]>([]);

  constructor() {
    this.refreshStatus();
  }

  refreshStatus(): void {
    const accountId = this.accountContext.getCurrentAccount().accountNumericId;

    this.loading.set(true);
    this.error.set(null);

    this.apiStatusService.getStatuses(accountId).subscribe({
      next: (statuses) => {
        this.services.set(statuses);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.error.set(this.errorMessageService.getMessage(error));
        this.loading.set(false);
      }
    });
  }
}

