import {
  ExceptionFilter,
  Catch,
  ArgumentsHost,
  BadRequestException,
  HttpException,
  HttpStatus,
  Logger,
} from '@nestjs/common';
import { RedactedLogger } from '../../common/redacted-logger';
import { Response } from 'express';

@Catch(BadRequestException, HttpException)
export class GlobalExceptionFilter implements ExceptionFilter {
  private logger = new RedactedLogger(GlobalExceptionFilter.name);
  private sensitiveFields = ['password', 'token', 'secret', 'apiKey'];

  private redactSensitive(obj: any): any {
    if (typeof obj !== 'object' || obj === null) return obj;
    const redacted: any = Array.isArray(obj) ? [...obj] : { ...obj };
    for (const key in redacted) {
      if (this.sensitiveFields.some((f) => key.toLowerCase().includes(f.toLowerCase()))) {
        redacted[key] = '***REDACTED***';
      } else if (typeof redacted[key] === 'object') {
        redacted[key] = this.redactSensitive(redacted[key]);
      }
    }
    return redacted;
  }

  catch(exception: BadRequestException | HttpException, host: ArgumentsHost) {
    const ctx = host.switchToHttp();
    const response = ctx.getResponse<Response>();
    const request = ctx.getRequest();

    let status = HttpStatus.INTERNAL_SERVER_ERROR;
    let errorResponse: any = {
      errorCode: 'UNKNOWN',
      message: 'Internal server error',
    };

    if (exception instanceof BadRequestException) {
      status = HttpStatus.UNPROCESSABLE_ENTITY;
      errorResponse = {
        errorCode: 'VAL-422',
        message: 'Invalid input',
      };
      this.logger.warnRedacted('[AUTH_EVENT] validation_error', { path: request.url, errors: this.redactSensitive(exception.getResponse()) });
    } else if (exception instanceof HttpException) {
      status = exception.getStatus();
      const exceptionResponse = exception.getResponse();

      if (
        typeof exceptionResponse === 'object' &&
        exceptionResponse !== null &&
        'errorCode' in exceptionResponse
      ) {
        errorResponse = exceptionResponse;
      } else {
        errorResponse = {
          errorCode: `HTTP-${status}`,
          message: exception.message,
        };
      }
    }

    response.status(status).json(errorResponse);
  }
}
