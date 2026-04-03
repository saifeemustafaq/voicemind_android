import { defineSecret } from "firebase-functions/params";

export const openaiApiKey = defineSecret("OPENAI_API_KEY");
export const googleClientSecret = defineSecret("GOOGLE_CLIENT_SECRET");

export const WEB_CLIENT_ID =
  "685270102033-tupn4a0mm03k7pdrnd1lhlv53gbq605t.apps.googleusercontent.com";

export const RATE_LIMIT_WINDOW_MS = 60_000;
export const RATE_LIMIT_MAX = 10;
