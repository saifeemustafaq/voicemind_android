import * as functionsV1 from "firebase-functions/v1";
import { db } from "./lib/firestore.js";

/**
 * Auth trigger (Gen 1 — auth triggers are not available in Gen 2).
 * Creates a user profile document on first sign-in.
 */
export const onUserCreated = functionsV1.auth.user().onCreate(async (user) => {
  const { uid, displayName, email, photoURL } = user;
  await db.collection("users").doc(uid).set(
    {
      displayName: displayName || "",
      email: email || "",
      photoUrl: photoURL || "",
      discoverable: true,
    },
    { merge: true }
  );
});
