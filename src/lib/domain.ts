export type ReviewStatus = 'UNREVIEWED' | 'CONFIRMED' | 'REJECTED';

export interface SampleMetadata {
  sampling_site: string | null;
  latitude: number | null;
  longitude: number | null;
  sampled_at: string | null;
  water_depth_meters: number | null;
  water_temperature_celsius: number | null;
  ph: number | null;
  salinity_psu: number | null;
  sample_code: string | null;
}

export interface DatasetRecord extends SampleMetadata {
  id: string;
  name: string;
  description: string | null;
  created_at: string;
}

export interface PlanktonImageRecord {
  id: string;
  image_url: string;
  custom_name: string | null;
  created_at: string;
  dataset_id: string;
  species_id: string | null;
  is_favorite: boolean;
  identification_confidence: number | null;
  review_status: ReviewStatus;
  review_note: string | null;
  reviewed_at: string | null;
}
