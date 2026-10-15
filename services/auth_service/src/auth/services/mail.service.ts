import { Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import * as nodemailer from 'nodemailer';
import type { Transporter } from 'nodemailer';

/**
 * Sends the welcome email that carries a new customer's default password over SMTP.
 *
 * Configure with SMTP_HOST, SMTP_PORT, SMTP_USER, SMTP_PASS, SMTP_FROM and (optionally)
 * SMTP_SECURE=true for implicit TLS. APP_LOGIN_URL is optional: when it is not set, the email has
 * no link and just carries the username and password. Without SMTP_HOST the service reports itself as not
 * configured and registration refuses to create a user it could not tell the password to.
 * The password is written into the message and nowhere else: it is never logged.
 */
@Injectable()
export class MailService {
    private readonly logger = new Logger(MailService.name);
    private transporter: Transporter | null = null;

    constructor(private readonly config: ConfigService) { }

    isConfigured(): boolean {
        return !!this.config.get<string>('SMTP_HOST');
    }

    private getTransporter(): Transporter {
        if (!this.transporter) {
            const user = this.config.get<string>('SMTP_USER');
            const port = Number(this.config.get<string>('SMTP_PORT') ?? 587);
            this.transporter = nodemailer.createTransport({
                host: this.config.get<string>('SMTP_HOST'),
                port,
                secure: this.config.get<string>('SMTP_SECURE') === 'true' || port === 465,
                auth: user ? { user, pass: this.config.get<string>('SMTP_PASS') } : undefined,
            });
        }
        return this.transporter;
    }

    async sendWelcome(to: string, name: string, username: string, password: string): Promise<void> {
        const from = this.config.get<string>('SMTP_FROM') ?? this.config.get<string>('SMTP_USER');
        const loginUrl = this.config.get<string>('APP_LOGIN_URL')?.trim() || null;
        const greeting = name?.trim() ? `Hello ${name.trim()},` : 'Hello,';

        await this.getTransporter().sendMail({
            from,
            to,
            subject: 'Your Quorum account is ready',
            text: [
                greeting,
                '',
                'An account has been created for you on Quorum.',
                '',
                `Username: ${username}`,
                `Temporary password: ${password}`,
                '',
                ...(loginUrl ? [`Sign in at ${loginUrl}`] : []),
                'When you sign in you will be asked to choose your own password in the Account tab. Please do that straight away.',
                '',
                'If you did not expect this email, you can ignore it.',
            ].join('\n'),
            html: `
                <p>${escapeHtml(greeting)}</p>
                <p>An account has been created for you on <strong>Quorum</strong>.</p>
                <table cellpadding="6" style="border-collapse:collapse;background:#f3f6f4;border-radius:8px">
                  <tr><td>Username</td><td><strong>${escapeHtml(username)}</strong></td></tr>
                  <tr><td>Temporary password</td><td><code style="font-size:15px"><strong>${escapeHtml(password)}</strong></code></td></tr>
                </table>
                ${loginUrl ? `<p><a href="${escapeHtml(loginUrl)}">Sign in to Quorum</a></p>` : ''}
                <p>When you sign in you will be asked to choose your own password in the <strong>Account</strong> tab. Please do that straight away.</p>
                <p style="color:#666">If you did not expect this email, you can ignore it.</p>`,
        });
        this.logger.log(`[AUTH_EVENT] welcome_email_sent username=${username}`);
    }
}

function escapeHtml(value: string): string {
    return value
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');
}
