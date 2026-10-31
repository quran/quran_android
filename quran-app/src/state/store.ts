import AsyncStorage from '@react-native-async-storage/async-storage';
import { useSyncExternalStore } from 'react';
import type { Ref, TranslationId } from '../data/quran';
import { refKey } from '../data/quran';

export type Settings = {
  theme: 'system' | 'light' | 'sepia' | 'dark';
  translation: TranslationId;
  showTransliteration: boolean;
  arabicSize: number;
  translationSize: number;
};

export type Tag = { id: string; name: string; color: string; createdAt: number };

export type SavedVerse = Ref & {
  tagIds: string[];
  note?: string;
  addedAt: number;
};

export type Place = Ref & { at: number };

export type State = {
  hydrated: boolean;
  settings: Settings;
  /** The place you set yourself with "Mark as last read". */
  lastRead: Place | null;
  /** Where you most recently were, tracked automatically while reading. */
  recent: Place | null;
  tags: Tag[];
  /** Keyed by "sura:ayah". */
  saved: Record<string, SavedVerse>;
};

const STORAGE_KEY = 'quran-app/v1';

export const DEFAULT_SETTINGS: Settings = {
  theme: 'system',
  translation: 'en',
  showTransliteration: false,
  arabicSize: 28,
  translationSize: 16,
};

let state: State = {
  hydrated: false,
  settings: DEFAULT_SETTINGS,
  lastRead: null,
  recent: null,
  tags: [],
  saved: {},
};

const listeners = new Set<() => void>();
let saveTimer: ReturnType<typeof setTimeout> | null = null;

function persisted(s: State) {
  const { hydrated: _h, ...rest } = s;
  return rest;
}

function set(update: (s: State) => Partial<State>) {
  state = { ...state, ...update(state) };
  listeners.forEach((l) => l());
  if (state.hydrated) {
    if (saveTimer) clearTimeout(saveTimer);
    saveTimer = setTimeout(() => {
      AsyncStorage.setItem(STORAGE_KEY, JSON.stringify(persisted(state))).catch(() => {});
    }, 300);
  }
}

export function getState() {
  return state;
}

/** Subscribe to part of the state. The selector must return a stable reference. */
export function useStore<T>(selector: (s: State) => T): T {
  return useSyncExternalStore(
    (l) => {
      listeners.add(l);
      return () => listeners.delete(l);
    },
    () => selector(state),
    () => selector(state)
  );
}

function sanitize(raw: any): Omit<State, 'hydrated'> {
  const settings = { ...DEFAULT_SETTINGS, ...(raw?.settings ?? {}) };
  const tags: Tag[] = Array.isArray(raw?.tags)
    ? raw.tags.filter((t: any) => t && typeof t.id === 'string' && typeof t.name === 'string')
    : [];
  const saved: Record<string, SavedVerse> = {};
  if (raw?.saved && typeof raw.saved === 'object') {
    for (const v of Object.values<any>(raw.saved)) {
      if (v && Number.isInteger(v.sura) && Number.isInteger(v.ayah)) {
        saved[refKey(v)] = {
          sura: v.sura,
          ayah: v.ayah,
          tagIds: Array.isArray(v.tagIds) ? v.tagIds : [],
          note: typeof v.note === 'string' ? v.note : undefined,
          addedAt: Number(v.addedAt) || Date.now(),
        };
      }
    }
  }
  const place = (p: any): Place | null =>
    p && Number.isInteger(p.sura) && Number.isInteger(p.ayah)
      ? { sura: p.sura, ayah: p.ayah, at: Number(p.at) || Date.now() }
      : null;
  return { settings, tags, saved, lastRead: place(raw?.lastRead), recent: place(raw?.recent) };
}

export async function hydrate() {
  try {
    const raw = await AsyncStorage.getItem(STORAGE_KEY);
    const loaded = raw ? sanitize(JSON.parse(raw)) : {};
    set(() => ({ ...loaded, hydrated: true }));
  } catch {
    set(() => ({ hydrated: true }));
  }
}

const newId = () => `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 7)}`;

export const actions = {
  updateSettings(patch: Partial<Settings>) {
    set((s) => ({ settings: { ...s.settings, ...patch } }));
  },

  setLastRead(r: Ref) {
    set(() => ({ lastRead: { sura: r.sura, ayah: r.ayah, at: Date.now() } }));
  },

  clearLastRead() {
    set(() => ({ lastRead: null }));
  },

  setRecent(r: Ref) {
    const cur = state.recent;
    if (cur && cur.sura === r.sura && cur.ayah === r.ayah) return;
    set(() => ({ recent: { sura: r.sura, ayah: r.ayah, at: Date.now() } }));
  },

  createTag(name: string, color: string): Tag {
    const tag: Tag = { id: newId(), name: name.trim(), color, createdAt: Date.now() };
    set((s) => ({ tags: [...s.tags, tag] }));
    return tag;
  },

  updateTag(id: string, patch: Partial<Pick<Tag, 'name' | 'color'>>) {
    set((s) => ({ tags: s.tags.map((t) => (t.id === id ? { ...t, ...patch } : t)) }));
  },

  /** Deletes a tag. Verses that were only in this tag are un-saved. */
  deleteTag(id: string) {
    set((s) => {
      const saved: Record<string, SavedVerse> = {};
      for (const [k, v] of Object.entries(s.saved)) {
        const tagIds = v.tagIds.filter((t) => t !== id);
        if (tagIds.length > 0 || v.tagIds.length === 0) saved[k] = { ...v, tagIds };
      }
      return { tags: s.tags.filter((t) => t.id !== id), saved };
    });
  },

  /** Sets exactly which tags a verse is in. An empty list removes it from saved. */
  setVerseTags(r: Ref, tagIds: string[]) {
    set((s) => {
      const key = refKey(r);
      const saved = { ...s.saved };
      if (tagIds.length === 0) {
        delete saved[key];
      } else {
        const prev = saved[key];
        saved[key] = {
          sura: r.sura,
          ayah: r.ayah,
          tagIds,
          note: prev?.note,
          addedAt: prev?.addedAt ?? Date.now(),
        };
      }
      return { saved };
    });
  },

  removeVerseFromTag(r: Ref, tagId: string) {
    const v = state.saved[refKey(r)];
    if (v) actions.setVerseTags(r, v.tagIds.filter((t) => t !== tagId));
  },

  setNote(r: Ref, note: string) {
    set((s) => {
      const key = refKey(r);
      const v = s.saved[key];
      if (!v) return {};
      return { saved: { ...s.saved, [key]: { ...v, note: note.trim() || undefined } } };
    });
  },

  exportBackup(): string {
    return JSON.stringify({ app: 'quran-app', version: 1, ...persisted(state) }, null, 2);
  },

  /** Replaces tags, saved verses and places with the backup. Returns false if it is unreadable. */
  importBackup(text: string): boolean {
    try {
      const raw = JSON.parse(text);
      if (!raw || (raw.tags === undefined && raw.saved === undefined)) return false;
      const loaded = sanitize(raw);
      set(() => loaded);
      return true;
    } catch {
      return false;
    }
  },
};
