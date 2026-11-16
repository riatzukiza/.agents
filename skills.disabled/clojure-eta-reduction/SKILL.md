---
name: clojure-eta-reduction
description: |
  Refactor ClojureScript/Clojure code toward eta-reduced, point-free style.
  Use when you see redundant wrapper lambdas, (fn [x] (f x)) patterns, or
  multi-step async flows using nested js-await. Covers eta reduction,
  partial application, p/let for async/await style, threading macros,
  when-let idioms. Activates on symbol η.
---

# clojure-eta-reduction (η)

η mode: strip the wrapper, expose the function.

## What is Eta Reduction?

In lambda calculus, eta reduction (η-reduction) collapses:

  (fn [x] (f x))  ->  f

when `f` is pure and the wrapper adds nothing.

## Core Rules

1. Prefer `partial` over `fn` for partial application.
   `(partial f a)` over `(fn [x] (f a x))`

2. Prefer point-free threading. `->>` and `->` are eta-reduced pipelines.

3. `when-let` over `let`+`if`.
   `(when-let [x (f)] ...)` over `(let [x (f)] (if x ...))`

4. `p/let` over nested `js-await` for async/await style in CLJS.
   Nested js-await is eta-abstraction of a Promise chain -- collapse it.

5. Name the function, kill the lambda.
   Extract lambdas with clear identity to `defn-` level.

## Async: p/let is the Async Eta Reduction

```clojure
;; Bad: eta-abstracted (verbose nested js-await)
(js-await [res (fetch! url)]
  (js-await [body (.json res)]
    body))

;; Good: eta-reduced (flat p/let)
(p/let [res  (fetch! url)
        body (.json res)]
  body)
```

Require `[promesa.core :as p]` in any namespace using `p/let`.
Always include `p/catch` for domain-relevant async errors.

## Sync I/O -> Async Migration

Synchronous blocking calls are wrapper noise. Replace on contact:

| Sync | Async |
|------|-------|
| `fs.readFileSync` | `(p/let [x (.readFile fsp path "utf8")] ...)` |
| `fs.writeFileSync` | `(p/let [_ (.writeFile fsp path content)] ...)` |
| `fs.existsSync` | `(p/let [_ (.access fsp path)] ...)` |

If it's on your code path, leave it async. Leave code better than you found it.

## Detection Patterns

- `(fn [x] (f x))` -- trivial wrapper, collapse to `f`
- `(fn [x] (f config x))` -- partial app, collapse to `(partial f config)`
- nested `js-await` -- Promise wrapper, collapse to `p/let`
- `(let [x ...] (if x ...))` -- conditional wrapper, collapse to `when-let`
- `(.readFileSync ...)` -- sync I/O, collapse to `p/let` + `fs/promises`

## Origin

η is the operator symbol in the ημΠ prompt signature of this system.
It means: minimal executable output. Strip the wrapper.
The symbol and the lambda calculus concept converged independently --
same shape at different levels of abstraction. That's the point.

When combined with μ (the homoiconic operator), ημ represents the fixed point where the minimal executable form is identical to the formal domain shape. The doing is the being.
