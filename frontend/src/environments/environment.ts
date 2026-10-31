import { generated } from './environment.generated';

/** App settings. The values come from ../.env through scripts/write-env.mjs (never committed). */
export const environment: {
  /** The Spring Boot API, such as http://localhost:8080/api/v1 */
  apiUrl: string;
  /** The Supabase project, used for sign-in only (PLAN.md section 2) */
  supabaseUrl: string;
  /** Public key for Supabase Auth from the browser; it is not a secret, but stays out of git */
  supabasePublishableKey: string;
  /** The build, such as "2026-11-01 (f30c2e4)", shown in settings */
  version: string;
} = generated;
