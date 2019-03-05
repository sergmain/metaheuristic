import { Injectable } from '@angular/core';

export interface Snippet {
  name: string;
  version: string;
  type: string;
  environment: string;
  params: string;
  isSigned: boolean;
}

function rand(min, max) {
  return Math.floor(Math.random() * (max - min)) + min;
}

function initItem() {
  return {
    name: 'name-' + rand(1, 9999),
    version: 'v' + rand(1, 9999),
    type: 'type-' + rand(1, 9999),
    environment: 'environment-' + rand(1, 9999),
    params: 'params-' + rand(1, 9999),
    isSigned: [true, false][rand(0, 2)]
  }
}

function initItems(): Snippet[] {
  return Array.from(Array(99)).map(el => initItem())
}

@Injectable({
  providedIn: 'root'
})

export class SnippetsService {
  private data: Snippet[] = initItems();
  constructor() { }
  getSnippets(): Snippet[] {
    return this.data
  }
}