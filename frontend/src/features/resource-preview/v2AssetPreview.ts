import { fetchAssetContent, getAsset } from "@/api/assetLibraryV2";
import type { ResourcePreviewDto } from "@/types/contracts/library";
import type { SpreadsheetSheetDraft } from "@/types/contracts/spreadsheet";
import { parseCsv, parseDocx, parseXlsx } from "./previewParsers";
import { buildAssetPreviewDto, resolveAssetPreview } from "./previewRegistry";

export type PreparedV2AssetPreview = {
  preview: ResourcePreviewDto;
  wordHtml: string;
  sheets: SpreadsheetSheetDraft[];
  objectUrl: string | null;
};

export async function prepareV2AssetPreview(assetId: string): Promise<PreparedV2AssetPreview> {
  const { asset } = await getAsset(assetId);
  const descriptor = resolveAssetPreview(asset);
  const preview = buildAssetPreviewDto(asset, descriptor);
  const prepared: PreparedV2AssetPreview = {
    preview,
    wordHtml: "",
    sheets: [],
    objectUrl: null,
  };
  if (preview.status !== "ready") return prepared;

  const blob = await fetchAssetContent(asset.assetId, "inline");
  switch (descriptor.strategy) {
    case "text":
      preview.textContent = await blob.text();
      break;
    case "docx":
      prepared.wordHtml = await parseDocx(blob);
      break;
    case "csv":
      prepared.sheets = parseCsv(await blob.text());
      break;
    case "xlsx":
      prepared.sheets = await parseXlsx(blob);
      break;
    case "object-url":
      prepared.objectUrl = URL.createObjectURL(blob);
      preview.previewUrl = prepared.objectUrl;
      break;
    case "unsupported":
      break;
  }
  return prepared;
}

export function disposePreparedV2AssetPreview(prepared: PreparedV2AssetPreview) {
  if (prepared.objectUrl) URL.revokeObjectURL(prepared.objectUrl);
}

