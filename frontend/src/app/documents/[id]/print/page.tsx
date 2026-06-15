import { Printer } from "lucide-react";

type DocumentPrintPageProps = {
  params: Promise<{ id: string }>;
};

export default async function DocumentPrintPage({ params }: DocumentPrintPageProps) {
  const { id } = await params;
  const printUrl = `/api/v1/rental-documents/${id}/print`;

  return (
    <main className="min-h-screen bg-white text-slate-950">
      <div className="flex items-center justify-between border-b border-slate-200 px-4 py-3 print:hidden">
        <div>
          <h1 className="text-base font-semibold">Tai lieu thue xe</h1>
          <p className="text-sm text-slate-500">{id}</p>
        </div>
        <a
          className="inline-flex items-center gap-2 rounded-md border border-slate-300 px-3 py-2 text-sm font-medium hover:bg-slate-50"
          href={printUrl}
          rel="noreferrer"
          target="_blank"
        >
          <Printer className="h-4 w-4" aria-hidden />
          Mo ban in
        </a>
      </div>
      <iframe
        className="h-[calc(100vh-61px)] w-full border-0 print:h-screen"
        src={printUrl}
        title="Printable rental document"
      />
    </main>
  );
}
