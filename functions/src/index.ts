// lib/firestore.ts MUST be the first import — it calls admin.initializeApp() and
// setGlobalOptions() before any feature module registers a Cloud Function.
import "./lib/firestore.js";

export * from "./transcription.js";
export * from "./summary.js";
export * from "./googleTasks.js";
export * from "./sharing.js";
export * from "./nts.js";
export * from "./userProfile.js";
