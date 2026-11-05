// SPDX-License-Identifier: GPL-3.0-or-later
// Native async/await interop for NBB, which does not implement CLJS await.
// Invocation, policy, challenge ownership and failure values belong to callers.
export async function consumeResponse(invoke, consume, failure) {
  let response;
  try {
    response = await invoke();
  } catch {
    response = failure;
  }
  return consume(response);
}
