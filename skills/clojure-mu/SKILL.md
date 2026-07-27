---
name: clojure-mu
description: |
  Emit Malli schemas as named, registry-keyed, composable data.
  Use when a domain type needs to be defined, validated, or composed.
  μ is the homoiconic operator: the schema IS the data, in the same
  medium, subject to the same transformations. Activates on symbol μ.
---

# clojure-mu (μ)

μ = the least fixed point of a domain constraint.

A Malli schema is not a description of data from outside.
It IS the data: a Clojure value in the same medium, subject to the same
transformations: assoc, merge, serialize to EDN, store, transmit, inspect.
That is homoiconicity. That is the data-oriented mentality formalized.

  μ X . F(X) = the smallest structure X such that F(X) = X

## Obligations

When you emit μ, you must:

1. Name the type -- give it a registry key, not an anonymous [:map ...]
2. Close the recursion -- use [:ref ...] if the type refers to itself
3. Emit the validator -- (m/validator schema) is the executable μ
4. Keep it data -- the schema must be EDN-serializable plain data

Anonymous [:map ...] is NOT μ. A schema with no name has no fixed point.

## Canonical Form

```clojure
(ns knoxx.schema
  (:require [malli.core :as m]
            [malli.registry :as mr]))

;; The registry IS the fixed-point lattice
(def registry
  {"Fact"      [:map
                 [:id     :uuid]
                 [:claim  :string]
                 [:source :string]
                 [:p      [:double {:min 0.0 :max 1.0}]]
                 [:time   inst?]]

   "Obs"       [:map
                 [:id     :uuid]
                 [:raw    :any]
                 [:source :string]
                 [:time   inst?]]

   "Inference" [:map
                 [:id    :uuid]
                 [:from  [:vector [:ref "Fact"]]]
                 [:claim :string]
                 [:p     [:double {:min 0.0 :max 1.0}]]]

   "Judgment"  [:map
                 [:id      :uuid]
                 [:infers  [:ref "Inference"]]
                 [:verdict [:enum :accept :reject :defer]]]

   ;; μ X . Node with children of type X
   "Node"      [:map
                 [:id       :uuid]
                 [:type     :keyword]
                 [:value    :any]
                 [:children [:vector [:ref "Node"]]]]})

;; μ as a function: schema-name -> least-fixed-point predicate
(defn μ [schema-name]
  (m/validator [:ref schema-name]
               {:registry (mr/composite-registry
                            m/default-registry
                            registry)}))

;; ((μ "Fact") {:id #uuid"..." :claim "x" :source "y" :p 0.9 :time (inst)})
;; => true | false
```

## Rules

1. Registry over inline -- schemas live in a named registry, not scattered
   inline. The registry is the single source of truth for domain types.

2. Composable by default -- schemas built from other schemas via
   [:ref ...], [:merge ...], [:and ...]. Never copy-paste schema fragments.

3. EDN-serializable always -- if a schema cannot round-trip through EDN,
   it is not μ. No functions, closures, or opaque objects in schema bodies.

4. Validator is the contract -- (m/validator schema) is the executable
   form of the constraint. Ship the validator alongside the schema.

5. Optional keys are explicit -- [:map [:key {:optional true}] ...]
   Never silently ignore missing keys.

6. Recursive types use [:ref] -- self-referential schemas must close
   the recursion explicitly. An open recursion is an unclosed fixed point.

## Detection: when to emit μ

Emit a μ-form when you see:

- A domain concept passed as an unvalidated plain map
- An inline anonymous [:map ...] that appears more than once
- A function that defensively nil-checks keys it should trust
- A comment describing what keys a map 'should have'
- Any data crossing a trust boundary (API, DB, Discord, MCP)

## Relationship to η and β

- β (beta) is what the machine does: macro expansion, compilation, evaluation.
  The runtime β-reduces your code automatically. You cannot stop it.

- η (eta) is what you do to form: strip the wrapper lambda, reveal the
  function. Reduce syntactic noise. Point-free style.

- μ (mu) is what you do to data: collapse the wall between the thing and
  its description. The schema IS the data. Homoiconicity made explicit.

- ημ (eta-mu) is the Fixed Point: the convergence where the minimal executable form is identical to the formal domain shape. The doing is the being.

β runs the substrate. η cleans the form. μ formalizes the shape. ημ unifies them.

## Origin

μ is the fixed-point operator of domain theory and the μ-calculus.
μ X . F(X) = the least type satisfying its own definition.
In Knoxx, μ is the homoiconic operator: the schema and the value it
validates are the same kind of thing, in the same medium, at the same
level of abstraction. That is the data-oriented mentality formalized.