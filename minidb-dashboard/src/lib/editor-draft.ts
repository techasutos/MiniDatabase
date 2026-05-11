const KEY = "minidb.dashboard.editorDraft";
const EVENT = "minidb-editor-draft-updated";

export function setEditorDraft(sql: string) {
  localStorage.setItem(KEY, sql);
  window.dispatchEvent(new CustomEvent(EVENT));
}

export function readEditorDraft() {
  return localStorage.getItem(KEY) ?? "";
}

export function consumeEditorDraft() {
  const value = readEditorDraft();
  if (value) {
    localStorage.removeItem(KEY);
  }
  return value;
}

export function subscribeEditorDraft(listener: () => void) {
  const wrapped = () => listener();
  window.addEventListener(EVENT, wrapped);
  return () => window.removeEventListener(EVENT, wrapped);
}

