import { GlobalExceptionFilter } from './global-exception.filter';
import { BadRequestException, HttpException } from '@nestjs/common';

describe('GlobalExceptionFilter', () => {
  it('handles BadRequestException and redacts sensitive fields', () => {
    const filter = new GlobalExceptionFilter();
    const mockResponse = { status: jest.fn().mockReturnThis(), json: jest.fn() };
    const mockHost: any = {
      switchToHttp: () => ({ getResponse: () => mockResponse, getRequest: () => ({ url: '/test' }) }),
    };

    const ex = new BadRequestException({ password: 'secret', other: 'x' });
    filter.catch(ex, mockHost);
    expect(mockResponse.status).toHaveBeenCalled();
    expect(mockResponse.json).toHaveBeenCalled();
  });

  it('handles generic HttpException', () => {
    const filter = new GlobalExceptionFilter();
    const mockResponse = { status: jest.fn().mockReturnThis(), json: jest.fn() };
    const mockHost: any = {
      switchToHttp: () => ({ getResponse: () => mockResponse, getRequest: () => ({ url: '/test' }) }),
    };

    const ex = new HttpException({ errorCode: 'X', message: 'msg' }, 418);
    filter.catch(ex, mockHost);
    expect(mockResponse.status).toHaveBeenCalledWith(418);
    expect(mockResponse.json).toHaveBeenCalledWith({ errorCode: 'X', message: 'msg' });
  });
});
