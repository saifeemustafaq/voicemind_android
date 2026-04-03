import { openaiApiKey } from "./config.js";

export interface OpenAIChatResponse {
  choices: Array<{ message: { content: string } }>;
}

/**
 * Single OpenAI chat completions helper used by all text-based calls.
 * Throws on non-OK HTTP response with status code only — never logs the response
 * body to avoid leaking transcript content in error echo-backs.
 */
export async function callOpenAI(
  messages: Array<{ role: string; content: string }>,
  opts: {
    model?: string;
    maxTokens?: number;
    responseFormat?: unknown;
  } = {}
): Promise<OpenAIChatResponse> {
  const response = await fetch("https://api.openai.com/v1/chat/completions", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${openaiApiKey.value()}`,
    },
    body: JSON.stringify({
      model: opts.model ?? "gpt-4o-mini",
      messages,
      max_tokens: opts.maxTokens ?? 300,
      ...(opts.responseFormat ? { response_format: opts.responseFormat } : {}),
    }),
  });
  if (!response.ok) {
    throw new Error(`OpenAI request failed: ${response.status}`);
  }
  return (await response.json()) as OpenAIChatResponse;
}
