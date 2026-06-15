"use client";

import { Plus, Trash2 } from "lucide-react";
import { useState } from "react";

import { Button } from "@/components/rentflow/ui/button";
import type { CreateDamageItemInput, TripDamageSeverity } from "@/features/trips/api";

type DamageItemEditorProps = {
  items: CreateDamageItemInput[];
  onChange: (items: CreateDamageItemInput[]) => void;
};

const severityOptions: { value: TripDamageSeverity; label: string }[] = [
  { value: "MINOR", label: "Nhe" },
  { value: "MODERATE", label: "Vua" },
  { value: "SEVERE", label: "Nang" },
];

export function DamageItemEditor({ items, onChange }: DamageItemEditorProps) {
  const [draft, setDraft] = useState<CreateDamageItemInput>({
    location: "",
    severity: "MINOR",
    description: "",
    preExisting: false,
  });

  function addItem() {
    const location = draft.location.trim();
    const description = draft.description.trim();
    if (!location || !description) return;
    onChange([...items, { ...draft, location, description }]);
    setDraft({ location: "", severity: "MINOR", description: "", preExisting: false });
  }

  return (
    <div className="space-y-3">
      <div className="grid gap-3 md:grid-cols-[1fr_140px]">
        <label className="grid gap-1 text-sm">
          <span className="font-medium">Vi tri</span>
          <input
            className="h-9 rounded-md border border-input bg-background px-3 text-sm"
            value={draft.location}
            onChange={(event) => setDraft((current) => ({ ...current, location: event.target.value }))}
          />
        </label>
        <label className="grid gap-1 text-sm">
          <span className="font-medium">Muc do</span>
          <select
            className="h-9 rounded-md border border-input bg-background px-3 text-sm"
            value={draft.severity}
            onChange={(event) =>
              setDraft((current) => ({ ...current, severity: event.target.value as TripDamageSeverity }))
            }
          >
            {severityOptions.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </label>
      </div>
      <label className="grid gap-1 text-sm">
        <span className="font-medium">Mo ta</span>
        <textarea
          className="min-h-20 rounded-md border border-input bg-background px-3 py-2 text-sm"
          value={draft.description}
          onChange={(event) => setDraft((current) => ({ ...current, description: event.target.value }))}
        />
      </label>
      <label className="flex items-center gap-2 text-sm">
        <input
          type="checkbox"
          checked={draft.preExisting}
          onChange={(event) => setDraft((current) => ({ ...current, preExisting: event.target.checked }))}
        />
        Co truoc chuyen di
      </label>
      <Button type="button" variant="outline" size="sm" onClick={addItem}>
        <Plus className="size-4" aria-hidden="true" />
        Them hu hong
      </Button>

      {items.length ? (
        <div className="space-y-2">
          {items.map((item, index) => (
            <div key={`${item.location}-${index}`} className="flex items-start justify-between gap-3 rounded-lg border p-3">
              <div>
                <p className="text-sm font-medium">{item.location}</p>
                <p className="text-xs text-muted-foreground">{item.severity}</p>
                <p className="mt-1 text-sm">{item.description}</p>
              </div>
              <Button
                type="button"
                variant="ghost"
                size="icon-sm"
                aria-label="Xoa hu hong"
                onClick={() => onChange(items.filter((_, itemIndex) => itemIndex !== index))}
              >
                <Trash2 className="size-4" aria-hidden="true" />
              </Button>
            </div>
          ))}
        </div>
      ) : null}
    </div>
  );
}
