import { Logger } from '@nestjs/common';

export class RedactedLogger extends Logger {
  private sensitiveFields = [
    'password',
    'passwordHash',
    'refreshToken',
    'accessToken',
    'token',
    'secret',
    'apiKey',
    'Authorization',
  ];

  private redactValue(value: any): any {
    if (typeof value === 'string') {
      return '***REDACTED***';
    }
    if (typeof value === 'object' && value !== null) {
      return this.redactObject(value);
    }
    return value;
  }

  private redactObject(obj: any): any {
    if (Array.isArray(obj)) {
      return obj.map((item) => this.redactObject(item));
    }

    const redacted: any = {};
    for (const key in obj) {
      if (Object.prototype.hasOwnProperty.call(obj, key)) {
        const lowerKey = key.toLowerCase();
        const isSensitive = this.sensitiveFields.some(
          (field) => lowerKey.includes(field.toLowerCase())
        );

        if (isSensitive) {
          redacted[key] = this.redactValue(obj[key]);
        } else if (typeof obj[key] === 'object' && obj[key] !== null) {
          redacted[key] = this.redactObject(obj[key]);
        } else {
          redacted[key] = obj[key];
        }
      }
    }
    return redacted;
  }

  logRedacted(message: string, data?: any): void {
    const redactedData = data ? this.redactObject(data) : undefined;
    if (redactedData) {
      this.log(`${message} ${JSON.stringify(redactedData)}`);
    } else {
      this.log(message);
    }
  }

  warnRedacted(message: string, data?: any): void {
    const redactedData = data ? this.redactObject(data) : undefined;
    if (redactedData) {
      this.warn(`${message} ${JSON.stringify(redactedData)}`);
    } else {
      this.warn(message);
    }
  }

  errorRedacted(message: string, error?: any): void {
    const redactedError = error ? this.redactObject(error) : undefined;
    if (redactedError) {
      this.error(`${message} ${JSON.stringify(redactedError)}`);
    } else {
      this.error(message);
    }
  }
}
