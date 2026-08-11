import type { SpreadsheetSheetDraft } from "@/types/contracts/spreadsheet";

function spreadsheetColumnName(index: number) {
  let value = index + 1;
  let label = "";
  while (value > 0) {
    const remainder = (value - 1) % 26;
    label = String.fromCharCode(65 + remainder) + label;
    value = Math.floor((value - 1) / 26);
  }
  return label;
}

function spreadsheetCellText(value: unknown): string | number | boolean | null {
  if (value == null) return null;
  if (typeof value === "string" || typeof value === "number" || typeof value === "boolean") {
    return value;
  }
  if (value instanceof Date) return value.toLocaleString();
  if (typeof value === "object") {
    const record = value as Record<string, unknown>;
    if (record.result != null) return spreadsheetCellText(record.result);
    if (typeof record.text === "string") return record.text;
    if (Array.isArray(record.richText)) {
      return record.richText
        .map((part) =>
          typeof part === "object" && part !== null && "text" in part
            ? String((part as { text: unknown }).text)
            : "",
        )
        .join("");
    }
  }
  return String(value);
}

export function sanitizeDocumentHtml(value: string) {
  const documentValue = new DOMParser().parseFromString(value, "text/html");
  documentValue
    .querySelectorAll("script,style,iframe,object,embed,form,meta,link")
    .forEach((node) => node.remove());
  documentValue.querySelectorAll("*").forEach((element) => {
    for (const attribute of [...element.attributes]) {
      const name = attribute.name.toLowerCase();
      const content = attribute.value.trim().toLowerCase();
      if (
        name.startsWith("on") ||
        ((name === "href" || name === "src") && content.startsWith("javascript:"))
      ) {
        element.removeAttribute(attribute.name);
      }
    }
  });
  return documentValue.body.innerHTML;
}

export async function parseDocx(blob: Blob) {
  const mammoth = await import("mammoth");
  const result = await mammoth.convertToHtml({ arrayBuffer: await blob.arrayBuffer() });
  return sanitizeDocumentHtml(result.value);
}

export async function parseXlsx(blob: Blob): Promise<SpreadsheetSheetDraft[]> {
  const ExcelJS = await import("exceljs");
  const workbook = new ExcelJS.Workbook();
  await workbook.xlsx.load(await blob.arrayBuffer());
  return workbook.worksheets.map((worksheet, sheetIndex) => {
    const rows = worksheet.getSheetValues().slice(1) as unknown[][];
    const width = Math.max(1, ...rows.map((row) => (Array.isArray(row) ? row.length - 1 : 0)));
    return {
      sheetId: String(worksheet.id || sheetIndex + 1),
      name: worksheet.name,
      columns: Array.from({ length: width }, (_, index) => spreadsheetColumnName(index)),
      rows: rows.map((row) =>
        Array.from({ length: width }, (_, index) =>
          spreadsheetCellText(Array.isArray(row) ? row[index + 1] : null),
        ),
      ),
    };
  });
}

export function parseCsv(text: string): SpreadsheetSheetDraft[] {
  const rows: string[][] = [];
  let row: string[] = [];
  let cell = "";
  let quoted = false;
  for (let index = 0; index < text.length; index += 1) {
    const character = text[index];
    if (character === '"') {
      if (quoted && text[index + 1] === '"') {
        cell += '"';
        index += 1;
      } else {
        quoted = !quoted;
      }
    } else if (character === "," && !quoted) {
      row.push(cell);
      cell = "";
    } else if ((character === "\n" || character === "\r") && !quoted) {
      if (character === "\r" && text[index + 1] === "\n") index += 1;
      row.push(cell);
      if (row.some((value) => value.length > 0)) rows.push(row);
      row = [];
      cell = "";
    } else {
      cell += character;
    }
  }
  row.push(cell);
  if (row.some((value) => value.length > 0)) rows.push(row);
  const width = Math.max(1, ...rows.map((value) => value.length));
  return [
    {
      sheetId: "csv",
      name: "CSV",
      columns: Array.from({ length: width }, (_, index) => spreadsheetColumnName(index)),
      rows,
    },
  ];
}

