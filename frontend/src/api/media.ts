import { mediaRepository } from "@/repositories/media";
import { createRandomId } from "@/utils/randomId";
import type {
  AudioTranscriptionDto,
  CreateImageRecognitionRequest,
  ImageRecognitionJob,
  MediaAssetDto,
  TranscribeAudioRequest,
  UploadImageRequest,
} from "@/types/contracts/media";

export type {
  MediaAssetDto,
  UploadImageRequest,
  TranscribeAudioRequest,
  AudioTranscriptionDto,
  CreateImageRecognitionRequest,
  ImageRecognitionJob,
};

export async function uploadImage(
  file: File,
  input: Omit<UploadImageRequest, "clientRequestId">,
  signal?: AbortSignal,
): Promise<MediaAssetDto> {
  const clientRequestId = createRandomId("media");
  return mediaRepository.uploadImage(file, { ...input, clientRequestId }, signal);
}

export async function transcribeAudio(
  file: File,
  input: Omit<TranscribeAudioRequest, "clientRequestId">,
  signal?: AbortSignal,
): Promise<AudioTranscriptionDto> {
  const clientRequestId = createRandomId("media");
  return mediaRepository.transcribeAudio(file, { ...input, clientRequestId }, signal);
}

export async function createImageRecognitionJob(
  assetId: string,
  input: CreateImageRecognitionRequest,
): Promise<ImageRecognitionJob> {
  return mediaRepository.createImageRecognitionJob(assetId, input);
}

export async function getImageRecognitionJob(jobId: string): Promise<ImageRecognitionJob> {
  return mediaRepository.getImageRecognitionJob(jobId);
}
