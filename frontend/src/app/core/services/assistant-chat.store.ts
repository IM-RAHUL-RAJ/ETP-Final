import { Injectable, signal } from '@angular/core';
import { ChatMessage } from '../models/assistant.model';

const KEY_PREFIX = 'quorum-assistant-';
const MAX_MESSAGES = 60;

/**
 * Keeps the conversation outside the chat page, so it is still there after the customer visits another page and comes
 * back (the page is rebuilt each time), and after a refresh (it is mirrored to this tab's session storage, per account).
 * Nothing is sent anywhere from here.
 */
@Injectable({ providedIn: 'root' })
export class AssistantChatStore {
  readonly messages = signal<ChatMessage[]>([]);
  conversationId: string | undefined;
  private nextId = 1;
  private accountId: number | null = null;

  /** Call with the signed-in account: loads that account's saved chat, and drops another account's. */
  bind(accountId: number): void {
    if (this.accountId === accountId) {
      return;
    }
    this.accountId = accountId;
    this.messages.set([]);
    this.conversationId = undefined;
    this.nextId = 1;
    try {
      const raw = sessionStorage.getItem(KEY_PREFIX + accountId);
      if (raw) {
        const saved = JSON.parse(raw) as { messages?: ChatMessage[]; conversationId?: string };
        const messages = Array.isArray(saved.messages) ? saved.messages.slice(-MAX_MESSAGES) : [];
        this.messages.set(messages);
        this.conversationId = saved.conversationId;
        this.nextId = messages.reduce((highest, message) => Math.max(highest, message.id), 0) + 1;
      }
    } catch {
      // Storage blocked or damaged: start with an empty chat.
    }
  }

  add(entry: Omit<ChatMessage, 'id'>): void {
    this.messages.update((list) => [...list, { ...entry, id: this.nextId++ }].slice(-MAX_MESSAGES));
    this.save();
  }

  remove(id: number): void {
    this.messages.update((list) => list.filter((entry) => entry.id !== id));
    this.save();
  }

  setConversation(id: string): void {
    this.conversationId = id;
    this.save();
  }

  clear(): void {
    this.messages.set([]);
    this.conversationId = undefined;
    this.save();
  }

  private save(): void {
    if (this.accountId === null) {
      return;
    }
    try {
      sessionStorage.setItem(KEY_PREFIX + this.accountId, JSON.stringify({ messages: this.messages(), conversationId: this.conversationId }));
    } catch {
      // Storage blocked or full: the chat still holds while the app is open.
    }
  }
}
